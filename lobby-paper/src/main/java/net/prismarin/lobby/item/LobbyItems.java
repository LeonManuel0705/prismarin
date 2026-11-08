package net.prismarin.lobby.item;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.util.CurrencyDisplay;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.lobby.player.PlayerLocales;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

@Singleton
public final class LobbyItems {

    public static final int NAVIGATOR_SLOT = 0;
    public static final int PROFILE_SLOT = 4;
    public static final int HIDER_SLOT = 8;
    public static final String NAVIGATOR_ID = "navigator";
    public static final String PROFILE_ID = "profile";
    public static final String HIDER_ID = "hider";

    private final LocaleService locale;
    private final ProfileService profiles;
    private final LobbySettings settings;
    private final CurrencyDisplay currencies;
    private final CoreConfig config;
    private final NamespacedKey key;

    @Inject
    public LobbyItems(LobbyPlugin plugin, LocaleService locale, ProfileService profiles, LobbySettings settings,
                      CurrencyDisplay currencies, CoreConfig config) {
        this.locale = locale;
        this.profiles = profiles;
        this.settings = settings;
        this.currencies = currencies;
        this.config = config;
        this.key = new NamespacedKey(plugin, "lobby-item");
    }

    public void give(Player player, boolean playersHidden) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        PlayerInventory inventory = player.getInventory();
        if (settings.enabled(LobbySettings.NAVIGATOR)) {
            inventory.setItem(NAVIGATOR_SLOT, navigator(playerLocale));
        }
        if (settings.enabled(LobbySettings.PROFILE_MENU)) {
            inventory.setItem(PROFILE_SLOT, profileHead(player, playerLocale));
        }
        if (settings.enabled(LobbySettings.PLAYER_HIDER)) {
            inventory.setItem(HIDER_SLOT, hiderItem(playerLocale, playersHidden));
        }
    }

    public ItemStack navigator(Locale playerLocale) {
        return tag(ItemBuilder.of(Material.COMPASS)
                .name(locale.render("lobby.item.navigator.name", playerLocale))
                .lore(locale.render("lobby.item.navigator.lore", playerLocale))
                .build(), NAVIGATOR_ID);
    }

    public ItemStack profileHead(Player player, Locale playerLocale) {
        Optional<PlayerProfile> profile = profiles.getCached(player.getUniqueId());
        List<Component> lore = new ArrayList<>();
        for (Currency currency : currencies.shown()) {
            long amount = profile.map(value -> value.balance(currency)).orElse(0L);
            lore.add(currencies.render("lobby.item.profile.lore", currency, amount, playerLocale));
        }
        if (config.systemEnabled(PrismarinSystems.LEVELS)) {
            int level = profile.map(PlayerProfile::getLevel).orElse(0);
            lore.add(locale.render("lobby.item.profile.lore.level", playerLocale,
                    Placeholder.unparsed("level", String.valueOf(level))));
        }
        return tag(ItemBuilder.of(Material.PLAYER_HEAD)
                .skullOwner(player)
                .name(locale.render("lobby.item.profile.name", playerLocale,
                        Placeholder.unparsed("player", player.getName())))
                .lore(lore)
                .build(), PROFILE_ID);
    }

    public ItemStack hiderItem(Locale playerLocale, boolean hidden) {
        Material material = hidden ? Material.GRAY_DYE : Material.LIME_DYE;
        String nameKey = hidden ? "lobby.item.hider.hidden" : "lobby.item.hider.visible";
        return tag(ItemBuilder.of(material)
                .name(locale.render(nameKey, playerLocale))
                .lore(locale.render("lobby.item.hider.lore", playerLocale))
                .build(), HIDER_ID);
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

    private ItemStack tag(ItemStack item, String id) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id));
        return item;
    }
}
