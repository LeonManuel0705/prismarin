package net.prismarin.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlCommentsTest {

    private static final List<String> FILE = List.of(
            "server-id: lobby",
            "storage:",
            "  mode: LOCAL",
            "  mysql:",
            "    host: localhost",
            "economy:",
            "  currencies:",
            "  - coins",
            "  primary: coins");

    @Test
    void writesHeaderAndCommentsAboveNestedKeys() {
        List<String> annotated = YamlComments.annotate(FILE, List.of("Title", ""), Map.of(
                "storage", List.of("Storage settings"),
                "storage.mysql.host", List.of("Database host"),
                "economy.primary", List.of("Main currency")));
        assertEquals(List.of(
                "# Title",
                "#",
                "",
                "server-id: lobby",
                "",
                "# Storage settings",
                "storage:",
                "  mode: LOCAL",
                "  mysql:",
                "    # Database host",
                "    host: localhost",
                "economy:",
                "  currencies:",
                "  - coins",
                "  # Main currency",
                "  primary: coins"), annotated);
    }

    @Test
    void isIdempotent() {
        Map<String, List<String>> comments = Map.of("storage.mode", List.of("Mode"), "economy", List.of("Money"));
        List<String> once = YamlComments.annotate(FILE, List.of("Title"), comments);
        assertEquals(once, YamlComments.annotate(once, List.of("Title"), comments));
    }

    @Test
    void handlesQuotedKeysAndIgnoresValuesWithColons() {
        List<String> lines = List.of("servers:", "  \"bw-1\":", "    motd: \"Hello: world\"");
        List<String> annotated = YamlComments.annotate(lines, List.of(), Map.of("servers.bw-1.motd", List.of("Text")));
        assertEquals(List.of("servers:", "  \"bw-1\":", "    # Text", "    motd: \"Hello: world\""), annotated);
    }
}
