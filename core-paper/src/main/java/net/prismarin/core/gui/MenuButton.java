package net.prismarin.core.gui;

import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record MenuButton(ItemStack icon, Consumer<ClickContext> action) {

    public static final Consumer<ClickContext> NOTHING = context -> context.player().updateInventory();

    public MenuButton {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(action, "action");
    }

    public static MenuButton display(ItemStack icon) {
        return new MenuButton(icon, NOTHING);
    }

    public static MenuButton of(ItemStack icon, Consumer<Player> action) {
        Objects.requireNonNull(action, "action");
        return new MenuButton(icon, context -> action.accept(context.player()));
    }
}
