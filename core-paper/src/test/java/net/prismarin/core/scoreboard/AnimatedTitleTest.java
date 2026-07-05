package net.prismarin.core.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class AnimatedTitleTest {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Test
    void prismarinHasTwelveFrames() {
        assertEquals(12, AnimatedTitle.prismarin(miniMessage).frameCount());
    }

    @Test
    void nextStartsAtFirstFrame() {
        AnimatedTitle title = AnimatedTitle.prismarin(miniMessage);
        assertEquals(title.frame(0), title.next());
        assertEquals(title.frame(1), title.next());
    }

    @Test
    void nextWrapsAroundAfterLastFrame() {
        AnimatedTitle title = AnimatedTitle.prismarin(miniMessage);
        for (int i = 0; i < title.frameCount(); i++) {
            title.next();
        }
        assertEquals(title.frame(0), title.next());
    }

    @Test
    void framesArePhaseShifted() {
        AnimatedTitle title = AnimatedTitle.prismarin(miniMessage);
        assertNotEquals(title.frame(0), title.frame(1));
        assertNotEquals(title.frame(0), title.frame(6));
    }

    @Test
    void frameIndexWrapsAround() {
        AnimatedTitle title = AnimatedTitle.prismarin(miniMessage);
        assertEquals(title.frame(2), title.frame(14));
    }

    @Test
    void everyFrameRendersTheBrandName() {
        AnimatedTitle title = AnimatedTitle.prismarin(miniMessage);
        for (int i = 0; i < title.frameCount(); i++) {
            assertEquals("PRISMARIN", PlainTextComponentSerializer.plainText().serialize(title.frame(i)));
        }
    }

    @Test
    void emptyFrameListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AnimatedTitle(List.of()));
    }

    @Test
    void customFrameCountIsRespected() {
        assertEquals(4, AnimatedTitle.brandGradient(miniMessage, "LOBBY", 4).frameCount());
    }

    @Test
    void singleFrameAlwaysReturnsSameComponent() {
        AnimatedTitle title = AnimatedTitle.brandGradient(miniMessage, "LOBBY", 1);
        Component first = title.next();
        assertEquals(first, title.next());
    }
}
