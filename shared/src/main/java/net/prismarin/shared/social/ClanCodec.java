package net.prismarin.shared.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAction;
import net.prismarin.api.social.ClanData;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;

public final class ClanCodec {

    public static final String NAME_KEY = "name-key";
    public static final String TAG_KEY = "tag-key";

    private ClanCodec() {
        throw new AssertionError();
    }

    public static JsonObject encode(Clan clan) {
        return encode(clan, true);
    }

    public static JsonObject encode(Clan clan, boolean invites) {
        JsonObject object = new JsonObject();
        object.addProperty("id", clan.id().toString());
        object.addProperty("name", clan.name());
        object.addProperty("tag", clan.tag());
        object.addProperty(NAME_KEY, key(clan.name()));
        object.addProperty(TAG_KEY, key(clan.tag()));
        object.addProperty("color", clan.color());
        object.addProperty("access", clan.access().id());
        object.addProperty("leader", clan.leader().toString());
        object.addProperty("created", clan.created());
        JsonArray members = new JsonArray();
        for (ClanMember member : clan.members()) {
            JsonObject item = new JsonObject();
            item.addProperty("uuid", member.uuid().toString());
            item.addProperty("name", member.name());
            item.addProperty("role", member.role().id());
            item.addProperty("joined", member.joined());
            members.add(item);
        }
        object.add("members", members);
        JsonObject data = new JsonObject();
        clan.data().forEach(data::addProperty);
        object.add("data", data);
        if (invites) {
            JsonArray invited = new JsonArray();
            clan.invited().forEach(uuid -> invited.add(uuid.toString()));
            object.add("invited", invited);
        }
        return object;
    }

    public static Optional<Clan> decode(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        try {
            JsonObject object = element.getAsJsonObject();
            Optional<UUID> id = uuid(object.get("id"));
            Optional<UUID> leader = uuid(object.get("leader"));
            Optional<String> name = text(object.get("name")).filter(value -> !value.isBlank());
            Optional<String> tag = text(object.get("tag")).filter(value -> !value.isBlank());
            if (id.isEmpty() || leader.isEmpty() || name.isEmpty() || tag.isEmpty()) {
                return Optional.empty();
            }
            List<ClanMember> members = new ArrayList<>();
            boolean leaderFound = false;
            JsonElement rawMembers = object.get("members");
            if (rawMembers != null && rawMembers.isJsonArray()) {
                for (JsonElement entry : rawMembers.getAsJsonArray()) {
                    if (!entry.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = entry.getAsJsonObject();
                    Optional<UUID> uuid = uuid(item.get("uuid"));
                    if (uuid.isEmpty() || members.stream().anyMatch(member -> member.uuid().equals(uuid.get()))) {
                        continue;
                    }
                    boolean isLeader = uuid.get().equals(leader.get());
                    ClanRole role = isLeader ? ClanRole.LEADER : text(item.get("role")).flatMap(ClanRole::byId)
                            .filter(found -> found != ClanRole.LEADER).orElse(ClanRole.MEMBER);
                    leaderFound |= isLeader;
                    members.add(new ClanMember(uuid.get(), text(item.get("name")).orElse(""), role,
                            number(item.get("joined"))));
                }
            }
            if (!leaderFound) {
                return Optional.empty();
            }
            Set<UUID> invited = new LinkedHashSet<>();
            JsonElement rawInvited = object.get("invited");
            if (rawInvited != null && rawInvited.isJsonArray()) {
                for (JsonElement entry : rawInvited.getAsJsonArray()) {
                    uuid(entry).ifPresent(invited::add);
                }
            }
            ClanAccess access = text(object.get("access")).flatMap(ClanAccess::byId).orElse(ClanAccess.INVITE_ONLY);
            String color = text(object.get("color")).flatMap(ClanColors::normalize).orElse(ClanColors.DEFAULT);
            return Optional.of(new Clan(id.get(), name.get(), tag.get(), color, access, leader.get(), members,
                    invited, data(object.get("data")), number(object.get("created"))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Map<String, String> data(JsonElement element) {
        Map<String, String> data = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) {
            return data;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            Optional<String> value = text(entry.getValue());
            if (data.size() < ClanData.MAX_KEYS && ClanData.validKey(entry.getKey()) && value.isPresent()
                    && ClanData.validValue(value.get())) {
                data.put(entry.getKey(), value.get());
            }
        }
        return data;
    }

    public static JsonObject encodeRights(ClanRights rights) {
        JsonObject object = new JsonObject();
        rights.minimum().forEach((action, role) -> object.addProperty(action.id(), role.id()));
        return object;
    }

    public static ClanRights decodeRights(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return ClanRights.DEFAULT;
        }
        Map<ClanAction, ClanRole> roles = new EnumMap<>(ClanAction.class);
        element.getAsJsonObject().entrySet().forEach(entry -> ClanAction.byId(entry.getKey())
                .ifPresent(action -> text(entry.getValue()).flatMap(ClanRole::byId)
                        .ifPresent(role -> roles.put(action, role))));
        return new ClanRights(roles);
    }

    public static JsonObject encodeDisplay(ClanTagFormat display) {
        JsonObject object = new JsonObject();
        object.addProperty("format", display.format());
        object.addProperty("position", display.position().id());
        object.addProperty("chat", display.chat());
        object.addProperty("tablist", display.tablist());
        return object;
    }

    public static ClanTagFormat decodeDisplay(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return ClanTagFormat.DEFAULT;
        }
        JsonObject object = element.getAsJsonObject();
        return new ClanTagFormat(text(object.get("format")).orElse(ClanTagFormat.DEFAULT_FORMAT),
                text(object.get("position")).flatMap(ClanTagFormat.Position::byId)
                        .orElse(ClanTagFormat.DEFAULT.position()),
                flag(object.get("chat"), true), flag(object.get("tablist"), true));
    }

    public static String key(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean flag(JsonElement element, boolean fallback) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : fallback;
    }

    private static long number(JsonElement element) {
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsLong() : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Optional<String> text(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? Optional.of(element.getAsString()) : Optional.empty();
    }

    private static Optional<UUID> uuid(JsonElement element) {
        try {
            return text(element).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
