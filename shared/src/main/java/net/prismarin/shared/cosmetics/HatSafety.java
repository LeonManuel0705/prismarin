package net.prismarin.shared.cosmetics;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HatSafety {

    public static final String DISGUISE_BASE = "paper";

    public enum Treatment {
        KEEP,
        REPLACE,
        DISGUISE,
        WARN,
        REFUSE
    }

    public record Verdict(Treatment treatment, String material, String model, String asset, String reason) {

        public Verdict {
            Objects.requireNonNull(treatment, "treatment");
            Objects.requireNonNull(material, "material");
            model = model == null ? "" : model;
            asset = asset == null ? "" : asset;
            reason = reason == null ? "" : reason;
        }

        public boolean usable() {
            return treatment != Treatment.REFUSE;
        }
    }

    public interface Editor {

        void neutralizeAttributes();

        void removeEnchantments();

        void disguise(String model, String asset);
    }

    private static final Map<String, String> REFUSED = Map.of(
            "turtle_helmet", "gives water breathing");
    private static final Map<String, String> REPLACED = Map.of(
            "carved_pumpkin", "jack_o_lantern");
    private static final Map<String, String> NOTICED = Map.of(
            "skeleton_skull", "skeletons",
            "zombie_head", "zombies",
            "creeper_head", "creepers",
            "piglin_head", "piglins");
    private static final Set<String> GOLDEN = Set.of("golden_helmet", "golden_chestplate", "golden_leggings",
            "golden_boots");

    private HatSafety() {
        throw new AssertionError();
    }

    public static Verdict check(String material) {
        String key = material == null ? "" : material.strip().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        String refused = REFUSED.get(key);
        if (refused != null) {
            return new Verdict(Treatment.REFUSE, key, "", "", key + " " + refused + ", pick another item");
        }
        String replacement = REPLACED.get(key);
        if (replacement != null) {
            return new Verdict(Treatment.REPLACE, replacement, "", "", key + " blurs the screen of the wearer and "
                    + "calms endermen, " + replacement + " is worn instead");
        }
        if (GOLDEN.contains(key)) {
            return new Verdict(Treatment.DISGUISE, DISGUISE_BASE, key, "gold", "");
        }
        String mob = NOTICED.get(key);
        if (mob != null) {
            return new Verdict(Treatment.WARN, key, "", "", key + " makes " + mob + " notice the wearer later, "
                    + "a player head with a texture looks the same without that");
        }
        return new Verdict(Treatment.KEEP, key, "", "", "");
    }

    public static void apply(Verdict verdict, Editor editor) {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(editor, "editor");
        if (!verdict.usable()) {
            throw new IllegalArgumentException(verdict.reason());
        }
        editor.neutralizeAttributes();
        editor.removeEnchantments();
        if (verdict.treatment() == Treatment.DISGUISE) {
            editor.disguise(verdict.model(), verdict.asset());
        }
    }
}
