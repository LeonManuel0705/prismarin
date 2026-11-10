package net.prismarin.shared.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyAccess;
import net.prismarin.api.social.PartyMember;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;

public final class PartyCodec {

    private PartyCodec() {
        throw new AssertionError();
    }

    public static JsonObject encode(Party party) {
        JsonObject object = new JsonObject();
        object.addProperty("id", party.id().toString());
        object.addProperty("leader", party.leader().toString());
        JsonArray members = new JsonArray();
        for (PartyMember member : party.members()) {
            JsonObject item = new JsonObject();
            item.addProperty("uuid", member.uuid().toString());
            item.addProperty("name", member.name());
            item.addProperty("role", member.role().id());
            members.add(item);
        }
        object.add("members", members);
        JsonArray invited = new JsonArray();
        party.invited().forEach(uuid -> invited.add(uuid.toString()));
        object.add("invited", invited);
        object.add("settings", encodeSettings(party.settings()));
        return object;
    }

    public static JsonObject encodeSettings(PartySettings settings) {
        JsonObject object = new JsonObject();
        object.addProperty("invite", settings.invite().id());
        object.addProperty("access", settings.access().id());
        object.addProperty("follow", settings.follow());
        object.addProperty("chat", settings.chat().id());
        return object;
    }

    public static Optional<Party> decode(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        try {
            JsonObject object = element.getAsJsonObject();
            Optional<UUID> id = uuid(object.get("id"));
            Optional<UUID> leader = uuid(object.get("leader"));
            if (id.isEmpty() || leader.isEmpty()) {
                return Optional.empty();
            }
            List<PartyMember> members = new ArrayList<>();
            JsonElement rawMembers = object.get("members");
            if (rawMembers != null && rawMembers.isJsonArray()) {
                for (JsonElement entry : rawMembers.getAsJsonArray()) {
                    if (!entry.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = entry.getAsJsonObject();
                    Optional<UUID> uuid = uuid(item.get("uuid"));
                    if (uuid.isEmpty()) {
                        continue;
                    }
                    PartyRole role = text(item.get("role")).flatMap(PartyRole::byId)
                            .orElse(uuid.get().equals(leader.get()) ? PartyRole.LEADER : PartyRole.MEMBER);
                    members.add(new PartyMember(uuid.get(), text(item.get("name")).orElse(""), role));
                }
            }
            Set<UUID> invited = new LinkedHashSet<>();
            JsonElement rawInvited = object.get("invited");
            if (rawInvited != null && rawInvited.isJsonArray()) {
                for (JsonElement entry : rawInvited.getAsJsonArray()) {
                    uuid(entry).ifPresent(invited::add);
                }
            }
            return Optional.of(new Party(id.get(), leader.get(), members, invited,
                    decodeSettings(object.get("settings"), PartySettings.DEFAULT)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static PartySettings decodeSettings(JsonElement element, PartySettings fallback) {
        if (element == null || !element.isJsonObject()) {
            return fallback;
        }
        JsonObject object = element.getAsJsonObject();
        PartyRole invite = text(object.get("invite")).flatMap(PartyRole::byId).orElse(fallback.invite());
        PartyAccess access = text(object.get("access")).flatMap(PartyAccess::byId).orElse(fallback.access());
        JsonElement follow = object.get("follow");
        boolean following = follow != null && follow.isJsonPrimitive() && follow.getAsJsonPrimitive().isBoolean()
                ? follow.getAsBoolean() : fallback.follow();
        PartyRole chat = text(object.get("chat")).flatMap(PartyRole::byId).orElse(fallback.chat());
        return new PartySettings(invite, access, following, chat);
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
