package net.prismarin.shared.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.cosmetics.CosmeticFrame;
import net.prismarin.api.cosmetics.CosmeticParticles;
import net.prismarin.api.cosmetics.CosmeticPoint;
import net.prismarin.api.cosmetics.CosmeticRenderer;
import net.prismarin.api.event.Subscription;

public final class CosmeticShapes {

    public static final String POINT = "point";
    public static final String RING = "ring";
    public static final String HALO = "halo";
    public static final String SPIRAL = "spiral";
    public static final String ORBIT = "orbit";
    public static final String WINGS = "wings";
    public static final String BURST = "burst";
    public static final String HEART = "heart";
    public static final String PILLAR = "pillar";
    public static final String NONE = "none";
    public static final String WAVE = "wave";
    public static final String RINGS = "rings";
    public static final String METEOR = "meteor";
    public static final String RAIN = "rain";
    public static final String SWIRL = "swirl";
    public static final String GHOST = "ghost";
    public static final String CROWN = "crown";
    public static final String GYRO = "gyro";
    public static final String CLOUD = "rain-cloud";

    public static final List<String> BUILT_IN = List.of(POINT, RING, HALO, SPIRAL, ORBIT, WINGS, BURST, HEART,
            PILLAR, NONE, WAVE, RINGS, METEOR, RAIN, SWIRL, GHOST, CROWN, GYRO, CLOUD);

    static final int WAVE_PERIOD = 20;
    static final double RINGS_HEIGHT = 2.4;
    static final int RINGS_COUNT = 3;
    static final double METEOR_TOP = 6.0;
    static final int METEOR_CYCLE = 30;
    static final int METEOR_TAIL = 3;
    static final double RAIN_TOP = 4.0;
    static final double GHOST_RISE = 2.5;
    static final double CLOUD_HEIGHT = 2.6;
    static final int CLOUD_DROPS = 3;

    static final double WING_STEP = 0.2;
    static final double WING_SPINE = 0.15;
    static final double WING_TOP = 2.1;
    static final double WING_BACK = 0.25;
    static final List<String> WING = List.of(
            ".....oo",
            "...ooxo",
            ".ooxxxo",
            "oxxxxo.",
            "oxxo...",
            ".oo....");

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private final Map<String, CosmeticRenderer> registered = new ConcurrentHashMap<>();

    public Subscription register(String id, CosmeticRenderer renderer) {
        String key = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (!CosmeticParticles.KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("'" + id + "' is no shape name");
        }
        if (BUILT_IN.contains(key)) {
            throw new IllegalArgumentException("the shape " + key + " is built in");
        }
        Objects.requireNonNull(renderer, "renderer");
        if (registered.putIfAbsent(key, renderer) != null) {
            throw new IllegalArgumentException("the shape " + key + " is already registered");
        }
        return () -> registered.remove(key, renderer);
    }

    public boolean known(String shape) {
        return BUILT_IN.contains(shape) || registered.containsKey(shape);
    }

    public Optional<CosmeticRenderer> plugin(String shape) {
        return Optional.ofNullable(registered.get(shape));
    }

    public List<CosmeticPoint> render(CosmeticFrame frame) {
        String shape = frame.look().shape();
        CosmeticRenderer renderer = registered.get(shape);
        if (renderer == null) {
            return builtIn(BUILT_IN.contains(shape) ? shape : POINT, frame);
        }
        List<CosmeticPoint> points = renderer.render(frame);
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        return points.size() <= CosmeticParticles.MAX_POINTS ? List.copyOf(points)
                : List.copyOf(points.subList(0, CosmeticParticles.MAX_POINTS));
    }

    public static List<CosmeticPoint> builtIn(String shape, CosmeticFrame frame) {
        CosmeticParticles look = frame.look();
        long tick = frame.tick();
        return switch (shape) {
            case RING -> ring(radius(look, 0.8), 0.1 + look.height(), points(look, 20), phase(tick, look, 0.05),
                    look);
            case HALO -> ring(radius(look, 0.35), 2.15 + look.height(), points(look, 12), phase(tick, look, 0.08),
                    look);
            case SPIRAL -> helix(radius(look, 0.6), 0.05 + look.height(), 2.0, 2.0, points(look, 16),
                    phase(tick, look, 0.15), look);
            case PILLAR -> helix(radius(look, 0.3), look.height(), 2.6, 3.0, points(look, 20),
                    phase(tick, look, 0.2), look);
            case ORBIT -> orbit(radius(look, 0.8), 1.0 + look.height(), points(look, 3), phase(tick, look, 0.12),
                    look);
            case WINGS -> wings(radius(look, 1.0), look.height(), frame.yaw(),
                    Math.toRadians(20.0 + 12.0 * Math.sin(phase(tick, look, 0.15))), look);
            case BURST -> burst(radius(look, 0.9), 1.0 + look.height(), points(look, 30), phase(tick, look, 0.0),
                    look);
            case HEART -> heart(radius(look, 0.5), 1.3 + look.height(), points(look, 24), frame.yaw(), look);
            case NONE -> List.of();
            case WAVE -> wave(radius(look, 1.6), 0.1 + look.height(), points(look, 24), tick, look);
            case RINGS -> rings(radius(look, 0.9), look.height(), points(look, 16), phase(tick, look, 0.04), look);
            case METEOR -> meteor(radius(look, 3.0), look.height(), points(look, 5), tick, look);
            case RAIN -> rain(radius(look, 2.5), look.height(), points(look, 40), phase(tick, look, 0.15), look);
            case SWIRL -> swirl(radius(look, 2.2), 0.1 + look.height(), points(look, 36),
                    phase(tick, look, 0.12), look);
            case GHOST -> ghost(radius(look, 0.3), look.height(), tick, frame.yaw(), look);
            case CROWN -> crown(radius(look, 0.32), 2.05 + look.height(), points(look, 16),
                    phase(tick, look, 0.05), look);
            case GYRO -> gyro(radius(look, 0.9), 1.0 + look.height(), points(look, 12), phase(tick, look, 0.1),
                    look);
            case CLOUD -> cloud(radius(look, 0.45), CLOUD_HEIGHT + look.height(), points(look, 10), tick, look);
            default -> List.of(new CosmeticPoint(0.0, 0.1 + look.height(), 0.0, look.color((int) (tick / 2L))));
        };
    }

    public static List<CosmeticPoint> ring(double radius, double height, int points, double phase,
                                           CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double angle = phase + 2.0 * Math.PI * index / points;
            out.add(new CosmeticPoint(radius * Math.cos(angle), height, radius * Math.sin(angle), look.color(index)));
        }
        return out;
    }

    public static List<CosmeticPoint> helix(double radius, double base, double height, double turns, int points,
                                            double phase, CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double share = points == 1 ? 0.0 : (double) index / (points - 1);
            double angle = phase + share * turns * 2.0 * Math.PI;
            out.add(new CosmeticPoint(radius * Math.cos(angle), base + share * height, radius * Math.sin(angle),
                    look.color(index)));
        }
        return out;
    }

    public static List<CosmeticPoint> orbit(double radius, double height, int points, double phase,
                                            CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double angle = phase + 2.0 * Math.PI * index / points;
            out.add(new CosmeticPoint(radius * Math.cos(angle), height + 0.25 * Math.sin(phase * 2.0 + angle),
                    radius * Math.sin(angle), look.color(index)));
        }
        return out;
    }

    public static List<CosmeticPoint> wings(double scale, double lift, double yaw, double flap,
                                            CosmeticParticles look) {
        double radians = Math.toRadians(yaw);
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);
        double backX = Math.sin(radians);
        double backZ = -Math.cos(radians);
        int outline = look.color(0);
        int fill = look.colors().size() > 1 ? look.color(1) : outline;
        List<CosmeticPoint> out = new ArrayList<>();
        for (int row = 0; row < WING.size(); row++) {
            String line = WING.get(row);
            double y = WING_TOP + lift - row * WING_STEP * scale;
            for (int column = 0; column < line.length(); column++) {
                char mark = line.charAt(column);
                if (mark == '.') {
                    continue;
                }
                double reach = (WING_SPINE + column * WING_STEP) * scale;
                double side = reach * Math.cos(flap);
                double back = WING_BACK + reach * Math.sin(flap);
                int color = mark == 'o' ? outline : fill;
                for (int sign : new int[]{1, -1}) {
                    out.add(new CosmeticPoint(rightX * side * sign + backX * back, y,
                            rightZ * side * sign + backZ * back, color));
                }
            }
        }
        return out;
    }

    public static List<CosmeticPoint> burst(double radius, double height, int points, double phase,
                                            CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double y = 1.0 - 2.0 * (index + 0.5) / points;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double angle = index * GOLDEN_ANGLE + phase;
            out.add(new CosmeticPoint(radius * ring * Math.cos(angle), height + radius * y,
                    radius * ring * Math.sin(angle), look.color(index)));
        }
        return out;
    }

    public static List<CosmeticPoint> heart(double size, double height, int points, double yaw,
                                            CosmeticParticles look) {
        double radians = Math.toRadians(yaw);
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);
        double scale = size / 16.0;
        List<CosmeticPoint> out = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double t = 2.0 * Math.PI * index / points;
            double x = 16.0 * Math.pow(Math.sin(t), 3);
            double y = 13.0 * Math.cos(t) - 5.0 * Math.cos(2 * t) - 2.0 * Math.cos(3 * t) - Math.cos(4 * t);
            out.add(new CosmeticPoint(rightX * x * scale, height + y * scale, rightZ * x * scale, look.color(index)));
        }
        return out;
    }

    public static List<CosmeticPoint> wave(double radius, double height, int points, long tick,
                                           CosmeticParticles look) {
        double share = (double) Math.floorMod(tick, WAVE_PERIOD) / WAVE_PERIOD;
        return ring(radius * (0.2 + 0.8 * share), height, points, share * Math.PI, look);
    }

    public static List<CosmeticPoint> rings(double radius, double base, int perRing, double phase,
                                            CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(perRing * RINGS_COUNT);
        for (int ring = 0; ring < RINGS_COUNT; ring++) {
            double share = fraction(phase + (double) ring / RINGS_COUNT);
            double height = base + 0.1 + share * RINGS_HEIGHT;
            double size = radius * (1.0 - 0.45 * share);
            for (int index = 0; index < perRing; index++) {
                double angle = 2.0 * Math.PI * index / perRing + ring;
                out.add(new CosmeticPoint(size * Math.cos(angle), height, size * Math.sin(angle), look.color(ring)));
            }
        }
        return out;
    }

    public static List<CosmeticPoint> meteor(double radius, double base, int meteors, long tick,
                                             CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(meteors * (METEOR_TAIL + 1));
        for (int index = 0; index < meteors; index++) {
            double angle = index * GOLDEN_ANGLE;
            double reach = radius * (0.4 + 0.6 * noise(index, 1));
            double startX = Math.cos(angle) * (reach + 2.5);
            double startZ = Math.sin(angle) * (reach + 2.5);
            double endX = Math.cos(angle) * reach * 0.35;
            double endZ = Math.sin(angle) * reach * 0.35;
            long offset = Math.round(noise(index, 2) * METEOR_CYCLE);
            double progress = (double) Math.floorMod(tick + offset, METEOR_CYCLE) / METEOR_CYCLE;
            for (int tail = 0; tail <= METEOR_TAIL; tail++) {
                double at = Math.max(0.0, progress - tail * 0.04);
                out.add(new CosmeticPoint(startX + (endX - startX) * at, base + METEOR_TOP * (1.0 - at) + 0.1,
                        startZ + (endZ - startZ) * at, look.color(tail == 0 ? 0 : 1)));
            }
        }
        return out;
    }

    public static List<CosmeticPoint> rain(double radius, double base, int drops, double phase,
                                           CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(drops);
        for (int index = 0; index < drops; index++) {
            double angle = 2.0 * Math.PI * noise(index, 3);
            double reach = radius * Math.sqrt(noise(index, 4));
            double fall = fraction(noise(index, 5) + phase / RAIN_TOP);
            out.add(new CosmeticPoint(reach * Math.cos(angle), base + 0.1 + RAIN_TOP * (1.0 - fall),
                    reach * Math.sin(angle), look.color(index)));
        }
        return out;
    }

    public static List<CosmeticPoint> swirl(double radius, double height, int points, double phase,
                                            CosmeticParticles look) {
        int arms = 3;
        int perArm = Math.max(1, points / arms);
        List<CosmeticPoint> out = new ArrayList<>(perArm * arms);
        for (int arm = 0; arm < arms; arm++) {
            for (int index = 1; index <= perArm; index++) {
                double share = (double) index / perArm;
                double angle = phase + arm * 2.0 * Math.PI / arms + share * Math.PI * 1.5;
                out.add(new CosmeticPoint(radius * share * Math.cos(angle), height, radius * share * Math.sin(angle),
                        look.color(arm)));
            }
        }
        return out;
    }

    public static List<CosmeticPoint> ghost(double size, double lift, long tick, double yaw,
                                            CosmeticParticles look) {
        double radians = Math.toRadians(yaw);
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);
        double rise = lift + Math.min(GHOST_RISE, tick * 0.06);
        int body = look.color(0);
        int eyes = look.colors().size() > 1 ? look.color(1) : body;
        List<CosmeticPoint> out = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            double angle = 2.0 * Math.PI * index / 10;
            out.add(point(rightX, rightZ, size * Math.cos(angle), rise + 1.45 + size * Math.sin(angle), body));
        }
        for (int step = 0; step <= 4; step++) {
            double share = step / 4.0;
            double width = size * (0.85 + 0.5 * share);
            double y = rise + 1.2 - share * 0.7;
            out.add(point(rightX, rightZ, width, y, body));
            out.add(point(rightX, rightZ, -width, y, body));
        }
        double hem = size * 1.35;
        for (int step = 0; step <= 6; step++) {
            double x = -hem + 2.0 * hem * step / 6;
            double y = rise + 0.5 + 0.08 * Math.sin(step * Math.PI / 1.5 + tick * 0.3);
            out.add(point(rightX, rightZ, x, y, body));
        }
        out.add(point(rightX, rightZ, size * 0.35, rise + 1.5, eyes));
        out.add(point(rightX, rightZ, -size * 0.35, rise + 1.5, eyes));
        return out;
    }

    public static List<CosmeticPoint> crown(double radius, double height, int points, double phase,
                                            CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double angle = phase + 2.0 * Math.PI * index / points;
            boolean spike = index % 2 == 0;
            out.add(new CosmeticPoint(radius * Math.cos(angle), height + (spike ? 0.22 : 0.0),
                    radius * Math.sin(angle), look.color(spike ? 0 : 1)));
        }
        return out;
    }

    public static List<CosmeticPoint> gyro(double radius, double height, int perRing, double phase,
                                           CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(perRing * 2);
        double tilt = Math.toRadians(60.0);
        for (int ring = 0; ring < 2; ring++) {
            double turn = ring == 0 ? phase : -phase + Math.PI / 2;
            double lean = ring == 0 ? tilt : -tilt;
            for (int index = 0; index < perRing; index++) {
                double angle = 2.0 * Math.PI * index / perRing;
                double x = radius * Math.cos(angle);
                double flat = radius * Math.sin(angle);
                double y = flat * Math.sin(lean);
                double z = flat * Math.cos(lean);
                out.add(new CosmeticPoint(x * Math.cos(turn) - z * Math.sin(turn), height + y,
                        x * Math.sin(turn) + z * Math.cos(turn), look.color(ring)));
            }
        }
        return out;
    }

    public static List<CosmeticPoint> cloud(double radius, double height, int puffs, long tick,
                                            CosmeticParticles look) {
        List<CosmeticPoint> out = new ArrayList<>(puffs + CLOUD_DROPS);
        for (int index = 0; index < puffs; index++) {
            double angle = 2.0 * Math.PI * noise(index, 6);
            double reach = radius * Math.sqrt(noise(index, 7));
            out.add(new CosmeticPoint(reach * Math.cos(angle), height + 0.12 * (noise(index, 8) - 0.5),
                    reach * 0.7 * Math.sin(angle), look.color(0)));
        }
        int drop = look.colors().size() > 1 ? look.color(1) : look.color(0);
        for (int index = 0; index < CLOUD_DROPS; index++) {
            double angle = 2.0 * Math.PI * noise(index, 9);
            double reach = radius * 0.8 * noise(index, 10);
            double fall = fraction(noise(index, 11) + tick * 0.08);
            out.add(new CosmeticPoint(reach * Math.cos(angle), height - 0.2 - fall * (height - 0.4),
                    reach * 0.7 * Math.sin(angle), drop));
        }
        return out;
    }

    static double noise(int index, int salt) {
        long mixed = (index + 1L) * 0x9E3779B97F4A7C15L ^ (salt + 1L) * 0xC2B2AE3D27D4EB4FL;
        mixed ^= mixed >>> 31;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 29;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    private static CosmeticPoint point(double rightX, double rightZ, double side, double y, int color) {
        return new CosmeticPoint(rightX * side, y, rightZ * side, color);
    }

    static int wingPoints() {
        int marks = 0;
        for (String line : WING) {
            for (char mark : line.toCharArray()) {
                if (mark != '.') {
                    marks++;
                }
            }
        }
        return marks * 2;
    }

    private static double radius(CosmeticParticles look, double fallback) {
        return look.radius() > 0.0 ? look.radius() : fallback;
    }

    private static int points(CosmeticParticles look, int fallback) {
        return look.points() > 0 ? look.points() : fallback;
    }

    private static double phase(long tick, CosmeticParticles look, double fallback) {
        return tick * (look.spin() != 0.0 ? look.spin() : fallback);
    }
}
