package net.prismarin.lobby.leaderboard;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.prismarin.api.stats.StatKeys;
import net.prismarin.api.stats.StatPeriod;

public record LeaderboardBoard(String id, String stat, StatPeriod period, int size, String world, double x, double y,
                               double z, float yaw, String title, String line) {

    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 20;

    public LeaderboardBoard {
        if (!validId(id)) {
            throw new IllegalArgumentException("'" + id + "' is no leaderboard id, use " + ID.pattern());
        }
        id = id.toLowerCase(Locale.ROOT);
        stat = StatKeys.key(stat);
        period = Objects.requireNonNullElse(period, StatPeriod.ALL_TIME);
        size = Math.clamp(size, 1, MAX_SIZE);
        Objects.requireNonNull(world, "world");
        title = title == null ? "" : title;
        line = line == null ? "" : line;
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id.toLowerCase(Locale.ROOT)).matches();
    }

    public LeaderboardBoard at(String targetWorld, double targetX, double targetY, double targetZ, float targetYaw) {
        return new LeaderboardBoard(id, stat, period, size, targetWorld, targetX, targetY, targetZ, targetYaw, title,
                line);
    }

    public int chunkX() {
        return (int) Math.floor(x) >> 4;
    }

    public int chunkZ() {
        return (int) Math.floor(z) >> 4;
    }
}
