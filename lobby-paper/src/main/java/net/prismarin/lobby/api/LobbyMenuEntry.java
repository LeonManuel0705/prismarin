package net.prismarin.lobby.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.prismarin.core.gui.ClickContext;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface LobbyMenuEntry {

    String id();

    ItemStack icon(Player viewer);

    void click(ClickContext context);

    default boolean visible(Player viewer) {
        return true;
    }

    default int order() {
        return 0;
    }

    static LobbyMenuEntry of(String id, Function<Player, ItemStack> icon, Consumer<ClickContext> action) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(action, "action");
        return new LobbyMenuEntry() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public ItemStack icon(Player viewer) {
                return icon.apply(viewer);
            }

            @Override
            public void click(ClickContext context) {
                action.accept(context);
            }
        };
    }
}
