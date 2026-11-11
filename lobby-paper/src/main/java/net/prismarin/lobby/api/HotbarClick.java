package net.prismarin.lobby.api;

import java.util.Objects;
import org.bukkit.entity.Player;

public record HotbarClick(Player player, boolean left, boolean sneaking) {

    public HotbarClick {
        Objects.requireNonNull(player, "player");
    }

    public boolean right() {
        return !left;
    }
}
