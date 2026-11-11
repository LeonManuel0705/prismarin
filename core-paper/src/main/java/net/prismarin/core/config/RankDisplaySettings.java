package net.prismarin.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record RankDisplaySettings(String beforeName, String alone, String trim, String luckPermsMeta) {

    public static final String PREFIX = "%prefix%";
    public static final String RANK = "%rank%";
    public static final List<String> DEFAULT_TRIM = List.of("┃", "|", "│", "»", ">", "•",
            "·", ":", "-", "➤", "▏");
    public static final String DEFAULT_META = "rank-label";
    public static final RankDisplaySettings DEFAULT = new RankDisplaySettings(PREFIX, RANK,
            String.join("", DEFAULT_TRIM), DEFAULT_META);

    public RankDisplaySettings {
        beforeName = beforeName == null || beforeName.isBlank() ? PREFIX : beforeName;
        alone = alone == null || alone.isBlank() ? RANK : alone;
        trim = trim == null ? "" : trim;
        luckPermsMeta = luckPermsMeta == null ? "" : luckPermsMeta.trim();
    }

    public static RankDisplaySettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = loader.load();
            ConfigurationNode display = root.node("rank-display");
            boolean changed = fill(display.node("before-name"), PREFIX);
            changed |= fill(display.node("alone"), RANK);
            if (display.node("trim").virtual()) {
                display.node("trim").setList(String.class, DEFAULT_TRIM);
                changed = true;
            }
            changed |= fill(display.node("luckperms-meta"), DEFAULT_META);
            if (changed) {
                loader.save(root);
            }
            return new RankDisplaySettings(display.node("before-name").getString(PREFIX),
                    display.node("alone").getString(RANK),
                    String.join("", display.node("trim").getList(String.class, DEFAULT_TRIM)),
                    display.node("luckperms-meta").getString(DEFAULT_META));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean fill(ConfigurationNode node, String value) throws IOException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }
}
