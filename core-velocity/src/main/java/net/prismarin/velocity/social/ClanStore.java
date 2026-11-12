package net.prismarin.velocity.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.social.Clan;
import net.prismarin.shared.social.ClanCodec;
import net.prismarin.shared.storage.DocumentStore;

public final class ClanStore {

    public static final String COLLECTION = "clans";
    public static final String CHANNEL = "prismarin:clans";

    private final DocumentStore documents;
    private final MessageBus bus;
    private final String origin = UUID.randomUUID().toString();
    private final Object lock = new Object();

    public ClanStore(DocumentStore documents, MessageBus bus) {
        this.documents = documents;
        this.bus = bus;
    }

    public List<Clan> loadAll() {
        List<Clan> clans = new ArrayList<>();
        for (String json : documents.findAll(COLLECTION)) {
            parse(json).ifPresent(clans::add);
        }
        return clans;
    }

    public Optional<Clan> load(UUID id) {
        return documents.find(COLLECTION, id.toString()).flatMap(ClanStore::parse);
    }

    public List<Clan> conflicts(String name, String tag) {
        List<Clan> found = new ArrayList<>();
        for (String field : List.of(ClanCodec.NAME_KEY, ClanCodec.TAG_KEY)) {
            for (String value : List.of(ClanCodec.key(name), ClanCodec.key(tag))) {
                for (String json : documents.findBy(COLLECTION, field, value)) {
                    parse(json).filter(clan -> found.stream().noneMatch(known -> known.id().equals(clan.id())))
                            .ifPresent(found::add);
                }
            }
        }
        return found;
    }

    public void persist(UUID id, Supplier<Optional<Clan>> current) {
        synchronized (lock) {
            Optional<Clan> clan = current.get();
            if (clan.isPresent()) {
                documents.save(COLLECTION, id.toString(), ClanCodec.encode(clan.get(), false).toString());
            } else {
                documents.delete(COLLECTION, id.toString());
            }
        }
        announce(id);
    }

    public void listen(Consumer<UUID> changed) {
        if (bus == null) {
            return;
        }
        bus.subscribe(CHANNEL, message -> {
            try {
                JsonObject payload = JsonParser.parseString(message.payloadJson()).getAsJsonObject();
                JsonElement sender = payload.get("origin");
                if (sender != null && origin.equals(sender.getAsString())) {
                    return;
                }
                changed.accept(UUID.fromString(payload.get("clan").getAsString()));
            } catch (RuntimeException ignored) {
                return;
            }
        });
    }

    private void announce(UUID id) {
        if (bus == null) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("clan", id.toString());
        payload.addProperty("origin", origin);
        try {
            bus.publish(CHANNEL, payload.toString());
        } catch (RuntimeException ignored) {
            return;
        }
    }

    static Optional<Clan> parse(String json) {
        try {
            return ClanCodec.decode(JsonParser.parseString(json));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
