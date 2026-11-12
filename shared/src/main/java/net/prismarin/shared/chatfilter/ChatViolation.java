package net.prismarin.shared.chatfilter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.prismarin.api.moderation.ChatFilterAction;
import net.prismarin.api.moderation.ChatFilterMatch;
import net.prismarin.api.moderation.ChatFilterResult;
import net.prismarin.api.moderation.ChatFilterRule;
import net.prismarin.api.moderation.ChatMessage;
import net.prismarin.shared.chatfilter.ChatFilterSettings.Escalation;
import net.prismarin.shared.chatfilter.ChatFilterSettings.RulePolicy;

public record ChatViolation(UUID player, String name, String channel, String message, String shown, boolean blocked,
                            List<Entry> entries) {

    public static final String TYPE = "chat-violation";
    public static final int MAX_TEXT = 512;
    public static final int MAX_NAME = 32;
    public static final int MAX_DETAIL = 128;
    public static final int MAX_ENTRIES = 16;

    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9_-]{1,32}");

    public record Entry(String rule, ChatFilterAction action, String detail, boolean alert, boolean audit,
                        Escalation escalation) {

        public Entry {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(action, "action");
            detail = clip(detail, MAX_DETAIL);
            escalation = escalation == null ? Escalation.OFF : escalation;
        }
    }

    public ChatViolation {
        Objects.requireNonNull(player, "player");
        name = clip(name, MAX_NAME);
        channel = channel == null || !CHANNEL.matcher(channel).matches() ? ChatMessage.PUBLIC : channel;
        message = clip(message, MAX_TEXT);
        shown = clip(shown, MAX_TEXT);
        entries = List.copyOf(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries);
    }

    public static ChatViolation of(ChatMessage message, ChatFilterResult result, ChatFilterSettings settings) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ChatFilterMatch match : result.matches()) {
            if (!seen.add(match.rule())) {
                continue;
            }
            RulePolicy policy = settings.policy(match.rule());
            if (policy.reported()) {
                entries.add(new Entry(match.rule(), match.action(), match.detail(), policy.notifyStaff(),
                        policy.audit(), policy.escalation()));
            }
        }
        return new ChatViolation(message.sender(), message.senderName(), message.channel(), result.original(),
                result.blocked() ? "" : result.text(), result.blocked(), entries);
    }

    public boolean empty() {
        return entries.isEmpty();
    }

    public boolean alerts() {
        return entries.stream().anyMatch(Entry::alert);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("uuid", player.toString());
        json.addProperty("name", name);
        json.addProperty("channel", channel);
        json.addProperty("message", message);
        json.addProperty("shown", shown);
        json.addProperty("blocked", blocked);
        JsonArray list = new JsonArray();
        for (Entry entry : entries) {
            JsonObject item = new JsonObject();
            item.addProperty("rule", entry.rule());
            item.addProperty("action", entry.action().id());
            item.addProperty("detail", entry.detail());
            item.addProperty("alert", entry.alert());
            item.addProperty("audit", entry.audit());
            Escalation escalation = entry.escalation();
            JsonObject steps = new JsonObject();
            steps.addProperty("enabled", escalation.enabled());
            steps.addProperty("violations", escalation.violations());
            steps.addProperty("window", escalation.windowMillis());
            steps.addProperty("template", escalation.template());
            steps.addProperty("report", escalation.report());
            steps.addProperty("report-reason", escalation.reportReason());
            item.add("escalation", steps);
            list.add(item);
        }
        json.add("entries", list);
        return json;
    }

    public static Optional<ChatViolation> parse(JsonObject json) {
        try {
            if (json == null || !TYPE.equals(text(json.get("type")))) {
                return Optional.empty();
            }
            UUID player = UUID.fromString(text(json.get("uuid")));
            List<Entry> entries = new ArrayList<>();
            JsonElement list = json.get("entries");
            if (list != null && list.isJsonArray()) {
                for (JsonElement element : list.getAsJsonArray()) {
                    if (entries.size() >= MAX_ENTRIES || !element.isJsonObject()) {
                        break;
                    }
                    entry(element.getAsJsonObject()).ifPresent(entries::add);
                }
            }
            return Optional.of(new ChatViolation(player, text(json.get("name")),
                    text(json.get("channel")).toLowerCase(Locale.ROOT), text(json.get("message")),
                    text(json.get("shown")), flag(json.get("blocked")), entries));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Entry> entry(JsonObject item) {
        String rule = text(item.get("rule")).toLowerCase(Locale.ROOT);
        Optional<ChatFilterAction> action = ChatFilterAction.byId(text(item.get("action")));
        if (!ChatFilterRule.ID.matcher(rule).matches() || action.isEmpty()) {
            return Optional.empty();
        }
        Escalation escalation = Escalation.OFF;
        JsonElement steps = item.get("escalation");
        if (steps != null && steps.isJsonObject()) {
            JsonObject object = steps.getAsJsonObject();
            escalation = new Escalation(flag(object.get("enabled")), number(object.get("violations"), 3),
                    number(object.get("window"), 600_000L), text(object.get("template")), flag(object.get("report")),
                    text(object.get("report-reason")));
        }
        return Optional.of(new Entry(rule, action.get(), text(item.get("detail")), flag(item.get("alert")),
                flag(item.get("audit")), escalation));
    }

    private static String text(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    private static boolean flag(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static int number(JsonElement element, int fallback) {
        return (int) Math.clamp(number(element, (long) fallback), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private static long number(JsonElement element, long fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String clip(String value, int max) {
        String clean = value == null ? "" : value;
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
