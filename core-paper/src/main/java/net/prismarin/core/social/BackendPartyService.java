package net.prismarin.core.social;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyService;
import net.prismarin.api.social.PartySettings;
import net.prismarin.api.social.SocialResult;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.shared.social.PartyCodec;
import org.bukkit.entity.Player;

@Singleton
public final class BackendPartyService implements PartyService {

    private final PrismarinCorePlugin plugin;
    private final SocialClient client;
    private final PartyMirror mirror;

    @Inject
    public BackendPartyService(PrismarinCorePlugin plugin, SocialClient client, PartyMirror mirror) {
        this.plugin = plugin;
        this.client = client;
        this.mirror = mirror;
    }

    @Override
    public Optional<Party> partyOf(UUID player) {
        return mirror.partyOf(player);
    }

    @Override
    public Optional<Party> party(UUID partyId) {
        return mirror.party(partyId);
    }

    @Override
    public CompletableFuture<Optional<Party>> lookup(UUID player) {
        Optional<Party> known = mirror.partyOf(player);
        if (known.isPresent()) {
            return CompletableFuture.completedFuture(known);
        }
        Player carrier = carrier(player);
        if (carrier == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return client.query(carrier, "party", player, null)
                .thenApply(answer -> PartyCodec.decode(answer.get("party")))
                .exceptionally(error -> Optional.empty());
    }

    @Override
    public CompletableFuture<SocialResult> invite(UUID actor, UUID target) {
        return act(actor, "party-invite", target, null);
    }

    @Override
    public CompletableFuture<SocialResult> join(UUID player, UUID member) {
        return act(player, "party-join", member, null);
    }

    @Override
    public CompletableFuture<SocialResult> leave(UUID player) {
        return act(player, "party-leave", null, null);
    }

    @Override
    public CompletableFuture<SocialResult> kick(UUID actor, UUID target) {
        return act(actor, "party-kick", target, null);
    }

    @Override
    public CompletableFuture<SocialResult> promote(UUID actor, UUID target) {
        return act(actor, "party-promote", target, null);
    }

    @Override
    public CompletableFuture<SocialResult> demote(UUID actor, UUID target) {
        return act(actor, "party-demote", target, null);
    }

    @Override
    public CompletableFuture<SocialResult> transfer(UUID actor, UUID target) {
        return act(actor, "party-transfer", target, null);
    }

    @Override
    public CompletableFuture<SocialResult> disband(UUID actor) {
        return act(actor, "party-disband", null, null);
    }

    @Override
    public CompletableFuture<SocialResult> warp(UUID actor) {
        return act(actor, "party-warp", null, null);
    }

    @Override
    public CompletableFuture<SocialResult> changeSettings(UUID actor, PartySettings settings) {
        if (settings == null) {
            return CompletableFuture.completedFuture(SocialResult.FAILED);
        }
        return act(actor, "party-setting", null, options(settings));
    }

    static JsonObject options(PartySettings settings) {
        JsonObject options = new JsonObject();
        options.addProperty("invite", settings.invite().id());
        options.addProperty("access", settings.access().id());
        options.addProperty("follow", settings.follow() ? "on" : "off");
        options.addProperty("chat", settings.chat().id());
        return options;
    }

    private CompletableFuture<SocialResult> act(UUID actor, String action, UUID target, JsonObject options) {
        if (actor == null || "party-setting".equals(action) && options == null) {
            return CompletableFuture.completedFuture(SocialResult.FAILED);
        }
        Player player = plugin.getServer().getPlayer(actor);
        if (player == null) {
            return CompletableFuture.completedFuture(SocialResult.UNAVAILABLE);
        }
        return client.perform(player, action, target, options);
    }

    private Player carrier(UUID preferred) {
        Player own = plugin.getServer().getPlayer(preferred);
        if (own != null) {
            return own;
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            return online;
        }
        return null;
    }
}
