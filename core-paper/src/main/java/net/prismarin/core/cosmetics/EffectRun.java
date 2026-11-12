package net.prismarin.core.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticEffectFrame;
import net.prismarin.api.cosmetics.CosmeticEffectKind;
import net.prismarin.api.cosmetics.CosmeticFrame;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import net.prismarin.api.cosmetics.CosmeticSpot;
import net.prismarin.shared.cosmetics.EffectScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class EffectRun implements EffectScheduler.Run, CosmeticEffectFrame {

    private final PaperEffects effects;
    private final long id;
    private final UUID player;
    private final Cosmetic cosmetic;
    private final CosmeticParticles look;
    private final String kindId;
    private final CosmeticEffectKind kind;
    private final World world;
    private final boolean follow;
    private final boolean preview;
    private final List<Player> viewers;
    private final List<Player> shownTo;
    private final int duration;
    private final List<Entity> entities = new ArrayList<>();
    private final SplittableRandom random;
    private Location anchor;
    private int tick;

    EffectRun(PaperEffects effects, long id, UUID player, Cosmetic cosmetic, CosmeticParticles look, String kindId,
              CosmeticEffectKind kind, Location anchor, boolean follow, boolean preview, List<Player> viewers,
              int duration) {
        this.effects = effects;
        this.id = id;
        this.player = player;
        this.cosmetic = cosmetic;
        this.look = look;
        this.kindId = kindId;
        this.kind = kind;
        this.world = anchor.getWorld();
        this.anchor = anchor.clone();
        this.follow = follow;
        this.preview = preview;
        this.viewers = new ArrayList<>(viewers);
        this.shownTo = List.copyOf(viewers);
        this.duration = duration;
        this.random = new SplittableRandom(id * 31L + player.getLeastSignificantBits());
    }

    @Override
    public int duration() {
        return duration;
    }

    @Override
    public boolean frame(int now) {
        tick = now;
        if (effects.server().getWorld(world.getUID()) == null) {
            return false;
        }
        if (follow) {
            Player wearer = effects.server().getPlayer(player);
            if (wearer == null || !wearer.getWorld().equals(world)) {
                return false;
            }
            anchor = wearer.getLocation();
        }
        viewers.removeIf(viewer -> !viewer.isOnline() || !viewer.getWorld().equals(world));
        if (viewers.isEmpty()) {
            return false;
        }
        if (now == 0 && !look.sound().isEmpty()) {
            soundAt(anchor, look.sound(), 0.8f, 1.0f);
        }
        try {
            kind.frame(this);
        } catch (RuntimeException | LinkageError e) {
            effects.failed(kindId, e);
            return false;
        }
        return true;
    }

    @Override
    public void end() {
        try {
            kind.end(this);
        } catch (RuntimeException | LinkageError e) {
            effects.failed(kindId, e);
        }
        for (Entity entity : List.copyOf(entities)) {
            effects.entities().remove(entity, shownTo);
        }
        entities.clear();
    }

    long id() {
        return id;
    }

    CosmeticEffectKind kind() {
        return kind;
    }

    boolean owner(UUID uuid) {
        return player.equals(uuid);
    }

    boolean previewing(UUID viewer) {
        return preview && player.equals(viewer);
    }

    World world() {
        return world;
    }

    Location anchor() {
        return anchor.clone();
    }

    Player wearer() {
        return effects.server().getPlayer(player);
    }

    List<Player> viewerPlayers() {
        return List.copyOf(viewers);
    }

    SplittableRandom random() {
        return random;
    }

    <T extends Entity> List<T> entities(Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Entity entity : entities) {
            if (type.isInstance(entity) && entity.isValid()) {
                found.add(type.cast(entity));
            }
        }
        return found;
    }

    <T extends Entity> T spawn(Class<T> type, Location at, Consumer<? super T> setup) {
        T entity = effects.entities().spawn(world, at, type, id, viewers, setup);
        if (entity != null) {
            entities.add(entity);
        }
        return entity;
    }

    Item drop(Location at, ItemStack stack, Consumer<Item> setup) {
        Item item = effects.entities().drop(world, at, stack, id, viewers, setup);
        if (item != null) {
            entities.add(item);
        }
        return item;
    }

    boolean lightning(Location at) {
        LightningStrike strike = effects.entities().lightning(world, at, id, viewers);
        if (strike == null) {
            return false;
        }
        entities.add(strike);
        return true;
    }

    void release(Entity entity) {
        entities.remove(entity);
        effects.entities().remove(entity, shownTo);
    }

    boolean drawShapeAt(Location at) {
        return drawAt(at, look, shape());
    }

    boolean drawAt(Location at, CosmeticParticles with, List<CosmeticPoint> points) {
        if (points.isEmpty() || viewers.isEmpty()) {
            return true;
        }
        if (!effects.budget().take(points.size() * viewers.size())) {
            return false;
        }
        effects.spawner().spawn(world, at.getX(), at.getY(), at.getZ(), with, points, viewers, effects.range());
        return true;
    }

    void soundAt(Location at, String key, float volume, float pitch) {
        effects.spawner().sound(world, at.getX(), at.getY(), at.getZ(), key, volume, pitch, viewers);
    }

    List<CosmeticPoint> shape() {
        return effects.points(new CosmeticFrame(player, tick, anchor.getYaw(), false, look));
    }

    @Override
    public UUID player() {
        return player;
    }

    @Override
    public Cosmetic cosmetic() {
        return cosmetic;
    }

    @Override
    public CosmeticParticles look() {
        return look;
    }

    @Override
    public CosmeticSpot spot() {
        return CosmeticSpot.of(world.getName(), anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getYaw());
    }

    @Override
    public int tick() {
        return tick;
    }

    @Override
    public boolean preview() {
        return preview;
    }

    @Override
    public List<UUID> viewers() {
        return viewers.stream().map(Player::getUniqueId).toList();
    }

    @Override
    public boolean drawShape() {
        return drawShapeAt(anchor);
    }

    @Override
    public boolean draw(List<CosmeticPoint> points) {
        return draw(look, points);
    }

    @Override
    public boolean draw(CosmeticParticles with, List<CosmeticPoint> points) {
        if (with == null || points == null) {
            return false;
        }
        List<CosmeticPoint> limited = points.size() <= CosmeticParticles.MAX_POINTS ? points
                : points.subList(0, CosmeticParticles.MAX_POINTS);
        return drawAt(anchor, with, limited);
    }

    @Override
    public void sound(String key, float volume, float pitch) {
        soundAt(anchor, key, volume, pitch);
    }

    @Override
    public boolean adopt(UUID entity) {
        Entity found = entity == null ? null : effects.server().getEntity(entity);
        if (found == null || found instanceof Player || !found.getWorld().equals(world)) {
            return false;
        }
        effects.entities().adopt(found, id, viewers);
        if (!entities.contains(found)) {
            entities.add(found);
        }
        return true;
    }
}
