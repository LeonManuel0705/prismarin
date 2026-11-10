package net.prismarin.velocity.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.ReportAction;
import net.prismarin.shared.admin.Report;
import net.prismarin.shared.admin.ReportService;
import net.prismarin.velocity.social.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReportsMenu {

    static final int[] CASE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private static final Logger LOGGER = LoggerFactory.getLogger("PrismarinCore");
    private static final int ROWS = 6;
    private static final long SESSION_MILLIS = 900_000L;
    private static final int CHAT_PREVIEW = 6;
    private static final int REPORTER_LINES = 6;
    private static final String KEY = "reports.menu.";
    private static final View START = new View(ReportBoard.Filter.ALL, ReportBoard.Sort.URGENT, 0);

    private record View(ReportBoard.Filter filter, ReportBoard.Sort sort, int page) {
    }

    private record Session(int id, Set<String> actions, View view, long expires) {
    }

    private record Button(String icon, Component name, List<Component> lore, String action, boolean close) {
    }

    private final class Layout {

        private final JsonArray items = new JsonArray();
        private final Set<String> actions = new HashSet<>();
        private final Player viewer;

        private Layout(Player viewer) {
            this.viewer = viewer;
        }

        private JsonObject item(int slot, String icon, Component name, List<Component> lore, String action,
                                boolean close) {
            JsonObject item = new JsonObject();
            item.addProperty("slot", slot);
            item.addProperty("icon", icon);
            item.addProperty("name", mini.serialize(name));
            JsonArray lines = new JsonArray();
            LoreWrap.wrapAll(lore, LoreWrap.WIDTH).forEach(line -> lines.add(mini.serialize(line)));
            item.add("lore", lines);
            if (action != null) {
                item.addProperty("action", action);
                actions.add(action);
            }
            item.addProperty("close", close);
            items.add(item);
            return item;
        }

        private Component text(String key, TagResolver... resolvers) {
            return context.messages().render(viewer, KEY + key, resolvers);
        }
    }

    private final AdminContext context;
    private final ReportFlow flow;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    ReportsMenu(AdminContext context, ReportFlow flow) {
        this.context = context;
        this.flow = flow;
        context.channel().handleReportsMenu(this::click);
    }

    boolean available(Player player) {
        return context.messages().players().prismarinBackend(player);
    }

    void open(Player staff) {
        list(staff, views.getOrDefault(staff.getUniqueId(), START));
    }

    void forget(UUID player) {
        sessions.remove(player);
        views.remove(player);
    }

    void click(Player staff, int session, String action) {
        Session current = sessions.get(staff.getUniqueId());
        if (current == null || current.id() != session || current.expires() < System.currentTimeMillis()
                || !current.actions().contains(action)) {
            return;
        }
        if (!staff.hasPermission(ReportFlow.STAFF_REPORTS)) {
            context.send(staff, "general.no-permission");
            return;
        }
        int colon = action.indexOf(':');
        String verb = colon < 0 ? action : action.substring(0, colon);
        String id = colon < 0 ? "" : action.substring(colon + 1);
        View view = current.view();
        switch (verb) {
            case "page" -> list(staff, new View(view.filter(), view.sort(), PunishmentCommands.page(id) - 1));
            case "filter" -> list(staff, new View(view.filter().next(), view.sort(), 0));
            case "sort" -> list(staff, new View(view.filter(), view.sort().next(), 0));
            case "refresh", "back" -> list(staff, view);
            case "open" -> detail(staff, id, view, false);
            case "claim" -> detail(staff, id, view, true);
            case "chat" -> flow.showChat(staff, id);
            case "goto" -> run(staff, id, "goto");
            case "record" -> run(staff, id, "pinfo");
            case "punish" -> run(staff, id, "punish");
            case "run" -> {
                int split = id.indexOf(':');
                if (split > 0) {
                    flow.runAction(staff, id.substring(0, split), id.substring(split + 1));
                }
            }
            case "close" -> flow.close(staff, id, false).thenRun(() -> list(staff, view));
            case "reject" -> flow.close(staff, id, true).thenRun(() -> list(staff, view));
            default -> {
            }
        }
    }

    private void run(Player staff, String id, String command) {
        flow.touch(staff, id, false).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(staff, "reports.unknown", Placeholder.unparsed("id", id));
                return;
            }
            context.proxy().getCommandManager().executeAsync(staff, command + " " + found.get().report().targetName());
        }).exceptionally(error -> failed(staff, error));
    }

    private void list(Player staff, View view) {
        context.services().reports().open().thenAccept(open -> {
            long now = System.currentTimeMillis();
            long claim = flow.claimMillis();
            int urgentFrom = context.settings().reportBehaviour().priorityReporters();
            List<Report> arranged = ReportBoard.arrange(open, view.filter(), view.sort(),
                    AdminContext.actor(staff), now, claim);
            Page<Report> page = ReportBoard.page(arranged, view.page(), CASE_SLOTS.length);
            View shown = new View(view.filter(), view.sort(), page.page());
            views.put(staff.getUniqueId(), shown);
            ReportBoard.Counts counts = ReportBoard.count(open, now, claim, urgentFrom);
            Layout layout = new Layout(staff);
            layout.item(4, "BOOK", layout.text("summary", count("count", counts.open())),
                    List.of(layout.text("summary-urgent", count("count", counts.urgent())),
                            layout.text("summary-progress", count("count", counts.progress())),
                            layout.text("summary-free", count("count", counts.free())),
                            layout.text("summary-page", count("page", page.page() + 1),
                                    count("pages", page.pages())),
                            Component.empty(),
                            layout.text("refresh-hint")), "refresh", false);
            for (int index = 0; index < page.entries().size(); index++) {
                caseItem(layout, CASE_SLOTS[index], page.entries().get(index), now, urgentFrom);
            }
            if (page.entries().isEmpty()) {
                layout.item(22, "LIME_DYE", layout.text(view.filter() == ReportBoard.Filter.ALL
                        ? "empty" : "empty-filtered"), List.of(layout.text("empty-lore")), null, false);
            }
            if (page.page() > 0) {
                layout.item(45, "ARROW", layout.text("previous", count("page", page.page())), List.of(),
                        "page:" + page.page(), false);
            }
            layout.item(47, "COMPARATOR", layout.text("sort", Placeholder.component("sort",
                    layout.text("sort-" + shown.sort().key()))), List.of(layout.text("sort-hint",
                    Placeholder.component("next", layout.text("sort-" + shown.sort().next().key())))),
                    "sort", false);
            layout.item(49, "BARRIER", layout.text("close"), List.of(), null, true);
            layout.item(51, "HOPPER", layout.text("filter", Placeholder.component("filter",
                    layout.text("filter-" + shown.filter().key()))), List.of(layout.text("filter-hint",
                    Placeholder.component("next", layout.text("filter-" + shown.filter().next().key())))),
                    "filter", false);
            if (page.page() + 1 < page.pages()) {
                layout.item(53, "ARROW", layout.text("next", count("page", page.page() + 2)), List.of(),
                        "page:" + (page.page() + 2), false);
            }
            show(staff, layout.text("title", count("count", counts.open())), layout, shown);
        }).exceptionally(error -> failed(staff, error));
    }

    private void caseItem(Layout layout, int slot, Report report, long now, int urgentFrom) {
        Player staff = layout.viewer;
        boolean urgent = ReportBoard.urgent(report, urgentFrom);
        List<Component> lore = new ArrayList<>();
        lore.add(layout.text("reasons", Placeholder.component("reasons", flow.reasons(staff, report))));
        lore.add(layout.text("reporters", count("count", report.reporters()),
                Placeholder.unparsed("reporter", report.reporterName())));
        lore.add(layout.text("server", Placeholder.unparsed("server", report.server())));
        lore.add(layout.text("age", Placeholder.unparsed("age", flow.duration(staff, now - report.createdAt()))));
        lore.add(report.chat().isEmpty() ? layout.text("chat-no")
                : layout.text("chat-yes", count("count", report.chat().size())));
        lore.add(Component.empty());
        lore.add(flow.status(staff, report, now));
        lore.add(layout.text("open-hint"));
        JsonObject item = layout.item(slot, "PLAYER_HEAD", layout.text(urgent ? "case-urgent" : "case",
                Placeholder.unparsed("player", report.targetName()), Placeholder.unparsed("id", report.id())),
                lore, "open:" + report.id(), false);
        item.addProperty("skull", report.target().toString());
        item.addProperty("amount", Math.max(1, Math.min(64, report.reporters())));
        item.addProperty("glow", urgent);
    }

    private void detail(Player staff, String id, View back, boolean force) {
        flow.touch(staff, id, force).thenAccept(found -> {
            if (found.isEmpty()) {
                context.send(staff, "reports.unknown", Placeholder.unparsed("id", id));
                list(staff, back);
                return;
            }
            Report report = found.get().report();
            if (force && found.get().outcome() == ReportService.ClaimOutcome.CLAIMED) {
                context.notifyStaff(ReportFlow.STAFF_REPORTS, "reports.claimed",
                        Placeholder.unparsed("staff", AdminContext.actor(staff)),
                        Placeholder.unparsed("id", id),
                        Placeholder.unparsed("player", report.targetName()));
            }
            context.services().punishments().history(report.target())
                    .exceptionally(error -> List.of())
                    .thenCombine(context.services().reports().countAgainst(report.target())
                            .exceptionally(error -> 0L), (history, total) -> {
                        showDetail(staff, report, history, total, back);
                        return null;
                    }).exceptionally(error -> failed(staff, error));
        }).exceptionally(error -> failed(staff, error));
    }

    private void showDetail(Player staff, Report report, List<Punishment> history, long total, View back) {
        long now = System.currentTimeMillis();
        String me = AdminContext.actor(staff);
        Optional<String> holder = report.holder(now, flow.claimMillis());
        Optional<Player> online = context.proxy().getPlayer(report.target())
                .filter(player -> context.vanish().canSee(staff, player));
        Layout layout = new Layout(staff);
        String id = report.id();

        List<Component> head = new ArrayList<>();
        if (!report.shownName().equalsIgnoreCase(report.targetName())) {
            head.add(layout.text("nick", Placeholder.unparsed("nick", report.shownName())));
        }
        head.add(online.map(player -> layout.text("online", Placeholder.unparsed("server",
                StaffCommands.serverOf(player)))).orElseGet(() -> layout.text("offline")));
        String record = ReportFlow.record(history);
        head.add(layout.text("record", Placeholder.component("record", record.isEmpty()
                ? context.messages().render(staff, "reports.record-clean") : Component.text(record))));
        head.add(layout.text("total", count("count", total)));
        if (!report.context().isBlank()) {
            head.add(layout.text("client", Placeholder.unparsed("client", report.context())));
        }
        JsonObject target = layout.item(4, "PLAYER_HEAD", layout.text("target",
                Placeholder.unparsed("player", report.targetName())), head, null, false);
        target.addProperty("skull", report.target().toString());

        List<Component> reporters = new ArrayList<>();
        reporters.add(reporterLine(layout, report.reporterName(), report.reason(), now - report.createdAt()));
        for (Report.Supporter supporter : report.supporters()) {
            if (reporters.size() >= REPORTER_LINES) {
                break;
            }
            reporters.add(reporterLine(layout, supporter.name(), supporter.reason(), now - supporter.at()));
        }
        if (report.reporters() > reporters.size()) {
            reporters.add(layout.text("reporter-more", count("count", report.reporters() - reporters.size())));
        }
        reporters.add(layout.text("server", Placeholder.unparsed("server", report.server())));
        layout.item(11, "PAPER", layout.text("reports-title", count("count", report.reporters())), reporters,
                null, false).addProperty("amount", Math.max(1, Math.min(64, report.reporters())));

        List<Component> status = new ArrayList<>();
        int minutes = context.settings().reportBehaviour().claimMinutes();
        status.add(minutes > 0 ? layout.text("status-timeout", count("minutes", minutes))
                : layout.text("status-forever"));
        String claimAction = null;
        if (holder.isEmpty() || !holder.get().equalsIgnoreCase(me)) {
            status.add(Component.empty());
            status.add(layout.text(holder.isEmpty() ? "claim-hint" : "take-over-hint"));
            claimAction = "claim:" + id;
        }
        layout.item(13, "CLOCK", flow.status(staff, report, now), status, claimAction, false)
                .addProperty("glow", holder.map(me::equalsIgnoreCase).orElse(false));

        if (report.chat().isEmpty()) {
            layout.item(15, "BOOK", layout.text("chat-none"), List.of(layout.text("chat-none-lore")), null, false);
        } else {
            List<Component> chat = new ArrayList<>();
            List<String> lines = report.chat();
            for (String line : lines.subList(Math.max(0, lines.size() - CHAT_PREVIEW), lines.size())) {
                boolean reported = line.startsWith("> ");
                chat.add(layout.text(reported ? "chat-line-reported" : "chat-line",
                        Placeholder.unparsed("line", line.length() > 2 ? line.substring(2) : line)));
            }
            chat.add(Component.empty());
            chat.add(layout.text("chat-hint"));
            layout.item(15, "WRITABLE_BOOK", layout.text("chat-title", count("count", lines.size())), chat,
                    "chat:" + id, true);
        }

        List<Button> buttons = new ArrayList<>();
        if (hasCommand("goto")) {
            buttons.add(online.isPresent()
                    ? new Button("ENDER_PEARL", layout.text("goto"), List.of(
                            context.messages().render(staff, "reports.action.goto-hover")), "goto:" + id, true)
                    : new Button("GRAY_DYE", layout.text("goto-offline"), List.of(), null, false));
        }
        if (hasCommand("pinfo")) {
            buttons.add(new Button("KNOWLEDGE_BOOK", layout.text("record-button"), List.of(
                    context.messages().render(staff, "reports.action.info-hover")), "record:" + id, true));
        }
        if (hasCommand("punish") && PunishmentCommands.entry(staff, PunishmentArguments.Command.PUNISH)) {
            buttons.add(new Button("ANVIL", layout.text("punish"), List.of(
                    context.messages().render(staff, "reports.action.punish-hover")), "punish:" + id, true));
        }
        Locale locale = context.messages().localeOf(staff);
        for (ReportAction action : flow.actions(staff)) {
            buttons.add(new Button(action.icon(), layout.text("custom",
                    Placeholder.component("name", ReportActions.label(action, locale))),
                    List.of(layout.text("custom-hint")), "run:" + action.id() + ":" + id, action.closeMenu()));
        }
        List<Integer> slots = ReportActions.slots(buttons.size());
        for (int index = 0; index < slots.size(); index++) {
            Button button = buttons.get(index);
            layout.item(slots.get(index), button.icon(), button.name(), button.lore(), button.action(),
                    button.close());
        }
        layout.item(39, "LIME_CONCRETE", layout.text("done"), List.of(
                context.messages().render(staff, "reports.action.close-hover")), "close:" + id, false);
        layout.item(41, "RED_CONCRETE", layout.text("reject"), List.of(
                context.messages().render(staff, "reports.action.reject-hover")), "reject:" + id, false);
        layout.item(45, "ARROW", layout.text("back"), List.of(), "back", false);
        layout.item(49, "BARRIER", layout.text("close"), List.of(), null, true);
        show(staff, layout.text("case-title", Placeholder.unparsed("id", id),
                Placeholder.unparsed("player", report.targetName())), layout, back);
    }

    private Component reporterLine(Layout layout, String reporter, String reason, long ago) {
        return layout.text("reporter-line", Placeholder.unparsed("reporter", reporter),
                Placeholder.component("reason", flow.reason(layout.viewer, reason)),
                Placeholder.unparsed("age", flow.duration(layout.viewer, ago)));
    }

    private boolean hasCommand(String name) {
        return context.proxy().getCommandManager().hasCommand(name);
    }

    private void show(Player staff, Component title, Layout layout, View view) {
        if (!staff.isActive()) {
            return;
        }
        int id = ids.incrementAndGet();
        sessions.put(staff.getUniqueId(), new Session(id, Set.copyOf(layout.actions), view,
                System.currentTimeMillis() + SESSION_MILLIS));
        JsonObject message = new JsonObject();
        message.addProperty("session", id);
        message.addProperty("title", mini.serialize(title));
        message.addProperty("rows", ROWS);
        message.add("items", layout.items);
        context.channel().sendReportsMenu(staff, message);
    }

    private static TagResolver count(String name, long value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    private Void failed(Player staff, Throwable error) {
        LOGGER.warn("Report menu failed", error);
        context.send(staff, "punishment.failed");
        return null;
    }
}
