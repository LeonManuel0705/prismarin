package net.prismarin.shared.reward;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RewardPlaceholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-z0-9_]{1,32})%");

    private RewardPlaceholders() {
        throw new AssertionError();
    }

    public static String fill(String template, Map<String, String> values) {
        if (template == null || template.isEmpty() || values.isEmpty()) {
            return template == null ? "" : template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String command(String template, Map<String, String> values) {
        String filled = fill(template, values).strip();
        return filled.startsWith("/") ? filled.substring(1) : filled;
    }
}
