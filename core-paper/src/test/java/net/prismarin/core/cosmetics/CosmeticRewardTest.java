package net.prismarin.core.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.reward.Reward;
import net.prismarin.api.reward.RewardContext;
import net.prismarin.api.reward.RewardHandlers;
import net.prismarin.core.reward.DailyRewardSettings;
import net.prismarin.shared.reward.RewardConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class CosmeticRewardTest {

    @TempDir
    Path directory;

    @Test
    void theCustomRewardTypeCosmeticUnlocksForTheRewardedPlayer() {
        StubCosmetics cosmetics = new StubCosmetics().add(Cosmetic.particles("streak-flame", CosmeticSlot.TRAIL,
                "Streak flame", CosmeticParticles.of("point", "small_flame")));
        List<CosmeticResult> told = new ArrayList<>();
        RewardHandlers handlers = new RewardHandlers();
        Subscription registered = new CosmeticReward(() -> cosmetics, (player, result) -> told.add(result))
                .register(handlers);
        UUID player = UUID.randomUUID();
        RewardContext daily = new RewardContext(RewardContext.DAILY, player, "Leon", 30, "30");

        handlers.find("cosmetic").orElseThrow().give(daily, " Streak-Flame ");
        handlers.find("COSMETIC").orElseThrow().give(daily, "streak-flame");
        handlers.find("cosmetic").orElseThrow().give(daily, "nothing");

        assertEquals(List.of(player + " streak-flame reward", player + " streak-flame reward",
                player + " nothing reward"), cosmetics.unlocks);
        assertEquals(List.of(CosmeticResult.Status.DONE, CosmeticResult.Status.ALREADY_OWNED,
                CosmeticResult.Status.UNKNOWN), told.stream().map(CosmeticResult::status).toList());
        registered.unsubscribe();
        assertTrue(handlers.find("cosmetic").isEmpty());
    }

    @Test
    void rewardFilesCanNameACosmetic() throws IOException {
        ConfigurationNode node = YamlConfigurationLoader.builder().buildAndLoadString("""
                currencies:
                  gems: 5
                custom:
                  cosmetic: angel-wings
                """);
        List<String> problems = new ArrayList<>();

        Reward reward = RewardConfig.read(node, "votes.default", problems);

        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(Map.of("cosmetic", "angel-wings"), reward.custom());
    }

    @Test
    void theThirtyDayMilestoneGivesTheStreakFlame() {
        DailyRewardSettings settings = DailyRewardSettings.load(directory.resolve(DailyRewardSettings.FILE_NAME));
        CosmeticSettings cosmetics = CosmeticSettings.defaults();

        assertEquals("streak-flame", settings.schedule().reward(30).custom().get(CosmeticReward.ID));
        Cosmetic flame = cosmetics.cosmetics().stream().filter(cosmetic -> cosmetic.id().equals("streak-flame"))
                .findFirst().orElseThrow();
        assertTrue(!flame.purchasable() && flame.permission().isEmpty());
    }
}
