package net.prismarin.core.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.core.PrismarinCorePlugin;
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

    private static final int MAX_SERVER_SLOTS = 45;
    private static final double HEALTHY_TPS = 18D;

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
        menus.open(player, build(player, status.statuses()));
        status.refresh().thenAccept(fresh -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && menus.openMenu(player).filter(NetworkOverview.class::isInstance).isPresent()) {
                menus.open(player, build(player, fresh));
            }
        }));
    }

    private Menu build(Player player, List<ServerStatus> statuses) {
        Locale target = PlayerLocales.of(player, profiles);
        List<NetworkServer> servers = directory.servers().stream().limit(MAX_SERVER_SLOTS).toList();
        Map<String, ServerStatus> byName = statuses.stream()
                .collect(Collectors.toMap(entry -> entry.name().toLowerCase(Locale.ROOT), Function.identity(),
                        (first, second) -> first));
        int online = (int) statuses.stream().filter(ServerStatus::online).count();
        int players = statuses.stream().mapToInt(ServerStatus::players).sum();
        int rows = Math.min(6, Math.max(2, (servers.size() + 8) / 9 + 1));
        Menu menu = new NetworkOverview(locale.render("network.menu.title", target,
                Placeholder.unparsed("online", String.valueOf(online)),
                Placeholder.unparsed("total", String.valueOf(servers.size()))), rows);
        for (int i = 0; i < servers.size(); i++) {
            NetworkServer server = servers.get(i);
            ServerStatus entry = byName.getOrDefault(server.name().toLowerCase(Locale.ROOT),
                    ServerStatus.offline(server.name(), Instant.now()));
            menu.button(i, serverButton(target, server, entry));
        }
        int bottom = (rows - 1) * 9;
        menu.button(bottom + 3, new MenuButton(ItemBuilder.of(Material.PLAYER_HEAD)
                .name(locale.render("network.menu.summary", target,
                        Placeholder.unparsed("players", String.valueOf(players)),
                        Placeholder.unparsed("online", String.valueOf(online)),
                        Placeholder.unparsed("total", String.valueOf(servers.size()))))
                .build(), context -> context.player().updateInventory()));
        menu.button(bottom + 4, new MenuButton(ItemBuilder.of(Material.CLOCK)
                .name(locale.render("network.menu.refresh", target))
                .build(), context -> open(context.player())));
        menu.button(bottom + 5, new MenuButton(ItemBuilder.of(Material.BARRIER)
                .name(locale.render("menu.close", target))
                .build(), context -> plugin.getServer().getScheduler()
                .runTask(plugin, () -> context.player().closeInventory())));
        return menu;
    }

    private MenuButton serverButton(Locale target, NetworkServer server, ServerStatus entry) {
        boolean here = server.name().equalsIgnoreCase(config.serverId());
        List<Component> lore = new ArrayList<>();
        TagResolver group = Placeholder.unparsed("group", server.group());
        lore.add(locale.render("network.server.group", target, group));
        if (!entry.online()) {
            lore.add(locale.render("network.server.offline", target));
        } else {
            lore.add(locale.render("network.server.players", target,
                    Placeholder.unparsed("players", String.valueOf(entry.players())),
                    Placeholder.unparsed("max", String.valueOf(entry.maxPlayers()))));
            lore.add(entry.latencyMillis() >= 0
                    ? locale.render("network.server.ping", target,
                            Placeholder.unparsed("ping", String.valueOf(entry.latencyMillis())))
                    : locale.render("network.server.ping-unknown", target));
            if (!entry.version().isEmpty()) {
                lore.add(locale.render("network.server.version", target,
                        Placeholder.unparsed("version", entry.version())));
            }
            if (entry.prismarin()) {
                lore.add(locale.render("network.server.tps", target,
                        Placeholder.unparsed("tps", String.format(Locale.ROOT, "%.1f", entry.tps())),
                        Placeholder.unparsed("mspt", String.format(Locale.ROOT, "%.1f", entry.mspt()))));
                lore.add(locale.render("network.server.memory", target,
                        Placeholder.unparsed("used", String.valueOf(entry.usedMemoryMb())),
                        Placeholder.unparsed("max", String.valueOf(entry.maxMemoryMb()))));
                lore.add(locale.render("network.server.uptime", target,
                        Placeholder.unparsed("uptime", UptimeFormat.compact(entry.uptimeSeconds()))));
            } else {
                lore.add(locale.render("network.server.no-metrics", target));
            }
        }
        lore.add(Component.empty());
        lore.add(locale.render(here ? "network.server.here" : "network.server.connect", target));
        ItemStack icon = ItemBuilder.of(materialFor(entry))
                .name(locale.format(server.displayName()))
                .lore(lore)
                .glow(here)
                .build();
        if (here || !entry.online()) {
            return new MenuButton(icon, context -> context.player().updateInventory());
        }
        return new MenuButton(icon, context -> connect(context.player(), server.name()));
    }

    private static Material materialFor(ServerStatus entry) {
        if (!entry.online()) {
            return Material.RED_CONCRETE;
        }
        if (entry.prismarin() && entry.tps() < HEALTHY_TPS) {
            return Material.YELLOW_CONCRETE;
        }
        return Material.LIME_CONCRETE;
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
