package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.network.GameStateNames;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.server.ServerConnector;
import net.prismarin.lobby.util.CurrencyDisplay;
import net.prismarin.lobby.util.XpProgressBar;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.NetworkStatusService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class LobbyMenus {

    private static final int MENU_ROWS = 3;
    private static final int SERVER_ROW = 1;
    private static final int PROFILE_HEAD_SLOT = 4;
    private static final int PROFILE_ROW = 1;
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
    private final CoreConfig config;
    private final Branding branding;
    private final CurrencyDisplay currencies;
    private final NetworkStatusService status;
    private final NetworkConnector network;

    @Inject
    public LobbyMenus(LobbyPlugin plugin, MenuService menuService, LocaleService locale, ProfileService profiles,
                      ServerDirectory directory, MiniMessage miniMessage, LobbyItems items,
                      PlayerHiderService hider, ServerConnector connector, CoreConfig config,
                      Branding branding, CurrencyDisplay currencies, NetworkStatusService status,
                      NetworkConnector network) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.locale = locale;
        this.profiles = profiles;
        this.directory = directory;
        this.miniMessage = miniMessage;
        this.items = items;
        this.hider = hider;
        this.connector = connector;
        this.config = config;
        this.branding = branding;
        this.currencies = currencies;
        this.status = status;
        this.network = network;
    }

    public void openNavigator(Player player) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        Menu menu = new Menu(locale.render("lobby.navigator.title", playerLocale), MENU_ROWS);
        fill(menu);
        List<MenuButton> entries = new ArrayList<>();
        Set<String> listedGroups = new HashSet<>();
        for (ServerGroup group : directory.groups()) {
            if (!directory.byGroup(group.name()).isEmpty()) {
                listedGroups.add(group.name());
                entries.add(groupButton(playerLocale, group));
            }
        }
        for (NetworkServer server : directory.servers()) {
            if (!listedGroups.contains(server.group())) {
                entries.add(serverButton(playerLocale, server));
            }
        }
        int count = Math.min(entries.size(), 9);
        if (count > 0) {
            List<Integer> slots = MenuLayout.centeredRowSlots(SERVER_ROW, count);
            for (int i = 0; i < count; i++) {
                menu.button(slots.get(i), entries.get(i));
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
        List<MenuButton> entries = new ArrayList<>();
        for (Currency currency : currencies.shown()) {
            entries.add(new MenuButton(currencyIcon(playerLocale, profile, currency), DECORATION));
        }
        if (config.systemEnabled(PrismarinSystems.LEVELS)) {
            entries.add(new MenuButton(levelIcon(playerLocale, profile), DECORATION));
        }
        entries.add(localeButton(playerLocale, profile));
        List<Integer> slots = MenuLayout.centeredRowSlots(PROFILE_ROW, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            menu.button(slots.get(i), entries.get(i));
        }
        menu.button(BACK_SLOT, new MenuButton(
                ItemBuilder.of(Material.ARROW).name(locale.render("menu.back", playerLocale)).build(),
                context -> openNavigator(context.player())));
        menu.button(CLOSE_SLOT, new MenuButton(
                ItemBuilder.of(Material.BARRIER).name(locale.render("menu.close", playerLocale)).build(),
                context -> closeLater(context.player())));
        menuService.open(player, menu);
    }

    private MenuButton serverButton(Locale playerLocale, NetworkServer server) {
        boolean here = server.name().equalsIgnoreCase(config.serverId());
        Component name = here
                ? locale.render("lobby.navigator.here-name", playerLocale,
                        Placeholder.unparsed("server", capitalize(server.name())))
                : locale.format(server.displayName());
        List<Component> lore = new ArrayList<>();
        lore.add(description(server, playerLocale));
        status.status(server.name()).filter(ServerStatus::online).ifPresent(entry -> lore.add(
                locale.render("lobby.navigator.server-status", playerLocale,
                        Placeholder.component("state", GameStateNames.render(locale, entry, playerLocale)),
                        Placeholder.unparsed("players", String.valueOf(entry.players())),
                        Placeholder.unparsed("max", String.valueOf(entry.maxPlayers())))));
        lore.add(Component.empty());
        lore.add(here
                ? locale.render("lobby.navigator.here", playerLocale)
                : locale.render("lobby.navigator.connect", playerLocale));
        ItemStack icon = ItemBuilder.of(iconFor(server))
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

    private MenuButton groupButton(Locale playerLocale, ServerGroup group) {
        List<NetworkServer> servers = directory.byGroup(group.name());
        int online = 0;
        int joinable = 0;
        for (NetworkServer server : servers) {
            Optional<ServerStatus> entry = status.status(server.name());
            online += entry.filter(ServerStatus::online).map(ServerStatus::players).orElse(0);
            joinable += entry.filter(ServerStatus::joinable).isPresent() ? 1 : 0;
        }
        List<Component> lore = new ArrayList<>();
        group.property("description").map(text -> locale.raw(text, playerLocale).equals(text)
                ? locale.format(text) : locale.render(text, playerLocale)).ifPresent(lore::add);
        lore.add(locale.render("lobby.navigator.group-lore", playerLocale,
                Placeholder.unparsed("online", String.valueOf(online)),
                Placeholder.unparsed("joinable", String.valueOf(joinable))));
        lore.add(Component.empty());
        lore.add(locale.render(joinable > 0 ? "lobby.navigator.group-click" : "lobby.navigator.group-none",
                playerLocale));
        Material material = group.property("icon").map(Material::matchMaterial).filter(Material::isItem)
                .orElse(Material.ENDER_PEARL);
        ItemStack icon = ItemBuilder.of(material)
                .name(locale.format(group.displayName()))
                .lore(lore)
                .glow(joinable > 0)
                .build();
        return new MenuButton(icon, context -> playGroup(context.player(), group));
    }

    private void playGroup(Player player, ServerGroup group) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        closeLater(player);
        network.connectToGroup(player.getUniqueId(), group.name()).thenAccept(result -> plugin.getServer()
                .getScheduler().runTask(plugin, () -> {
                    if (result.isEmpty()) {
                        player.sendMessage(locale.render("play.no-server", playerLocale,
                                Placeholder.unparsed("group", group.name())));
                        return;
                    }
                    player.sendMessage(locale.render("lobby.navigator.connecting", playerLocale,
                            Placeholder.component("server", locale.format(result.get().displayName()))));
                    player.playSound(Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 0.7f,
                            1.2f), Sound.Emitter.self());
                }));
    }

    private void connect(Player player, NetworkServer server) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        player.sendMessage(locale.render("lobby.navigator.connecting", playerLocale,
                Placeholder.component("server", locale.format(server.displayName()))));
        player.playSound(Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 0.7f, 1.2f),
                Sound.Emitter.self());
        connector.connect(player, server.name());
        closeLater(player);
    }

    private void closeLater(Player player) {
        Runnable close = player::closeInventory;
        plugin.getServer().getScheduler().runTask(plugin, close);
    }

    private ItemStack currencyIcon(Locale playerLocale, PlayerProfile profile, Currency currency) {
        long amount = profile.balance(currency);
        Material material = currency.equals(Currency.COINS) ? Material.GOLD_INGOT
                : currency.equals(Currency.GEMS) ? Material.EMERALD : Material.SUNFLOWER;
        return ItemBuilder.of(material)
                .name(currencies.render("lobby.profile", currency, amount, playerLocale, "name"))
                .lore(currencies.render("lobby.profile", currency, amount, playerLocale, "lore"))
                .build();
    }

    private ItemStack levelIcon(Locale playerLocale, PlayerProfile profile) {
        Component bar = miniMessage.deserialize(
                XpProgressBar.render(profile.getXp(), profile.xpForNextLevel(), LEVEL_BAR_SEGMENTS,
                        branding.primaryColor()));
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
        profiles.modify(player.getUniqueId(), profile -> {
                    profile.setLocale(next);
                    profile.getSettings().put(PlayerProfile.SETTING_LOCALE_OVERRIDE, "true");
                })
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

    private static Material iconFor(NetworkServer server) {
        return server.property("icon")
                .map(name -> Material.matchMaterial(name))
                .filter(Material::isItem)
                .orElseGet(() -> switch (server.group()) {
                    case NetworkServer.GROUP_LOBBY -> Material.PRISMARINE;
                    case "build" -> Material.GRASS_BLOCK;
                    default -> Material.ENDER_PEARL;
                });
    }

    private Component description(NetworkServer server, Locale playerLocale) {
        Optional<String> configured = server.property("description");
        if (configured.isPresent()) {
            String text = configured.get();
            return locale.raw(text, playerLocale).equals(text)
                    ? locale.format(text)
                    : locale.render(text, playerLocale);
        }
        String key = "lobby.navigator.description." + server.group();
        return locale.raw(key, playerLocale).equals(key)
                ? locale.render("lobby.navigator.description.game", playerLocale)
                : locale.render(key, playerLocale);
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
