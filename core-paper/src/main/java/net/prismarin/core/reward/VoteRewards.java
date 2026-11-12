package net.prismarin.core.reward;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.reward.RewardApi;
import net.prismarin.api.reward.RewardContext;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteReceivedEvent;
import net.prismarin.api.reward.VoteRewardGivenEvent;
import net.prismarin.api.reward.VoteService;
import net.prismarin.api.stats.StatsApi;
import net.prismarin.core.CoreModules;
import net.prismarin.core.ModuleState;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.stats.StatsSettings;
import net.prismarin.shared.network.ProxyLink;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@Singleton
public final class VoteRewards implements Listener {

    private static final long JOIN_DELAY_TICKS = 60L;
    private static final long ARRIVAL_DELAY_TICKS = 20L;

    private final PrismarinCorePlugin plugin;
    private final RewardGranter granter;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final EventBus events;
    private final ModuleState modules;
    private final Provider<StatsSettings> statsSettings;
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();
    private Subscription received;

    @Inject
    public VoteRewards(PrismarinCorePlugin plugin, RewardGranter granter, LocaleService locale,
                       ProfileService profiles, EventBus events, ModuleState modules,
                       Provider<StatsSettings> statsSettings) {
        this.plugin = plugin;
        this.granter = granter;
        this.locale = locale;
        this.profiles = profiles;
        this.events = events;
        this.modules = modules;
        this.statsSettings = statsSettings;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        received = events.subscribe(VoteReceivedEvent.class, this::received);
        plugin.getServer().getOnlinePlayers().forEach(player -> later(player.getUniqueId(), JOIN_DELAY_TICKS));
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        if (received != null) {
            received.unsubscribe();
            received = null;
        }
        claiming.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer().getUniqueId(), JOIN_DELAY_TICKS);
    }

    private void received(VoteReceivedEvent event) {
        event.vote().playerId().ifPresent(player -> later(player, ARRIVAL_DELAY_TICKS));
    }

    private void later(UUID player, long delay) {
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> claim(player), delay);
        } catch (IllegalStateException | UnsupportedOperationException e) {
            plugin.getLogger().fine("Vote rewards of " + player + " wait for the next join: " + e.getMessage());
        }
    }

    private void claim(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        Optional<VoteService> service = RewardApi.votes();
        if (player == null || service.isEmpty() || !claiming.add(uuid)) {
            return;
        }
        String name = profiles.getCached(uuid).map(PlayerProfile::getName).orElse(player.getName());
        service.get().claim(uuid, name).whenComplete((votes, error) -> {
            claiming.remove(uuid);
            if (error != null) {
                if (!ProxyLink.noRoute(error)) {
                    plugin.getLogger().log(Level.FINE, "Could not fetch the vote rewards of " + name, error);
                }
                return;
            }
            if (!votes.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> give(uuid, name, votes));
            }
        });
    }

    private void give(UUID uuid, String name, List<Vote> votes) {
        for (Vote vote : votes) {
            RewardContext context = new RewardContext(RewardContext.VOTE, uuid, name, vote.streak(), vote.service());
            granter.give(uuid, vote.reward(), context, Map.of("service", vote.service()))
                    .thenRun(() -> events.publish(new VoteRewardGivenEvent(uuid, vote)));
        }
        countStats(uuid, votes.size());
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            return;
        }
        if (votes.size() == 1) {
            player.sendMessage(locale.render("vote.rewarded", PlayerLocales.of(player, profiles),
                    Placeholder.unparsed("service", service(votes.getFirst()))));
        } else {
            player.sendMessage(locale.render("vote.rewarded-many", PlayerLocales.of(player, profiles),
                    Placeholder.unparsed("count", Integer.toString(votes.size()))));
        }
    }

    private void countStats(UUID player, int count) {
        if (!modules.enabled(CoreModules.STATS)) {
            return;
        }
        StatsApi.service().ifPresent(stats -> statsSettings.get().builtIn().votesKey()
                .ifPresent(key -> stats.increment(player, key, count).exceptionally(error -> null)));
    }

    private static String service(Vote vote) {
        return vote.service().isBlank() ? "-" : vote.service();
    }
}
