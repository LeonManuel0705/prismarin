package net.prismarin.shared.chatfilter;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.prismarin.api.moderation.ChatFilterAction;
import net.prismarin.api.moderation.ChatFilterMatch;
import net.prismarin.api.moderation.ChatFilterResult;
import net.prismarin.api.moderation.ChatFilterRule;
import net.prismarin.api.moderation.ChatMessage;
import net.prismarin.shared.chatfilter.ChatFilterSettings.RulePolicy;

public final class ChatFilterEngine {

    private static final Logger LOGGER = Logger.getLogger(ChatFilterEngine.class.getName());
    private static final int MAX_LENGTH = 1_024;
    private static final long IDLE_MILLIS = 3_600_000L;
    private static final int CLEANUP_SIZE = 2_000;

    private record StateKey(UUID player, String channel) {
    }

    private final ChatFilterSettings settings;
    private final Clock clock;
    private final WordMatcher words;
    private final LinkMatcher links;
    private final Map<StateKey, ChatState> states = new ConcurrentHashMap<>();
    private final List<ChatFilterRule> custom = new CopyOnWriteArrayList<>();
    private final List<String> problems;

    public ChatFilterEngine(ChatFilterSettings settings, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        List<String> problems = new ArrayList<>();
        this.words = WordMatcher.compile(settings.words().list(), settings.words().patterns(),
                settings.words().allowed(), problems);
        this.links = new LinkMatcher(settings.links().blockIps(), settings.links().allowedDomains(),
                settings.links().topLevelDomains());
        this.problems = List.copyOf(problems);
    }

    public ChatFilterSettings settings() {
        return settings;
    }

    public List<String> problems() {
        return problems;
    }

    public ChatFilterResult check(String text) {
        return evaluate(ChatMessage.text(text), rule -> false, false);
    }

    public ChatFilterResult filter(ChatMessage message, Predicate<String> bypassed) {
        return evaluate(message, bypassed == null ? rule -> false : bypassed, !message.anonymous());
    }

    public List<String> rules() {
        List<String> ids = new ArrayList<>();
        for (String rule : ChatFilterSettings.BUILT_IN) {
            if (settings.policy(rule).enabled()) {
                ids.add(rule);
            }
        }
        for (ChatFilterRule rule : custom) {
            if (settings.policy(rule.id()).enabled()) {
                ids.add(rule.id());
            }
        }
        return List.copyOf(ids);
    }

    public void register(ChatFilterRule rule) {
        Objects.requireNonNull(rule, "rule");
        String id = Objects.requireNonNull(rule.id(), "rule id").toLowerCase(Locale.ROOT);
        if (!ChatFilterRule.ID.matcher(id).matches()) {
            throw new IllegalArgumentException("rule id '" + id + "' must match " + ChatFilterRule.ID.pattern());
        }
        if (ChatFilterSettings.BUILT_IN.contains(id)) {
            throw new IllegalArgumentException("the rule id " + id + " belongs to PRISMARIN");
        }
        synchronized (custom) {
            custom.removeIf(existing -> existing.id().equalsIgnoreCase(id));
            custom.add(rule);
        }
    }

    public void unregister(String id) {
        if (id == null) {
            return;
        }
        synchronized (custom) {
            custom.removeIf(existing -> existing.id().equalsIgnoreCase(id));
        }
    }

    public void forget(UUID player) {
        states.keySet().removeIf(key -> key.player().equals(player));
    }

    private ChatFilterResult evaluate(ChatMessage message, Predicate<String> bypassed, boolean stateful) {
        String original = message.text();
        if (original.isBlank()) {
            return ChatFilterResult.untouched(original);
        }
        String channel = message.channel();
        long now = clock.millis();
        Pass pass = new Pass(original.length() > MAX_LENGTH ? original.substring(0, MAX_LENGTH) : original);
        ChatState state = stateful ? state(message.sender(), channel, now) : null;
        String simplified = TextNormalizer.simplify(pass.text);
        if (state != null) {
            ChatFilterSettings.Flood flood = settings.flood();
            if (active(flood.policy(), ChatFilterSettings.FLOOD, channel, bypassed)
                    && state.flooding(now, flood.messages(), flood.windowMillis())) {
                pass.add(flood.policy().apply(ChatFilterMatch.block(ChatFilterSettings.FLOOD,
                        flood.messages() + "/" + ChatFilterSettings.text(flood.windowMillis()))));
                if (pass.blocked) {
                    return pass.result(original);
                }
            }
            ChatFilterSettings.Repeat repeat = settings.repeat();
            if (active(repeat.policy(), ChatFilterSettings.REPEAT, channel, bypassed)) {
                OptionalInt score = state.repeated(simplified, now, repeat.windowMillis(), repeat.similarity(),
                        repeat.minLength());
                if (score.isPresent()) {
                    pass.add(repeat.policy().apply(ChatFilterMatch.block(ChatFilterSettings.REPEAT,
                            score.getAsInt() + "%")));
                }
            }
        }
        if (active(settings.words().policy(), ChatFilterSettings.WORDS, channel, bypassed) && !words.empty()) {
            List<Span> found = words.find(pass.text);
            if (!found.isEmpty()) {
                pass.add(settings.words().policy().apply(ChatFilterMatch.replace(ChatFilterSettings.WORDS,
                        Span.merge(found).getFirst().detail(), replaced(pass.text, found))));
            }
        }
        if (active(settings.links().policy(), ChatFilterSettings.LINKS, channel, bypassed)) {
            List<Span> found = links.find(pass.text);
            if (!found.isEmpty()) {
                pass.add(settings.links().policy().apply(ChatFilterMatch.replace(ChatFilterSettings.LINKS,
                        found.getFirst().detail(), replaced(pass.text, found))));
            }
        }
        ChatFilterSettings.Caps caps = settings.caps();
        if (active(caps.policy(), ChatFilterSettings.CAPS, channel, bypassed)) {
            OptionalInt percent = CapsCheck.percent(pass.text, caps.minLetters());
            if (percent.isPresent() && percent.getAsInt() >= caps.percent()) {
                pass.add(caps.policy().apply(ChatFilterMatch.replace(ChatFilterSettings.CAPS,
                        percent.getAsInt() + "%", CapsCheck.soften(pass.text))));
            }
        }
        for (ChatFilterRule rule : custom) {
            String id = rule.id().toLowerCase(Locale.ROOT);
            RulePolicy policy = settings.policy(id);
            if (!active(policy, id, channel, bypassed)) {
                continue;
            }
            custom(rule, message.withText(pass.text)).map(policy::apply).ifPresent(pass::add);
        }
        if (state != null && !pass.blocked) {
            state.remember(simplified, now, settings.repeat().history());
        }
        return pass.result(original);
    }

    private Optional<ChatFilterMatch> custom(ChatFilterRule rule, ChatMessage message) {
        try {
            Optional<ChatFilterMatch> match = rule.check(message);
            return match == null ? Optional.empty() : match;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Chat filter rule " + rule.id() + " failed", e);
            return Optional.empty();
        }
    }

    private String replaced(String text, List<Span> spans) {
        String result = Span.replace(text, spans, settings.replacement());
        return result.isEmpty() ? settings.replacement() : result;
    }

    private static boolean active(RulePolicy policy, String rule, String channel, Predicate<String> bypassed) {
        return policy.covers(channel) && !bypassed.test(rule);
    }

    private ChatState state(UUID player, String channel, long now) {
        if (states.size() > CLEANUP_SIZE) {
            states.values().removeIf(state -> now - state.lastActivity() > IDLE_MILLIS);
        }
        return states.computeIfAbsent(new StateKey(player, channel), key -> new ChatState());
    }

    private static final class Pass {

        private final List<ChatFilterMatch> matches = new ArrayList<>();
        private String text;
        private boolean blocked;

        private Pass(String text) {
            this.text = text;
        }

        void add(ChatFilterMatch match) {
            matches.add(match);
            if (match.action() == ChatFilterAction.BLOCK) {
                blocked = true;
            } else if (match.action() == ChatFilterAction.REPLACE && !match.text().isEmpty()) {
                text = match.text();
            }
        }

        ChatFilterResult result(String original) {
            return new ChatFilterResult(original, blocked ? original : text, blocked, matches);
        }
    }
}
