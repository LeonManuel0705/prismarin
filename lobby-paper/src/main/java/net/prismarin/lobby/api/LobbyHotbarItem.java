package net.prismarin.lobby.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface LobbyHotbarItem {

    String id();

    int slot();

    ItemStack icon(Player player);

    void click(HotbarClick click);

    default boolean visible(Player player) {
        return true;
    }

    static LobbyHotbarItem of(String id, int slot, Function<Player, ItemStack> icon, Consumer<HotbarClick> action) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(action, "action");
        return new LobbyHotbarItem() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int slot() {
                return slot;
            }

            @Override
            public ItemStack icon(Player player) {
                return icon.apply(player);
            }

            @Override
            public void click(HotbarClick click) {
                action.accept(click);
            }
        };
    }
}
