package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.network.GameStateNames;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.lobby.action.ItemAction;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.config.ProfileLayout;
import net.prismarin.lobby.extension.LobbyExtensions;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.network.LobbyDirectory;
import net.prismarin.lobby.network.LobbyTravel;
import net.prismarin.lobby.util.CurrencyDisplay;
import net.prismarin.lobby.util.Languages;
import net.prismarin.lobby.util.XpProgressBar;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.NetworkStatusService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class LobbyMenus {

    private static final int NAVIGATOR_PER_ROW = 7;
    private static final int NAVIGATOR_MAX_ROWS = 4;
    private static final int LEVEL_BAR_SEGMENTS = 10;

    private final MenuService menuService;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ServerDirectory directory;
    private final MiniMessage miniMessage;
    private final LobbyItems items;
    private final CoreConfig config;
    private final Branding branding;
    private final CurrencyDisplay currencies;
    private final NetworkStatusService status;
    private final LobbySettings settings;
    private final RankFormatProvider ranks;
    private final LobbyDirectory lobbies;
    private final LobbyTravel travel;
    private final MenuKit kit;
    private final LobbySelectorMenu selector;
    private final LanguageMenu languages;
    private final SocialMenus social;
    private final NickToolMenu nickTool;
    private final LobbyExtensions extensions;

    @Inject
    public LobbyMenus(MenuService menuService, LocaleService locale, ProfileService profiles,
                      ServerDirectory directory, MiniMessage miniMessage, LobbyItems items, CoreConfig config,
                      Branding branding, CurrencyDisplay currencies, NetworkStatusService status,
                      LobbySettings settings, RankFormatProvider ranks, LobbyDirectory lobbies, LobbyTravel travel,
                      MenuKit kit, LobbySelectorMenu selector, LanguageMenu languages, SocialMenus social,
                      NickToolMenu nickTool, LobbyExtensions extensions) {
        this.extensions = extensions;
        this.social = social;
        this.nickTool = nickTool;
        this.menuService = menuService;
        this.locale = locale;
        this.profiles = profiles;
        this.directory = directory;
        this.miniMessage = miniMessage;
        this.items = items;
        this.config = config;
        this.branding = branding;
        this.currencies = currencies;
        this.status = status;
        this.settings = settings;
        this.ranks = ranks;
        this.lobbies = lobbies;
        this.travel = travel;
        this.kit = kit;
        this.selector = selector;
        this.languages = languages;
    }

    public boolean open(Player player, String menu) {
        switch (menu) {
            case ItemAction.MENU_NAVIGATOR -> {
                if (!settings.enabled(LobbySettings.NAVIGATOR)) {
                    return false;
                }
                openNavigator(player);
            }
            case ItemAction.MENU_PROFILE -> {
                if (!settings.enabled(LobbySettings.PROFILE_MENU)) {
                    return false;
                }
                openProfile(player);
            }
            case ItemAction.MENU_LOBBIES -> {
                if (!settings.enabled(LobbySettings.LOBBY_SELECTOR)) {
                    return false;
                }
                openLobbies(player);
            }
            case ItemAction.MENU_LANGUAGES -> {
                if (languages.languages().size() < 2) {
                    return false;
                }
                languages.open(player, this::openProfile);
            }
            case ItemAction.MENU_FRIENDS -> social.openFriends(player, 0);
            case ItemAction.MENU_PARTY -> social.openParty(player);
            case ItemAction.MENU_NICK -> nickTool.open(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    public void openLobbies(Player player) {
        selector.open(player, settings.enabled(LobbySettings.NAVIGATOR) ? this::openNavigator : null);
    }

    public void openNavigator(Player player) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
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
        entries.addAll(extensions.navigatorButtons(player));
        int count = Math.min(entries.size(), NAVIGATOR_PER_ROW * NAVIGATOR_MAX_ROWS);
        int contentRows = MenuLayout.rowsFor(count, NAVIGATOR_PER_ROW);
        Menu menu = new Menu(locale.render("lobby.navigator.title", playerLocale), contentRows + 2);
        kit.fill(menu);
        List<Integer> slots = MenuLayout.centeredSlots(1, count, NAVIGATOR_PER_ROW);
        for (int i = 0; i < count; i++) {
            menu.button(slots.get(i), entries.get(i));
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
        ProfileLayout layout = settings.profile();
        Menu menu = new Menu(locale.render("lobby.profile.title", playerLocale), layout.rows());
        kit.fill(menu);
        if (layout.headSlot() >= 0) {
            menu.button(layout.headSlot(), kit.decoration(items.profileHead(player, playerLocale)));
        }
        List<MenuButton> stats = new ArrayList<>();
        List<MenuButton> actions = new ArrayList<>();
        for (ProfileLayout.Entry entry : layout.allEntries()) {
            List<MenuButton> buttons = entryButtons(player, playerLocale, profile, entry.kind());
            if (entry.automatic()) {
                (ProfileLayout.actionEntry(entry.kind()) ? actions : stats).addAll(buttons);
                continue;
            }
            for (int i = 0; i < buttons.size() && entry.slot() + i < menu.size(); i++) {
                menu.button(entry.slot() + i, buttons.get(i));
            }
        }
        actions.addAll(extensions.profileButtons(player));
        if (layout.entryRow() == layout.actionRow()) {
            stats.addAll(actions);
            actions.clear();
        }
        placeRow(menu, layout.entryRow(), stats);
        placeRow(menu, layout.actionRow(), actions);
        if (layout.rows() >= 4) {
            kit.accent(menu, List.of(3, 5, menu.size() - 5));
        }
        if (layout.backSlot() >= 0 && settings.enabled(LobbySettings.NAVIGATOR)) {
            menu.button(layout.backSlot(), kit.back(playerLocale, this::openNavigator));
        }
        if (layout.closeSlot() >= 0) {
            menu.button(layout.closeSlot(), kit.close(playerLocale));
        }
        menuService.open(player, menu);
    }

    private static void placeRow(Menu menu, int row, List<MenuButton> buttons) {
        int count = Math.min(buttons.size(), 9);
        if (count == 0) {
            return;
        }
        List<Integer> slots = MenuLayout.centeredRowSlots(row, count);
        for (int i = 0; i < count; i++) {
            menu.button(slots.get(i), buttons.get(i));
        }
    }

    private List<MenuButton> entryButtons(Player player, Locale playerLocale, PlayerProfile profile,
                                          ProfileLayout.Entry.Kind kind) {
        return switch (kind) {
            case CURRENCIES -> currencies.shown().stream()
                    .map(currency -> kit.decoration(currencyIcon(playerLocale, profile, currency)))
                    .toList();
            case LEVEL -> config.systemEnabled(PrismarinSystems.LEVELS)
                    ? List.of(kit.decoration(levelIcon(playerLocale, profile)))
                    : List.of();
            case RANK -> List.of(kit.decoration(rankIcon(player, playerLocale)));
            case LANGUAGE -> languages.languages().size() < 2 ? List.of() : List.of(languageButton(player,
                    playerLocale));
            case FRIENDS, PARTY -> social.profileEntry(player, playerLocale, kind);
        };
    }

    private MenuButton serverButton(Locale playerLocale, NetworkServer server) {
        boolean here = server.name().equalsIgnoreCase(config.serverId());
        boolean selectorHere = here && server.inGroup(NetworkServer.GROUP_LOBBY)
                && settings.enabled(LobbySettings.LOBBY_SELECTOR) && lobbies.hasChoice();
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
        lore.add(selectorHere ? locale.render("lobby.navigator.lobby-click", playerLocale)
                : here ? locale.render("lobby.navigator.here", playerLocale)
                : locale.render("lobby.navigator.connect", playerLocale));
        ItemStack icon = ItemBuilder.of(iconFor(server))
                .name(name)
                .lore(lore)
                .glow(!here)
                .build();
        if (selectorHere) {
            return new MenuButton(icon, context -> openLobbies(context.player()));
        }
        if (here) {
            return new MenuButton(icon, context -> context.player().sendActionBar(
                    locale.render("lobby.navigator.already-here",
                            PlayerLocales.of(context.player(), profiles))));
        }
        return new MenuButton(icon, context -> {
            kit.closeLater(context.player());
            travel.connect(context.player(), server);
        });
    }

    private MenuButton groupButton(Locale playerLocale, ServerGroup group) {
        List<NetworkServer> servers = directory.byGroup(group.name());
        boolean lobbyGroup = NetworkServer.GROUP_LOBBY.equalsIgnoreCase(group.name())
                && settings.enabled(LobbySettings.LOBBY_SELECTOR);
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
        lore.add(locale.render(lobbyGroup ? "lobby.navigator.lobby-click"
                : joinable > 0 ? "lobby.navigator.group-click" : "lobby.navigator.group-none", playerLocale));
        Material material = group.property("icon").map(Material::matchMaterial).filter(Material::isItem)
                .orElse(lobbyGroup ? Material.PRISMARINE : Material.ENDER_PEARL);
        ItemStack icon = ItemBuilder.of(material)
                .name(locale.format(group.displayName()))
                .lore(lore)
                .glow(lobbyGroup || joinable > 0)
                .build();
        if (lobbyGroup) {
            return new MenuButton(icon, context -> openLobbies(context.player()));
        }
        return new MenuButton(icon, context -> {
            kit.closeLater(context.player());
            travel.playGroup(context.player(), group.name());
        });
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

    private ItemStack rankIcon(Player player, Locale playerLocale) {
        return ItemBuilder.of(Material.NAME_TAG)
                .name(locale.render("lobby.profile.rank.name", playerLocale))
                .lore(locale.render("lobby.profile.rank.lore", playerLocale, player.getUniqueId(),
                        Placeholder.component("rank", ranks.ownLabel(player))))
                .build();
    }

    private MenuButton languageButton(Player player, Locale playerLocale) {
        List<String> available = languages.languages();
        String current = languages.current(player);
        Optional<String> toggle = settings.profile().languageClick() == ProfileLayout.LanguageClick.TOGGLE
                ? Languages.toggleTarget(available, current)
                : Optional.empty();
        Component hint = toggle
                .map(next -> locale.render("lobby.profile.language.hint-toggle", playerLocale,
                        Placeholder.unparsed("language", languages.name(next))))
                .orElseGet(() -> locale.render("lobby.profile.language.hint-menu", playerLocale));
        ItemStack icon = languages.iconBuilder(current)
                .name(locale.render("lobby.profile.language.name", playerLocale))
                .lore(List.of(
                        locale.render("lobby.profile.language.current", playerLocale,
                                Placeholder.unparsed("language", languages.name(current))),
                        hint))
                .build();
        if (toggle.isPresent()) {
            String next = toggle.get();
            return new MenuButton(icon, context -> languages.switchTo(context.player(), next, this::openProfile));
        }
        return new MenuButton(icon, context -> languages.open(context.player(), this::openProfile));
    }

    private static Material iconFor(NetworkServer server) {
        return server.property("icon")
                .map(Material::matchMaterial)
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
