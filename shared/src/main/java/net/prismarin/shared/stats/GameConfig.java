package net.prismarin.shared.stats;

import java.util.List;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatKeys;

public record GameConfig(String id, NoticeText name, String icon, String color, Integer order,
                         List<String> featured) {

    public GameConfig {
        id = StatKeys.gameId(id);
        featured = featured == null ? null : List.copyOf(featured);
        StatGame checked = apply(StatGame.of(id), name, icon, color, order, featured);
        icon = icon == null ? null : checked.icon();
        color = color == null ? null : checked.color();
        featured = featured == null ? null : checked.featured();
    }

    public static GameConfig named(String id, NoticeText name) {
        return new GameConfig(id, name, null, null, null, null);
    }

    public StatGame apply(StatGame base) {
        return apply(base == null ? StatGame.of(id) : base, name, icon, color, order, featured);
    }

    private static StatGame apply(StatGame base, NoticeText name, String icon, String color, Integer order,
                                  List<String> featured) {
        StatGame result = base;
        if (name != null) {
            result = result.withName(name);
        }
        if (icon != null) {
            result = result.withIcon(icon);
        }
        if (color != null) {
            result = result.withColor(color);
        }
        if (order != null) {
            result = result.withOrder(order);
        }
        if (featured != null) {
            result = result.withFeatured(featured);
        }
        return result;
    }
}
