package net.prismarin.api.moderation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentService {

    CompletableFuture<Punishment> punish(UUID target, String targetName, PunishmentType type, String reason,
                                         String actor, Duration duration);

    default CompletableFuture<Punishment> punish(PunishmentRequest request) {
        return punish(request.target(), request.targetName(), request.type(), request.reason(), request.actor(),
                request.duration());
    }

    CompletableFuture<Optional<Punishment>> active(UUID target, PunishmentType type);

    default CompletableFuture<Optional<Punishment>> activeForAddress(String addressHash) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    CompletableFuture<List<Punishment>> history(UUID target);

    default CompletableFuture<List<Punishment>> inForce(PunishmentType type) {
        return CompletableFuture.completedFuture(List.of());
    }

    CompletableFuture<Optional<Punishment>> revoke(UUID target, PunishmentType type, String actor);

    default CompletableFuture<Optional<Punishment>> revoke(UUID target, PunishmentType type, String actor,
                                                           String reason) {
        return revoke(target, type, actor);
    }
}
