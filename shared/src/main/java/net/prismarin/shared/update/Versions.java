package net.prismarin.shared.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Versions {

    private Versions() {
        throw new AssertionError();
    }

    public static int compare(String left, String right) {
        List<Integer> a = numbers(left);
        List<Integer> b = numbers(right);
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int x = i < a.size() ? a.get(i) : 0;
            int y = i < b.size() ? b.get(i) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return Boolean.compare(!preRelease(left), !preRelease(right));
    }

    public static boolean newer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    static boolean preRelease(String version) {
        return clean(version).contains("-");
    }

    private static String clean(String version) {
        String value = version == null ? "" : version.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("v") ? value.substring(1) : value;
    }

    private static List<Integer> numbers(String version) {
        String core = clean(version);
        int dash = core.indexOf('-');
        if (dash >= 0) {
            core = core.substring(0, dash);
        }
        List<Integer> parts = new ArrayList<>();
        for (String part : core.split("\\.")) {
            String digits = part.replaceAll("[^0-9].*$", "");
            parts.add(digits.isEmpty() ? 0 : Integer.parseInt(digits));
        }
        return parts;
    }
}
