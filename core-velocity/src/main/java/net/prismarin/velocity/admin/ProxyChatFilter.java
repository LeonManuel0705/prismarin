package net.prismarin.velocity.admin;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.moderation.ChatFilterAction;
import net.prismarin.api.moderation.ChatFilterMatch;
import net.prismarin.api.moderation.ChatFilterResult;
import net.prismarin.api.moderation.ChatFilterRule;
import net.prismarin.api.moderation.ChatFilterService;
import net.prismarin.api.moderation.ChatFilteredEvent;
import net.prismarin.api.moderation.ChatMessage;
import net.prismarin.shared.chatfilter.ChatFilterEngine;
import net.prismarin.shared.chatfilter.ChatFilterSettings;
import net.prismarin.shared.chatfilter.ChatViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProxyChatFilter implements ChatFilterService {

    public static final String BYPASS = "prismarin.chatfilter.bypass";

    private static final Logger LOGGER = LoggerFactory.getLogger("PrismarinCore");
    private static final String KEY = "chat-filter.";

    private final AdminContext context;
    private final ChatFilterEngine engine;
    private final ChatCommandPatterns commands;
    private final ChatViolations violations;

    public ProxyChatFilter(AdminContext context, ChatFilterSettings settings, ChatViolations violations, Clock clock) {
        this.context = context;
        this.engine = new ChatFilterEngine(settings, clock);
        this.violations = violations;
        List<String> problems = new ArrayList<>(settings.problems());
        problems.addAll(engine.problems());
        this.commands = settings.commands().enabled()
                ? ChatCommandPatterns.parse(settings.commands().channels(), problems)
                : ChatCommandPatterns.parse(Map.of(), problems);
        problems.forEach(problem -> LOGGER.warn("{}: {}", ChatFilterSettings.FILE_NAME, problem));
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player) || !event.getResult().isAllowed()
                || commands.empty() || player.hasPermission(BYPASS)) {
            return;
        }
        CommandExecuteEvent.InvocationInfo info = event.getInvocationInfo();
        if (info != null && info.signedState() == CommandExecuteEvent.SignedState.SIGNED_WITH_ARGS) {
            return;
        }
        String command = event.getResult().getCommand().orElse(event.getCommand());
        Optional<ChatCommandPatterns.Match> match = commands.match(command);
        if (match.isEmpty() || match.get().text().isBlank()) {
            return;
        }
        ChatFilterResult result = process(player, new ChatMessage(player.getUniqueId(), player.getUsername(),
                match.get().channel(), match.get().text()));
        if (result.blocked()) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        } else if (result.changed()) {
            String rebuilt = match.get().rebuild(result.text());
            event.setResult(context.proxy().getCommandManager().hasCommand(match.get().label())
                    ? CommandExecuteEvent.CommandResult.command(rebuilt)
                    : CommandExecuteEvent.CommandResult.forwardToServer(rebuilt));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        engine.forget(event.getPlayer().getUniqueId());
        violations.forget(event.getPlayer().getUniqueId());
    }

    @Override
    public ChatFilterResult check(String text) {
        return engine.check(Objects.requireNonNull(text, "text"));
    }

    @Override
    public ChatFilterResult filter(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        Player player = message.anonymous() ? null : context.proxy().getPlayer(message.sender()).orElse(null);
        if (player != null && player.hasPermission(BYPASS)) {
            return ChatFilterResult.untouched(message.text());
        }
        return process(player, message);
    }

    @Override
    public List<String> rules() {
        return engine.rules();
    }

    @Override
    public void registerRule(ChatFilterRule rule) {
        engine.register(rule);
    }

    @Override
    public void unregisterRule(String id) {
        engine.unregister(id);
    }

    private ChatFilterResult process(Player player, ChatMessage message) {
        ChatFilterResult result = engine.filter(message,
                rule -> player != null && player.hasPermission(BYPASS + "." + rule));
        if (result.clean()) {
            return result;
        }
        String server = player == null ? ProxyNotifications.PROXY
                : player.getCurrentServer().map(connection -> connection.getServerInfo().getName())
                        .orElse(ProxyNotifications.PROXY);
        ChatFilteredEvent filtered = new ChatFilteredEvent(message, result, context.services().config().serverId());
        try {
            context.services().events().publish(filtered);
        } catch (RuntimeException error) {
            LOGGER.warn("A ChatFilteredEvent listener failed", error);
        }
        if (filtered.cancelled()) {
            return ChatFilterResult.untouched(message.text());
        }
        if (player != null) {
            feedback(player, result);
        }
        violations.handle(server, ChatViolation.of(message, result, engine.settings()));
        return result;
    }

    private void feedback(Player player, ChatFilterResult result) {
        Optional<ChatFilterMatch> shown = result.blocking()
                .or(() -> first(result, ChatFilterAction.WARN))
                .or(() -> first(result, ChatFilterAction.REPLACE));
        shown.ifPresent(match -> context.send(player, KEY + switch (match.action()) {
            case BLOCK -> "blocked";
            case WARN -> "warned";
            default -> "changed";
        }, Placeholder.component("reason", reason(player, match.rule()))));
    }

    private Component reason(Player player, String rule) {
        String key = KEY + "reason." + rule;
        boolean known = !context.messages().locale().raw(key, context.messages().localeOf(player)).equals(key);
        return context.messages().render(player, known ? key : KEY + "reason.custom");
    }

    private static Optional<ChatFilterMatch> first(ChatFilterResult result, ChatFilterAction action) {
        return result.matches().stream().filter(match -> match.action() == action).findFirst();
    }
}
