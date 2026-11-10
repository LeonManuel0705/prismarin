package net.prismarin.api.nick;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NickService {

    String SETTING_NAME = "nick.name";

    String SETTING_SKIN = "nick.skin";

    String SETTING_AUTO = "nick.auto";

    boolean isNicked(UUID player);

    Optional<String> shownNick(UUID player);

    Optional<String> storedNick(UUID player);

    Optional<String> realName(UUID player);

    Optional<UUID> findByNick(String nick);

    Collection<UUID> nickedPlayers();

    CompletableFuture<Optional<UUID>> lookupNick(String nick);

    CompletableFuture<NickResult> nickRandom(UUID player);

    CompletableFuture<NickResult> nick(UUID player, String name);

    CompletableFuture<NickResult> unnick(UUID player);

    boolean autoNick(UUID player);

    CompletableFuture<Void> setAutoNick(UUID player, boolean enabled);

    void markInRound(UUID player, boolean inRound);

    void clearRoundMarker(UUID player);

    boolean inRound(UUID player);

    boolean nicksShownHere();
}
