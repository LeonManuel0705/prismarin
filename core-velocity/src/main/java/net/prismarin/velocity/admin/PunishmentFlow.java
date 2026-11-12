package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.moderation.AltAccount;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentRequest;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.admin.Report;
import net.prismarin.shared.text.LegacyColors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PunishmentFlow {

    public static final String PUNISH = "prismarin.staff.punish";
    public static final String EXEMPT = "prismarin.punish.exempt";
    public static final String IP_BYPASS = "prismarin.punish.ipban.bypass";

    private static final Logger LOGGER = LoggerFactory.getLogger("PrismarinCore");
    private static final long MENU_TIMEOUT_MILLIS = 120_000L;
    private static final int HISTORY_PAGE_SIZE = 8;
    private static final int MENU_LIMIT = 28;
    private static final String FALLBACK_DATE = "dd.MM.yyyy HH:mm";
    private static final String KEY = "punishment.";

    public record Subject(UUID uuid, String name, Player online) {
    }

    private record Pending(UUID target, String targetName, Set<String> templates, long expires) {
    }

    private final AdminContext context;
    private final PunishmentSettings settings;
    private final NickDirectory nicks;
    private final AddressKeys addresses;
    private final Map<UUID, Pending> menus = new ConcurrentHashMap<>();
    private final Map<String, DateTimeFormatter> formats = new ConcurrentHashMap<>();
    private final Map<String, PunishmentTemplate> added = new LinkedHashMap<>();

    public PunishmentFlow(AdminContext context, PunishmentSettings settings, AddressKeys addresses) {
        this.context = context;
        this.settings = settings;
        this.nicks = new NickDirectory(context.services().documents());
        this.addresses = addresses;
        context.channel().handlePunishments(this::chosen);
        settings.problems().forEach(problem -> LOGGER.warn("{}: {}", PunishmentSettings.FILE_NAME, problem));
    }

    public PunishmentSettings settings() {
        return settings;
    }

    public static boolean mayIssue(CommandSource source, PunishmentKind kind) {
        return switch (kind) {
            case BAN -> source.hasPermission("prismarin.staff.ban");
            case TEMPBAN -> source.hasPermission("prismarin.staff.tempban") || source.hasPermission("prismarin.staff.ban");
            case IPBAN -> source.hasPermission("prismarin.staff.ipban");
            case MUTE -> source.hasPermission("prismarin.staff.mute");
            case TEMPMUTE -> source.hasPermission("prismarin.staff.tempmute")
                    || source.hasPermission("prismarin.staff.mute");
            case KICK -> source.hasPermission("prismarin.staff.kick");
            case WARN -> source.hasPermission("prismarin.staff.warn");
        };
    }

    public boolean mayUse(CommandSource source, PunishmentTemplate template) {
        return !template.restricted() || source.hasPermission(template.permission());
    }

    public List<PunishmentTemplate> usableTemplates(CommandSource source) {
        return templates().stream().filter(template -> mayUse(source, template)).toList();
    }

    public List<PunishmentTemplate> templates() {
        synchronized (added) {
            List<PunishmentTemplate> all = new ArrayList<>(settings.templates());
            all.addAll(added.values());
            return List.copyOf(all);
        }
    }

    public Optional<PunishmentTemplate> template(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.toLowerCase(Locale.ROOT);
        return templates().stream().filter(template -> template.id().equals(key)).findFirst();
    }

    public void register(PunishmentTemplate template) {
        if (settings.template(template.id()).isPresent()) {
            throw new IllegalArgumentException("the template " + template.id() + " already comes from "
                    + PunishmentSettings.FILE_NAME);
        }
        synchronized (added) {
            added.put(template.id(), template);
        }
    }

    public void unregister(String id) {
        synchronized (added) {
            added.remove(id.toLowerCase(Locale.ROOT));
        }
    }

    public CompletableFuture<Escalation.Outcome> preview(UUID target, String templateId) {
        Optional<PunishmentTemplate> template = template(templateId);
        if (template.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("unknown template " + templateId));
        }
        return context.services().punishments().history(target).thenApply(history ->
                Escalation.next(template.get(), history, now(), settings.countLifted()));
    }

    public CompletableFuture<Punishment> punish(UUID target, String templateId, String actor, String note) {
        Optional<PunishmentTemplate> template = template(templateId);
        if (template.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("unknown template " + templateId));
        }
        Optional<Player> online = context.proxy().getPlayer(target);
        CompletableFuture<Optional<Subject>> subject = online.isPresent()
                ? CompletableFuture.completedFuture(Optional.of(new Subject(target, online.get().getUsername(),
                        online.get())))
                : context.services().profiles().find(target).thenApply(profile -> profile.map(value ->
                        new Subject(target, value.getName(), null)));
        String cleanNote = note == null ? "" : note.trim();
        return subject.thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.<Punishment>failedFuture(new IllegalArgumentException(target
                        + " never joined"));
            }
            return context.services().punishments().history(target).thenCompose(history -> {
                Escalation.Outcome outcome = Escalation.next(template.get(), history, now(), settings.countLifted());
                PunishmentRequest request = request(found.get(), template.get(), outcome.step(), actor, cleanNote);
                return issue(context.proxy().getConsoleCommandSource(), found.get(), request, outcome)
                        .thenApply(issued -> issued.orElseThrow(() -> new IllegalStateException(found.get().name()
                                + " already has a longer punishment")));
            });
        });
    }

    private PunishmentRequest request(Subject subject, PunishmentTemplate template, PunishmentTemplate.Step step,
                                      String actor, String note) {
        String name = template.name(context.messages().fallback());
        String reason = note.isBlank() ? name : name + ": " + note;
        return PunishmentRequest.of(subject.uuid(), subject.name(), step.type(), reason, actor, step.duration())
                .withTemplate(template.id(), note);
    }

    CompletableFuture<Optional<Subject>> resolve(String input) {
        Optional<Player> online = context.proxy().getPlayer(input);
        Optional<UUID> nickOwner = online.isPresent() ? Optional.empty() : nicks.ownerOf(input);
        if (online.isEmpty()) {
            online = nickOwner.flatMap(context.proxy()::getPlayer);
        }
        if (online.isPresent()) {
            Player player = online.get();
            return CompletableFuture.completedFuture(Optional.of(new Subject(player.getUniqueId(),
                    player.getUsername(), player)));
        }
        return context.resolve(input).thenCompose(found -> {
            if (found.isPresent() || nickOwner.isEmpty()) {
                return CompletableFuture.<Optional<Subject>>completedFuture(found.map(target ->
                        new Subject(target.uuid(), target.name(),
                                context.proxy().getPlayer(target.uuid()).orElse(null))));
            }
            UUID owner = nickOwner.get();
            return context.services().profiles().find(owner).thenApply(profile -> profile
                    .map(value -> new Subject(owner, value.getName(), null)));
        });
    }

    void manual(CommandSource source, PunishmentArguments.Manual parsed) {
        withSubject(source, parsed.target(), subject -> {
            PunishmentKind kind = parsed.kind();
            boolean needsOnline = kind == PunishmentKind.KICK || kind == PunishmentKind.IPBAN;
            if (needsOnline && (subject.online() == null || !context.vanish().canSee(source, subject.online()))) {
                context.send(source, "staff.not-online", Placeholder.unparsed("player", parsed.target()));
                return;
            }
            PunishmentRequest request = PunishmentRequest.of(subject.uuid(), subject.name(), parsed.type(),
                    parsed.reason(), AdminContext.actor(source), parsed.duration());
            if (kind == PunishmentKind.IPBAN) {
                Optional<String> hash = addresses.of(subject.online().getRemoteAddress());
                if (hash.isEmpty()) {
                    context.send(source, KEY + "no-address", Placeholder.unparsed("player", subject.name()));
                    return;
                }
                request = request.withAddress(hash.get());
            }
            issue(source, subject, request, null);
        });
    }

    void template(CommandSource source, String input, String templateId, String note) {
        Optional<PunishmentTemplate> template = template(templateId);
        if (template.isEmpty()) {
            unknownTemplate(source, templateId);
            return;
        }
        if (!mayUse(source, template.get())) {
            context.send(source, KEY + "template-locked", Placeholder.unparsed("template",
                    template.get().name(context.messages().localeOf(source))));
            return;
        }
        withSubject(source, input, subject -> applyTemplate(source, subject, template.get(), note));
    }

    void unknownTemplate(CommandSource source, String templateId) {
        context.send(source, KEY + "unknown-template", Placeholder.unparsed("template", templateId),
                Placeholder.unparsed("templates", String.join(", ", usableTemplates(source).stream()
                        .map(PunishmentTemplate::id).toList())));
    }

    private void applyTemplate(CommandSource source, Subject subject, PunishmentTemplate template, String note) {
        context.services().punishments().history(subject.uuid()).thenAccept(history -> {
            Escalation.Outcome outcome = Escalation.next(template, history, now(), settings.countLifted());
            PunishmentTemplate.Step step = outcome.step();
            PunishmentRequest request = request(subject, template, step, AdminContext.actor(source), note.trim());
            if (step.type() == PunishmentType.KICK && subject.online() == null) {
                context.send(source, KEY + "kick-offline", Placeholder.unparsed("player", subject.name()));
            }
            issue(source, subject, request, outcome);
        }).exceptionally(error -> failed(source, error));
    }

    private void withSubject(CommandSource source, String input, Consumer<Subject> action) {
        resolve(input).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", input));
                return;
            }
            Subject subject = found.get();
            if (source instanceof Player self && self.getUniqueId().equals(subject.uuid())) {
                context.send(source, KEY + "self");
                return;
            }
            if (source instanceof Player && subject.online() != null && subject.online().hasPermission(EXEMPT)) {
                context.send(source, KEY + "exempt", Placeholder.unparsed("player", subject.name()));
                return;
            }
            action.accept(subject);
        }).exceptionally(error -> failed(source, error));
    }

    private CompletableFuture<Optional<Punishment>> issue(CommandSource source, Subject subject,
                                                          PunishmentRequest request, Escalation.Outcome outcome) {
        CompletableFuture<Optional<Punishment>> current = request.type().lasting()
                ? context.services().punishments().active(subject.uuid(), request.type())
                : CompletableFuture.<Optional<Punishment>>completedFuture(Optional.empty());
        CompletableFuture<Optional<Punishment>> result = current.thenCompose(active -> {
            if (active.isPresent() && outlasts(active.get(), request)) {
                context.send(source, KEY + "already-longer", resolvers(source, active.get(), false));
                return CompletableFuture.<Optional<Punishment>>completedFuture(Optional.empty());
            }
            return context.services().punishments().punish(request).thenApply(punishment -> {
                issued(source, subject, punishment, outcome, active.orElse(null));
                return Optional.of(punishment);
            });
        });
        result.whenComplete((issued, error) -> {
            if (error != null) {
                failed(source, error);
            }
        });
        return result;
    }

    static boolean outlasts(Punishment active, PunishmentRequest request) {
        if (active.addressBound() && request.addressHash().isEmpty()) {
            return true;
        }
        if (request.duration() == null) {
            return false;
        }
        if (active.permanent()) {
            return true;
        }
        return active.expiresAt() > System.currentTimeMillis() + request.duration().toMillis();
    }

    private void issued(CommandSource source, Subject subject, Punishment punishment, Escalation.Outcome outcome,
                        Punishment replaced) {
        PunishmentKind kind = PunishmentKind.of(punishment);
        enforce(punishment);
        context.services().audit().record(punishment.actor(), kind.key(), punishment.targetName(),
                auditDetails(punishment, outcome));
        Component step = Component.empty();
        String nick = subject.online() == null ? null : nicks.shownNick(subject.online()).orElse(null);
        for (CommandSource staff : staffAudience(source)) {
            Map<String, TagResolver> placeholders = placeholders(staff, punishment, false);
            TagResolver[] hover = array(placeholders);
            placeholders.put("player", Placeholder.component("player",
                    staffName(staff, punishment.targetName(), nick)));
            placeholders.put("step", Placeholder.component("step", outcome == null ? step
                    : context.messages().render(staff, KEY + "staff.step",
                    Placeholder.unparsed("level", String.valueOf(outcome.level())),
                    Placeholder.unparsed("levels", String.valueOf(outcome.levels())),
                    Placeholder.unparsed("offence", String.valueOf(outcome.offence())))));
            placeholders.put("replaced", Placeholder.component("replaced", replaced == null ? Component.empty()
                    : context.messages().render(staff, KEY + "staff.replaced",
                    Placeholder.unparsed("old", replaced.id()))));
            staff.sendMessage(context.messages().render(staff, KEY + "staff.issued", array(placeholders))
                    .clickEvent(ClickEvent.runCommand("/check " + punishment.targetName()))
                    .hoverEvent(HoverEvent.showText(context.messages().render(staff, KEY + "staff.issued-hover",
                            hover))));
        }
        broadcast(subject, punishment, kind, nick);
    }

    private List<CommandSource> staffAudience(CommandSource source) {
        List<CommandSource> audience = new ArrayList<>();
        for (Player player : context.proxy().getAllPlayers()) {
            if (player.hasPermission(StaffCommands.STAFF) || player.equals(source)) {
                audience.add(player);
            }
        }
        audience.add(context.proxy().getConsoleCommandSource());
        return audience;
    }

    private Component staffName(CommandSource viewer, String name, String nick) {
        if (nick == null || nick.equalsIgnoreCase(name)) {
            return Component.text(name);
        }
        return context.messages().render(viewer, KEY + "staff.nicked", Placeholder.unparsed("player", name),
                Placeholder.unparsed("nick", nick));
    }

    private String auditDetails(Punishment punishment, Escalation.Outcome outcome) {
        StringBuilder details = new StringBuilder(punishment.reason());
        if (punishment.type().lasting()) {
            details.append(" (").append(punishment.permanent() ? "permanent"
                    : DurationText.compact(punishment.length())).append(")");
        }
        if (outcome != null) {
            details.append(" [").append(outcome.template().id()).append(" ").append(outcome.level()).append("/")
                    .append(outcome.levels()).append("]");
        }
        return details.append(" #").append(punishment.id()).toString();
    }

    void enforce(Punishment punishment) {
        Optional<Player> online = context.proxy().getPlayer(punishment.target());
        switch (punishment.type()) {
            case BAN -> {
                online.ifPresent(player -> player.disconnect(screen(player, punishment)));
                if (punishment.addressBound()) {
                    for (Player player : context.proxy().getAllPlayers()) {
                        if (!player.getUniqueId().equals(punishment.target()) && !player.hasPermission(IP_BYPASS)
                                && addresses.of(player.getRemoteAddress()).map(punishment.addressHash()::equals)
                                .orElse(false)) {
                            player.disconnect(screen(player, punishment));
                        }
                    }
                }
            }
            case KICK -> online.ifPresent(player -> player.disconnect(context.messages().render(player,
                    KEY + "screen.kick", resolvers(player, punishment, true))));
            case MUTE -> online.ifPresent(player -> {
                muteOn(player, punishment);
                context.send(player, KEY + "notice." + PunishmentKind.of(punishment).key(),
                        resolvers(player, punishment, true));
            });
            case WARN -> online.ifPresent(player -> context.send(player, KEY + "notice.warn",
                    resolvers(player, punishment, true)));
        }
    }

    void muteOn(Player player, Punishment punishment) {
        context.channel().sendMute(player, punishment.expiresAt(), reason(player, punishment), punishment.id(),
                settings.appealUrl());
    }

    Component screen(Player viewer, Punishment punishment) {
        return context.messages().render(viewer, KEY + "screen." + PunishmentKind.of(punishment).key(),
                resolvers(viewer, punishment, true));
    }

    private void broadcast(Subject subject, Punishment punishment, PunishmentKind kind, String nick) {
        if (!settings.broadcast().covers(kind)) {
            return;
        }
        if (subject.online() != null && context.vanish().vanished(subject.online().getUniqueId())) {
            return;
        }
        String shown = nick == null ? punishment.targetName() : nick;
        for (Player player : context.proxy().getAllPlayers()) {
            if (player.hasPermission(StaffCommands.STAFF) || player.getUniqueId().equals(punishment.target())) {
                continue;
            }
            Map<String, TagResolver> placeholders = placeholders(player, punishment, true);
            placeholders.put("player", Placeholder.unparsed("player", shown));
            context.send(player, KEY + "public." + kind.key(), array(placeholders));
        }
    }

    void revoke(CommandSource source, String input, PunishmentType type, String why) {
        resolve(input).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", input));
                return;
            }
            Subject subject = found.get();
            String actor = AdminContext.actor(source);
            context.services().punishments().revoke(subject.uuid(), type, actor, why).thenAccept(revoked -> {
                if (revoked.isEmpty()) {
                    context.send(source, KEY + "nothing-active", Placeholder.unparsed("player", subject.name()),
                            Placeholder.unparsed("type", typeName(source, type == PunishmentType.BAN
                                    ? PunishmentKind.BAN : PunishmentKind.MUTE)));
                    return;
                }
                Punishment punishment = revoked.get();
                if (type == PunishmentType.MUTE) {
                    context.proxy().getPlayer(subject.uuid()).ifPresent(player -> {
                        context.channel().sendUnmute(player);
                        context.send(player, KEY + "notice.unmuted");
                    });
                }
                String action = type == PunishmentType.BAN ? "unban" : "unmute";
                context.services().audit().record(actor, action, subject.name(),
                        (why.isBlank() ? "" : why + " ") + "#" + punishment.id());
                for (CommandSource staff : staffAudience(source)) {
                    Map<String, TagResolver> placeholders = placeholders(staff, punishment, false);
                    placeholders.put("actor", Placeholder.unparsed("actor", actor));
                    placeholders.put("note", Placeholder.component("note", why.isBlank() ? Component.empty()
                            : context.messages().render(staff, KEY + "staff.revoked-note",
                            Placeholder.unparsed("note", why))));
                    context.send(staff, KEY + "staff.revoked", array(placeholders));
                }
            }).exceptionally(error -> failed(source, error));
        }).exceptionally(error -> failed(source, error));
    }

    public CompletableFuture<Optional<Component>> loginBlock(Player player) {
        return context.services().punishments().active(player.getUniqueId(), PunishmentType.BAN)
                .thenCompose(ban -> {
                    if (ban.isPresent()) {
                        return CompletableFuture.<Optional<Component>>completedFuture(
                                Optional.of(screen(player, ban.get())));
                    }
                    if (!settings.ipBans() || player.hasPermission(IP_BYPASS)) {
                        return CompletableFuture.<Optional<Component>>completedFuture(Optional.empty());
                    }
                    Optional<String> hash = addresses.of(player.getRemoteAddress());
                    if (hash.isEmpty()) {
                        return CompletableFuture.<Optional<Component>>completedFuture(Optional.empty());
                    }
                    return context.services().punishments().activeForAddress(hash.get()).thenApply(found -> {
                        found.ifPresent(punishment -> context.notifyStaff(StaffCommands.STAFF,
                                KEY + "staff.address-blocked", Placeholder.unparsed("player", player.getUsername()),
                                Placeholder.unparsed("target", punishment.targetName()),
                                Placeholder.unparsed("id", punishment.id())));
                        return found.map(punishment -> screen(player, punishment));
                    });
                });
    }

    void check(CommandSource source, String input) {
        resolve(input).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", input));
                return;
            }
            Subject subject = found.get();
            CompletableFuture<Optional<List<AltAccount>>> alts = AltCommands.lookup(subject.uuid());
            context.services().punishments().history(subject.uuid())
                    .thenCombine(context.services().reports().openAgainst(subject.uuid())
                            .exceptionally(error -> List.of()), Map::entry)
                    .thenCombine(alts, (record, accounts) -> {
                        sendCheck(source, subject, record.getKey(), record.getValue(), accounts);
                        return null;
                    }).exceptionally(error -> failed(source, error));
        }).exceptionally(error -> failed(source, error));
    }

    private void sendCheck(CommandSource source, Subject subject, List<Punishment> history, List<Report> reports,
                           Optional<List<AltAccount>> alts) {
        long now = now();
        boolean visible = subject.online() != null && context.vanish().canSee(source, subject.online());
        context.send(source, KEY + "check.header", Placeholder.unparsed("player", subject.name()),
                Placeholder.component("status", visible
                        ? context.messages().render(source, KEY + "check.online",
                        Placeholder.unparsed("server", StaffCommands.serverOf(subject.online())))
                        : context.messages().render(source, KEY + "check.offline")));
        for (PunishmentType type : List.of(PunishmentType.BAN, PunishmentType.MUTE)) {
            Optional<Punishment> active = history.stream().filter(entry -> entry.type() == type && entry.inForce(now))
                    .max(Comparator.comparingLong(Punishment::createdAt));
            String name = type == PunishmentType.BAN ? "ban" : "mute";
            if (active.isPresent()) {
                context.send(source, KEY + "check.active", resolvers(source, active.get(), false));
            } else {
                context.send(source, KEY + "check.no-" + name);
            }
        }
        long warnings = history.stream().filter(entry -> entry.type() == PunishmentType.WARN).count();
        long kicks = history.stream().filter(entry -> entry.type() == PunishmentType.KICK).count();
        context.send(source, KEY + "check.counts", Placeholder.unparsed("warnings", String.valueOf(warnings)),
                Placeholder.unparsed("kicks", String.valueOf(kicks)),
                Placeholder.unparsed("total", String.valueOf(history.size())),
                Placeholder.unparsed("reports", String.valueOf(reports.size())));
        Locale locale = context.messages().localeOf(source);
        for (PunishmentTemplate template : templates()) {
            Escalation.Outcome outcome = Escalation.next(template, history, now, settings.countLifted());
            if (outcome.previous() == 0) {
                continue;
            }
            context.send(source, KEY + "check.offences", Placeholder.unparsed("template", template.name(locale)),
                    Placeholder.unparsed("count", String.valueOf(outcome.previous())),
                    Placeholder.unparsed("next", stepText(source, outcome.step())));
        }
        alts.ifPresent(accounts -> context.send(source, KEY + "check.alts", Placeholder.component("alts",
                AltCommands.names(context, source, accounts))));
        source.sendMessage(context.messages().render(source, KEY + "check.footer",
                        Placeholder.unparsed("player", subject.name()))
                .clickEvent(ClickEvent.runCommand("/history " + subject.name())));
    }

    void history(CommandSource source, String input, int page) {
        resolve(input).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", input));
                return;
            }
            Subject subject = found.get();
            context.services().punishments().history(subject.uuid()).thenAccept(entries -> {
                int pages = Math.max(1, (entries.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE);
                int shown = Math.max(1, Math.min(page, pages));
                context.send(source, KEY + "history.header", Placeholder.unparsed("player", subject.name()),
                        Placeholder.unparsed("count", String.valueOf(entries.size())),
                        Placeholder.unparsed("page", String.valueOf(shown)),
                        Placeholder.unparsed("pages", String.valueOf(pages)));
                if (entries.isEmpty()) {
                    context.send(source, KEY + "history.empty");
                    return;
                }
                long now = now();
                entries.stream().skip((long) (shown - 1) * HISTORY_PAGE_SIZE).limit(HISTORY_PAGE_SIZE)
                        .forEach(entry -> {
                            Map<String, TagResolver> placeholders = placeholders(source, entry, false);
                            placeholders.put("by", Placeholder.unparsed("by", entry.revokedBy()));
                            placeholders.put("successor", Placeholder.unparsed("successor", entry.replacedBy()));
                            placeholders.put("note", Placeholder.unparsed("note", entry.revokeReason()));
                            context.send(source, KEY + "history." + state(entry, now), array(placeholders));
                        });
                if (pages > 1) {
                    source.sendMessage(pager(source, subject.name(), shown, pages));
                }
            }).exceptionally(error -> failed(source, error));
        }).exceptionally(error -> failed(source, error));
    }

    static String state(Punishment entry, long now) {
        if (!entry.type().lasting()) {
            return "entry";
        }
        if (entry.inForce(now)) {
            return "entry-active";
        }
        if (entry.replaced()) {
            return "entry-replaced";
        }
        if (entry.lifted()) {
            return "entry-lifted";
        }
        return "entry-expired";
    }

    private Component pager(CommandSource source, String player, int page, int pages) {
        Component line = Component.text(" ");
        if (page > 1) {
            line = line.append(context.messages().render(source, KEY + "history.previous")
                    .clickEvent(ClickEvent.runCommand("/history " + player + " " + (page - 1))));
            line = line.append(Component.text(" "));
        }
        if (page < pages) {
            line = line.append(context.messages().render(source, KEY + "history.next")
                    .clickEvent(ClickEvent.runCommand("/history " + player + " " + (page + 1))));
        }
        return line;
    }

    void menu(CommandSource source, String input) {
        List<PunishmentTemplate> templates = usableTemplates(source).stream().limit(MENU_LIMIT).toList();
        if (templates.isEmpty()) {
            context.send(source, KEY + "no-templates");
            return;
        }
        withSubject(source, input, subject -> context.services().punishments().history(subject.uuid())
                .thenCombine(context.services().reports().openAgainst(subject.uuid())
                        .exceptionally(error -> List.of()), (history, reports) -> {
                    showMenu(source, subject, templates, history, reports.size());
                    return null;
                }).exceptionally(error -> failed(source, error)));
    }

    private void showMenu(CommandSource source, Subject subject, List<PunishmentTemplate> templates,
                          List<Punishment> history, int reports) {
        long now = now();
        Locale locale = context.messages().localeOf(source);
        MiniMessage mini = MiniMessage.miniMessage();
        if (source instanceof Player staff && context.messages().players().prismarinBackend(staff)) {
            List<ModerationChannel.PunishMenuEntry> entries = new ArrayList<>();
            Set<String> ids = new LinkedHashSet<>();
            for (PunishmentTemplate template : templates) {
                Escalation.Outcome outcome = Escalation.next(template, history, now, settings.countLifted());
                List<Component> lore = new ArrayList<>();
                lore.add(context.messages().render(staff, KEY + "menu.next",
                        Placeholder.unparsed("next", stepText(staff, outcome.step()))));
                lore.add(context.messages().render(staff, KEY + "menu.previous",
                        Placeholder.unparsed("count", String.valueOf(outcome.previous()))));
                String description = template.description(locale);
                if (!description.isBlank()) {
                    lore.add(context.messages().render(staff, KEY + "menu.description",
                            Placeholder.component("description",
                                    mini.deserialize(LegacyColors.toMiniMessage(description)))));
                }
                lore.add(Component.empty());
                lore.add(context.messages().render(staff, KEY + "menu.click",
                        Placeholder.unparsed("player", subject.name())));
                entries.add(new ModerationChannel.PunishMenuEntry(template.id(), template.icon(),
                        mini.serialize(context.messages().render(staff, KEY + "menu.template",
                                Placeholder.unparsed("template", template.name(locale)))), wrapped(lore)));
                ids.add(template.id());
            }
            List<Component> headLore = new ArrayList<>();
            for (PunishmentType type : List.of(PunishmentType.BAN, PunishmentType.MUTE)) {
                history.stream().filter(entry -> entry.type() == type && entry.inForce(now))
                        .max(Comparator.comparingLong(Punishment::createdAt))
                        .ifPresent(active -> headLore.add(context.messages().render(staff,
                                KEY + "menu.active", resolvers(staff, active, false))));
            }
            headLore.add(context.messages().render(staff, KEY + "menu.record",
                    Placeholder.unparsed("total", String.valueOf(history.size())),
                    Placeholder.unparsed("reports", String.valueOf(reports))));
            menus.put(staff.getUniqueId(), new Pending(subject.uuid(), subject.name(), Set.copyOf(ids),
                    System.currentTimeMillis() + MENU_TIMEOUT_MILLIS));
            context.channel().sendPunishMenu(staff, subject.uuid(), subject.name(),
                    mini.serialize(context.messages().render(staff, KEY + "menu.title",
                            Placeholder.unparsed("player", subject.name()))),
                    mini.serialize(context.messages().render(staff, KEY + "menu.target",
                            Placeholder.unparsed("player", subject.name()))),
                    wrapped(headLore), entries);
            return;
        }
        context.send(source, KEY + "menu.chat-header", Placeholder.unparsed("player", subject.name()),
                Placeholder.unparsed("total", String.valueOf(history.size())),
                Placeholder.unparsed("reports", String.valueOf(reports)));
        for (PunishmentTemplate template : templates) {
            Escalation.Outcome outcome = Escalation.next(template, history, now, settings.countLifted());
            source.sendMessage(context.messages().render(source, KEY + "menu.chat-entry",
                            Placeholder.unparsed("template", template.name(locale)),
                            Placeholder.unparsed("id", template.id()),
                            Placeholder.unparsed("next", stepText(source, outcome.step())),
                            Placeholder.unparsed("count", String.valueOf(outcome.previous())))
                    .clickEvent(ClickEvent.suggestCommand("/punish " + subject.name() + " " + template.id()))
                    .hoverEvent(HoverEvent.showText(ladder(source, outcome))));
        }
    }

    private static List<String> wrapped(List<Component> lore) {
        MiniMessage mini = MiniMessage.miniMessage();
        return LoreWrap.wrapAll(lore, LoreWrap.WIDTH).stream().map(mini::serialize).toList();
    }

    private Component ladder(CommandSource viewer, Escalation.Outcome outcome) {
        Component line = context.messages().render(viewer, KEY + "menu.ladder");
        List<PunishmentTemplate.Step> steps = outcome.template().steps();
        for (int index = 0; index < steps.size(); index++) {
            if (index > 0) {
                line = line.append(context.messages().render(viewer, KEY + "menu.ladder-separator"));
            }
            line = line.append(context.messages().render(viewer, index == outcome.stepIndex()
                    ? KEY + "menu.ladder-current" : KEY + "menu.ladder-step",
                    Placeholder.unparsed("step", stepText(viewer, steps.get(index)))));
        }
        return line;
    }

    void chosen(Player staff, UUID target, String templateId) {
        Pending request = menus.remove(staff.getUniqueId());
        if (request == null || !request.target().equals(target) || request.expires() < System.currentTimeMillis()
                || !request.templates().contains(templateId.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (!staff.hasPermission(PUNISH)) {
            context.send(staff, "general.no-permission");
            return;
        }
        Optional<PunishmentTemplate> template = template(templateId);
        if (template.isEmpty() || !mayUse(staff, template.get())) {
            return;
        }
        Subject subject = new Subject(target, request.targetName(), context.proxy().getPlayer(target).orElse(null));
        if (subject.online() != null && subject.online().hasPermission(EXEMPT)) {
            context.send(staff, KEY + "exempt", Placeholder.unparsed("player", subject.name()));
            return;
        }
        applyTemplate(staff, subject, template.get(), "");
    }

    String stepText(CommandSource viewer, PunishmentTemplate.Step step) {
        PunishmentKind kind = step.kind();
        if (!step.type().lasting()) {
            return typeName(viewer, kind);
        }
        return context.messages().locale().raw(KEY + "step", context.messages().localeOf(viewer))
                .replace("%type%", typeName(viewer, kind))
                .replace("%duration%", step.permanent() ? raw(viewer, "permanent")
                        : duration(viewer, step.duration().toMillis()));
    }

    String typeName(CommandSource viewer, PunishmentKind kind) {
        return raw(viewer, "type." + kind.key());
    }

    String reason(CommandSource viewer, Punishment punishment) {
        Locale locale = context.messages().localeOf(viewer);
        Optional<PunishmentTemplate> template = punishment.fromTemplate()
                ? template(punishment.template()) : Optional.empty();
        if (template.isPresent()) {
            String name = template.get().name(locale);
            return punishment.note().isBlank() ? name : name + ": " + punishment.note();
        }
        return punishment.reason().isBlank() ? raw(viewer, "no-reason") : punishment.reason();
    }

    TagResolver[] resolvers(CommandSource viewer, Punishment punishment, boolean playerFacing) {
        return array(placeholders(viewer, punishment, playerFacing));
    }

    static TagResolver[] array(Map<String, TagResolver> placeholders) {
        return placeholders.values().toArray(TagResolver[]::new);
    }

    Map<String, TagResolver> placeholders(CommandSource viewer, Punishment punishment, boolean playerFacing) {
        long now = now();
        String appeal = settings.appealUrl();
        Component appealLine = appeal.isEmpty() ? Component.empty()
                : context.messages().render(viewer, KEY + "appeal-line", Placeholder.unparsed("appeal", appeal));
        Optional<PunishmentTemplate> template = punishment.fromTemplate()
                ? template(punishment.template()) : Optional.empty();
        Map<String, TagResolver> placeholders = new LinkedHashMap<>();
        put(placeholders, "player", punishment.targetName());
        put(placeholders, "type", typeName(viewer, PunishmentKind.of(punishment)));
        put(placeholders, "reason", reason(viewer, punishment));
        put(placeholders, "actor", playerFacing ? shownActor(viewer, punishment) : punishment.actor());
        put(placeholders, "id", punishment.id());
        put(placeholders, "date", date(viewer, punishment.createdAt()));
        boolean lasting = punishment.type().lasting();
        String once = lasting ? "" : raw(viewer, "not-lasting");
        put(placeholders, "duration", !lasting ? once : punishment.permanent() ? raw(viewer, "permanent")
                : duration(viewer, punishment.length()));
        put(placeholders, "remaining", !lasting ? once : punishment.permanent() ? raw(viewer, "permanent")
                : duration(viewer, punishment.expiresAt() - now));
        put(placeholders, "expires", !lasting ? once : punishment.permanent() ? raw(viewer, "never")
                : date(viewer, punishment.expiresAt()));
        put(placeholders, "appeal", appeal);
        placeholders.put("appeal_line", Placeholder.component("appeal_line", appealLine));
        put(placeholders, "template", template.map(value -> value.name(context.messages().localeOf(viewer)))
                .orElse(""));
        return placeholders;
    }

    private static void put(Map<String, TagResolver> placeholders, String name, String value) {
        placeholders.put(name, Placeholder.unparsed(name, value));
    }

    private String shownActor(CommandSource viewer, Punishment punishment) {
        if (!settings.showActor()) {
            return raw(viewer, "team");
        }
        Optional<Player> actor = context.proxy().getPlayer(punishment.actor());
        if (actor.isEmpty() || context.vanish().vanished(actor.get().getUniqueId())
                || nicks.shownNick(actor.get()).isPresent()) {
            return raw(viewer, "team");
        }
        return actor.get().getUsername();
    }

    private String duration(CommandSource viewer, long millis) {
        Locale locale = context.messages().localeOf(viewer);
        return DurationText.format(millis, key -> context.messages().locale().raw(KEY + "time." + key, locale));
    }

    private String date(CommandSource viewer, long millis) {
        String pattern = raw(viewer, "date-format");
        DateTimeFormatter formatter = formats.computeIfAbsent(pattern, value -> {
            try {
                return DateTimeFormatter.ofPattern(value).withZone(settings.zone());
            } catch (IllegalArgumentException e) {
                return DateTimeFormatter.ofPattern(FALLBACK_DATE).withZone(settings.zone());
            }
        });
        return formatter.format(Instant.ofEpochMilli(millis));
    }

    private String raw(CommandSource viewer, String key) {
        return context.messages().locale().raw(KEY + key, context.messages().localeOf(viewer));
    }

    private Void failed(CommandSource source, Throwable error) {
        LOGGER.warn("Punishment command failed", error);
        context.send(source, KEY + "failed");
        return null;
    }

    static long now() {
        return System.currentTimeMillis();
    }
}
