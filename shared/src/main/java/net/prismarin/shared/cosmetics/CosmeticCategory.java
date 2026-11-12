package net.prismarin.shared.cosmetics;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticSlot;

public record CosmeticCategory(CosmeticSlot slot, boolean enabled, NoticeText name, String icon, String pane,
                               int order, List<String> groups) {

    public static final String EVERYWHERE = "*";
    public static final String DEFAULT_ICON = "chest";
    public static final String DEFAULT_PANE = "light_blue_stained_glass_pane";

    public CosmeticCategory {
        Objects.requireNonNull(slot, "slot");
        name = name == null ? NoticeText.of(Cosmetic.humanize(slot.id())) : name;
        icon = icon == null || icon.isBlank() ? DEFAULT_ICON : icon.strip().toLowerCase(Locale.ROOT);
        pane = pane == null || pane.isBlank() ? DEFAULT_PANE : pane.strip().toLowerCase(Locale.ROOT);
        groups = groups == null ? List.of() : groups.stream()
                .filter(group -> group != null && !group.isBlank())
                .map(group -> group.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public static CosmeticCategory fallback(CosmeticSlot slot) {
        return new CosmeticCategory(slot, true, null, DEFAULT_ICON, DEFAULT_PANE, 100, List.of(EVERYWHERE));
    }
}
