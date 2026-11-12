package net.prismarin.shared.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.prismarin.api.stats.Leaderboard;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.StatOrder;

public final class StatSnapshot {

    private final Leaderboard board;
    private final long[] values;
    private final StatOrder order;
    private final boolean shared;
    private final Map<UUID, Integer> positions;
    private final boolean listedOnly;

    public StatSnapshot(Leaderboard board, long[] values, StatOrder order, boolean shared) {
        this(board, values, order, shared, false);
    }

    private StatSnapshot(Leaderboard board, long[] values, StatOrder order, boolean shared, boolean listedOnly) {
        this.listedOnly = listedOnly;
        this.board = board;
        this.values = values.clone();
        this.order = order;
        this.shared = shared;
        Map<UUID, Integer> ranks = new HashMap<>();
        for (LeaderboardEntry entry : board.entries()) {
            ranks.put(entry.player(), entry.rank());
        }
        this.positions = Map.copyOf(ranks);
    }

    public static StatSnapshot listed(Leaderboard board) {
        return new StatSnapshot(board, new long[0], StatOrder.HIGHEST, false, true);
    }

    public boolean listedOnly() {
        return listedOnly;
    }

    public Leaderboard board() {
        return board;
    }

    public int players() {
        return values.length;
    }

    public OptionalInt rank(UUID player, Long value) {
        Integer listed = positions.get(player);
        if (listed != null) {
            return OptionalInt.of(listed);
        }
        if (value == null || listedOnly) {
            return OptionalInt.empty();
        }
        int rank = TopList.rank(values, value, order);
        if (!shared && rank <= board.entries().size()) {
            rank = board.entries().size() + 1;
        }
        return OptionalInt.of(rank);
    }
}
