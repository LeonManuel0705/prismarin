package net.prismarin.core.cosmetics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticService;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.PlayerCosmetics;

public final class CosmeticPlaceholders {

    public static final String COUNT_PREFIX = "cosmetics_";
    public static final String SLOT_PREFIX = "cosmetic_";
    private static final String ID_SUFFIX = "_id";
    private static final String RARITY_SUFFIX = "_rarity";

    private final CosmeticService service;
    private final BiFunction<UUID, NoticeText, String> text;

    public CosmeticPlaceholders(CosmeticService service, BiFunction<UUID, NoticeText, String> text) {
        this.service = service;
        this.text = text;
    }

    public static boolean candidate(String key) {
        return key.startsWith(COUNT_PREFIX) || key.startsWith(SLOT_PREFIX);
    }

    public String resolve(UUID player, String key) {
        if (key.startsWith(COUNT_PREFIX)) {
            return count(player, key.substring(COUNT_PREFIX.length()));
        }
        if (!key.startsWith(SLOT_PREFIX)) {
            return null;
        }
        String rest = key.substring(SLOT_PREFIX.length());
        String part = "";
        for (String suffix : List.of(ID_SUFFIX, RARITY_SUFFIX)) {
            if (rest.endsWith(suffix)) {
                part = suffix;
                rest = rest.substring(0, rest.length() - suffix.length());
                break;
            }
        }
        Optional<CosmeticSlot> slot = CosmeticSlot.parse(rest);
        if (slot.isEmpty()) {
            return null;
        }
        Optional<Cosmetic> worn = player == null ? Optional.empty() : service.equipped(player, slot.get());
        if (worn.isEmpty()) {
            return "";
        }
        Cosmetic cosmetic = worn.get();
        return switch (part) {
            case ID_SUFFIX -> cosmetic.id();
            case RARITY_SUFFIX -> service.rarity(cosmetic.rarity())
                    .map(rarity -> text.apply(player, rarity.name())).orElse(cosmetic.rarity());
            default -> text.apply(player, cosmetic.name());
        };
    }

    private String count(UUID player, String part) {
        List<CosmeticSlot> slots = service.slots();
        List<Cosmetic> listed = service.cosmetics().stream().filter(cosmetic -> slots.contains(cosmetic.slot()))
                .toList();
        long total = listed.stream().filter(cosmetic -> !cosmetic.hidden()).count();
        long owned = player == null ? 0L : listed.stream().filter(cosmetic -> service.owns(player, cosmetic.id()))
                .count();
        return switch (part) {
            case "owned" -> Long.toString(owned);
            case "total" -> Long.toString(total);
            case "percent" -> Long.toString(total == 0L ? 0L : Math.min(100L, owned * 100L / total));
            case "hidden" -> Boolean.toString(player != null && service.cached(player)
                    .map(PlayerCosmetics::hidesOthers).orElse(false));
            default -> null;
        };
    }
}
