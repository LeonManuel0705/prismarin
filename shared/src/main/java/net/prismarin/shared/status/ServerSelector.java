package net.prismarin.shared.status;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerStatus;

public final class ServerSelector {

    private ServerSelector() {
        throw new AssertionError();
    }

    public static Optional<NetworkServer> pick(List<NetworkServer> servers,
                                               Function<String, Optional<ServerStatus>> statuses) {
        return servers.stream()
                .map(server -> new Candidate(server, statuses.apply(server.name()).orElse(null)))
                .filter(candidate -> candidate.status() != null && candidate.status().joinable())
                .sorted(Comparator.comparingInt((Candidate candidate) -> rank(candidate.status().state()))
                        .thenComparing(Comparator.comparingInt((Candidate candidate) -> candidate.status().players())
                                .reversed())
                        .thenComparing(candidate -> candidate.server().name()))
                .map(Candidate::server)
                .findFirst();
    }

    private static int rank(GameState state) {
        return switch (state) {
            case STARTING -> 0;
            case WAITING -> 1;
            default -> 2;
        };
    }

    private record Candidate(NetworkServer server, ServerStatus status) {
    }
}
