package net.prismarin.core.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.SocialResult;

public record SocialView(int id, String view, Features features, Counts counts, FriendPage friends,
                         Requests requests, PartyView party, Candidates candidates, List<Setting> settings,
                         SocialResult result) {

    public static final UUID NOBODY = new UUID(0L, 0L);

    public record Features(boolean friends, boolean party, boolean messages) {

        public static final Features NONE = new Features(false, false, false);

        public boolean any() {
            return friends || party || messages;
        }
    }

    public record Counts(int friends, int requests, int party, int partyInvites) {
    }

    public record Friend(UUID uuid, String name, boolean online, String server, long lastSeen, boolean jump) {
    }

    public record FriendPage(int page, int pages, int total, int online, int limit, boolean canInvite,
                             List<Friend> entries) {

        public FriendPage {
            entries = List.copyOf(entries);
        }
    }

    public record Request(UUID uuid, String name, long at, long expires) {
    }

    public record Requests(List<Request> incoming, List<Request> outgoing, int incomingTotal) {

        public Requests {
            incoming = List.copyOf(incoming);
            outgoing = List.copyOf(outgoing);
        }
    }

    public record Person(UUID uuid, UUID ref, String name, boolean masked, boolean friend, boolean online,
                         String server, boolean leader, boolean self, int size, PartyRole role,
                         Permissions permissions) {

        public boolean showSkin() {
            return !masked && !NOBODY.equals(uuid);
        }
    }

    public record Permissions(boolean kick, boolean promote, boolean demote, boolean transfer) {

        public static final Permissions NONE = new Permissions(false, false, false, false);

        public boolean any() {
            return kick || promote || demote || transfer;
        }
    }

    public record PartyView(boolean inParty, boolean canInvite, boolean friendsEnabled, UUID leader,
                            boolean leaderSelf, int max, List<Person> members, List<Person> invited,
                            List<Person> incoming, PartyRole role, boolean canWarp, boolean canDisband,
                            boolean canSettings, boolean canRevoke, List<Setting> settings) {

        public PartyView {
            members = List.copyOf(members);
            invited = List.copyOf(invited);
            incoming = List.copyOf(incoming);
            settings = List.copyOf(settings);
        }
    }

    public record Candidate(UUID uuid, String name, String server) {
    }

    public record Candidates(int page, int pages, int total, List<Candidate> entries) {

        public Candidates {
            entries = List.copyOf(entries);
        }
    }

    public record Setting(String key, String value, String next, List<String> options) {

        public Setting {
            options = List.copyOf(options);
        }
    }

    public SocialView {
        settings = List.copyOf(settings);
    }

    public static SocialView parse(JsonObject message) {
        JsonObject features = object(message, "features");
        JsonObject counts = object(message, "counts");
        return new SocialView(integer(message, "id", 0), text(message, "view", "none"),
                new Features(bool(features, "friends"), bool(features, "party"), bool(features, "messages")),
                new Counts(integer(counts, "friends", 0), integer(counts, "requests", 0), integer(counts, "party", 0),
                        integer(counts, "party-invites", 0)),
                message.has("friends") ? friendPage(object(message, "friends")) : null,
                message.has("requests") ? requests(object(message, "requests")) : null,
                message.has("party") ? party(object(message, "party")) : null,
                message.has("candidates") ? candidates(object(message, "candidates")) : null,
                settings(message.get("settings")),
                SocialResult.byId(text(message, "result", "")).orElse(null));
    }

    private static FriendPage friendPage(JsonObject object) {
        List<Friend> entries = new ArrayList<>();
        for (JsonObject item : objects(object, "entries")) {
            entries.add(new Friend(uuid(item, "uuid"), text(item, "name", "?"), bool(item, "online"),
                    text(item, "server", ""), number(item, "last-seen"), bool(item, "jump")));
        }
        return new FriendPage(integer(object, "page", 0), Math.max(1, integer(object, "pages", 1)),
                integer(object, "total", entries.size()), integer(object, "online", 0), integer(object, "limit", 0),
                bool(object, "can-invite"), entries);
    }

    private static Requests requests(JsonObject object) {
        return new Requests(requestList(object, "incoming"), requestList(object, "outgoing"),
                integer(object, "incoming-total", 0));
    }

    private static List<Request> requestList(JsonObject object, String field) {
        List<Request> requests = new ArrayList<>();
        for (JsonObject item : objects(object, field)) {
            requests.add(new Request(uuid(item, "uuid"), text(item, "name", "?"), number(item, "at"),
                    number(item, "expires")));
        }
        return requests;
    }

    private static PartyView party(JsonObject object) {
        boolean leaderSelf = bool(object, "leader-self");
        PartyRole role = PartyRole.byId(text(object, "role", ""))
                .orElse(leaderSelf ? PartyRole.LEADER : PartyRole.MEMBER);
        return new PartyView(bool(object, "in-party"), bool(object, "can-invite"), bool(object, "friends"),
                object.has("leader") ? uuid(object, "leader") : NOBODY, leaderSelf,
                integer(object, "max", 0), people(object, "members"), people(object, "invited"),
                people(object, "incoming"), role, bool(object, "can-warp"), bool(object, "can-disband"),
                bool(object, "can-settings"), bool(object, "can-revoke"), settings(object.get("settings")));
    }

    private static List<Person> people(JsonObject object, String field) {
        List<Person> people = new ArrayList<>();
        for (JsonObject item : objects(object, field)) {
            UUID ref = item.has("ref") ? uuid(item, "ref") : uuid(item, "uuid");
            boolean leader = bool(item, "leader");
            PartyRole role = PartyRole.byId(text(item, "role", "")).orElse(leader ? PartyRole.LEADER : PartyRole.MEMBER);
            people.add(new Person(uuid(item, "uuid"), ref, text(item, "name", "?"), bool(item, "masked"),
                    bool(item, "friend"), bool(item, "online"), text(item, "server", ""), leader,
                    bool(item, "self"), integer(item, "size", 0), role,
                    new Permissions(bool(item, "can-kick"), bool(item, "can-promote"), bool(item, "can-demote"),
                            bool(item, "can-transfer"))));
        }
        return people;
    }

    private static Candidates candidates(JsonObject object) {
        List<Candidate> entries = new ArrayList<>();
        for (JsonObject item : objects(object, "entries")) {
            entries.add(new Candidate(uuid(item, "uuid"), text(item, "name", "?"), text(item, "server", "")));
        }
        return new Candidates(integer(object, "page", 0), Math.max(1, integer(object, "pages", 1)),
                integer(object, "total", entries.size()), entries);
    }

    private static List<Setting> settings(JsonElement element) {
        List<Setting> settings = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return settings;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject item = entry.getAsJsonObject();
            List<String> options = new ArrayList<>();
            JsonElement raw = item.get("options");
            if (raw != null && raw.isJsonArray()) {
                raw.getAsJsonArray().forEach(option -> options.add(option.getAsString()));
            }
            settings.add(new Setting(text(item, "key", ""), text(item, "value", ""), text(item, "next", ""), options));
        }
        return settings;
    }

    private static List<JsonObject> objects(JsonObject object, String field) {
        List<JsonObject> objects = new ArrayList<>();
        JsonElement element = object == null ? null : object.get(field);
        if (element == null || !element.isJsonArray()) {
            return objects;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (entry.isJsonObject()) {
                objects.add(entry.getAsJsonObject());
            }
        }
        return objects;
    }

    private static JsonObject object(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String text(JsonObject object, String field, String fallback) {
        JsonElement element = object == null ? null : object.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static int integer(JsonObject object, String field, int fallback) {
        JsonElement element = object == null ? null : object.get(field);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long number(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsLong() : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static UUID uuid(JsonObject object, String field) {
        try {
            return UUID.fromString(text(object, field, ""));
        } catch (IllegalArgumentException e) {
            return NOBODY;
        }
    }
}
