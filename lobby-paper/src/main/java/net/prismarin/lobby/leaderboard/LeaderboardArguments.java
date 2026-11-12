package net.prismarin.lobby.leaderboard;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.stats.StatKeys;
import net.prismarin.api.stats.StatPeriod;

public final class LeaderboardArguments {

    public sealed interface Result permits Create, Invalid {
    }

    public record Create(String id, String stat, StatPeriod period, int size) implements Result {
    }

    public record Invalid(String messageKey, String value) implements Result {
    }

    private LeaderboardArguments() {
        throw new AssertionError();
    }

    public static Result create(String[] args, Set<StatPeriod> periods) {
        if (args.length < 3 || args.length > 5) {
            return new Invalid("lobby.leaderboard.usage", "");
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!LeaderboardBoard.validId(id)) {
            return new Invalid("lobby.leaderboard.invalid-id", args[1]);
        }
        if (!StatKeys.valid(args[2])) {
            return new Invalid("lobby.leaderboard.invalid-stat", args[2]);
        }
        StatPeriod period = StatPeriod.ALL_TIME;
        int size = LeaderboardBoard.DEFAULT_SIZE;
        for (int index = 3; index < args.length; index++) {
            String value = args[index];
            Optional<StatPeriod> parsed = StatPeriod.byId(value);
            if (parsed.isPresent()) {
                if (!periods.contains(parsed.get())) {
                    return new Invalid("lobby.leaderboard.invalid-period", value);
                }
                period = parsed.get();
            } else if (value.matches("\\d{1,3}")) {
                size = Integer.parseInt(value);
                if (size < 1 || size > LeaderboardBoard.MAX_SIZE) {
                    return new Invalid("lobby.leaderboard.invalid-size", value);
                }
            } else {
                return new Invalid("lobby.leaderboard.invalid-period", value);
            }
        }
        return new Create(id, StatKeys.key(args[2]), period, size);
    }
}
