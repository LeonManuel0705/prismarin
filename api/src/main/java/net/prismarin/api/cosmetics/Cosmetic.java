package net.prismarin.api.cosmetics;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import net.prismarin.api.admin.NoticeText;

public record Cosmetic(String id, CosmeticSlot slot, NoticeText name, NoticeText description, String rarity,
                       String icon, CosmeticPrice price, String permission, boolean hidden, int order,
                       CosmeticLook look) {

    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,47}");
    private static final Pattern PERMISSION = Pattern.compile("[a-z0-9_.*-]{1,128}");

    public Cosmetic {
        id = key(id);
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(look, "look");
        if (slot.hat() != (look instanceof CosmeticHat)) {
            throw new IllegalArgumentException("cosmetic " + id + ": " + (slot.hat()
                    ? "a hat needs an item or a head texture" : "the slot " + slot.id() + " needs particles"));
        }
        name = name == null ? NoticeText.of(humanize(id)) : name;
        rarity = rarity == null || rarity.isBlank() ? CosmeticRarity.COMMON : rarity.strip().toLowerCase(Locale.ROOT);
        icon = icon == null ? "" : icon.strip().toLowerCase(Locale.ROOT);
        permission = permission == null ? "" : permission.strip().toLowerCase(Locale.ROOT);
        if (!permission.isEmpty() && !PERMISSION.matcher(permission).matches()) {
            throw new IllegalArgumentException("cosmetic " + id + ": '" + permission + "' is no permission");
        }
    }

    public static Cosmetic particles(String id, CosmeticSlot slot, String name, CosmeticParticles look) {
        return new Cosmetic(id, slot, name == null ? null : NoticeText.of(name), null, null, null, null, null,
                false, 0, look);
    }

    public static Cosmetic hat(String id, String name, CosmeticHat look) {
        return new Cosmetic(id, CosmeticSlot.HAT, name == null ? null : NoticeText.of(name), null, null, null, null,
                null, false, 0, look);
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static String humanize(String id) {
        String text = id.replace('-', ' ').replace('_', ' ').strip();
        return text.isEmpty() ? id : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public Optional<CosmeticPrice> priceTag() {
        return Optional.ofNullable(price);
    }

    public Optional<NoticeText> about() {
        return Optional.ofNullable(description);
    }

    public boolean purchasable() {
        return price != null;
    }

    public boolean permissionOnly() {
        return price == null && !permission.isEmpty();
    }

    public Optional<CosmeticParticles> particles() {
        return look instanceof CosmeticParticles particles ? Optional.of(particles) : Optional.empty();
    }

    public Optional<CosmeticHat> hat() {
        return look instanceof CosmeticHat hat ? Optional.of(hat) : Optional.empty();
    }

    public Cosmetic withName(NoticeText value) {
        return new Cosmetic(id, slot, value, description, rarity, icon, price, permission, hidden, order, look);
    }

    public Cosmetic withDescription(String value) {
        return withDescription(value == null || value.isBlank() ? null : NoticeText.of(value));
    }

    public Cosmetic withDescription(NoticeText value) {
        return new Cosmetic(id, slot, name, value, rarity, icon, price, permission, hidden, order, look);
    }

    public Cosmetic withRarity(String value) {
        return new Cosmetic(id, slot, name, description, value, icon, price, permission, hidden, order, look);
    }

    public Cosmetic withIcon(String value) {
        return new Cosmetic(id, slot, name, description, rarity, value, price, permission, hidden, order, look);
    }

    public Cosmetic withPrice(String currency, long amount) {
        return withPrice(CosmeticPrice.of(currency, amount));
    }

    public Cosmetic withPrice(CosmeticPrice value) {
        return new Cosmetic(id, slot, name, description, rarity, icon, value, permission, hidden, order, look);
    }

    public Cosmetic withPermission(String value) {
        return new Cosmetic(id, slot, name, description, rarity, icon, price, value, hidden, order, look);
    }

    public Cosmetic withHidden(boolean value) {
        return new Cosmetic(id, slot, name, description, rarity, icon, price, permission, value, order, look);
    }

    public Cosmetic withOrder(int value) {
        return new Cosmetic(id, slot, name, description, rarity, icon, price, permission, hidden, value, look);
    }

    private static String key(String id) {
        String key = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (!ID.matcher(key).matches()) {
            throw new IllegalArgumentException("'" + id + "' is no cosmetic id, use " + ID.pattern());
        }
        return key;
    }
}
