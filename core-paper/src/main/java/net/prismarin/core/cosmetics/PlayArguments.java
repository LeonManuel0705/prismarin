package net.prismarin.core.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record PlayArguments(String player, String slot, Where where, Coordinate x, Coordinate y, Coordinate z) {

    public static final String HERE = "here";
    public static final String RELATIVE = "~";

    public enum Where {
        PLAYER,
        HERE,
        COORDINATES
    }

    public record Coordinate(double value, boolean relative) {

        public static final Coordinate SAME = new Coordinate(0.0, true);

        public double resolve(double base) {
            return relative ? base + value : value;
        }

        static Optional<Coordinate> parse(String text) {
            String value = text.strip();
            boolean relative = value.startsWith(RELATIVE);
            String number = relative ? value.substring(1) : value;
            if (relative && number.isEmpty()) {
                return Optional.of(SAME);
            }
            try {
                double parsed = Double.parseDouble(number);
                if (!Double.isFinite(parsed) || Math.abs(parsed) > 30_000_000.0) {
                    return Optional.empty();
                }
                return Optional.of(new Coordinate(parsed, relative));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }

    public static Optional<PlayArguments> parse(String[] args) {
        if (args.length != 3 && args.length != 4 && args.length != 6) {
            return Optional.empty();
        }
        String player = args[1].strip();
        String slot = args[2].strip().toLowerCase(Locale.ROOT);
        if (player.isEmpty() || slot.isEmpty()) {
            return Optional.empty();
        }
        if (args.length == 3) {
            return Optional.of(new PlayArguments(player, slot, Where.PLAYER, Coordinate.SAME, Coordinate.SAME,
                    Coordinate.SAME));
        }
        if (args.length == 4) {
            return HERE.equalsIgnoreCase(args[3].strip())
                    ? Optional.of(new PlayArguments(player, slot, Where.HERE, Coordinate.SAME, Coordinate.SAME,
                    Coordinate.SAME)) : Optional.empty();
        }
        Optional<Coordinate> x = Coordinate.parse(args[3]);
        Optional<Coordinate> y = Coordinate.parse(args[4]);
        Optional<Coordinate> z = Coordinate.parse(args[5]);
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PlayArguments(player, slot, Where.COORDINATES, x.get(), y.get(), z.get()));
    }

    public static List<String> suggestions(int index, String typed, List<String> players, List<String> slots) {
        String start = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> options = switch (index) {
            case 1 -> players;
            case 2 -> slots;
            case 3 -> List.of(HERE, RELATIVE);
            case 4, 5 -> List.of(RELATIVE);
            default -> List.of();
        };
        List<String> found = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(start)) {
                found.add(option);
            }
        }
        return List.copyOf(found);
    }
}
