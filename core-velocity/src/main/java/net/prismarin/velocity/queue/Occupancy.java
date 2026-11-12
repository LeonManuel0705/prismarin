package net.prismarin.velocity.queue;

import java.util.Optional;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.ServerStatus;

public final class Occupancy {

    private Occupancy() {
        throw new AssertionError();
    }

    public static Optional<QueueBook.Slot> slot(String server, int connected, Optional<ServerStatus> status,
                                                boolean known, QueueRules rules, int connectedAtReport) {
        if (!known) {
            return Optional.of(new QueueBook.Slot(server, free(rules.capacity(0), connected), rank(GameState.UNKNOWN),
                    connected));
        }
        Optional<ServerStatus> online = status.filter(ServerStatus::online);
        if (online.isEmpty() && connected == 0) {
            return Optional.empty();
        }
        GameState state = online.map(ServerStatus::state).orElse(GameState.UNKNOWN);
        if (rules.respectGameState() && !state.joinable()) {
            return Optional.empty();
        }
        int players = players(connected, online.map(ServerStatus::players).orElse(connected), connectedAtReport);
        int capacity = rules.capacity(online.map(ServerStatus::maxPlayers).orElse(0));
        return Optional.of(new QueueBook.Slot(server, free(capacity, players), rank(state), players));
    }

    public static int players(int connectedNow, int reported, int connectedAtReport) {
        if (connectedAtReport < 0) {
            return Math.max(connectedNow, reported);
        }
        return connectedNow + Math.max(0, reported - connectedAtReport);
    }

    public static int free(int capacity, int players) {
        if (capacity <= 0) {
            return QueueBook.Slot.UNLIMITED;
        }
        return Math.max(0, capacity - players);
    }

    public static int rank(GameState state) {
        return switch (state) {
            case STARTING -> 0;
            case WAITING -> 1;
            default -> 2;
        };
    }
}
