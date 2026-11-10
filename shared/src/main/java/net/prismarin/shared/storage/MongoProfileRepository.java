package net.prismarin.shared.storage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    public List<UUID> ids() {
        List<UUID> ids = new ArrayList<>();
        collection.find().projection(new Document("_id", 1))
                .forEach(document -> ids.add(UUID.fromString(document.getString("_id"))));
        return ids;
    }

    @Override
    public Optional<PlayerProfile> find(UUID uuid) {
        Document document = collection.find(idFilter(uuid)).first();
        return Optional.ofNullable(document).map(MongoProfileRepository::toProfile);
    }

    @Override
    public void save(PlayerProfile profile) {
        collection.updateOne(
                idFilter(profile.getUuid()),
                Updates.combine(
                        Updates.set("name", profile.getName()),
                        Updates.set("firstJoin", profile.getFirstJoin().toEpochMilli()),
                        Updates.set("lastSeen", profile.getLastSeen().toEpochMilli()),
                        Updates.set("locale", profile.getLocale()),
                        Updates.set("level", profile.getLevel()),
                        Updates.set("xp", profile.getXp()),
                        Updates.set("settings", new Document(new HashMap<>(profile.getSettings()))),
                        Updates.setOnInsert("balances", new Document(balanceFields(profile)))
                ),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public EconomyResult incrementCurrency(UUID uuid, Currency currency, long delta, boolean allowNegative) {
        String field = fieldFor(currency);
        Bson filter = guardedFilter(uuid, field, delta, allowNegative);
        Document updated = collection.findOneAndUpdate(
                filter,
                Updates.inc(field, delta),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        if (updated != null) {
            return EconomyResult.success(balanceOf(updated, field));
        }
        if (delta > 0) {
            Document saturated = collection.findOneAndUpdate(
                    Filters.and(idFilter(uuid), Filters.gt(field, Long.MAX_VALUE - delta)),
                    Updates.set(field, Long.MAX_VALUE),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            );
            if (saturated != null) {
                return EconomyResult.success(Long.MAX_VALUE);
            }
        }
        Document existing = collection.find(idFilter(uuid)).first();
        if (existing == null) {
            return EconomyResult.failure(EconomyResult.FailureReason.PROFILE_NOT_LOADED, 0L);
        }
        return EconomyResult.failure(EconomyResult.FailureReason.INSUFFICIENT_FUNDS, balanceOf(existing, field));
    }

    @Override
    public EconomyResult setCurrency(UUID uuid, Currency currency, long value) {
        String field = fieldFor(currency);
        Document updated = collection.findOneAndUpdate(
                idFilter(uuid),
                Updates.set(field, value),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        if (updated == null) {
            return EconomyResult.failure(EconomyResult.FailureReason.PROFILE_NOT_LOADED, 0L);
        }
        return EconomyResult.success(balanceOf(updated, field));
    }

    private static Bson guardedFilter(UUID uuid, String field, long delta, boolean allowNegative) {
        Bson idFilter = idFilter(uuid);
        if (delta > 0) {
            return Filters.and(idFilter, Filters.or(
                    Filters.exists(field, false),
                    Filters.lte(field, Long.MAX_VALUE - delta)));
        }
        if (allowNegative) {
            return idFilter;
        }
        return Filters.and(idFilter, Filters.gte(field, -delta));
    }

    private static Bson idFilter(UUID uuid) {
        return Filters.eq("_id", uuid.toString());
    }

    private static String fieldFor(Currency currency) {
        return "balances." + currency.id();
    }

    private static long balanceOf(Document document, String field) {
        Object value = document.getEmbedded(List.of(field.split("\\.")), Object.class);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Map<String, Object> balanceFields(PlayerProfile profile) {
        Map<String, Object> fields = new HashMap<>();
        profile.balances().forEach((currency, value) -> fields.put(currency.id(), value));
        return fields;
    }

    private static Map<Currency, Long> readBalances(Document document) {
        Map<Currency, Long> balances = new HashMap<>();
        Document stored = document.get("balances", Document.class);
        if (stored != null) {
            stored.forEach((id, value) -> {
                if (value instanceof Number number) {
                    balances.put(Currency.of(id), number.longValue());
                }
            });
        }
        return balances;
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
                readBalances(document),
                document.getInteger("level"),
                document.getLong("xp"),
                settings
        );
    }
}
