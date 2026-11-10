package net.prismarin.core.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.NetworkStatusService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class NetworkMenu {

    public static final String CHANNEL = "BungeeCord";

    private static final double HEALTHY_TPS = 18D;
    private static final int BAR_SEGMENTS = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final TextColor GOOD = TextColor.color(0x4ADE80);
    private static final TextColor WARN = TextColor.color(0xFACC15);
    private static final TextColor BUSY = TextColor.color(0xFB923C);
    private static final TextColor DOWN = TextColor.color(0xF87171);

    private final PrismarinCorePlugin plugin;
    private final MenuService menus;
    private final LocaleService locale;
    private final ServerDirectory directory;
    private final NetworkStatusService status;
    private final CoreConfig config;
    private final ProfileService profiles;

    @Inject
    public NetworkMenu(PrismarinCorePlugin plugin, MenuService menus, LocaleService locale,
                       ServerDirectory directory, NetworkStatusService status, CoreConfig config,
                       ProfileService profiles) {
        this.plugin = plugin;
        this.menus = menus;
        this.locale = locale;
        this.directory = directory;
        this.status = status;
        this.config = config;
        this.profiles = profiles;
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int page) {
        menus.open(player, build(player, status.statuses(), page));
        status.refresh().thenAccept(fresh -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && menus.openMenu(player).filter(NetworkOverview.class::isInstance).isPresent()) {
                menus.open(player, build(player, fresh, page));
            }
        }));
    }

    private Menu build(Player player, List<ServerStatus> statuses, int requestedPage) {
        Locale target = PlayerLocales.of(player, profiles);
        List<NetworkServer> servers = directory.servers();
        Map<String, ServerStatus> byName = statuses.stream()
                .collect(Collectors.toMap(entry -> entry.name().toLowerCase(Locale.ROOT), Function.identity(),
                        (first, second) -> first));
        List<ServerStatus> known = servers.stream().map(server -> statusOf(byName, server)).toList();
        int online = (int) known.stream().filter(ServerStatus::online).count();
        int pages = NetworkLayout.pages(servers.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int from = page * NetworkLayout.PER_PAGE;
        int to = Math.min(servers.size(), from + NetworkLayout.PER_PAGE);
        List<NetworkServer> shown = servers.subList(from, to);
        int rows = NetworkLayout.rows(shown.size());
        Menu menu = new NetworkOverview(locale.render("network.overview.title", target,
                Placeholder.unparsed("online", String.valueOf(online)),
                Placeholder.unparsed("total", String.valueOf(servers.size()))), rows);
        frame(menu, rows);
        menu.button(4, new MenuButton(summary(target, known, online, servers.size()), noop()));
        List<Integer> slots = NetworkLayout.slots(shown.size());
        for (int i = 0; i < shown.size(); i++) {
            NetworkServer server = shown.get(i);
            menu.button(slots.get(i), serverButton(target, server, statusOf(byName, server)));
        }
        int bottom = (rows - 1) * NetworkLayout.COLUMNS;
        menu.button(bottom + 3, new MenuButton(ItemBuilder.of(Material.CLOCK)
                .name(locale.render("network.overview.refresh", target))
                .lore(locale.render("network.overview.refresh-hint", target))
                .build(), context -> open(context.player(), page)));
        menu.button(bottom + 5, new MenuButton(ItemBuilder.of(Material.BARRIER)
                .name(locale.render("menu.close", target))
                .build(), context -> plugin.getServer().getScheduler()
                .runTask(plugin, () -> context.player().closeInventory())));
        if (page > 0) {
            menu.button(bottom, new MenuButton(ItemBuilder.of(Material.ARROW)
                    .name(locale.render("network.overview.previous", target,
                            Placeholder.unparsed("page", String.valueOf(page)),
                            Placeholder.unparsed("pages", String.valueOf(pages))))
                    .build(), context -> open(context.player(), page - 1)));
        }
        if (page < pages - 1) {
            menu.button(bottom + 8, new MenuButton(ItemBuilder.of(Material.ARROW)
                    .name(locale.render("network.overview.next", target,
                            Placeholder.unparsed("page", String.valueOf(page + 2)),
                            Placeholder.unparsed("pages", String.valueOf(pages))))
                    .build(), context -> open(context.player(), page + 1)));
        }
        return menu;
    }

    private static ServerStatus statusOf(Map<String, ServerStatus> byName, NetworkServer server) {
        return byName.getOrDefault(server.name().toLowerCase(Locale.ROOT),
                ServerStatus.offline(server.name(), Instant.now()));
    }

    private void frame(Menu menu, int rows) {
        ItemStack border = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(Component.space()).build();
        ItemStack accent = ItemBuilder.of(Material.CYAN_STAINED_GLASS_PANE).name(Component.space()).build();
        ItemStack inner = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.space()).build();
        int size = rows * NetworkLayout.COLUMNS;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / NetworkLayout.COLUMNS;
            boolean edge = row == 0 || row == rows - 1;
            ItemStack pane = edge ? border : inner;
            if (slot == 3 || slot == 5 || slot == size - 5) {
                pane = accent;
            }
            menu.button(slot, new MenuButton(pane, noop()));
        }
    }

    private ItemStack summary(Locale target, List<ServerStatus> known, int online, int total) {
        int players = known.stream().filter(ServerStatus::online).mapToInt(ServerStatus::players).sum();
        int capacity = known.stream().filter(ServerStatus::online).mapToInt(ServerStatus::maxPlayers).sum();
        List<ServerStatus> measured = known.stream().filter(ServerStatus::online).filter(ServerStatus::prismarin)
                .toList();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(locale.render("network.overview.players", target,
                Placeholder.unparsed("players", String.valueOf(players)),
                Placeholder.unparsed("max", String.valueOf(capacity))));
        lore.add(locale.render("network.overview.servers", target,
                Placeholder.unparsed("online", String.valueOf(online)),
                Placeholder.unparsed("total", String.valueOf(total))));
        if (!measured.isEmpty()) {
            double tps = measured.stream().mapToDouble(ServerStatus::tps).average().orElse(0D);
            lore.add(locale.render("network.overview.tps", target,
                    Placeholder.unparsed("tps", String.format(Locale.ROOT, "%.1f", tps))));
        }
        lore.add(locale.render("network.overview.updated", target,
                Placeholder.unparsed("time", LocalTime.now(ZoneId.systemDefault()).format(TIME))));
        lore.add(Component.empty());
        lore.add(locale.render("network.overview.legend", target));
        return ItemBuilder.of(Material.NETHER_STAR)
                .name(locale.render("network.overview.summary", target))
                .lore(lore)
                .build();
    }

    private MenuButton serverButton(Locale target, NetworkServer server, ServerStatus entry) {
        boolean here = server.name().equalsIgnoreCase(config.serverId());
        TextColor color = colorFor(entry);
        Component name = Component.text("● ", color).decoration(TextDecoration.ITALIC, false)
                .append(locale.format(server.displayName()));
        List<Component> lore = new ArrayList<>();
        lore.add(locale.render("network.card.group", target,
                Placeholder.unparsed("group", directory.group(server.group())
                        .map(ServerGroup::displayName).map(this::plain).orElse(server.group())),
                Placeholder.unparsed("server", server.name())));
        lore.add(Component.empty());
        if (!entry.online()) {
            lore.add(locale.render("network.card.offline", target));
        } else {
            Component state = entry.state() != GameState.UNKNOWN || entry.full()
                    ? GameStateNames.render(locale, entry, target)
                    : locale.render("network.card.online", target);
            lore.add(locale.render("network.card.state", target, Placeholder.component("state", state)));
            lore.add(locale.render("network.card.players", target,
                    Placeholder.unparsed("players", String.valueOf(entry.players())),
                    Placeholder.unparsed("max", String.valueOf(entry.maxPlayers())),
                    Placeholder.component("bar", bar(entry.players(), entry.maxPlayers(), color))));
            lore.add(entry.latencyMillis() >= 0
                    ? locale.render("network.card.ping", target,
                            Placeholder.unparsed("ping", String.valueOf(entry.latencyMillis())))
                    : locale.render("network.card.ping-unknown", target));
            if (entry.prismarin()) {
                lore.add(locale.render("network.card.tps", target,
                        Placeholder.unparsed("tps", String.format(Locale.ROOT, "%.1f", entry.tps())),
                        Placeholder.unparsed("mspt", String.format(Locale.ROOT, "%.1f", entry.mspt()))));
                lore.add(locale.render("network.card.memory", target,
                        Placeholder.unparsed("used", String.valueOf(entry.usedMemoryMb())),
                        Placeholder.unparsed("max", String.valueOf(entry.maxMemoryMb()))));
                lore.add(locale.render("network.card.uptime", target,
                        Placeholder.unparsed("uptime", UptimeFormat.compact(entry.uptimeSeconds()))));
            } else {
                lore.add(locale.render("network.card.no-metrics", target));
            }
            if (!entry.version().isEmpty()) {
                lore.add(locale.render("network.card.version", target,
                        Placeholder.unparsed("version", entry.version())));
            }
        }
        lore.add(Component.empty());
        lore.add(locale.render(here ? "network.card.here" : entry.online() ? "network.card.connect"
                : "network.card.unreachable", target));
        ItemStack icon = ItemBuilder.of(materialFor(entry))
                .amount(Math.max(1, Math.min(64, entry.players())))
                .name(name)
                .lore(lore)
                .glow(here)
                .build();
        if (here || !entry.online()) {
            return new MenuButton(icon, noop());
        }
        return new MenuButton(icon, context -> connect(context.player(), server.name()));
    }

    private Component bar(int players, int max, TextColor color) {
        int filled = NetworkLayout.filled(players, max, BAR_SEGMENTS);
        return Component.text("▌".repeat(filled), color)
                .append(Component.text("▌".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }

    private String plain(String text) {
        return PlainTextComponentSerializer.plainText().serialize(locale.format(text));
    }

    private static TextColor colorFor(ServerStatus entry) {
        if (!entry.online()) {
            return DOWN;
        }
        if (!entry.joinable()) {
            return BUSY;
        }
        if (entry.prismarin() && entry.tps() < HEALTHY_TPS) {
            return WARN;
        }
        return GOOD;
    }

    private static Material materialFor(ServerStatus entry) {
        if (!entry.online()) {
            return Material.RED_CONCRETE;
        }
        if (!entry.joinable()) {
            return Material.ORANGE_CONCRETE;
        }
        if (entry.prismarin() && entry.tps() < HEALTHY_TPS) {
            return Material.YELLOW_CONCRETE;
        }
        return Material.LIME_CONCRETE;
    }

    private static Consumer<ClickContext> noop() {
        return context -> context.player().updateInventory();
    }

    private void connect(Player player, String serverName) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    private static final class NetworkOverview extends Menu {

        NetworkOverview(Component title, int rows) {
            super(title, rows);
        }
    }
}
