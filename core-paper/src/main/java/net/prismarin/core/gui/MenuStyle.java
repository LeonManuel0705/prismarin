package net.prismarin.core.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.prismarin.api.locale.LocaleService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class MenuStyle {

    private final MenuService menus;
    private final LocaleService locale;

    @Inject
    public MenuStyle(MenuService menus, LocaleService locale) {
        this.menus = menus;
        this.locale = locale;
    }

    public ItemStack filler() {
        return pane(Material.BLACK_STAINED_GLASS_PANE);
    }

    public ItemStack accent() {
        return pane(Material.CYAN_STAINED_GLASS_PANE);
    }

    public void fill(Menu menu) {
        MenuButton filler = MenuButton.display(filler());
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.button(slot, filler);
        }
    }

    public void accent(Menu menu, Collection<Integer> slots) {
        MenuButton accent = MenuButton.display(accent());
        for (int slot : slots) {
            if (slot >= 0 && slot < menu.size()) {
                menu.button(slot, accent);
            }
        }
    }

    public MenuButton back(Locale target, Consumer<Player> action) {
        return MenuButton.of(ItemBuilder.of(Material.ARROW).name(locale.render("menu.back", target)).build(), action);
    }

    public MenuButton close(Locale target) {
        return MenuButton.of(ItemBuilder.of(Material.BARRIER).name(locale.render("menu.close", target)).build(),
                menus::close);
    }

    private static ItemStack pane(Material material) {
        return ItemBuilder.of(material).name(Component.space()).build();
    }
}
