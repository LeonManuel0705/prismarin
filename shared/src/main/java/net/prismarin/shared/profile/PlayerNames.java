package net.prismarin.shared.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.shared.storage.DocumentStore;

public final class PlayerNames {

    static final String COLLECTION = "names";

    private final DocumentStore documents;

    public PlayerNames(DocumentStore documents) {
        this.documents = documents;
    }

    public void remember(UUID uuid, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        JsonObject document = new JsonObject();
        document.addProperty("uuid", uuid.toString());
        document.addProperty("name", name);
        documents.save(COLLECTION, name.toLowerCase(Locale.ROOT), document.toString());
    }

    public Optional<UUID> lookup(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return documents.find(COLLECTION, name.toLowerCase(Locale.ROOT))
                .map(json -> JsonParser.parseString(json).getAsJsonObject().get("uuid").getAsString())
                .map(UUID::fromString);
    }
}
