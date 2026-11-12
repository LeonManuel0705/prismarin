package net.prismarin.velocity.social;

import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.event.Cancellable;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAction;
import net.prismarin.api.social.ClanChatEvent;
import net.prismarin.api.social.ClanCreateEvent;
import net.prismarin.api.social.ClanDisbandEvent;
import net.prismarin.api.social.ClanJoinEvent;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanMemberLeftEvent;
import net.prismarin.api.social.ClanPage;
import net.prismarin.api.social.ClanResult;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.SocialResult;
import net.prismarin.shared.social.ClanColors;
import net.prismarin.shared.text.TimeAgo;
import net.prismarin.velocity.admin.ProxyRemote;

public final class ClanService {

    public static final int LIST_PAGE_SIZE = 8;
    private static final List<ClanRole> ORDER = List.of(ClanRole.LEADER, ClanRole.CO_LEADER, ClanRole.MODERATOR,
            ClanRole.MEMBER);

    private final SocialContext context;
    private final ClanManager clans;
    private final ClanStore store;
    private final ClanFeed feed;
    private final Map<UUID, Long> disbandRequests = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> loaded;

    public ClanService(SocialContext context, ClanManager clans, ClanStore store) {
        this.context = context;
        this.clans = clans;
        this.store = store;
        this.feed = new ClanFeed(context);
        this.loaded = context.async(() -> {
            List<Clan> stored = store.loadAll();
            stored.forEach(clans::load);
            context.logger().info("Loaded {} clans", stored.size());
        });
        store.listen(id -> context.async(() -> remote(id)));
    }

    public ClanManager clans() {
        return clans;
    }

    public SocialSettings.Clans settings() {
        return context.settings().clans();
    }

    public String command() {
        return "/" + settings().commands().getFirst();
    }

    public void ready() {
        loaded.join();
    }

    public Component tag(Clan clan) {
        return settings().tag().render(context.services().formatter(), clan);
    }

    public static SocialResult result(ClanManager.Status status) {
        return switch (status) {
            case OK -> SocialResult.SUCCESS;
            case SELF -> SocialResult.SELF;
            case NOT_IN_CLAN -> SocialResult.NOT_IN_CLAN;
            case IN_CLAN -> SocialResult.ALREADY_IN_CLAN;
            case TARGET_IN_CLAN -> SocialResult.TARGET_IN_CLAN;
            case NOT_ALLOWED -> SocialResult.NOT_ALLOWED;
            case NOT_MEMBER -> SocialResult.NOT_MEMBER;
            case ALREADY_MEMBER -> SocialResult.ALREADY_MEMBER;
            case ALREADY_INVITED -> SocialResult.ALREADY_INVITED;
            case NO_INVITE -> SocialResult.NO_INVITE;
            case NO_CLAN -> SocialResult.NOT_FOUND;
            case CLOSED -> SocialResult.CLOSED;
            case FULL -> SocialResult.FULL;
            case NAME_TAKEN -> SocialResult.NAME_TAKEN;
            case TAG_TAKEN -> SocialResult.TAG_TAKEN;
            case ALREADY_ROLE -> SocialResult.ALREADY_ROLE;
            case TRANSFER_FIRST -> SocialResult.TRANSFER_FIRST;
            case INVALID -> SocialResult.INVALID_DATA;
            case UNCHANGED -> SocialResult.UNCHANGED;
        };
    }

    public CompletableFuture<SocialResult> create(Player player, String name, String tag) {
        return context.result(() -> {
            ready();
            UUID uuid = player.getUniqueId();
            String permission = settings().createPermission();
            if (!permission.isEmpty() && !player.hasPermission(permission)) {
                context.send(player, "general.no-permission");
                return SocialResult.NOT_ALLOWED;
            }
            SocialResult invalid = checkNames(player, name, tag);
            if (invalid != null) {
                return invalid;
            }
            if (clans.clanOf(uuid).isPresent()) {
                context.send(player, "clan.already-in-clan", Placeholder.unparsed("command", command()));
                return SocialResult.ALREADY_IN_CLAN;
            }
            loadConflicts(name, tag);
            if (cancelled(new ClanCreateEvent(uuid, name, tag))) {
                context.send(player, "clan.cancelled");
                return SocialResult.CANCELLED;
            }
            ClanManager.Result result = clans.create(uuid, player.getUsername(), name, tag,
                    settings().defaultAccess(), System.currentTimeMillis());
            switch (result.status()) {
                case IN_CLAN -> context.send(player, "clan.already-in-clan", Placeholder.unparsed("command", command()));
                case NAME_TAKEN -> context.send(player, "clan.name-taken", Placeholder.unparsed("value", name));
                case TAG_TAKEN -> context.send(player, "clan.tag-taken", Placeholder.unparsed("value", tag));
                case OK -> {
                    context.send(player, "clan.created", with(clanResolvers(result.clan()),
                            Placeholder.unparsed("command", command())));
                    commit(result, null, uuid, true);
                }
                default -> context.send(player, "clan.failed");
            }
            return result(result.status());
        });
    }

    public CompletableFuture<SocialResult> invite(Player actor, String input) {
        return context.result(() -> {
            ready();
            Optional<Player> target = context.findVisible(actor, context.friendsOf(actor), input);
            if (target.isEmpty()) {
                context.send(actor, "clan.not-online", Placeholder.unparsed("player", input));
                return SocialResult.NOT_FOUND;
            }
            return inviteNow(actor, target.get());
        });
    }

    public CompletableFuture<SocialResult> invite(Player actor, UUID target) {
        return context.result(() -> {
            ready();
            Optional<Player> online = context.proxy().getPlayer(target).filter(found -> context.visibleTo(actor, found));
            if (online.isEmpty()) {
                context.send(actor, "clan.not-online", Placeholder.unparsed("player", context.shownTo(actor, target)));
                return SocialResult.NOT_FOUND;
            }
            return inviteNow(actor, online.get());
        });
    }

    private SocialResult inviteNow(Player actor, Player target) {
        UUID actorId = actor.getUniqueId();
        UUID targetId = target.getUniqueId();
        String shownTarget = context.shownTo(actor, targetId);
        SocialProfile theirs = context.store().cached(targetId);
        String privacy = context.setting(theirs, SocialSetting.CLAN_INVITES);
        if (!actorId.equals(targetId) && (SocialSetting.NOBODY.equals(privacy)
                || SocialSetting.FRIENDS.equals(privacy) && !theirs.friendsWith(actorId))) {
            context.send(actor, "clan.not-accepting", Placeholder.unparsed("player", shownTarget));
            return SocialResult.NOT_ACCEPTING;
        }
        long now = System.currentTimeMillis();
        ClanManager.Result result = clans.invite(actorId, targetId, context::clanLimit, now,
                settings().inviteExpiryMillis());
        switch (result.status()) {
            case NOT_IN_CLAN -> notInClan(actor);
            case SELF -> context.send(actor, "clan.self");
            case NOT_ALLOWED -> needsRole(actor, ClanAction.INVITE);
            case ALREADY_MEMBER -> context.send(actor, "clan.already-member", Placeholder.unparsed("player", shownTarget));
            case TARGET_IN_CLAN -> context.send(actor, "clan.target-in-clan", Placeholder.unparsed("player", shownTarget));
            case ALREADY_INVITED -> context.send(actor, "clan.already-invited",
                    Placeholder.unparsed("player", shownTarget));
            case FULL -> context.send(actor, "clan.full",
                    Placeholder.unparsed("max", String.valueOf(context.clanLimit(result.clan().leader()))));
            case OK -> {
                Clan clan = result.clan();
                int seconds = settings().inviteExpirySeconds();
                context.send(actor, "clan.invited", Placeholder.unparsed("player", shownTarget),
                        Placeholder.unparsed("seconds", String.valueOf(seconds)));
                broadcast(clan, actorId, "clan.invite-broadcast", actorId,
                        viewer -> List.of(Placeholder.unparsed("target", context.shownTo(viewer, targetId))));
                target.sendMessage(context.render(target, "clan.invite-received", with(clanResolvers(clan),
                                Placeholder.unparsed("player", context.shownTo(target, actorId)),
                                Placeholder.unparsed("seconds", String.valueOf(seconds))))
                        .append(Component.space())
                        .append(context.button(target, "clan.button.accept", command() + " accept " + clan.name()))
                        .append(Component.space())
                        .append(context.button(target, "clan.button.deny", command() + " deny " + clan.name())));
                long expiresAt = now + settings().inviteExpiryMillis();
                UUID clanId = clan.id();
                context.later(() -> expire(clanId, targetId, expiresAt), Math.max(1L, expiresAt - now) + 50L,
                        TimeUnit.MILLISECONDS);
                commit(result, null, actorId, false);
            }
            default -> context.send(actor, "clan.failed");
        }
        return result(result.status());
    }

    private void expire(UUID clanId, UUID invitee, long expiresAt) {
        ClanManager.Result result = clans.expire(clanId, invitee, expiresAt);
        if (!result.ok()) {
            return;
        }
        context.proxy().getPlayer(invitee).ifPresent(player -> context.send(player, "clan.invite-expired-self",
                clanResolvers(result.clan())));
        context.async(() -> commit(result, null, null, false));
    }

    public CompletableFuture<SocialResult> accept(Player player, String input) {
        return context.result(() -> {
            ready();
            UUID clanId = null;
            if (input != null) {
                Optional<Clan> found = inviting(player, input);
                if (found.isEmpty()) {
                    context.send(player, "clan.no-invite-from", Placeholder.unparsed("value", input));
                    return SocialResult.NO_INVITE;
                }
                clanId = found.get().id();
            }
            return acceptNow(player, clanId);
        });
    }

    public CompletableFuture<SocialResult> accept(Player player, UUID clanId) {
        return context.result(() -> {
            ready();
            return acceptNow(player, clanId);
        });
    }

    private SocialResult acceptNow(Player player, UUID clanId) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Optional<Clan> target = clanId != null ? clans.clan(clanId).filter(clan -> clan.isInvited(uuid))
                : clans.invitesFor(uuid, now).stream().findFirst();
        if (target.isPresent() && clans.clanOf(uuid).isEmpty() && cancelled(new ClanJoinEvent(target.get(), uuid))) {
            context.send(player, "clan.cancelled");
            return SocialResult.CANCELLED;
        }
        ClanManager.Result result = clans.accept(uuid, player.getUsername(),
                target.map(Clan::id).orElse(clanId), context::clanLimit, now);
        switch (result.status()) {
            case IN_CLAN -> context.send(player, "clan.already-in-clan", Placeholder.unparsed("command", command()));
            case NO_INVITE -> context.send(player, "clan.no-invite");
            case FULL -> context.send(player, "clan.full",
                    Placeholder.unparsed("max", String.valueOf(context.clanLimit(result.clan().leader()))));
            case OK -> joined(player, result);
            default -> context.send(player, "clan.failed");
        }
        return result(result.status());
    }

    private void joined(Player player, ClanManager.Result result) {
        Clan clan = result.clan();
        context.send(player, "clan.joined-self", clanResolvers(clan));
        broadcast(clan, player.getUniqueId(), "clan.joined", player.getUniqueId());
        commit(result, null, player.getUniqueId(), true);
    }

    public CompletableFuture<SocialResult> deny(Player player, String input) {
        return context.result(() -> {
            ready();
            UUID clanId = null;
            if (input != null) {
                Optional<Clan> found = inviting(player, input);
                if (found.isEmpty()) {
                    context.send(player, "clan.no-invite-from", Placeholder.unparsed("value", input));
                    return SocialResult.NO_INVITE;
                }
                clanId = found.get().id();
            }
            return denyNow(player, clanId);
        });
    }

    public CompletableFuture<SocialResult> deny(Player player, UUID clanId) {
        return context.result(() -> {
            ready();
            return denyNow(player, clanId);
        });
    }

    private SocialResult denyNow(Player player, UUID clanId) {
        ClanManager.Result result = clans.deny(player.getUniqueId(), clanId, System.currentTimeMillis());
        if (!result.ok()) {
            context.send(player, "clan.no-invite");
            return result(result.status());
        }
        Clan clan = result.clan();
        context.send(player, "clan.denied", clanResolvers(clan));
        for (ClanMember member : clan.members()) {
            if (clan.can(member.uuid(), ClanAction.INVITE, null, settings().rights())) {
                context.proxy().getPlayer(member.uuid()).ifPresent(online -> context.send(online, "clan.invite-denied",
                        Placeholder.unparsed("player", context.shownTo(online, player.getUniqueId()))));
            }
        }
        commit(result, null, player.getUniqueId(), false);
        return SocialResult.SUCCESS;
    }

    public CompletableFuture<SocialResult> join(Player player, String input) {
        return context.result(() -> {
            ready();
            Optional<Clan> clan = clans.find(input);
            if (clan.isEmpty()) {
                context.send(player, "clan.unknown", Placeholder.unparsed("value", input));
                return SocialResult.NOT_FOUND;
            }
            return clan.get().isInvited(player.getUniqueId()) ? acceptNow(player, clan.get().id())
                    : joinNow(player, clan.get());
        });
    }

    public CompletableFuture<SocialResult> join(Player player, UUID clanId) {
        return context.result(() -> {
            ready();
            Optional<Clan> clan = clans.clan(clanId);
            if (clan.isEmpty()) {
                context.send(player, "clan.unknown", Placeholder.unparsed("value", "?"));
                return SocialResult.NOT_FOUND;
            }
            return clan.get().isInvited(player.getUniqueId()) ? acceptNow(player, clanId) : joinNow(player, clan.get());
        });
    }

    private SocialResult joinNow(Player player, Clan clan) {
        UUID uuid = player.getUniqueId();
        if (clans.clanOf(uuid).isEmpty() && clan.access() == ClanAccess.OPEN
                && cancelled(new ClanJoinEvent(clan, uuid))) {
            context.send(player, "clan.cancelled");
            return SocialResult.CANCELLED;
        }
        ClanManager.Result result = clans.join(uuid, player.getUsername(), clan.id(), context::clanLimit,
                System.currentTimeMillis());
        switch (result.status()) {
            case IN_CLAN -> context.send(player, "clan.already-in-clan", Placeholder.unparsed("command", command()));
            case NO_CLAN -> context.send(player, "clan.unknown", Placeholder.unparsed("value", clan.name()));
            case CLOSED -> context.send(player, "clan.join-closed", clanResolvers(clan));
            case FULL -> context.send(player, "clan.full",
                    Placeholder.unparsed("max", String.valueOf(context.clanLimit(clan.leader()))));
            case OK -> joined(player, result);
            default -> context.send(player, "clan.failed");
        }
        return result(result.status());
    }

    public CompletableFuture<SocialResult> leave(Player player) {
        return context.result(() -> {
            ready();
            UUID uuid = player.getUniqueId();
            Optional<Clan> current = clans.clanOf(uuid);
            if (current.isPresent() && current.get().isLeader(uuid) && current.get().size() == 1
                    && cancelled(new ClanDisbandEvent(current.get(), uuid))) {
                context.send(player, "clan.cancelled");
                return SocialResult.CANCELLED;
            }
            ClanManager.Result result = clans.leave(uuid);
            switch (result.status()) {
                case NOT_IN_CLAN -> notInClan(player);
                case TRANSFER_FIRST -> context.send(player, "clan.transfer-first",
                        Placeholder.unparsed("command", command()));
                case OK -> {
                    if (result.disbanded()) {
                        context.send(player, "clan.left-disbanded", clanResolvers(result.clan()));
                    } else {
                        context.send(player, "clan.left-self", clanResolvers(result.clan()));
                        Clan before = current.orElse(result.clan());
                        broadcastAs(result.clan(), "clan.left", uuid, viewer -> named(viewer, before, uuid));
                    }
                    commit(result, ClanMemberLeftEvent.Reason.LEFT, uuid, true);
                }
                default -> context.send(player, "clan.failed");
            }
            return result(result.status());
        });
    }

    public CompletableFuture<SocialResult> kick(Player actor, String input) {
        return byName(actor, input, true, target -> kickNow(actor, target));
    }

    public CompletableFuture<SocialResult> kick(Player actor, UUID target) {
        return context.result(() -> {
            ready();
            return kickNow(actor, target);
        });
    }

    private SocialResult kickNow(Player actor, UUID target) {
        UUID actorId = actor.getUniqueId();
        Optional<Clan> before = clans.clanOf(actorId);
        boolean member = before.map(clan -> clan.contains(target)).orElse(false);
        String shown = before.map(clan -> shown(actor, clan, target)).orElse("?");
        ClanManager.Result result = clans.kick(actorId, target);
        switch (result.status()) {
            case NOT_IN_CLAN -> notInClan(actor);
            case SELF -> context.send(actor, "clan.self");
            case NOT_MEMBER -> context.send(actor, "clan.not-member", Placeholder.unparsed("player", shown));
            case NOT_ALLOWED -> {
                if (member) {
                    rankOrRole(actor, before.get(), ClanAction.KICK);
                } else {
                    needsRole(actor, ClanAction.INVITE);
                }
            }
            case OK -> {
                Clan clan = result.clan();
                if (!member) {
                    context.send(actor, "clan.invite-cancelled", Placeholder.unparsed("player", shown));
                    context.proxy().getPlayer(target).ifPresent(player -> context.send(player, "clan.invite-revoked",
                            clanResolvers(clan)));
                    commit(result, null, actorId, false);
                } else {
                    context.proxy().getPlayer(target).ifPresent(player -> context.send(player, "clan.kicked-self",
                            with(clanResolvers(clan), Placeholder.unparsed("player",
                                    context.shownTo(player, actorId)))));
                    broadcastAs(clan, "clan.kicked", null, viewer -> named(viewer, before.get(), target));
                    commit(result, ClanMemberLeftEvent.Reason.KICKED, actorId, true);
                }
            }
            default -> context.send(actor, "clan.failed");
        }
        return result(result.status());
    }

    public CompletableFuture<SocialResult> promote(Player actor, String input) {
        return byName(actor, input, false, target -> promoteNow(actor, target));
    }

    public CompletableFuture<SocialResult> promote(Player actor, UUID target) {
        return context.result(() -> {
            ready();
            return promoteNow(actor, target);
        });
    }

    private SocialResult promoteNow(Player actor, UUID target) {
        Optional<Clan> before = clans.clanOf(actor.getUniqueId());
        ClanManager.Result result = clans.promote(actor.getUniqueId(), target);
        return roleChanged(actor, target, before, result, ClanAction.PROMOTE, "clan.highest-role");
    }

    public CompletableFuture<SocialResult> demote(Player actor, String input) {
        return byName(actor, input, false, target -> demoteNow(actor, target));
    }

    public CompletableFuture<SocialResult> demote(Player actor, UUID target) {
        return context.result(() -> {
            ready();
            return demoteNow(actor, target);
        });
    }

    private SocialResult demoteNow(Player actor, UUID target) {
        Optional<Clan> before = clans.clanOf(actor.getUniqueId());
        ClanManager.Result result = clans.demote(actor.getUniqueId(), target);
        return roleChanged(actor, target, before, result, ClanAction.DEMOTE, "clan.lowest-role");
    }

    public CompletableFuture<SocialResult> transfer(Player actor, String input) {
        return byName(actor, input, false, target -> transferNow(actor, target));
    }

    public CompletableFuture<SocialResult> transfer(Player actor, UUID target) {
        return context.result(() -> {
            ready();
            return transferNow(actor, target);
        });
    }

    private SocialResult transferNow(Player actor, UUID target) {
        Optional<Clan> before = clans.clanOf(actor.getUniqueId());
        ClanManager.Result result = clans.transfer(actor.getUniqueId(), target);
        if (result.status() == ClanManager.Status.NOT_ALLOWED) {
            context.send(actor, "clan.leader-only");
            return SocialResult.NOT_ALLOWED;
        }
        return roleChanged(actor, target, before, result, ClanAction.TRANSFER, "clan.failed");
    }

    private SocialResult roleChanged(Player actor, UUID target, Optional<Clan> before, ClanManager.Result result,
                                     ClanAction action, String sameKey) {
        String shown = before.map(clan -> shown(actor, clan, target)).orElse("?");
        switch (result.status()) {
            case NOT_IN_CLAN -> notInClan(actor);
            case SELF -> context.send(actor, "clan.self");
            case NOT_MEMBER -> context.send(actor, "clan.not-member", Placeholder.unparsed("player", shown));
            case NOT_ALLOWED -> rankOrRole(actor, before.orElse(null), action);
            case ALREADY_ROLE -> context.send(actor, sameKey, Placeholder.unparsed("player", shown));
            case OK -> {
                announceRole(result.clan(), target);
                commit(result, null, actor.getUniqueId(), true);
            }
            default -> context.send(actor, "clan.failed");
        }
        return result(result.status());
    }

    private void announceRole(Clan clan, UUID target) {
        ClanRole role = clan.role(target).orElse(ClanRole.MEMBER);
        if (role == ClanRole.LEADER) {
            broadcast(clan, target, "clan.leader-changed", null);
            return;
        }
        broadcast(clan, target, "clan.role-changed", null,
                viewer -> List.of(Placeholder.component("role", roleName(viewer, role))));
    }

    public CompletableFuture<SocialResult> disband(Player actor, boolean confirmed) {
        return context.result(() -> {
            ready();
            UUID uuid = actor.getUniqueId();
            Optional<Clan> clan = clans.clanOf(uuid);
            if (clan.isEmpty()) {
                notInClan(actor);
                return SocialResult.NOT_IN_CLAN;
            }
            if (!clan.get().isLeader(uuid)) {
                context.send(actor, "clan.leader-only");
                return SocialResult.NOT_ALLOWED;
            }
            long now = System.currentTimeMillis();
            if (!confirmed) {
                int seconds = settings().disbandConfirmSeconds();
                disbandRequests.put(uuid, now + seconds * 1000L);
                actor.sendMessage(context.render(actor, "clan.disband-confirm", with(clanResolvers(clan.get()),
                                Placeholder.unparsed("seconds", String.valueOf(seconds))))
                        .append(Component.space())
                        .append(context.button(actor, "clan.button.disband", command() + " disband confirm")));
                return SocialResult.UNCHANGED;
            }
            Long until = disbandRequests.remove(uuid);
            if (until == null || until < now) {
                context.send(actor, "clan.disband-expired", Placeholder.unparsed("command", command()));
                return SocialResult.UNCHANGED;
            }
            return disbandNow(actor);
        });
    }

    public CompletableFuture<SocialResult> disbandConfirmed(Player actor) {
        return context.result(() -> {
            ready();
            return disbandNow(actor);
        });
    }

    private SocialResult disbandNow(Player actor) {
        UUID uuid = actor.getUniqueId();
        Optional<Clan> clan = clans.clanOf(uuid);
        if (clan.isPresent() && clan.get().isLeader(uuid) && cancelled(new ClanDisbandEvent(clan.get(), uuid))) {
            context.send(actor, "clan.cancelled");
            return SocialResult.CANCELLED;
        }
        ClanManager.Result result = clans.disband(uuid);
        switch (result.status()) {
            case NOT_IN_CLAN -> notInClan(actor);
            case NOT_ALLOWED -> context.send(actor, "clan.leader-only");
            case OK -> {
                for (UUID member : result.clan().memberIds()) {
                    context.sendTo(member, "clan.disbanded", clanResolvers(result.clan()));
                }
                commit(result, null, uuid, true);
            }
            default -> context.send(actor, "clan.failed");
        }
        return result(result.status());
    }

    public CompletableFuture<SocialResult> color(Player actor, String input) {
        return context.result(() -> {
            ready();
            String permission = settings().colorPermission();
            if (!permission.isEmpty() && !actor.hasPermission(permission)) {
                context.send(actor, "general.no-permission");
                return SocialResult.NOT_ALLOWED;
            }
            Optional<String> color = ClanColors.normalize(input);
            if (color.isEmpty()) {
                context.send(actor, "clan.invalid-color", Placeholder.unparsed("value", input == null ? "" : input));
                return SocialResult.INVALID_COLOR;
            }
            ClanManager.Result result = clans.color(actor.getUniqueId(), color.get());
            switch (result.status()) {
                case NOT_IN_CLAN -> notInClan(actor);
                case NOT_ALLOWED -> needsRole(actor, ClanAction.COLOR);
                case UNCHANGED -> context.send(actor, "clan.color-unchanged");
                case OK -> {
                    broadcast(result.clan(), actor.getUniqueId(), "clan.color-changed", null,
                            viewer -> List.of(Placeholder.component("tag", tag(result.clan()))));
                    commit(result, null, actor.getUniqueId(), true);
                }
                default -> context.send(actor, "clan.failed");
            }
            return result(result.status());
        });
    }

    public CompletableFuture<SocialResult> rename(Player actor, String name, String tag) {
        return context.result(() -> {
            ready();
            Optional<Clan> current = clans.clanOf(actor.getUniqueId());
            if (current.isEmpty()) {
                notInClan(actor);
                return SocialResult.NOT_IN_CLAN;
            }
            SocialResult invalid = checkNames(actor, name == null ? current.get().name() : name,
                    tag == null ? current.get().tag() : tag);
            if (invalid != null) {
                return invalid;
            }
            loadConflicts(name == null ? current.get().name() : name, tag == null ? current.get().tag() : tag);
            ClanManager.Result result = clans.renameBy(actor.getUniqueId(), name, tag);
            switch (result.status()) {
                case NOT_IN_CLAN -> notInClan(actor);
                case NOT_ALLOWED -> needsRole(actor, ClanAction.RENAME);
                case NAME_TAKEN -> context.send(actor, "clan.name-taken", Placeholder.unparsed("value", name));
                case TAG_TAKEN -> context.send(actor, "clan.tag-taken", Placeholder.unparsed("value", tag));
                case UNCHANGED -> context.send(actor, "clan.rename-unchanged");
                case OK -> {
                    broadcast(result.clan(), actor.getUniqueId(), "clan.renamed", null,
                            viewer -> List.of(clanResolvers(result.clan())));
                    commit(result, null, actor.getUniqueId(), true);
                }
                default -> context.send(actor, "clan.failed");
            }
            return result(result.status());
        });
    }

    public CompletableFuture<SocialResult> access(Player actor, ClanAccess access) {
        return context.result(() -> {
            ready();
            ClanManager.Result result = clans.access(actor.getUniqueId(), access);
            switch (result.status()) {
                case NOT_IN_CLAN -> notInClan(actor);
                case NOT_ALLOWED -> needsRole(actor, ClanAction.SETTINGS);
                case UNCHANGED -> context.send(actor, "clan.settings-unchanged");
                case OK -> {
                    broadcast(result.clan(), actor.getUniqueId(), "clan.access-changed", null,
                            viewer -> List.of(Placeholder.component("value",
                                    context.render(viewer, "clan.access." + result.clan().access().id()))));
                    commit(result, null, actor.getUniqueId(), true);
                }
                default -> context.send(actor, "clan.failed");
            }
            return result(result.status());
        });
    }

    public CompletableFuture<SocialResult> chat(Player sender, String text) {
        UUID uuid = sender.getUniqueId();
        Optional<Clan> clan = clans.clanOf(uuid);
        if (clan.isEmpty()) {
            notInClan(sender);
            return CompletableFuture.completedFuture(SocialResult.NOT_IN_CLAN);
        }
        if (!clan.get().can(uuid, ClanAction.CHAT, null, settings().rights())) {
            needsRole(sender, ClanAction.CHAT);
            return CompletableFuture.completedFuture(SocialResult.NOT_ALLOWED);
        }
        return context.muted(uuid).thenApplyAsync(muted -> {
            if (muted) {
                context.send(sender, "clan.muted");
                return SocialResult.NOT_ALLOWED;
            }
            Clan current = clans.clanOf(uuid).orElse(null);
            if (current == null) {
                return SocialResult.NOT_IN_CLAN;
            }
            ClanChatEvent event = new ClanChatEvent(current, uuid, text);
            if (cancelled(event)) {
                return SocialResult.CANCELLED;
            }
            ClanRole role = current.role(uuid).orElse(ClanRole.MEMBER);
            for (UUID member : current.memberIds()) {
                context.proxy().getPlayer(member).ifPresent(online -> context.send(online,
                        "clan.chat-line." + role.id(),
                        Placeholder.unparsed("player", context.shownTo(online, uuid)),
                        Placeholder.unparsed("message", text)));
            }
            ProxyRemote.relay(context.proxy(), event);
            spy(current, sender, text);
            return SocialResult.SUCCESS;
        }, runnable -> context.async(runnable));
    }

    private void spy(Clan clan, Player sender, String text) {
        if (!context.settings().messages().staffSpy()) {
            return;
        }
        for (UUID spy : context.spies()) {
            if (clan.contains(spy)) {
                continue;
            }
            context.proxy().getPlayer(spy).filter(online -> online.hasPermission(SocialContext.SPY))
                    .ifPresent(online -> context.send(online, "clan.spy",
                            Placeholder.unparsed("clan", clan.name()),
                            Placeholder.unparsed("player", sender.getUsername()),
                            Placeholder.unparsed("message", text)));
        }
    }

    public CompletableFuture<Void> info(Player viewer, String input) {
        return context.async(() -> {
            ready();
            UUID self = viewer.getUniqueId();
            Optional<Clan> own = clans.clanOf(self);
            if (input == null) {
                if (own.isEmpty()) {
                    none(viewer);
                    return;
                }
                full(viewer, own.get());
                return;
            }
            Optional<Clan> found = clans.find(input);
            if (found.isEmpty()) {
                context.send(viewer, "clan.unknown", Placeholder.unparsed("value", input));
                return;
            }
            if (found.get().contains(self)) {
                full(viewer, found.get());
            } else {
                summary(viewer, found.get());
            }
        });
    }

    private void none(Player viewer) {
        context.send(viewer, "clan.none", Placeholder.unparsed("command", command()));
        boolean invites = settings().subCommands().enabled("accept");
        if (!invites) {
            return;
        }
        for (Clan invite : clans.invitesFor(viewer.getUniqueId(), System.currentTimeMillis())) {
            viewer.sendMessage(context.render(viewer, "clan.none-invite", clanResolvers(invite))
                    .append(Component.space())
                    .append(context.button(viewer, "clan.button.accept", command() + " accept " + invite.name()))
                    .append(Component.space())
                    .append(context.button(viewer, "clan.button.deny", command() + " deny " + invite.name())));
        }
    }

    private void header(Player viewer, Clan clan) {
        long now = System.currentTimeMillis();
        context.send(viewer, "clan.info-header", with(clanResolvers(clan),
                Placeholder.unparsed("count", String.valueOf(clan.size())),
                Placeholder.unparsed("max", String.valueOf(context.clanLimit(clan.leader())))));
        context.send(viewer, "clan.info-access", Placeholder.component("value",
                context.render(viewer, "clan.access." + clan.access().id())));
        if (clan.created() > 0) {
            context.send(viewer, "clan.info-founded", Placeholder.component("time",
                    context.time(viewer, TimeAgo.since(clan.created(), now))));
        }
    }

    private void full(Player viewer, Clan clan) {
        header(viewer, clan);
        for (ClanRole role : ORDER) {
            List<ClanMember> members = clan.withRole(role);
            if (!members.isEmpty()) {
                context.send(viewer, "clan.group." + role.id(),
                        Placeholder.unparsed("count", String.valueOf(members.size())));
            }
            for (ClanMember member : members) {
                Optional<Player> online = context.proxy().getPlayer(member.uuid())
                        .filter(found -> context.visibleTo(viewer, found));
                String key = "clan.list-" + role.id() + (online.isEmpty() ? "-offline" : "");
                context.send(viewer, key, Placeholder.unparsed("player", shown(viewer, clan, member.uuid())),
                        Placeholder.unparsed("server", online.map(context::serverOf).orElse("-")));
            }
        }
        if (!clan.invited().isEmpty()) {
            context.send(viewer, "clan.group.invited",
                    Placeholder.unparsed("count", String.valueOf(clan.invited().size())));
        }
        for (UUID invited : clan.invited()) {
            context.send(viewer, "clan.list-invited", Placeholder.unparsed("player", context.shownTo(viewer, invited)));
        }
        ClanRole role = clan.role(viewer.getUniqueId()).orElse(ClanRole.MEMBER);
        Component line = context.render(viewer, "clan.info-role", Placeholder.component("role", roleName(viewer, role)));
        if (settings().subCommands().enabled("settings")) {
            line = line.append(Component.space()).append(context.button(viewer, "clan.button.settings",
                    command() + " settings"));
        }
        viewer.sendMessage(line);
    }

    private void summary(Player viewer, Clan clan) {
        header(viewer, clan);
        context.send(viewer, "clan.info-leader", Placeholder.unparsed("player", shown(viewer, clan, clan.leader())));
        if (clan.access() == ClanAccess.OPEN && clans.clanOf(viewer.getUniqueId()).isEmpty()
                && settings().subCommands().enabled("join")) {
            viewer.sendMessage(context.button(viewer, "clan.button.join", command() + " join " + clan.name()));
        }
    }

    public CompletableFuture<Void> list(Player viewer, int requested) {
        return context.async(() -> {
            ready();
            ClanPage page = ClanPage.of(clans.all(), requested, LIST_PAGE_SIZE);
            if (page.total() == 0) {
                context.send(viewer, "clan.list-empty", Placeholder.unparsed("command", command()));
                return;
            }
            context.send(viewer, "clan.list-header", Placeholder.unparsed("count", String.valueOf(page.total())),
                    Placeholder.unparsed("page", String.valueOf(page.page() + 1)),
                    Placeholder.unparsed("pages", String.valueOf(page.pages())));
            boolean info = settings().subCommands().enabled("info");
            for (Clan clan : page.clans()) {
                Component line = context.render(viewer, "clan.list-entry", with(clanResolvers(clan),
                        Placeholder.unparsed("count", String.valueOf(clan.size()))));
                if (info) {
                    line = line.clickEvent(ClickEvent.runCommand(command() + " info " + clan.name()))
                            .hoverEvent(HoverEvent.showText(context.render(viewer, "clan.list-entry-hover")));
                }
                viewer.sendMessage(line);
            }
            if (page.pages() > 1) {
                Component navigation = Component.empty();
                if (page.page() > 0) {
                    navigation = navigation.append(context.button(viewer, "clan.button.previous",
                            command() + " list " + page.page())).append(Component.space());
                }
                if (page.hasNext()) {
                    navigation = navigation.append(context.button(viewer, "clan.button.next",
                            command() + " list " + (page.page() + 2)));
                }
                viewer.sendMessage(navigation);
            }
        });
    }

    public CompletableFuture<Void> showSettings(Player viewer) {
        return context.async(() -> {
            ready();
            Optional<Clan> clan = clans.clanOf(viewer.getUniqueId());
            if (clan.isEmpty()) {
                return;
            }
            boolean editable = clan.get().can(viewer.getUniqueId(), ClanAction.SETTINGS, null, settings().rights());
            context.send(viewer, editable ? "clan.settings-header" : "clan.settings-header-locked");
            ClanAccess access = clan.get().access();
            Component line = context.render(viewer, "clan.settings-entry",
                    Placeholder.component("setting", context.render(viewer, "clan.setting.access")),
                    Placeholder.component("value", context.render(viewer, "clan.access." + access.id())));
            if (editable) {
                line = line.append(Component.space()).append(context.button(viewer, "clan.button.change",
                        command() + " settings access " + access.next().id()));
            }
            viewer.sendMessage(line);
        });
    }

    public void arrived(Player player) {
        ready();
        UUID uuid = player.getUniqueId();
        clans.refreshName(uuid, player.getUsername()).ifPresent(clan -> {
            store.persist(clan.id(), () -> clans.clan(clan.id()));
            feed.remote(null, clan);
        });
        feed.arrived(player, clans.clanOf(uuid));
    }

    private void remote(UUID id) {
        ready();
        Optional<Clan> before = clans.clan(id);
        Optional<Clan> stored = store.load(id);
        if (stored.isPresent()) {
            clans.load(stored.get());
        } else {
            clans.forget(id);
        }
        feed.remote(before.orElse(null), clans.clan(id).orElse(null));
    }

    public CompletableFuture<ClanResult> createClan(UUID leader, String name, String tag) {
        return managed(() -> {
            if (name == null || tag == null) {
                return ClanResult.of(SocialResult.INVALID_NAME);
            }
            SocialResult invalid = checkNames(null, name, tag);
            if (invalid != null) {
                return ClanResult.of(invalid);
            }
            if (clans.clanOf(leader).isPresent()) {
                return ClanResult.of(SocialResult.ALREADY_IN_CLAN);
            }
            loadConflicts(name, tag);
            if (cancelled(new ClanCreateEvent(leader, name, tag))) {
                return ClanResult.of(SocialResult.CANCELLED);
            }
            ClanManager.Result result = clans.create(leader, context.nameOf(leader), name, tag,
                    settings().defaultAccess(), System.currentTimeMillis());
            if (result.ok()) {
                context.sendTo(leader, "clan.created", with(clanResolvers(result.clan()),
                        Placeholder.unparsed("command", command())));
            }
            return finish(result, null);
        });
    }

    public CompletableFuture<ClanResult> renameClan(UUID clanId, String name, String tag) {
        return managed(() -> {
            Optional<Clan> current = clans.clan(clanId);
            if (current.isEmpty()) {
                return ClanResult.of(SocialResult.NOT_FOUND);
            }
            String nextName = name == null ? current.get().name() : name;
            String nextTag = tag == null ? current.get().tag() : tag;
            SocialResult invalid = checkNames(null, nextName, nextTag);
            if (invalid != null) {
                return ClanResult.of(invalid, current.get());
            }
            loadConflicts(nextName, nextTag);
            return finish(clans.rename(clanId, name, tag), null);
        });
    }

    public CompletableFuture<ClanResult> disbandClan(UUID clanId) {
        return managed(() -> {
            Optional<Clan> current = clans.clan(clanId);
            if (current.isEmpty()) {
                return ClanResult.of(SocialResult.NOT_FOUND);
            }
            if (cancelled(new ClanDisbandEvent(current.get(), null))) {
                return ClanResult.of(SocialResult.CANCELLED, current.get());
            }
            ClanManager.Result result = clans.dissolve(clanId);
            if (result.ok()) {
                for (UUID member : result.clan().memberIds()) {
                    context.sendTo(member, "clan.disbanded", clanResolvers(result.clan()));
                }
            }
            return finish(result, null);
        });
    }

    public CompletableFuture<ClanResult> addMember(UUID clanId, UUID player, ClanRole role) {
        return managed(() -> {
            Optional<Clan> current = clans.clan(clanId);
            if (current.isEmpty()) {
                return ClanResult.of(SocialResult.NOT_FOUND);
            }
            if (clans.clanOf(player).isEmpty() && cancelled(new ClanJoinEvent(current.get(), player))) {
                return ClanResult.of(SocialResult.CANCELLED, current.get());
            }
            ClanManager.Result result = clans.add(clanId, player, context.nameOf(player),
                    role == null ? ClanRole.MEMBER : role, context::clanLimit, System.currentTimeMillis());
            if (result.ok()) {
                context.sendTo(player, "clan.joined-self", clanResolvers(result.clan()));
                broadcast(result.clan(), player, "clan.joined", player);
            }
            return finish(result, null);
        });
    }

    public CompletableFuture<ClanResult> removeMember(UUID clanId, UUID player) {
        return managed(() -> {
            Optional<Clan> before = clans.clan(clanId);
            ClanManager.Result result = clans.removeMember(clanId, player);
            if (result.ok()) {
                context.sendTo(player, result.disbanded() ? "clan.disbanded" : "clan.removed-self",
                        clanResolvers(result.clan()));
                if (!result.disbanded()) {
                    broadcastAs(result.clan(), "clan.left", null,
                            viewer -> named(viewer, before.orElse(result.clan()), player));
                }
            }
            return finish(result, ClanMemberLeftEvent.Reason.REMOVED);
        });
    }

    public CompletableFuture<ClanResult> setRole(UUID clanId, UUID player, ClanRole role) {
        return managed(() -> {
            ClanManager.Result result = clans.setRole(clanId, player, role);
            if (result.ok()) {
                announceRole(result.clan(), player);
            }
            return finish(result, null);
        });
    }

    public CompletableFuture<ClanResult> setColor(UUID clanId, String color) {
        return managed(() -> {
            Optional<String> normalized = ClanColors.normalize(color);
            if (normalized.isEmpty()) {
                return ClanResult.of(SocialResult.INVALID_COLOR, clans.clan(clanId).orElse(null));
            }
            return finish(clans.setColor(clanId, normalized.get()), null);
        });
    }

    public CompletableFuture<ClanResult> setAccess(UUID clanId, ClanAccess access) {
        return managed(() -> finish(clans.setAccess(clanId, access), null));
    }

    public CompletableFuture<ClanResult> setData(UUID clanId, String key, String value) {
        return managed(() -> {
            ClanManager.Result result = clans.setData(clanId, key, value);
            if (result.status() == ClanManager.Status.FULL) {
                return ClanResult.of(SocialResult.INVALID_DATA, result.clan());
            }
            return finish(result, null);
        });
    }

    public CompletableFuture<SocialResult> broadcast(UUID clanId, String message) {
        return context.result(() -> {
            ready();
            Optional<Clan> clan = clans.clan(clanId);
            if (clan.isEmpty()) {
                return SocialResult.NOT_FOUND;
            }
            Component text = context.services().formatter().format(message == null ? "" : message);
            for (UUID member : clan.get().memberIds()) {
                context.proxy().getPlayer(member).ifPresent(online -> online.sendMessage(text));
            }
            return SocialResult.SUCCESS;
        });
    }

    private CompletableFuture<ClanResult> managed(Supplier<ClanResult> task) {
        return context.supply(() -> {
            ready();
            return task.get();
        }).exceptionally(error -> {
            context.logger().warn("Clan task failed", error);
            return ClanResult.of(SocialResult.FAILED);
        });
    }

    private ClanResult finish(ClanManager.Result result, ClanMemberLeftEvent.Reason reason) {
        if (result.ok()) {
            commit(result, reason, null, true);
        }
        return ClanResult.of(result(result.status()), result.after());
    }

    public List<String> memberNames(Player viewer, String prefix, boolean invites, Predicate<ClanRole> roles) {
        Optional<Clan> clan = clans.clanOf(viewer.getUniqueId());
        if (clan.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (ClanMember member : clan.get().members()) {
            if (!member.uuid().equals(viewer.getUniqueId()) && roles.test(member.role())) {
                names.add(shown(viewer, clan.get(), member.uuid()));
            }
        }
        if (invites) {
            clan.get().invited().forEach(invited -> names.add(context.shownTo(viewer, invited)));
        }
        return FriendService.filter(names, prefix);
    }

    public List<String> invitingNames(Player viewer, String prefix) {
        List<String> names = new ArrayList<>();
        for (Clan clan : clans.invitesFor(viewer.getUniqueId(), System.currentTimeMillis())) {
            names.add(clan.name());
        }
        return FriendService.filter(names, prefix);
    }

    public List<String> clanNames(String prefix) {
        List<String> names = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (Clan clan : clans.all()) {
            if (clan.name().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(clan.name());
            }
            if (names.size() >= 50) {
                break;
            }
        }
        names.sort(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)));
        return names;
    }

    private Optional<Clan> inviting(Player player, String input) {
        for (Clan clan : clans.invitesFor(player.getUniqueId(), System.currentTimeMillis())) {
            if (clan.name().equalsIgnoreCase(input) || clan.tag().equalsIgnoreCase(input)) {
                return Optional.of(clan);
            }
        }
        return Optional.empty();
    }

    private CompletableFuture<SocialResult> byName(Player actor, String input, boolean invites,
                                                   Function<UUID, SocialResult> action) {
        return context.result(() -> {
            ready();
            Optional<Clan> clan = clans.clanOf(actor.getUniqueId());
            if (clan.isEmpty()) {
                notInClan(actor);
                return SocialResult.NOT_IN_CLAN;
            }
            List<UUID> candidates = new ArrayList<>(clan.get().memberIds());
            if (invites) {
                candidates.addAll(clan.get().invited());
            }
            for (UUID candidate : candidates) {
                if (shownWithin(actor, clan.get(), candidate).equalsIgnoreCase(input)) {
                    return action.apply(candidate);
                }
            }
            context.send(actor, "clan.not-member", Placeholder.unparsed("player", input));
            return SocialResult.NOT_MEMBER;
        });
    }

    private void loadConflicts(String name, String tag) {
        for (Clan found : store.conflicts(name, tag)) {
            if (clans.clan(found.id()).isEmpty()) {
                clans.load(found);
            }
        }
    }

    private SocialResult checkNames(Player player, String name, String tag) {
        ClanNaming naming = settings().naming();
        ClanNaming.Check nameCheck = naming.name(name);
        if (nameCheck != ClanNaming.Check.OK) {
            if (player != null) {
                naming(player, "name", nameCheck, name, naming.nameMin(), naming.nameMax());
            }
            return SocialResult.INVALID_NAME;
        }
        ClanNaming.Check tagCheck = naming.tag(tag);
        if (tagCheck != ClanNaming.Check.OK) {
            if (player != null) {
                naming(player, "tag", tagCheck, tag, naming.tagMin(), naming.tagMax());
            }
            return SocialResult.INVALID_TAG;
        }
        return null;
    }

    private void naming(Player player, String kind, ClanNaming.Check check, String value, int min, int max) {
        String suffix = switch (check) {
            case TOO_SHORT -> "too-short";
            case TOO_LONG -> "too-long";
            case CHARACTERS -> "characters";
            case BLOCKED, OK -> "blocked";
        };
        context.send(player, "clan." + kind + "-" + suffix, Placeholder.unparsed("value", value == null ? "" : value),
                Placeholder.unparsed("min", String.valueOf(min)), Placeholder.unparsed("max", String.valueOf(max)));
    }

    private void commit(ClanManager.Result result, ClanMemberLeftEvent.Reason reason, UUID actor, boolean persist) {
        if (!result.ok()) {
            return;
        }
        Clan subject = result.clan() != null ? result.clan() : result.before();
        if (persist && subject != null) {
            UUID id = subject.id();
            store.persist(id, () -> clans.clan(id));
        }
        feed.changed(result, reason, actor);
    }

    private boolean cancelled(PrismarinEvent event) {
        context.publish(event);
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    private void notInClan(Player player) {
        context.send(player, "clan.not-in-clan", Placeholder.unparsed("command", command()));
    }

    private void needsRole(Player player, ClanAction action) {
        context.send(player, "clan.needs-role", Placeholder.component("role",
                roleName(player, settings().rights().role(action))));
    }

    private void rankOrRole(Player actor, Clan clan, ClanAction action) {
        ClanRole own = clan == null ? null : clan.role(actor.getUniqueId()).orElse(null);
        if (own != null && own.atLeast(settings().rights().role(action))) {
            context.send(actor, "clan.rank-too-low");
        } else {
            needsRole(actor, action);
        }
    }

    private Component roleName(Player viewer, ClanRole role) {
        return context.render(viewer, "clan.role." + role.id());
    }

    private TagResolver[] clanResolvers(Clan clan) {
        return new TagResolver[]{Placeholder.unparsed("clan", clan.name()), Placeholder.component("tag", tag(clan))};
    }

    private static TagResolver[] with(TagResolver[] base, TagResolver... extra) {
        TagResolver[] all = new TagResolver[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    private String shown(Player viewer, Clan clan, UUID subject) {
        if (context.proxy().getPlayer(subject).isPresent()) {
            return context.shownTo(viewer, subject);
        }
        return clan.member(subject).map(ClanMember::name).filter(name -> !name.isBlank())
                .orElseGet(() -> context.nameOf(subject));
    }

    private Component named(Player viewer, Clan clan, UUID subject) {
        ClanRole role = clan.role(subject).orElse(ClanRole.MEMBER);
        return context.render(viewer, "clan.member-name." + role.id(),
                Placeholder.unparsed("player", shownWithin(viewer, clan, subject)));
    }

    private String shownWithin(Player viewer, Clan clan, UUID subject) {
        return clan.contains(subject) ? shown(viewer, clan, subject) : context.shownTo(viewer, subject);
    }

    private void broadcast(Clan clan, UUID subject, String key, UUID except) {
        broadcast(clan, subject, key, except, viewer -> List.of());
    }

    private void broadcast(Clan clan, UUID subject, String key, UUID except,
                           Function<Player, List<TagResolver>> extra) {
        broadcastAs(clan, key, except, viewer -> named(viewer, clan, subject), extra);
    }

    private void broadcastAs(Clan clan, String key, UUID except, Function<Player, Component> name) {
        broadcastAs(clan, key, except, name, viewer -> List.of());
    }

    private void broadcastAs(Clan clan, String key, UUID except, Function<Player, Component> name,
                             Function<Player, List<TagResolver>> extra) {
        for (UUID member : clan.memberIds()) {
            if (member.equals(except)) {
                continue;
            }
            context.proxy().getPlayer(member).ifPresent(online -> {
                List<TagResolver> resolvers = new ArrayList<>();
                resolvers.add(Placeholder.component("player", name.apply(online)));
                resolvers.addAll(extra.apply(online));
                context.send(online, key, resolvers.toArray(TagResolver[]::new));
            });
        }
    }
}
