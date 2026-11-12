package net.prismarin.velocity.vote;

import com.velocitypowered.api.proxy.Player;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.player.ViewerRules;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.reward.Reward;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteReceivedEvent;
import net.prismarin.api.reward.VoteService;
import net.prismarin.api.reward.VoteSite;
import net.prismarin.api.reward.VoteStats;
import net.prismarin.shared.network.NetworkCalls;
import net.prismarin.shared.network.RemoteDispatcher;
import net.prismarin.shared.reward.VoteLedger;

public final class ProxyVoteService implements VoteService {

    public static final int MAX_SERVICE_LENGTH = 64;

    private final VoteSettings settings;
    private final VoteLedger ledger;
    private final ProfileService profiles;
    private final OnlinePlayers online;
    private final EventBus events;
    private final Executor executor;
    private final Clock clock;
    private final Consumer<Vote> announce;

    public interface OnlinePlayers {

        Optional<Player> byName(String name);

        Optional<Player> byUuid(UUID uuid);
    }

    public ProxyVoteService(VoteSettings settings, VoteLedger ledger, ProfileService profiles, OnlinePlayers online,
                            EventBus events, Executor executor, Clock clock, Consumer<Vote> announce) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.online = Objects.requireNonNull(online, "online");
        this.events = Objects.requireNonNull(events, "events");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.announce = Objects.requireNonNull(announce, "announce");
    }

    public VoteSettings settings() {
        return settings;
    }

    @Override
    public CompletableFuture<List<VoteSite>> sites() {
        return CompletableFuture.completedFuture(settings.sites());
    }

    @Override
    public CompletableFuture<Vote> vote(String name, String service) {
        return receive(name, service, "");
    }

    @Override
    public CompletableFuture<Vote> vote(UUID player, String service) {
        Objects.requireNonNull(player, "player");
        Optional<Player> here = online.byUuid(player);
        if (here.isPresent()) {
            return count(player, here.get().getUsername(), service, "");
        }
        return profiles.find(player).thenCompose(profile -> {
            if (profile.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("unknown player " + player));
            }
            return count(player, profile.get().getName(), service, "");
        });
    }

    public CompletableFuture<Vote> receive(String name, String service, String address) {
        if (!ViewerRules.plausibleName(name)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("'" + name + "' is no player name"));
        }
        String username = name.strip();
        Optional<Player> here = online.byName(username);
        if (here.isPresent()) {
            return count(here.get().getUniqueId(), here.get().getUsername(), service, address);
        }
        return profiles.lookup(username).thenCompose(found -> found
                .map(uuid -> profiles.find(uuid).thenCompose(profile -> count(uuid,
                        profile.map(PlayerProfile::getName).orElse(username), service, address)))
                .orElseGet(() -> count(null, username, service, address)));
    }

    @Override
    public CompletableFuture<VoteStats> stats(UUID player) {
        Objects.requireNonNull(player, "player");
        return CompletableFuture.supplyAsync(() -> ledger.stats(player), executor);
    }

    @Override
    public CompletableFuture<List<Vote>> pending(UUID player) {
        Objects.requireNonNull(player, "player");
        return name(player).thenApplyAsync(name -> ledger.pending(player, name), executor);
    }

    @Override
    public CompletableFuture<List<Vote>> claim(UUID player, String name, int limit) {
        Objects.requireNonNull(player, "player");
        int budget = limit <= RemoteDispatcher.CLAIM_BATCH ? NetworkCalls.BODY_BUDGET - 2_000 : Integer.MAX_VALUE;
        return CompletableFuture.supplyAsync(() -> ledger.claim(player, name, settings.keepMillis(), limit, budget),
                executor);
    }

    public void prune() {
        CompletableFuture.runAsync(() -> ledger.prune(settings.keepMillis()), executor);
    }

    static String service(String service) {
        String value = service == null ? "" : service.strip();
        return value.length() > MAX_SERVICE_LENGTH ? value.substring(0, MAX_SERVICE_LENGTH) : value;
    }

    private CompletableFuture<Vote> count(UUID player, String name, String service, String address) {
        String site = service(service);
        long at = clock.millis();
        return CompletableFuture.supplyAsync(() -> {
            VoteLedger.Counted counted = player == null ? null : ledger.count(player, name, at);
            Reward reward = settings.rules().reward(site, counted);
            Vote vote = new Vote(UUID.randomUUID().toString(), player, name, site,
                    address == null ? "" : address, at, counted == null ? 0 : counted.streak(), reward);
            ledger.queue(vote);
            return vote;
        }, executor).thenApply(vote -> {
            events.publish(new VoteReceivedEvent(vote));
            announce.accept(vote);
            return vote;
        });
    }

    private CompletableFuture<String> name(UUID player) {
        Optional<Player> here = online.byUuid(player);
        if (here.isPresent()) {
            return CompletableFuture.completedFuture(here.get().getUsername());
        }
        return profiles.find(player).thenApply(profile -> profile.map(PlayerProfile::getName).orElse(""));
    }
}
