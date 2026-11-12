package net.prismarin.velocity.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeCreateEvent;
import net.prismarin.api.joinme.JoinMeCreatedEvent;
import net.prismarin.api.joinme.JoinMePreviewEvent;
import net.prismarin.api.joinme.JoinMeRequest;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.joinme.JoinMeSpot;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.skin.SkinFaces;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.joinme.JoinMeDraft;
import net.prismarin.shared.joinme.JoinMeLook;
import net.prismarin.shared.messaging.LocalMessageBus;
import net.prismarin.shared.skin.DefaultFaces;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.velocity.admin.AdminContext;
import net.prismarin.velocity.admin.ModerationChannel;
import net.prismarin.velocity.admin.VanishRegistry;
import net.prismarin.velocity.locale.PlayerLocaleRegistry;
import net.prismarin.velocity.locale.ProxyMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.helpers.NOPLogger;

class ProxyJoinMePreviewTest {

    private static final UUID CREATOR = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final ProxyServer proxy = mock(ProxyServer.class);
    private final Player creator = mock(Player.class);
    private final Player other = mock(Player.class);
    private final ServerDirectory directory = mock(ServerDirectory.class);
    private final SimpleEventBus events = new SimpleEventBus();
    private final List<JoinMePreviewEvent> previews = new ArrayList<>();
    private final List<Object> creations = new ArrayList<>();
    private ProxyJoinMeService service;

    private static final class Keys implements LocaleService {

        @Override
        public Component render(String key, Locale locale, TagResolver... resolvers) {
            return Component.text(key);
        }

        @Override
        public String raw(String key, Locale locale) {
            return key;
        }

        @Override
        public Component format(String text, TagResolver... resolvers) {
            return Component.text(text);
        }
    }

    @BeforeEach
    void setUp() {
        player(creator, CREATOR, "Leon");
        player(other, OTHER, "Ben");
        when(proxy.getAllPlayers()).thenReturn(List.of(creator, other));
        when(directory.byName("lobby-1")).thenReturn(Optional.of(new NetworkServer("lobby-1", "Lobby 1", "localhost",
                25566, NetworkServer.GROUP_LOBBY, Map.of())));
        LocalMessageBus bus = new LocalMessageBus("proxy");
        VanishRegistry vanish = new VanishRegistry();
        PlayerLocaleRegistry locales = new PlayerLocaleRegistry();
        AdminContext context = new AdminContext(proxy, null, new ProxyMessages(new Keys(), Locale.ENGLISH, locales),
                vanish, new ModerationChannel(vanish, locales), mock(NetworkConnector.class), directory,
                new NetworkStatusService(directory, bus, Runnable::run), null);
        SkinFaces faces = mock(SkinFaces.class);
        when(faces.defaultFace(any())).thenReturn(DefaultFaces.forOwner(CREATOR));
        service = new ProxyJoinMeService(context, events, bus, mock(PlayerService.class), JoinMeSettings.defaults(),
                faces, Runnable::run, NOPLogger.NOP_LOGGER);
        events.subscribe(JoinMePreviewEvent.class, event -> {
            previews.add(event);
            event.line(9, "Season 3");
        });
        events.subscribe(JoinMeCreateEvent.class, creations::add);
        events.subscribe(JoinMeCreatedEvent.class, creations::add);
    }

    private void player(Player player, UUID uuid, String name) {
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn(name);
        when(player.isActive()).thenReturn(true);
        when(proxy.getPlayer(uuid)).thenReturn(Optional.of(player));
    }

    private static JoinMeDraft draft(String server, String group) {
        return JoinMeDraft.of(new JoinMeSpot(CREATOR, server, group, "arena-1", GameState.WAITING),
                JoinMeRequest.of(CREATOR)).withShownName("SilentFox").withHandler("", true)
                .withPlaceholders(Map.of("map", "Castle")).withLook(new JoinMeLook("", Map.of()));
    }

    @Test
    void onlyTheCreatorSeesThePreviewAndNothingIsStored() {
        JoinMeResult result = service.preview(draft("lobby-1", "lobby")).join();

        assertEquals(JoinMeResult.Status.SUCCESS, result.status());
        assertTrue(result.joinMe().isEmpty());
        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(creator, times(2)).sendMessage(sent.capture());
        verify(other, never()).sendMessage(any(Component.class));
        assertEquals(Component.text("joinme.preview-header"), sent.getAllValues().getFirst());
        Component block = sent.getAllValues().get(1);
        assertNull(block.clickEvent());
        assertNotNull(block.hoverEvent());
        assertEquals(Component.text("joinme.preview-hover"), block.hoverEvent().value());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(block).contains("Season 3"));
        assertEquals(1, previews.size());
        assertEquals("Castle", previews.getFirst().placeholders().get("map"));
        assertTrue(creations.isEmpty());
        assertTrue(service.active().join().isEmpty());
        assertTrue(service.check(draft("bedwars-1", "bedwars")).join().allowed());
    }

    @Test
    void thePreviewIgnoresTheCooldownAndKeepsTheRealJoinMe() {
        JoinMeResult created = service.create(draft("bedwars-1", "bedwars")).join();
        JoinMe real = created.joinMe().orElseThrow();
        assertEquals(JoinMeResult.Status.COOLDOWN, service.check(draft("bedwars-1", "bedwars")).join().status());
        clearInvocations(creator, other);
        creations.clear();

        JoinMeResult result = service.preview(draft("bedwars-1", "bedwars")).join();

        assertEquals(JoinMeResult.Status.SUCCESS, result.status());
        verify(creator, times(2)).sendMessage(any(Component.class));
        verify(other, never()).sendMessage(any(Component.class));
        assertEquals(List.of(real), service.active().join());
        assertTrue(creations.isEmpty());
    }

    @Test
    void anOfflineCreatorGetsNoPreview() {
        UUID stranger = UUID.randomUUID();
        JoinMeDraft draft = JoinMeDraft.of(new JoinMeSpot(stranger, "bedwars-1", "bedwars", "arena-1",
                GameState.WAITING), JoinMeRequest.of(stranger));

        assertEquals(JoinMeResult.Status.OFFLINE, service.preview(draft).join().status());
        verify(creator, never()).sendMessage(any(Component.class));
        verify(other, never()).sendMessage(any(Component.class));
        assertTrue(previews.isEmpty());
    }
}
