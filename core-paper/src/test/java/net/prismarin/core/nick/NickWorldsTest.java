package net.prismarin.core.nick;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class NickWorldsTest {

    @Test
    void offAllowsEveryWorld() {
        assertTrue(NickWorlds.OFF.allows("anything"));
    }

    @Test
    void aListedWorldAlsoCoversItsNetherAndEnd() {
        NickWorlds worlds = new NickWorlds(true, NickWorlds.Mode.WHITELIST, Set.of("BedWars"), true);
        assertTrue(worlds.allows("bedwars"));
        assertTrue(worlds.allows("bedwars_nether"));
        assertTrue(worlds.allows("BedWars_the_end"));
        assertFalse(worlds.allows("lobby"));
    }

    @Test
    void listingADimensionCoversTheWholeWorld() {
        NickWorlds worlds = new NickWorlds(true, NickWorlds.Mode.BLACKLIST, Set.of("world_nether"), true);
        assertFalse(worlds.allows("world"));
        assertFalse(worlds.allows("world_the_end"));
        assertTrue(worlds.allows("arena"));
    }

    @Test
    void dimensionsStaySeparateWhenLinkingIsOff() {
        NickWorlds worlds = new NickWorlds(true, NickWorlds.Mode.WHITELIST, Set.of("world"), false);
        assertTrue(worlds.allows("world"));
        assertFalse(worlds.allows("world_nether"));
        assertFalse(worlds.allows("_nether"));
    }
}
