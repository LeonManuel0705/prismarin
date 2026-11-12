package net.prismarin.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatOrder;
import org.junit.jupiter.api.Test;

class StatsViewTest {

    private final PlayerStats stats = new PlayerStats(UUID.randomUUID(), "Leon",
            Map.of("bedwars.wins", 3L, "bedwars.kills", 9L, "votes", 2L, "skywars.wins", 1L, "hidden.thing", 4L),
            Map.of(), Map.of());

    private final List<StatDefinition> configured = List.of(
            new StatDefinition("votes", null, StatFormat.NUMBER, StatOrder.HIGHEST, true, true),
            new StatDefinition("bedwars.kills", null, StatFormat.NUMBER, StatOrder.HIGHEST, true, true),
            new StatDefinition("bedwars.wins", null, StatFormat.NUMBER, StatOrder.HIGHEST, true, false),
            new StatDefinition("hidden.thing", null, StatFormat.NUMBER, StatOrder.HIGHEST, false, false),
            new StatDefinition("never.played", null, StatFormat.NUMBER, StatOrder.HIGHEST, true, true));

    private static List<StatGame> games(String... ids) {
        List<StatGame> games = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            games.add(StatGame.of(ids[index]).withOrder(index));
        }
        return games;
    }

    @Test
    void statsAreGroupedByGameInConfiguredOrder() {
        List<StatsView.Group> groups = StatsView.groups(stats, configured, StatDefinition::of,
                games("general", "skywars"), null);

        assertEquals(List.of("general", "skywars", "bedwars"), groups.stream().map(StatsView.Group::game).toList());
        assertEquals(List.of("bedwars.kills", "bedwars.wins"), groups.get(2).stats().stream()
                .map(StatDefinition::key).toList());
    }

    @Test
    void gamesSortByTheirOrderAndShowFeaturedStatsFirst() {
        List<StatGame> games = List.of(StatGame.of("general").withOrder(50),
                StatGame.of("bedwars").withOrder(1).withFeatured("wins"), StatGame.of("skywars").withOrder(2));

        List<StatsView.Group> groups = StatsView.groups(stats, configured, StatDefinition::of, games, null);

        assertEquals(List.of("bedwars", "skywars", "general"), groups.stream().map(StatsView.Group::game).toList());
        assertEquals(List.of("bedwars.wins", "bedwars.kills"), groups.getFirst().stats().stream()
                .map(StatDefinition::key).toList());
    }

    @Test
    void oneGameCanBeChosen() {
        List<StatsView.Group> groups = StatsView.groups(stats, configured, StatDefinition::of, List.of(),
                "bedwars");

        assertEquals(1, groups.size());
        assertEquals("bedwars", groups.getFirst().game());
    }

    @Test
    void unlistedStatsFollowTheirDefinition() {
        List<StatsView.Group> hidden = StatsView.groups(stats, configured,
                key -> new StatDefinition(key, null, StatFormat.NUMBER, StatOrder.HIGHEST, false, false), List.of(),
                "skywars");

        assertEquals(List.of(), hidden);
    }

    @Test
    void longStatListsArePaged() {
        Map<String, Long> values = new LinkedHashMap<>();
        for (int index = 1; index <= 7; index++) {
            values.put("bedwars.stat-" + index, (long) index);
        }
        values.put("skywars.wins", 1L);
        values.put("skywars.kills", 2L);
        PlayerStats many = new PlayerStats(UUID.randomUUID(), "Leon", values, Map.of(), Map.of());
        List<StatsView.Group> groups = StatsView.groups(many, List.of(), StatDefinition::of,
                games("bedwars", "skywars"), null);

        StatsView.Sheet first = StatsView.sheet(groups, null, 4, 3, 1);
        StatsView.Sheet second = StatsView.sheet(groups, null, 4, 3, 2);
        StatsView.Sheet last = StatsView.sheet(groups, null, 4, 3, 99);

        assertEquals(StatsView.Layout.STATS, first.layout());
        assertEquals(3, first.pages());
        assertEquals(4, first.keys().size());
        assertEquals(List.of("bedwars"), first.groups().stream().map(StatsView.Group::game).toList());
        assertEquals(List.of("bedwars", "skywars"), second.groups().stream().map(StatsView.Group::game).toList());
        assertEquals(List.of("bedwars.stat-5", "bedwars.stat-6", "bedwars.stat-7", "skywars.kills"), second.keys());
        assertEquals(3, last.page());
        assertEquals(List.of("skywars.wins"), last.keys());
    }

    @Test
    void manyGamesShowAsAListFirst() {
        List<StatsView.Group> groups = StatsView.groups(stats, configured, StatDefinition::of,
                games("general", "skywars"), null);

        StatsView.Sheet list = StatsView.sheet(groups, null, 2, 2, 1);
        StatsView.Sheet next = StatsView.sheet(groups, null, 2, 2, 2);
        StatsView.Sheet chosen = StatsView.sheet(groups, "bedwars", 2, 2, 1);
        StatsView.Sheet few = StatsView.sheet(groups, null, 10, 3, 1);

        assertEquals(StatsView.Layout.GAMES, list.layout());
        assertEquals(2, list.pages());
        assertEquals(List.of("general", "skywars"), list.groups().stream().map(StatsView.Group::game).toList());
        assertEquals(List.of("bedwars"), next.groups().stream().map(StatsView.Group::game).toList());
        assertEquals(StatsView.Layout.STATS, chosen.layout());
        assertEquals(StatsView.Layout.STATS, few.layout());
        assertEquals(1, few.pages());
    }

    @Test
    void theProfileShowsMarkedStatsTheyHave() {
        assertEquals(List.of("votes", "bedwars.kills"), StatsView.profile(stats, configured, 5).stream()
                .map(StatDefinition::key).toList());
        assertEquals(1, StatsView.profile(stats, configured, 1).size());
    }
}
