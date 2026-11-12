package net.prismarin.core.cosmetics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import net.prismarin.api.cosmetics.CosmeticEffectFrame;
import net.prismarin.api.cosmetics.CosmeticEffectKind;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import net.prismarin.shared.cosmetics.CosmeticShapes;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.EntityEffect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Squid;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

final class BuiltInEffects {

    static final String PARTICLES = CosmeticParticles.PARTICLES;
    static final String LIGHTNING = "lightning";
    static final String LIGHTNING_STORM = "lightning-storm";
    static final String SQUID = "squid";
    static final String FOUNTAIN = "fountain";
    static final String TOMBSTONE = "tombstone";
    static final String FIREWORK = "firework";
    static final String FIREWORKS = "fireworks";
    static final String TOTEM = "totem";
    static final String SHEEP = "sheep";

    static final List<String> NAMES = List.of(PARTICLES, LIGHTNING, LIGHTNING_STORM, SQUID, FOUNTAIN, TOMBSTONE,
            FIREWORK, FIREWORKS, TOTEM, SHEEP);

    private static final Map<String, Integer> MINIMUM = Map.of(LIGHTNING, 10, LIGHTNING_STORM, 40, SQUID, 20,
            FOUNTAIN, 30, TOMBSTONE, 40, FIREWORK, 4, FIREWORKS, 40, SHEEP, 40);
    private static final double LIFT = 0.05;
    private static final List<Integer> RAINBOW = List.of(0xF87171, 0xFB923C, 0xFACC15, 0x4ADE80, 0x22D3EE, 0xA78BFA);
    private static final List<FireworkEffect.Type> SHOW_TYPES = List.of(FireworkEffect.Type.BALL,
            FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR, FireworkEffect.Type.BURST);
    private static final float[] MELODY = {1.0f, 1.26f, 1.5f, 2.0f, 1.5f, 1.26f, 1.0f, 0.75f};
    private static final CosmeticParticles CLOUD = CosmeticParticles.of(CosmeticShapes.POINT, "cloud").withCount(2)
            .withSpread(0.1);
    private static final CosmeticParticles POOF = CosmeticParticles.of(CosmeticShapes.POINT, "poof").withCount(8)
            .withSpread(0.3).withSpeed(0.02);
    private static final List<CosmeticPoint> HERE = List.of(CosmeticPoint.of(0.0, 0.0, 0.0));
    private static final EntityEffect TOTEM_EFFECT = entityEffect("PROTECTED_FROM_DEATH", "TOTEM_RESURRECT");

    private BuiltInEffects() {
        throw new AssertionError();
    }

    static Map<String, CosmeticEffectKind> kinds(ParticleSpawner spawner) {
        Map<String, CosmeticEffectKind> kinds = new LinkedHashMap<>();
        kinds.put(PARTICLES, BuiltInEffects::particles);
        kinds.put(LIGHTNING, built(BuiltInEffects::lightning));
        kinds.put(LIGHTNING_STORM, built(BuiltInEffects::storm));
        kinds.put(SQUID, built(BuiltInEffects::squid));
        kinds.put(FOUNTAIN, built(run -> fountain(run, spawner)));
        kinds.put(TOMBSTONE, built(run -> tombstone(run, spawner)));
        kinds.put(FIREWORK, built(BuiltInEffects::firework));
        kinds.put(FIREWORKS, built(BuiltInEffects::fireworks));
        kinds.put(TOTEM, built(BuiltInEffects::totem));
        kinds.put(SHEEP, built(BuiltInEffects::sheep));
        return Map.copyOf(kinds);
    }

    static int duration(String kind, int configured) {
        return Math.max(configured, MINIMUM.getOrDefault(kind, 0));
    }

    static void particles(CosmeticEffectFrame frame) {
        if (frame.duration() == 0 || frame.tick() % 2 == 0) {
            frame.drawShape();
        }
    }

    private static CosmeticEffectKind built(Consumer<EffectRun> frame) {
        return effect -> {
            if (effect instanceof EffectRun run) {
                frame.accept(run);
            }
        };
    }

    private static void lightning(EffectRun run) {
        if (run.first()) {
            Location at = run.anchor().add(0.0, LIFT, 0.0);
            if (!run.lightning(at)) {
                fallbackBolt(run, at);
            }
        }
        if (run.tick() % 2 == 0 && run.tick() <= 8) {
            run.drawShape();
        }
    }

    private static void storm(EffectRun run) {
        CosmeticParticles look = run.look();
        int bolts = look.points() > 0 ? look.points() : 6;
        double radius = look.radius() > 0.0 ? look.radius() : 5.0;
        int span = Math.max(1, run.duration() - 10);
        for (int bolt = 0; bolt < bolts; bolt++) {
            if (bolt * span / bolts != run.tick()) {
                continue;
            }
            SplittableRandom random = run.random();
            double angle = random.nextDouble() * Math.PI * 2.0;
            double reach = 1.5 + random.nextDouble() * Math.max(0.5, radius - 1.5);
            Location around = run.anchor().add(Math.cos(angle) * reach, 0.0, Math.sin(angle) * reach);
            Location ground = surface(around);
            Location at = ground != null ? ground : around.add(0.0, LIFT, 0.0);
            if (ground == null || !run.lightning(at)) {
                fallbackBolt(run, at);
            }
            run.drawAt(at, look, CosmeticShapes.burst(0.6, 0.3, 12, 0.0, look));
        }
        if (!CosmeticShapes.NONE.equals(look.shape()) && run.tick() % 2 == 0) {
            run.drawShape();
        }
    }

    private static void squid(EffectRun run) {
        if (run.first()) {
            run.spawn(Squid.class, run.anchor().add(0.0, 0.3, 0.0), squid -> squid.setGravity(false));
            return;
        }
        List<Squid> squids = run.entities(Squid.class);
        if (squids.isEmpty()) {
            return;
        }
        Squid squid = squids.getFirst();
        Location at = squid.getLocation();
        if (run.last()) {
            run.drawShapeAt(at);
            run.soundAt(at, "entity.squid.squirt", 1.0f, 1.0f);
            run.release(squid);
            return;
        }
        squid.setVelocity(new Vector(0.0, 0.5, 0.0));
        squid.setRotation(at.getYaw() + 35.0f, 0.0f);
        if (run.tick() % 2 == 0) {
            run.drawAt(at, CLOUD, HERE);
        }
    }

    private static void fountain(EffectRun run, ParticleSpawner spawner) {
        CosmeticParticles look = run.look();
        int total = look.points() > 0 ? look.points() : 20;
        int spawning = Math.max(1, run.duration() * 3 / 5);
        int before = (int) ((long) total * run.tick() / spawning);
        int after = (int) Math.min(total, (long) total * (run.tick() + 1) / spawning);
        Location mouth = run.anchor().add(0.0, 0.6, 0.0);
        ItemStack stack = spawner.item(look.variant()).orElseGet(() -> new ItemStack(Material.COOKIE));
        SplittableRandom random = run.random();
        for (int item = before; item < after && run.tick() < spawning; item++) {
            Vector push = new Vector((random.nextDouble() - 0.5) * 0.3, 0.42 + random.nextDouble() * 0.2,
                    (random.nextDouble() - 0.5) * 0.3);
            run.drop(mouth, stack.clone(), dropped -> dropped.setVelocity(push));
        }
        if (run.tick() % 2 == 0 && run.tick() < spawning) {
            run.drawShapeAt(mouth);
        }
    }

    private static void tombstone(EffectRun run, ParticleSpawner spawner) {
        if (run.first()) {
            Location base = run.anchor();
            base.setPitch(0.0f);
            BlockData stone = spawner.block(run.look().variant())
                    .orElseGet(Material.CHISELED_STONE_BRICKS::createBlockData);
            run.spawn(BlockDisplay.class, base, display -> {
                display.setBlock(stone);
                display.setTransformation(shape(-0.35f, -0.1f, 0.7f, 0.001f, 0.2f));
            });
            run.spawn(BlockDisplay.class, base, display -> {
                display.setBlock(Material.COARSE_DIRT.createBlockData());
                display.setTransformation(shape(-0.4f, 0.1f, 0.8f, 0.001f, 1.0f));
            });
            return;
        }
        List<BlockDisplay> parts = run.entities(BlockDisplay.class);
        if (run.tick() == 2 && parts.size() == 2) {
            grow(parts.get(0), shape(-0.35f, -0.1f, 0.7f, 0.95f, 0.2f));
            grow(parts.get(1), shape(-0.4f, 0.1f, 0.8f, 0.12f, 1.0f));
        }
        if (run.tick() == run.duration() - 12 && parts.size() == 2) {
            grow(parts.get(0), shape(-0.35f, -0.1f, 0.7f, 0.001f, 0.2f));
            grow(parts.get(1), shape(-0.4f, 0.1f, 0.8f, 0.001f, 1.0f));
        }
        if (run.tick() % 4 == 0) {
            run.drawShape();
        }
    }

    private static void firework(EffectRun run) {
        if (!run.first()) {
            return;
        }
        CosmeticParticles look = run.look();
        FireworkEffect.Type type = fireworkType(look.variant(), FireworkEffect.Type.BALL_LARGE);
        List<Color> colors = colors(look, run.random(), Math.max(1, look.colors().size()));
        int rockets = Math.clamp(look.points(), 1, 5);
        for (int index = 0; index < rockets; index++) {
            Location at = run.anchor().add(index == 0 ? 0.0 : (run.random().nextDouble() - 0.5) * 1.6, 1.2,
                    index == 0 ? 0.0 : (run.random().nextDouble() - 0.5) * 1.6);
            Firework rocket = run.spawn(Firework.class, at, spawned -> spawned.setFireworkMeta(meta(spawned, type,
                    colors, type == FireworkEffect.Type.STAR, true, 0)));
            if (rocket != null) {
                rocket.detonate();
            }
        }
        if (!CosmeticShapes.NONE.equals(look.shape())) {
            run.drawShape();
        }
    }

    private static void fireworks(EffectRun run) {
        CosmeticParticles look = run.look();
        int rockets = look.points() > 0 ? look.points() : 8;
        double radius = look.radius() > 0.0 ? look.radius() : 3.0;
        int span = Math.max(1, run.duration() - 25);
        SplittableRandom random = run.random();
        for (int rocket = 0; rocket < rockets; rocket++) {
            if (rocket * span / rockets != run.tick()) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2.0;
            double reach = radius * Math.sqrt(random.nextDouble());
            Location at = run.anchor().add(Math.cos(angle) * reach, 0.2, Math.sin(angle) * reach);
            FireworkEffect.Type type = SHOW_TYPES.get(random.nextInt(SHOW_TYPES.size()));
            List<Color> colors = colors(look, random, 2);
            boolean flicker = random.nextBoolean();
            boolean trail = random.nextBoolean();
            int fuse = 14 + random.nextInt(10);
            Firework launched = run.spawn(Firework.class, at, spawned -> spawned.setFireworkMeta(meta(spawned, type,
                    colors, flicker, trail, 1)));
            if (launched != null) {
                launched.setTicksToDetonate(fuse);
                run.soundAt(at, "entity.firework_rocket.launch", 1.5f, 1.0f);
            }
        }
        if (!CosmeticShapes.NONE.equals(look.shape()) && run.tick() % 2 == 0) {
            run.drawShape();
        }
    }

    private static void totem(EffectRun run) {
        if (run.first()) {
            Player wearer = run.wearer();
            if (wearer != null && wearer.getWorld().equals(run.world()) && TOTEM_EFFECT != null) {
                run.viewerPlayers().forEach(viewer -> viewer.sendEntityEffect(TOTEM_EFFECT, wearer));
            } else {
                run.soundAt(run.anchor(), "item.totem.use", 0.8f, 1.0f);
            }
        }
        if (run.tick() % 2 == 0 && run.tick() <= 10) {
            run.drawShape();
        }
    }

    private static void sheep(EffectRun run) {
        CosmeticParticles look = run.look();
        int count = Math.clamp(look.points() > 0 ? look.points() : 6, 1, 16);
        double radius = look.radius() > 0.0 ? look.radius() : 2.4;
        DyeColor[] dyes = DyeColor.values();
        if (run.first()) {
            for (int index = 0; index < count; index++) {
                double angle = 2.0 * Math.PI * index / count;
                Location at = run.anchor().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
                if (!at.getBlock().isPassable() || !at.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
                    continue;
                }
                int shade = index * 3;
                boolean baby = index % 2 == 1;
                run.spawn(Sheep.class, at, sheep -> {
                    sheep.setColor(dyes[Math.floorMod(shade, dyes.length)]);
                    if (baby) {
                        sheep.setBaby();
                    }
                });
            }
            return;
        }
        List<Sheep> flock = run.entities(Sheep.class);
        if (run.last()) {
            for (Sheep sheep : flock) {
                run.drawAt(sheep.getLocation().add(0.0, 0.5, 0.0), POOF, HERE);
            }
            run.soundAt(run.anchor(), "entity.sheep.ambient", 0.8f, 1.2f);
            return;
        }
        SplittableRandom random = run.random();
        for (int index = 0; index < flock.size(); index++) {
            Sheep sheep = flock.get(index);
            if (run.tick() % 4 == 0) {
                sheep.setColor(dyes[Math.floorMod(index * 3 + run.tick() / 4, dyes.length)]);
            }
            if ((run.tick() + index * 3) % 10 == 0 && sheep.isOnGround()) {
                sheep.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.2, 0.42,
                        (random.nextDouble() - 0.5) * 0.2));
            }
        }
        if (run.tick() % 5 == 0 && !flock.isEmpty()) {
            float pitch = MELODY[Math.floorMod(run.tick() / 5, MELODY.length)];
            run.soundAt(run.anchor(), "block.note_block.bit", 0.7f, pitch);
            Sheep singer = flock.get(Math.floorMod(run.tick() / 5, flock.size()));
            run.drawAt(singer.getLocation().add(0.0, 1.3, 0.0), look, HERE);
        }
    }

    static List<CosmeticPoint> bolt(double height, int steps, SplittableRandom random) {
        List<CosmeticPoint> points = new ArrayList<>(steps + 1);
        double x = 0.0;
        double z = 0.0;
        for (int step = 0; step <= steps; step++) {
            points.add(CosmeticPoint.of(x, height * step / steps, z));
            x += (random.nextDouble() - 0.5) * 0.5;
            z += (random.nextDouble() - 0.5) * 0.5;
        }
        return points;
    }

    private static void fallbackBolt(EffectRun run, Location at) {
        run.drawAt(at, CosmeticParticles.of(CosmeticShapes.POINT, "end_rod"), bolt(7.0, 24, run.random()));
        run.soundAt(at, "entity.lightning_bolt.thunder", 0.6f, 1.2f);
    }

    private static Location surface(Location around) {
        Block start = around.getBlock();
        for (int dy = 2; dy >= -4; dy--) {
            Block block = start.getRelative(0, dy, 0);
            if (block.isEmpty() && !block.getRelative(BlockFace.DOWN).isEmpty()) {
                return block.getLocation().add(0.5, LIFT, 0.5);
            }
        }
        return null;
    }

    private static Transformation shape(float x, float z, float width, float height, float depth) {
        return new Transformation(new Vector3f(x, 0.0f, z), new AxisAngle4f(), new Vector3f(width, height, depth),
                new AxisAngle4f());
    }

    private static void grow(BlockDisplay display, Transformation to) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(10);
        display.setTransformation(to);
    }

    private static FireworkMeta meta(Firework firework, FireworkEffect.Type type, List<Color> colors,
                                     boolean flicker, boolean trail, int power) {
        FireworkMeta meta = firework.getFireworkMeta();
        meta.clearEffects();
        meta.addEffect(FireworkEffect.builder().with(type).withColor(colors).withFade(Color.WHITE).flicker(flicker)
                .trail(trail).build());
        meta.setPower(power);
        return meta;
    }

    private static List<Color> colors(CosmeticParticles look, SplittableRandom random, int count) {
        List<Integer> palette = look.colors().isEmpty() ? RAINBOW : look.colors();
        List<Color> colors = new ArrayList<>(count);
        if (count >= palette.size()) {
            palette.forEach(color -> colors.add(Color.fromRGB(color)));
            return colors;
        }
        int start = random.nextInt(palette.size());
        for (int index = 0; index < count; index++) {
            colors.add(Color.fromRGB(palette.get((start + index) % palette.size())));
        }
        return colors;
    }

    private static EntityEffect entityEffect(String... names) {
        for (String name : names) {
            try {
                return EntityEffect.valueOf(name);
            } catch (IllegalArgumentException e) {
                continue;
            }
        }
        return null;
    }

    static FireworkEffect.Type fireworkType(String variant, FireworkEffect.Type fallback) {
        if (variant == null || variant.isEmpty()) {
            return fallback;
        }
        try {
            return FireworkEffect.Type.valueOf(variant.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
