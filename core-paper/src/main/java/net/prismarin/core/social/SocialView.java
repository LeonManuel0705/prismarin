package net.prismarin.core.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.SocialResult;
import net.prismarin.shared.social.ClanCodec;
import net.prismarin.shared.social.ClanTagFormat;

public record SocialView(int id, String view, Features features, Counts counts, FriendPage friends,
                         Requests requests, PartyView party, Candidates candidates, List<Setting> settings,
                         SocialResult result, Map<String, Set<String>> commands, ClanView clan,
                         ClanTagFormat clanDisplay) {

    public static final UUID NOBODY = new UUID(0L, 0L);

    public record Features(boolean friends, boolean party, boolean messages, boolean clans, boolean settings) {

        public static final Features NONE = new Features(false, false, false, false, true);

        public Features(boolean friends, boolean party, boolean messages) {
            this(friends, party, messages, false, true);
        }

        public boolean any() {
            return friends || party || messages || clans;
        }
    }

    public record Counts(int friends, int requests, int party, int partyInvites, int clan, int clanInvites) {
    }

    public record ClanBadge(UUID id, String name, String tag, String color, int size) {
    }

    public record Friend(UUID uuid, String name, boolean online, String server, long lastSeen, boolean jump,
                         ClanBadge clan) {

        public Friend(UUID uuid, String name, boolean online, String server, long lastSeen, boolean jump) {
            this(uuid, name, online, server, lastSeen, jump, null);
        }
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
                         Permissions permissions, ClanBadge clan) {

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

    public record ClanMemberView(Person person, ClanRole role) {
    }

    public record ClanView(boolean inClan, boolean canCreate, ClanBadge badge, ClanAccess access, long created,
                           int max, ClanRole role, boolean canInvite, boolean canSettings, boolean canDisband,
                           boolean canLeave, List<ClanMemberView> members, List<Person> invited,
                           List<ClanBadge> incoming) {

        public ClanView {
            members = List.copyOf(members);
            invited = List.copyOf(invited);
            incoming = List.copyOf(incoming);
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
        commands = commands == null ? Map.of() : Map.copyOf(commands);
    }

    public boolean enabled(String feature, String action) {
        Set<String> actions = commands.get(feature);
        return actions == null || actions.contains(action);
    }

    public static SocialView parse(JsonObject message) {
        JsonObject features = object(message, "features");
        JsonObject counts = object(message, "counts");
        return new SocialView(integer(message, "id", 0), text(message, "view", "none"),
                new Features(bool(features, "friends"), bool(features, "party"), bool(features, "messages"),
                        bool(features, "clans"), !features.has("settings") || bool(features, "settings")),
                new Counts(integer(counts, "friends", 0), integer(counts, "requests", 0), integer(counts, "party", 0),
                        integer(counts, "party-invites", 0), integer(counts, "clan", 0),
                        integer(counts, "clan-invites", 0)),
                message.has("friends") ? friendPage(object(message, "friends")) : null,
                message.has("requests") ? requests(object(message, "requests")) : null,
                message.has("party") ? party(object(message, "party")) : null,
                message.has("candidates") ? candidates(object(message, "candidates")) : null,
                settings(message.get("settings")),
                SocialResult.byId(text(message, "result", "")).orElse(null),
                commands(message.get("commands")),
                message.has("clan") ? clan(object(message, "clan")) : null,
                message.has("clan-display") ? ClanCodec.decodeDisplay(message.get("clan-display")) : null);
    }

    private static Map<String, Set<String>> commands(JsonElement element) {
        Map<String, Set<String>> commands = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) {
            return commands;
        }
        element.getAsJsonObject().entrySet().forEach(entry -> {
            Set<String> actions = new LinkedHashSet<>();
            if (entry.getValue().isJsonArray()) {
                entry.getValue().getAsJsonArray().forEach(action -> {
                    if (action.isJsonPrimitive()) {
                        actions.add(action.getAsString());
                    }
                });
            }
            commands.put(entry.getKey(), actions);
        });
        return commands;
    }

    private static ClanBadge badge(JsonObject object) {
        if (object == null || !object.has("tag")) {
            return null;
        }
        return new ClanBadge(uuid(object, "id"), text(object, "name", "?"), text(object, "tag", "?"),
                text(object, "color", ""), integer(object, "size", 0));
    }

    private static ClanView clan(JsonObject object) {
        List<ClanMemberView> members = new ArrayList<>();
        List<Person> people = people(object, "members");
        List<JsonObject> raw = objects(object, "members");
        for (int i = 0; i < people.size(); i++) {
            members.add(new ClanMemberView(people.get(i),
                    ClanRole.byId(text(raw.get(i), "role", "")).orElse(ClanRole.MEMBER)));
        }
        List<ClanBadge> incoming = new ArrayList<>();
        for (JsonObject item : objects(object, "incoming")) {
            ClanBadge badge = badge(item);
            if (badge != null) {
                incoming.add(badge);
            }
        }
        return new ClanView(bool(object, "in-clan"), bool(object, "can-create"),
                object.has("badge") ? badge(object(object, "badge")) : null,
                ClanAccess.byId(text(object, "access", "")).orElse(ClanAccess.INVITE_ONLY),
                number(object, "created"), integer(object, "max", 0),
                ClanRole.byId(text(object, "role", "")).orElse(ClanRole.MEMBER), bool(object, "can-invite"),
                bool(object, "can-settings"), bool(object, "can-disband"), bool(object, "can-leave"), members,
                people(object, "invited"), incoming);
    }

    private static FriendPage friendPage(JsonObject object) {
        List<Friend> entries = new ArrayList<>();
        for (JsonObject item : objects(object, "entries")) {
            entries.add(new Friend(uuid(item, "uuid"), text(item, "name", "?"), bool(item, "online"),
                    text(item, "server", ""), number(item, "last-seen"), bool(item, "jump"),
                    item.has("clan") ? badge(object(item, "clan")) : null));
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
                            bool(item, "can-transfer")), item.has("clan") ? badge(object(item, "clan")) : null));
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
