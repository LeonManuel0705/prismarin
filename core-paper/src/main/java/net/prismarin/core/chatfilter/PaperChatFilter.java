package net.prismarin.core.chatfilter;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.moderation.ChatFilterAction;
import net.prismarin.api.moderation.ChatFilterMatch;
import net.prismarin.api.moderation.ChatFilterResult;
import net.prismarin.api.moderation.ChatFilterRule;
import net.prismarin.api.moderation.ChatFilterService;
import net.prismarin.api.moderation.ChatFilteredEvent;
import net.prismarin.api.moderation.ChatMessage;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.moderation.ModerationChannel;
import net.prismarin.shared.chatfilter.ChatFilterEngine;
import net.prismarin.shared.chatfilter.ChatFilterSettings;
import net.prismarin.shared.chatfilter.ChatViolation;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class PaperChatFilter implements ChatFilterService, Listener {

    public static final String BYPASS = "prismarin.chatfilter.bypass";

    private static final String KEY = "chat-filter.";

    private final PrismarinCorePlugin plugin;
    private final ChatFilterEngine engine;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ModerationChannel channel;
    private final EventBus events;
    private final CoreConfig config;
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    @Inject
    public PaperChatFilter(PrismarinCorePlugin plugin, ChatFilterSettings settings, LocaleService locale,
                           ProfileService profiles, ModerationChannel channel, EventBus events, CoreConfig config) {
        this.plugin = plugin;
        this.engine = new ChatFilterEngine(settings, Clock.systemUTC());
        this.locale = locale;
        this.profiles = profiles;
        this.channel = channel;
        this.events = events;
        this.config = config;
        settings.problems().forEach(problem -> plugin.getLogger().warning(ChatFilterSettings.FILE_NAME + ": " + problem));
        engine.problems().forEach(problem -> plugin.getLogger().warning(ChatFilterSettings.FILE_NAME + ": " + problem));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS)) {
            return;
        }
        String text = plainText.serialize(event.message());
        ChatFilterResult result = process(player, ChatMessage.of(player.getUniqueId(), player.getName(), text));
        if (result.blocked()) {
            event.setCancelled(true);
        } else if (result.changed()) {
            event.message(Component.text(result.text()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        engine.forget(event.getPlayer().getUniqueId());
    }

    @Override
    public ChatFilterResult check(String text) {
        return engine.check(Objects.requireNonNull(text, "text"));
    }

    @Override
    public ChatFilterResult filter(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        Player player = message.anonymous() ? null : plugin.getServer().getPlayer(message.sender());
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
        ChatFilteredEvent filtered = new ChatFilteredEvent(message, result, config.serverId());
        events.publish(filtered);
        if (filtered.cancelled()) {
            return ChatFilterResult.untouched(message.text());
        }
        if (player != null) {
            feedback(player, result);
            report(player, ChatViolation.of(message, result, engine.settings()));
        }
        return result;
    }

    private void feedback(Player player, ChatFilterResult result) {
        Locale language = PlayerLocales.of(player, profiles);
        Optional<ChatFilterMatch> shown = result.blocking()
                .or(() -> first(result, ChatFilterAction.WARN))
                .or(() -> first(result, ChatFilterAction.REPLACE));
        shown.ifPresent(match -> player.sendMessage(locale.render(KEY + switch (match.action()) {
            case BLOCK -> "blocked";
            case WARN -> "warned";
            default -> "changed";
        }, language, Placeholder.component("reason", reason(language, match.rule())))));
    }

    private Component reason(Locale language, String rule) {
        String key = KEY + "reason." + rule;
        return locale.render(locale.raw(key, language).equals(key) ? KEY + "reason.custom" : key, language);
    }

    private static Optional<ChatFilterMatch> first(ChatFilterResult result, ChatFilterAction action) {
        return result.matches().stream().filter(match -> match.action() == action).findFirst();
    }

    private void report(Player player, ChatViolation violation) {
        if (violation.empty()) {
            return;
        }
        JsonObject message = violation.toJson();
        Runnable send = () -> {
            if (player.isOnline()) {
                channel.sendChatViolation(player, message);
            }
        };
        if (plugin.getServer().isPrimaryThread()) {
            send.run();
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, send);
        } catch (RuntimeException ignored) {
            return;
        }
    }
}
