package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuStyle;
import net.prismarin.lobby.LobbyPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class MenuKit {

    public static final Consumer<ClickContext> DECORATION = MenuButton.NOTHING;

    private final LobbyPlugin plugin;
    private final MenuStyle style;

    @Inject
    public MenuKit(LobbyPlugin plugin, MenuStyle style) {
        this.plugin = plugin;
        this.style = style;
    }

    public void fill(Menu menu) {
        style.fill(menu);
    }

    public void accent(Menu menu, List<Integer> slots) {
        style.accent(menu, slots);
    }

    public MenuButton decoration(ItemStack icon) {
        return MenuButton.display(icon);
    }

    public MenuButton back(Locale target, Consumer<Player> action) {
        return style.back(target, action);
    }

    public MenuButton close(Locale target) {
        return style.close(target);
    }

    public void closeLater(Player player) {
        Runnable close = player::closeInventory;
        plugin.getServer().getScheduler().runTask(plugin, close);
    }
}
