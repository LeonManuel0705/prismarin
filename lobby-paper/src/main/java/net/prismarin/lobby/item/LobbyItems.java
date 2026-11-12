package net.prismarin.lobby.item;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.cosmetics.CosmeticApi;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerApi;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.SocialApi;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.nick.NickManager;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.CustomItem;
import net.prismarin.lobby.config.HotbarItem;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.config.ProfileLayout;
import net.prismarin.lobby.extension.LobbyExtensions;
import net.prismarin.lobby.network.LobbyDirectory;
import net.prismarin.lobby.player.LobbyVisibility;
import net.prismarin.lobby.util.ConfigText;
import net.prismarin.lobby.util.CurrencyDisplay;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

@Singleton
public final class LobbyItems {

    public static final String NAVIGATOR_ID = LobbySettings.NAVIGATOR_ITEM;
    public static final String LOBBY_SELECTOR_ID = LobbySettings.LOBBY_SELECTOR_ITEM;
    public static final String PROFILE_ID = LobbySettings.PROFILE_ITEM;
    public static final String PREMIUM_ID = LobbySettings.PREMIUM_ITEM;
    public static final String HIDER_ID = LobbySettings.HIDER_ITEM;
    public static final String NICK_TOOL_ID = LobbySettings.NICK_TOOL_ITEM;
    public static final String SILENT_ID = LobbySettings.SILENT_ITEM;
    public static final String COSMETICS_ID = LobbySettings.COSMETICS_ITEM;
    public static final String CUSTOM_PREFIX = "custom:";

    private static final Map<String, Material> FALLBACK_MATERIALS = Map.of(
            NAVIGATOR_ID, Material.COMPASS,
            LOBBY_SELECTOR_ID, Material.NETHER_STAR,
            PROFILE_ID, Material.PLAYER_HEAD,
            PREMIUM_ID, Material.TNT,
            HIDER_ID, Material.LIME_DYE,
            NICK_TOOL_ID, Material.NAME_TAG,
            SILENT_ID, Material.AMETHYST_SHARD,
            COSMETICS_ID, Material.CHEST);

    private final LocaleService locale;
    private final ProfileService profiles;
    private final LobbySettings settings;
    private final CurrencyDisplay currencies;
    private final CoreConfig config;
    private final RankFormatProvider ranks;
    private final LobbyDirectory lobbies;
    private final ConfigText texts;
    private final NamespacedKey key;
    private final Map<String, Material> materials = new LinkedHashMap<>();
    private final Map<String, Material> customMaterials = new LinkedHashMap<>();
    private final Material hiddenMaterial;
    private final Material silentMaterial;
    private final LobbyPlugin plugin;
    private final LobbyVisibility visibility;
    private final LobbyExtensions extensions;

    @Inject
    public LobbyItems(LobbyPlugin plugin, LocaleService locale, ProfileService profiles, LobbySettings settings,
                      CurrencyDisplay currencies, CoreConfig config, RankFormatProvider ranks,
                      LobbyDirectory lobbies, ConfigText texts, LobbyVisibility visibility,
                      LobbyExtensions extensions) {
        this.plugin = plugin;
        this.extensions = extensions;
        this.visibility = visibility;
        this.locale = locale;
        this.profiles = profiles;
        this.settings = settings;
        this.currencies = currencies;
        this.config = config;
        this.ranks = ranks;
        this.lobbies = lobbies;
        this.texts = texts;
        this.key = new NamespacedKey(plugin, "lobby-item");
        Logger logger = plugin.getLogger();
        for (HotbarItem item : settings.items().values()) {
            materials.put(item.id(), resolve(logger, "items." + item.id() + ".material", item.material(),
                    FALLBACK_MATERIALS.get(item.id())));
        }
        this.hiddenMaterial = resolve(logger, "items.player-hider.hidden-material",
                settings.item(HIDER_ID).hiddenMaterial(), Material.GRAY_DYE);
        this.silentMaterial = resolve(logger, "items.silent-lobby.active-material",
                settings.item(SILENT_ID).hiddenMaterial(), Material.ECHO_SHARD);
        for (CustomItem item : settings.customItems()) {
            customMaterials.put(item.id(), resolve(logger, "custom-items." + item.id() + ".material",
                    item.material(), Material.PAPER));
        }
    }

    public void give(Player player, boolean playersHidden) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        PlayerInventory inventory = player.getInventory();
        removeAll(player);
        if (shown(NAVIGATOR_ID)) {
            place(inventory, slot(NAVIGATOR_ID), navigator(playerLocale));
        }
        if (shown(LOBBY_SELECTOR_ID)) {
            place(inventory, slot(LOBBY_SELECTOR_ID), lobbySelector(playerLocale));
        }
        if (shown(PROFILE_ID)) {
            place(inventory, slot(PROFILE_ID), profileHead(player, playerLocale));
        }
        if (shown(PREMIUM_ID)) {
            place(inventory, slot(PREMIUM_ID), premium(playerLocale));
        }
        if (shown(HIDER_ID)) {
            place(inventory, slot(HIDER_ID), hiderItem(playerLocale, playersHidden));
        }
        if (shown(NICK_TOOL_ID) && permitted(player, NICK_TOOL_ID)) {
            place(inventory, slot(NICK_TOOL_ID), nickTool(playerLocale));
        }
        if (shown(SILENT_ID) && permitted(player, SILENT_ID)) {
            place(inventory, slot(SILENT_ID), silentItem(playerLocale, visibility.silent(player.getUniqueId())));
        }
        if (shown(COSMETICS_ID)) {
            place(inventory, slot(COSMETICS_ID), cosmetics(playerLocale));
        }
        if (settings.enabled(LobbySettings.CUSTOM_ITEMS)) {
            for (CustomItem item : settings.customItems()) {
                if (item.enabled() && (item.permission().isEmpty() || player.hasPermission(item.permission()))) {
                    place(inventory, item.slot(), custom(player, playerLocale, item));
                }
            }
        }
        for (LobbyExtensions.HotbarIcon icon : extensions.hotbarIcons(player)) {
            place(inventory, icon.slot(), tag(icon.item(), LobbyExtensions.HOTBAR_PREFIX + icon.id()));
        }
    }

    private void place(PlayerInventory inventory, int slot, ItemStack item) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.isEmpty() || isLobbyItem(current)) {
            inventory.setItem(slot, item);
        }
    }

    public boolean shown(String id) {
        HotbarItem item = settings.item(id);
        if (!item.enabled()) {
            return false;
        }
        return switch (id) {
            case NAVIGATOR_ID -> settings.enabled(LobbySettings.NAVIGATOR);
            case LOBBY_SELECTOR_ID -> settings.enabled(LobbySettings.LOBBY_SELECTOR) && lobbies.hasChoice();
            case PROFILE_ID -> settings.enabled(LobbySettings.PROFILE_MENU);
            case PREMIUM_ID -> settings.enabled(LobbySettings.PREMIUM_LOBBY) && lobbies.hasPremium();
            case HIDER_ID -> settings.enabled(LobbySettings.PLAYER_HIDER);
            case NICK_TOOL_ID -> settings.enabled(LobbySettings.NICK_TOOL) && nickService().isPresent();
            case SILENT_ID -> settings.enabled(LobbySettings.SILENT_LOBBY);
            case COSMETICS_ID -> settings.enabled(LobbySettings.COSMETICS) && CosmeticApi.service().isPresent();
            default -> false;
        };
    }

    public boolean permitted(Player player, String id) {
        String permission = settings.item(id).permission();
        if (!permission.isEmpty()) {
            return player.hasPermission(permission);
        }
        if (NICK_TOOL_ID.equals(id)) {
            return NickManager.NICK_PERMISSIONS.stream().anyMatch(player::hasPermission);
        }
        return true;
    }

    public Optional<NickService> nickService() {
        return Optional.ofNullable(plugin.getServer().getServicesManager().load(NickService.class));
    }

    public void refreshSilent(Player player, boolean active) {
        if (shown(SILENT_ID) && permitted(player, SILENT_ID)) {
            place(player.getInventory(), slot(SILENT_ID), silentItem(PlayerLocales.of(player, profiles), active));
        }
    }

    public ItemStack nickTool(Locale playerLocale) {
        return tag(ItemBuilder.of(materials.get(NICK_TOOL_ID))
                .name(locale.render("lobby.item.nick-tool.name", playerLocale))
                .lore(locale.render("lobby.item.nick-tool.lore", playerLocale))
                .build(), NICK_TOOL_ID);
    }

    public ItemStack cosmetics(Locale playerLocale) {
        return tag(ItemBuilder.of(materials.get(COSMETICS_ID))
                .name(locale.render("lobby.item.cosmetics.name", playerLocale))
                .lore(locale.render("lobby.item.cosmetics.lore", playerLocale))
                .build(), COSMETICS_ID);
    }

    public ItemStack silentItem(Locale playerLocale, boolean active) {
        return tag(ItemBuilder.of(active ? silentMaterial : materials.get(SILENT_ID))
                .name(locale.render(active ? "lobby.item.silent.on" : "lobby.item.silent.off", playerLocale))
                .lore(locale.render("lobby.item.silent.lore", playerLocale))
                .glow(active)
                .build(), SILENT_ID);
    }

    public void refreshHider(Player player, boolean hidden) {
        if (shown(HIDER_ID)) {
            place(player.getInventory(), slot(HIDER_ID), hiderItem(PlayerLocales.of(player, profiles), hidden));
        }
    }

    public void removeAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isLobbyItem(contents[slot])) {
                inventory.setItem(slot, null);
            }
        }
    }

    public ItemStack navigator(Locale playerLocale) {
        return tag(ItemBuilder.of(materials.get(NAVIGATOR_ID))
                .name(locale.render("lobby.item.navigator.name", playerLocale))
                .lore(locale.render("lobby.item.navigator.lore", playerLocale))
                .build(), NAVIGATOR_ID);
    }

    public ItemStack lobbySelector(Locale playerLocale) {
        return tag(ItemBuilder.of(materials.get(LOBBY_SELECTOR_ID))
                .name(locale.render("lobby.item.lobby-selector.name", playerLocale))
                .lore(locale.render("lobby.item.lobby-selector.lore", playerLocale))
                .build(), LOBBY_SELECTOR_ID);
    }

    public ItemStack premium(Locale playerLocale) {
        return tag(ItemBuilder.of(materials.get(PREMIUM_ID))
                .name(locale.render("lobby.item.premium.name", playerLocale))
                .lore(locale.render("lobby.item.premium.lore", playerLocale))
                .build(), PREMIUM_ID);
    }

    public ItemStack profileHead(Player player, Locale playerLocale) {
        return tag(ItemBuilder.of(materials.get(PROFILE_ID))
                .skullOwner(player)
                .name(locale.render("lobby.item.profile.name", playerLocale,
                        Placeholder.unparsed("player", player.getName())))
                .lore(profileLore(player, playerLocale))
                .build(), PROFILE_ID);
    }

    public ItemStack profileMenuHead(Player player, Locale playerLocale) {
        return ItemBuilder.of(materials.get(PROFILE_ID))
                .skullOwner(player)
                .name(locale.render("lobby.item.profile.menu-name", playerLocale,
                        Placeholder.component("player", Component.text(player.getName(), ranks.nameColor(player)))))
                .lore(profileLore(player, playerLocale))
                .build();
    }

    public List<Component> profileLore(Player player, Locale playerLocale) {
        Optional<PlayerProfile> profile = profiles.getCached(player.getUniqueId());
        TagResolver name = Placeholder.unparsed("player", player.getName());
        List<Component> lore = new ArrayList<>();
        List<ProfileLayout.Line> head = settings.profile().head();
        Set<String> placed = ProfileLayout.placedAttributes(head);
        Optional<PlayerService> players = PlayerApi.service();
        Optional<NetworkPlayer> self = players.filter(service -> !service.attributes().all().isEmpty())
                .flatMap(service -> service.online(player.getUniqueId()));
        for (ProfileLayout.Line line : head) {
            switch (line.kind()) {
                case CURRENCIES -> currencies.shown().forEach(currency -> lore.add(currencyLine(profile, currency,
                        playerLocale)));
                case CURRENCY -> currencies.find(line.argument())
                        .ifPresent(currency -> lore.add(currencyLine(profile, currency, playerLocale)));
                case LEVEL -> {
                    if (config.systemEnabled(PrismarinSystems.LEVELS)) {
                        int level = profile.map(PlayerProfile::getLevel).orElse(0);
                        lore.add(locale.render("lobby.item.profile.lore.level", playerLocale,
                                Placeholder.unparsed("level", String.valueOf(level))));
                    }
                }
                case RANK -> lore.add(locale.render("lobby.item.profile.lore.rank", playerLocale,
                        player.getUniqueId(), Placeholder.component("rank", ranks.ownLabel(player))));
                case EMPTY -> lore.add(Component.empty());
                case TEXT -> lore.add(texts.render(line.argument(), playerLocale, player.getUniqueId(), name));
                case ATTRIBUTE -> self.flatMap(target -> players.get().attributes().profileLine(line.argument(),
                        player.getUniqueId(), target, playerLocale)).ifPresent(lore::add);
                case ATTRIBUTES -> self.ifPresent(target -> lore.addAll(players.get().attributes()
                        .profileLines(player.getUniqueId(), target, playerLocale, placed)));
                case CLAN -> SocialApi.clans().ifPresent(clans -> clans.clanOf(player.getUniqueId())
                        .ifPresent(clan -> lore.add(locale.render("lobby.item.profile.lore.clan", playerLocale,
                                Placeholder.component("tag", clans.formatTag(clan)),
                                Placeholder.unparsed("clan", clan.name()),
                                Placeholder.component("role", locale.render("clan.role."
                                        + clan.role(player.getUniqueId()).orElse(ClanRole.MEMBER).id(),
                                        playerLocale))))));
            }
        }
        if (!ProfileLayout.listsAttributes(head)) {
            self.ifPresent(target -> lore.addAll(players.get().attributes()
                    .profileLines(player.getUniqueId(), target, playerLocale, placed)));
        }
        return lore;
    }

    public ItemStack hiderItem(Locale playerLocale, boolean hidden) {
        Material material = hidden ? hiddenMaterial : materials.get(HIDER_ID);
        String nameKey = hidden ? "lobby.item.hider.hidden" : "lobby.item.hider.visible";
        return tag(ItemBuilder.of(material)
                .name(locale.render(nameKey, playerLocale))
                .lore(locale.render("lobby.item.hider.lore", playerLocale))
                .build(), HIDER_ID);
    }

    public ItemStack custom(Player player, Locale playerLocale, CustomItem item) {
        TagResolver name = Placeholder.unparsed("player", player.getName());
        List<Component> lore = item.lore().stream()
                .map(line -> texts.render(line, playerLocale, player.getUniqueId(), name))
                .toList();
        ItemBuilder builder = ItemBuilder.of(customMaterials.getOrDefault(item.id(), Material.PAPER))
                .glow(item.glow())
                .lore(lore);
        if (!item.name().isEmpty()) {
            builder.name(texts.render(item.name(), playerLocale, player.getUniqueId(), name));
        }
        ItemStack stack = builder.build();
        if (item.customModelData() > 0) {
            stack.editMeta(meta -> meta.setCustomModelData(item.customModelData()));
        }
        return tag(stack, CUSTOM_PREFIX + item.id());
    }

    public Optional<CustomItem> customItem(String id) {
        if (!id.startsWith(CUSTOM_PREFIX)) {
            return Optional.empty();
        }
        String name = id.substring(CUSTOM_PREFIX.length());
        return settings.customItems().stream().filter(item -> item.id().equals(name)).findFirst();
    }

    public Optional<String> itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    public boolean isLobbyItem(ItemStack item) {
        return itemId(item).isPresent();
    }

    private Component currencyLine(Optional<PlayerProfile> profile, Currency currency, Locale playerLocale) {
        long amount = profile.map(value -> value.balance(currency)).orElse(0L);
        return currencies.render("lobby.item.profile.lore", currency, amount, playerLocale);
    }

    private int slot(String id) {
        return settings.item(id).slot();
    }

    private ItemStack tag(ItemStack item, String id) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id));
        return item;
    }

    private static Material resolve(Logger logger, String path, String name, Material fallback) {
        Optional<Material> material = ConfigText.material(name);
        if (material.isEmpty()) {
            logger.warning(path + ": '" + name + "' is no item material, using " + fallback.name());
        }
        return material.orElse(fallback);
    }
}
