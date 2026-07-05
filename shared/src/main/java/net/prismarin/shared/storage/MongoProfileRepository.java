package net.prismarin.shared.storage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyResult;
import net.prismarin.api.profile.PlayerProfile;
import org.bson.Document;
import org.bson.conversions.Bson;

public final class MongoProfileRepository implements ProfileRepository {

    private final MongoCollection<Document> collection;

    public MongoProfileRepository(MongoDatabase database) {
        this.collection = database.getCollection("profiles");
    }

    @Override
    public Optional<PlayerProfile> find(UUID uuid) {
        Document document = collection.find(Filters.eq("_id", uuid.toString())).first();
        return Optional.ofNullable(document).map(MongoProfileRepository::toProfile);
    }

    @Override
    public void upsert(PlayerProfile profile) {
        collection.replaceOne(
                Filters.eq("_id", profile.getUuid().toString()),
                toDocument(profile),
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public EconomyResult incrementCurrency(UUID uuid, Currency currency, long delta, boolean allowNegative) {
        String field = fieldFor(currency);
        Bson idFilter = Filters.eq("_id", uuid.toString());
        Bson filter = allowNegative || delta >= 0
                ? idFilter
                : Filters.and(idFilter, Filters.gte(field, -delta));
        Document updated = collection.findOneAndUpdate(
                filter,
                Updates.inc(field, delta),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        if (updated != null) {
            return EconomyResult.success(updated.getLong(field));
        }
        Document existing = collection.find(idFilter).first();
        if (existing == null) {
            return EconomyResult.failure(EconomyResult.FailureReason.PROFILE_NOT_LOADED, 0L);
        }
        return EconomyResult.failure(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, existing.getLong(field));
    }

    private static String fieldFor(Currency currency) {
        return currency.name().toLowerCase(Locale.ROOT);
    }

    private static Document toDocument(PlayerProfile profile) {
        return new Document("_id", profile.getUuid().toString())
                .append("name", profile.getName())
                .append("firstJoin", profile.getFirstJoin().toEpochMilli())
                .append("lastSeen", profile.getLastSeen().toEpochMilli())
                .append("locale", profile.getLocale())
                .append("coins", profile.getCoins())
                .append("gems", profile.getGems())
                .append("level", profile.getLevel())
                .append("xp", profile.getXp())
                .append("settings", new Document(new HashMap<>(profile.getSettings())));
    }

    private static PlayerProfile toProfile(Document document) {
        Map<String, String> settings = new HashMap<>();
        Document settingsDocument = document.get("settings", Document.class);
        if (settingsDocument != null) {
            settingsDocument.forEach((key, value) -> settings.put(key, String.valueOf(value)));
        }
        return new PlayerProfile(
                UUID.fromString(document.getString("_id")),
                document.getString("name"),
                Instant.ofEpochMilli(document.getLong("firstJoin")),
                Instant.ofEpochMilli(document.getLong("lastSeen")),
                document.getString("locale"),
                document.getLong("coins"),
                document.getLong("gems"),
                document.getInteger("level"),
                document.getLong("xp"),
                settings
        );
    }
}
