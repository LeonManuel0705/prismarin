package net.prismarin.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import net.prismarin.api.Prismarin;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticFrame;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import net.prismarin.api.cosmetics.CosmeticPurchaseEvent;
import net.prismarin.api.cosmetics.CosmeticService;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSpot;
import net.prismarin.api.cosmetics.CosmeticUnlockedEvent;
import net.prismarin.api.event.Subscription;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class Cosmetics {

    static final String STAR = "example-star";
    private static final int TIPS = 5;
    private static final int PER_EDGE = 4;

    private final Prismarin prismarin;
    private final Logger logger;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean round;

    Cosmetics(Prismarin prismarin, Logger logger) {
        this.prismarin = prismarin;
        this.logger = logger;
    }

    void register() {
        prismarin.cosmetics().ifPresent(cosmetics -> {
            subscriptions.add(cosmetics.registerShape(STAR, Cosmetics::star));
            subscriptions.add(cosmetics.register(Cosmetic.particles(STAR, CosmeticSlot.AURA, "Example star",
                            CosmeticParticles.of(STAR, "dust").withColors(0xFDE68A, 0xF472B6).withRadius(1.0))
                    .withDescription("A star drawn by the example plugin.")
                    .withRarity("epic")
                    .withIcon("nether_star")
                    .withPrice("coins", 1500)));
        });
        subscriptions.add(prismarin.events().subscribe(CosmeticPurchaseEvent.class, event -> {
            if (round) {
                event.setCancelled(true);
            }
        }));
        subscriptions.add(prismarin.events().subscribe(CosmeticUnlockedEvent.class, event ->
                logger.info(event.player() + " unlocked " + event.cosmetic().id() + " by " + event.source().id())));
    }

    void unregister() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
        endRound();
    }

    void startRound() {
        round = true;
        prismarin.cosmetics().ifPresent(cosmetics -> {
            cosmetics.enable(CosmeticSlot.TRAIL);
            cosmetics.enable(CosmeticSlot.KILL_EFFECT);
            cosmetics.enable(CosmeticSlot.WIN_EFFECT);
        });
    }

    void endRound() {
        round = false;
        prismarin.cosmetics().ifPresent(cosmetics -> {
            cosmetics.reset(CosmeticSlot.TRAIL);
            cosmetics.reset(CosmeticSlot.KILL_EFFECT);
            cosmetics.reset(CosmeticSlot.WIN_EFFECT);
        });
    }

    boolean celebrate(Player winner) {
        return prismarin.cosmetics().map(cosmetics -> cosmetics.playEffect(winner.getUniqueId(),
                CosmeticSlot.WIN_EFFECT)).orElse(false);
    }

    boolean knockedOut(Player killer, Location where) {
        Optional<CosmeticService> cosmetics = prismarin.cosmetics();
        return cosmetics.isPresent() && cosmetics.get().playEffect(killer.getUniqueId(), CosmeticSlot.KILL_EFFECT,
                CosmeticSpot.of(where.getWorld().getName(), where.getX(), where.getY(), where.getZ(),
                        where.getYaw()));
    }

    static List<CosmeticPoint> star(CosmeticFrame frame) {
        double outer = frame.look().radius() > 0.0 ? frame.look().radius() : 1.0;
        double inner = outer * 0.4;
        double turn = frame.tick() * 0.05;
        double height = 1.1 + frame.look().height();
        List<CosmeticPoint> points = new ArrayList<>();
        for (int corner = 0; corner < TIPS * 2; corner++) {
            double fromAngle = turn + Math.PI * corner / TIPS;
            double toAngle = turn + Math.PI * (corner + 1) / TIPS;
            double from = corner % 2 == 0 ? outer : inner;
            double to = corner % 2 == 0 ? inner : outer;
            for (int step = 0; step < PER_EDGE; step++) {
                double share = (double) step / PER_EDGE;
                double x = Math.cos(fromAngle) * from * (1 - share) + Math.cos(toAngle) * to * share;
                double z = Math.sin(fromAngle) * from * (1 - share) + Math.sin(toAngle) * to * share;
                points.add(new CosmeticPoint(x, height, z, frame.look().color(corner % 2)));
            }
        }
        return points;
    }
}
