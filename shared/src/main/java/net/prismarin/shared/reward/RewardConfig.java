package net.prismarin.shared.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.reward.Reward;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class RewardConfig {

    private RewardConfig() {
        throw new AssertionError();
    }

    public static Reward read(ConfigurationNode node, String path, List<String> problems) {
        if (node == null || node.virtual() || node.empty()) {
            return Reward.EMPTY;
        }
        if (!node.isMap()) {
            problems.add(path + " must be a section with currencies, xp, levels, commands, messages or custom");
            return Reward.EMPTY;
        }
        Map<String, Long> currencies = new LinkedHashMap<>();
        node.node("currencies").childrenMap().forEach((id, amount) -> {
            long value = amount.getLong(0L);
            if (value <= 0L) {
                problems.add(path + ".currencies." + id + " must be a whole number above 0");
            } else {
                currencies.merge(id.toString().strip().toLowerCase(Locale.ROOT), value, Long::sum);
            }
        });
        long xp = node.node("xp").getLong(0L);
        if (xp < 0L) {
            problems.add(path + ".xp must not be negative");
            xp = 0L;
        }
        int levels = node.node("levels").getInt(0);
        if (levels < 0) {
            problems.add(path + ".levels must not be negative");
            levels = 0;
        }
        List<String> commands = strings(node.node("commands"));
        List<NoticeText> messages = new ArrayList<>();
        for (String key : List.of("message", "messages")) {
            ConfigurationNode texts = node.node(key);
            if (texts.isList()) {
                for (ConfigurationNode entry : texts.childrenList()) {
                    text(entry, path + "." + key, problems).ifPresent(messages::add);
                }
            } else {
                text(texts, path + "." + key, problems).ifPresent(messages::add);
            }
        }
        Map<String, String> custom = new LinkedHashMap<>();
        node.node("custom").childrenMap().forEach((id, value) -> {
            String text = value.getString();
            if (text == null) {
                problems.add(path + ".custom." + id + " needs a value");
            } else {
                custom.put(id.toString().strip().toLowerCase(Locale.ROOT), text);
            }
        });
        return new Reward(currencies, xp, levels, commands, messages, custom);
    }

    public static Optional<NoticeText> text(ConfigurationNode node, String path, List<String> problems) {
        if (node == null || node.virtual() || node.empty()) {
            return Optional.empty();
        }
        try {
            if (node.isMap()) {
                Map<String, String> templates = new LinkedHashMap<>();
                node.childrenMap().forEach((language, text) -> {
                    String value = text.getString();
                    if (value != null && !value.isBlank()) {
                        templates.put(language.toString(), value);
                    }
                });
                return templates.isEmpty() ? Optional.empty() : Optional.of(NoticeText.of(templates));
            }
            String value = node.getString();
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(NoticeText.of(value));
        } catch (IllegalArgumentException e) {
            problems.add(path + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public static void write(ConfigurationNode node, Reward reward) throws SerializationException {
        for (Map.Entry<String, Long> currency : reward.currencies().entrySet()) {
            node.node("currencies", currency.getKey()).set(currency.getValue());
        }
        if (reward.xp() > 0L) {
            node.node("xp").set(reward.xp());
        }
        if (reward.levels() > 0) {
            node.node("levels").set(reward.levels());
        }
        if (!reward.commands().isEmpty()) {
            node.node("commands").setList(String.class, reward.commands());
        }
        if (reward.messages().size() == 1) {
            writeText(node.node("message"), reward.messages().getFirst());
        }
        for (Map.Entry<String, String> entry : reward.custom().entrySet()) {
            node.node("custom", entry.getKey()).set(entry.getValue());
        }
    }

    public static void writeText(ConfigurationNode node, NoticeText text) throws SerializationException {
        if (text.templates().size() == 1 && text.templates().containsKey(NoticeText.ANY_LANGUAGE)) {
            node.set(text.templates().get(NoticeText.ANY_LANGUAGE));
            return;
        }
        for (Map.Entry<String, String> entry : text.templates().entrySet()) {
            node.node(entry.getKey()).set(entry.getValue());
        }
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || node.empty()) {
            return List.of();
        }
        if (!node.isList()) {
            String single = node.getString();
            return single == null || single.isBlank() ? List.of() : List.of(single.strip());
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null && !value.isBlank()) {
                values.add(value.strip());
            }
        }
        return List.copyOf(values);
    }
}
