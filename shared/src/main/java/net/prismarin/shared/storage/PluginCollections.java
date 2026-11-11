package net.prismarin.shared.storage;

import java.util.List;
import net.prismarin.api.storage.StorageNames;

public final class PluginCollections {

    public static final String PREFIX = "ext_";
    private static final String SEPARATOR = "_";

    private PluginCollections() {
        throw new AssertionError();
    }

    public static String physical(String namespace, String collection) {
        return PREFIX + StorageNames.namespace(namespace) + SEPARATOR + StorageNames.collection(collection);
    }

    public static boolean owned(String physical) {
        return physical != null && physical.startsWith(PREFIX);
    }

    public static List<String> in(DocumentStore documents) {
        return documents.collections().stream().filter(PluginCollections::owned).sorted().toList();
    }
}
