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

    @Test
    void aPrefixOverrideSortsAndColoursLikeTheGroupShowingIt() {
        FakeRankResolver.GroupLook standard = new FakeRankResolver.GroupLook(null, OptionalInt.empty(), null);
        List<FakeRankResolver.GroupLook> groups = List.of(
                new FakeRankResolver.GroupLook("&7Player ", OptionalInt.of(1), null),
                new FakeRankResolver.GroupLook("&6&lPremium ", OptionalInt.of(50), "yellow"),
                new FakeRankResolver.GroupLook("&cAdmin ", OptionalInt.of(100), "red"));
        FakeRankResolver.Look look = FakeRankResolver.resolve(rank("<gold><b>Premium</b></gold>", "", -1),
                Optional.of(standard), groups);
        assertEquals(50, look.weight());
        assertEquals(NamedTextColor.YELLOW, look.nameColor());
        assertEquals(Optional.of("<gold><b>Premium</b></gold>"), look.prefix());
    }

    @Test
    void anExplicitWeightStillWinsOverTheLookalike() {
        List<FakeRankResolver.GroupLook> groups = List.of(
                new FakeRankResolver.GroupLook("&6Premium", OptionalInt.of(50), null));
        FakeRankResolver.Look look = FakeRankResolver.resolve(rank("&6Premium", "", 7), Optional.empty(), groups);
        assertEquals(7, look.weight());
    }

    @Test
    void aPrefixNoGroupShowsFallsBackToTheConfiguredGroup() {
        FakeRankResolver.GroupLook standard = new FakeRankResolver.GroupLook("&7Player", OptionalInt.of(1), "gray");
        List<FakeRankResolver.GroupLook> groups = List.of(
                standard, new FakeRankResolver.GroupLook("&6Premium", OptionalInt.of(50), null));
        FakeRankResolver.Look look = FakeRankResolver.resolve(rank("&bVIP", "", -1), Optional.of(standard), groups);
        assertEquals(1, look.weight());
        assertEquals(NamedTextColor.GRAY, look.nameColor());
    }

    @Test
    void theHighestGroupWinsWhenSeveralShowTheSameText() {
        List<FakeRankResolver.GroupLook> groups = List.of(
                new FakeRankResolver.GroupLook("&6Premium", OptionalInt.of(20), null),
                new FakeRankResolver.GroupLook("<#FBBF24>Premium", OptionalInt.of(40), null));
        assertEquals(OptionalInt.of(40), FakeRankResolver.lookalike("&ePremium", groups).orElseThrow().weight());
        assertEquals(Optional.empty(), FakeRankResolver.lookalike("&6", groups));
    }

    @Test
    void shownTextIgnoresEveryKindOfFormatting() {
        assertEquals("Premium", FakeRankResolver.shown("&6&lPremium "));
        assertEquals("Premium", FakeRankResolver.shown("<gradient:#22D3EE:#2DD4BF><b>Premium</b></gradient>"));
        assertEquals("Premium", FakeRankResolver.shown("&#FBBF24Premium"));
    }
}
