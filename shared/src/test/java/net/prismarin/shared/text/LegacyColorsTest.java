package net.prismarin.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LegacyColorsTest {

    @Test
    void plainTextIsUnchanged() {
        assertEquals("<gray>Hallo</gray>", LegacyColors.toMiniMessage("<gray>Hallo</gray>"));
    }

    @Test
    void ampersandColorsBecomeNamedTags() {
        assertEquals("<green>Hallo <red>Welt", LegacyColors.toMiniMessage("&aHallo &cWelt"));
    }

    @Test
    void sectionSignIsAccepted() {
        assertEquals("<gold>Gold", LegacyColors.toMiniMessage("§6Gold"));
    }

    @Test
    void colorAfterDecorationResetsLikeVanilla() {
        assertEquals("<bold>Fett<reset><aqua>normal", LegacyColors.toMiniMessage("&lFett&bnormal"));
    }

    @Test
    void shortHexIsConverted() {
        assertEquals("<#22D3EE>Prisma", LegacyColors.toMiniMessage("&#22d3eePrisma"));
    }

    @Test
    void bungeeHexIsConverted() {
        assertEquals("<#2DD4BF>Teal", LegacyColors.toMiniMessage("&x&2&D&D&4&B&FTeal"));
    }

    @Test
    void resetCodeIsConverted() {
        assertEquals("<red>a<reset>b", LegacyColors.toMiniMessage("&ca&rb"));
    }

    @Test
    void unknownCodesAndLoneAmpersandsStay() {
        assertEquals("Tom & Jerry &z", LegacyColors.toMiniMessage("Tom & Jerry &z"));
    }

    @Test
    void invalidHexStaysLiteral() {
        assertEquals("&#GGGGGGx", LegacyColors.toMiniMessage("&#GGGGGGx"));
    }
}
