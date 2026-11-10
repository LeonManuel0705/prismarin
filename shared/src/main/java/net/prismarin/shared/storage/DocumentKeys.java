package net.prismarin.shared.storage;

import java.util.regex.Pattern;

final class DocumentKeys {

    private static final Pattern COLLECTION = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final int MAX_ID_LENGTH = 128;

    private DocumentKeys() {
        throw new AssertionError();
    }

    static String collection(String collection) {
        if (collection == null || !COLLECTION.matcher(collection).matches()) {
            throw new IllegalArgumentException("invalid collection name: " + collection);
        }
        return collection;
    }

    static String id(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("invalid document id");
        }
        return id;
    }
}
