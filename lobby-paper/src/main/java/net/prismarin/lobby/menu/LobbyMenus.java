package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.player.PlayerLocales;
import net.prismarin.lobby.server.ServerConnector;
import net.prismarin.lobby.util.XpProgressBar;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class LobbyMenus {

    private static final int MENU_ROWS = 3;
    private static final int SERVER_ROW = 1;
    private static final int PROFILE_HEAD_SLOT = 4;
    private static final int PROFILE_COINS_SLOT = 10;
    private static final int PROFILE_GEMS_SLOT = 12;
    private static final int PROFILE_LEVEL_SLOT = 14;
    private static final int PROFILE_LOCALE_SLOT = 16;
    private static final int BACK_SLOT = 18;
    private static final int CLOSE_SLOT = 26;
    private static final int LEVEL_BAR_SEGMENTS = 10;
    private static final Consumer<ClickContext> DECORATION = context -> context.player().updateInventory();

    private final LobbyPlugin plugin;
    private final MenuService menuService;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ServerDirectory directory;
    private final MiniMessage miniMessage;
    private final LobbyItems items;
    private final PlayerHiderService hider;
    private final ServerConnector connector;

    @Inject
    public LobbyMenus(LobbyPlugin plugin, MenuService menuService, LocaleService locale, ProfileService profiles,
                      ServerDirectory directory, MiniMessage miniMessage, LobbyItems items,
                      PlayerHiderService hider, ServerConnector connector) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.locale = locale;
        this.profiles = profiles;
        this.directory = directory;
        this.miniMessage = miniMessage;
        this.items = items;
        this.hider = hider;
        this.connector = connector;
    }

    public void openNavigator(Player player) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        Menu menu = new Menu(locale.render("lobby.navigator.title", playerLocale), MENU_ROWS);
        fill(menu);
        List<NetworkServer> servers = directory.servers();
        int count = Math.min(servers.size(), 9);
        if (count > 0) {
            List<Integer> slots = MenuLayout.centeredRowSlots(SERVER_ROW, count);
            for (int i = 0; i < count; i++) {
                menu.button(slots.get(i), serverButton(playerLocale, servers.get(i)));
            }
        }
        menuService.open(player, menu);
    }

    public void openProfile(Player player) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        Optional<PlayerProfile> cached = profiles.getCached(player.getUniqueId());
        if (cached.isEmpty()) {
            player.sendMessage(locale.render("general.profile-not-loaded", playerLocale));
            return;
        }
        PlayerProfile profile = cached.get();
        Menu menu = new Menu(locale.render("lobby.profile.title", playerLocale), MENU_ROWS);
        fill(menu);
        menu.button(PROFILE_HEAD_SLOT, new MenuButton(items.profileHead(player, playerLocale), DECORATION));
        menu.button(PROFILE_COINS_SLOT, new MenuButton(coinsIcon(playerLocale, profile), DECORATION));
        menu.button(PROFILE_GEMS_SLOT, new MenuButton(gemsIcon(playerLocale, profile), DECORATION));
        menu.button(PROFILE_LEVEL_SLOT, new MenuButton(levelIcon(playerLocale, profile), DECORATION));
        menu.button(PROFILE_LOCALE_SLOT, localeButton(playerLocale, profile));
        menu.button(BACK_SLOT, new MenuButton(
                ItemBuilder.of(Material.ARROW).name(locale.render("menu.back", playerLocale)).build(),
                context -> openNavigator(context.player())));
        menu.button(CLOSE_SLOT, new MenuButton(
                ItemBuilder.of(Material.BARRIER).name(locale.render("menu.close", playerLocale)).build(),
                context -> closeLater(context.player())));
        menuService.open(player, menu);
    }

    private MenuButton serverButton(Locale playerLocale, NetworkServer server) {
        boolean here = server.category() == NetworkServer.Category.LOBBY;
        Component name = here
                ? locale.render("lobby.navigator.here-name", playerLocale,
                        Placeholder.unparsed("server", capitalize(server.name())))
                : miniMessage.deserialize(server.displayName());
        List<Component> lore = List.of(
                locale.render(descriptionKey(server.category()), playerLocale),
                Component.empty(),
                here
                        ? locale.render("lobby.navigator.here", playerLocale)
                        : locale.render("lobby.navigator.connect", playerLocale));
        ItemStack icon = ItemBuilder.of(iconFor(server.category()))
                .name(name)
                .lore(lore)
                .glow(!here)
                .build();
        if (here) {
            return new MenuButton(icon, context -> context.player().sendActionBar(
                    locale.render("lobby.navigator.already-here",
                            PlayerLocales.of(context.player(), profiles))));
        }
        return new MenuButton(icon, context -> connect(context.player(), server));
    }

    private void connect(Player player, NetworkServer server) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        player.sendMessage(locale.render("lobby.navigator.connecting", playerLocale,
                Placeholder.component("server", miniMessage.deserialize(server.displayName()))));
        player.playSound(Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 0.7f, 1.2f),
                Sound.Emitter.self());
        connector.connect(player, server.name());
        closeLater(player);
    }

    private void closeLater(Player player) {
        Runnable close = player::closeInventory;
        plugin.getServer().getScheduler().runTask(plugin, close);
    }

    private ItemStack coinsIcon(Locale playerLocale, PlayerProfile profile) {
        return ItemBuilder.of(Material.GOLD_INGOT)
                .name(locale.render("lobby.profile.coins.name", playerLocale))
                .lore(locale.render("lobby.profile.coins.lore", playerLocale,
                        Placeholder.unparsed("coins", String.valueOf(profile.getCoins()))))
                .build();
    }

    private ItemStack gemsIcon(Locale playerLocale, PlayerProfile profile) {
        return ItemBuilder.of(Material.EMERALD)
                .name(locale.render("lobby.profile.gems.name", playerLocale))
                .lore(locale.render("lobby.profile.gems.lore", playerLocale,
                        Placeholder.unparsed("gems", String.valueOf(profile.getGems()))))
                .build();
    }

    private ItemStack levelIcon(Locale playerLocale, PlayerProfile profile) {
        Component bar = miniMessage.deserialize(
                XpProgressBar.render(profile.getXp(), profile.xpForNextLevel(), LEVEL_BAR_SEGMENTS));
        return ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(locale.render("lobby.profile.level.name", playerLocale,
                        Placeholder.unparsed("level", String.valueOf(profile.getLevel()))))
                .lore(locale.render("lobby.profile.level.lore", playerLocale,
                        Placeholder.component("bar", bar),
                        Placeholder.unparsed("xp", String.valueOf(profile.getXp())),
                        Placeholder.unparsed("max", String.valueOf(profile.xpForNextLevel()))))
                .build();
    }

    private MenuButton localeButton(Locale playerLocale, PlayerProfile profile) {
        ItemStack icon = ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(locale.render("lobby.profile.locale.name", playerLocale))
                .lore(List.of(
                        locale.render("lobby.profile.locale.current", playerLocale),
                        locale.render("lobby.profile.locale.hint", playerLocale)))
                .build();
        return new MenuButton(icon, context -> switchLocale(context.player(), profile.getLocale()));
    }

    private void switchLocale(Player player, String current) {
        String next = current != null && current.startsWith("de") ? "en" : "de";
        Locale fallback = PlayerLocales.of(player, profiles);
        profiles.modify(player.getUniqueId(), profile -> profile.setLocale(next))
                .whenComplete((profile, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(locale.render("general.profile-not-loaded", fallback));
                        return;
                    }
                    Locale updated = Locale.forLanguageTag(next);
                    items.give(player, hider.hiding(player));
                    player.sendMessage(locale.render("lobby.profile.locale.changed", updated));
                    openProfile(player);
                }));
    }

    private void fill(Menu menu) {
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(Component.space()).build();
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.button(slot, new MenuButton(pane, DECORATION));
        }
    }

    private static Material iconFor(NetworkServer.Category category) {
        return switch (category) {
            case LOBBY -> Material.PRISMARINE;
            case BUILD -> Material.GRASS_BLOCK;
            case GAME -> Material.ENDER_PEARL;
        };
    }

    private static String descriptionKey(NetworkServer.Category category) {
        return switch (category) {
            case LOBBY -> "lobby.navigator.description.lobby";
            case BUILD -> "lobby.navigator.description.build";
            case GAME -> "lobby.navigator.description.game";
        };
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
