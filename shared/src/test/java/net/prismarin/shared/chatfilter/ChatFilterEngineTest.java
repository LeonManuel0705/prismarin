package net.prismarin.shared.chatfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.moderation.ChatFilterAction;
import net.prismarin.api.moderation.ChatFilterMatch;
import net.prismarin.api.moderation.ChatFilterResult;
import net.prismarin.api.moderation.ChatFilterRule;
import net.prismarin.api.moderation.ChatMessage;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class ChatFilterEngineTest {

    private final UUID player = UUID.randomUUID();
    private final TestClock clock = new TestClock(1_000_000L);

    static ChatFilterSettings settings(String yaml) {
        try {
            ConfigurationNode root = YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .build()
                    .load();
            ChatFilterSettings.fillDefaults(root, false);
            return ChatFilterSettings.read(root, false, new ArrayList<>());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ChatFilterEngine engine(String yaml) {
        return new ChatFilterEngine(settings(yaml), clock);
    }

    private ChatFilterResult say(ChatFilterEngine engine, String text) {
        return engine.filter(ChatMessage.of(player, "Steve", text), rule -> false);
    }

    private static ChatFilterRule rule(String id, ChatFilterMatch match) {
        return new ChatFilterRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Optional<ChatFilterMatch> check(ChatMessage message) {
                return message.text().contains("bedwars") ? Optional.of(match) : Optional.empty();
            }
        };
    }

    @Test
    void cleanMessagesPass() {
        ChatFilterEngine engine = engine("");
        ChatFilterResult result = say(engine, "Hello everyone, ready for a round of Bedwars?");
        assertTrue(result.clean());
        assertFalse(result.blocked());
        assertEquals(result.original(), result.text());
    }

    @Test
    void insultsAreBlockedByDefault() {
        ChatFilterResult result = say(engine(""), "du Hurensohn");
        assertTrue(result.blocked());
        assertEquals(List.of(ChatFilterSettings.WORDS), result.rules());
        assertEquals("*hurensohn*", result.blocking().orElseThrow().detail());
    }

    @Test
    void capitalsAreWrittenSmall() {
        ChatFilterResult result = say(engine(""), "HELLO EVERYONE HOW ARE YOU");
        assertFalse(result.blocked());
        assertTrue(result.changed());
        assertEquals("Hello everyone how are you", result.text());
        assertEquals(ChatFilterAction.REPLACE, result.matches().getFirst().action());
    }

    @Test
    void advertisingIsBlocked() {
        ChatFilterResult result = say(engine(""), "join coolserver.de for free ranks");
        assertTrue(result.blocked());
        assertEquals("coolserver.de", result.blocking().orElseThrow().detail());
    }

    @Test
    void floodingStopsTheFifthMessage() {
        ChatFilterEngine engine = engine("");
        for (String text : List.of("hello there", "how are you", "nice build", "lets play bedwars")) {
            assertTrue(say(engine, text).clean(), text);
        }
        ChatFilterResult flood = say(engine, "anyone online");
        assertTrue(flood.blocked());
        assertEquals(List.of(ChatFilterSettings.FLOOD), flood.rules());
        clock.advance(6_000L);
        assertTrue(say(engine, "anyone online").clean());
    }

    @Test
    void repeatedMessagesAreBlockedForAWhile() {
        ChatFilterEngine engine = engine("");
        assertTrue(say(engine, "anyone wants to team?").clean());
        clock.advance(2_000L);
        ChatFilterResult again = say(engine, "Anyone wants to team??");
        assertTrue(again.blocked());
        assertEquals(List.of(ChatFilterSettings.REPEAT), again.rules());
        clock.advance(31_000L);
        assertTrue(say(engine, "anyone wants to team?").clean());
    }

    @Test
    void privateMessagesMayRepeatByDefault() {
        ChatFilterEngine engine = engine("");
        ChatMessage message = new ChatMessage(player, "Steve", ChatMessage.PRIVATE, "see you in the lobby");
        assertTrue(engine.filter(message, rule -> false).clean());
        clock.advance(2_000L);
        assertTrue(engine.filter(message, rule -> false).clean());
    }

    @Test
    void bypassedRulesAreSkipped() {
        ChatFilterEngine engine = engine("");
        ChatFilterResult result = engine.filter(ChatMessage.of(player, "Steve", "HELLO EVERYONE HOW ARE YOU"),
                ChatFilterSettings.CAPS::equals);
        assertTrue(result.clean());
    }

    @Test
    void checkingTextIgnoresFloodAndRepeat() {
        ChatFilterEngine engine = engine("");
        for (int round = 0; round < 10; round++) {
            assertTrue(engine.check("this is a normal sentence").clean());
        }
        assertTrue(engine.check("du hurensohn").blocked());
        assertTrue(engine.check("   ").clean());
    }

    @Test
    void warnLetsTheMessageThrough() {
        ChatFilterResult result = say(engine("words:\n  action: warn\n"), "du hurensohn");
        assertFalse(result.blocked());
        assertTrue(result.warned());
        assertEquals("du hurensohn", result.text());
    }

    @Test
    void replaceMasksTheWord() {
        ChatFilterResult result = say(engine("words:\n  action: replace\nreplacement: \"#\"\n"), "du hurensohn!");
        assertFalse(result.blocked());
        assertEquals("du #!", result.text());
    }

    @Test
    void logOnlyRecordsTheHit() {
        ChatFilterResult result = say(engine("links:\n  action: log\n"), "visit coolserver.de");
        assertFalse(result.blocked());
        assertFalse(result.changed());
        assertEquals(ChatFilterAction.LOG, result.matches().getFirst().action());
    }

    @Test
    void disabledRulesDoNothing() {
        ChatFilterResult result = say(engine("words:\n  enabled: false\n"), "du hurensohn");
        assertTrue(result.clean());
        assertFalse(engine("words:\n  enabled: false\n").rules().contains(ChatFilterSettings.WORDS));
    }

    @Test
    void customRulesJoinTheFilter() {
        ChatFilterEngine engine = engine("");
        engine.register(rule("no-bedwars", ChatFilterMatch.block("no-bedwars", "bedwars")));
        assertTrue(engine.rules().contains("no-bedwars"));
        assertEquals(List.of("no-bedwars"), say(engine, "who plays bedwars").rules());
        engine.unregister("no-bedwars");
        clock.advance(60_000L);
        assertTrue(say(engine, "who plays bedwars").clean());
        assertThrows(IllegalArgumentException.class, () -> engine.register(rule("words",
                ChatFilterMatch.block("words", ""))));
        assertThrows(IllegalArgumentException.class, () -> engine.register(rule("Bad Id", ChatFilterMatch.block("x",
                ""))));
    }

    @Test
    void customRulesSeeTheTextAfterEarlierReplacements() {
        ChatFilterEngine engine = engine("words:\n  action: replace\n");
        List<String> seen = new ArrayList<>();
        engine.register(new ChatFilterRule() {
            @Override
            public String id() {
                return "spy";
            }

            @Override
            public Optional<ChatFilterMatch> check(ChatMessage message) {
                seen.add(message.text());
                return Optional.empty();
            }
        });
        say(engine, "du hurensohn");
        assertEquals(List.of("du ***"), seen);
    }

    @Test
    void configuredPoliciesOverrideCustomActions() {
        ChatFilterEngine engine = engine("custom:\n  shout:\n    action: warn\n");
        engine.register(rule("shout", ChatFilterMatch.block("shout", "bedwars")));
        ChatFilterResult result = say(engine, "bedwars is great");
        assertFalse(result.blocked());
        assertTrue(result.warned());
    }

    @Test
    void failingCustomRulesAreIgnored() {
        ChatFilterEngine engine = engine("");
        engine.register(new ChatFilterRule() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public Optional<ChatFilterMatch> check(ChatMessage message) {
                throw new IllegalStateException("boom");
            }
        });
        assertTrue(say(engine, "hello there").clean());
    }

    @Test
    void forgettingAPlayerClearsTheHistory() {
        ChatFilterEngine engine = engine("");
        assertTrue(say(engine, "anyone wants to team?").clean());
        engine.forget(player);
        assertTrue(say(engine, "anyone wants to team?").clean());
    }

    @Test
    void severalRulesCanHitOneMessage() {
        ChatFilterResult result = say(engine(""), "DU HURENSOHN, JOIN COOLSERVER.DE");
        assertTrue(result.blocked());
        assertEquals(List.of(ChatFilterSettings.WORDS, ChatFilterSettings.LINKS, ChatFilterSettings.CAPS),
                result.rules());
    }
}
