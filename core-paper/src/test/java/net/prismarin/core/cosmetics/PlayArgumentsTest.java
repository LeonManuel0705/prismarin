package net.prismarin.core.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.prismarin.core.cosmetics.PlayArguments.Where;
import org.junit.jupiter.api.Test;

class PlayArgumentsTest {

    private static PlayArguments parse(String line) {
        return PlayArguments.parse(line.split(" ")).orElseThrow();
    }

    @Test
    void withoutASpotTheEffectPlaysAtThePlayer() {
        PlayArguments play = parse("play Steve Win-Effect");

        assertEquals("Steve", play.player());
        assertEquals("win-effect", play.slot());
        assertEquals(Where.PLAYER, play.where());
    }

    @Test
    void hereAndCoordinatesAreUnderstood() {
        assertEquals(Where.HERE, parse("play Steve kill-effect HERE").where());

        PlayArguments absolute = parse("play Steve kill-effect 10 64.5 -3");
        assertEquals(Where.COORDINATES, absolute.where());
        assertEquals(10.0, absolute.x().resolve(500.0));
        assertEquals(64.5, absolute.y().resolve(500.0));
        assertEquals(-3.0, absolute.z().resolve(500.0));

        PlayArguments relative = parse("play Steve win-effect ~ ~3 ~-1.5");
        assertEquals(100.0, relative.x().resolve(100.0));
        assertEquals(67.0, relative.y().resolve(64.0));
        assertEquals(8.5, relative.z().resolve(10.0));
    }

    @Test
    void anythingElseShowsTheUsage() {
        for (String line : List.of("play", "play Steve", "play Steve win-effect there",
                "play Steve win-effect 1 2", "play Steve win-effect 1 two 3", "play Steve win-effect 1 2 3 4",
                "play Steve win-effect NaN 2 3", "play Steve win-effect 1 Infinity 3",
                "play Steve win-effect 1 2 99999999999", "play Steve win-effect ~x 2 3")) {
            assertEquals(Optional.empty(), PlayArguments.parse(line.split(" ")), line);
        }
    }

    @Test
    void tabCompletionOffersPlayersCategoriesAndSpots() {
        List<String> players = List.of("Steve", "Alex");
        List<String> slots = List.of("kill-effect", "win-effect");

        assertEquals(List.of("Steve"), PlayArguments.suggestions(1, "st", players, slots));
        assertEquals(List.of("win-effect"), PlayArguments.suggestions(2, "w", players, slots));
        assertEquals(List.of("here", "~"), PlayArguments.suggestions(3, "", players, slots));
        assertEquals(List.of("~"), PlayArguments.suggestions(5, "", players, slots));
        assertEquals(List.of(), PlayArguments.suggestions(6, "", players, slots));
    }

    @Test
    void playIsHiddenFromHelpAndTabCompletionAndNeedsItsPermission() {
        Predicate<String> player = Set.of(CosmeticSubCommands.USE_PERMISSION)::contains;
        Predicate<String> operator = Set.of(CosmeticSubCommands.USE_PERMISSION, CosmeticSubCommands.GIVE_PERMISSION,
                CosmeticSubCommands.TAKE_PERMISSION, CosmeticSubCommands.PLAY_PERMISSION)::contains;
        Predicate<String> console = permission -> true;

        assertTrue(!CosmeticSubCommands.ALL.allowed(CosmeticSubCommands.PLAY, player));
        assertTrue(CosmeticSubCommands.ALL.allowed(CosmeticSubCommands.PLAY, operator));
        assertTrue(CosmeticSubCommands.ALL.allowed(CosmeticSubCommands.PLAY, console));
        assertTrue(new CosmeticSubCommands(false).allowed(CosmeticSubCommands.PLAY, console));
        for (Predicate<String> who : List.of(player, operator, console)) {
            assertTrue(!CosmeticSubCommands.ALL.visible(who).contains(CosmeticSubCommands.PLAY));
            assertTrue(!CosmeticSubCommands.ALL.suggestions(who, "p", List.of("trail")).contains(
                    CosmeticSubCommands.PLAY));
        }
        assertEquals(List.of("hide", "give", "take", "help"), CosmeticSubCommands.ALL.visible(console));
    }
}
