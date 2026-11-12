package net.prismarin.velocity.vote;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.reward.RewardApi;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteReceivedEvent;
import net.prismarin.api.reward.VoteSite;
import net.prismarin.shared.network.NetworkCalls;
import net.prismarin.shared.network.NetworkEvents;
import net.prismarin.shared.reward.VoteLedger;
import net.prismarin.velocity.admin.AdminContext;
import net.prismarin.velocity.admin.ModerationChannel;
import org.slf4j.Logger;

public final class VoteModule {

    private static final String COMMAND = "/vote";

    private final Object plugin;
    private final AdminContext context;
    private final Logger logger;
    private final Set<UUID> reminded = ConcurrentHashMap.newKeySet();
    private ProxyVoteService votes;

    private VoteModule(Object plugin, AdminContext context, Logger logger) {
        this.plugin = plugin;
        this.context = context;
        this.logger = logger;
    }

    public static ProxyVoteService register(Object plugin, Logger logger, AdminContext context,
                                            ExecutorService executor, Path dataDirectory) {
        VoteSettings settings = VoteSettings.load(dataDirectory.resolve(VoteSettings.FILE_NAME));
        settings.problems().forEach(problem -> logger.warn("{}: {}", VoteSettings.FILE_NAME, problem));
        ProxyServer proxy = context.proxy();
        VoteLedger ledger = new VoteLedger(context.services().documents(), Clock.systemUTC(), settings.clock(),
                settings.graceDays(), settings.streaks());
        VoteModule module = new VoteModule(plugin, context, logger);
        ProxyVoteService votes = new ProxyVoteService(settings, ledger, context.services().profiles(),
                new ProxyVoteService.OnlinePlayers() {
                    @Override
                    public Optional<Player> byName(String name) {
                        return proxy.getPlayer(name);
                    }

                    @Override
                    public Optional<Player> byUuid(UUID uuid) {
                        return proxy.getPlayer(uuid);
                    }
                }, context.services().events(), executor, Clock.systemUTC(), module::announce);
        module.votes = votes;
        RewardApi.registerVotes(votes);
        proxy.getEventManager().register(plugin, module);
        CommandManager commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder("vote").aliases("votes").plugin(plugin).build(),
                new VoteCommand(context, votes, logger));
        if (settings.votifier()) {
            if (proxy.getPluginManager().isLoaded(VotifierListener.PLUGIN_ID)) {
                proxy.getEventManager().register(plugin, new VotifierListener(votes, logger));
                logger.info("Counting votes from NuVotifier");
            } else {
                logger.info("NuVotifier is not installed, votes only come from plugins through prismarin.votes()");
            }
        }
        proxy.getScheduler().buildTask(plugin, votes::prune).delay(1, TimeUnit.MINUTES).repeat(1, TimeUnit.DAYS)
                .schedule();
        int interval = settings.reminder().intervalMinutes();
        if (interval > 0) {
            proxy.getScheduler().buildTask(plugin, () -> proxy.getAllPlayers().forEach(module::remindWhenDue))
                    .delay(interval, TimeUnit.MINUTES).repeat(interval, TimeUnit.MINUTES).schedule();
        }
        return votes;
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        VoteSettings.Reminder reminder = votes.settings().reminder();
        if (event.getPreviousServer() != null || !reminder.onJoin() || votes.settings().sites().isEmpty()
                || !reminded.add(player.getUniqueId())) {
            return;
        }
        context.proxy().getScheduler()
                .buildTask(plugin, () -> remindWhenDue(player))
                .delay(Math.max(1, reminder.delaySeconds()), TimeUnit.SECONDS)
                .schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        reminded.remove(event.getPlayer().getUniqueId());
    }

    private void remindWhenDue(Player player) {
        if (!player.isActive() || votes.settings().sites().isEmpty()) {
            return;
        }
        votes.stats(player.getUniqueId()).thenAccept(stats -> {
            if (System.currentTimeMillis() - stats.lastVote() >= votes.settings().reminder().afterMillis()
                    && player.isActive()) {
                player.sendMessage(context.messages().render(player, "vote.reminder")
                        .clickEvent(ClickEvent.runCommand(COMMAND))
                        .hoverEvent(HoverEvent.showText(context.messages().render(player, "vote.reminder-hover"))));
            }
        }).exceptionally(error -> {
            logger.debug("Could not read the votes of {}", player.getUsername(), error);
            return null;
        });
    }

    private void announce(Vote vote) {
        logger.info("Vote from {} on {}", vote.name(), vote.service().isBlank() ? "-" : vote.service());
        relay(vote);
        if (!votes.settings().broadcast()) {
            return;
        }
        String site = votes.settings().site(vote.service()).map(VoteSite::name)
                .orElse(vote.service().isBlank() ? "?" : vote.service());
        for (Player player : context.proxy().getAllPlayers()) {
            context.send(player, "vote.broadcast", Placeholder.unparsed("player", vote.name()),
                    Placeholder.unparsed("site", site));
        }
    }

    private void relay(Vote vote) {
        NetworkEvents.encode(new VoteReceivedEvent(vote)).ifPresent(message -> {
            message.addProperty("type", NetworkCalls.EVENT);
            message.addProperty("uuid", NetworkCalls.NOBODY.toString());
            byte[] data = message.toString().getBytes(StandardCharsets.UTF_8);
            for (RegisteredServer server : context.proxy().getAllServers()) {
                if (!server.getPlayersConnected().isEmpty()) {
                    server.sendPluginMessage(ModerationChannel.ID, data);
                }
            }
        });
    }
}
