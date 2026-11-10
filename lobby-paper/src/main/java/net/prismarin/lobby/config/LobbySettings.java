package net.prismarin.lobby.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.prismarin.lobby.action.ItemAction;
import net.prismarin.lobby.action.ItemClick;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.status.LobbySelector.Strategy;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record LobbySettings(Map<String, Boolean> features, List<String> worlds, Strategy distribution,
                            boolean premiumAutoJoin, List<WorldLobby> lobbies, Map<String, HotbarItem> items,
                            List<CustomItem> customItems, ProfileLayout profile, List<String> warnings) {

    public static final String NAVIGATOR = "navigator";
    public static final String LOBBY_SELECTOR = "lobby-selector";
    public static final String PROFILE_MENU = "profile-menu";
    public static final String PLAYER_HIDER = "player-hider";
    public static final String PREMIUM_LOBBY = "premium-lobby";
    public static final String CUSTOM_ITEMS = "custom-items";
    public static final String DOUBLE_JUMP = "double-jump";
    public static final String PROTECTION = "protection";
    public static final String VOID_TELEPORT = "void-teleport";
    public static final String SPAWN_ON_JOIN = "spawn-on-join";
    public static final String LOBBY_DISTRIBUTION = "lobby-distribution";
    public static final String SIDEBAR = "sidebar";
    public static final String JOIN_TITLE = "join-title";
    public static final String NICK_TOOL = "nick-tool";
    public static final String SILENT_LOBBY = "silent-lobby";

    public static final List<String> ALL = List.of(NAVIGATOR, LOBBY_SELECTOR, PROFILE_MENU, PLAYER_HIDER,
            PREMIUM_LOBBY, CUSTOM_ITEMS, DOUBLE_JUMP, PROTECTION, VOID_TELEPORT, SPAWN_ON_JOIN, LOBBY_DISTRIBUTION,
            SIDEBAR, JOIN_TITLE, NICK_TOOL, SILENT_LOBBY);

    public static final String NAVIGATOR_ITEM = "navigator";
    public static final String LOBBY_SELECTOR_ITEM = "lobby-selector";
    public static final String PROFILE_ITEM = "profile";
    public static final String PREMIUM_ITEM = "premium-lobby";
    public static final String HIDER_ITEM = "player-hider";
    public static final String NICK_TOOL_ITEM = "nick-tool";
    public static final String SILENT_ITEM = "silent-lobby";
    public static final String SILENT_PERMISSION = "prismarin.lobby.silent";

    public static final String DEFAULT_WORLD = "world";
    public static final Strategy DEFAULT_DISTRIBUTION = Strategy.LEAST_PLAYERS;
    public static final String PREMIUM_PERMISSION = "prismarin.lobby.premium";

    static final List<HotbarItem> DEFAULT_ITEMS = List.of(
            new HotbarItem(NAVIGATOR_ITEM, true, 0, "COMPASS", "", ""),
            new HotbarItem(LOBBY_SELECTOR_ITEM, true, 1, "NETHER_STAR", "", ""),
            new HotbarItem(PROFILE_ITEM, true, 4, "PLAYER_HEAD", "", ""),
            new HotbarItem(PREMIUM_ITEM, true, 7, "TNT", "", ""),
            new HotbarItem(HIDER_ITEM, true, 8, "LIME_DYE", "GRAY_DYE", ""),
            new HotbarItem(NICK_TOOL_ITEM, true, 2, "NAME_TAG", "", "", ""),
            new HotbarItem(SILENT_ITEM, true, 6, "AMETHYST_SHARD", "ECHO_SHARD", "", SILENT_PERMISSION));

    private static final int HOTBAR_SLOTS = 9;

    private static final List<String> HEADER = header();
    private static final Map<String, List<String>> COMMENTS = comments();

    public LobbySettings {
        features = Map.copyOf(features);
        worlds = List.copyOf(worlds);
        lobbies = List.copyOf(lobbies);
        items = Map.copyOf(items);
        customItems = List.copyOf(customItems);
        warnings = List.copyOf(warnings);
    }

    public boolean enabled(String feature) {
        return features.getOrDefault(feature, true);
    }

    public HotbarItem item(String id) {
        HotbarItem item = items.get(id);
        if (item == null) {
            throw new IllegalArgumentException("unknown built in item " + id);
        }
        return item;
    }

    public Optional<WorldLobby> lobby(String id) {
        return lobbies.stream().filter(lobby -> lobby.id().equalsIgnoreCase(id)).findFirst();
    }

    public static LobbySettings load(Path file) {
        return load(file, DEFAULT_WORLD);
    }

    public static LobbySettings load(Path file, String defaultWorld) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            if (writeDefaults(root, defaultWorld == null || defaultWorld.isBlank() ? DEFAULT_WORLD : defaultWorld)) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean writeDefaults(CommentedConfigurationNode root, String defaultWorld)
            throws SerializationException {
        boolean changed = false;
        for (String feature : ALL) {
            changed |= fill(root.node("features", feature), true);
        }
        CommentedConfigurationNode worlds = root.node("worlds");
        if (worlds.virtual()) {
            worlds.setList(String.class, List.of(defaultWorld));
            changed = true;
        }
        changed |= fill(root.node("lobbies", "distribution"), DEFAULT_DISTRIBUTION.id());
        changed |= fill(root.node("lobbies", "premium-auto-join"), false);
        CommentedConfigurationNode lobbies = root.node("lobbies", "worlds");
        if (lobbies.virtual()) {
            writeLobby(lobbies.node("main"), new WorldLobby("main", true, defaultWorld, "&#22D3EELobby",
                    "PRISMARINE", 50, ""));
            writeLobby(lobbies.node("premium"), new WorldLobby("premium", false, "premium",
                    "&#FBBF24Premium Lobby", "GOLD_BLOCK", 30, PREMIUM_PERMISSION));
            changed = true;
        }
        for (HotbarItem item : DEFAULT_ITEMS) {
            CommentedConfigurationNode node = root.node("items", item.id());
            changed |= fill(node.node("enabled"), item.enabled());
            changed |= fill(node.node("slot"), item.slot());
            changed |= fill(node.node("material"), item.material());
            if (HIDER_ITEM.equals(item.id())) {
                changed |= fill(node.node("hidden-material"), item.hiddenMaterial());
            }
            if (PREMIUM_ITEM.equals(item.id())) {
                changed |= fill(node.node("target"), "");
            }
            if (SILENT_ITEM.equals(item.id())) {
                changed |= fill(node.node("active-material"), item.hiddenMaterial());
            }
            if (NICK_TOOL_ITEM.equals(item.id()) || SILENT_ITEM.equals(item.id())) {
                changed |= fill(node.node("permission"), item.permission());
            }
        }
        CommentedConfigurationNode custom = root.node("custom-items");
        if (custom.virtual()) {
            writeExampleItem(custom.node("example"));
            changed = true;
        }
        CommentedConfigurationNode head = root.node("profile", "head");
        if (head.virtual()) {
            head.setList(String.class, ProfileLayout.DEFAULT_HEAD);
            changed = true;
        }
        CommentedConfigurationNode menu = root.node("profile", "menu");
        ProfileLayout defaults = ProfileLayout.DEFAULT;
        if (ProfileLayout.classicDefaults(stored(menu, "rows"), stored(menu, "head-slot"), stored(menu, "entry-row"),
                stored(menu, "back-slot"), stored(menu, "close-slot"))) {
            menu.node("rows").set(defaults.rows());
            menu.node("head-slot").set(defaults.headSlot());
            menu.node("entry-row").set(defaults.entryRow());
            menu.node("action-row").set(defaults.actionRow());
            menu.node("back-slot").set(defaults.backSlot());
            menu.node("close-slot").set(defaults.closeSlot());
            changed = true;
        }
        changed |= fill(menu.node("rows"), defaults.rows());
        changed |= fill(menu.node("head-slot"), defaults.headSlot());
        changed |= fill(menu.node("entry-row"), defaults.entryRow());
        changed |= fill(menu.node("action-row"), defaults.actionRow());
        changed |= fill(menu.node("back-slot"), defaults.backSlot());
        changed |= fill(menu.node("close-slot"), defaults.closeSlot());
        changed |= fill(menu.node("language-click"), ProfileLayout.AUTO);
        CommentedConfigurationNode entries = menu.node("entries");
        if (entries.virtual()) {
            for (Map.Entry<String, String> entry : ProfileLayout.DEFAULT_ENTRIES.entrySet()) {
                entries.node(entry.getKey()).set(entry.getValue());
            }
            changed = true;
        } else {
            for (ProfileLayout.Entry.Kind kind : ProfileLayout.Entry.Kind.values()) {
                if (kind.social()) {
                    changed |= fill(entries.node(kind.id()), ProfileLayout.AUTO);
                }
            }
        }
        return changed;
    }

    private static void writeLobby(CommentedConfigurationNode node, WorldLobby lobby) throws SerializationException {
        node.node("enabled").set(lobby.enabled());
        node.node("world").set(lobby.world());
        node.node("display-name").set(lobby.displayName());
        node.node("icon").set(lobby.icon());
        node.node("soft-max-players").set(lobby.softMaxPlayers());
        node.node("permission").set(lobby.permission());
    }

    private static void writeExampleItem(CommentedConfigurationNode node) throws SerializationException {
        node.node("enabled").set(false);
        node.node("slot").set(2);
        node.node("material").set("EMERALD");
        node.node("name").set("&#2DD4BF◆ Shop &8› &7Right click");
        node.node("lore").setList(String.class, List.of("&7Right click opens the shop.",
                "&7Left click shows your coins."));
        node.node("glow").set(true);
        node.node("custom-model-data").set(0);
        node.node("permission").set("");
        node.node("actions", ItemClick.RIGHT_CLICK.id()).setList(String.class, List.of(
                "sound: entity.experience_orb.pickup 0.6 1.2",
                "player: shop"));
        node.node("actions", ItemClick.LEFT_CLICK.id()).setList(String.class, List.of(
                "player: coins"));
        node.node("actions", ItemClick.SHIFT_RIGHT_CLICK.id()).setList(String.class, List.of(
                "message: &7Hello &#22D3EE%player%&7, have fun!"));
    }

    private static int stored(CommentedConfigurationNode menu, String key) {
        CommentedConfigurationNode node = menu.node(key);
        return node.virtual() ? Integer.MIN_VALUE : node.getInt(Integer.MIN_VALUE);
    }

    private static boolean fill(CommentedConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }

    private static LobbySettings read(ConfigurationNode root) {
        List<String> warnings = new ArrayList<>();
        Map<String, Boolean> features = new LinkedHashMap<>();
        for (String feature : ALL) {
            features.put(feature, root.node("features", feature).getBoolean(true));
        }
        Set<String> worlds = new LinkedHashSet<>();
        for (String world : strings(root.node("worlds"), "worlds", warnings)) {
            if (!world.isBlank()) {
                worlds.add(world.strip());
            }
        }
        String strategyName = root.node("lobbies", "distribution").getString(DEFAULT_DISTRIBUTION.id());
        Strategy distribution = Strategy.parse(strategyName).orElseGet(() -> {
            warnings.add("lobbies.distribution: unknown strategy '" + strategyName + "', using least-players");
            return DEFAULT_DISTRIBUTION;
        });
        boolean premiumAutoJoin = root.node("lobbies", "premium-auto-join").getBoolean(false);
        List<WorldLobby> lobbies = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry
                : root.node("lobbies", "worlds").childrenMap().entrySet()) {
            String id = entry.getKey().toString();
            ConfigurationNode node = entry.getValue();
            lobbies.add(new WorldLobby(id,
                    node.node("enabled").getBoolean(true),
                    node.node("world").getString(id),
                    node.node("display-name").getString(id),
                    node.node("icon").getString("PRISMARINE"),
                    node.node("soft-max-players").getInt(0),
                    node.node("permission").getString("")));
        }
        Map<String, HotbarItem> items = new LinkedHashMap<>();
        for (HotbarItem fallback : DEFAULT_ITEMS) {
            ConfigurationNode node = root.node("items", fallback.id());
            int slot = node.node("slot").getInt(fallback.slot());
            if (slot < 0 || slot >= HOTBAR_SLOTS) {
                warnings.add("items." + fallback.id() + ".slot: " + slot + " is no hotbar slot (0-8), using "
                        + fallback.slot());
                slot = fallback.slot();
            }
            String alternative = SILENT_ITEM.equals(fallback.id()) ? "active-material" : "hidden-material";
            items.put(fallback.id(), new HotbarItem(fallback.id(),
                    node.node("enabled").getBoolean(fallback.enabled()),
                    slot,
                    node.node("material").getString(fallback.material()),
                    node.node(alternative).getString(fallback.hiddenMaterial()),
                    node.node("target").getString(fallback.target()),
                    node.node("permission").getString(fallback.permission())));
        }
        List<CustomItem> customItems = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("custom-items").childrenMap().entrySet()) {
            customItem(entry.getKey().toString(), entry.getValue(), warnings).ifPresent(customItems::add);
        }
        ProfileLayout profile = profile(root.node("profile"), warnings);
        warnings.addAll(slotConflicts(items, customItems));
        return new LobbySettings(features, List.copyOf(worlds), distribution, premiumAutoJoin, lobbies, items,
                customItems, profile, warnings);
    }

    private static List<String> slotConflicts(Map<String, HotbarItem> items, List<CustomItem> customItems) {
        Map<Integer, List<String>> bySlot = new TreeMap<>();
        items.values().stream().filter(HotbarItem::enabled)
                .forEach(item -> bySlot.computeIfAbsent(item.slot(), slot -> new ArrayList<>()).add(item.id()));
        customItems.stream().filter(CustomItem::enabled)
                .forEach(item -> bySlot.computeIfAbsent(item.slot(), slot -> new ArrayList<>())
                        .add("custom-items." + item.id()));
        List<String> conflicts = new ArrayList<>();
        bySlot.forEach((slot, ids) -> {
            if (ids.size() > 1) {
                conflicts.add("hotbar slot " + slot + " is used by " + String.join(", ", ids)
                        + ", the last one wins");
            }
        });
        return conflicts;
    }

    private static Optional<CustomItem> customItem(String id, ConfigurationNode node, List<String> warnings) {
        String path = "custom-items." + id;
        int slot = node.node("slot").getInt(-1);
        if (slot < 0 || slot >= HOTBAR_SLOTS) {
            warnings.add(path + ".slot: needs a hotbar slot from 0 to 8, the item is skipped");
            return Optional.empty();
        }
        Map<ItemClick, List<ItemAction>> actions = new EnumMap<>(ItemClick.class);
        ConfigurationNode actionNode = node.node("actions");
        if (actionNode.isList() || (!actionNode.isMap() && actionNode.getString() != null)) {
            actions.put(ItemClick.RIGHT_CLICK, actions(path + ".actions",
                    strings(actionNode, path + ".actions", warnings), warnings));
        } else {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : actionNode.childrenMap().entrySet()) {
                String key = entry.getKey().toString();
                Optional<ItemClick> click = ItemClick.byId(key);
                if (click.isEmpty()) {
                    warnings.add(path + ".actions." + key + ": unknown click, use right-click, left-click, "
                            + "shift-right-click or shift-left-click");
                    continue;
                }
                String clickPath = path + ".actions." + key;
                actions.put(click.get(), actions(clickPath, strings(entry.getValue(), clickPath, warnings), warnings));
            }
        }
        return Optional.of(new CustomItem(id,
                node.node("enabled").getBoolean(true),
                slot,
                node.node("material").getString("PAPER"),
                node.node("name").getString(""),
                strings(node.node("lore"), path + ".lore", warnings),
                node.node("glow").getBoolean(false),
                Math.max(0, node.node("custom-model-data").getInt(0)),
                node.node("permission").getString(""),
                actions));
    }

    private static List<ItemAction> actions(String path, List<String> raws, List<String> warnings) {
        List<ItemAction> actions = new ArrayList<>();
        for (String raw : raws) {
            try {
                actions.add(ItemAction.parse(raw));
            } catch (IllegalArgumentException e) {
                warnings.add(path + ": " + e.getMessage());
            }
        }
        return actions;
    }

    private static ProfileLayout profile(ConfigurationNode node, List<String> warnings) {
        ProfileLayout defaults = ProfileLayout.DEFAULT;
        ConfigurationNode headNode = node.node("head");
        List<ProfileLayout.Line> head = headNode.virtual() ? defaults.head()
                : ProfileLayout.parseHead(strings(headNode, "profile.head", warnings), warnings);
        ConfigurationNode menu = node.node("menu");
        int rows = menu.node("rows").getInt(defaults.rows());
        if (rows < 1 || rows > 6) {
            warnings.add("profile.menu.rows: " + rows + " must be between 1 and 6, using " + defaults.rows());
            rows = defaults.rows();
        }
        int size = rows * 9;
        int headSlot = slot(menu, "head-slot", defaults.headSlot(), size, warnings);
        int backSlot = slot(menu, "back-slot", defaults.backSlot(), size, warnings);
        int closeSlot = slot(menu, "close-slot", defaults.closeSlot(), size, warnings);
        int entryRow = menu.node("entry-row").getInt(defaults.entryRow());
        if (entryRow < 0 || entryRow >= rows) {
            int fallback = Math.min(defaults.entryRow(), rows - 1);
            warnings.add("profile.menu.entry-row: " + entryRow + " is outside of the menu, using " + fallback);
            entryRow = fallback;
        }
        int actionRow = menu.node("action-row").getInt(defaults.actionRow());
        if (actionRow < 0 || actionRow >= rows) {
            int fallback = Math.min(defaults.actionRow(), rows - 1);
            if (!menu.node("action-row").virtual()) {
                warnings.add("profile.menu.action-row: " + actionRow + " is outside of the menu, using " + fallback);
            }
            actionRow = fallback;
        }
        String clickName = menu.node("language-click").getString(ProfileLayout.AUTO);
        ProfileLayout.LanguageClick languageClick = ProfileLayout.LanguageClick.parse(clickName).orElseGet(() -> {
            warnings.add("profile.menu.language-click: use menu or toggle, got '" + clickName + "'");
            return ProfileLayout.LanguageClick.MENU;
        });
        List<ProfileLayout.Entry> entries = new ArrayList<>();
        List<ProfileLayout.Entry> social = new ArrayList<>();
        ConfigurationNode entriesNode = menu.node("entries");
        if (entriesNode.virtual()) {
            entries.addAll(defaults.entries());
            social.addAll(defaults.social());
        } else {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : entriesNode.childrenMap().entrySet()) {
                String key = entry.getKey().toString();
                Optional<ProfileLayout.Entry.Kind> kind = ProfileLayout.Entry.Kind.byId(key);
                if (kind.isEmpty()) {
                    warnings.add("profile.menu.entries." + key + ": unknown entry, use currencies, level, rank, "
                            + "language, friends or party");
                    continue;
                }
                List<ProfileLayout.Entry> target = kind.get().social() ? social : entries;
                try {
                    ProfileLayout.placement(entry.getValue().getString(), size)
                            .ifPresent(slot -> target.add(new ProfileLayout.Entry(kind.get(), slot)));
                } catch (IllegalArgumentException e) {
                    warnings.add("profile.menu.entries." + key + ": " + e.getMessage());
                }
            }
        }
        return new ProfileLayout(head, rows, headSlot, entryRow, actionRow, backSlot, closeSlot, entries,
                languageClick, social);
    }

    private static int slot(ConfigurationNode menu, String key, int fallback, int size, List<String> warnings) {
        int slot = menu.node(key).getInt(fallback);
        if (slot < -1 || slot >= size) {
            int corrected = fallback < size ? fallback : -1;
            warnings.add("profile.menu." + key + ": " + slot + " is outside of the menu, using " + corrected);
            return corrected;
        }
        return slot;
    }

    private static List<String> strings(ConfigurationNode node, String path, List<String> warnings) {
        if (node.virtual()) {
            return List.of();
        }
        if (!node.isList()) {
            Optional<String> single = text(node);
            if (single.isEmpty()) {
                warnings.add(path + ": expected a text or a list of texts, put the value in quotes");
            }
            return single.map(List::of).orElse(List.of());
        }
        List<String> values = new ArrayList<>();
        List<? extends ConfigurationNode> children = node.childrenList();
        for (int index = 0; index < children.size(); index++) {
            Optional<String> value = text(children.get(index));
            if (value.isPresent()) {
                values.add(value.get());
            } else {
                warnings.add(path + " entry " + (index + 1) + ": could not be read, put the whole entry in quotes");
            }
        }
        return values;
    }

    private static Optional<String> text(ConfigurationNode node) {
        if (node.isMap()) {
            Map<Object, ? extends ConfigurationNode> children = node.childrenMap();
            if (children.size() != 1) {
                return Optional.empty();
            }
            Map.Entry<Object, ? extends ConfigurationNode> entry = children.entrySet().iterator().next();
            String value = entry.getValue().getString();
            return value == null ? Optional.empty() : Optional.of(entry.getKey() + ": " + value);
        }
        return Optional.ofNullable(node.getString());
    }

    private static List<String> header() {
        List<String> lines = new ArrayList<>();
        lines.add("PRISMARIN Lobby settings. Restart the server after changes.");
        lines.add("Spawns and void heights are set in game with /setspawn and /setvoid and live in locations.yml.");
        lines.add("");
        lines.addAll(YamlComments.TEXT_HELP);
        return List.copyOf(lines);
    }

    private static Map<String, List<String>> comments() {
        Map<String, List<String>> comments = new LinkedHashMap<>();
        comments.put("features", List.of("Switch whole lobby features on or off."));
        comments.put("features.lobby-selector", List.of(
                "Lobby selector item and menu. The item only shows up when there is more than one lobby."));
        comments.put("features.premium-lobby", List.of(
                "Premium lobby item. It only shows up when a premium lobby exists."));
        comments.put("features.void-teleport", List.of(
                "Brings falling players back to the spawn. Stays off per world until /setvoid <y> is used."));
        comments.put("features.spawn-on-join", List.of(
                "Teleports joining players to a lobby spawn. Nobody is moved while no spawn is set."));
        comments.put("features.lobby-distribution", List.of(
                "Spreads joining players over the world lobbies below instead of always using the first one."));
        comments.put("worlds", List.of(
                "Worlds where protection, hotbar items, double jump and void teleport apply.",
                "The worlds of the lobbies below and every world with a /setspawn spawn are always included."));
        comments.put("lobbies", List.of("Several lobbies on this server, one world each."));
        comments.put("lobbies.distribution", List.of(
                "How joining players are spread: least-players, fill-first or random.",
                "fill-first fills the lobbies in the order below up to their soft-max-players."));
        comments.put("lobbies.premium-auto-join", List.of(
                "false keeps premium lobbies for an explicit choice in the selector or with the premium item."));
        comments.put("lobbies.worlds", List.of(
                "id: display-name and icon (a material) show up in the lobby selector.",
                "soft-max-players only steers the distribution, 0 means no limit.",
                "A permission makes a lobby premium. Every lobby uses the /setspawn spawn of its world."));
        comments.put("lobbies.worlds.premium", List.of(
                "Example premium lobby: create the world, run /setspawn there and set enabled to true."));
        comments.put("items", List.of(
                "Built in hotbar items. Slots go from 0 (left) to 8 (right), materials are item names."));
        comments.put("items.premium-lobby", List.of(
                "Brings players to the premium lobby: a premium lobby above or a lobby server in servers.yml",
                "with a permission property. target picks one by id or server name, empty picks automatically."));
        comments.put("features.nick-tool", List.of(
                "Nick tool in the hotbar for players with a nick permission. Needs the nick module of PrismarinCore."));
        comments.put("features.silent-lobby", List.of(
                "Silent lobby item: players who switch it on see nobody, nobody sees them and lobby chat stays quiet."));
        comments.put("items.nick-tool", List.of(
                "Opens a small nick menu. permission empty means every player with a prismarin.nick.* permission."));
        comments.put("items.silent-lobby", List.of(
                "Toggles the silent lobby. active-material shows while it is on, permission decides who gets it."));
        comments.put("custom-items", List.of(
                "Your own hotbar items. Actions run per click: right-click, left-click, shift-right-click and",
                "shift-left-click. Shift clicks fall back to the plain click, a plain list means right click.",
                "Actions: 'player: <command>', 'console: <command>', 'message: <text>', 'actionbar: <text>',",
                "'menu: navigator|lobbies|profile|languages|friends|party|nick', 'server: <name>', 'group: <group>',",
                "'lobby: <lobby id>' and 'sound: <key> [volume] [pitch]'.",
                "%player% works in commands and texts, PlaceholderAPI placeholders too when it is installed.",
                "name and lore also accept message keys from messages_<language>.yml."));
        comments.put("custom-items.example", List.of("Example item, set enabled to true to try it."));
        comments.put("custom-items.example.custom-model-data", List.of("0 means none."));
        comments.put("custom-items.example.permission", List.of(
                "Only players with this permission get the item. Empty means everyone."));
        comments.put("profile", List.of("What the profile head item and the profile menu show."));
        comments.put("profile.head", List.of(
                "Lore of the profile head item, line by line: currencies, 'currency: <id>', level, rank, empty",
                "or 'text: <text>'. Text lines may use PlaceholderAPI placeholders."));
        comments.put("profile.menu", List.of("Layout of the profile menu. Slots count from 0 (top left), -1 hides."));
        comments.put("profile.menu.entry-row", List.of("Row of currencies, level and rank (0 is the top row)."));
        comments.put("profile.menu.action-row", List.of(
                "Row of language, friends and party. Use the same number as entry-row for one shared row."));
        comments.put("profile.menu.language-click", List.of(
                "menu (or auto) opens a list of every language with flag heads, toggle switches directly",
                "between two languages."));
        comments.put("profile.menu.entries", List.of(
                "auto centers the entry in its row, a number pins it to that slot, off hides it.",
                "currencies takes one slot per currency. language needs at least two languages.",
                "friends and party show up when the PRISMARIN proxy has the friends or party module on."));
        return Map.copyOf(comments);
    }
}
