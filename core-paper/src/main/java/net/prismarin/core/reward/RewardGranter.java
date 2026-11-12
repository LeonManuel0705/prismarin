package net.prismarin.core.reward;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.level.LevelApi;
import net.prismarin.api.level.LevelService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.reward.Reward;
import net.prismarin.api.reward.RewardContext;
import net.prismarin.api.reward.RewardHandler;
import net.prismarin.api.reward.RewardHandlers;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.ConfiguredText;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.reward.RewardPlaceholders;
import org.bukkit.entity.Player;

@Singleton
public final class RewardGranter {

    private final PrismarinCorePlugin plugin;
    private final EconomyService economy;
    private final ProfileService profiles;
    private final ConfiguredText texts;

    @Inject
    public RewardGranter(PrismarinCorePlugin plugin, EconomyService economy, ProfileService profiles,
                         ConfiguredText texts) {
        this.plugin = plugin;
        this.economy = economy;
        this.profiles = profiles;
        this.texts = texts;
    }

    public static Map<String, String> placeholders(RewardContext context, Map<String, String> extra) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", context.name());
        values.put("uuid", context.player().toString());
        values.put("streak", Integer.toString(context.streak()));
        values.put("source", context.source());
        values.put("detail", context.detail());
        values.putAll(extra);
        return values;
    }

    public CompletableFuture<Void> give(UUID uuid, Reward reward, RewardContext context, Map<String, String> extra) {
        if (reward.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, String> values = placeholders(context, extra);
        List<CompletableFuture<?>> work = new ArrayList<>();
        if (economy.enabled()) {
            reward.currencies().forEach((id, amount) -> {
                Optional<Currency> currency = economy.currency(id);
                if (currency.isEmpty()) {
                    plugin.getLogger().warning("A " + context.source() + " reward names the unknown currency " + id);
                    return;
                }
                work.add(economy.deposit(uuid, currency.get(), amount));
            });
        }
        Optional<LevelService> levels = LevelApi.service();
        if (levels.isPresent() && reward.xp() > 0L) {
            work.add(levels.get().addXp(uuid, reward.xp(), context.source()));
        }
        if (levels.isPresent() && reward.levels() > 0) {
            work.add(levels.get().addLevels(uuid, reward.levels(), context.source()));
        }
        Runnable sync = () -> {
            for (String command : reward.commands()) {
                String line = RewardPlaceholders.command(command, values);
                try {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), line);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "The reward command '" + line + "' failed", e);
                }
            }
            reward.custom().forEach((id, value) -> {
                Optional<RewardHandler> handler = RewardHandlers.global().find(id);
                if (handler.isEmpty()) {
                    plugin.getLogger().warning("No plugin handles the custom reward " + id);
                    return;
                }
                try {
                    handler.get().give(context, RewardPlaceholders.fill(value, values));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "The custom reward " + id + " failed", e);
                }
            });
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                Locale language = PlayerLocales.of(player, profiles);
                for (NoticeText message : reward.messages()) {
                    player.sendMessage(texts.render(values, message, language, uuid));
                }
            }
        };
        if (plugin.getServer().isPrimaryThread()) {
            sync.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, sync);
        }
        CompletableFuture<?>[] pending = work.stream()
                .map(future -> future.exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "A " + context.source() + " reward for " + context.name()
                            + " failed", error);
                    return null;
                }))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(pending);
    }
}
