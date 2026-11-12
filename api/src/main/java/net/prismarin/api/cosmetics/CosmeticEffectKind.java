package net.prismarin.api.cosmetics;

@FunctionalInterface
public interface CosmeticEffectKind {

    void frame(CosmeticEffectFrame frame);

    default void end(CosmeticEffectFrame frame) {
    }
}
