package net.prismarin.velocity.locale;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxyMessagesTest {

    private final RecordingLocaleService localeService = new RecordingLocaleService();
    private final ProxyMessages messages = new ProxyMessages(localeService, Locale.GERMAN);

    @Test
    void usesEffectiveLocaleOfPlayers() {
        Player player = mock(Player.class);
        when(player.getEffectiveLocale()).thenReturn(Locale.ENGLISH);
        messages.render(player, "hub.connected");
        assertEquals(Locale.ENGLISH, localeService.lastLocale);
    }

    @Test
    void fallsBackToDefaultLocaleForConsole() {
        CommandSource console = mock(CommandSource.class);
        messages.render(console, "hub.connected");
        assertEquals(Locale.GERMAN, localeService.lastLocale);
    }

    @Test
    void fallsBackWhenPlayerLocaleUnknown() {
        Player player = mock(Player.class);
        when(player.getEffectiveLocale()).thenReturn(null);
        assertEquals(Locale.GERMAN, messages.localeOf(player));
    }

    @Test
    void sendsRenderedComponentToSource() {
        Player player = mock(Player.class);
        when(player.getEffectiveLocale()).thenReturn(Locale.ENGLISH);
        messages.send(player, "hub.connected");
        verify(player).sendMessage(Component.text("hub.connected"));
    }

    private static final class RecordingLocaleService implements LocaleService {

        private Locale lastLocale;

        @Override
        public Component render(String key, Locale locale, TagResolver... resolvers) {
            lastLocale = locale;
            return Component.text(key);
        }

        @Override
        public String raw(String key, Locale locale) {
            lastLocale = locale;
            return key;
        }
    }
}
