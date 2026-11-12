package net.prismarin.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.prismarin.api.stats.StatPeriod;
import org.junit.jupiter.api.Test;

class StatPlaceholderTest {

    private static StatPlaceholder parse(String params) {
        return StatPlaceholder.parse(params).orElseThrow();
    }

    @Test
    void valuesOfThePlayer() {
        assertEquals(new StatPlaceholder(StatPlaceholder.Kind.VALUE, "bedwars.wins", StatPeriod.ALL_TIME, 0,
                StatPlaceholder.Field.VALUE), parse("stat_bedwars.wins"));
        assertEquals(new StatPlaceholder(StatPlaceholder.Kind.VALUE, "bedwars.wins", StatPeriod.WEEKLY, 0,
                StatPlaceholder.Field.VALUE), parse("stat_bedwars.wins_weekly"));
        assertEquals(StatPlaceholder.Field.RAW, parse("stat_bedwars.wins_raw").field());
        assertEquals(StatPlaceholder.Field.RANK, parse("stat_bedwars.wins_rank").field());
        StatPlaceholder monthlyRank = parse("stat_bedwars.final-kills_monthly_rank");
        assertEquals("bedwars.final-kills", monthlyRank.key());
        assertEquals(StatPeriod.MONTHLY, monthlyRank.period());
        assertEquals(StatPlaceholder.Field.RANK, monthlyRank.field());
    }

    @Test
    void topListEntries() {
        StatPlaceholder name = parse("top_bedwars.wins_1_name");
        assertEquals(StatPlaceholder.Kind.TOP, name.kind());
        assertEquals(1, name.position());
        assertEquals(StatPlaceholder.Field.NAME, name.field());
        assertEquals(StatPeriod.ALL_TIME, name.period());

        StatPlaceholder weekly = parse("top_bedwars.wins_weekly_10_value");
        assertEquals(StatPeriod.WEEKLY, weekly.period());
        assertEquals(10, weekly.position());
        assertEquals(StatPlaceholder.Field.VALUE, weekly.field());
        assertEquals(StatPlaceholder.Field.RAW, parse("top_votes_3_raw").field());
        assertEquals(StatPlaceholder.Field.UUID, parse("TOP_Votes_3_UUID").field());
    }

    @Test
    void brokenPlaceholdersAreNotStats() {
        for (String broken : new String[] {"stat_", "stat_bed wars", "stat_bedwars.wins_daily",
                "stat_bedwars.wins_weekly_name", "stat_a_b_c_d", "top_bedwars.wins", "top_bedwars.wins_0_name",
                "top_bedwars.wins_101_name", "top_bedwars.wins_x_name", "top_bedwars.wins_1_color",
                "top_bedwars.wins_daily_1_name", "balance"}) {
            assertEquals(Optional.empty(), StatPlaceholder.parse(broken), broken);
        }
        assertTrue(StatPlaceholder.candidate("top_x"));
        assertFalse(StatPlaceholder.candidate("server_online_x"));
    }
}
