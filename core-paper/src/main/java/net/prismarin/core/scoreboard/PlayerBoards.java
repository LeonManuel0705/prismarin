package net.prismarin.core.scoreboard;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

@Singleton
public final class PlayerBoards {

    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public Scoreboard board(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(), uuid -> {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
            return board;
        });
    }

    public Optional<Scoreboard> existing(UUID uuid) {
        return Optional.ofNullable(boards.get(uuid));
    }

    public void remove(UUID uuid) {
        boards.remove(uuid);
    }
}
