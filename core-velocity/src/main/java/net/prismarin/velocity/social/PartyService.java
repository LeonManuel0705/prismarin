package net.prismarin.velocity.social;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.api.server.ServerStatusService;
import net.prismarin.api.social.PartyAction;
import net.prismarin.api.social.PartyLeftEvent;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import net.prismarin.api.social.SocialResult;

public final class PartyService {

    private final SocialContext context;
    private final PartyManager parties;
    private final ServerDirectory directory;
    private final ServerStatusService status;
    private final PartyFeed feed;

    public PartyService(SocialContext context, PartyManager parties, ServerDirectory directory,
                        ServerStatusService status) {
        this.context = context;
        this.parties = parties;
        this.directory = directory;
        this.status = status;
        this.feed = new PartyFeed(context);
    }

    public PartyManager parties() {
        return parties;
    }

    public PartyFeed feed() {
        return feed;
    }

    public String command() {
        return "/" + context.settings().party().commands().getFirst();
    }

    public static SocialResult result(PartyManager.Status status) {
        return switch (status) {
            case OK -> SocialResult.SUCCESS;
            case SELF -> SocialResult.SELF;
            case NOT_IN_PARTY -> SocialResult.NOT_IN_PARTY;
            case NOT_ALLOWED -> SocialResult.NOT_ALLOWED;
            case NOT_MEMBER -> SocialResult.NOT_MEMBER;
            case ALREADY_MEMBER -> SocialResult.ALREADY_MEMBER;
            case TARGET_IN_PARTY -> SocialResult.TARGET_IN_PARTY;
            case IN_PARTY -> SocialResult.ALREADY_IN_PARTY;
            case ALREADY_INVITED -> SocialResult.ALREADY_INVITED;
            case FULL -> SocialResult.FULL;
            case NO_INVITE -> SocialResult.NO_INVITE;
            case NO_PARTY -> SocialResult.NOT_FOUND;
            case CLOSED -> SocialResult.CLOSED;
            case ALREADY_ROLE -> SocialResult.ALREADY_ROLE;
            case UNCHANGED -> SocialResult.UNCHANGED;
        };
    }

    public CompletableFuture<SocialResult> invite(Player actor, String input) {
        return context.result(() -> {
            Optional<Player> target = context.findVisible(actor, context.friendsOf(actor), input);
            if (target.isEmpty()) {
                context.send(actor, "party.not-online", Placeholder.unparsed("player", input));
                return SocialResult.NOT_FOUND;
            }
            return inviteNow(actor, target.get());
        });
    }

    public CompletableFuture<SocialResult> invite(Player actor, UUID target) {
        return context.result(() -> {
            Optional<Player> online = context.proxy().getPlayer(target).filter(found -> context.visibleTo(actor, found));
            if (online.isEmpty()) {
                context.send(actor, "party.not-online", Placeholder.unparsed("player", context.shownTo(actor, target)));
                return SocialResult.NOT_FOUND;
            }
            return inviteNow(actor, online.get());
        });
    }

    private SocialResult inviteNow(Player actor, Player target) {
        String shownTarget = context.shownTo(actor, target.getUniqueId());
        SocialProfile theirs = context.store().cached(target.getUniqueId());
        String privacy = context.setting(theirs, SocialSetting.PARTY_INVITES);
        if (!actor.getUniqueId().equals(target.getUniqueId())
                && (SocialSetting.NOBODY.equals(privacy)
                || SocialSetting.FRIENDS.equals(privacy) && !theirs.friendsWith(actor.getUniqueId()))) {
            context.send(actor, "party.not-accepting", Placeholder.unparsed("player", shownTarget));
            return SocialResult.NOT_ACCEPTING;
        }
        long now = System.currentTimeMillis();
        Optional<PartyManager.Party> current = parties.partyOf(actor.getUniqueId());
        UUID leader = current.map(PartyManager.Party::leader).orElse(actor.getUniqueId());
        PartyManager.Result result = parties.invite(actor.getUniqueId(), target.getUniqueId(),
                context.partyLimit(leader), now, context.settings().party().inviteExpiryMillis());
        switch (result.status()) {
            case SELF -> context.send(actor, "party.self");
            case NOT_ALLOWED -> context.send(actor, current.map(party -> party.settings().invite() == PartyRole.LEADER)
                    .orElse(false) ? "party.invite-leader-only" : "party.invite-moderators-only");
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
                feed.changed(result, null, actor.getUniqueId());
            }
            default -> context.send(actor, "party.failed");
        }
        return result(result.status());
    }

    private void expire(UUID partyId, UUID invitee, long expiresAt) {
        PartyManager.Result result = parties.expire(partyId, invitee, expiresAt);
        if (!result.ok() || result.before() == null) {
            return;
        }
        PartyManager.Party party = result.before();
        context.proxy().getPlayer(party.leader()).ifPresent(leader -> {
            context.send(leader, "party.invite-expired",
                    Placeholder.unparsed("player", context.shownTo(leader, invitee)));
            if (result.disbanded()) {
                context.send(leader, "party.disbanded-empty");
            }
        });
        context.proxy().getPlayer(invitee).ifPresent(player -> context.send(player, "party.invite-expired-self",
                Placeholder.unparsed("player", context.shownTo(player, party.leader()))));
        context.async(() -> feed.changed(result, null, null));
    }

    public CompletableFuture<SocialResult> accept(Player player, String leaderName) {
        return context.result(() -> {
            UUID leader = null;
            if (leaderName != null) {
                Optional<UUID> found = inviter(player, leaderName);
                if (found.isEmpty()) {
                    context.send(player, "party.no-invite-from", Placeholder.unparsed("player", leaderName));
                    return SocialResult.NO_INVITE;
                }
                leader = found.get();
            }
            return acceptNow(player, leader);
        });
    }

    public CompletableFuture<SocialResult> accept(Player player, UUID leader) {
        return context.result(() -> acceptNow(player, leader));
    }

    private SocialResult acceptNow(Player player, UUID leader) {
        PartyManager.Result result = parties.accept(player.getUniqueId(), leader, context::partyLimit,
                System.currentTimeMillis());
        switch (result.status()) {
            case IN_PARTY -> context.send(player, "party.already-in-party", Placeholder.unparsed("command", command()));
            case NO_INVITE -> context.send(player, "party.no-invite");
            case FULL -> context.send(player, "party.full",
                    Placeholder.unparsed("max", String.valueOf(context.partyLimit(result.party().leader()))));
            case OK -> joined(player, result);
            default -> context.send(player, "party.failed");
        }
        return result(result.status());
    }

    public CompletableFuture<SocialResult> join(Player player, String input) {
        return context.result(() -> {
            Optional<UUID> inviter = inviter(player, input);
            if (inviter.isPresent()) {
                return acceptNow(player, inviter.get());
            }
            Optional<Player> target = context.findVisible(player, context.friendsOf(player), input);
            if (target.isEmpty()) {
                context.send(player, "party.not-online", Placeholder.unparsed("player", input));
                return SocialResult.NOT_FOUND;
            }
            return joinNow(player, target.get().getUniqueId());
        });
    }

    public CompletableFuture<SocialResult> join(Player player, UUID member) {
        return context.result(() -> {
            boolean invited = parties.invitesFor(player.getUniqueId(), System.currentTimeMillis()).stream()
                    .anyMatch(party -> party.member(member));
            if (invited) {
                return acceptNow(player, member);
            }
            if (context.proxy().getPlayer(member).filter(found -> context.visibleTo(player, found)).isEmpty()) {
                context.send(player, "party.not-online", Placeholder.unparsed("player", context.shownTo(player, member)));
                return SocialResult.NOT_FOUND;
            }
            return joinNow(player, member);
        });
    }

    private SocialResult joinNow(Player player, UUID member) {
        Set<UUID> friends = context.friendsEnabled() ? context.store().cached(player.getUniqueId()).friendIds()
                : Set.of();
        PartyManager.Result result = parties.join(player.getUniqueId(), member, friends, context::partyLimit,
                System.currentTimeMillis());
        String shown = context.shownTo(player, member);
        switch (result.status()) {
            case SELF -> context.send(player, "party.self");
            case IN_PARTY -> context.send(player, "party.already-in-party", Placeholder.unparsed("command", command()));
            case NO_PARTY -> context.send(player, "party.join-no-party", Placeholder.unparsed("player", shown));
            case CLOSED -> context.send(player, "party.join-closed", Placeholder.unparsed("player", shown));
            case FULL -> context.send(player, "party.full",
                    Placeholder.unparsed("max", String.valueOf(context.partyLimit(result.party().leader()))));
            case OK -> joined(player, result);
            default -> context.send(player, "party.failed");
        }
        return result(result.status());
    }

    private void joined(Player player, PartyManager.Result result) {
        PartyManager.Party party = result.party();
        context.send(player, "party.joined-self",
                Placeholder.unparsed("player", context.shownTo(player, party.leader())));
        broadcast(party, player.getUniqueId(), "party.joined", player.getUniqueId());
        feed.changed(result, null, player.getUniqueId());
    }

    public CompletableFuture<SocialResult> deny(Player player, String leaderName) {
        return context.result(() -> {
            UUID leader = null;
            if (leaderName != null) {
                Optional<UUID> found = inviter(player, leaderName);
                if (found.isEmpty()) {
                    context.send(player, "party.no-invite-from", Placeholder.unparsed("player", leaderName));
                    return SocialResult.NO_INVITE;
                }
                leader = found.get();
            }
            return denyNow(player, leader);
        });
    }

    public CompletableFuture<SocialResult> deny(Player player, UUID leader) {
        return context.result(() -> denyNow(player, leader));
    }

    private SocialResult denyNow(Player player, UUID leader) {
        PartyManager.Result result = parties.deny(player.getUniqueId(), leader, System.currentTimeMillis());
        if (!result.ok()) {
            context.send(player, "party.no-invite");
            return result(result.status());
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
        feed.changed(result, null, player.getUniqueId());
        return SocialResult.SUCCESS;
    }

    public CompletableFuture<SocialResult> leave(Player player) {
        return context.result(() -> {
            PartyManager.Result result = parties.leave(player.getUniqueId());
            if (!result.ok()) {
                context.send(player, "party.not-in-party");
                return result(result.status());
            }
            context.send(player, "party.left-self");
            departed(result, player.getUniqueId(), "party.left");
            feed.changed(result, PartyLeftEvent.Reason.LEFT, player.getUniqueId());
            return SocialResult.SUCCESS;
        });
    }

    public CompletableFuture<SocialResult> kick(Player actor, String input) {
        return context.result(() -> member(actor, input, true).map(target -> kickNow(actor, target))
                .orElseGet(() -> notMember(actor, input)));
    }

    public CompletableFuture<SocialResult> kick(Player actor, UUID target) {
        return context.result(() -> kickNow(actor, target));
    }

    private SocialResult kickNow(Player actor, UUID target) {
        Optional<PartyManager.Party> before = parties.partyOf(actor.getUniqueId());
        boolean member = before.map(party -> party.member(target)).orElse(false);
        PartyManager.Result result = parties.kick(actor.getUniqueId(), target);
        if (result.status() == PartyManager.Status.NOT_ALLOWED) {
            boolean moderator = before.map(party -> party.role(actor.getUniqueId()) == PartyRole.MODERATOR)
                    .orElse(false);
            context.send(actor, moderator && member ? "party.kick-not-allowed" : "party.moderators-only");
            return SocialResult.NOT_ALLOWED;
        }
        if (!statusOk(actor, result, target)) {
            return result(result.status());
        }
        if (!member) {
            context.send(actor, "party.invite-cancelled", Placeholder.unparsed("player", context.shownTo(actor, target)));
            context.proxy().getPlayer(target).ifPresent(player -> context.send(player, "party.invite-revoked",
                    Placeholder.unparsed("player", context.shownTo(player, actor.getUniqueId()))));
            if (result.disbanded()) {
                context.send(actor, "party.disbanded-empty");
            }
            feed.changed(result, null, actor.getUniqueId());
            return SocialResult.SUCCESS;
        }
        context.proxy().getPlayer(target).ifPresent(player -> context.send(player, "party.kicked-self",
                Placeholder.unparsed("player", context.shownTo(player, actor.getUniqueId()))));
        departed(result, target, "party.kicked");
        feed.changed(result, PartyLeftEvent.Reason.KICKED, actor.getUniqueId());
        return SocialResult.SUCCESS;
    }

    public CompletableFuture<SocialResult> promote(Player actor, String input) {
        return byName(actor, input, target -> promoteNow(actor, target));
    }

    public CompletableFuture<SocialResult> promote(Player actor, UUID target) {
        return context.result(() -> promoteNow(actor, target));
    }

    private SocialResult promoteNow(Player actor, UUID target) {
        PartyManager.Result result = parties.promote(actor.getUniqueId(), target);
        return roleChanged(actor, target, result, "party.already-moderator", "party.made-moderator");
    }

    public CompletableFuture<SocialResult> demote(Player actor, String input) {
        return byName(actor, input, target -> demoteNow(actor, target));
    }

    public CompletableFuture<SocialResult> demote(Player actor, UUID target) {
        return context.result(() -> demoteNow(actor, target));
    }

    private SocialResult demoteNow(Player actor, UUID target) {
        PartyManager.Result result = parties.demote(actor.getUniqueId(), target);
        return roleChanged(actor, target, result, "party.not-moderator", "party.made-member");
    }

    public CompletableFuture<SocialResult> transfer(Player actor, String input) {
        return byName(actor, input, target -> transferNow(actor, target));
    }

    public CompletableFuture<SocialResult> transfer(Player actor, UUID target) {
        return context.result(() -> transferNow(actor, target));
    }

    private SocialResult transferNow(Player actor, UUID target) {
        PartyManager.Result result = parties.transfer(actor.getUniqueId(), target);
        return roleChanged(actor, target, result, "party.failed", "party.leader-changed");
    }

    private SocialResult roleChanged(Player actor, UUID target, PartyManager.Result result, String sameKey,
                                     String broadcastKey) {
        switch (result.status()) {
            case OK -> {
                broadcast(result.party(), target, broadcastKey, null);
                feed.changed(result, null, actor.getUniqueId());
            }
            case NOT_ALLOWED -> context.send(actor, "party.not-leader");
            case ALREADY_ROLE -> context.send(actor, sameKey,
                    Placeholder.unparsed("player", context.shownTo(actor, target)));
            default -> statusOk(actor, result, target);
        }
        return result(result.status());
    }

    private CompletableFuture<SocialResult> byName(Player actor, String input, Function<UUID, SocialResult> action) {
        return context.result(() -> member(actor, input, false).map(action).orElseGet(() -> notMember(actor, input)));
    }

    private SocialResult notMember(Player actor, String input) {
        if (parties.partyOf(actor.getUniqueId()).isEmpty()) {
            context.send(actor, "party.not-in-party");
            return SocialResult.NOT_IN_PARTY;
        }
        context.send(actor, "party.not-member", Placeholder.unparsed("player", input));
        return SocialResult.NOT_MEMBER;
    }

    public CompletableFuture<SocialResult> disband(Player actor) {
        return context.result(() -> {
            PartyManager.Result result = parties.disband(actor.getUniqueId());
            if (result.status() == PartyManager.Status.NOT_ALLOWED) {
                context.send(actor, "party.not-leader");
                return SocialResult.NOT_ALLOWED;
            }
            if (!statusOk(actor, result, null)) {
                return result(result.status());
            }
            for (UUID member : result.formerMembers()) {
                context.sendTo(member, "party.disbanded");
            }
            feed.changed(result, null, actor.getUniqueId());
            return SocialResult.SUCCESS;
        });
    }

    public CompletableFuture<SocialResult> configure(Player actor, Map<PartyOption, String> changes) {
        return context.result(() -> configureNow(actor, settings -> {
            PartySettings next = settings;
            for (Map.Entry<PartyOption, String> change : changes.entrySet()) {
                next = change.getKey().apply(next, change.getValue());
            }
            return next;
        }));
    }

    public CompletableFuture<SocialResult> configure(Player actor, PartySettings settings) {
        return context.result(() -> configureNow(actor, current -> settings));
    }

    private SocialResult configureNow(Player actor, UnaryOperator<PartySettings> change) {
        PartyManager.Result result = parties.configure(actor.getUniqueId(), change);
        switch (result.status()) {
            case NOT_IN_PARTY -> context.send(actor, "party.not-in-party");
            case NOT_ALLOWED -> context.send(actor, "party.settings-not-allowed");
            case UNCHANGED -> context.send(actor, "party.settings-unchanged");
            case OK -> {
                PartySettings before = result.before().settings();
                PartySettings after = result.party().settings();
                for (PartyOption option : PartyOption.values()) {
                    String value = option.value(after);
                    if (value.equals(option.value(before))) {
                        continue;
                    }
                    for (UUID member : result.party().members()) {
                        context.proxy().getPlayer(member).ifPresent(online -> context.send(online,
                                "party.setting-changed",
                                Placeholder.unparsed("player", context.shownTo(online, actor.getUniqueId())),
                                Placeholder.component("setting",
                                        context.render(online, "party.setting." + option.id())),
                                Placeholder.component("value", context.render(online, "party.value." + value))));
                    }
                }
                feed.changed(result, null, actor.getUniqueId());
            }
            default -> context.send(actor, "party.failed");
        }
        return result(result.status());
    }

    public CompletableFuture<SocialResult> warp(Player actor) {
        return context.result(() -> {
            Optional<PartyManager.Party> party = parties.partyOf(actor.getUniqueId());
            if (party.isEmpty()) {
                context.send(actor, "party.not-in-party");
                return SocialResult.NOT_IN_PARTY;
            }
            if (!party.get().allowed(actor.getUniqueId(), PartyAction.WARP, null)) {
                context.send(actor, "party.not-leader");
                return SocialResult.NOT_ALLOWED;
            }
            Optional<RegisteredServer> server = actor.getCurrentServer().map(connection -> connection.getServer());
            if (server.isEmpty()) {
                return SocialResult.FAILED;
            }
            String name = server.get().getServerInfo().getName();
            FollowPlan plan = move(actor.getUniqueId(), party.get(), server.get(), "party.warped");
            if (plan.move().isEmpty() && plan.noRoom().isEmpty()) {
                context.send(actor, "party.warp-nobody");
                return SocialResult.UNCHANGED;
            }
            context.send(actor, "party.warp", Placeholder.unparsed("server", name),
                    Placeholder.unparsed("count", String.valueOf(plan.move().size())));
            return SocialResult.SUCCESS;
        });
    }

    public void follow(Player leader, RegisteredServer server) {
        if (!context.partyEnabled()) {
            return;
        }
        Optional<PartyManager.Party> party = parties.partyOf(leader.getUniqueId());
        if (party.isEmpty() || !party.get().leader(leader.getUniqueId()) || party.get().size() < 2
                || !party.get().settings().follow()) {
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

    public CompletableFuture<SocialResult> chat(Player sender, String text) {
        Optional<PartyManager.Party> party = parties.partyOf(sender.getUniqueId());
        if (party.isEmpty()) {
            context.send(sender, "party.not-in-party");
            return CompletableFuture.completedFuture(SocialResult.NOT_IN_PARTY);
        }
        if (!party.get().allowed(sender.getUniqueId(), PartyAction.CHAT, null)) {
            context.send(sender, party.get().settings().chat() == PartyRole.LEADER ? "party.chat-leader-only"
                    : "party.chat-moderators-only");
            return CompletableFuture.completedFuture(SocialResult.NOT_ALLOWED);
        }
        return context.muted(sender.getUniqueId()).thenApplyAsync(muted -> {
            if (muted) {
                context.send(sender, "party.muted");
                return SocialResult.NOT_ALLOWED;
            }
            PartyManager.Party current = parties.partyOf(sender.getUniqueId()).orElse(null);
            if (current == null) {
                return SocialResult.NOT_IN_PARTY;
            }
            String key = switch (current.role(sender.getUniqueId())) {
                case LEADER -> "party.chat-leader";
                case MODERATOR -> "party.chat-moderator";
                case MEMBER -> "party.chat";
            };
            for (UUID member : current.members()) {
                context.proxy().getPlayer(member).ifPresent(online -> context.send(online, key,
                        Placeholder.unparsed("player", context.shownTo(online, sender.getUniqueId())),
                        Placeholder.unparsed("message", text)));
            }
            spy(current, sender, text);
            return SocialResult.SUCCESS;
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
            for (PartyRole role : List.of(PartyRole.LEADER, PartyRole.MODERATOR, PartyRole.MEMBER)) {
                for (UUID member : current.members()) {
                    if (current.role(member) != role) {
                        continue;
                    }
                    Optional<Player> online = context.proxy().getPlayer(member)
                            .filter(found -> context.visibleTo(player, found));
                    String key = "party.list-" + role.id() + (online.isEmpty() ? "-offline" : "");
                    context.send(player, key, Placeholder.unparsed("player", context.shownTo(player, member)),
                            Placeholder.unparsed("server", online.map(context::serverOf).orElse("-")));
                }
            }
            for (UUID invited : current.invites().keySet()) {
                context.send(player, "party.list-invited", Placeholder.unparsed("player", context.shownTo(player, invited)));
            }
            player.sendMessage(context.render(player, "party.list-settings",
                            Placeholder.component("role", context.render(player,
                                    "party.role." + current.role(player.getUniqueId()).id())))
                    .append(Component.space())
                    .append(context.button(player, "party.button.settings", command() + " settings")));
        });
    }

    public CompletableFuture<Void> showSettings(Player player) {
        return context.async(() -> {
            Optional<PartyManager.Party> party = parties.partyOf(player.getUniqueId());
            if (party.isEmpty()) {
                return;
            }
            PartyManager.Party current = party.get();
            boolean editable = current.allowed(player.getUniqueId(), PartyAction.SETTINGS, null);
            context.send(player, editable ? "party.settings-header" : "party.settings-header-locked");
            for (PartyOption option : context.settings().party().options()) {
                String value = option.value(current.settings());
                Component line = context.render(player, "party.settings-entry",
                        Placeholder.component("setting", context.render(player, "party.setting." + option.id())),
                        Placeholder.component("value", context.render(player, "party.value." + value)));
                if (editable) {
                    line = line.append(Component.space()).append(context.button(player, "party.button.change",
                            command() + " settings " + option.id() + " " + option.next(value)));
                }
                player.sendMessage(line);
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
                feed.changed(result, PartyLeftEvent.Reason.DISCONNECTED, uuid);
            }
            return;
        }
        Set<UUID> online = new HashSet<>();
        context.proxy().getAllPlayers().forEach(found -> {
            if (!found.getUniqueId().equals(uuid)) {
                online.add(found.getUniqueId());
            }
        });
        PartyManager.Result handOver = parties.handOver(uuid, online);
        PartyManager.Party current = parties.partyOf(uuid).orElse(party.get());
        broadcast(current, uuid, "party.member-offline", uuid,
                Placeholder.unparsed("seconds", String.valueOf(grace)));
        if (handOver.ok()) {
            broadcast(current, handOver.newLeader(), "party.leader-changed", null);
            feed.changed(handOver, null, null);
        }
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
                context.async(() -> feed.changed(result, PartyLeftEvent.Reason.DISCONNECTED, uuid));
            }
        }, grace, TimeUnit.SECONDS);
    }

    public void reconnected(Player player) {
        parties.partyOf(player.getUniqueId()).ifPresent(party ->
                broadcast(party, player.getUniqueId(), "party.member-back", player.getUniqueId()));
    }

    public void arrived(Player player) {
        feed.arrived(player, parties.partyOf(player.getUniqueId()));
    }

    public List<String> memberNames(Player viewer, String prefix, boolean includeInvites) {
        return memberNames(viewer, prefix, includeInvites, null);
    }

    public List<String> memberNames(Player viewer, String prefix, boolean includeInvites, PartyRole role) {
        Optional<PartyManager.Party> party = parties.partyOf(viewer.getUniqueId());
        if (party.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID member : party.get().members()) {
            if (!member.equals(viewer.getUniqueId()) && (role == null || party.get().role(member) == role)) {
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
            case NOT_ALLOWED -> context.send(actor, "party.moderators-only");
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
            broadcast(party, result.newLeader(), "party.leader-changed", null);
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
