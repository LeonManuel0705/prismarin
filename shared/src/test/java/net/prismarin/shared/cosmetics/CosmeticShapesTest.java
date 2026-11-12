package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.prismarin.api.cosmetics.CosmeticFrame;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import net.prismarin.api.event.Subscription;
import org.junit.jupiter.api.Test;

class CosmeticShapesTest {

    private static final double EPSILON = 1.0E-9;
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static CosmeticFrame frame(CosmeticParticles look, long tick, double yaw) {
        return new CosmeticFrame(PLAYER, tick, yaw, false, look);
    }

    @Test
    void aRingPutsEveryPointOnTheCircleAtOneHeight() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.RING, "dust").withRadius(1.2).withPoints(16)
                .withHeight(0.4).withColors(0x22D3EE, 0x2DD4BF);

        List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.RING, frame(look, 40L, 0.0));

        assertEquals(16, points.size());
        for (int index = 0; index < points.size(); index++) {
            CosmeticPoint point = points.get(index);
            assertEquals(1.2, Math.hypot(point.x(), point.z()), EPSILON);
            assertEquals(0.5, point.y(), EPSILON);
            assertEquals(index % 2 == 0 ? 0x22D3EE : 0x2DD4BF, point.color());
        }
        CosmeticPoint first = points.get(0);
        CosmeticPoint second = points.get(1);
        assertEquals(Math.cos(2.0 * Math.PI / 16), (first.x() * second.x() + first.z() * second.z()) / (1.2 * 1.2),
                EPSILON);
    }

    @Test
    void aRingTurnsWithTheTicks() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.RING, "dust").withSpin(0.1);

        CosmeticPoint first = CosmeticShapes.builtIn(CosmeticShapes.RING, frame(look, 0L, 0.0)).getFirst();
        CosmeticPoint later = CosmeticShapes.builtIn(CosmeticShapes.RING, frame(look, 5L, 0.0)).getFirst();

        assertEquals(0.8, first.x(), EPSILON);
        assertEquals(0.8 * Math.cos(0.5), later.x(), EPSILON);
        assertEquals(0.8 * Math.sin(0.5), later.z(), EPSILON);
    }

    @Test
    void aHaloFloatsAboveTheHead() {
        List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.HALO,
                frame(CosmeticParticles.of(CosmeticShapes.HALO, "dust"), 0L, 90.0));

        assertEquals(12, points.size());
        points.forEach(point -> {
            assertEquals(2.15, point.y(), EPSILON);
            assertEquals(0.35, Math.hypot(point.x(), point.z()), EPSILON);
        });
    }

    @Test
    void aSpiralClimbsFromTheFeetToTheTop() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.SPIRAL, "end_rod").withPoints(9);

        List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.SPIRAL, frame(look, 0L, 0.0));

        assertEquals(9, points.size());
        assertEquals(0.05, points.getFirst().y(), EPSILON);
        assertEquals(2.05, points.getLast().y(), EPSILON);
        for (int index = 1; index < points.size(); index++) {
            assertTrue(points.get(index).y() > points.get(index - 1).y());
            assertEquals(0.6, Math.hypot(points.get(index).x(), points.get(index).z()), EPSILON);
        }
        assertEquals(points.getFirst().x(), points.getLast().x(), EPSILON);
    }

    @Test
    void aPillarIsATightTallerHelix() {
        List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.PILLAR,
                frame(CosmeticParticles.of(CosmeticShapes.PILLAR, "totem_of_undying"), 0L, 0.0));

        assertEquals(20, points.size());
        assertEquals(2.6, points.getLast().y() - points.getFirst().y(), EPSILON);
        points.forEach(point -> assertEquals(0.3, Math.hypot(point.x(), point.z()), EPSILON));
    }

    @Test
    void orbitsCircleEvenlyAroundTheWaist() {
        List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.ORBIT,
                frame(CosmeticParticles.of(CosmeticShapes.ORBIT, "dust"), 0L, 0.0));

        assertEquals(3, points.size());
        double sumX = points.stream().mapToDouble(CosmeticPoint::x).sum();
        double sumZ = points.stream().mapToDouble(CosmeticPoint::z).sum();
        assertEquals(0.0, sumX, EPSILON);
        assertEquals(0.0, sumZ, EPSILON);
        points.forEach(point -> {
            assertEquals(0.8, Math.hypot(point.x(), point.z()), EPSILON);
            assertTrue(Math.abs(point.y() - 1.0) <= 0.25 + EPSILON);
        });
    }

    @Test
    void wingsAreMirroredBehindThePlayerForEveryDirection() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.WINGS, "dust").withColors(0xFDE68A, 0xFFFFFF);
        for (double yaw : new double[]{0.0, 45.0, 90.0, 180.0, -135.0, 270.0}) {
            List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.WINGS, frame(look, 17L, yaw));
            assertEquals(CosmeticShapes.wingPoints(), points.size(), "yaw " + yaw);
            double radians = Math.toRadians(yaw);
            double forwardX = -Math.sin(radians);
            double forwardZ = Math.cos(radians);
            double rightX = -Math.cos(radians);
            double rightZ = -Math.sin(radians);
            for (int index = 0; index < points.size(); index += 2) {
                CosmeticPoint right = points.get(index);
                CosmeticPoint left = points.get(index + 1);
                assertTrue(right.x() * forwardX + right.z() * forwardZ < 0.0, "behind at yaw " + yaw);
                assertTrue(left.x() * forwardX + left.z() * forwardZ < 0.0, "behind at yaw " + yaw);
                assertEquals(right.y(), left.y(), EPSILON);
                double rightSide = right.x() * rightX + right.z() * rightZ;
                double leftSide = left.x() * rightX + left.z() * rightZ;
                assertTrue(rightSide > 0.0);
                assertEquals(-rightSide, leftSide, EPSILON);
                assertEquals(right.color(), left.color());
            }
            assertTrue(points.stream().allMatch(point -> point.y() <= CosmeticShapes.WING_TOP + EPSILON
                    && point.y() > 0.9));
        }
    }

    @Test
    void wingsUseTheFirstColourForTheRimAndTheSecondInside() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.WINGS, "dust").withColors(0xFF0000, 0x0000FF);

        List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.WINGS, frame(look, 0L, 0.0));

        long rim = points.stream().filter(point -> point.color() == 0xFF0000).count();
        long inside = points.stream().filter(point -> point.color() == 0x0000FF).count();
        assertEquals(points.size(), rim + inside);
        assertTrue(rim > 0 && inside > 0);
    }

    @Test
    void wingsFlapWithoutLosingTheirShape() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.WINGS, "dust");
        List<CosmeticPoint> open = CosmeticShapes.builtIn(CosmeticShapes.WINGS, frame(look, 0L, 0.0));
        List<CosmeticPoint> later = CosmeticShapes.builtIn(CosmeticShapes.WINGS, frame(look, 10L, 0.0));

        assertEquals(open.size(), later.size());
        assertTrue(IntStream.range(0, open.size()).anyMatch(index -> Math.abs(open.get(index).z()
                - later.get(index).z()) > 1.0E-3));
        for (int index = 0; index < open.size(); index++) {
            assertEquals(open.get(index).y(), later.get(index).y(), EPSILON);
        }
    }

    @Test
    void aBurstSpreadsItsPointsOverASphere() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.BURST, "firework").withRadius(1.5)
                .withPoints(40);

        List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.BURST, frame(look, 0L, 0.0));

        assertEquals(40, points.size());
        points.forEach(point -> assertEquals(1.5, Math.sqrt(point.x() * point.x() + (point.y() - 1.0)
                * (point.y() - 1.0) + point.z() * point.z()), EPSILON));
        assertTrue(points.stream().anyMatch(point -> point.y() > 2.0));
        assertTrue(points.stream().anyMatch(point -> point.y() < 0.0));
    }

    @Test
    void aHeartStandsUprightAndSymmetricFacingTheYaw() {
        List<CosmeticPoint> points = CosmeticShapes.builtIn(CosmeticShapes.HEART,
                frame(CosmeticParticles.of(CosmeticShapes.HEART, "dust").withPoints(24), 0L, 0.0));

        assertEquals(24, points.size());
        points.forEach(point -> assertEquals(0.0, point.z(), EPSILON));
        for (int index = 1; index < 12; index++) {
            assertEquals(-points.get(index).x(), points.get(24 - index).x(), EPSILON);
            assertEquals(points.get(index).y(), points.get(24 - index).y(), EPSILON);
        }
        assertTrue(points.stream().mapToDouble(point -> Math.abs(point.x())).max().orElse(0) <= 0.5 + EPSILON);
    }

    @Test
    void unknownShapesDrawASinglePointAndPointsCycleTheirColour() {
        CosmeticShapes shapes = new CosmeticShapes();
        CosmeticParticles look = CosmeticParticles.of("comet", "dust").withColors(1, 2, 3);

        List<CosmeticPoint> first = shapes.render(frame(look, 0L, 0.0));
        List<CosmeticPoint> second = shapes.render(frame(look, 2L, 0.0));

        assertFalse(shapes.known("comet"));
        assertEquals(1, first.size());
        assertEquals(0.1, first.getFirst().y(), EPSILON);
        assertEquals(1, first.getFirst().color());
        assertEquals(2, second.getFirst().color());
    }

    @Test
    void pluginShapesRenderUntilTheyAreRemoved() {
        CosmeticShapes shapes = new CosmeticShapes();
        CosmeticParticles look = CosmeticParticles.of("comet", "dust");
        Subscription comet = shapes.register("Comet", frame -> List.of(CosmeticPoint.of(1, 2, 3),
                CosmeticPoint.of(4, 5, 6)));

        assertTrue(shapes.known("comet"));
        assertEquals(2, shapes.render(frame(look, 0L, 0.0)).size());
        assertThrows(IllegalArgumentException.class, () -> shapes.register("comet", frame -> List.of()));
        assertThrows(IllegalArgumentException.class, () -> shapes.register(CosmeticShapes.WINGS,
                frame -> List.of()));
        comet.unsubscribe();
        assertFalse(shapes.known("comet"));
        assertEquals(1, shapes.render(frame(look, 0L, 0.0)).size());
    }

    @Test
    void pluginShapesAreCutAtTheLimit() {
        CosmeticShapes shapes = new CosmeticShapes();
        shapes.register("cloud", frame -> IntStream.range(0, 500).mapToObj(index -> CosmeticPoint.of(index, 0, 0))
                .toList());

        assertEquals(CosmeticParticles.MAX_POINTS, shapes.render(frame(CosmeticParticles.of("cloud", "dust"), 0L,
                0.0)).size());
    }
}
