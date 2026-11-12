package net.prismarin.api.moderation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AltService {

    CompletableFuture<List<AltAccount>> alts(UUID player);

    default CompletableFuture<List<AltAccount>> bannedAlts(UUID player) {
        return alts(player).thenApply(found -> found.stream().filter(AltAccount::banned).toList());
    }

    default CompletableFuture<Boolean> related(UUID first, UUID second) {
        return alts(first).thenApply(found -> found.stream().anyMatch(account -> account.uuid().equals(second)));
    }
}
