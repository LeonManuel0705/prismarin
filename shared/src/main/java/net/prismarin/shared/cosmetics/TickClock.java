package net.prismarin.shared.cosmetics;

@FunctionalInterface
public interface TickClock {

    Runnable start(Runnable everyTick);
}
