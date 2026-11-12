package net.prismarin.shared.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatPeriod;

final class StatDocument {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    String player;
    String name;
    Map<String, Long> all;
    Map<String, Long> at;
    Bucket week;
    Bucket month;

    static final class Bucket {

        String id;
        Map<String, Long> values;
        Map<String, Long> at;

        static Bucket fresh(String id) {
            Bucket bucket = new Bucket();
            bucket.id = id;
            bucket.values = new LinkedHashMap<>();
            bucket.at = new LinkedHashMap<>();
            return bucket;
        }

        Bucket normalized() {
            values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
            at = at == null ? new LinkedHashMap<>() : new LinkedHashMap<>(at);
            return this;
        }
    }

    static StatDocument fresh(UUID player, String name) {
        StatDocument document = new StatDocument();
        document.player = player.toString();
        document.name = name == null ? "" : name;
        document.all = new LinkedHashMap<>();
        document.at = new LinkedHashMap<>();
        return document;
    }

    static StatDocument decode(String json, UUID player) {
        if (json == null || json.isBlank()) {
            return fresh(player, "");
        }
        StatDocument document;
        try {
            document = GSON.fromJson(json, StatDocument.class);
        } catch (JsonParseException e) {
            return fresh(player, "");
        }
        if (document == null) {
            return fresh(player, "");
        }
        document.player = player == null ? document.player : player.toString();
        document.name = document.name == null ? "" : document.name;
        document.all = document.all == null ? new LinkedHashMap<>() : new LinkedHashMap<>(document.all);
        document.at = document.at == null ? new LinkedHashMap<>() : new LinkedHashMap<>(document.at);
        document.week = document.week == null ? null : document.week.normalized();
        document.month = document.month == null ? null : document.month.normalized();
        return document;
    }

    static StatDocument decode(String json) {
        return decode(json, null);
    }

    String encode() {
        return GSON.toJson(this);
    }

    UUID uuid() {
        try {
            return player == null ? null : UUID.fromString(player);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    Bucket bucket(StatPeriod period, String id) {
        Bucket current = period == StatPeriod.WEEKLY ? week : month;
        if (current != null && id.equals(current.id)) {
            return current;
        }
        Bucket fresh = Bucket.fresh(id);
        if (period == StatPeriod.WEEKLY) {
            week = fresh;
        } else {
            month = fresh;
        }
        return fresh;
    }

    Map<String, Long> values(StatPeriod period, String id) {
        if (period == StatPeriod.ALL_TIME) {
            return all;
        }
        Bucket current = period == StatPeriod.WEEKLY ? week : month;
        return current != null && id.equals(current.id) ? current.values : Map.of();
    }

    Map<String, Long> times(StatPeriod period, String id) {
        if (period == StatPeriod.ALL_TIME) {
            return at;
        }
        Bucket current = period == StatPeriod.WEEKLY ? week : month;
        return current != null && id.equals(current.id) ? current.at : Map.of();
    }

    PlayerStats view(UUID uuid, String weekId, String monthId) {
        return new PlayerStats(uuid, name, all, values(StatPeriod.WEEKLY, weekId), values(StatPeriod.MONTHLY, monthId));
    }
}
