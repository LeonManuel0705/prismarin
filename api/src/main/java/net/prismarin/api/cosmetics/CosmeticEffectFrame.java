package net.prismarin.api.cosmetics;

import java.util.List;
import java.util.UUID;

public interface CosmeticEffectFrame {

    UUID player();

    Cosmetic cosmetic();

    CosmeticParticles look();

    CosmeticSpot spot();

    int tick();

    int duration();

    boolean preview();

    List<UUID> viewers();

    boolean drawShape();

    boolean draw(List<CosmeticPoint> points);

    boolean draw(CosmeticParticles look, List<CosmeticPoint> points);

    void sound(String key, float volume, float pitch);

    boolean adopt(UUID entity);

    default boolean first() {
        return tick() == 0;
    }

    default boolean last() {
        return tick() >= duration();
    }
}
