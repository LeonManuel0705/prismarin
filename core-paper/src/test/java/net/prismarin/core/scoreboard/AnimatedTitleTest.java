package net.prismarin.core.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.brand.Branding;
import org.junit.jupiter.api.Test;

class AnimatedTitleTest {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Test
    void prismarinHasTwelveFrames() {
        assertEquals(12, AnimatedTitle.brand(miniMessage, Branding.DEFAULT).frameCount());
    }

    @Test
    void nextStartsAtFirstFrame() {
        AnimatedTitle title = AnimatedTitle.brand(miniMessage, Branding.DEFAULT);
        assertEquals(title.frame(0), title.next());
        assertEquals(title.frame(1), title.next());
    }

    @Test
    void nextWrapsAroundAfterLastFrame() {
        AnimatedTitle title = AnimatedTitle.brand(miniMessage, Branding.DEFAULT);
        for (int i = 0; i < title.frameCount(); i++) {
            title.next();
        }
        assertEquals(title.frame(0), title.next());
    }

    @Test
    void framesArePhaseShifted() {
        AnimatedTitle title = AnimatedTitle.brand(miniMessage, Branding.DEFAULT);
        assertNotEquals(title.frame(0), title.frame(1));
        assertNotEquals(title.frame(0), title.frame(6));
    }

    @Test
    void frameIndexWrapsAround() {
        AnimatedTitle title = AnimatedTitle.brand(miniMessage, Branding.DEFAULT);
        assertEquals(title.frame(2), title.frame(14));
    }

    @Test
    void everyFrameRendersTheBrandName() {
        AnimatedTitle title = AnimatedTitle.brand(miniMessage, Branding.DEFAULT);
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
        assertEquals(4, AnimatedTitle.brandGradient(miniMessage, Branding.DEFAULT, "LOBBY", 4).frameCount());
    }

    @Test
    void singleFrameAlwaysReturnsSameComponent() {
        AnimatedTitle title = AnimatedTitle.brandGradient(miniMessage, Branding.DEFAULT, "LOBBY", 1);
        Component first = title.next();
        assertEquals(first, title.next());
    }
}
