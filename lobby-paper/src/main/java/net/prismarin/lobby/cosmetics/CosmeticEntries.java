package net.prismarin.lobby.cosmetics;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticApi;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.CoreModules;
import net.prismarin.core.ModuleState;
import net.prismarin.core.cosmetics.CosmeticMenus;
import net.prismarin.core.cosmetics.PaperCosmetics;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.api.LobbyMenuEntry;
import net.prismarin.lobby.extension.LobbyExtensions;
import net.prismarin.lobby.menu.LobbyMenus;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class CosmeticEntries {

    public static final String ID = "prismarin-cosmetics";
    private static final int ORDER = 10;

    private final LobbyExtensions extensions;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ModuleState modules;
    private final Injector injector;
    private final Provider<LobbyMenus> menus;
    private final List<Subscription> registered = new ArrayList<>();

    @Inject
    public CosmeticEntries(LobbyExtensions extensions, LocaleService locale, ProfileService profiles,
                           ModuleState modules, Injector injector, Provider<LobbyMenus> menus) {
        this.extensions = extensions;
        this.locale = locale;
        this.profiles = profiles;
        this.modules = modules;
        this.injector = injector;
        this.menus = menus;
    }

    public void register() {
        service().filter(cosmetics -> cosmetics.settings().profileEntry())
                .ifPresent(cosmetics -> registered.add(extensions.profile().register(entry(cosmetics))));
    }

    public void unregister() {
        registered.forEach(Subscription::unsubscribe);
        registered.clear();
    }

    public boolean open(Player player, Consumer<Player> back) {
        if (service().isEmpty()) {
            return false;
        }
        injector.getInstance(CosmeticMenus.class).open(player, back);
        return true;
    }

    private Optional<PaperCosmetics> service() {
        if (!modules.enabled(CoreModules.COSMETICS)) {
            return Optional.empty();
        }
        return CosmeticApi.service().filter(PaperCosmetics.class::isInstance).map(PaperCosmetics.class::cast);
    }

    private LobbyMenuEntry entry(PaperCosmetics cosmetics) {
        return new LobbyMenuEntry() {
            @Override
            public String id() {
                return ID;
            }

            @Override
            public ItemStack icon(Player viewer) {
                Locale target = PlayerLocales.of(viewer, profiles);
                List<Cosmetic> listed = cosmetics.cosmetics().stream()
                        .filter(cosmetic -> cosmetics.slots().contains(cosmetic.slot()))
                        .filter(cosmetic -> !cosmetic.hidden() || cosmetics.ownsHere(viewer, cosmetic))
                        .toList();
                long owned = listed.stream().filter(cosmetic -> cosmetics.ownsHere(viewer, cosmetic)).count();
                List<Component> lore = List.of(
                        locale.render("cosmetics.menu.profile-lore", target,
                                Placeholder.unparsed("owned", Long.toString(owned)),
                                Placeholder.unparsed("total", Integer.toString(listed.size()))),
                        Component.empty(),
                        locale.render("cosmetics.menu.profile-click", target));
                return ItemBuilder.of(Material.CHEST)
                        .name(locale.render("cosmetics.menu.profile-name", target))
                        .lore(lore)
                        .build();
            }

            @Override
            public void click(ClickContext context) {
                open(context.player(), player -> menus.get().openProfile(player));
            }

            @Override
            public int order() {
                return ORDER;
            }
        };
    }
}
