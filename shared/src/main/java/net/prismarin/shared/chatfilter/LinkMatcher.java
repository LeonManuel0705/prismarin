package net.prismarin.shared.chatfilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinkMatcher {

    private static final Pattern OBFUSCATED_DOT = Pattern.compile(
            "\\s*[(\\[{<]\\s*(?:\\.|dot|punkt)\\s*[)\\]}>]\\s*"
                    + "|\\s+(?:dot|punkt)\\s+"
                    + "|(?<=[a-z0-9])\\s+\\.\\s*(?=[a-z0-9])");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?<![a-z0-9@._-])(https?://)?((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+([a-z]{2,24}))"
                    + "(?![a-z0-9-])(?::\\d{1,5})?(?:/\\S*)?");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9.])(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?![0-9]|\\.\\d)(?::\\d{1,5})?");

    private final boolean ips;
    private final Set<String> allowed;
    private final Set<String> topLevelDomains;

    public LinkMatcher(boolean ips, List<String> allowed, List<String> topLevelDomains) {
        this.ips = ips;
        this.allowed = normalized(allowed);
        this.topLevelDomains = normalized(topLevelDomains);
    }

    public List<Span> find(String text) {
        Mapped mapped = Mapped.of(text);
        List<Span> spans = new ArrayList<>();
        Matcher domains = DOMAIN.matcher(mapped.text());
        while (domains.find()) {
            boolean scheme = domains.group(1) != null;
            String host = domains.group(2);
            if ((scheme || topLevelDomains.contains(domains.group(3))) && !allowed(host)) {
                spans.add(mapped.span(domains.start(), domains.end(), host));
            }
        }
        if (ips) {
            Matcher addresses = IPV4.matcher(mapped.text());
            while (addresses.find()) {
                String address = addresses.group(1) + "." + addresses.group(2) + "." + addresses.group(3) + "."
                        + addresses.group(4);
                if (octets(addresses) && !allowed.contains(address)) {
                    spans.add(mapped.span(addresses.start(), addresses.end(), address));
                }
            }
        }
        return spans;
    }

    boolean allowed(String host) {
        String bare = host.startsWith("www.") ? host.substring(4) : host;
        for (String entry : allowed) {
            if (bare.equals(entry) || bare.endsWith("." + entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean octets(Matcher matcher) {
        for (int group = 1; group <= 4; group++) {
            if (Integer.parseInt(matcher.group(group)) > 255) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> normalized(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String entry = value.trim().toLowerCase(Locale.ROOT);
            while (entry.startsWith("*.") || entry.startsWith(".")) {
                entry = entry.substring(entry.indexOf('.') + 1);
            }
            if (entry.startsWith("www.")) {
                entry = entry.substring(4);
            }
            if (!entry.isEmpty()) {
                result.add(entry);
            }
        }
        return Set.copyOf(result);
    }

    record Mapped(String text, int[] starts, int[] ends) {

        static Mapped of(String original) {
            StringBuilder lower = new StringBuilder(original.length());
            for (int index = 0; index < original.length(); index++) {
                char c = original.charAt(index);
                lower.append(c == '\u3002' || c == '\uFF0E' || c == '\uFF61' ? '.' : Character.toLowerCase(c));
            }
            String source = lower.toString();
            StringBuilder out = new StringBuilder(source.length());
            int[] starts = new int[source.length() + 1];
            int[] ends = new int[source.length() + 1];
            Matcher matcher = OBFUSCATED_DOT.matcher(source);
            int position = 0;
            while (matcher.find()) {
                for (int index = position; index < matcher.start(); index++) {
                    starts[out.length()] = index;
                    ends[out.length()] = index + 1;
                    out.append(source.charAt(index));
                }
                starts[out.length()] = matcher.start();
                ends[out.length()] = matcher.end();
                out.append('.');
                position = matcher.end();
            }
            for (int index = position; index < source.length(); index++) {
                starts[out.length()] = index;
                ends[out.length()] = index + 1;
                out.append(source.charAt(index));
            }
            int length = out.length();
            return new Mapped(out.toString(), Arrays.copyOf(starts, length), Arrays.copyOf(ends, length));
        }

        Span span(int start, int end, String detail) {
            return new Span(starts[start], ends[end - 1], detail);
        }
    }
}
