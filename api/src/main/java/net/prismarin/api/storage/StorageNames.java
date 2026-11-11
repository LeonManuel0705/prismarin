package net.prismarin.api.storage;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class StorageNames {

    public static final Pattern NAMESPACE = Pattern.compile("[a-z0-9][a-z0-9-]{1,23}");
    public static final Pattern COLLECTION = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    public static final int MAX_ID_LENGTH = 128;
    public static final Set<String> RESERVED = Set.of("prismarin");

    private StorageNames() {
        throw new AssertionError();
    }

    public static boolean validNamespace(String name) {
        return name != null && NAMESPACE.matcher(name).matches() && !RESERVED.contains(name);
    }

    public static boolean validCollection(String name) {
        return name != null && COLLECTION.matcher(name).matches();
    }

    public static boolean validId(String id) {
        return id != null && !id.isEmpty() && id.length() <= MAX_ID_LENGTH;
    }

    public static String namespace(String name) {
        String normalized = normalize(name);
        if (RESERVED.contains(normalized)) {
            throw new IllegalArgumentException("the namespace '" + normalized + "' is reserved");
        }
        if (!validNamespace(normalized)) {
            throw new IllegalArgumentException("namespace '" + name + "' must match " + NAMESPACE.pattern());
        }
        return normalized;
    }

    public static String collection(String name) {
        String normalized = normalize(name);
        if (!validCollection(normalized)) {
            throw new IllegalArgumentException("collection '" + name + "' must match " + COLLECTION.pattern());
        }
        return normalized;
    }

    public static String id(String id) {
        if (!validId(id)) {
            throw new IllegalArgumentException("a document id needs 1 to " + MAX_ID_LENGTH + " characters");
        }
        return id;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
