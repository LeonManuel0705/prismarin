package net.prismarin.api.reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.prismarin.api.admin.NoticeText;

public record Reward(Map<String, Long> currencies, long xp, int levels, List<String> commands,
                     List<NoticeText> messages, Map<String, String> custom) {

    public static final Reward EMPTY = new Reward(Map.of(), 0L, 0, List.of(), List.of(), Map.of());

    public Reward {
        currencies = amounts(currencies);
        if (xp < 0L) {
            throw new IllegalArgumentException("xp must not be negative");
        }
        if (levels < 0) {
            throw new IllegalArgumentException("levels must not be negative");
        }
        commands = commands == null ? List.of() : commands.stream()
                .filter(command -> command != null && !command.isBlank())
                .map(String::strip)
                .toList();
        messages = messages == null ? List.of() : List.copyOf(messages);
        custom = custom == null || custom.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(custom));
    }

    public static Reward currency(String currency, long amount) {
        return new Reward(Map.of(currency, amount), 0L, 0, List.of(), List.of(), Map.of());
    }

    public static Reward experience(long xp) {
        return new Reward(Map.of(), xp, 0, List.of(), List.of(), Map.of());
    }

    public static Reward command(String command) {
        return new Reward(Map.of(), 0L, 0, List.of(command), List.of(), Map.of());
    }

    public static Reward message(NoticeText message) {
        return new Reward(Map.of(), 0L, 0, List.of(), List.of(message), Map.of());
    }

    public boolean isEmpty() {
        return currencies.isEmpty() && xp == 0L && levels == 0 && commands.isEmpty() && messages.isEmpty()
                && custom.isEmpty();
    }

    public Reward plus(Reward other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Map<String, Long> sum = new LinkedHashMap<>(currencies);
        other.currencies.forEach((currency, amount) -> sum.merge(currency, amount, Reward::add));
        List<String> allCommands = new ArrayList<>(commands);
        allCommands.addAll(other.commands);
        List<NoticeText> allMessages = new ArrayList<>(messages);
        allMessages.addAll(other.messages);
        Map<String, String> allCustom = new LinkedHashMap<>(custom);
        allCustom.putAll(other.custom);
        return new Reward(sum, add(xp, other.xp), (int) Math.min(Integer.MAX_VALUE, (long) levels + other.levels),
                allCommands, allMessages, allCustom);
    }

    private static long add(long first, long second) {
        long sum = first + second;
        return ((first ^ sum) & (second ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }

    private static Map<String, Long> amounts(Map<String, Long> currencies) {
        if (currencies == null || currencies.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> copy = new LinkedHashMap<>();
        currencies.forEach((currency, amount) -> {
            if (currency == null || currency.isBlank()) {
                throw new IllegalArgumentException("a reward currency needs an id");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException("the reward amount of " + currency + " must be positive");
            }
            copy.merge(currency.strip().toLowerCase(Locale.ROOT), amount, Reward::add);
        });
        return Collections.unmodifiableMap(copy);
    }
}
