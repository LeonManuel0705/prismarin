package net.prismarin.velocity.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.social.SocialResult;
import net.prismarin.shared.social.PartyCodec;
import net.prismarin.velocity.admin.ModerationChannel;

public final class SocialChannel implements ModerationChannel.SocialHandler {

    public static final String QUERY = "social-query";
    public static final String ANSWER = "social-answer";
    public static final int MAX_FRIENDS = 250;

    private final SocialContext context;
    private final FriendService friends;
    private final PartyService party;
    private final SocialViews views;

    public SocialChannel(SocialContext context, FriendService friends, PartyService party, SocialViews views) {
        this.context = context;
        this.friends = friends;
        this.party = party;
        this.views = views;
    }

    @Override
    public void handle(Player player, JsonObject message) {
        Optional<SocialRequest> parsed = SocialRequest.parse(message);
        if (parsed.isEmpty()) {
            rejected(player, message);
            return;
        }
        SocialRequest request = parsed.get();
        perform(player, request)
                .exceptionally(error -> SocialResult.FAILED)
                .thenCompose(result -> context.supply(() -> views.build(player, request, result)))
                .thenAccept(view -> {
                    if (player.isActive()) {
                        context.channel().sendSocial(player, view);
                    }
                })
                .exceptionally(error -> null);
    }

    @Override
    public void query(Player carrier, JsonObject message) {
        int id = number(message.get("id"));
        Optional<UUID> subject = uuid(message.get("player"));
        String query = text(message.get("query")).orElse("");
        if (subject.isEmpty()) {
            return;
        }
        context.supply(() -> answer(query, subject.get(), uuid(message.get("other"))))
                .thenAccept(answer -> {
                    answer.addProperty("type", ANSWER);
                    answer.addProperty("id", id);
                    if (carrier.isActive()) {
                        context.channel().sendSocial(carrier, answer);
                    }
                })
                .exceptionally(error -> null);
    }

    private JsonObject answer(String query, UUID subject, Optional<UUID> other) {
        JsonObject answer = new JsonObject();
        switch (query) {
            case "party" -> {
                answer.addProperty("enabled", context.partyEnabled());
                if (context.partyEnabled()) {
                    party.parties().partyOf(subject).ifPresent(found ->
                            answer.add("party", PartyCodec.encode(party.feed().view(found))));
                }
            }
            case "friends" -> {
                answer.addProperty("enabled", context.friendsEnabled());
                if (context.friendsEnabled()) {
                    Map<UUID, Long> all = context.store().cached(subject).friends();
                    JsonArray list = new JsonArray();
                    all.entrySet().stream().limit(MAX_FRIENDS).forEach(entry -> {
                        JsonObject item = new JsonObject();
                        item.addProperty("uuid", entry.getKey().toString());
                        item.addProperty("name", context.nameOf(entry.getKey()));
                        item.addProperty("since", entry.getValue());
                        list.add(item);
                    });
                    answer.add("friends", list);
                    answer.addProperty("total", all.size());
                }
            }
            case "are-friends" -> {
                answer.addProperty("enabled", context.friendsEnabled());
                answer.addProperty("friends", context.friendsEnabled() && other.isPresent()
                        && context.store().cached(subject).friendsWith(other.get()));
            }
            default -> answer.addProperty("enabled", false);
        }
        return answer;
    }

    private void rejected(Player player, JsonObject message) {
        if (!message.has("id")) {
            return;
        }
        SocialRequest none = new SocialRequest(number(message.get("id")), SocialRequest.Action.VIEW, null, null, null,
                SocialRequest.View.NONE, 0, Map.of());
        context.supply(() -> views.build(player, none, SocialResult.FAILED))
                .thenAccept(view -> {
                    if (player.isActive()) {
                        context.channel().sendSocial(player, view);
                    }
                })
                .exceptionally(error -> null);
    }

    private CompletableFuture<SocialResult> perform(Player player, SocialRequest request) {
        UUID target = request.target();
        boolean friendsOn = context.friendsEnabled();
        boolean partyOn = context.partyEnabled();
        return switch (request.action()) {
            case HELLO, VIEW -> CompletableFuture.completedFuture(null);
            case FRIEND_ADD -> friendsOn ? addFromParty(player, target) : disabled();
            case FRIEND_REQUEST -> friendsOn ? friends.addAnywhere(player, target) : disabled();
            case FRIEND_ACCEPT -> friendsOn ? friends.accept(player, target) : disabled();
            case FRIEND_DENY -> friendsOn ? friends.deny(player, target) : disabled();
            case FRIEND_CANCEL -> friendsOn ? friends.cancel(player, target) : disabled();
            case FRIEND_REMOVE -> friendsOn ? friends.remove(player, target) : disabled();
            case FRIEND_JUMP -> friendsOn ? friends.jump(player, target) : disabled();
            case PARTY_INVITE -> partyOn ? party.invite(player, target) : disabled();
            case PARTY_ACCEPT -> partyOn ? party.accept(player, target) : disabled();
            case PARTY_DENY -> partyOn ? party.deny(player, target) : disabled();
            case PARTY_JOIN -> partyOn ? party.join(player, target) : disabled();
            case PARTY_LEAVE -> partyOn ? party.leave(player) : disabled();
            case PARTY_KICK -> partyOn ? party.kick(player, target) : disabled();
            case PARTY_PROMOTE -> partyOn ? party.promote(player, target) : disabled();
            case PARTY_DEMOTE -> partyOn ? party.demote(player, target) : disabled();
            case PARTY_TRANSFER -> partyOn ? party.transfer(player, target) : disabled();
            case PARTY_DISBAND -> partyOn ? party.disband(player) : disabled();
            case PARTY_WARP -> partyOn ? party.warp(player) : disabled();
            case PARTY_SETTING -> partyOn ? configure(player, request.options()) : disabled();
            case SETTING -> context.enabled(request.setting().feature())
                    ? friends.change(player, request.setting(), request.value()).thenApply(done -> SocialResult.SUCCESS)
                    : disabled();
        };
    }

    private CompletableFuture<SocialResult> configure(Player player, Map<PartyOption, String> options) {
        Map<PartyOption, String> usable = new EnumMap<>(PartyOption.class);
        options.forEach((option, value) -> {
            if (context.settings().party().options().contains(option)) {
                usable.put(option, value);
            }
        });
        if (usable.isEmpty()) {
            return CompletableFuture.completedFuture(SocialResult.UNCHANGED);
        }
        return party.configure(player, usable);
    }

    private CompletableFuture<SocialResult> addFromParty(Player player, UUID target) {
        boolean together = party.parties().partyOf(player.getUniqueId())
                .map(current -> current.member(target)).orElse(false);
        return together ? friends.add(player, target) : CompletableFuture.completedFuture(SocialResult.NOT_ALLOWED);
    }

    private static CompletableFuture<SocialResult> disabled() {
        return CompletableFuture.completedFuture(SocialResult.DISABLED);
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

    private static int number(JsonElement element) {
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
