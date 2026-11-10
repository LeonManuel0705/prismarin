package net.prismarin.velocity.social;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.api.server.ServerStatusService;

public final class PartyService {

    private final SocialContext context;
    private final PartyManager parties;
    private final ServerDirectory directory;
    private final ServerStatusService status;

    public PartyService(SocialContext context, PartyManager parties, ServerDirectory directory,
                        ServerStatusService status) {
        this.context = context;
        this.parties = parties;
        this.directory = directory;
        this.status = status;
    }

    public PartyManager parties() {
        return parties;
    }

    public String command() {
        return "/" + context.settings().party().commands().getFirst();
    }

    public CompletableFuture<Void> invite(Player actor, String input) {
        return context.async(() -> {
            Optional<Player> target = context.findVisible(actor, context.friendsOf(actor), input);
            if (target.isEmpty()) {
                context.send(actor, "party.not-online", Placeholder.unparsed("player", input));
                return;
            }
            inviteNow(actor, target.get());
        });
    }

    public CompletableFuture<Void> invite(Player actor, UUID target) {
        return context.async(() -> {
            Optional<Player> online = context.proxy().getPlayer(target).filter(found -> context.visibleTo(actor, found));
            if (online.isEmpty()) {
                context.send(actor, "party.not-online", Placeholder.unparsed("player", context.shownTo(actor, target)));
                return;
            }
            inviteNow(actor, online.get());
        });
    }

    private void inviteNow(Player actor, Player target) {
        String shownTarget = context.shownTo(actor, target.getUniqueId());
        SocialProfile theirs = context.store().cached(target.getUniqueId());
        String privacy = context.setting(theirs, SocialSetting.PARTY_INVITES);
        if (!actor.getUniqueId().equals(target.getUniqueId())
                && (SocialSetting.NOBODY.equals(privacy)
                || SocialSetting.FRIENDS.equals(privacy) && !theirs.friendsWith(actor.getUniqueId()))) {
            context.send(actor, "party.not-accepting", Placeholder.unparsed("player", shownTarget));
            return;
        }
        long now = System.currentTimeMillis();
        UUID leader = parties.partyOf(actor.getUniqueId()).map(PartyManager.Party::leader)
                .orElse(actor.getUniqueId());
        PartyManager.Result result = parties.invite(actor.getUniqueId(), target.getUniqueId(),
                context.partyLimit(leader), now, context.settings().party().inviteExpiryMillis());
        switch (result.status()) {
            case SELF -> context.send(actor, "party.self");
            case NOT_LEADER -> context.send(actor, "party.not-leader");
            case ALREADY_MEMBER -> context.send(actor, "party.already-member", Placeholder.unparsed("player", shownTarget));
            case TARGET_IN_PARTY -> context.send(actor, "party.target-in-party",
                    Placeholder.unparsed("player", shownTarget));
            case ALREADY_INVITED -> context.send(actor, "party.already-invited",
                    Placeholder.unparsed("player", shownTarget));
            case FULL -> context.send(actor, "party.full",
                    Placeholder.unparsed("max", String.valueOf(context.partyLimit(leader))));
            case OK -> {
                PartyManager.Party party = result.party();
                int seconds = context.settings().party().inviteExpirySeconds();
                if (result.created()) {
                    context.send(actor, "party.created");
                }
                context.send(actor, "party.invited", Placeholder.unparsed("player", shownTarget),
                        Placeholder.unparsed("seconds", String.valueOf(seconds)));
                for (UUID member : party.members()) {
                    if (!member.equals(actor.getUniqueId())) {
                        context.proxy().getPlayer(member).ifPresent(online -> context.send(online,
                                "party.invite-broadcast",
                                Placeholder.unparsed("player", context.shownTo(online, actor.getUniqueId())),
                                Placeholder.unparsed("target", context.shownTo(online, target.getUniqueId()))));
                    }
                }
                String leaderName = context.shownTo(target, party.leader());
                target.sendMessage(context.render(target, "party.invite-received",
                                Placeholder.unparsed("player", context.shownTo(target, actor.getUniqueId())),
                                Placeholder.unparsed("seconds", String.valueOf(seconds)))
                        .append(Component.space())
                        .append(context.button(target, "party.button.accept", command() + " accept " + leaderName))
                        .append(Component.space())
                        .append(context.button(target, "party.button.deny", command() + " deny " + leaderName)));
                long expiresAt = party.invites().get(target.getUniqueId());
                UUID partyId = party.id();
                UUID invitee = target.getUniqueId();
                context.later(() -> expire(partyId, invitee, expiresAt), Math.max(1L, expiresAt - now) + 50L,
                        TimeUnit.MILLISECONDS);
            }
            default -> context.send(actor, "party.failed");
        }
    }

    private void expire(UUID partyId, UUID invitee, long expiresAt) {
        Optional<PartyManager.Party> before = parties.party(partyId);
        if (!parties.expire(partyId, invitee, expiresAt) || before.isEmpty()) {
            return;
        }
        PartyManager.Party party = before.get();
        context.proxy().getPlayer(party.leader()).ifPresent(leader -> {
            context.send(leader, "party.invite-expired",
                    Placeholder.unparsed("player", context.shownTo(leader, invitee)));
            if (parties.party(partyId).isEmpty()) {
                context.send(leader, "party.disbanded-empty");
            }
        });
        context.proxy().getPlayer(invitee).ifPresent(player -> context.send(player, "party.invite-expired-self",
                Placeholder.unparsed("player", context.shownTo(player, party.leader()))));
    }

    public CompletableFuture<Void> accept(Player player, String leaderName) {
        return context.async(() -> {
            UUID leader = null;
            if (leaderName != null) {
                Optional<UUID> found = inviter(player, leaderName);
                if (found.isEmpty()) {
                    context.send(player, "party.no-invite-from", Placeholder.unparsed("player", leaderName));
                    return;
                }
                leader = found.get();
            }
            acceptNow(player, leader);
        });
    }

    public CompletableFuture<Void> accept(Player player, UUID leader) {
        return context.async(() -> acceptNow(player, leader));
    }

    private void acceptNow(Player player, UUID leader) {
        PartyManager.Result result = parties.accept(player.getUniqueId(), leader, context::partyLimit,
                System.currentTimeMillis());
        switch (result.status()) {
            case IN_PARTY -> context.send(player, "party.already-in-party", Placeholder.unparsed("command", command()));
            case NO_INVITE -> context.send(player, "party.no-invite");
            case FULL -> context.send(player, "party.full",
                    Placeholder.unparsed("max", String.valueOf(context.partyLimit(result.party().leader()))));
            case OK -> {
                PartyManager.Party party = result.party();
                context.send(player, "party.joined-self",
                        Placeholder.unparsed("player", context.shownTo(player, party.leader())));
                broadcast(party, player.getUniqueId(), "party.joined", player.getUniqueId());
            }
            default -> context.send(player, "party.failed");
        }
    }

    public CompletableFuture<Void> deny(Player player, String leaderName) {
        return context.async(() -> {
            UUID leader = null;
            if (leaderName != null) {
                Optional<UUID> found = inviter(player, leaderName);
                if (found.isEmpty()) {
                    context.send(player, "party.no-invite-from", Placeholder.unparsed("player", leaderName));
                    return;
                }
                leader = found.get();
            }
            denyNow(player, leader);
        });
    }

    public CompletableFuture<Void> deny(Player player, UUID leader) {
        return context.async(() -> denyNow(player, leader));
    }

    private void denyNow(Player player, UUID leader) {
        PartyManager.Result result = parties.deny(player.getUniqueId(), leader, System.currentTimeMillis());
        if (!result.ok()) {
            context.send(player, "party.no-invite");
            return;
        }
        PartyManager.Party party = result.party();
        context.send(player, "party.denied", Placeholder.unparsed("player", context.shownTo(player, party.leader())));
        context.proxy().getPlayer(party.leader()).ifPresent(online -> {
            context.send(online, "party.invite-denied",
                    Placeholder.unparsed("player", context.shownTo(online, player.getUniqueId())));
            if (result.disbanded()) {
                context.send(online, "party.disbanded-empty");
            }
        });
    }

    public CompletableFuture<Void> leave(Player player) {
        return context.async(() -> {
            PartyManager.Result result = parties.leave(player.getUniqueId());
            if (!result.ok()) {
                context.send(player, "party.not-in-party");
                return;
            }
            context.send(player, "party.left-self");
            departed(result, player.getUniqueId(), "party.left");
        });
    }

    public CompletableFuture<Void> kick(Player actor, String input) {
        return context.async(() -> member(actor, input, true).ifPresentOrElse(target -> kickNow(actor, target),
                () -> context.send(actor, "party.not-member", Placeholder.unparsed("player", input))));
    }

    public CompletableFuture<Void> kick(Player actor, UUID target) {
        return context.async(() -> kickNow(actor, target));
    }

    private void kickNow(Player actor, UUID target) {
        Optional<PartyManager.Party> before = parties.partyOf(actor.getUniqueId());
        boolean member = before.map(party -> party.member(target)).orElse(false);
        PartyManager.Result result = parties.kick(actor.getUniqueId(), target);
        if (!statusOk(actor, result, target)) {
            return;
        }
        if (!member) {
            context.send(actor, "party.invite-cancelled", Placeholder.unparsed("player", context.shownTo(actor, target)));
            context.proxy().getPlayer(target).ifPresent(player -> context.send(player, "party.invite-revoked",
                    Placeholder.unparsed("player", context.shownTo(player, actor.getUniqueId()))));
            if (result.disbanded()) {
                context.send(actor, "party.disbanded-empty");
            }
            return;
        }
        context.proxy().getPlayer(target).ifPresent(player -> context.send(player, "party.kicked-self",
                Placeholder.unparsed("player", context.shownTo(player, actor.getUniqueId()))));
        departed(result, target, "party.kicked");
    }

    public CompletableFuture<Void> promote(Player actor, String input) {
        return context.async(() -> member(actor, input, false).ifPresentOrElse(target -> promoteNow(actor, target),
                () -> context.send(actor, "party.not-member", Placeholder.unparsed("player", input))));
    }

    public CompletableFuture<Void> promote(Player actor, UUID target) {
        return context.async(() -> promoteNow(actor, target));
    }

    private void promoteNow(Player actor, UUID target) {
        PartyManager.Result result = parties.promote(actor.getUniqueId(), target);
        if (!statusOk(actor, result, target)) {
            return;
        }
        broadcast(result.party(), target, "party.promoted", null);
    }

    public CompletableFuture<Void> disband(Player actor) {
        return context.async(() -> {
            PartyManager.Result result = parties.disband(actor.getUniqueId());
            if (!statusOk(actor, result, null)) {
                return;
            }
            for (UUID member : result.formerMembers()) {
                context.sendTo(member, "party.disbanded");
            }
        });
    }

    public CompletableFuture<Void> warp(Player actor) {
        return context.async(() -> {
            Optional<PartyManager.Party> party = parties.partyOf(actor.getUniqueId());
            if (party.isEmpty()) {
                context.send(actor, "party.not-in-party");
                return;
            }
            if (!party.get().leader(actor.getUniqueId())) {
                context.send(actor, "party.not-leader");
                return;
            }
            Optional<RegisteredServer> server = actor.getCurrentServer().map(connection -> connection.getServer());
            if (server.isEmpty()) {
                return;
            }
            String name = server.get().getServerInfo().getName();
            FollowPlan plan = move(actor.getUniqueId(), party.get(), server.get(), "party.warped");
            if (plan.move().isEmpty() && plan.noRoom().isEmpty()) {
                context.send(actor, "party.warp-nobody");
                return;
            }
            context.send(actor, "party.warp", Placeholder.unparsed("server", name),
                    Placeholder.unparsed("count", String.valueOf(plan.move().size())));
        });
    }

    public void follow(Player leader, RegisteredServer server) {
        if (!context.partyEnabled()) {
            return;
        }
        Optional<PartyManager.Party> party = parties.partyOf(leader.getUniqueId());
        if (party.isEmpty() || !party.get().leader(leader.getUniqueId()) || party.get().size() < 2) {
            return;
        }
        String name = server.getServerInfo().getName();
        String group = directory.byName(name).map(NetworkServer::group).orElse(NetworkServer.GROUP_DEFAULT);
        SocialSettings.Party settings = context.settings().party();
        if (!FollowPlan.follows(settings.follow(), settings.followIntoLobbies(), settings.followSkipGroups(), group,
                NetworkServer.GROUP_LOBBY.equalsIgnoreCase(group))) {
            return;
        }
        context.async(() -> move(leader.getUniqueId(), party.get(), server, "party.follow"));
    }

    private FollowPlan move(UUID leader, PartyManager.Party party, RegisteredServer server, String key) {
        String name = server.getServerInfo().getName();
        List<FollowPlan.Member> members = new ArrayList<>();
        for (UUID member : party.members()) {
            context.proxy().getPlayer(member).ifPresent(online -> members.add(
                    new FollowPlan.Member(member, context.serverOf(online))));
        }
        int max = status.status(name).filter(ServerStatus::online).map(ServerStatus::maxPlayers).orElse(0);
        FollowPlan plan = FollowPlan.plan(leader, name, members,
                FollowPlan.freeSlots(max, server.getPlayersConnected().size()));
        for (UUID uuid : plan.move()) {
            context.proxy().getPlayer(uuid).ifPresent(member -> {
                context.send(member, key, Placeholder.unparsed("server", name));
                member.createConnectionRequest(server).connect().thenAccept(result -> {
                    if (!result.isSuccessful()) {
                        context.send(member, "party.follow-failed", Placeholder.unparsed("server", name));
                    }
                }).exceptionally(error -> {
                    context.send(member, "party.follow-failed", Placeholder.unparsed("server", name));
                    return null;
                });
            });
        }
        for (UUID uuid : plan.noRoom()) {
            context.sendTo(uuid, "party.follow-full", Placeholder.unparsed("server", name));
        }
        return plan;
    }

    public CompletableFuture<Void> chat(Player sender, String text) {
        Optional<PartyManager.Party> party = parties.partyOf(sender.getUniqueId());
        if (party.isEmpty()) {
            context.send(sender, "party.not-in-party");
            return CompletableFuture.completedFuture(null);
        }
        return context.muted(sender.getUniqueId()).thenAcceptAsync(muted -> {
            if (muted) {
                context.send(sender, "party.muted");
                return;
            }
            PartyManager.Party current = parties.partyOf(sender.getUniqueId()).orElse(null);
            if (current == null) {
                return;
            }
            for (UUID member : current.members()) {
                context.proxy().getPlayer(member).ifPresent(online -> context.send(online,
                        current.leader(sender.getUniqueId()) ? "party.chat-leader" : "party.chat",
                        Placeholder.unparsed("player", context.shownTo(online, sender.getUniqueId())),
                        Placeholder.unparsed("message", text)));
            }
            spy(current, sender, text);
        }, runnable -> context.async(runnable));
    }

    private void spy(PartyManager.Party party, Player sender, String text) {
        if (!context.settings().messages().staffSpy()) {
            return;
        }
        for (UUID spy : context.spies()) {
            if (party.member(spy)) {
                continue;
            }
            context.proxy().getPlayer(spy).filter(online -> online.hasPermission(SocialContext.SPY))
                    .ifPresent(online -> context.send(online, "party.spy",
                            Placeholder.unparsed("leader", context.nameOf(party.leader())),
                            Placeholder.unparsed("player", sender.getUsername()),
                            Placeholder.unparsed("message", text)));
        }
    }

    public CompletableFuture<Void> list(Player player) {
        return context.async(() -> {
            Optional<PartyManager.Party> party = parties.partyOf(player.getUniqueId());
            if (party.isEmpty()) {
                List<PartyManager.Party> invites = parties.invitesFor(player.getUniqueId(), System.currentTimeMillis());
                context.send(player, "party.list-none", Placeholder.unparsed("command", command()));
                for (PartyManager.Party invite : invites) {
                    String leaderName = context.shownTo(player, invite.leader());
                    player.sendMessage(context.render(player, "party.list-open-invite",
                                    Placeholder.unparsed("player", leaderName))
                            .append(Component.space())
                            .append(context.button(player, "party.button.accept", command() + " accept " + leaderName))
                            .append(Component.space())
                            .append(context.button(player, "party.button.deny", command() + " deny " + leaderName)));
                }
                return;
            }
            PartyManager.Party current = party.get();
            context.send(player, "party.list-header",
                    Placeholder.unparsed("count", String.valueOf(current.size())),
                    Placeholder.unparsed("max", String.valueOf(context.partyLimit(current.leader()))));
            for (UUID member : current.members()) {
                Optional<Player> online = context.proxy().getPlayer(member)
                        .filter(found -> context.visibleTo(player, found));
                String shown = context.shownTo(player, member);
                String key = current.leader(member) ? "party.list-leader" : "party.list-member";
                if (online.isEmpty()) {
                    key = key + "-offline";
                }
                context.send(player, key, Placeholder.unparsed("player", shown),
                        Placeholder.unparsed("server", online.map(context::serverOf).orElse("-")));
            }
            for (UUID invited : current.invites().keySet()) {
                context.send(player, "party.list-invited", Placeholder.unparsed("player", context.shownTo(player, invited)));
            }
        });
    }

    public void disconnected(Player player) {
        UUID uuid = player.getUniqueId();
        Optional<PartyManager.Party> party = parties.partyOf(uuid);
        if (party.isEmpty()) {
            return;
        }
        int grace = context.settings().party().offlineGraceSeconds();
        if (grace <= 0) {
            PartyManager.Result result = parties.leave(uuid);
            if (result.ok()) {
                departed(result, uuid, "party.left-offline");
            }
            return;
        }
        Set<UUID> online = new HashSet<>();
        context.proxy().getAllPlayers().forEach(found -> {
            if (!found.getUniqueId().equals(uuid)) {
                online.add(found.getUniqueId());
            }
        });
        Optional<UUID> newLeader = parties.handOver(uuid, online);
        PartyManager.Party current = parties.partyOf(uuid).orElse(party.get());
        broadcast(current, uuid, "party.member-offline", uuid,
                Placeholder.unparsed("seconds", String.valueOf(grace)));
        newLeader.ifPresent(leader -> broadcast(current, leader, "party.promoted", null));
        UUID partyId = current.id();
        context.later(() -> {
            if (context.proxy().getPlayer(uuid).isPresent()) {
                return;
            }
            Optional<PartyManager.Party> still = parties.partyOf(uuid);
            if (still.isEmpty() || !still.get().id().equals(partyId)) {
                return;
            }
            PartyManager.Result result = parties.leave(uuid);
            if (result.ok()) {
                departed(result, uuid, "party.removed-offline");
            }
        }, grace, TimeUnit.SECONDS);
    }

    public void reconnected(Player player) {
        parties.partyOf(player.getUniqueId()).ifPresent(party ->
                broadcast(party, player.getUniqueId(), "party.member-back", player.getUniqueId()));
    }

    public List<String> memberNames(Player viewer, String prefix, boolean includeInvites) {
        Optional<PartyManager.Party> party = parties.partyOf(viewer.getUniqueId());
        if (party.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID member : party.get().members()) {
            if (!member.equals(viewer.getUniqueId())) {
                names.add(context.shownTo(viewer, member));
            }
        }
        if (includeInvites) {
            party.get().invites().keySet().forEach(invited -> names.add(context.shownTo(viewer, invited)));
        }
        return FriendService.filter(names, prefix);
    }

    public List<String> inviterNames(Player viewer, String prefix) {
        List<String> names = new ArrayList<>();
        for (PartyManager.Party party : parties.invitesFor(viewer.getUniqueId(), System.currentTimeMillis())) {
            names.add(context.shownTo(viewer, party.leader()));
        }
        return FriendService.filter(names, prefix);
    }

    private Optional<UUID> inviter(Player player, String name) {
        for (PartyManager.Party party : parties.invitesFor(player.getUniqueId(), System.currentTimeMillis())) {
            if (context.shownTo(player, party.leader()).equalsIgnoreCase(name)) {
                return Optional.of(party.leader());
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> member(Player actor, String input, boolean includeInvites) {
        Optional<PartyManager.Party> party = parties.partyOf(actor.getUniqueId());
        if (party.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> candidates = new ArrayList<>(party.get().members());
        if (includeInvites) {
            candidates.addAll(party.get().invites().keySet());
        }
        for (UUID candidate : candidates) {
            if (context.shownTo(actor, candidate).equalsIgnoreCase(input)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private boolean statusOk(Player actor, PartyManager.Result result, UUID target) {
        String shown = target == null ? "" : context.shownTo(actor, target);
        switch (result.status()) {
            case OK -> {
                return true;
            }
            case NOT_IN_PARTY -> context.send(actor, "party.not-in-party");
            case NOT_LEADER -> context.send(actor, "party.not-leader");
            case SELF -> context.send(actor, "party.self");
            case NOT_MEMBER -> context.send(actor, "party.not-member", Placeholder.unparsed("player", shown));
            default -> context.send(actor, "party.failed");
        }
        return false;
    }

    private void departed(PartyManager.Result result, UUID subject, String key) {
        PartyManager.Party party = result.party();
        if (party == null) {
            return;
        }
        if (result.disbanded()) {
            for (UUID member : result.formerMembers()) {
                if (!member.equals(subject)) {
                    context.proxy().getPlayer(member).ifPresent(online -> {
                        context.send(online, key, Placeholder.unparsed("player", context.shownTo(online, subject)));
                        context.send(online, "party.disbanded-empty");
                    });
                }
            }
            return;
        }
        broadcast(party, subject, key, subject);
        if (result.newLeader() != null) {
            broadcast(party, result.newLeader(), "party.promoted", null);
        }
    }

    private void broadcast(PartyManager.Party party, UUID subject, String key, UUID except, TagResolver... extra) {
        for (UUID member : party.members()) {
            if (member.equals(except)) {
                continue;
            }
            context.proxy().getPlayer(member).ifPresent(online -> {
                TagResolver[] resolvers = new TagResolver[extra.length + 1];
                resolvers[0] = Placeholder.unparsed("player", context.shownTo(online, subject));
                System.arraycopy(extra, 0, resolvers, 1, extra.length);
                context.send(online, key, resolvers);
            });
        }
    }
}
