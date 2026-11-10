package net.prismarin.shared.moderation;

import com.google.gson.Gson;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentIssuedEvent;
import net.prismarin.api.moderation.PunishmentRevokedEvent;
import net.prismarin.api.moderation.PunishmentService;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.storage.DocumentStore;

public final class DocumentPunishmentService implements PunishmentService {

    static final String COLLECTION = "punishments";
    private static final String ID_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    private final DocumentStore documents;
    private final Executor executor;
    private final EventBus events;
    private final Clock clock;
    private final Gson gson = new Gson();
    private final SecureRandom random = new SecureRandom();

    public DocumentPunishmentService(DocumentStore documents, Executor executor, EventBus events, Clock clock) {
        this.documents = documents;
        this.executor = executor;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<Punishment> punish(UUID target, String targetName, PunishmentType type, String reason,
                                                String actor, Duration duration) {
        return CompletableFuture.supplyAsync(() -> {
            long now = clock.millis();
            if (type.lasting()) {
                load(target).stream()
                        .filter(existing -> existing.type() == type && existing.inForce(now))
                        .forEach(existing -> store(existing.revoke(actor, now)));
            }
            long expires = type.lasting() && duration != null ? now + duration.toMillis() : 0L;
            Punishment punishment = new Punishment(newId(), target, targetName, type, reason, actor, now, expires,
                    type.lasting(), "", 0L);
            store(punishment);
            events.publish(new PunishmentIssuedEvent(punishment));
            return punishment;
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> active(UUID target, PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> {
            long now = clock.millis();
            return load(target).stream()
                    .filter(punishment -> punishment.type() == type && punishment.inForce(now))
                    .max(Comparator.comparingLong(Punishment::createdAt));
        }, executor);
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID target) {
        return CompletableFuture.supplyAsync(() -> load(target).stream()
                .sorted(Comparator.comparingLong(Punishment::createdAt).reversed())
                .toList(), executor);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> revoke(UUID target, PunishmentType type, String actor) {
        return CompletableFuture.supplyAsync(() -> {
            long now = clock.millis();
            Optional<Punishment> revoked = Optional.empty();
            for (Punishment punishment : load(target)) {
                if (punishment.type() == type && punishment.inForce(now)) {
                    Punishment updated = punishment.revoke(actor, now);
                    store(updated);
                    revoked = Optional.of(updated);
                }
            }
            revoked.ifPresent(punishment -> events.publish(new PunishmentRevokedEvent(punishment)));
            return revoked;
        }, executor);
    }

    private List<Punishment> load(UUID target) {
        return documents.findBy(COLLECTION, "target", target.toString()).stream()
                .map(json -> gson.fromJson(json, Punishment.class))
                .toList();
    }

    private void store(Punishment punishment) {
        documents.save(COLLECTION, punishment.id(), gson.toJson(punishment));
    }

    private String newId() {
        StringBuilder id = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }
}
