package net.prismarin.shared.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.bson.conversions.Bson;

public final class MongoDocumentStore implements DocumentStore {

    private static final String PREFIX = "documents_";
    private static final String BODY = "body";
    private static final String FIELDS = "fields";

    private final MongoDatabase database;
    private final Set<String> indexed = ConcurrentHashMap.newKeySet();

    public MongoDocumentStore(MongoDatabase database) {
        this.database = database;
    }

    @Override
    public void save(String collection, String id, String json) {
        MongoCollection<Document> target = collectionFor(collection);
        Objects.requireNonNull(json, "json");
        Document document = new Document("_id", DocumentKeys.id(id))
                .append(BODY, json)
                .append(FIELDS, fields(json));
        target.replaceOne(Filters.eq("_id", id), document, new ReplaceOptions().upsert(true));
    }

    @Override
    public Optional<String> find(String collection, String id) {
        MongoCollection<Document> target = collectionFor(collection);
        Document document = target.find(Filters.eq("_id", DocumentKeys.id(id)))
                .projection(Projections.include(BODY))
                .first();
        return Optional.ofNullable(document).map(found -> found.getString(BODY));
    }

    @Override
    public List<String> findAll(String collection) {
        return bodies(collectionFor(collection).find());
    }

    @Override
    public List<String> findBy(String collection, String field, String value) {
        MongoCollection<Document> target = collectionFor(collection);
        if (field == null || value == null) {
            return List.of();
        }
        Bson filter = Filters.elemMatch(FIELDS, Filters.and(Filters.eq("k", field), Filters.eq("v", value)));
        return bodies(target.find(filter));
    }

    @Override
    public boolean delete(String collection, String id) {
        MongoCollection<Document> target = collectionFor(collection);
        return target.deleteOne(Filters.eq("_id", DocumentKeys.id(id))).getDeletedCount() > 0;
    }

    @Override
    public void close() {
    }

    private MongoCollection<Document> collectionFor(String collection) {
        String name = PREFIX + DocumentKeys.collection(collection);
        MongoCollection<Document> target = database.getCollection(name);
        if (!indexed.contains(name)) {
            target.createIndex(Indexes.ascending(FIELDS + ".k", FIELDS + ".v"));
            indexed.add(name);
        }
        return target;
    }

    private static List<String> bodies(FindIterable<Document> found) {
        List<String> bodies = new ArrayList<>();
        found.projection(Projections.include(BODY))
                .sort(Sorts.ascending("_id"))
                .forEach(document -> bodies.add(document.getString(BODY)));
        return bodies;
    }

    private static List<Document> fields(String json) {
        List<Document> fields = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            return fields;
        }
        if (root.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    fields.add(new Document("k", entry.getKey()).append("v", entry.getValue().getAsString()));
                }
            }
        }
        return fields;
    }
}
