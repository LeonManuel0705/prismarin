package net.prismarin.core.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class FakeRankResolverTest {

    private static NickSettings.FakeRank rank(String prefix, String color, int weight) {
        return new NickSettings.FakeRank("default", prefix, color, weight, List.of(), true, 1_000_000);
    }

    @Test
    void withoutGroupAndOverridesTheDefaultRankIsUsed() {
        FakeRankResolver.Look look = FakeRankResolver.resolve(rank("", "", -1), Optional.empty());
        assertEquals(Optional.empty(), look.prefix());
        assertEquals(0, look.weight());
        assertEquals(NamedTextColor.GRAY, look.nameColor());
        assertEquals("", look.disguisePrefix());
        assertEquals("gray", look.disguiseColor());
    }

    @Test
    void groupValuesAreCopied() {
        FakeRankResolver.GroupLook group = new FakeRankResolver.GroupLook("&7Spieler", OptionalInt.of(5), "white");
        FakeRankResolver.Look look = FakeRankResolver.resolve(rank("", "", -1), Optional.of(group));
        assertEquals(Optional.of("&7Spieler"), look.prefix());
        assertEquals(5, look.weight());
        assertEquals(NamedTextColor.WHITE, look.nameColor());
        assertEquals("&7Spieler", look.disguisePrefix());
    }

    @Test
    void overridesWinOverTheGroup() {
        FakeRankResolver.GroupLook group = new FakeRankResolver.GroupLook("&7Spieler", OptionalInt.of(5), "white");
        FakeRankResolver.Look look = FakeRankResolver.resolve(rank("<gray>Player</gray>", "dark-aqua", 0),
                Optional.of(group));
        assertEquals(Optional.of("<gray>Player</gray>"), look.prefix());
        assertEquals(0, look.weight());
        assertEquals(NamedTextColor.DARK_AQUA, look.nameColor());
        assertEquals("dark_aqua", look.disguiseColor());
    }

    @Test
    void blankGroupPrefixAndUnknownColoursFallBack() {
        FakeRankResolver.GroupLook group = new FakeRankResolver.GroupLook("  ", OptionalInt.empty(), "rainbow");
        FakeRankResolver.Look look = FakeRankResolver.resolve(rank("", "nope", -1), Optional.of(group));
        assertEquals(Optional.empty(), look.prefix());
        assertEquals(0, look.weight());
        assertEquals(NamedTextColor.GRAY, look.nameColor());
    }

    @Test
    void coloursParseLeniently() {
        assertEquals(Optional.of(NamedTextColor.LIGHT_PURPLE), FakeRankResolver.color(" Light_Purple "));
        assertEquals(Optional.of(NamedTextColor.DARK_GREEN), FakeRankResolver.color("dark-green"));
        assertEquals(Optional.empty(), FakeRankResolver.color(""));
        assertEquals(Optional.empty(), FakeRankResolver.color(null));
    }
}
