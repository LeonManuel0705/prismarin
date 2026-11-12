package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.prismarin.api.cosmetics.CosmeticFrame;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import org.junit.jupiter.api.Test;

class EffectShapesTest {

    private static final double EPSILON = 1.0E-9;
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static List<CosmeticPoint> render(CosmeticParticles look, long tick, double yaw) {
        return CosmeticShapes.builtIn(look.shape(), new CosmeticFrame(PLAYER, tick, yaw, false, look));
    }

    private static double reach(CosmeticPoint point) {
        return Math.hypot(point.x(), point.z());
    }

    @Test
    void everyNewShapeIsBuiltInAndKnown() {
        CosmeticShapes shapes = new CosmeticShapes();
        for (String shape : List.of(CosmeticShapes.NONE, CosmeticShapes.WAVE, CosmeticShapes.RINGS,
                CosmeticShapes.METEOR, CosmeticShapes.RAIN, CosmeticShapes.SWIRL, CosmeticShapes.GHOST,
                CosmeticShapes.CROWN, CosmeticShapes.GYRO, CosmeticShapes.CLOUD)) {
            assertTrue(CosmeticShapes.BUILT_IN.contains(shape), shape);
            assertTrue(shapes.known(shape), shape);
            for (long tick = 0; tick < 120; tick += 7) {
                assertTrue(render(CosmeticParticles.of(shape, "dust"), tick, 30.0).size()
                        <= CosmeticParticles.MAX_POINTS, shape);
            }
        }
        assertEquals(List.of(), render(CosmeticParticles.of(CosmeticShapes.NONE, "dust"), 5L, 0.0));
    }

    @Test
    void aWaveGrowsWithinOnePeriodAndStartsAgain() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.WAVE, "dust").withRadius(2.0).withPoints(12);

        double start = reach(render(look, 0L, 0.0).getFirst());
        double middle = reach(render(look, 10L, 0.0).getFirst());
        double late = reach(render(look, 19L, 0.0).getFirst());
        double again = reach(render(look, CosmeticShapes.WAVE_PERIOD, 0.0).getFirst());

        assertEquals(12, render(look, 0L, 0.0).size());
        assertEquals(0.4, start, EPSILON);
        assertTrue(middle > start && late > middle);
        assertTrue(late <= 2.0);
        assertEquals(start, again, EPSILON);
        render(look, 7L, 0.0).forEach(point -> assertEquals(0.1, point.y(), EPSILON));
    }

    @Test
    void risingRingsStayInTheirColumnAndClimb() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.RINGS, "dust").withPoints(10)
                .withColors(0xFF0000, 0x00FF00, 0x0000FF);

        List<CosmeticPoint> now = render(look, 3L, 0.0);
        List<CosmeticPoint> later = render(look, 6L, 0.0);

        assertEquals(10 * CosmeticShapes.RINGS_COUNT, now.size());
        now.forEach(point -> {
            assertTrue(point.y() >= 0.1 - EPSILON && point.y() <= 0.1 + CosmeticShapes.RINGS_HEIGHT + EPSILON);
            assertTrue(reach(point) <= 0.9 + EPSILON);
        });
        assertEquals(Set.of(0xFF0000, 0x00FF00, 0x0000FF),
                now.stream().map(CosmeticPoint::color).collect(Collectors.toSet()));
        assertTrue(later.getFirst().y() > now.getFirst().y());
    }

    @Test
    void meteorsFallFromTheSkyTowardsTheMiddle() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.METEOR, "dust").withPoints(4)
                .withColors(0xFDBA74, 0xDC2626);

        List<CosmeticPoint> points = render(look, 0L, 0.0);

        assertEquals(4 * (CosmeticShapes.METEOR_TAIL + 1), points.size());
        points.forEach(point -> assertTrue(point.y() >= 0.1 - EPSILON
                && point.y() <= CosmeticShapes.METEOR_TOP + 0.1 + EPSILON));
        for (int meteor = 0; meteor < 4; meteor++) {
            CosmeticPoint head = points.get(meteor * (CosmeticShapes.METEOR_TAIL + 1));
            CosmeticPoint tail = points.get(meteor * (CosmeticShapes.METEOR_TAIL + 1) + 1);
            assertEquals(0xFDBA74, head.color());
            assertEquals(0xDC2626, tail.color());
            assertTrue(head.y() <= tail.y() + EPSILON);
        }
        CosmeticPoint first = points.getFirst();
        CosmeticPoint next = render(look, 1L, 0.0).getFirst();
        assertTrue(next.y() < first.y() || next.y() > CosmeticShapes.METEOR_TOP - 0.5);
    }

    @Test
    void rainStaysInsideItsCylinderAndFalls() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.RAIN, "dust").withRadius(2.0).withPoints(30);

        List<CosmeticPoint> now = render(look, 0L, 0.0);
        List<CosmeticPoint> later = render(look, 1L, 0.0);

        assertEquals(30, now.size());
        int falling = 0;
        for (int index = 0; index < now.size(); index++) {
            CosmeticPoint point = now.get(index);
            assertTrue(reach(point) <= 2.0 + EPSILON);
            assertTrue(point.y() >= 0.1 - EPSILON && point.y() <= CosmeticShapes.RAIN_TOP + 0.1 + EPSILON);
            assertEquals(point.x(), later.get(index).x(), EPSILON);
            if (later.get(index).y() < point.y()) {
                falling++;
            }
        }
        assertTrue(falling >= 25);
        assertEquals(now, render(look, 0L, 0.0));
    }

    @Test
    void aSwirlHasThreeArmsThatTurn() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.SWIRL, "dragon_breath").withRadius(2.4)
                .withPoints(36).withColors(1, 2, 3);

        List<CosmeticPoint> points = render(look, 0L, 0.0);
        List<CosmeticPoint> later = render(look, 10L, 0.0);

        assertEquals(36, points.size());
        points.forEach(point -> {
            assertTrue(reach(point) <= 2.4 + EPSILON);
            assertEquals(0.1, point.y(), EPSILON);
        });
        assertEquals(2.4, reach(points.get(11)), EPSILON);
        assertEquals(Set.of(1, 2, 3), points.stream().map(CosmeticPoint::color).collect(Collectors.toSet()));
        assertNotEquals(points.getFirst().x(), later.getFirst().x());
    }

    @Test
    void aGhostRisesAndFacesTheViewer() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.GHOST, "dust").withColors(0xF8FAFC, 0x1E293B);

        List<CosmeticPoint> start = render(look, 0L, 0.0);
        List<CosmeticPoint> later = render(look, 20L, 0.0);
        List<CosmeticPoint> top = render(look, 500L, 0.0);

        assertEquals(start.size(), later.size());
        double lowest = start.stream().mapToDouble(CosmeticPoint::y).min().orElseThrow();
        assertEquals(lowest + 1.2, later.stream().mapToDouble(CosmeticPoint::y).min().orElseThrow(), 0.2);
        assertTrue(top.stream().mapToDouble(CosmeticPoint::y).max().orElseThrow() < CosmeticShapes.GHOST_RISE + 2.0);
        start.forEach(point -> assertEquals(0.0, point.z(), EPSILON));
        assertEquals(2, start.stream().filter(point -> point.color() == 0x1E293B).count());
        render(look, 0L, 90.0).forEach(point -> assertEquals(0.0, point.x(), EPSILON));
    }

    @Test
    void aCrownSitsOnTheHeadWithSpikes() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.CROWN, "flame").withPoints(12);

        List<CosmeticPoint> points = render(look, 0L, 0.0);

        assertEquals(12, points.size());
        for (int index = 0; index < points.size(); index++) {
            CosmeticPoint point = points.get(index);
            assertEquals(0.32, reach(point), EPSILON);
            assertEquals(index % 2 == 0 ? 2.27 : 2.05, point.y(), EPSILON);
        }
    }

    @Test
    void gyroRingsKeepTheirRadiusAroundTheBody() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.GYRO, "dust").withRadius(1.0).withPoints(10)
                .withColors(0xA855F7, 0x22D3EE);

        for (long tick : new long[]{0L, 13L, 77L}) {
            List<CosmeticPoint> points = render(look, tick, 0.0);
            assertEquals(20, points.size());
            points.forEach(point -> assertEquals(1.0, Math.sqrt(point.x() * point.x()
                    + (point.y() - 1.0) * (point.y() - 1.0) + point.z() * point.z()), EPSILON));
            assertTrue(points.stream().anyMatch(point -> point.y() > 1.5));
            assertEquals(0xA855F7, points.getFirst().color());
            assertEquals(0x22D3EE, points.getLast().color());
        }
    }

    @Test
    void aCloudFloatsOverTheHeadAndDrops() {
        CosmeticParticles look = CosmeticParticles.of(CosmeticShapes.CLOUD, "dust").withPoints(8)
                .withColors(0xF1F5F9, 0x38BDF8);

        List<CosmeticPoint> points = render(look, 4L, 0.0);

        assertEquals(8 + CosmeticShapes.CLOUD_DROPS, points.size());
        List<CosmeticPoint> puffs = points.stream().filter(point -> point.color() == 0xF1F5F9).toList();
        List<CosmeticPoint> drops = points.stream().filter(point -> point.color() == 0x38BDF8).toList();
        assertEquals(8, puffs.size());
        assertEquals(CosmeticShapes.CLOUD_DROPS, drops.size());
        puffs.forEach(point -> assertEquals(CosmeticShapes.CLOUD_HEIGHT, point.y(), 0.07));
        drops.forEach(point -> assertTrue(point.y() < CosmeticShapes.CLOUD_HEIGHT - 0.1 && point.y() >= 0.2));
    }

    @Test
    void noiseIsStableAndSpread() {
        double sum = 0.0;
        for (int index = 0; index < 200; index++) {
            double value = CosmeticShapes.noise(index, 3);
            assertTrue(value >= 0.0 && value < 1.0);
            assertEquals(value, CosmeticShapes.noise(index, 3));
            sum += value;
        }
        assertEquals(0.5, sum / 200, 0.1);
    }
}
