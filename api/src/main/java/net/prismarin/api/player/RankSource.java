package net.prismarin.api.player;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface RankSource {

    Optional<RankDisplay> rank(UUID player);
}
