package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.lobby.LobbyPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class MenuKit {

    public static final Consumer<ClickContext> DECORATION = context -> context.player().updateInventory();

    private final LobbyPlugin plugin;
    private final LocaleService locale;

    @Inject
    public MenuKit(LobbyPlugin plugin, LocaleService locale) {
        this.plugin = plugin;
        this.locale = locale;
    }

    public void fill(Menu menu) {
        ItemStack pane = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(Component.space()).build();
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.button(slot, new MenuButton(pane, DECORATION));
        }
    }

    public MenuButton decoration(ItemStack icon) {
        return new MenuButton(icon, DECORATION);
    }

    public MenuButton back(Locale target, Consumer<Player> action) {
        return new MenuButton(ItemBuilder.of(Material.ARROW).name(locale.render("menu.back", target)).build(),
                context -> action.accept(context.player()));
    }

    public MenuButton close(Locale target) {
        return new MenuButton(ItemBuilder.of(Material.BARRIER).name(locale.render("menu.close", target)).build(),
                context -> closeLater(context.player()));
    }

    public void closeLater(Player player) {
        Runnable close = player::closeInventory;
        plugin.getServer().getScheduler().runTask(plugin, close);
    }
}
