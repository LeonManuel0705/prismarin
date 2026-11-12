package net.prismarin.api.moderation;

import java.util.Locale;
import java.util.Objects;

public record ChatFilterMatch(String rule, ChatFilterAction action, String detail, String text) {

    public ChatFilterMatch {
        Objects.requireNonNull(rule, "rule");
        rule = rule.trim().toLowerCase(Locale.ROOT);
        if (!ChatFilterRule.ID.matcher(rule).matches()) {
            throw new IllegalArgumentException("rule id '" + rule + "' must match " + ChatFilterRule.ID.pattern());
        }
        Objects.requireNonNull(action, "action");
        detail = detail == null ? "" : detail;
        text = text == null ? "" : text;
        if (action == ChatFilterAction.REPLACE && text.isEmpty()) {
            throw new IllegalArgumentException("a replacement needs the changed text");
        }
    }

    public static ChatFilterMatch block(String rule, String detail) {
        return new ChatFilterMatch(rule, ChatFilterAction.BLOCK, detail, "");
    }

    public static ChatFilterMatch warn(String rule, String detail) {
        return new ChatFilterMatch(rule, ChatFilterAction.WARN, detail, "");
    }

    public static ChatFilterMatch log(String rule, String detail) {
        return new ChatFilterMatch(rule, ChatFilterAction.LOG, detail, "");
    }

    public static ChatFilterMatch replace(String rule, String detail, String text) {
        return new ChatFilterMatch(rule, ChatFilterAction.REPLACE, detail, text);
    }

    public ChatFilterMatch as(ChatFilterAction other) {
        if (other == action) {
            return this;
        }
        if (other == ChatFilterAction.REPLACE) {
            return text.isEmpty() ? block(rule, detail) : new ChatFilterMatch(rule, other, detail, text);
        }
        return new ChatFilterMatch(rule, other, detail, "");
    }
}
