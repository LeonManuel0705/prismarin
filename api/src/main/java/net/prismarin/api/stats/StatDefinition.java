package net.prismarin.api.stats;

import java.util.Objects;
import net.prismarin.api.admin.NoticeText;

public record StatDefinition(String key, NoticeText name, StatFormat format, StatOrder order, boolean inStats,
                             boolean inProfile) {

    public StatDefinition {
        key = StatKeys.key(key);
        name = name == null ? NoticeText.of(StatKeys.humanize(key)) : name;
        format = Objects.requireNonNullElse(format, StatFormat.NUMBER);
        order = Objects.requireNonNullElse(order, StatOrder.HIGHEST);
    }

    public static StatDefinition of(String key) {
        return new StatDefinition(key, null, StatFormat.NUMBER, StatOrder.HIGHEST, true, false);
    }

    public static StatDefinition of(String key, String name) {
        return new StatDefinition(key, NoticeText.of(name), StatFormat.NUMBER, StatOrder.HIGHEST, true, false);
    }

    public String game() {
        return StatKeys.game(key);
    }

    public StatDefinition withFormat(StatFormat value) {
        return new StatDefinition(key, name, value, order, inStats, inProfile);
    }

    public StatDefinition withOrder(StatOrder value) {
        return new StatDefinition(key, name, format, value, inStats, inProfile);
    }

    public StatDefinition withProfile(boolean value) {
        return new StatDefinition(key, name, format, order, inStats, value);
    }
}
