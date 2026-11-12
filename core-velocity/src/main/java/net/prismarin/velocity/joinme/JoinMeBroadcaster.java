package net.prismarin.velocity.joinme;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.ViewerRules;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.api.server.ServerStatusService;
import net.prismarin.api.skin.ChatFace;
import net.prismarin.api.skin.SkinFaces;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.SocialApi;
import net.prismarin.shared.joinme.JoinMeLook;
import net.prismarin.shared.joinme.JoinMePlaceholders;
import net.prismarin.shared.skin.FaceRows;
import net.prismarin.velocity.locale.ProxyMessages;
import org.slf4j.Logger;

public final class JoinMeBroadcaster {

    public static final String CLICK = "/joinme join ";
    public static final String PREVIEW_ID = "preview";
    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3),
            Duration.ofMillis(700));

    private final ProxyServer proxy;
    private final ProxyMessages messages;
    private final ServerDirectory directory;
    private final ServerStatusService status;
    private final PlayerService players;
    private final JoinMeSettings settings;
    private final Logger logger;
    private final Sound sound;
    private final SkinFaces faces;

    public JoinMeBroadcaster(ProxyServer proxy, ProxyMessages messages, ServerDirectory directory,
                             ServerStatusService status, PlayerService players, JoinMeSettings settings,
                             SkinFaces faces, Logger logger) {
        this.proxy = proxy;
        this.faces = faces;
        this.messages = messages;
        this.directory = directory;
        this.status = status;
        this.players = players;
        this.settings = settings;
        this.logger = logger;
        this.sound = sound(settings.broadcast(), logger);
    }

    public CompletableFuture<AudienceSelector.Circles> circles(JoinMe joinMe) {
        JoinMeAudience audience = joinMe.audience();
        if (!audience.social()) {
            return CompletableFuture.completedFuture(AudienceSelector.Circles.NONE);
        }
        UUID creator = joinMe.creator();
        Set<UUID> party = audience.circles().contains(JoinMeAudience.Circle.PARTY)
                ? SocialApi.parties().flatMap(parties -> parties.partyOf(creator)).map(Party::memberIds)
                .map(Set::copyOf).orElse(Set.of()) : Set.of();
        Set<UUID> clan = audience.circles().contains(JoinMeAudience.Circle.CLAN)
                ? SocialApi.clans().flatMap(clans -> clans.clanOf(creator)).map(Clan::memberIds)
                .map(Set::copyOf).orElse(Set.of()) : Set.of();
        CompletableFuture<Set<UUID>> friends = audience.circles().contains(JoinMeAudience.Circle.FRIENDS)
                ? SocialApi.friends().map(service -> service.friendIds(creator))
                .orElseGet(() -> CompletableFuture.completedFuture(Set.of()))
                : CompletableFuture.completedFuture(Set.of());
        return friends.exceptionally(error -> {
            logger.debug("Could not read the friends of {}", creator, error);
            return Set.of();
        }).thenApply(found -> new AudienceSelector.Circles(found, party, clan));
    }

    public CompletableFuture<Optional<ChatFace>> face(JoinMe joinMe, JoinMeLook look) {
        JoinMeSettings.Face face = settings.face();
        if (!face.enabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<ChatFace> found = look.hasTexture() ? faces.fromTextures(look.texture(), joinMe.creator())
                : CompletableFuture.completedFuture(faces.defaultFace(joinMe.creator()));
        return found.thenApply(chosen -> chosen.fallback() && face.textWithoutSkin() ? Optional.<ChatFace>empty()
                : Optional.of(chosen)).exceptionally(error -> face.textWithoutSkin() ? Optional.empty()
                : Optional.of(faces.defaultFace(joinMe.creator())));
    }

    public CompletableFuture<Integer> broadcast(JoinMe joinMe, JoinMeLook look) {
        return circles(joinMe).thenCombine(face(joinMe, look), (circles, face) -> {
            List<AudienceSelector.Candidate> candidates = new ArrayList<>();
            String permission = settings.audience().permission();
            for (Player player : proxy.getAllPlayers()) {
                String server = serverOf(player);
                candidates.add(new AudienceSelector.Candidate(player.getUniqueId(), server, groupOf(server),
                        players.online(player.getUniqueId()).map(NetworkPlayer::world).orElse(""),
                        permission.isEmpty() || player.hasPermission(permission)));
            }
            List<UUID> chosen = AudienceSelector.select(joinMe, candidates, circles,
                    settings.audience().skipSameWorld());
            for (UUID uuid : chosen) {
                proxy.getPlayer(uuid).ifPresent(player -> send(player, joinMe, look, face));
            }
            return chosen.size();
        });
    }

    public CompletableFuture<Boolean> preview(Player viewer, JoinMe joinMe, JoinMeLook look) {
        return face(joinMe, look).thenApply(face -> {
            if (!viewer.isActive()) {
                return false;
            }
            TagResolver resolver = resolver(viewer, joinMe, false);
            viewer.sendMessage(messages.render(viewer, "joinme.preview-header", resolver));
            Component message = face.map(found -> block(viewer, look, found, resolver))
                    .orElseGet(() -> messages.render(viewer, "joinme.broadcast", resolver))
                    .hoverEvent(HoverEvent.showText(messages.render(viewer, "joinme.preview-hover", resolver)));
            deliver(viewer, message, resolver);
            return true;
        });
    }

    public void send(Player viewer, JoinMe joinMe, JoinMeLook look, Optional<ChatFace> face) {
        TagResolver resolver = resolver(viewer, joinMe);
        Component message = face.map(found -> block(viewer, look, found, resolver))
                .orElseGet(() -> messages.render(viewer, "joinme.broadcast", resolver))
                .clickEvent(ClickEvent.runCommand(CLICK + joinMe.id()))
                .hoverEvent(HoverEvent.showText(messages.render(viewer, "joinme.broadcast-hover", resolver)));
        deliver(viewer, message, resolver);
    }

    private void deliver(Player viewer, Component message, TagResolver resolver) {
        viewer.sendMessage(message);
        if (settings.broadcast().title()) {
            viewer.showTitle(Title.title(messages.render(viewer, "joinme.title", resolver),
                    messages.render(viewer, "joinme.subtitle", resolver), TIMES));
        }
        if (sound != null) {
            try {
                viewer.playSound(sound, Sound.Emitter.self());
            } catch (RuntimeException e) {
                logger.debug("Could not play the JoinMe sound for {}", viewer.getUsername(), e);
            }
        }
    }

    public Component block(CommandSource viewer, JoinMeLook look, ChatFace face, TagResolver resolver) {
        JoinMeSettings.Face layout = settings.face();
        Map<Integer, Component> texts = new TreeMap<>();
        FaceRows.texts(layout.rows(), look.lines()).forEach((row, value) -> texts.put(row, row(viewer, value,
                resolver)));
        return FaceRows.join(FaceRows.compose(face, layout.symbol(), layout.gap(), texts, layout.blankBefore(),
                layout.blankAfter()));
    }

    private Component row(CommandSource viewer, String value, TagResolver resolver) {
        if (FaceRows.KEY.matcher(value).matches()
                && !value.equals(messages.locale().raw(value, messages.localeOf(viewer)))) {
            return messages.render(viewer, value, resolver);
        }
        return messages.locale().format(value, resolver);
    }

    public TagResolver resolver(CommandSource viewer, JoinMe joinMe) {
        boolean revealed = !(viewer instanceof Player player) || ViewerRules.revealsRealName(player.getUniqueId(),
                joinMe.creator(), player.hasPermission(PlayerService.REAL_NAME_PERMISSION));
        return resolver(viewer, joinMe, revealed);
    }

    public TagResolver resolver(CommandSource viewer, JoinMe joinMe, boolean revealed) {
        Map<String, String> values = JoinMePlaceholders.values(joinMe, revealed, System.currentTimeMillis());
        ServerStatus live = status.status(joinMe.server()).filter(ServerStatus::online).orElse(null);
        if (live != null) {
            values = JoinMePlaceholders.withStatus(values, live.players(), live.maxPlayers());
        }
        Map<String, Component> rich = new HashMap<>();
        rich.put("group", messages.locale().format(groupName(joinMe.group())));
        rich.put("server_display", messages.locale().format(directory.byName(joinMe.server())
                .map(NetworkServer::displayName).orElse(joinMe.server())));
        rich.put("state", messages.render(viewer, "joinme.state." + joinMe.state().name().toLowerCase(Locale.ROOT)));
        return JoinMePlaceholders.resolver(values, rich);
    }

    public String serverOf(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("");
    }

    public String groupOf(String server) {
        return directory.byName(server).map(NetworkServer::group).orElse(NetworkServer.GROUP_DEFAULT);
    }

    private String groupName(String group) {
        return directory.group(group).map(ServerGroup::displayName)
                .orElseGet(() -> group.isEmpty() ? "?" : group.substring(0, 1).toUpperCase(Locale.ROOT)
                        + group.substring(1));
    }

    private static Sound sound(JoinMeSettings.Broadcast broadcast, Logger logger) {
        if (broadcast.sound().isEmpty()) {
            return null;
        }
        try {
            return Sound.sound(Key.key(broadcast.sound()), Sound.Source.MASTER, broadcast.volume(), broadcast.pitch());
        } catch (RuntimeException e) {
            logger.warn("{}: broadcast.sound '{}' is no sound key", JoinMeSettings.FILE_NAME, broadcast.sound());
            return null;
        }
    }
}
