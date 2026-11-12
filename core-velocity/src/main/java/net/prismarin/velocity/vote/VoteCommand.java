package net.prismarin.velocity.vote;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.reward.VoteSite;
import net.prismarin.velocity.admin.AdminContext;
import org.slf4j.Logger;

public final class VoteCommand implements SimpleCommand {

    public static final String TEST_PERMISSION = "prismarin.vote.test";
    private static final String TEST = "test";

    private final AdminContext context;
    private final ProxyVoteService votes;
    private final Logger logger;

    public VoteCommand(AdminContext context, ProxyVoteService votes, Logger logger) {
        this.context = context;
        this.votes = votes;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length > 0 && args[0].equalsIgnoreCase(TEST) && source.hasPermission(TEST_PERMISSION)) {
            test(source, args);
            return;
        }
        List<VoteSite> sites = votes.settings().sites();
        if (sites.isEmpty()) {
            context.send(source, "vote.none");
            return;
        }
        context.send(source, "vote.header");
        for (VoteSite site : sites) {
            source.sendMessage(context.messages().render(source, "vote.site", Placeholder.unparsed("site", site.name()))
                    .clickEvent(ClickEvent.openUrl(site.url()))
                    .hoverEvent(HoverEvent.showText(context.messages().render(source, "vote.site-hover",
                            Placeholder.unparsed("url", site.url())))));
        }
        if (source instanceof Player player) {
            votes.stats(player.getUniqueId()).thenAccept(stats -> {
                if (stats.total() > 0L) {
                    context.send(source, "vote.stats", Placeholder.unparsed("total", Long.toString(stats.total())),
                            Placeholder.unparsed("streak", Integer.toString(stats.streak())));
                }
            }).exceptionally(error -> {
                logger.debug("Could not read the votes of {}", player.getUsername(), error);
                return null;
            });
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!source.hasPermission(TEST_PERMISSION)) {
            return List.of();
        }
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return TEST.startsWith(typed) ? List.of(TEST) : List.of();
        }
        if (!args[0].equalsIgnoreCase(TEST)) {
            return List.of();
        }
        if (args.length == 2) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            return context.proxy().getAllPlayers().stream()
                    .filter(player -> context.vanish().canSee(source, player))
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed))
                    .sorted()
                    .toList();
        }
        if (args.length == 3) {
            String typed = args[2].toLowerCase(Locale.ROOT);
            return Stream.concat(votes.settings().sites().stream().map(VoteSite::service), Stream.of(TEST))
                    .filter(service -> !service.isBlank() && service.toLowerCase(Locale.ROOT).startsWith(typed))
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private void test(CommandSource source, String[] args) {
        if (args.length < 2 || args.length > 3) {
            context.send(source, "vote.test-usage");
            return;
        }
        String name = args[1];
        String service = args.length == 3 ? args[2] : TEST;
        votes.receive(name, service, "").whenComplete((vote, error) -> {
            if (error != null) {
                logger.warn("The test vote for {} failed", name, error);
                context.send(source, "vote.failed");
                return;
            }
            context.send(source, vote.known() ? "vote.test-counted" : "vote.test-queued",
                    Placeholder.unparsed("player", vote.name()), Placeholder.unparsed("service", vote.service()));
        });
    }
}
