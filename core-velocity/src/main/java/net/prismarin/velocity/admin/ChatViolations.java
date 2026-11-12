package net.prismarin.velocity.admin;

import com.google.gson.JsonObject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.moderation.ChatFilterEscalatedEvent;
import net.prismarin.api.moderation.ChatMessage;
import net.prismarin.shared.chatfilter.ChatFilterSettings.Escalation;
import net.prismarin.shared.chatfilter.ChatViolation;
import net.prismarin.shared.chatfilter.ViolationCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChatViolations {

    public static final String NOTIFY = "prismarin.staff.chatfilter";

    private static final Logger LOGGER = LoggerFactory.getLogger("PrismarinCore");
    private static final long ALERT_GAP_MILLIS = 2_000L;
    private static final int MAX_DETAILS = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AdminContext context;
    private final PunishmentFlow punishments;
    private final ReportFlow reports;
    private final ViolationCounter counter;
    private final Clock clock;
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();

    public ChatViolations(AdminContext context, PunishmentFlow punishments, ReportFlow reports, Clock clock) {
        this.context = context;
        this.punishments = punishments;
        this.reports = reports;
        this.clock = clock;
        this.counter = new ViolationCounter(clock);
        context.channel().handleViolations(this::received);
    }

    void received(Player carrier, String server, JsonObject message) {
        ChatViolation.parse(message)
                .filter(violation -> violation.player().equals(carrier.getUniqueId()))
                .ifPresent(violation -> handle(server, violation));
    }

    public void handle(String server, ChatViolation violation) {
        if (violation.empty()) {
            return;
        }
        alert(server, violation);
        audit(violation);
        escalate(violation);
    }

    public void forget(UUID player) {
        lastAlert.remove(player);
    }

    private void alert(String server, ChatViolation violation) {
        List<ChatViolation.Entry> shown = violation.entries().stream().filter(ChatViolation.Entry::alert).toList();
        if (shown.isEmpty()) {
            return;
        }
        long now = clock.millis();
        Long last = lastAlert.put(violation.player(), now);
        if (last != null && now - last < ALERT_GAP_MILLIS) {
            return;
        }
        boolean check = context.proxy().getCommandManager().hasCommand("check");
        List<CommandSource> receivers = new ArrayList<>();
        context.proxy().getAllPlayers().stream().filter(player -> player.hasPermission(NOTIFY)).forEach(receivers::add);
        receivers.add(context.proxy().getConsoleCommandSource());
        for (CommandSource receiver : receivers) {
            Component line = context.messages().render(receiver, "chat-filter.staff",
                            Placeholder.unparsed("player", violation.name()),
                            Placeholder.unparsed("server", server),
                            Placeholder.unparsed("rules", rules(receiver, shown)),
                            Placeholder.unparsed("message", violation.message()))
                    .hoverEvent(HoverEvent.showText(context.messages().render(receiver, "chat-filter.staff-hover",
                            Placeholder.unparsed("channel", violation.channel()),
                            Placeholder.unparsed("details", details(shown)),
                            Placeholder.unparsed("shown", violation.blocked() ? "-" : violation.shown()))));
            receiver.sendMessage(check ? line.clickEvent(ClickEvent.runCommand("/check " + violation.name())) : line);
        }
    }

    private void audit(ChatViolation violation) {
        List<ChatViolation.Entry> logged = violation.entries().stream().filter(ChatViolation.Entry::audit).toList();
        if (logged.isEmpty()) {
            return;
        }
        String action = "chat-filter." + (violation.blocked() ? "block" : logged.getFirst().action().id());
        String rules = logged.stream().map(ChatViolation.Entry::rule).collect(Collectors.joining(", "));
        String details = rules + " (" + violation.channel() + "): " + violation.message();
        context.services().audit().record(actorName(), action, violation.name(),
                details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details);
    }

    private void escalate(ChatViolation violation) {
        for (ChatViolation.Entry entry : violation.entries()) {
            Escalation escalation = entry.escalation();
            if (!escalation.enabled()) {
                continue;
            }
            counter.record(violation.player(), entry.rule(), escalation.violations(), escalation.windowMillis(),
                    evidence(violation, entry)).ifPresent(trigger -> trigger(violation, entry, trigger));
        }
    }

    private void trigger(ChatViolation violation, ChatViolation.Entry entry, ViolationCounter.Trigger trigger) {
        Escalation escalation = entry.escalation();
        String actor = actorName();
        String count = String.valueOf(trigger.violations());
        boolean exempt = context.proxy().getPlayer(violation.player())
                .map(player -> player.hasPermission(PunishmentFlow.EXEMPT)).orElse(false);
        boolean punish = escalation.punishes() && punishments != null && !exempt;
        if (punish && punishments.template(escalation.template()).isEmpty()) {
            LOGGER.warn("chat-filter.yml: {} escalates to the template {}, which punishments.yml does not have",
                    entry.rule(), escalation.template());
            punish = false;
        }
        boolean report = escalation.report() && reports != null;
        context.notifyStaff(NOTIFY, "chat-filter.escalated", Placeholder.unparsed("player", violation.name()),
                Placeholder.unparsed("rule", entry.rule()), Placeholder.unparsed("count", count),
                Placeholder.unparsed("template", punish ? escalation.template() : "-"));
        context.services().audit().record(actor, "chat-filter.escalation", violation.name(),
                entry.rule() + " " + count + "x" + (punish ? " -> " + escalation.template() : ""));
        if (punish) {
            punishments.punish(violation.player(), escalation.template(), actor, fill("chat-filter.escalation-note",
                    count, entry.rule())).exceptionally(error -> {
                        LOGGER.warn("The chat filter could not punish {}: {}", violation.name(), error.getMessage());
                        return null;
                    });
        }
        if (report) {
            reports.fileWithEvidence(actor, violation.player(), escalation.reportReason(),
                    fill("chat-filter.report-details", count, entry.rule()), trigger.evidence())
                    .exceptionally(error -> {
                        LOGGER.warn("The chat filter could not report {}: {}", violation.name(), error.getMessage());
                        return null;
                    });
        }
        try {
            context.services().events().publish(new ChatFilterEscalatedEvent(violation.player(), violation.name(),
                    entry.rule(), trigger.violations(), punish ? escalation.template() : "", report));
        } catch (RuntimeException error) {
            LOGGER.warn("A ChatFilterEscalatedEvent listener failed", error);
        }
    }

    private String evidence(ChatViolation violation, ChatViolation.Entry entry) {
        String time = TIME.format(Instant.ofEpochMilli(clock.millis()).atZone(ZoneId.systemDefault()));
        String tag = ChatMessage.PUBLIC.equals(violation.channel()) ? entry.rule()
                : entry.rule() + ", " + violation.channel();
        return "> " + time + " " + violation.name() + ": " + violation.message() + " [" + tag + "]";
    }

    private String rules(CommandSource viewer, List<ChatViolation.Entry> entries) {
        return entries.stream().map(entry -> entry.rule() + " " + context.messages().locale().raw(
                "chat-filter.action." + entry.action().id(), context.messages().localeOf(viewer)))
                .collect(Collectors.joining(", "));
    }

    private static String details(List<ChatViolation.Entry> entries) {
        return entries.stream().map(entry -> entry.rule() + ": " + (entry.detail().isEmpty() ? "-" : entry.detail()))
                .collect(Collectors.joining(", "));
    }

    private String actorName() {
        return context.messages().locale().raw("chat-filter.name", context.messages().fallback());
    }

    private String fill(String key, String count, String rule) {
        return context.messages().locale().raw(key, context.messages().fallback()).replace("%count%", count)
                .replace("%rule%", rule);
    }
}
