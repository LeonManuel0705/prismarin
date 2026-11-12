package net.prismarin.velocity.social;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class PresenceTracker {

    private final SocialContext context;
    private final FriendService friends;
    private final PartyService party;
    private final MessageService messages;
    private final ClanService clans;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();

    public PresenceTracker(SocialContext context, FriendService friends, PartyService party,
                           MessageService messages, ClanService clans) {
        this.context = context;
        this.friends = friends;
        this.party = party;
        this.messages = messages;
        this.clans = clans;
        context.vanish().onChange(this::vanishChanged);
    }

    public void start() {
        context.async(() -> context.store().closeStalePresence(System.currentTimeMillis()));
    }

    @Subscribe
    public void onConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (context.partyEnabled()) {
            context.async(() -> party.arrived(player));
        }
        if (clans != null) {
            context.async(() -> clans.arrived(player));
        }
        if (!context.announced(uuid)) {
            if (pending.add(uuid)) {
                context.later(() -> announce(player), context.settings().friends().joinDelaySeconds(),
                        TimeUnit.SECONDS);
            }
            return;
        }
        if (shown.contains(uuid)) {
            context.async(() -> savePresence(player, true));
        }
        if (event.getPreviousServer() != null && context.partyEnabled()) {
            player.getCurrentServer().ifPresent(connection -> party.follow(player, connection.getServer()));
        }
    }

    private void announce(Player player) {
        UUID uuid = player.getUniqueId();
        pending.remove(uuid);
        if (!player.isActive()) {
            return;
        }
        context.announce(uuid);
        if (context.partyEnabled()) {
            context.async(() -> party.reconnected(player));
        }
        if (context.vanish().vanished(uuid)) {
            return;
        }
        goOnline(player);
        if (context.friendsEnabled() && context.settings().friends().joinSummary()) {
            context.async(() -> {
                int count = friends.incoming(player).size();
                if (count > 0) {
                    Component summary = context.render(player, "friends.join-summary",
                            Placeholder.unparsed("count", String.valueOf(count)));
                    if (context.available(SocialSetting.Feature.FRIENDS, "requests")) {
                        summary = summary.clickEvent(ClickEvent.runCommand(friends.command() + " requests"))
                                .hoverEvent(HoverEvent.showText(context.render(player,
                                        "friends.join-summary-hover")));
                    }
                    player.sendMessage(summary);
                }
            });
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        pending.remove(uuid);
        context.unannounce(uuid);
        if (shown.remove(uuid)) {
            notifyFriends(player, "friends.left");
            context.async(() -> savePresence(player, false));
        }
        if (context.partyEnabled()) {
            context.async(() -> party.disconnected(player));
        }
        messages.forget(uuid);
        context.async(() -> context.store().forget(uuid));
    }

    private void vanishChanged(UUID uuid, boolean vanished) {
        context.proxy().getPlayer(uuid).ifPresent(player -> {
            if (!context.announced(uuid) || !player.isActive()) {
                return;
            }
            if (vanished && shown.remove(uuid)) {
                notifyFriends(player, "friends.left");
                context.async(() -> savePresence(player, false));
            } else if (!vanished && !shown.contains(uuid)) {
                goOnline(player);
            }
        });
    }

    private void goOnline(Player player) {
        if (!shown.add(player.getUniqueId())) {
            return;
        }
        notifyFriends(player, "friends.joined");
        context.async(() -> savePresence(player, true));
    }

    private void savePresence(Player player, boolean online) {
        context.store().savePresence(new SocialStore.Presence(player.getUniqueId(), player.getUsername(),
                online ? context.serverOf(player) : "", online, System.currentTimeMillis()));
    }

    private void notifyFriends(Player player, String key) {
        if (!context.friendsEnabled()) {
            return;
        }
        context.async(() -> {
            SocialProfile profile = context.store().cached(player.getUniqueId());
            for (UUID friend : profile.friendIds()) {
                context.proxy().getPlayer(friend).ifPresent(online -> {
                    SocialProfile theirs = context.store().cached(friend);
                    if (SocialSetting.ON.equals(context.setting(theirs, SocialSetting.NOTIFICATIONS))) {
                        context.send(online, key, Placeholder.unparsed("player", player.getUsername()));
                    }
                });
            }
        });
    }
}
