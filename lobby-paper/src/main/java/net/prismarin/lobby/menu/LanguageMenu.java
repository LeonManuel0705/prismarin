package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.moderation.ModerationChannel;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.player.LobbyPlayerSetup;
import net.prismarin.lobby.util.ConfigText;
import net.prismarin.lobby.util.Languages;
import net.prismarin.lobby.world.LobbyWorlds;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.locale.YamlLocaleService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class LanguageMenu {

    private static final int PER_ROW = 5;
    private static final int MAX_ENTRIES = 20;
    private static final Sound CHANGED = Sound.sound(Key.key("block.note_block.chime"), Sound.Source.MASTER,
            0.6f, 1.4f);

    private final LobbyPlugin plugin;
    private final MenuService menuService;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;
    private final MenuKit kit;
    private final LobbyPlayerSetup setup;
    private final LobbyWorlds worlds;
    private final ModerationChannel channel;

    @Inject
    public LanguageMenu(LobbyPlugin plugin, MenuService menuService, LocaleService locale, ProfileService profiles,
                        CoreConfig config, MenuKit kit, LobbyPlayerSetup setup, LobbyWorlds worlds,
                        ModerationChannel channel) {
        this.plugin = plugin;
        this.channel = channel;
        this.menuService = menuService;
        this.locale = locale;
        this.profiles = profiles;
        this.config = config;
        this.kit = kit;
        this.setup = setup;
        this.worlds = worlds;
    }

    public List<String> languages() {
        return List.copyOf(locale.languages());
    }

    public String current(Player player) {
        return Languages.resolve(languages(), PlayerLocales.of(player, profiles), config.defaultLocale());
    }

    public String name(String tag) {
        return locale.raw(YamlLocaleService.LANGUAGE_NAME, Locale.forLanguageTag(tag));
    }

    public Material icon(String tag) {
        return ConfigText.material(locale.raw(YamlLocaleService.LANGUAGE_ICON, Locale.forLanguageTag(tag)),
                Material.PAPER);
    }

    public void open(Player player, Consumer<Player> back) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        List<String> languages = languages();
        int count = Math.min(languages.size(), MAX_ENTRIES);
        int contentRows = MenuLayout.rowsFor(count, PER_ROW);
        Menu menu = new Menu(locale.render("lobby.profile.language.title", playerLocale), contentRows + 2);
        kit.fill(menu);
        String current = current(player);
        List<Integer> slots = MenuLayout.centeredSlots(1, count, PER_ROW);
        for (int i = 0; i < count; i++) {
            menu.button(slots.get(i), button(playerLocale, languages.get(i), current, back));
        }
        int bottom = (contentRows + 1) * 9;
        menu.button(bottom, kit.back(playerLocale, back));
        menu.button(bottom + 8, kit.close(playerLocale));
        menuService.open(player, menu);
    }

    public void switchTo(Player player, String tag, Consumer<Player> after) {
        Locale fallback = PlayerLocales.of(player, profiles);
        profiles.modify(player.getUniqueId(), profile -> {
                    profile.setLocale(tag);
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
                    channel.sendLocale(player, Locale.forLanguageTag(tag));
                    if (worlds.isLobbyWorld(player.getWorld())) {
                        setup.refreshItems(player);
                    }
                    player.sendMessage(locale.render("lobby.profile.language.changed", Locale.forLanguageTag(tag),
                            Placeholder.unparsed("language", name(tag))));
                    player.playSound(CHANGED, Sound.Emitter.self());
                    after.accept(player);
                }));
    }

    private MenuButton button(Locale playerLocale, String tag, String current, Consumer<Player> back) {
        boolean selected = tag.equals(current);
        ItemStack icon = ItemBuilder.of(icon(tag))
                .name(locale.render("lobby.profile.language.entry", playerLocale,
                        Placeholder.unparsed("language", name(tag))))
                .lore(locale.render(selected ? "lobby.profile.language.selected" : "lobby.profile.language.select",
                        playerLocale))
                .glow(selected)
                .build();
        if (selected) {
            return kit.decoration(icon);
        }
        return new MenuButton(icon, context -> switchTo(context.player(), tag, back));
    }
}
