package net.prismarin.shared.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanPage;
import net.prismarin.api.social.ClanResult;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.ClanService;
import net.prismarin.api.social.SocialResult;
import net.prismarin.shared.social.ClanCodec;

public final class ClanCalls {

    public static final int MAX_PAGE_SIZE = 50;

    private final ProxyLink link;

    public ClanCalls(ProxyLink link) {
        this.link = link;
    }

    public CompletableFuture<Optional<Clan>> lookup(UUID player) {
        JsonObject body = new JsonObject();
        body.addProperty("player", player.toString());
        return link.call(NetworkCalls.Action.CLAN_LOOKUP, body).thenApply(ClanCalls::clan);
    }

    public CompletableFuture<Optional<Clan>> byId(UUID clanId) {
        JsonObject body = new JsonObject();
        body.addProperty("clan", clanId.toString());
        return link.call(NetworkCalls.Action.CLAN_GET, body).thenApply(ClanCalls::clan);
    }

    public CompletableFuture<Optional<Clan>> find(String nameOrTag) {
        JsonObject body = new JsonObject();
        body.addProperty("name", nameOrTag == null ? "" : nameOrTag);
        return link.call(NetworkCalls.Action.CLAN_FIND, body).thenApply(ClanCalls::clan);
    }

    public CompletableFuture<ClanPage> list(int page, int pageSize) {
        JsonObject body = new JsonObject();
        body.addProperty("page", Math.max(0, page));
        body.addProperty("size", Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize)));
        return link.call(NetworkCalls.Action.CLAN_LIST, body).thenApply(ClanCalls::page);
    }

    public CompletableFuture<SocialResult> act(String op, UUID actor, UUID target, String name, String tag,
                                               String value) {
        JsonObject body = new JsonObject();
        body.addProperty("op", op);
        body.addProperty("actor", actor.toString());
        put(body, "target", target);
        put(body, "name", name);
        put(body, "tag", tag);
        put(body, "value", value);
        return link.call(NetworkCalls.Action.CLAN_ACT, body).thenApply(ClanCalls::result)
                .exceptionally(error -> ProxyLink.noRoute(error) ? SocialResult.UNAVAILABLE : SocialResult.FAILED);
    }

    public CompletableFuture<ClanResult> manage(String op, UUID clanId, UUID player, String name, String tag,
                                                String value) {
        JsonObject body = new JsonObject();
        body.addProperty("op", op);
        put(body, "clan", clanId);
        put(body, "player", player);
        put(body, "name", name);
        put(body, "tag", tag);
        put(body, "value", value);
        return link.call(NetworkCalls.Action.CLAN_MANAGE, body)
                .thenApply(answer -> new ClanResult(result(answer), clan(answer)))
                .exceptionally(error -> ClanResult.of(ProxyLink.noRoute(error) ? SocialResult.UNAVAILABLE
                        : SocialResult.FAILED));
    }

    public static CompletableFuture<JsonObject> dispatch(ClanService clans, NetworkCalls.Action action,
                                                         JsonObject body) {
        return switch (action) {
            case CLAN_LOOKUP -> clans.lookup(ApiJson.uuid(body, "player")).thenApply(ClanCalls::single);
            case CLAN_GET -> clans.byId(ApiJson.uuid(body, "clan")).thenApply(ClanCalls::single);
            case CLAN_FIND -> clans.find(ApiJson.stringOr(body, "name", "")).thenApply(ClanCalls::single);
            case CLAN_LIST -> clans.list(ApiJson.number(body, "page", 0),
                    Math.max(1, Math.min(MAX_PAGE_SIZE, ApiJson.number(body, "size", 10))))
                    .thenApply(ClanCalls::listed);
            case CLAN_ACT -> act(clans, body).thenApply(ClanCalls::answer);
            case CLAN_MANAGE -> manage(clans, body);
            default -> throw new IllegalArgumentException("no clan call");
        };
    }

    private static CompletableFuture<SocialResult> act(ClanService clans, JsonObject body) {
        UUID actor = ApiJson.uuid(body, "actor");
        String value = ApiJson.stringOr(body, "value", "");
        return switch (ApiJson.string(body, "op")) {
            case "create" -> clans.create(actor, ApiJson.string(body, "name"), ApiJson.string(body, "tag"));
            case "invite" -> clans.invite(actor, ApiJson.uuid(body, "target"));
            case "join" -> clans.join(actor, ApiJson.uuid(body, "target"));
            case "leave" -> clans.leave(actor);
            case "kick" -> clans.kick(actor, ApiJson.uuid(body, "target"));
            case "promote" -> clans.promote(actor, ApiJson.uuid(body, "target"));
            case "demote" -> clans.demote(actor, ApiJson.uuid(body, "target"));
            case "transfer" -> clans.transfer(actor, ApiJson.uuid(body, "target"));
            case "disband" -> clans.disband(actor);
            case "color" -> clans.changeColor(actor, value);
            case "access" -> clans.changeAccess(actor, ClanAccess.byId(value)
                    .orElseThrow(() -> new IllegalArgumentException("unknown access")));
            case "chat" -> clans.chat(actor, value);
            default -> throw new IllegalArgumentException("unknown clan action");
        };
    }

    private static CompletableFuture<JsonObject> manage(ClanService clans, JsonObject body) {
        String value = ApiJson.stringOr(body, "value", null);
        String op = ApiJson.string(body, "op");
        if ("create".equals(op)) {
            return clans.createClan(ApiJson.uuid(body, "player"), ApiJson.string(body, "name"),
                    ApiJson.string(body, "tag")).thenApply(ClanCalls::answer);
        }
        UUID clan = ApiJson.uuid(body, "clan");
        CompletableFuture<ClanResult> result = switch (op) {
            case "rename" -> clans.renameClan(clan, optional(body, "name"), optional(body, "tag"));
            case "disband" -> clans.disbandClan(clan);
            case "add" -> clans.addMember(clan, ApiJson.uuid(body, "player"), role(value));
            case "remove" -> clans.removeMember(clan, ApiJson.uuid(body, "player"));
            case "role" -> clans.setRole(clan, ApiJson.uuid(body, "player"), role(value));
            case "color" -> clans.setColor(clan, value);
            case "access" -> clans.setAccess(clan, ClanAccess.byId(value)
                    .orElseThrow(() -> new IllegalArgumentException("unknown access")));
            case "data" -> clans.setData(clan, ApiJson.string(body, "name"), value);
            case "broadcast" -> clans.broadcast(clan, value == null ? "" : value).thenApply(ClanResult::of);
            default -> throw new IllegalArgumentException("unknown clan action");
        };
        return result.thenApply(ClanCalls::answer);
    }

    private static ClanRole role(String value) {
        return ClanRole.byId(value).orElseThrow(() -> new IllegalArgumentException("unknown clan role"));
    }

    private static String optional(JsonObject body, String key) {
        return ApiJson.stringOr(body, key, null);
    }

    private static JsonObject answer(SocialResult result) {
        JsonObject answer = new JsonObject();
        answer.addProperty("result", result.id());
        return answer;
    }

    private static JsonObject answer(ClanResult result) {
        JsonObject answer = answer(result.result());
        result.clan().ifPresent(clan -> answer.add("clan", ClanCodec.encode(clan)));
        return answer;
    }

    private static JsonObject single(Optional<Clan> clan) {
        JsonObject answer = new JsonObject();
        clan.ifPresent(found -> answer.add("clan", ClanCodec.encode(found)));
        return answer;
    }

    private static JsonObject listed(ClanPage page) {
        JsonArray all = new JsonArray();
        page.clans().forEach(clan -> all.add(ClanCodec.encode(clan, false)));
        JsonArray kept = NetworkCalls.within(all, NetworkCalls.BODY_BUDGET);
        JsonObject answer = new JsonObject();
        answer.add("clans", kept);
        answer.addProperty("page", page.page());
        answer.addProperty("pages", page.pages());
        answer.addProperty("total", page.total());
        answer.addProperty("truncated", kept.size() < all.size());
        return answer;
    }

    static Optional<Clan> clan(JsonObject answer) {
        return ClanCodec.decode(answer.get("clan"));
    }

    static SocialResult result(JsonObject answer) {
        return SocialResult.byId(ApiJson.stringOr(answer, "result", "")).orElse(SocialResult.FAILED);
    }

    static ClanPage page(JsonObject answer) {
        List<Clan> clans = new ArrayList<>();
        JsonElement raw = answer.get("clans");
        if (raw != null && raw.isJsonArray()) {
            raw.getAsJsonArray().forEach(entry -> ClanCodec.decode(entry).ifPresent(clans::add));
        }
        return new ClanPage(clans, ApiJson.number(answer, "page", 0), ApiJson.number(answer, "pages", 1),
                ApiJson.number(answer, "total", clans.size()));
    }

    private static void put(JsonObject body, String key, Object value) {
        if (value != null) {
            body.addProperty(key, value.toString());
        }
    }
}
