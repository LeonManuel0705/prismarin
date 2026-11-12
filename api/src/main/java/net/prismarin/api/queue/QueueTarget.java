package net.prismarin.api.queue;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record QueueTarget(Kind kind, String name) {

    public enum Kind {
        GROUP,
        SERVER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            for (Kind kind : values()) {
                if (kind.id().equalsIgnoreCase(id.strip())) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public QueueTarget {
        Objects.requireNonNull(kind, "kind");
        name = Objects.requireNonNull(name, "name").strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("a queue target needs a name");
        }
    }

    public static QueueTarget group(String name) {
        return new QueueTarget(Kind.GROUP, name);
    }

    public static QueueTarget server(String name) {
        return new QueueTarget(Kind.SERVER, name);
    }

    public static Optional<QueueTarget> parse(String id) {
        if (id == null) {
            return Optional.empty();
        }
        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1) {
            return Optional.empty();
        }
        String name = id.substring(separator + 1).strip();
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Kind.byId(id.substring(0, separator)).map(kind -> new QueueTarget(kind, name));
    }

    public boolean isGroup() {
        return kind == Kind.GROUP;
    }

    public boolean isServer() {
        return kind == Kind.SERVER;
    }

    public String id() {
        return kind.id() + ":" + name;
    }
}
