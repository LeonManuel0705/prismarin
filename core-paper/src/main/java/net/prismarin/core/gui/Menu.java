package net.prismarin.core.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class Menu {

    private final Component title;
    private final int rows;
    private final Map<Integer, MenuButton> buttons = new HashMap<>();

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
}
