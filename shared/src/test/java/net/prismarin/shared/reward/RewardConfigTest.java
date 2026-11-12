package net.prismarin.shared.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.reward.Reward;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class RewardConfigTest {

    private static ConfigurationNode yaml(String text) throws IOException {
        return YamlConfigurationLoader.builder().source(() -> new BufferedReader(new StringReader(text))).build()
                .load();
    }

    @Test
    void everyPartOfARewardIsRead() throws IOException {
        ConfigurationNode node = yaml("""
                currencies:
                  Coins: 100
                  gems: 5
                xp: 50
                levels: 1
                commands:
                  - "give %player% diamond 1"
                message:
                  en: "Thanks"
                  de: "Danke"
                custom:
                  crate: "vote"
                """);
        List<String> problems = new ArrayList<>();

        Reward reward = RewardConfig.read(node, "reward", problems);

        assertEquals(List.of(), problems);
        assertEquals(Map.of("coins", 100L, "gems", 5L), reward.currencies());
        assertEquals(50L, reward.xp());
        assertEquals(1, reward.levels());
        assertEquals(List.of("give %player% diamond 1"), reward.commands());
        assertEquals("Danke", reward.messages().getFirst().template(Locale.GERMAN));
        assertEquals(Map.of("crate", "vote"), reward.custom());
    }

    @Test
    void singleTextsAndListsWorkToo() throws IOException {
        ConfigurationNode node = yaml("""
                commands: "say hi"
                messages:
                  - "one"
                  - en: "two"
                """);

        Reward reward = RewardConfig.read(node, "reward", new ArrayList<>());

        assertEquals(List.of("say hi"), reward.commands());
        assertEquals(2, reward.messages().size());
        assertEquals("two", reward.messages().get(1).template(Locale.FRENCH));
    }

    @Test
    void wrongValuesAreReportedAndSkipped() throws IOException {
        ConfigurationNode node = yaml("""
                currencies:
                  coins: -5
                xp: -1
                levels: -2
                """);
        List<String> problems = new ArrayList<>();

        Reward reward = RewardConfig.read(node, "days.3", problems);

        assertTrue(reward.isEmpty());
        assertEquals(3, problems.size());
        assertTrue(problems.getFirst().startsWith("days.3.currencies.coins"));
    }

    @Test
    void writtenRewardsReadBackTheSame() throws IOException {
        Reward reward = Reward.currency("coins", 75L).plus(Reward.experience(20L))
                .plus(Reward.command("say %player%"))
                .plus(Reward.message(NoticeText.of(Map.of("en", "Hi"))));
        ConfigurationNode node = yaml("{}");

        RewardConfig.write(node, reward);

        assertEquals(reward, RewardConfig.read(node, "reward", new ArrayList<>()));
    }

    @Test
    void anEmptySectionIsNoReward() throws IOException {
        assertTrue(RewardConfig.read(yaml("{}").node("missing"), "x", new ArrayList<>()).isEmpty());
    }
}
