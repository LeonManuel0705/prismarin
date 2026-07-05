package net.prismarin.lobby.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.prismarin.api.brand.Brand;
import org.junit.jupiter.api.Test;

class XpProgressBarTest {

    @Test
    void rendersEmptyBarWithoutXp() {
        assertEquals(0, XpProgressBar.filledSegments(0L, 500L, 10));
    }

    @Test
    void rendersHalfFilledBar() {
        assertEquals(5, XpProgressBar.filledSegments(250L, 500L, 10));
    }

    @Test
    void clampsToFullBarWhenXpReachesRequirement() {
        assertEquals(10, XpProgressBar.filledSegments(500L, 500L, 10));
        assertEquals(10, XpProgressBar.filledSegments(9999L, 500L, 10));
    }

    @Test
    void clampsNegativeXpToEmptyBar() {
        assertEquals(0, XpProgressBar.filledSegments(-50L, 500L, 10));
    }

    @Test
    void treatsInvalidRequirementAsFullBar() {
        assertEquals(10, XpProgressBar.filledSegments(0L, 0L, 10));
        assertEquals(10, XpProgressBar.filledSegments(0L, -1L, 10));
    }

    @Test
    void rejectsNonPositiveSegmentCount() {
        assertThrows(IllegalArgumentException.class, () -> XpProgressBar.filledSegments(0L, 500L, 0));
    }

    @Test
    void rendersMiniMessageBarWithBrandColor() {
        String expected = "<color:" + Brand.PRIMARY + ">▮▮</color><dark_gray>▯▯</dark_gray>";
        assertEquals(expected, XpProgressBar.render(250L, 500L, 4));
    }

    @Test
    void rendersOnlyEmptyGlyphsAtZeroProgress() {
        String expected = "<color:" + Brand.PRIMARY + "></color><dark_gray>▯▯▯▯</dark_gray>";
        assertEquals(expected, XpProgressBar.render(0L, 500L, 4));
    }
}
