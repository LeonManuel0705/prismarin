package net.prismarin.velocity.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAction;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.PartyAction;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import net.prismarin.api.social.SocialResult;
import net.prismarin.shared.social.ClanCodec;

public final class SocialViews {

    public static final int PAGE_SIZE = 28;
    private static final int MAX_REQUESTS = 21;
    private static final int MAX_OUTGOING = 7;
    private static final int MAX_MEMBERS = 45;

    private final SocialContext context;
    private final FriendService friends;
    private final PartyService party;
    private final ClanService clans;

    public SocialViews(SocialContext context, FriendService friends, PartyService party, ClanService clans) {
        this.context = context;
        this.friends = friends;
        this.party = party;
        this.clans = clans;
    }

    public JsonObject build(Player viewer, SocialRequest request, SocialResult result) {
        JsonObject view = new JsonObject();
        view.addProperty("type", "social-view");
        view.addProperty("id", request.id());
        view.addProperty("view", request.view().id());
        if (result != null) {
            view.addProperty("result", result.id());
        }
        JsonObject features = new JsonObject();
        features.addProperty("friends", context.friendsEnabled());
        features.addProperty("party", context.partyEnabled());
        features.addProperty("messages", context.messagesEnabled());
        features.addProperty("clans", clans != null);
        features.addProperty("settings", context.personalSettings());
        view.add("features", features);
        view.add("commands", commands(viewer));
        if (clans != null) {
            view.add("clan-display", ClanCodec.encodeDisplay(context.settings().clans().tag()));
        }
        JsonObject counts = new JsonObject();
        if (context.friendsEnabled()) {
            SocialProfile profile = context.store().cached(viewer.getUniqueId());
            counts.addProperty("friends", profile.friendIds().size());
            counts.addProperty("requests", friends.incoming(viewer).size());
        }
        if (context.partyEnabled()) {
            Optional<PartyManager.Party> current = party.parties().partyOf(viewer.getUniqueId());
            counts.addProperty("party", current.map(PartyManager.Party::size).orElse(0));
            counts.addProperty("party-invites",
                    party.parties().invitesFor(viewer.getUniqueId(), System.currentTimeMillis()).size());
        }
        if (clans != null) {
            counts.addProperty("clan", clans.clans().clanOf(viewer.getUniqueId()).map(Clan::size).orElse(0));
            counts.addProperty("clan-invites",
                    clans.clans().invitesFor(viewer.getUniqueId(), System.currentTimeMillis()).size());
        }
        view.add("counts", counts);
        switch (request.view()) {
            case FRIENDS -> {
                if (context.friendsEnabled()) {
                    view.add("friends", friendsPage(viewer, request.page()));
                }
            }
            case REQUESTS -> {
                if (context.friendsEnabled()) {
                    view.add("requests", requests(viewer));
                }
            }
            case PARTY -> {
                if (context.partyEnabled()) {
                    view.add("party", party(viewer));
                }
            }
            case PARTY_INVITE -> {
                if (context.partyEnabled() && context.friendsEnabled()) {
                    view.add("candidates", candidates(viewer, request.page()));
                }
            }
            case SETTINGS -> view.add("settings", settings(viewer));
            case CLAN -> {
                if (clans != null) {
                    view.add("clan", clan(viewer));
                }
            }
            case NONE -> {
            }
        }
        return view;
    }

    private JsonObject friendsPage(Player viewer, int requested) {
        List<FriendService.Entry> entries = friends.entries(viewer);
        Page<FriendService.Entry> page = Page.of(entries, requested, PAGE_SIZE);
        JsonObject object = new JsonObject();
        object.addProperty("page", page.page());
        object.addProperty("pages", page.pages());
        object.addProperty("total", page.total());
        object.addProperty("online", entries.stream().filter(FriendService.Entry::online).count());
        object.addProperty("limit", context.friendLimit(viewer.getUniqueId()));
        object.addProperty("can-invite", context.partyEnabled() && canInvite(viewer));
        JsonArray array = new JsonArray();
        for (FriendService.Entry entry : page.entries()) {
            JsonObject item = new JsonObject();
            item.addProperty("uuid", entry.uuid().toString());
            item.addProperty("name", entry.name());
            item.addProperty("online", entry.online());
            item.addProperty("server", entry.server());
            item.addProperty("last-seen", entry.lastSeen());
            item.addProperty("jump", entry.jump() && context.available(SocialSetting.Feature.FRIENDS, "jump"));
            badge(viewer, "friends", entry.uuid()).ifPresent(badge -> item.add("clan", badge));
            array.add(item);
        }
        object.add("entries", array);
        return object;
    }

    private JsonObject requests(Player viewer) {
        JsonObject object = new JsonObject();
        object.add("incoming", requestArray(friends.incoming(viewer), MAX_REQUESTS));
        object.add("outgoing", requestArray(friends.outgoing(viewer), MAX_OUTGOING));
        object.addProperty("incoming-total", friends.incoming(viewer).size());
        return object;
    }

    private static JsonArray requestArray(List<FriendService.Request> requests, int limit) {
        JsonArray array = new JsonArray();
        for (FriendService.Request request : requests.subList(0, Math.min(limit, requests.size()))) {
            JsonObject item = new JsonObject();
            item.addProperty("uuid", request.uuid().toString());
            item.addProperty("name", request.name());
            item.addProperty("at", request.at());
            item.addProperty("expires", request.expires());
            array.add(item);
        }
        return array;
    }

    private JsonObject party(Player viewer) {
        JsonObject object = new JsonObject();
        Set<UUID> viewerFriends = context.friendsOf(viewer);
        Optional<PartyManager.Party> current = party.parties().partyOf(viewer.getUniqueId());
        object.addProperty("in-party", current.isPresent());
        object.addProperty("can-invite", canInvite(viewer));
        object.addProperty("friends", context.friendsEnabled());
        if (current.isPresent()) {
            PartyManager.Party value = current.get();
            UUID self = viewer.getUniqueId();
            object.addProperty("leader", value.leader().toString());
            object.addProperty("leader-self", value.leader(self));
            object.addProperty("max", context.partyLimit(value.leader()));
            object.addProperty("role", value.role(self).id());
            object.addProperty("can-warp", value.allowed(self, PartyAction.WARP, null) && partyOn("warp"));
            object.addProperty("can-disband", value.allowed(self, PartyAction.DISBAND, null) && partyOn("disband"));
            object.addProperty("can-settings", value.allowed(self, PartyAction.SETTINGS, null)
                    && partyOn("settings"));
            object.addProperty("can-revoke", value.allowed(self, PartyAction.REVOKE, null) && partyOn("kick"));
            object.add("settings", partySettings(value.settings()));
            JsonArray members = new JsonArray();
            for (UUID member : ordered(value).subList(0, Math.min(MAX_MEMBERS, value.members().size()))) {
                Optional<Player> online = context.proxy().getPlayer(member)
                        .filter(found -> context.visibleTo(viewer, found));
                JsonObject item = person(viewer, viewerFriends, member);
                item.addProperty("online", online.isPresent());
                item.addProperty("server", online.map(context::serverOf).orElse(""));
                item.addProperty("leader", value.leader(member));
                item.addProperty("self", member.equals(self));
                item.addProperty("role", value.role(member).id());
                item.addProperty("can-kick", value.allowed(self, PartyAction.KICK, member) && partyOn("kick"));
                item.addProperty("can-promote", value.allowed(self, PartyAction.PROMOTE, member)
                        && partyOn("promote"));
                item.addProperty("can-demote", value.allowed(self, PartyAction.DEMOTE, member)
                        && partyOn("demote"));
                item.addProperty("can-transfer", value.allowed(self, PartyAction.TRANSFER, member)
                        && partyOn("transfer"));
                badge(viewer, "party", member).ifPresent(badge -> item.add("clan", badge));
                members.add(item);
            }
            object.add("members", members);
            JsonArray invited = new JsonArray();
            value.invites().keySet().forEach(uuid -> invited.add(person(viewer, viewerFriends, uuid)));
            object.add("invited", invited);
        } else {
            object.addProperty("max", context.partyLimit(viewer.getUniqueId()));
        }
        JsonArray incoming = new JsonArray();
        for (PartyManager.Party invite : party.parties().invitesFor(viewer.getUniqueId(), System.currentTimeMillis())) {
            JsonObject item = person(viewer, viewerFriends, invite.leader());
            item.addProperty("size", invite.size());
            incoming.add(item);
        }
        object.add("incoming", incoming);
        return object;
    }

    static List<UUID> ordered(PartyManager.Party party) {
        List<UUID> ordered = new ArrayList<>();
        for (PartyRole role : List.of(PartyRole.LEADER, PartyRole.MODERATOR, PartyRole.MEMBER)) {
            for (UUID member : party.members()) {
                if (party.role(member) == role) {
                    ordered.add(member);
                }
            }
        }
        return ordered;
    }

    private JsonArray partySettings(PartySettings settings) {
        JsonArray array = new JsonArray();
        for (PartyOption option : context.settings().party().options()) {
            JsonObject item = new JsonObject();
            String value = option.value(settings);
            item.addProperty("key", option.id());
            item.addProperty("value", value);
            item.addProperty("next", option.next(value));
            JsonArray options = new JsonArray();
            option.options().forEach(options::add);
            item.add("options", options);
            array.add(item);
        }
        return array;
    }

    private JsonObject person(Player viewer, Set<UUID> viewerFriends, UUID subject) {
        JsonObject item = new JsonObject();
        boolean masked = context.masked(viewer, viewerFriends, subject);
        item.addProperty("uuid", masked ? new UUID(0L, 0L).toString() : subject.toString());
        item.addProperty("ref", subject.toString());
        item.addProperty("name", context.visibleName(viewer, viewerFriends, subject, context.nameOf(subject)));
        item.addProperty("masked", masked);
        item.addProperty("friend", viewerFriends.contains(subject));
        return item;
    }

    private JsonObject candidates(Player viewer, int requested) {
        Optional<PartyManager.Party> current = party.parties().partyOf(viewer.getUniqueId());
        List<FriendService.Entry> online = new ArrayList<>();
        for (FriendService.Entry entry : friends.entries(viewer)) {
            if (!entry.online()) {
                continue;
            }
            boolean taken = current.map(value -> value.member(entry.uuid()) || value.invites().containsKey(entry.uuid()))
                    .orElse(false);
            if (!taken && party.parties().partyOf(entry.uuid()).isEmpty()) {
                online.add(entry);
            }
        }
        Page<FriendService.Entry> page = Page.of(online, requested, PAGE_SIZE);
        JsonObject object = new JsonObject();
        object.addProperty("page", page.page());
        object.addProperty("pages", page.pages());
        object.addProperty("total", page.total());
        JsonArray array = new JsonArray();
        for (FriendService.Entry entry : page.entries()) {
            JsonObject item = new JsonObject();
            item.addProperty("uuid", entry.uuid().toString());
            item.addProperty("name", entry.name());
            item.addProperty("server", entry.server());
            array.add(item);
        }
        object.add("entries", array);
        return object;
    }

    private JsonArray settings(Player viewer) {
        SocialProfile profile = context.store().cached(viewer.getUniqueId());
        JsonArray array = new JsonArray();
        if (!context.personalSettings()) {
            return array;
        }
        for (SocialSetting setting : SocialSetting.values()) {
            if (!context.settingVisible(setting)) {
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("key", setting.id());
            item.addProperty("value", context.setting(profile, setting));
            item.addProperty("next", setting.next(context.setting(profile, setting)));
            JsonArray options = new JsonArray();
            setting.options().forEach(options::add);
            item.add("options", options);
            array.add(item);
        }
        return array;
    }

    private boolean partyOn(String action) {
        return context.available(SocialSetting.Feature.PARTY, action);
    }

    private boolean clanOn(String action) {
        return context.available(SocialSetting.Feature.CLANS, action);
    }

    private JsonObject commands(Player viewer) {
        JsonObject commands = new JsonObject();
        if (context.friendsEnabled()) {
            commands.add("friends", strings(context.settings().friends().subCommands().visible()));
        }
        if (context.partyEnabled()) {
            commands.add("party", strings(context.settings().party().subCommands().visible()));
        }
        if (clans != null) {
            commands.add("clans", strings(ClanCommands.visible(context, viewer)));
        }
        return commands;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private Optional<JsonObject> badge(Player viewer, String place, UUID subject) {
        return context.clanShownTo(viewer, place, subject).map(SocialViews::badge);
    }

    static JsonObject badge(Clan clan) {
        JsonObject object = new JsonObject();
        object.addProperty("id", clan.id().toString());
        object.addProperty("name", clan.name());
        object.addProperty("tag", clan.tag());
        object.addProperty("color", clan.color());
        object.addProperty("size", clan.size());
        return object;
    }

    private JsonObject clan(Player viewer) {
        JsonObject object = new JsonObject();
        UUID self = viewer.getUniqueId();
        Set<UUID> viewerFriends = context.friendsOf(viewer);
        Optional<Clan> current = clans.clans().clanOf(self);
        ClanRights rights = clans.settings().rights();
        object.addProperty("in-clan", current.isPresent());
        object.addProperty("can-create", ClanCommands.visible(context, viewer).contains("create"));
        if (current.isPresent()) {
            Clan clan = current.get();
            object.add("badge", badge(clan));
            object.addProperty("access", clan.access().id());
            object.addProperty("created", clan.created());
            object.addProperty("max", context.clanLimit(clan.leader()));
            object.addProperty("role", clan.role(self).orElse(ClanRole.MEMBER).id());
            object.addProperty("can-invite", clan.can(self, ClanAction.INVITE, null, rights) && clanOn("invite"));
            object.addProperty("can-settings", clan.can(self, ClanAction.SETTINGS, null, rights)
                    && clanOn("settings"));
            object.addProperty("can-disband", clan.isLeader(self) && clanOn("disband"));
            object.addProperty("can-leave", !clan.isLeader(self) || clan.size() == 1);
            JsonArray members = new JsonArray();
            List<ClanMember> ordered = new ArrayList<>();
            for (ClanRole role : List.of(ClanRole.LEADER, ClanRole.CO_LEADER, ClanRole.MODERATOR, ClanRole.MEMBER)) {
                ordered.addAll(clan.withRole(role));
            }
            for (ClanMember member : ordered.subList(0, Math.min(MAX_MEMBERS, ordered.size()))) {
                UUID uuid = member.uuid();
                Optional<Player> online = context.proxy().getPlayer(uuid)
                        .filter(found -> context.visibleTo(viewer, found));
                JsonObject item = clanPerson(viewer, viewerFriends, member);
                item.addProperty("online", online.isPresent());
                item.addProperty("server", online.map(context::serverOf).orElse(""));
                item.addProperty("leader", clan.isLeader(uuid));
                item.addProperty("self", uuid.equals(self));
                item.addProperty("role", member.role().id());
                item.addProperty("can-kick", clan.can(self, ClanAction.KICK, uuid, rights) && clanOn("kick"));
                item.addProperty("can-promote", clan.can(self, ClanAction.PROMOTE, uuid, rights)
                        && clanOn("promote"));
                item.addProperty("can-demote", clan.can(self, ClanAction.DEMOTE, uuid, rights)
                        && clanOn("demote"));
                item.addProperty("can-transfer", clan.can(self, ClanAction.TRANSFER, uuid, rights)
                        && clanOn("transfer"));
                members.add(item);
            }
            object.add("members", members);
            JsonArray invited = new JsonArray();
            clan.invited().forEach(uuid -> invited.add(person(viewer, viewerFriends, uuid)));
            object.add("invited", invited);
        }
        JsonArray incoming = new JsonArray();
        if (clanOn("accept")) {
            for (Clan invite : clans.clans().invitesFor(self, System.currentTimeMillis())) {
                incoming.add(badge(invite));
            }
        }
        object.add("incoming", incoming);
        return object;
    }

    private JsonObject clanPerson(Player viewer, Set<UUID> viewerFriends, ClanMember member) {
        UUID subject = member.uuid();
        JsonObject item = new JsonObject();
        boolean masked = context.masked(viewer, viewerFriends, subject);
        String real = context.proxy().getPlayer(subject).map(Player::getUsername)
                .orElse(member.name().isBlank() ? context.nameOf(subject) : member.name());
        item.addProperty("uuid", masked ? new UUID(0L, 0L).toString() : subject.toString());
        item.addProperty("ref", subject.toString());
        item.addProperty("name", context.visibleName(viewer, viewerFriends, subject, real));
        item.addProperty("masked", masked);
        item.addProperty("friend", viewerFriends.contains(subject));
        return item;
    }

    private boolean canInvite(Player viewer) {
        Optional<PartyManager.Party> current = party.parties().partyOf(viewer.getUniqueId());
        return current.isEmpty() || current.get().allowed(viewer.getUniqueId(), PartyAction.INVITE, null);
    }
}
