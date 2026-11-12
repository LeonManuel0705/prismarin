package net.prismarin.api.cosmetics;

import java.util.Locale;
import java.util.regex.Pattern;
import net.prismarin.api.admin.NoticeText;

public record CosmeticRarity(String id, NoticeText name, int color, int order) {

    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    public static final String COMMON = "common";

    public CosmeticRarity {
        id = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("'" + id + "' is no rarity id, use " + ID.pattern());
        }
        name = name == null ? NoticeText.of(Cosmetic.humanize(id)) : name;
        color = color & 0xFFFFFF;
    }

    public static CosmeticRarity of(String id, String name, int color, int order) {
        return new CosmeticRarity(id, NoticeText.of(name), color, order);
    }
}
