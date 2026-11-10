package net.prismarin.api.moderation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentService {

    CompletableFuture<Punishment> punish(UUID target, String targetName, PunishmentType type, String reason,
                                         String actor, Duration duration);

    CompletableFuture<Optional<Punishment>> active(UUID target, PunishmentType type);

    CompletableFuture<List<Punishment>> history(UUID target);

    CompletableFuture<Optional<Punishment>> revoke(UUID target, PunishmentType type, String actor);
}
