package net.prismarin.shared.chatfilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record Span(int start, int end, String detail) {

    public Span {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("a span needs 0 <= start < end");
        }
        detail = detail == null ? "" : detail;
    }

    public static List<Span> merge(List<Span> spans) {
        List<Span> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingInt(Span::start).thenComparingInt(Span::end));
        List<Span> merged = new ArrayList<>();
        for (Span span : sorted) {
            if (!merged.isEmpty() && merged.getLast().end() >= span.start()) {
                Span last = merged.removeLast();
                merged.add(new Span(last.start(), Math.max(last.end(), span.end()), last.detail()));
            } else {
                merged.add(span);
            }
        }
        return merged;
    }

    public static String replace(String text, List<Span> spans, String replacement) {
        StringBuilder out = new StringBuilder(text.length());
        int position = 0;
        for (Span span : merge(spans)) {
            int start = Math.min(span.start(), text.length());
            int end = Math.min(span.end(), text.length());
            if (start < position) {
                continue;
            }
            out.append(text, position, start).append(replacement);
            position = end;
        }
        out.append(text, Math.min(position, text.length()), text.length());
        return out.toString();
    }
}
