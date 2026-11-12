package net.prismarin.shared.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.prismarin.api.moderation.AltAccount;
import net.prismarin.api.moderation.AltService;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentService;
import net.prismarin.api.moderation.PunishmentType;

public final class DocumentAltService implements AltService {

    private final AltStore store;
    private final PunishmentService punishments;
    private final Executor executor;

    public DocumentAltService(AltStore store, PunishmentService punishments, Executor executor) {
        this.store = Objects.requireNonNull(store, "store");
        this.punishments = Objects.requireNonNull(punishments, "punishments");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletableFuture<Void> record(UUID player, String name, String addressHash) {
        return CompletableFuture.runAsync(() -> store.record(player, name, addressHash), executor);
    }

    public CompletableFuture<Integer> prune() {
        return CompletableFuture.supplyAsync(store::prune, executor);
    }

    @Override
    public CompletableFuture<List<AltAccount>> alts(UUID player) {
        Objects.requireNonNull(player, "player");
        return CompletableFuture.supplyAsync(() -> store.alts(player), executor).thenCompose(links -> {
            List<CompletableFuture<AltAccount>> accounts = new ArrayList<>();
            for (AltStore.Link link : links) {
                CompletableFuture<Optional<Punishment>> ban = punishments.active(link.uuid(), PunishmentType.BAN)
                        .exceptionally(error -> Optional.empty());
                CompletableFuture<Optional<Punishment>> mute = punishments.active(link.uuid(), PunishmentType.MUTE)
                        .exceptionally(error -> Optional.empty());
                accounts.add(ban.thenCombine(mute, (banned, muted) -> new AltAccount(link.uuid(), link.name(),
                        link.firstSeen(), link.lastSeen(), link.sharedAddresses(), banned.isPresent(),
                        muted.isPresent())));
            }
            return CompletableFuture.allOf(accounts.toArray(CompletableFuture[]::new))
                    .thenApply(done -> accounts.stream().map(CompletableFuture::join).toList());
        });
    }
}
