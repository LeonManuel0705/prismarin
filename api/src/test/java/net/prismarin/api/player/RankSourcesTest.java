package net.prismarin.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class RankSourcesTest {

    private static RankDisplay rank(String label) {
        return new RankDisplay(label, Component.text(label), Component.empty(), NamedTextColor.GOLD, null, 10);
    }

    @Test
    void theHighestPrioritySourceWithAnAnswerWins() {
        RankSources sources = new RankSources();
        UUID player = UUID.randomUUID();
        sources.register(uuid -> Optional.of(rank("Low")), 1);
        Subscription high = sources.register(uuid -> Optional.of(rank("High")), 5);
        sources.register(uuid -> Optional.empty(), 9);
        sources.register(uuid -> {
            throw new IllegalStateException("broken plugin");
        }, 10);
        assertEquals("High", sources.rank(player).orElseThrow().label());
        high.unsubscribe();
        assertEquals("Low", sources.rank(player).orElseThrow().label());
    }

    @Test
    void unknownRanksAreSkipped() {
        RankSources sources = new RankSources();
        sources.register(uuid -> Optional.of(RankDisplay.NONE));
        assertTrue(sources.rank(UUID.randomUUID()).isEmpty());
    }
}
