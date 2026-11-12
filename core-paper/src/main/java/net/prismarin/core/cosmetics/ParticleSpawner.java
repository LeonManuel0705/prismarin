package net.prismarin.core.cosmetics;

import com.destroystokyo.paper.ParticleBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class ParticleSpawner {

    private static final int WHITE = 0xFFFFFF;
    private static final double FORCE_DISTANCE = 32.0;
    private static final float VOLUME = 0.8f;

    private final Map<String, Optional<Particle>> particles = new ConcurrentHashMap<>();
    private final Map<String, Optional<Key>> sounds = new ConcurrentHashMap<>();
    private final Map<String, Optional<BlockData>> blocks = new ConcurrentHashMap<>();
    private final Map<String, Optional<ItemStack>> items = new ConcurrentHashMap<>();

    Optional<Particle> particle(String key) {
        return particles.computeIfAbsent(key, ParticleSpawner::lookup);
    }

    Optional<String> problem(CosmeticParticles look) {
        Optional<Particle> particle = particle(look.particle());
        if (particle.isEmpty()) {
            return Optional.of("'" + look.particle() + "' is no particle of this server version");
        }
        Class<?> type = particle.get().getDataType();
        if (type == BlockData.class && block(look.variant()).isEmpty()) {
            return Optional.of("the particle " + look.particle() + " needs variant: a block such as redstone_block");
        }
        if (type == ItemStack.class && item(look.variant()).isEmpty()) {
            return Optional.of("the particle " + look.particle() + " needs variant: an item such as cookie");
        }
        if (!supported(type)) {
            return Optional.of("the particle " + look.particle() + " needs data PRISMARIN cannot give, pick "
                    + "another one");
        }
        if (!look.sound().isEmpty() && soundKey(look.sound()).isEmpty()) {
            return Optional.of("'" + look.sound() + "' is no sound key");
        }
        return Optional.empty();
    }

    void spawn(World world, double x, double y, double z, CosmeticParticles look, List<CosmeticPoint> points,
               List<Player> receivers, double range) {
        Optional<Particle> particle = particle(look.particle());
        if (particle.isEmpty() || receivers.isEmpty() || points.isEmpty()) {
            return;
        }
        Class<?> type = particle.get().getDataType();
        if (!supported(type)) {
            return;
        }
        Object shared = type == BlockData.class ? block(look.variant()).orElse(null)
                : type == ItemStack.class ? item(look.variant()).orElse(null) : null;
        if ((type == BlockData.class || type == ItemStack.class) && shared == null) {
            return;
        }
        ParticleBuilder builder = new ParticleBuilder(particle.get())
                .count(look.count())
                .offset(look.spread(), look.spread(), look.spread())
                .extra(look.speed())
                .force(range > FORCE_DISTANCE)
                .receivers(receivers);
        for (int index = 0; index < points.size(); index++) {
            CosmeticPoint point = points.get(index);
            Object data = shared != null ? shared
                    : data(type, look, point.color() >= 0 ? point.color() : look.color(index));
            builder.location(world, x + point.x(), y + point.y(), z + point.z()).data(data).spawn();
        }
    }

    void sound(World world, double x, double y, double z, String key, List<Player> receivers) {
        sound(world, x, y, z, key, VOLUME, 1.0f, receivers);
    }

    void sound(World world, double x, double y, double z, String key, float volume, float pitch,
               List<Player> receivers) {
        if (key == null || key.isEmpty() || receivers.isEmpty()) {
            return;
        }
        soundKey(key).ifPresent(found -> {
            Sound sound = Sound.sound(found, Sound.Source.PLAYER, Math.clamp(volume, 0.0f, 4.0f),
                    Math.clamp(pitch, 0.5f, 2.0f));
            receivers.forEach(receiver -> receiver.playSound(sound, x, y, z));
        });
    }

    Optional<BlockData> block(String variant) {
        if (variant == null || variant.isEmpty()) {
            return Optional.empty();
        }
        return blocks.computeIfAbsent(variant, value -> {
            Material material = Material.matchMaterial(value);
            return material != null && material.isBlock() && !material.isAir()
                    ? Optional.of(material.createBlockData()) : Optional.empty();
        });
    }

    Optional<ItemStack> item(String variant) {
        if (variant == null || variant.isEmpty()) {
            return Optional.empty();
        }
        return items.computeIfAbsent(variant, value -> {
            Material material = Material.matchMaterial(value);
            return material != null && material.isItem() && !material.isAir()
                    ? Optional.of(new ItemStack(material)) : Optional.empty();
        }).map(ItemStack::clone);
    }

    private Optional<Key> soundKey(String key) {
        return sounds.computeIfAbsent(key, value -> {
            try {
                return Optional.of(Key.key(value));
            } catch (InvalidKeyException e) {
                return Optional.empty();
            }
        });
    }

    private static Object data(Class<?> type, CosmeticParticles look, int color) {
        int rgb = color < 0 ? WHITE : color;
        if (type == Particle.DustTransition.class) {
            int to = look.colors().size() > 1 ? look.color(look.colors().indexOf(rgb) + 1) : rgb;
            return new Particle.DustTransition(Color.fromRGB(rgb), Color.fromRGB(to < 0 ? rgb : to), look.size());
        }
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(Color.fromRGB(rgb), look.size());
        }
        if (type == Color.class) {
            return Color.fromRGB(rgb);
        }
        if (type == Float.class) {
            return 1.0f;
        }
        if (type == Integer.class) {
            return 0;
        }
        return null;
    }

    private static boolean supported(Class<?> type) {
        return type == Void.class || type == Particle.DustOptions.class || type == Particle.DustTransition.class
                || type == Color.class || type == Float.class || type == Integer.class || type == BlockData.class
                || type == ItemStack.class;
    }

    private static Optional<Particle> lookup(String key) {
        for (Particle particle : Particle.values()) {
            if (particle.getKey().getKey().equals(key) || particle.name().equalsIgnoreCase(key)) {
                return Optional.of(particle);
            }
        }
        return Optional.empty();
    }
}
