package net.prismarin.core.social;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanPage;
import net.prismarin.api.social.ClanResult;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.ClanService;
import net.prismarin.api.social.SocialResult;
import net.prismarin.core.remote.PaperProxyLink;
import net.prismarin.shared.network.ClanCalls;
import net.prismarin.shared.platform.CoreServices;

@Singleton
public final class BackendClanService implements ClanService {

    private final ClanMirror mirror;
    private final ClanCalls calls;
    private final CoreServices services;

    @Inject
    public BackendClanService(ClanMirror mirror, PaperProxyLink link, CoreServices services) {
        this.mirror = mirror;
        this.calls = new ClanCalls(link);
        this.services = services;
    }

    @Override
    public Optional<Clan> clanOf(UUID player) {
        return mirror.clanOf(player);
    }

    @Override
    public Optional<Clan> clan(UUID clanId) {
        return mirror.clan(clanId);
    }

    @Override
    public CompletableFuture<Optional<Clan>> lookup(UUID player) {
        Optional<Clan> known = mirror.clanOf(player);
        if (known.isPresent()) {
            return CompletableFuture.completedFuture(known);
        }
        return calls.lookup(player).exceptionally(error -> Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<Clan>> byId(UUID clanId) {
        return calls.byId(clanId).exceptionally(error -> mirror.clan(clanId));
    }

    @Override
    public CompletableFuture<Optional<Clan>> find(String nameOrTag) {
        return calls.find(nameOrTag).exceptionally(error -> Optional.empty());
    }

    @Override
    public CompletableFuture<ClanPage> list(int page, int pageSize) {
        return calls.list(page, pageSize).exceptionally(error -> ClanPage.EMPTY);
    }

    @Override
    public ClanRights rights() {
        return mirror.rights();
    }

    @Override
    public Component formatTag(Clan clan) {
        return mirror.display().render(services.formatter(), clan);
    }

    public Component formatTag(String tag, String name, String color) {
        return mirror.display().render(services.formatter(), tag, name, color);
    }

    @Override
    public CompletableFuture<SocialResult> create(UUID founder, String name, String tag) {
        return calls.act("create", founder, null, name, tag, null);
    }

    @Override
    public CompletableFuture<SocialResult> invite(UUID actor, UUID target) {
        return calls.act("invite", actor, target, null, null, null);
    }

    @Override
    public CompletableFuture<SocialResult> join(UUID player, UUID clanId) {
        return calls.act("join", player, clanId, null, null, null);
    }

    @Override
    public CompletableFuture<SocialResult> leave(UUID player) {
        return calls.act("leave", player, null, null, null, null);
    }

    @Override
    public CompletableFuture<SocialResult> kick(UUID actor, UUID target) {
        return calls.act("kick", actor, target, null, null, null);
    }

    @Override
    public CompletableFuture<SocialResult> promote(UUID actor, UUID target) {
        return calls.act("promote", actor, target, null, null, null);
    }

    @Override
    public CompletableFuture<SocialResult> demote(UUID actor, UUID target) {
        return calls.act("demote", actor, target, null, null, null);
    }

    @Override
    public CompletableFuture<SocialResult> transfer(UUID actor, UUID target) {
        return calls.act("transfer", actor, target, null, null, null);
    }

    @Override
    public CompletableFuture<SocialResult> disband(UUID actor) {
        return calls.act("disband", actor, null, null, null, null);
    }

    @Override
    public CompletableFuture<SocialResult> changeColor(UUID actor, String color) {
        return calls.act("color", actor, null, null, null, color == null ? "" : color);
    }

    @Override
    public CompletableFuture<SocialResult> changeAccess(UUID actor, ClanAccess access) {
        if (access == null) {
            return CompletableFuture.completedFuture(SocialResult.FAILED);
        }
        return calls.act("access", actor, null, null, null, access.id());
    }

    @Override
    public CompletableFuture<SocialResult> chat(UUID sender, String message) {
        if (message == null || message.isBlank()) {
            return CompletableFuture.completedFuture(SocialResult.FAILED);
        }
        return calls.act("chat", sender, null, null, null, message);
    }

    @Override
    public CompletableFuture<ClanResult> createClan(UUID leader, String name, String tag) {
        return calls.manage("create", null, leader, name, tag, null);
    }

    @Override
    public CompletableFuture<ClanResult> renameClan(UUID clanId, String name, String tag) {
        return calls.manage("rename", clanId, null, name, tag, null);
    }

    @Override
    public CompletableFuture<ClanResult> disbandClan(UUID clanId) {
        return calls.manage("disband", clanId, null, null, null, null);
    }

    @Override
    public CompletableFuture<ClanResult> addMember(UUID clanId, UUID player, ClanRole role) {
        return calls.manage("add", clanId, player, null, null, (role == null ? ClanRole.MEMBER : role).id());
    }

    @Override
    public CompletableFuture<ClanResult> removeMember(UUID clanId, UUID player) {
        return calls.manage("remove", clanId, player, null, null, null);
    }

    @Override
    public CompletableFuture<ClanResult> setRole(UUID clanId, UUID player, ClanRole role) {
        if (role == null) {
            return CompletableFuture.completedFuture(ClanResult.of(SocialResult.FAILED));
        }
        return calls.manage("role", clanId, player, null, null, role.id());
    }

    @Override
    public CompletableFuture<ClanResult> setColor(UUID clanId, String color) {
        return calls.manage("color", clanId, null, null, null, color == null ? "" : color);
    }

    @Override
    public CompletableFuture<ClanResult> setAccess(UUID clanId, ClanAccess access) {
        if (access == null) {
            return CompletableFuture.completedFuture(ClanResult.of(SocialResult.FAILED));
        }
        return calls.manage("access", clanId, null, null, null, access.id());
    }

    @Override
    public CompletableFuture<ClanResult> setData(UUID clanId, String key, String value) {
        return calls.manage("data", clanId, null, key, null, value);
    }

    @Override
    public CompletableFuture<SocialResult> broadcast(UUID clanId, String message) {
        return calls.manage("broadcast", clanId, null, null, null, message == null ? "" : message)
                .thenApply(ClanResult::result);
    }
}
