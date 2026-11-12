package net.prismarin.shared.chatfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinkMatcherTest {

    private final LinkMatcher links = new LinkMatcher(true, List.of("example.net", "*.allowed.de", "10.0.0.1"),
            ChatFilterSettings.DEFAULT_TLDS);

    private boolean hit(String text) {
        return !links.find(text).isEmpty();
    }

    @Test
    void domainsAndUrlsCount() {
        assertTrue(hit("join play.coolserver.de now"));
        assertTrue(hit("hypixel.net"));
        assertTrue(hit("discord.gg/abc123"));
        assertTrue(hit("https://some.weird.tld"));
        assertTrue(hit("WWW.SHOP.COM"));
        assertTrue(hit("mc.server.eu:25565"));
    }

    @Test
    void obfuscatedDotsAreFound() {
        assertTrue(hit("coolserver dot de"));
        assertTrue(hit("coolserver (dot) net"));
        assertTrue(hit("coolserver [.] com"));
        assertTrue(hit("coolserver .com"));
        assertTrue(hit("coolserver . com"));
        assertTrue(hit("coolserver\u3002com"));
    }

    @Test
    void ipAddressesCount() {
        assertTrue(hit("connect to 123.45.67.89"));
        assertTrue(hit("123.45.67.89:25565"));
        assertTrue(hit("123 . 45 . 67 . 89"));
        assertFalse(hit("999.45.67.89"));
        assertFalse(new LinkMatcher(false, List.of(), List.of()).find("123.45.67.89").size() > 0);
    }

    @Test
    void normalSentencesPass() {
        for (String text : List.of("e.g. this", "Version 1.21.4 is out", "ok. so what", "see you. me too",
                "hello.how are you", "i.e. later", "3.5 hearts left", "lol.xd", "mail me at name@gmail.com",
                "score 10 20 30 40", "1.2.3", "Wait...what")) {
            assertFalse(hit(text), text);
        }
    }

    @Test
    void allowedDomainsCoverTheirSubdomains() {
        assertFalse(hit("play.example.net"));
        assertFalse(hit("https://www.example.net/rules"));
        assertFalse(hit("shop.allowed.de"));
        assertFalse(hit("10.0.0.1"));
        assertTrue(hit("example.net.evil.com"));
        assertTrue(hit("notexample.net"));
    }

    @Test
    void spansPointAtTheOriginalText() {
        String text = "visit Cool.Server.DE today";
        Span span = links.find(text).getFirst();
        assertEquals("Cool.Server.DE", text.substring(span.start(), span.end()));
        assertEquals("cool.server.de", span.detail());
        String spaced = "go to coolserver dot de please";
        assertEquals("go to *** please", Span.replace(spaced, links.find(spaced), "***"));
    }
}
