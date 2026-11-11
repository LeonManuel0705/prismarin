package net.prismarin.core.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Menu {

    private final Component title;
    private final int rows;
    private final Map<Integer, MenuButton> buttons = new HashMap<>();
    private volatile Consumer<Player> closeAction;

    public Menu(Component title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6");
        }
        this.title = Objects.requireNonNull(title, "title");
        this.rows = rows;
    }

    public Component title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    public void button(int slot, MenuButton button) {
        if (slot < 0 || slot >= size()) {
            throw new IllegalArgumentException("slot " + slot + " is outside of the menu");
        }
        buttons.put(slot, Objects.requireNonNull(button, "button"));
    }

    public Menu fill(ItemStack pane) {
        MenuButton filler = MenuButton.display(pane);
        for (int slot = 0; slot < size(); slot++) {
            buttons.putIfAbsent(slot, filler);
        }
        return this;
    }

    public Menu onClose(Consumer<Player> action) {
        this.closeAction = action;
        return this;
    }

    public MenuButton buttonAt(int slot) {
        return buttons.get(slot);
    }

    public Map<Integer, MenuButton> buttons() {
        return Collections.unmodifiableMap(buttons);
    }

    protected void clearButtons() {
        buttons.clear();
    }

    protected void prepare(Player viewer) {
    }

    void closed(Player viewer) {
        Consumer<Player> action = closeAction;
        if (action != null) {
            action.accept(viewer);
        }
    }
}
