package net.prismarin.core.cosmetics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticHat;
import net.prismarin.api.cosmetics.CosmeticLook;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPrice;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.cosmetics.CosmeticCategory;
import net.prismarin.shared.cosmetics.CosmeticShapes;
import net.prismarin.shared.cosmetics.HatSafety;
import net.prismarin.shared.cosmetics.HatTextures;
import net.prismarin.shared.reward.RewardConfig;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record CosmeticSettings(List<String> activeGroups, boolean equipAfterPurchase, int previewSeconds,
                               boolean hideCommand, boolean profileEntry, Performance performance,
                               Triggers triggers, List<CosmeticRarity> rarities, List<CosmeticCategory> categories,
                               List<Cosmetic> cosmetics, List<String> problems) {

    public static final String FILE_NAME = "cosmetics.yml";
    public static final String DEFAULT_CURRENCY = "coins";
    public static final int CATALOGUE_VERSION = 2;

    static final int DEFAULT_PREVIEW_SECONDS = 5;
    static final int DEFAULT_INTERVAL = 3;
    static final int DEFAULT_MAX_PARTICLES = 1500;
    static final int DEFAULT_VIEW_DISTANCE = 24;
    static final int DEFAULT_MAX_EFFECTS = 32;
    static final int DEFAULT_MAX_EFFECT_ENTITIES = 120;
    static final int DEFAULT_TRAIL_INTERVAL = 1;
    static final int DEFAULT_TRAIL_TICKS = 160;
    static final int DEFAULT_TRAIL_TRACKED = 200;
    static final List<String> DEFAULT_PROJECTILES = List.of("arrow", "spectral_arrow", "trident", "snowball", "egg",
            "ender_pearl");

    public record Performance(int intervalTicks, int maxParticles, int viewDistance, int maxEffects,
                              int maxEffectEntities) {

        public static final Performance DEFAULT = new Performance(DEFAULT_INTERVAL, DEFAULT_MAX_PARTICLES,
                DEFAULT_VIEW_DISTANCE, DEFAULT_MAX_EFFECTS, DEFAULT_MAX_EFFECT_ENTITIES);
    }

    public record Triggers(boolean projectileAuto, List<String> projectiles, int trailInterval, int trailTicks,
                           int trailTracked, boolean killAuto, boolean mobKills) {

        public static final Triggers DEFAULT = new Triggers(true, DEFAULT_PROJECTILES, DEFAULT_TRAIL_INTERVAL,
                DEFAULT_TRAIL_TICKS, DEFAULT_TRAIL_TRACKED, true, false);

        public Triggers {
            projectiles = projectiles == null ? List.of() : projectiles.stream()
                    .map(type -> type.strip().toLowerCase(Locale.ROOT))
                    .map(type -> type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type)
                    .filter(type -> !type.isEmpty()).distinct().toList();
        }

        public boolean tracks(String type) {
            return type != null && projectiles.contains(type.toLowerCase(Locale.ROOT));
        }

        public boolean auto(CosmeticSlot slot) {
            if (CosmeticSlot.PROJECTILE_TRAIL.equals(slot)) {
                return projectileAuto;
            }
            return CosmeticSlot.KILL_EFFECT.equals(slot) && killAuto;
        }
    }

    private record RaritySeed(String id, String color) {
    }

    private record CategorySeed(CosmeticSlot slot, String icon, String pane, int order, List<String> groups) {
    }

    private static final List<RaritySeed> RARITIES = List.of(
            new RaritySeed("common", "#D4D4D8"),
            new RaritySeed("uncommon", "#4ADE80"),
            new RaritySeed("rare", "#38BDF8"),
            new RaritySeed("epic", "#C084FC"),
            new RaritySeed("legendary", "#FBBF24"));

    private static final List<CategorySeed> CATEGORIES = List.of(
            new CategorySeed(CosmeticSlot.TRAIL, "blaze_powder", "orange_stained_glass_pane", 1, List.of()),
            new CategorySeed(CosmeticSlot.AURA, "end_crystal", "magenta_stained_glass_pane", 2, List.of()),
            new CategorySeed(CosmeticSlot.HAT, "golden_helmet", "light_blue_stained_glass_pane", 3, List.of()),
            new CategorySeed(CosmeticSlot.PROJECTILE_TRAIL, "spectral_arrow", "yellow_stained_glass_pane", 4,
                    List.of(CosmeticCategory.EVERYWHERE)),
            new CategorySeed(CosmeticSlot.KILL_EFFECT, "iron_sword", "red_stained_glass_pane", 5,
                    List.of(CosmeticCategory.EVERYWHERE)),
            new CategorySeed(CosmeticSlot.WIN_EFFECT, "totem_of_undying", "lime_stained_glass_pane", 6,
                    List.of(CosmeticCategory.EVERYWHERE)));

    static final String GERMANY_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0"
            + "L3RleHR1cmUvNWU3ODk5YjQ4MDY4NTg2OTdlMjgzZjA4NGQ5MTczZmU0ODc4ODY0NTM3NzQ2MjZiMjRiZDhjZmVj"
            + "Yzc3YjNmIn19fQ==";

    private static final List<String> RAINBOW = List.of("#F87171", "#FB923C", "#FACC15", "#4ADE80", "#22D3EE",
            "#A78BFA");

    private static final List<String> HEADER = Stream.concat(Stream.of(
            "Cosmetics: particle trails and auras, hats, arrow trails, kill effects and win effects.",
            "Players open the menu with /cosmetics or the cosmetics item in the lobby hotbar and buy with the",
            "currencies of economy.currencies. Ownership and what is equipped are stored per player in the",
            "network storage, so with MONGO or MYSQL they count on every server.",
            "Keep this file the same on every server, /prismarin reload reads it again.",
            ""), YamlComments.TEXT_HELP.stream()).toList();

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("active-groups", List.of(
                    "Server groups of servers.yml (or server ids) where trails, auras and hats show.",
                    "A category can list its own groups, \"*\" means every server. Game plugins can switch a",
                    "category on for a round through the API.")),
            Map.entry("equip-after-purchase", List.of("Puts a cosmetic on right after it was bought in the menu.")),
            Map.entry("preview-seconds", List.of(
                    "Right click in the menu shows particles in front of the player for this many seconds,",
                    "only to that player. 0 turns the preview off.")),
            Map.entry("sub-commands", List.of("false removes a part of /cosmetics everywhere.")),
            Map.entry("sub-commands.hide", List.of("/cosmetics hide: a player stops seeing the particles of others.")),
            Map.entry("lobby", List.of(
                    "The lobby reaches the menu through its cosmetics hotbar item (items.cosmetics in the lobby",
                    "config.yml).")),
            Map.entry("lobby.profile-entry", List.of("Also adds an entry to the lobby profile menu.")),
            Map.entry("performance", List.of(
                    "Particles run on one task that only exists while someone wears a trail or an aura.")),
            Map.entry("performance.interval-ticks", List.of("Ticks between two particle frames, 1 to 20.")),
            Map.entry("performance.max-particles", List.of(
                    "Particles sent per frame to all players together. Above it the rest waits for the next frame.")),
            Map.entry("performance.view-distance", List.of("Blocks within which players see the particles of others.")),
            Map.entry("performance.max-effects", List.of(
                    "Kill and win effects that may run at the same time on this server. Effects share",
                    "max-particles per tick with arrow trails, a full tick skips particles, not the effect.")),
            Map.entry("performance.max-effect-entities", List.of(
                    "Entities effects may have at once (squids, sheep, items, fireworks, displays). They are",
                    "only shown to the players who see the effect, cannot be hurt, picked up or used and are",
                    "removed when the effect ends, the chunk unloads or the server stops.")),
            Map.entry("triggers", List.of(
                    "Arrow trails and kill effects play by themselves, no game plugin needed. A game plugin that",
                    "wants to decide itself (for example only final kills) sets auto: false here or calls",
                    "setAutomatic(slot, false) and plays effects through the API or /cosmetics play.")),
            Map.entry("triggers.projectile-trail.projectiles", List.of(
                    "Projectile types that get the trail: arrow, spectral_arrow, trident, snowball, egg,",
                    "ender_pearl, fishing_bobber, wind_charge, splash_potion, lingering_potion, experience_bottle.")),
            Map.entry("triggers.projectile-trail.interval-ticks", List.of("Ticks between two trail frames, 1 to 10.")),
            Map.entry("triggers.projectile-trail.max-ticks", List.of(
                    "A trail stops when the projectile lands, is removed or flew this many ticks.")),
            Map.entry("triggers.projectile-trail.max-tracked", List.of("Projectiles with a trail at once.")),
            Map.entry("triggers.kill-effect.mob-kills", List.of(
                    "true also plays kill effects when a player kills a mob.")),
            Map.entry("catalogue-version", List.of(
                    "Newer PRISMARIN versions add their new default cosmetics once and raise this number.",
                    "Cosmetics you changed or removed are never touched or brought back.")),
            Map.entry("rarities", List.of(
                    "Rarities with their colour. name is a text, a message key or one text per language,",
                    "order sorts the menu.")),
            Map.entry("categories", List.of(
                    "One category per slot, a player wears one cosmetic per slot. enabled: false hides the",
                    "category and everything in it. groups empty uses active-groups.",
                    "projectile-trail and kill-effect play by themselves (see triggers), win-effect through",
                    "/cosmetics play or the API, because there is no common win event.")),
            Map.entry("cosmetics", List.of(
                    "category: trail, aura, hat, projectile-trail, kill-effect, win-effect or a slot of a plugin.",
                    "name and description: a text, a message key or one text per language.",
                    "price and currency: bought in the menu. Without a price a cosmetic comes from permission,",
                    "rewards (custom: {cosmetic: <id>} in daily-rewards.yml or votes.yml), /cosmetics give",
                    "or plugins. permission: players with it own the cosmetic for free.",
                    "hidden: true shows it in the menu only once it is owned.",
                    "Hats: item (any item or block) or texture (head texture value, URL or hash).",
                    "Particles under effect: particle (dust, flame, note, heart, end_rod, block, ...), shape",
                    "(point, ring, halo, spiral, orbit, wings, burst, heart, pillar, wave, rings, meteor, rain,",
                    "swirl, ghost, crown, gyro, rain-cloud, none or one of a plugin), colors for dust, size,",
                    "count, spread, speed, radius, height (lifts the shape), points, spin and sound (played",
                    "with kill and win effects). variant is the block or item of block and item particles.",
                    "Kill and win effects also take kind (particles, lightning, lightning-storm, squid,",
                    "fountain, tombstone, firework, fireworks, totem, sheep or one of a plugin) and duration",
                    "in ticks (20 are one second). variant is the item of a fountain, the block of a tombstone",
                    "and the firework type (ball, ball_large, star, burst, creeper).")));

    public CosmeticSettings {
        activeGroups = List.copyOf(activeGroups);
        rarities = List.copyOf(rarities);
        categories = List.copyOf(categories);
        cosmetics = List.copyOf(cosmetics);
        problems = List.copyOf(problems);
    }

    public static CosmeticSettings load(Path file, Collection<String> currencies, boolean economy) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = loader(file);
            CommentedConfigurationNode root;
            try {
                root = Files.exists(file) ? loader.load() : loader.createNode();
            } catch (ConfigurateException e) {
                return read(filled(), List.of(FILE_NAME + " could not be read, using the defaults: "
                        + e.getMessage()), currencies, economy);
            }
            if (fillDefaults(root)) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root, List.of(), currencies, economy);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CosmeticSettings defaults() {
        return read(filled(), List.of(), List.of("coins", "gems"), true);
    }

    static CosmeticSettings read(ConfigurationNode root, List<String> initial, Collection<String> currencies,
                                 boolean economy) {
        List<String> problems = new ArrayList<>(initial);
        List<String> groups = strings(root.node("active-groups"));
        ConfigurationNode performance = root.node("performance");
        Performance limits = new Performance(
                Math.clamp(performance.node("interval-ticks").getInt(DEFAULT_INTERVAL), 1, 20),
                Math.clamp(performance.node("max-particles").getInt(DEFAULT_MAX_PARTICLES), 50, 20_000),
                Math.clamp(performance.node("view-distance").getInt(DEFAULT_VIEW_DISTANCE), 4, 96),
                Math.clamp(performance.node("max-effects").getInt(DEFAULT_MAX_EFFECTS), 1, 500),
                Math.clamp(performance.node("max-effect-entities").getInt(DEFAULT_MAX_EFFECT_ENTITIES), 0, 2000));
        ConfigurationNode trails = root.node("triggers", "projectile-trail");
        ConfigurationNode kills = root.node("triggers", "kill-effect");
        Triggers triggers = new Triggers(trails.node("auto").getBoolean(true),
                trails.node("projectiles").virtual() ? DEFAULT_PROJECTILES : strings(trails.node("projectiles")),
                Math.clamp(trails.node("interval-ticks").getInt(DEFAULT_TRAIL_INTERVAL), 1, 10),
                Math.clamp(trails.node("max-ticks").getInt(DEFAULT_TRAIL_TICKS), 20, 1200),
                Math.clamp(trails.node("max-tracked").getInt(DEFAULT_TRAIL_TRACKED), 1, 2000),
                kills.node("auto").getBoolean(true), kills.node("mob-kills").getBoolean(false));
        List<CosmeticRarity> rarities = rarities(root.node("rarities"), problems);
        List<CosmeticCategory> categories = categories(root.node("categories"), problems);
        Set<String> rarityIds = new LinkedHashSet<>();
        rarities.forEach(rarity -> rarityIds.add(rarity.id()));
        Set<String> knownCurrencies = new LinkedHashSet<>();
        currencies.forEach(currency -> knownCurrencies.add(currency.toLowerCase(Locale.ROOT)));
        List<Cosmetic> cosmetics = new ArrayList<>();
        root.node("cosmetics").childrenMap().forEach((key, node) -> cosmetic(key.toString(), node, rarityIds,
                knownCurrencies, economy, problems).ifPresent(cosmetics::add));
        return new CosmeticSettings(groups, root.node("equip-after-purchase").getBoolean(true),
                Math.clamp(root.node("preview-seconds").getInt(DEFAULT_PREVIEW_SECONDS), 0, 30),
                root.node("sub-commands", "hide").getBoolean(true),
                root.node("lobby", "profile-entry").getBoolean(false), limits, triggers, rarities, categories,
                cosmetics, problems);
    }

    private static List<CosmeticRarity> rarities(ConfigurationNode node, List<String> problems) {
        List<CosmeticRarity> rarities = new ArrayList<>();
        int index = 0;
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            String id = entry.getKey().toString().strip().toLowerCase(Locale.ROOT);
            ConfigurationNode rarity = entry.getValue();
            if (!CosmeticRarity.ID.matcher(id).matches()) {
                problems.add("rarities." + id + " is no valid rarity id, use " + CosmeticRarity.ID.pattern());
                continue;
            }
            String colorText = rarity.node("color").getString("#FFFFFF");
            int color = color(colorText).orElseGet(() -> {
                problems.add("rarities." + id + ".color '" + colorText + "' is no colour such as #FBBF24");
                return 0xFFFFFF;
            });
            NoticeText name = RewardConfig.text(rarity.node("name"), "rarities." + id + ".name", problems)
                    .orElse(null);
            rarities.add(new CosmeticRarity(id, name, color, rarity.node("order").getInt(index)));
            index++;
        }
        if (rarities.isEmpty()) {
            problems.add("rarities is empty, every cosmetic is common");
            rarities.add(CosmeticRarity.of(CosmeticRarity.COMMON, "Common", 0xD4D4D8, 0));
        }
        return rarities;
    }

    private static List<CosmeticCategory> categories(ConfigurationNode node, List<String> problems) {
        Map<CosmeticSlot, CosmeticCategory> categories = new LinkedHashMap<>();
        for (CategorySeed seed : CATEGORIES) {
            categories.put(seed.slot(), new CosmeticCategory(seed.slot(), true,
                    NoticeText.of("cosmetics.category." + seed.slot().id()), seed.icon(), seed.pane(), seed.order(),
                    seed.groups()));
        }
        node.childrenMap().forEach((key, category) -> {
            Optional<CosmeticSlot> slot = CosmeticSlot.parse(key.toString());
            if (slot.isEmpty()) {
                problems.add("categories." + key + " is no valid slot, use " + CosmeticSlot.ID.pattern());
                return;
            }
            CosmeticCategory fallback = categories.getOrDefault(slot.get(), CosmeticCategory.fallback(slot.get()));
            NoticeText name = RewardConfig.text(category.node("name"), "categories." + key + ".name", problems)
                    .orElse(fallback.name());
            List<String> groups = category.node("groups").virtual() ? fallback.groups()
                    : strings(category.node("groups"));
            categories.put(slot.get(), new CosmeticCategory(slot.get(), category.node("enabled").getBoolean(true),
                    name, category.node("icon").getString(fallback.icon()),
                    category.node("pane").getString(fallback.pane()),
                    category.node("order").getInt(fallback.order()), groups));
        });
        return List.copyOf(categories.values());
    }

    private static Optional<Cosmetic> cosmetic(String id, ConfigurationNode node, Set<String> rarities,
                                               Set<String> currencies, boolean economy, List<String> problems) {
        String path = "cosmetics." + id;
        if (!Cosmetic.validId(id)) {
            problems.add(path + " is no valid id, use " + Cosmetic.ID.pattern());
            return Optional.empty();
        }
        String categoryText = node.node("category").getString("");
        Optional<CosmeticSlot> slot = CosmeticSlot.parse(categoryText);
        if (slot.isEmpty()) {
            problems.add(path + ".category '" + categoryText + "' is no category");
            return Optional.empty();
        }
        String rarity = node.node("rarity").getString(CosmeticRarity.COMMON).strip().toLowerCase(Locale.ROOT);
        if (!rarities.contains(rarity)) {
            String first = rarities.iterator().next();
            problems.add(path + ".rarity '" + rarity + "' is unknown, using " + first);
            rarity = first;
        }
        CosmeticPrice price = price(node, path, currencies, economy, problems);
        Optional<CosmeticLook> look = slot.get().hat() ? hat(node, path, problems)
                : particles(node.node("effect"), path + ".effect", slot.get(), problems);
        if (look.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Cosmetic(id, slot.get(),
                    RewardConfig.text(node.node("name"), path + ".name", problems).orElse(null),
                    RewardConfig.text(node.node("description"), path + ".description", problems).orElse(null),
                    rarity, node.node("icon").getString(""), price, node.node("permission").getString(""),
                    node.node("hidden").getBoolean(false), node.node("order").getInt(0), look.get()));
        } catch (IllegalArgumentException e) {
            problems.add(path + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static CosmeticPrice price(ConfigurationNode node, String path, Set<String> currencies, boolean economy,
                                       List<String> problems) {
        long amount = node.node("price").getLong(0L);
        if (amount < 0L) {
            problems.add(path + ".price must not be negative, it is not for sale");
            return null;
        }
        if (amount == 0L) {
            return null;
        }
        String currency = node.node("currency").getString(DEFAULT_CURRENCY).strip().toLowerCase(Locale.ROOT);
        if (economy && !currencies.contains(currency)) {
            problems.add(path + ".currency '" + currency + "' is no currency of economy.currencies, it is not for "
                    + "sale");
            return null;
        }
        try {
            return CosmeticPrice.of(currency, amount);
        } catch (IllegalArgumentException e) {
            problems.add(path + ".currency: " + e.getMessage());
            return null;
        }
    }

    private static Optional<CosmeticLook> hat(ConfigurationNode node, String path, List<String> problems) {
        String texture = node.node("texture").getString("");
        String item = node.node("item").getString("");
        if (!texture.isBlank()) {
            if (HatTextures.hash(texture).isEmpty()) {
                problems.add(path + ".texture is no head texture value, URL or hash");
                return Optional.empty();
            }
            return Optional.of(CosmeticHat.head(texture));
        }
        if (item.isBlank()) {
            problems.add(path + " needs an item or a texture");
            return Optional.empty();
        }
        HatSafety.Verdict verdict = HatSafety.check(item);
        if (!verdict.reason().isEmpty()) {
            problems.add(path + ".item: " + verdict.reason());
        }
        if (!verdict.usable()) {
            return Optional.empty();
        }
        return Optional.of(CosmeticHat.item(verdict.treatment() == HatSafety.Treatment.REPLACE ? verdict.material()
                : item));
    }

    private static Optional<CosmeticLook> particles(ConfigurationNode node, String path, CosmeticSlot slot,
                                                    List<String> problems) {
        String particle = node.node("particle").getString("");
        if (particle.isBlank()) {
            problems.add(path + ".particle is missing");
            return Optional.empty();
        }
        String shape = node.node("shape").getString(defaultShape(slot)).strip().toLowerCase(Locale.ROOT);
        List<Integer> colors = new ArrayList<>();
        for (String text : strings(node.node("colors"))) {
            color(text).ifPresentOrElse(colors::add,
                    () -> problems.add(path + ".colors: '" + text + "' is no colour such as #22D3EE"));
        }
        try {
            return Optional.of(new CosmeticParticles(shape, particle, colors,
                    (float) node.node("size").getDouble(1.0), node.node("count").getInt(1),
                    node.node("spread").getDouble(0.0), node.node("speed").getDouble(0.0),
                    node.node("radius").getDouble(0.0), node.node("height").getDouble(0.0),
                    node.node("points").getInt(0), node.node("spin").getDouble(0.0),
                    node.node("sound").getString(""), node.node("kind").getString(CosmeticParticles.PARTICLES),
                    node.node("duration").getInt(0), node.node("variant").getString("")));
        } catch (IllegalArgumentException e) {
            problems.add(path + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    static String defaultShape(CosmeticSlot slot) {
        if (slot.equals(CosmeticSlot.AURA)) {
            return CosmeticShapes.RING;
        }
        if (slot.equals(CosmeticSlot.KILL_EFFECT) || slot.equals(CosmeticSlot.WIN_EFFECT)) {
            return CosmeticShapes.BURST;
        }
        return CosmeticShapes.POINT;
    }

    static Optional<Integer> color(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String hex = text.strip();
        if (hex.startsWith("&#")) {
            hex = hex.substring(2);
        } else if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return Optional.empty();
        }
        return Optional.of(HexFormat.fromHexDigits(hex));
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = false;
        if (root.node("active-groups").virtual()) {
            root.node("active-groups").setList(String.class, List.of("lobby"));
            changed = true;
        }
        changed |= setIfAbsent(root.node("equip-after-purchase"), true);
        changed |= setIfAbsent(root.node("preview-seconds"), DEFAULT_PREVIEW_SECONDS);
        changed |= setIfAbsent(root.node("sub-commands", "hide"), true);
        changed |= setIfAbsent(root.node("lobby", "profile-entry"), false);
        changed |= setIfAbsent(root.node("performance", "interval-ticks"), DEFAULT_INTERVAL);
        changed |= setIfAbsent(root.node("performance", "max-particles"), DEFAULT_MAX_PARTICLES);
        changed |= setIfAbsent(root.node("performance", "view-distance"), DEFAULT_VIEW_DISTANCE);
        changed |= setIfAbsent(root.node("performance", "max-effects"), DEFAULT_MAX_EFFECTS);
        changed |= setIfAbsent(root.node("performance", "max-effect-entities"), DEFAULT_MAX_EFFECT_ENTITIES);
        ConfigurationNode trails = root.node("triggers", "projectile-trail");
        changed |= setIfAbsent(trails.node("auto"), true);
        if (trails.node("projectiles").virtual()) {
            trails.node("projectiles").setList(String.class, DEFAULT_PROJECTILES);
            changed = true;
        }
        changed |= setIfAbsent(trails.node("interval-ticks"), DEFAULT_TRAIL_INTERVAL);
        changed |= setIfAbsent(trails.node("max-ticks"), DEFAULT_TRAIL_TICKS);
        changed |= setIfAbsent(trails.node("max-tracked"), DEFAULT_TRAIL_TRACKED);
        changed |= setIfAbsent(root.node("triggers", "kill-effect", "auto"), true);
        changed |= setIfAbsent(root.node("triggers", "kill-effect", "mob-kills"), false);
        if (root.node("rarities").virtual()) {
            int order = 0;
            for (RaritySeed seed : RARITIES) {
                ConfigurationNode rarity = root.node("rarities", seed.id());
                rarity.node("name").set("cosmetics.rarity." + seed.id());
                rarity.node("color").set(seed.color());
                rarity.node("order").set(order++);
            }
            changed = true;
        }
        for (CategorySeed seed : CATEGORIES) {
            ConfigurationNode category = root.node("categories", seed.slot().id());
            if (!category.virtual()) {
                continue;
            }
            category.node("enabled").set(true);
            category.node("name").set("cosmetics.category." + seed.slot().id());
            category.node("icon").set(seed.icon());
            category.node("pane").set(seed.pane());
            category.node("order").set(seed.order());
            category.node("groups").setList(String.class, seed.groups());
            changed = true;
        }
        ConfigurationNode version = root.node("catalogue-version");
        if (root.node("cosmetics").virtual()) {
            version.set(CATALOGUE_VERSION);
            writeCatalogue(root.node("cosmetics"), 0);
            changed = true;
        } else {
            int seeded = version.getInt(1);
            if (seeded < CATALOGUE_VERSION) {
                writeCatalogue(root.node("cosmetics"), seeded);
                version.set(CATALOGUE_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static void writeCatalogue(ConfigurationNode cosmetics, int after) throws SerializationException {
        Seeds seeds = new Seeds(cosmetics, after);
        seeds.since(1);
        seeds.add("flame-trail", "trail", "common", "blaze_powder")
                .name("Flame trail", "Flammenspur")
                .about("Small flames follow your steps.", "Kleine Flammen folgen deinen Schritten.")
                .price(500, "coins").effect("point", "flame").set("count", 2).set("spread", 0.08)
                .set("speed", 0.01);
        seeds.add("heart-trail", "trail", "uncommon", "red_dye")
                .name("Heart trail", "Herzspur")
                .about("Hearts wherever you walk.", "Herzen, wo immer du gehst.")
                .price(1000, "coins").effect("point", "heart").set("spread", 0.2).set("height", 0.3);
        seeds.add("note-trail", "trail", "uncommon", "note_block")
                .name("Music trail", "Musikspur")
                .about("Colourful notes on every step.", "Bunte Noten bei jedem Schritt.")
                .price(1000, "coins").effect("point", "note").set("spread", 0.3).set("height", 0.4);
        seeds.add("rainbow-trail", "trail", "rare", "prismarine_crystals")
                .name("Rainbow trail", "Regenbogenspur")
                .about("A trail that runs through every colour.", "Eine Spur, die durch alle Farben läuft.")
                .price(2500, "coins").effect("point", "dust").colors(RAINBOW).set("size", 1.2).set("count", 3)
                .set("spread", 0.12);
        seeds.add("soul-trail", "trail", "rare", "soul_torch")
                .name("Soul trail", "Seelenspur")
                .about("Blue soul flames, cold as the night.", "Blaue Seelenflammen, kalt wie die Nacht.")
                .price(25, "gems").effect("point", "soul_fire_flame").set("count", 2).set("spread", 0.08)
                .set("speed", 0.01);
        seeds.add("cherry-trail", "trail", "epic", "cherry_sapling")
                .name("Cherry blossom", "Kirschblüten")
                .about("Petals drift down behind you.", "Blütenblätter schweben hinter dir zu Boden.")
                .price(4000, "coins").effect("point", "cherry_leaves").set("count", 2).set("spread", 0.35)
                .set("height", 1.2);
        seeds.add("streak-flame", "trail", "epic", "fire_charge")
                .name("Streak flame", "Serienflamme")
                .about("Only for 30 daily rewards in a row.", "Nur für 30 Tagesbelohnungen am Stück.")
                .effect("point", "small_flame").set("count", 3).set("spread", 0.15).set("speed", 0.01);
        seeds.add("ring-aura", "aura", "common", "prismarine_shard")
                .name("Prismarine ring", "Prismarinring")
                .about("A calm ring of light at your feet.", "Ein ruhiger Lichtring um deine Füße.")
                .price(750, "coins").effect("ring", "dust").colors(List.of("#22D3EE", "#2DD4BF"))
                .set("size", 0.9);
        seeds.add("snow-cloud", "aura", "uncommon", "snowball")
                .name("Snow cloud", "Schneewolke")
                .about("Your own little snowfall.", "Dein eigener kleiner Schneefall.")
                .price(1500, "coins").effect("ring", "snowflake").set("radius", 0.5).set("height", 2.3)
                .set("points", 8).set("spread", 0.15);
        seeds.add("light-spiral", "aura", "rare", "end_rod")
                .name("Light spiral", "Lichtspirale")
                .about("Rods of light circle up around you.", "Lichtstäbe kreisen um dich nach oben.")
                .price(3000, "coins").effect("spiral", "end_rod");
        seeds.add("spark-orbit", "aura", "epic", "glow_berries")
                .name("Spark orbit", "Funkenbahn")
                .about("Three bright sparks circle around you.", "Drei helle Funken kreisen um dich.")
                .price(40, "gems").effect("orbit", "dust").colors(List.of("#FDE68A", "#F472B6", "#22D3EE"))
                .set("size", 1.4);
        seeds.add("angel-wings", "aura", "legendary", "feather")
                .name("Angel wings", "Engelsflügel")
                .about("Soft white wings with a golden rim.", "Weiche weiße Flügel mit goldenem Rand.")
                .price(80, "gems").effect("wings", "dust").colors(List.of("#FDE68A", "#FFFFFF")).set("size", 0.9);
        seeds.add("ember-wings", "aura", "legendary", "magma_cream")
                .name("Ember wings", "Glutflügel")
                .about("Dark wings that glow like embers.", "Dunkle Flügel, die wie Glut leuchten.")
                .price(80, "gems").effect("wings", "dust").colors(List.of("#F97316", "#7F1D1D")).set("size", 0.9);
        seeds.add("vip-halo", "aura", "epic", "gold_nugget")
                .name("VIP halo", "VIP-Heiligenschein")
                .about("A golden halo for everyone who supports us.",
                        "Ein goldener Heiligenschein für alle, die uns unterstützen.")
                .permission("prismarin.cosmetics.vip").effect("halo", "dust").colors(List.of("#FBBF24"))
                .set("size", 0.8);
        seeds.add("team-wings", "aura", "legendary", "prismarine_crystals")
                .name("Team wings", "Team-Flügel")
                .about("Wings in the brand colours, only for the team.",
                        "Flügel in den Markenfarben, nur fürs Team.")
                .permission("prismarin.cosmetics.team").effect("wings", "dust")
                .colors(List.of("#22D3EE", "#2DD4BF")).set("size", 0.9);
        seeds.add("pumpkin-head", "hat", "common", "")
                .name("Pumpkin head", "Kürbiskopf")
                .about("Glows a little, scares a little.", "Leuchtet ein bisschen, erschreckt ein bisschen.")
                .price(600, "coins").set("item", "jack_o_lantern");
        seeds.add("astronaut", "hat", "uncommon", "")
                .name("Astronaut helmet", "Astronautenhelm")
                .about("Ready for space.", "Bereit fürs All.")
                .price(1500, "coins").set("item", "glass");
        seeds.add("tnt-head", "hat", "rare", "")
                .name("TNT head", "TNT-Kopf")
                .about("Please do not light.", "Bitte nicht anzünden.")
                .price(3000, "coins").set("item", "tnt");
        seeds.add("birthday-cake", "hat", "rare", "")
                .name("Birthday cake", "Geburtstagstorte")
                .about("Every day is a party.", "Jeder Tag ist eine Party.")
                .price(20, "gems").set("item", "cake");
        seeds.add("germany-fan", "hat", "epic", "")
                .name("Germany fan", "Deutschland-Fan")
                .about("Black, red and gold for match days.", "Schwarz, Rot und Gold für Spieltage.")
                .price(30, "gems").set("texture", GERMANY_TEXTURE);
        seeds.add("vip-crown", "hat", "legendary", "")
                .name("VIP crown", "VIP-Krone")
                .about("Gold for everyone who supports us.", "Gold für alle, die uns unterstützen.")
                .permission("prismarin.cosmetics.vip").set("item", "golden_helmet");
        seeds.add("spark-arrows", "projectile-trail", "uncommon", "arrow")
                .name("Spark arrows", "Funkenpfeile")
                .about("Sparks follow your arrows and snowballs.", "Funken folgen deinen Pfeilen und Schneebällen.")
                .price(1200, "coins").effect("point", "electric_spark").set("count", 2).set("spread", 0.05);
        seeds.add("heart-burst", "kill-effect", "uncommon", "pink_dye")
                .name("Heart burst", "Herzexplosion")
                .about("A heart where your opponent stood.", "Ein Herz, wo dein Gegner stand.")
                .price(1500, "coins").effect("heart", "dust").colors(List.of("#F43F5E")).set("size", 1.1)
                .set("sound", "block.amethyst_block.chime");
        seeds.add("firework-kill", "kill-effect", "rare", "firework_rocket")
                .name("Firework", "Feuerwerk")
                .about("A colourful bang for every knockout.", "Ein bunter Knall für jeden K.o.")
                .price(20, "gems").effect("burst", "firework").set("speed", 0.02)
                .set("sound", "entity.firework_rocket.blast");
        seeds.add("golden-pillar", "win-effect", "epic", "totem_of_undying")
                .name("Golden pillar", "Goldene Säule")
                .about("A column of light for every win.", "Eine Lichtsäule für jeden Sieg.")
                .price(50, "gems").effect("pillar", "totem_of_undying").set("count", 2).set("spread", 0.1)
                .set("sound", "ui.toast.challenge_complete");
        seeds.since(2);
        seeds.add("frost-halo", "aura", "uncommon", "blue_ice")
                .name("Frost halo", "Frostkranz")
                .about("A ring of snowflakes above your head.", "Ein Ring aus Schneeflocken über deinem Kopf.")
                .price(1600, "coins").effect("halo", "snowflake").set("radius", 0.4).set("points", 10)
                .set("spread", 0.02);
        seeds.add("heart-circle", "aura", "rare", "pink_tulip")
                .name("Heart circle", "Herzkreis")
                .about("Hearts circle around you.", "Herzen kreisen um dich herum.")
                .price(2200, "coins").effect("ring", "heart").set("radius", 0.9).set("height", 0.9)
                .set("points", 8).set("spin", 0.04);
        seeds.add("rain-cloud", "aura", "rare", "white_wool")
                .name("Rain cloud", "Regenwolke")
                .about("Your own little cloud, rain included.", "Deine eigene kleine Wolke, Regen inklusive.")
                .price(25, "gems").effect("rain-cloud", "dust").colors(List.of("#F1F5F9", "#38BDF8"))
                .set("size", 1.3).set("points", 12);
        seeds.add("ender-rings", "aura", "epic", "ender_eye")
                .name("Ender rings", "Ender-Ringe")
                .about("Two rings of void light orbit around you.", "Zwei Ringe aus Leerenlicht kreisen um dich.")
                .price(4500, "coins").effect("gyro", "dust").colors(List.of("#A855F7", "#22D3EE"))
                .set("size", 0.8).set("points", 14);
        seeds.add("flame-crown", "aura", "epic", "fire_coral")
                .name("Crown of flames", "Flammenkrone")
                .about("A crown of fire that burns nothing.", "Eine Krone aus Feuer, die nichts verbrennt.")
                .price(45, "gems").effect("crown", "flame").set("radius", 0.3).set("points", 12);
        seeds.add("melon-head", "hat", "common", "")
                .name("Melon head", "Melonenkopf")
                .about("Fresh, round and a little sticky.", "Frisch, rund und ein bisschen klebrig.")
                .price(600, "coins").set("item", "melon");
        seeds.add("glowstone-head", "hat", "common", "")
                .name("Glowstone head", "Leuchtsteinkopf")
                .about("Bright ideas, literally.", "Ein heller Kopf, ganz wörtlich.")
                .price(700, "coins").set("item", "glowstone");
        seeds.add("sea-lantern", "hat", "uncommon", "")
                .name("Sea lantern", "Seelaterne")
                .about("Glows like the ocean at night.", "Leuchtet wie das Meer bei Nacht.")
                .price(1200, "coins").set("item", "sea_lantern");
        seeds.add("slime-head", "hat", "uncommon", "")
                .name("Slime head", "Schleimkopf")
                .about("Wobbly, green and proud of it.", "Wabbelig, grün und stolz darauf.")
                .price(1400, "coins").set("item", "slime_block");
        seeds.add("honey-head", "hat", "uncommon", "")
                .name("Honey head", "Honigkopf")
                .about("Sweet as can be.", "Zuckersüß von oben bis unten.")
                .price(1400, "coins").set("item", "honey_block");
        seeds.add("ice-cube", "hat", "rare", "")
                .name("Ice cube", "Eiswürfel")
                .about("Keeps a cool head in every round.", "Behält in jeder Runde einen kühlen Kopf.")
                .price(2000, "coins").set("item", "packed_ice");
        seeds.add("tinted-visor", "hat", "rare", "")
                .name("Tinted visor", "Getöntes Visier")
                .about("Nobody sees where you are looking.", "Niemand sieht, wohin du schaust.")
                .price(15, "gems").set("item", "tinted_glass");
        seeds.add("jukebox-head", "hat", "rare", "")
                .name("DJ booth", "DJ-Pult")
                .about("The party is wherever you are.", "Die Party ist dort, wo du bist.")
                .price(2500, "coins").set("item", "jukebox");
        seeds.add("soul-lantern", "hat", "rare", "")
                .name("Soul lantern", "Seelenlaterne")
                .about("A blue light that only rewards bring.", "Ein blaues Licht, das nur Belohnungen bringen.")
                .set("item", "soul_lantern");
        seeds.add("amethyst-crown", "hat", "epic", "")
                .name("Amethyst crown", "Amethystkrone")
                .about("Crystals that grew right on your head.", "Kristalle, die direkt auf deinem Kopf wuchsen.")
                .price(5000, "coins").set("item", "amethyst_cluster");
        seeds.add("wither-skull", "hat", "epic", "")
                .name("Wither skull", "Witherschädel")
                .about("Dark, rare and a little spooky.", "Dunkel, selten und ein bisschen gruselig.")
                .price(40, "gems").set("item", "wither_skeleton_skull");
        seeds.add("blossom-crown", "hat", "epic", "")
                .name("Blossom crown", "Blütenkrone")
                .about("A blooming bush for the most loyal players.",
                        "Ein blühender Busch für die treuesten Spieler.")
                .set("item", "flowering_azalea");
        seeds.add("dragon-head", "hat", "legendary", "")
                .name("Dragon head", "Drachenkopf")
                .about("Its jaw moves while you walk.", "Sein Kiefer bewegt sich, während du läufst.")
                .price(90, "gems").set("item", "dragon_head");
        seeds.add("team-beacon", "hat", "legendary", "")
                .name("Team beacon", "Team-Leuchtfeuer")
                .about("A beacon for the team, so everyone finds you.",
                        "Ein Leuchtfeuer fürs Team, damit dich alle finden.")
                .permission("prismarin.cosmetics.team").set("item", "beacon");
        seeds.add("smoke-arrows", "projectile-trail", "common", "campfire")
                .name("Smoke trail", "Rauchspur")
                .about("A grey plume marks every shot.", "Eine graue Rauchfahne markiert jeden Schuss.")
                .price(500, "coins").effect("point", "large_smoke").set("spread", 0.02);
        seeds.add("drip-arrows", "projectile-trail", "common", "water_bucket")
                .name("Water drops", "Wassertropfen")
                .about("Your shots drip as if they came out of the sea.",
                        "Deine Schüsse tropfen, als kämen sie gerade aus dem Meer.")
                .price(500, "coins").effect("point", "falling_water").set("count", 2).set("spread", 0.05);
        seeds.add("flame-arrows", "projectile-trail", "common", "flint_and_steel")
                .name("Flame arrows", "Flammenpfeile")
                .about("Your arrows burn bright without setting anything alight.",
                        "Deine Pfeile brennen hell, ohne etwas anzuzünden.")
                .price(600, "coins").effect("point", "flame").set("count", 2).set("spread", 0.03)
                .set("speed", 0.01);
        seeds.add("snow-arrows", "projectile-trail", "common", "snowball")
                .name("Snow arrows", "Schneepfeile")
                .about("Snowflakes drift behind your shots.", "Schneeflocken wehen hinter deinen Schüssen her.")
                .price(600, "coins").effect("point", "snowflake").set("count", 2).set("spread", 0.05);
        seeds.add("crit-arrows", "projectile-trail", "uncommon", "glowstone_dust")
                .name("Crit stars", "Kritische Sterne")
                .about("Sparkling stars like a perfect hit.", "Funkelnde Sterne wie bei einem perfekten Treffer.")
                .price(900, "coins").effect("point", "crit").set("count", 3).set("spread", 0.05);
        seeds.add("heart-arrows", "projectile-trail", "uncommon", "poppy")
                .name("Love arrows", "Liebespfeile")
                .about("Hearts follow every shot.", "Herzen folgen jedem Schuss.")
                .price(1000, "coins").effect("point", "heart");
        seeds.add("note-arrows", "projectile-trail", "uncommon", "music_disc_cat")
                .name("Melody arrows", "Melodiepfeile")
                .about("Every shot plays a little tune in colour.", "Jeder Schuss spielt eine kleine Melodie in Farbe.")
                .price(1000, "coins").effect("point", "note");
        seeds.add("ender-arrows", "projectile-trail", "uncommon", "ender_pearl")
                .name("Ender trail", "Enderspur")
                .about("Purple portal dust swirls around your shots.",
                        "Violetter Portalstaub wirbelt um deine Schüsse.")
                .price(1200, "coins").effect("point", "portal").set("count", 3).set("spread", 0.1);
        seeds.add("glyph-arrows", "projectile-trail", "rare", "enchanting_table")
                .name("Glyph trail", "Glyphenspur")
                .about("Glyphs from the enchanting table chase your shots.",
                        "Zeichen vom Zaubertisch jagen deinen Schüssen nach.")
                .price(2000, "coins").effect("point", "enchant").set("count", 5).set("spread", 0.2)
                .set("speed", 0.4);
        seeds.add("rainbow-arrows", "projectile-trail", "rare", "amethyst_shard")
                .name("Rainbow arrows", "Regenbogenpfeile")
                .about("A streak through every colour of the rainbow.",
                        "Ein Streifen durch alle Farben des Regenbogens.")
                .price(2500, "coins").effect("point", "dust").colors(RAINBOW).set("count", 2).set("spread", 0.03);
        seeds.add("soul-arrows", "projectile-trail", "rare", "soul_campfire")
                .name("Soul arrows", "Seelenpfeile")
                .about("Blue soul fire marks the path of every shot.",
                        "Blaues Seelenfeuer zeichnet die Bahn jedes Schusses.")
                .price(20, "gems").effect("point", "soul_fire_flame").set("count", 2).set("spread", 0.03)
                .set("speed", 0.01);
        seeds.add("cherry-arrows", "projectile-trail", "epic", "cherry_leaves")
                .name("Cherry arrows", "Kirschblütenpfeile")
                .about("Pink petals flutter after every shot.", "Rosa Blüten flattern jedem Schuss hinterher.")
                .price(3500, "coins").effect("point", "cherry_leaves").set("count", 2).set("spread", 0.15);
        seeds.add("totem-arrows", "projectile-trail", "epic", "totem_of_undying")
                .name("Totem sparkle", "Totemfunkeln")
                .about("Green and gold sparks of a saved life.", "Grüne und goldene Funken eines geretteten Lebens.")
                .price(30, "gems").effect("point", "totem_of_undying").set("count", 3).set("spread", 0.05)
                .set("speed", 0.1);
        seeds.add("comet-arrows", "projectile-trail", "legendary", "end_rod")
                .name("Comet tail", "Kometenschweif")
                .about("A ring of starlight spins around your shots.",
                        "Ein Ring aus Sternenlicht dreht sich um deine Schüsse.")
                .price(60, "gems").effect("ring", "end_rod").set("radius", 0.25).set("height", -0.1)
                .set("points", 6).set("spin", 0.4);
        seeds.add("rainbow-ring", "kill-effect", "common", "lime_dye")
                .name("Rainbow ring", "Regenbogenring")
                .about("A ring of colour rolls out from your opponent.", "Ein Farbring rollt von deinem Gegner aus.")
                .price(800, "coins").effect("wave", "dust").colors(RAINBOW).set("size", 1.3).set("radius", 1.8)
                .set("duration", 18).set("sound", "block.amethyst_block.chime");
        seeds.add("explosion-kill", "kill-effect", "uncommon", "gunpowder")
                .name("Kaboom", "Kawumm")
                .about("A loud bang without a scratch on the map.", "Ein lauter Knall ohne einen Kratzer an der Map.")
                .price(1000, "coins").effect("point", "explosion").set("count", 3).set("spread", 0.4)
                .set("height", 0.8).set("sound", "entity.generic.explode");
        seeds.add("blood-burst", "kill-effect", "uncommon", "redstone_block")
                .name("Blood burst", "Blutspritzer")
                .about("A splash of red where your opponent fell.", "Ein roter Spritzer, wo dein Gegner fiel.")
                .price(1200, "coins").effect("burst", "block").set("variant", "redstone_block").set("radius", 0.6)
                .set("points", 24).set("count", 3).set("spread", 0.1).set("sound", "entity.slime.squish");
        seeds.add("soul-rise", "kill-effect", "uncommon", "soul_sand")
                .name("Soul rise", "Seelenflug")
                .about("The soul of your opponent spirals up into the sky.",
                        "Die Seele deines Gegners steigt in Spiralen zum Himmel.")
                .price(1500, "coins").effect("pillar", "soul").set("radius", 0.35).set("duration", 30)
                .set("sound", "particle.soul_escape");
        seeds.add("ice-shatter", "kill-effect", "rare", "packed_ice")
                .name("Ice shatter", "Eissplitter")
                .about("Your opponent shatters like thin ice.", "Dein Gegner zerspringt wie dünnes Eis.")
                .price(2200, "coins").effect("burst", "block").set("variant", "packed_ice").set("radius", 0.8)
                .set("points", 30).set("count", 2).set("spread", 0.1).set("speed", 0.1)
                .set("sound", "block.glass.break");
        seeds.add("flame-pillar", "kill-effect", "rare", "blaze_rod")
                .name("Flame pillar", "Flammensäule")
                .about("A spiral of fire rises and nothing catches fire.",
                        "Eine Feuerspirale steigt auf und nichts fängt Feuer.")
                .price(2500, "coins").effect("pillar", "flame").set("radius", 0.5).set("speed", 0.01)
                .set("duration", 30).set("sound", "item.firecharge.use");
        seeds.add("ghost-rise", "kill-effect", "rare", "phantom_membrane")
                .name("Ghost", "Geist")
                .about("A little ghost leaves the body and floats away.",
                        "Ein kleiner Geist verlässt den Körper und schwebt davon.")
                .price(2800, "coins").effect("ghost", "dust").colors(List.of("#F8FAFC", "#1E293B"))
                .set("size", 0.8).set("duration", 50).set("sound", "entity.vex.ambient");
        seeds.add("cookie-fountain", "kill-effect", "rare", "cookie")
                .name("Cookie fountain", "Keksfontäne")
                .about("Cookies rain down and nobody can pick them up.",
                        "Kekse regnen herab und niemand kann sie aufheben.")
                .price(3000, "coins").effect("point", "happy_villager").set("kind", "fountain")
                .set("variant", "cookie").set("points", 20).set("spread", 0.3).set("duration", 50)
                .set("sound", "entity.player.burp");
        seeds.add("lightning-kill", "kill-effect", "rare", "lightning_rod")
                .name("Lightning strike", "Blitzschlag")
                .about("A bolt from the sky, only for show.", "Ein Blitz vom Himmel, nur zur Show.")
                .price(25, "gems").effect("burst", "electric_spark").set("kind", "lightning").set("radius", 0.7)
                .set("count", 2).set("spread", 0.1).set("duration", 12);
        seeds.add("tombstone", "kill-effect", "epic", "chiseled_stone_bricks")
                .name("Tombstone", "Grabstein")
                .about("A gravestone rises for a moment and sinks back.",
                        "Ein Grabstein erhebt sich kurz und versinkt wieder.")
                .price(4000, "coins").effect("ring", "soul").set("kind", "tombstone")
                .set("variant", "chiseled_stone_bricks").set("radius", 0.6).set("points", 8).set("duration", 70)
                .set("sound", "block.bell.use");
        seeds.add("firework-star", "kill-effect", "epic", "firework_star")
                .name("Star burst", "Sternenregen")
                .about("A golden firework star where your opponent fell.",
                        "Ein goldener Feuerwerksstern, wo dein Gegner fiel.")
                .price(30, "gems").effect("none", "firework").set("kind", "firework").set("variant", "star")
                .colors(List.of("#FBBF24", "#FFFFFF")).set("duration", 6);
        seeds.add("squid-launch", "kill-effect", "epic", "ink_sac")
                .name("Squid launch", "Tintenfisch-Rakete")
                .about("A squid rockets into the sky and pops into ink.",
                        "Ein Tintenfisch schießt in den Himmel und platzt zu Tinte.")
                .price(35, "gems").effect("burst", "squid_ink").set("kind", "squid").set("radius", 0.8)
                .set("points", 30).set("count", 2).set("spread", 0.1).set("duration", 30)
                .set("sound", "entity.firework_rocket.launch");
        seeds.add("gold-fountain", "kill-effect", "legendary", "gold_ingot")
                .name("Gold fountain", "Goldfontäne")
                .about("A fountain of gold bars, only to look at.", "Eine Fontäne aus Goldbarren, nur zum Anschauen.")
                .price(60, "gems").effect("point", "dust").colors(List.of("#FBBF24")).set("kind", "fountain")
                .set("variant", "gold_ingot").set("size", 0.8).set("count", 3).set("spread", 0.3)
                .set("points", 24).set("duration", 60).set("sound", "entity.player.levelup");
        seeds.add("firework-creeper", "kill-effect", "legendary", "creeper_head")
                .name("Creeper firework", "Creeper-Feuerwerk")
                .about("A green creeper face lights up the sky.", "Ein grünes Creeper-Gesicht erleuchtet den Himmel.")
                .price(50, "gems").effect("none", "firework").set("kind", "firework").set("variant", "creeper")
                .colors(List.of("#4ADE80", "#14532D")).set("duration", 6);
        seeds.add("winner-halo", "win-effect", "uncommon", "sunflower")
                .name("Winner's halo", "Siegerglanz")
                .about("A golden halo stays with you after the win.",
                        "Ein goldener Heiligenschein bleibt nach dem Sieg bei dir.")
                .price(2500, "coins").effect("halo", "dust").colors(List.of("#FDE68A", "#FBBF24")).set("size", 0.9)
                .set("radius", 0.4).set("points", 14).set("duration", 100).set("sound", "block.beacon.activate");
        seeds.add("confetti-rain", "win-effect", "rare", "paper")
                .name("Confetti rain", "Konfettiregen")
                .about("Colourful confetti falls all around you.", "Buntes Konfetti fällt rund um dich herab.")
                .price(4500, "coins").effect("rain", "dust").colors(RAINBOW).set("size", 1.2).set("radius", 3.0)
                .set("points", 45).set("duration", 80).set("sound", "entity.player.levelup");
        seeds.add("totem-pop", "win-effect", "rare", "totem_of_undying")
                .name("Totem pop", "Totem-Moment")
                .about("The totem animation, as if you had just been saved.",
                        "Die Totem-Animation, als wärst du gerade gerettet worden.")
                .price(30, "gems").effect("burst", "totem_of_undying").set("kind", "totem").set("radius", 1.0)
                .set("points", 30).set("count", 2).set("spread", 0.2).set("speed", 0.3).set("duration", 20);
        seeds.add("rainbow-rings", "win-effect", "epic", "magenta_glazed_terracotta")
                .name("Rainbow rings", "Regenbogenringe")
                .about("Rings in every colour rise around you.", "Ringe in allen Farben steigen um dich auf.")
                .price(6000, "coins").effect("rings", "dust").colors(RAINBOW).set("size", 1.2).set("radius", 1.0)
                .set("points", 18).set("duration", 80).set("sound", "block.note_block.chime");
        seeds.add("dragon-breath", "win-effect", "epic", "dragon_breath")
                .name("Dragon breath", "Drachenatem")
                .about("A purple swirl of dragon breath spreads around you.",
                        "Ein violetter Wirbel aus Drachenatem breitet sich um dich aus.")
                .price(50, "gems").effect("swirl", "dragon_breath").set("radius", 2.4).set("points", 36)
                .set("duration", 80).set("sound", "entity.ender_dragon.growl");
        seeds.add("sheep-disco", "win-effect", "epic", "pink_wool")
                .name("Sheep disco", "Schafsdisco")
                .about("Colourful sheep hop around you to the beat.", "Bunte Schafe hüpfen im Takt um dich herum.")
                .price(60, "gems").effect("none", "note").set("kind", "sheep").set("radius", 2.4).set("points", 6)
                .set("duration", 100);
        seeds.add("fireworks-show", "win-effect", "legendary", "firework_rocket")
                .name("Fireworks show", "Feuerwerksshow")
                .about("Ten rockets light up the sky above you.", "Zehn Raketen erleuchten den Himmel über dir.")
                .price(100, "gems").effect("none", "firework").set("kind", "fireworks").colors(RAINBOW)
                .set("radius", 3.0).set("points", 10).set("duration", 80);
        seeds.add("meteor-shower", "win-effect", "legendary", "magma_block")
                .name("Meteor shower", "Meteoritenschauer")
                .about("Glowing meteors fall from the sky around you.",
                        "Glühende Meteore fallen rund um dich vom Himmel.")
                .price(90, "gems").effect("meteor", "dust").colors(List.of("#FDBA74", "#DC2626")).set("size", 1.6)
                .set("radius", 3.5).set("points", 6).set("duration", 80).set("sound", "entity.blaze.shoot");
        seeds.add("lightning-storm", "win-effect", "legendary", "trident")
                .name("Lightning storm", "Gewittersturm")
                .about("Bolts strike all around you, only for show.", "Rund um dich schlagen Blitze ein, nur zur Show.")
                .price(80, "gems").effect("none", "electric_spark").set("kind", "lightning-storm")
                .set("radius", 5.0).set("points", 6).set("count", 2).set("spread", 0.1).set("duration", 70);
    }

    private static final class Seeds {

        private final ConfigurationNode cosmetics;
        private final int after;
        private int since;

        private Seeds(ConfigurationNode cosmetics, int after) {
            this.cosmetics = cosmetics;
            this.after = after;
        }

        private void since(int version) {
            since = version;
        }

        private Seed add(String id, String category, String rarity, String icon) throws SerializationException {
            ConfigurationNode target = cosmetics.node(id);
            ConfigurationNode node = since > after && target.virtual() ? target : CommentedConfigurationNode.root();
            node.node("category").set(category);
            return new Seed(node).rarity(rarity, icon);
        }
    }

    private static final class Seed {

        private final ConfigurationNode node;
        private ConfigurationNode effect;

        private Seed(ConfigurationNode node) {
            this.node = node;
        }

        private Seed rarity(String rarity, String icon) throws SerializationException {
            node.node("rarity").set(rarity);
            if (!icon.isEmpty()) {
                node.node("icon").set(icon);
            }
            return this;
        }

        private Seed name(String english, String german) throws SerializationException {
            node.node("name", "en").set(english);
            node.node("name", "de").set(german);
            return this;
        }

        private Seed about(String english, String german) throws SerializationException {
            node.node("description", "en").set(english);
            node.node("description", "de").set(german);
            return this;
        }

        private Seed price(long amount, String currency) throws SerializationException {
            node.node("price").set(amount);
            node.node("currency").set(currency);
            return this;
        }

        private Seed permission(String permission) throws SerializationException {
            node.node("permission").set(permission);
            return this;
        }

        private Seed effect(String shape, String particle) throws SerializationException {
            effect = node.node("effect");
            effect.node("particle").set(particle);
            effect.node("shape").set(shape);
            return this;
        }

        private Seed colors(List<String> colors) throws SerializationException {
            effect.node("colors").setList(String.class, colors);
            return this;
        }

        private Seed set(String key, Object value) throws SerializationException {
            (effect != null && !"item".equals(key) && !"texture".equals(key) ? effect : node).node(key).set(value);
            return this;
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

    private static CommentedConfigurationNode filled() {
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        try {
            fillDefaults(root);
        } catch (SerializationException e) {
            throw new IllegalStateException(e);
        }
        return root;
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
