package net.prismarin.shared.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAccessChangedEvent;
import net.prismarin.api.social.ClanChatEvent;
import net.prismarin.api.social.ClanColorChangedEvent;
import net.prismarin.api.social.ClanCreatedEvent;
import net.prismarin.api.social.ClanDataChangedEvent;
import net.prismarin.api.social.ClanDisbandedEvent;
import net.prismarin.api.social.ClanInviteEvent;
import net.prismarin.api.social.ClanMemberJoinedEvent;
import net.prismarin.api.social.ClanMemberLeftEvent;
import net.prismarin.api.social.ClanRenamedEvent;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.ClanRoleChangedEvent;

public final class ClanEvents {

    public static final String PREFIX = "clan-";

    private ClanEvents() {
        throw new AssertionError();
    }

    public static Optional<JsonObject> encode(PrismarinEvent event) {
        JsonObject message = new JsonObject();
        Clan clan;
        switch (event) {
            case ClanCreatedEvent created -> {
                clan = created.clan();
                message.addProperty("event", "clan-created");
            }
            case ClanDisbandedEvent disbanded -> {
                clan = disbanded.clan();
                message.addProperty("event", "clan-disbanded");
            }
            case ClanRenamedEvent renamed -> {
                clan = renamed.clan();
                message.addProperty("event", "clan-renamed");
                message.addProperty("previous-name", renamed.previousName());
                message.addProperty("previous-tag", renamed.previousTag());
            }
            case ClanMemberJoinedEvent joined -> {
                clan = joined.clan();
                message.addProperty("event", "clan-joined");
                message.addProperty("player", joined.player().toString());
            }
            case ClanMemberLeftEvent left -> {
                clan = left.clan();
                message.addProperty("event", "clan-left");
                message.addProperty("player", left.player().toString());
                message.addProperty("reason", left.reason().id());
            }
            case ClanRoleChangedEvent role -> {
                clan = role.clan();
                message.addProperty("event", "clan-role");
                message.addProperty("player", role.player().toString());
                message.addProperty("previous", role.previous().id());
                message.addProperty("current", role.current().id());
            }
            case ClanColorChangedEvent color -> {
                clan = color.clan();
                message.addProperty("event", "clan-color");
                message.addProperty("previous", color.previous());
            }
            case ClanAccessChangedEvent access -> {
                clan = access.clan();
                message.addProperty("event", "clan-access");
                message.addProperty("previous", access.previous().id());
            }
            case ClanDataChangedEvent data -> {
                clan = data.clan();
                message.addProperty("event", "clan-data");
                message.addProperty("key", data.key());
                if (data.previous() != null) {
                    message.addProperty("previous", data.previous());
                }
                if (data.value() != null) {
                    message.addProperty("value", data.value());
                }
            }
            case ClanInviteEvent invite -> {
                clan = invite.clan();
                message.addProperty("event", "clan-invite");
                message.addProperty("inviter", invite.inviter().toString());
                message.addProperty("target", invite.target().toString());
            }
            case ClanChatEvent chat -> {
                clan = chat.clan();
                message.addProperty("event", "clan-chat");
                message.addProperty("sender", chat.sender().toString());
                message.addProperty("message", chat.message());
            }
            default -> {
                return Optional.empty();
            }
        }
        message.add("clan", ClanCodec.encode(clan, false));
        return Optional.of(message);
    }

    public static Optional<PrismarinEvent> decode(JsonObject message) {
        try {
            String event = text(message.get("event"));
            if (!event.startsWith(PREFIX)) {
                return Optional.empty();
            }
            Optional<Clan> decoded = ClanCodec.decode(message.get("clan"));
            if (decoded.isEmpty()) {
                return Optional.empty();
            }
            Clan clan = decoded.get();
            PrismarinEvent result = switch (event) {
                case "clan-created" -> new ClanCreatedEvent(clan);
                case "clan-disbanded" -> new ClanDisbandedEvent(clan);
                case "clan-renamed" -> new ClanRenamedEvent(clan, text(message.get("previous-name")),
                        text(message.get("previous-tag")));
                case "clan-joined" -> new ClanMemberJoinedEvent(clan, uuid(message.get("player")));
                case "clan-left" -> new ClanMemberLeftEvent(clan, uuid(message.get("player")),
                        ClanMemberLeftEvent.Reason.byId(text(message.get("reason")))
                                .orElse(ClanMemberLeftEvent.Reason.LEFT));
                case "clan-role" -> new ClanRoleChangedEvent(clan, uuid(message.get("player")),
                        ClanRole.byId(text(message.get("previous"))).orElseThrow(),
                        ClanRole.byId(text(message.get("current"))).orElseThrow());
                case "clan-color" -> new ClanColorChangedEvent(clan, text(message.get("previous")));
                case "clan-access" -> new ClanAccessChangedEvent(clan,
                        ClanAccess.byId(text(message.get("previous"))).orElse(ClanAccess.INVITE_ONLY));
                case "clan-data" -> new ClanDataChangedEvent(clan, text(message.get("key")),
                        optional(message.get("previous")), optional(message.get("value")));
                case "clan-invite" -> new ClanInviteEvent(clan, uuid(message.get("inviter")),
                        uuid(message.get("target")));
                case "clan-chat" -> new ClanChatEvent(clan, uuid(message.get("sender")),
                        text(message.get("message")));
                default -> null;
            };
            return Optional.ofNullable(result);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String optional(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static String text(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    private static UUID uuid(JsonElement element) {
        return UUID.fromString(text(element));
    }
}
