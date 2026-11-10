package net.prismarin.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.StringReader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.brand.Branding;
import net.prismarin.shared.locale.BrandingTags;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class EscapedPlaceholderTest {

    private final TextFormatter formatter = BrandingTags.formatter(Branding.DEFAULT);
    private final TagResolver command = Placeholder.unparsed("command", "/friend");

    @Test
    void anEscapedTagIsShownAsText() {
        assertEquals("/friend add <name>", plain(formatter.format("%command% add \\<name>", command)));
        assertEquals("/friend list [<page>]", plain(formatter.format("%command% list [\\<page>]", command)));
    }

    @Test
    void anEscapedTagIsNotReplacedByAPlaceholderOfTheSameName() {
        TagResolver player = Placeholder.unparsed("player", "Leon");
        assertEquals("/msg Leon", plain(formatter.format("/msg <player>", player)));
        assertEquals("/msg <player> <message>", plain(formatter.format("/msg \\<player> \\<message>", player,
                Placeholder.unparsed("message", "hi"))));
    }

    @Test
    void styleAroundTheEscapedTagStays() {
        Component result = formatter.format("<color:#22D3EE>\\<name></color>");
        assertEquals("<name>", plain(result));
        TextColor color = result.color() != null ? result.color() : result.children().getFirst().color();
        assertEquals(TextColor.fromHexString("#22D3EE"), color);
    }

    @Test
    void yamlKeepsTheBackslashForMiniMessage() throws ConfigurateException {
        String yaml = "usage: \"%command% add \\\\<name>\"\nsingle: '%command% jump \\<name>'\n";
        ConfigurationNode root = YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml))).build().load();
        assertEquals("%command% add \\<name>", root.node("usage").getString());
        assertEquals("/friend add <name>", plain(formatter.format(root.node("usage").getString(), command)));
        assertEquals("/friend jump <name>", plain(formatter.format(root.node("single").getString(), command)));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
