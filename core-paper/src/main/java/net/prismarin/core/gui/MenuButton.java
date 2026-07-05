package net.prismarin.core.gui;

import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;

public record MenuButton(ItemStack icon, Consumer<ClickContext> action) {

    public MenuButton {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(action, "action");
    }
}
