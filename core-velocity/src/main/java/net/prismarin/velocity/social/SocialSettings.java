package net.prismarin.velocity.social;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAction;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.social.ClanTagFormat;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record SocialSettings(Friends friends, Party party, Messages messages, Clans clans,
                             Map<SocialSetting, String> defaults, List<String> warnings) {

    public static final String FILE_NAME = "social.yml";

    public record Friends(int maxFriends, Map<String, Integer> limitPermissions, int requestExpiryHours,
                          int requestCooldownSeconds, int joinDelaySeconds, boolean joinSummary,
                          List<String> commands, SubCommands subCommands) {

        public Friends {
            limitPermissions = Map.copyOf(limitPermissions);
            commands = List.copyOf(commands);
            subCommands = subCommands == null ? SubCommands.FRIENDS : subCommands;
        }

        public long requestExpiryMillis() {
            return requestExpiryHours * 3_600_000L;
        }
    }

    public record Party(int maxSize, Map<String, Integer> sizePermissions, int inviteExpirySeconds,
                        PartySettings defaults, int offlineGraceSeconds, boolean follow, boolean followIntoLobbies,
                        Set<String> followSkipGroups, List<String> commands, List<String> chatCommands,
                        SubCommands subCommands) {

        public Party {
            sizePermissions = Map.copyOf(sizePermissions);
            followSkipGroups = Set.copyOf(followSkipGroups);
            commands = List.copyOf(commands);
            chatCommands = List.copyOf(chatCommands);
            subCommands = subCommands == null ? SubCommands.PARTY : subCommands;
        }

        public long inviteExpiryMillis() {
            return inviteExpirySeconds * 1000L;
        }

        public List<PartyOption> options() {
            List<PartyOption> options = new ArrayList<>();
            for (PartyOption option : PartyOption.available(follow)) {
                if (option == PartyOption.ACCESS && !subCommands.on("join")
                        || option == PartyOption.CHAT && !subCommands.on("chat")) {
                    continue;
                }
                options.add(option);
            }
            return List.copyOf(options);
        }
    }

    public record Messages(List<String> commands, List<String> replyCommands, boolean staffSpy,
                           SubCommands subCommands) {

        public Messages {
            commands = List.copyOf(commands);
            replyCommands = List.copyOf(replyCommands);
            subCommands = subCommands == null ? SubCommands.MESSAGES : subCommands;
        }
    }

    public record Clans(int maxMembers, Map<String, Integer> sizePermissions, ClanNaming naming,
                        int inviteExpirySeconds, int disbandConfirmSeconds, ClanAccess defaultAccess,
                        String createPermission, String colorPermission, ClanRights rights, ClanTagFormat tag,
                        Map<String, Boolean> showIn, List<String> commands, List<String> chatCommands,
                        SubCommands subCommands) {

        public static final List<String> PLACES = List.of("friends", "party", "player-info");

        public Clans {
            sizePermissions = Map.copyOf(sizePermissions);
            showIn = Map.copyOf(showIn);
            createPermission = createPermission == null ? "" : createPermission.strip();
            colorPermission = colorPermission == null ? "" : colorPermission.strip();
            commands = List.copyOf(commands);
            chatCommands = List.copyOf(chatCommands);
            subCommands = subCommands == null ? SubCommands.CLANS : subCommands;
        }

        public long inviteExpiryMillis() {
            return inviteExpirySeconds * 1000L;
        }

        public boolean showIn(String place) {
            return showIn.getOrDefault(place, true);
        }
    }

    public SocialSettings {
        defaults = Map.copyOf(defaults);
        warnings = List.copyOf(warnings);
    }

    public String defaultValue(SocialSetting setting) {
        return defaults.getOrDefault(setting, setting.fallback());
    }

    public static final String CLAN_COLOR_PERMISSION = "prismarin.clan.color";

    private static final Pattern COMMAND = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final List<String> FRIEND_COMMANDS = List.of("friend", "friends", "f");
    private static final List<String> PARTY_COMMANDS = List.of("party", "p");
    private static final List<String> PARTY_CHAT_COMMANDS = List.of("pc", "partychat");
    private static final List<String> MESSAGE_COMMANDS = List.of("msg", "tell", "w", "whisper", "m");
    private static final List<String> REPLY_COMMANDS = List.of("r", "reply");
    private static final List<String> CLAN_COMMANDS = List.of("clan");
    private static final List<String> CLAN_CHAT_COMMANDS = List.of("cc", "clanchat");
    private static final List<String> BLOCKED_NAMES = List.of("admin", "staff", "team", "mod", "owner", "server");

    private static final List<String> HEADER = List.of(
            "PRISMARIN friends, parties and private messages on the proxy, clans included.",
            "Switch whole parts off in config.yml under modules: friends, party, clans and private-messages.",
            "sub-commands switch single commands off. Switched off commands disappear from the help, the tab",
            "completion and the lobby menus.",
            "Changes take effect after a proxy restart.");

    private static final Map<String, List<String>> COMMENTS = comments();

    public static SocialSettings standard() {
        return read(filled(), new ArrayList<>());
    }

    public static SocialSettings load(Path file) {
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
            CommentedConfigurationNode root;
            try {
                root = Files.exists(file) ? loader.load() : loader.createNode();
            } catch (ConfigurateException e) {
                List<String> warnings = new ArrayList<>();
                warnings.add(FILE_NAME + " could not be read, using the defaults: " + e.getMessage());
                return read(filled(), warnings);
            }
            if (fillDefaults(root)) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root, new ArrayList<>());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CommentedConfigurationNode filled() {
        CommentedConfigurationNode root = YamlConfigurationLoader.builder().build().createNode();
        try {
            fillDefaults(root);
        } catch (SerializationException e) {
            throw new IllegalStateException(e);
        }
        return root;
    }

    private static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = false;
        ConfigurationNode friends = root.node("friends");
        changed |= fill(friends.node("max-friends"), 100);
        if (friends.node("limit-permissions").virtual()) {
            friends.node("limit-permissions", "prismarin.friends.limit.vip").set(200);
            changed = true;
        }
        changed |= fill(friends.node("request-expiry-hours"), 72);
        changed |= fill(friends.node("request-cooldown-seconds"), 5);
        changed |= fill(friends.node("join-delay-seconds"), 3);
        changed |= fill(friends.node("join-summary"), true);
        changed |= fillList(friends.node("commands"), FRIEND_COMMANDS);
        changed |= fillSwitches(friends.node("sub-commands"), SubCommands.FRIENDS);
        ConfigurationNode party = root.node("party");
        changed |= fill(party.node("max-size"), 8);
        if (party.node("size-permissions").virtual()) {
            party.node("size-permissions", "prismarin.party.size.vip").set(16);
            changed = true;
        }
        changed |= fill(party.node("invite-expiry-seconds"), 60);
        changed |= fill(party.node("defaults", PartyOption.INVITE.id()),
                legacyMembersInvite(party) ? PartyOption.MEMBERS : PartyOption.MODERATORS);
        changed |= fill(party.node("defaults", PartyOption.ACCESS.id()), PartyOption.INVITE_ONLY);
        changed |= fill(party.node("defaults", PartyOption.FOLLOW.id()), SocialSetting.ON);
        changed |= fill(party.node("defaults", PartyOption.CHAT.id()), PartyOption.MEMBERS);
        changed |= fill(party.node("offline-grace-seconds"), 120);
        changed |= fill(party.node("follow", "enabled"), true);
        changed |= fill(party.node("follow", "into-lobbies"), false);
        changed |= fillList(party.node("follow", "skip-groups"), List.of());
        changed |= fillList(party.node("commands"), PARTY_COMMANDS);
        changed |= fillList(party.node("chat-commands"), PARTY_CHAT_COMMANDS);
        changed |= fillSwitches(party.node("sub-commands"), SubCommands.PARTY);
        ConfigurationNode messages = root.node("messages");
        changed |= fillList(messages.node("commands"), MESSAGE_COMMANDS);
        changed |= fillList(messages.node("reply-commands"), REPLY_COMMANDS);
        changed |= fill(messages.node("staff-spy"), false);
        changed |= fillSwitches(messages.node("sub-commands"), SubCommands.MESSAGES);
        changed |= fillClans(root.node("clans"));
        for (SocialSetting setting : SocialSetting.values()) {
            changed |= fill(root.node("defaults", setting.id()), setting.fallback());
        }
        return changed;
    }

    private static boolean fillClans(ConfigurationNode clans) throws SerializationException {
        boolean changed = fill(clans.node("max-members"), 20);
        if (clans.node("size-permissions").virtual()) {
            clans.node("size-permissions", "prismarin.clan.size.vip").set(40);
            changed = true;
        }
        changed |= fill(clans.node("name", "min-length"), ClanNaming.DEFAULT.nameMin());
        changed |= fill(clans.node("name", "max-length"), ClanNaming.DEFAULT.nameMax());
        changed |= fill(clans.node("tag", "min-length"), ClanNaming.DEFAULT.tagMin());
        changed |= fill(clans.node("tag", "max-length"), ClanNaming.DEFAULT.tagMax());
        changed |= fill(clans.node("tag", "format"), ClanTagFormat.DEFAULT_FORMAT);
        changed |= fill(clans.node("tag", "position"), ClanTagFormat.DEFAULT.position().id());
        changed |= fill(clans.node("tag", "chat"), true);
        changed |= fill(clans.node("tag", "tablist"), true);
        changed |= fill(clans.node("allowed-characters"), ClanNaming.DEFAULT_PATTERN);
        changed |= fillList(clans.node("blocked-names"), BLOCKED_NAMES);
        changed |= fill(clans.node("invite-expiry-seconds"), 300);
        changed |= fill(clans.node("disband-confirm-seconds"), 30);
        changed |= fill(clans.node("default-access"), ClanAccess.INVITE_ONLY.id());
        changed |= fill(clans.node("create-permission"), "");
        changed |= fill(clans.node("color-permission"), CLAN_COLOR_PERMISSION);
        for (ClanAction action : ClanAction.values()) {
            if (action.configurable()) {
                changed |= fill(clans.node("rights", action.id()), ClanRights.fallback(action).id());
            }
        }
        for (String place : Clans.PLACES) {
            changed |= fill(clans.node("show-in", place), true);
        }
        changed |= fillList(clans.node("commands"), CLAN_COMMANDS);
        changed |= fillList(clans.node("chat-commands"), CLAN_CHAT_COMMANDS);
        changed |= fillSwitches(clans.node("sub-commands"), SubCommands.CLANS);
        return changed;
    }

    private static SocialSettings read(ConfigurationNode root, List<String> warnings) {
        ConfigurationNode friendsNode = root.node("friends");
        Friends friends = new Friends(
                range(friendsNode.node("max-friends"), "friends.max-friends", 100, 1, 10_000, warnings),
                limits(friendsNode.node("limit-permissions"), "friends.limit-permissions", warnings),
                range(friendsNode.node("request-expiry-hours"), "friends.request-expiry-hours", 72, 0, 8_760, warnings),
                range(friendsNode.node("request-cooldown-seconds"), "friends.request-cooldown-seconds", 5, 0, 3_600,
                        warnings),
                range(friendsNode.node("join-delay-seconds"), "friends.join-delay-seconds", 3, 1, 60, warnings),
                friendsNode.node("join-summary").getBoolean(true),
                commands(friendsNode.node("commands"), "friends.commands", FRIEND_COMMANDS, warnings),
                switches(friendsNode.node("sub-commands"), "friends.sub-commands", SubCommands.FRIENDS, warnings));
        ConfigurationNode partyNode = root.node("party");
        Party party = new Party(
                range(partyNode.node("max-size"), "party.max-size", 8, 2, 100, warnings),
                limits(partyNode.node("size-permissions"), "party.size-permissions", warnings),
                range(partyNode.node("invite-expiry-seconds"), "party.invite-expiry-seconds", 60, 10, 3_600, warnings),
                partyDefaults(partyNode, warnings),
                range(partyNode.node("offline-grace-seconds"), "party.offline-grace-seconds", 120, 0, 3_600, warnings),
                partyNode.node("follow", "enabled").getBoolean(true),
                partyNode.node("follow", "into-lobbies").getBoolean(false),
                lowerSet(partyNode.node("follow", "skip-groups"), "party.follow.skip-groups", warnings),
                commands(partyNode.node("commands"), "party.commands", PARTY_COMMANDS, warnings),
                commands(partyNode.node("chat-commands"), "party.chat-commands", PARTY_CHAT_COMMANDS, warnings),
                switches(partyNode.node("sub-commands"), "party.sub-commands", SubCommands.PARTY, warnings));
        ConfigurationNode messagesNode = root.node("messages");
        Messages messages = new Messages(
                commands(messagesNode.node("commands"), "messages.commands", MESSAGE_COMMANDS, warnings),
                commands(messagesNode.node("reply-commands"), "messages.reply-commands", REPLY_COMMANDS, warnings),
                messagesNode.node("staff-spy").getBoolean(false),
                switches(messagesNode.node("sub-commands"), "messages.sub-commands", SubCommands.MESSAGES, warnings));
        Clans clans = clans(root.node("clans"), warnings);
        Map<SocialSetting, String> defaults = new EnumMap<>(SocialSetting.class);
        for (SocialSetting setting : SocialSetting.values()) {
            String raw = root.node("defaults", setting.id()).getString(setting.fallback());
            String value = setting.normalize(raw).orElseGet(() -> {
                warnings.add("defaults." + setting.id() + ": '" + raw + "' is not one of " + setting.options()
                        + ", using " + setting.fallback());
                return setting.fallback();
            });
            defaults.put(setting, value);
        }
        return new SocialSettings(friends, party, messages, clans, defaults, warnings);
    }

    private static Clans clans(ConfigurationNode node, List<String> warnings) {
        int nameMin = range(node.node("name", "min-length"), "clans.name.min-length", ClanNaming.DEFAULT.nameMin(), 1,
                32, warnings);
        int nameMax = range(node.node("name", "max-length"), "clans.name.max-length", ClanNaming.DEFAULT.nameMax(),
                nameMin, 32, warnings);
        int tagMin = range(node.node("tag", "min-length"), "clans.tag.min-length", ClanNaming.DEFAULT.tagMin(), 1, 16,
                warnings);
        int tagMax = range(node.node("tag", "max-length"), "clans.tag.max-length", ClanNaming.DEFAULT.tagMax(),
                tagMin, 16, warnings);
        ClanNaming naming = new ClanNaming(nameMin, nameMax, tagMin, tagMax,
                pattern(node.node("allowed-characters"), warnings), lowerSet(node.node("blocked-names"),
                "clans.blocked-names", warnings));
        String rawPosition = node.node("tag", "position").getString(ClanTagFormat.DEFAULT.position().id());
        ClanTagFormat.Position position = ClanTagFormat.Position.byId(rawPosition).orElseGet(() -> {
            warnings.add("clans.tag.position: '" + rawPosition + "' is not before or after, using after");
            return ClanTagFormat.Position.AFTER;
        });
        ClanTagFormat tag = new ClanTagFormat(node.node("tag", "format").getString(ClanTagFormat.DEFAULT_FORMAT),
                position, node.node("tag", "chat").getBoolean(true), node.node("tag", "tablist").getBoolean(true));
        String rawAccess = node.node("default-access").getString(ClanAccess.INVITE_ONLY.id());
        ClanAccess access = ClanAccess.byId(rawAccess).orElseGet(() -> {
            warnings.add("clans.default-access: '" + rawAccess + "' is not invite-only or open, using invite-only");
            return ClanAccess.INVITE_ONLY;
        });
        return new Clans(
                range(node.node("max-members"), "clans.max-members", 20, 2, 500, warnings),
                limits(node.node("size-permissions"), "clans.size-permissions", warnings),
                naming,
                range(node.node("invite-expiry-seconds"), "clans.invite-expiry-seconds", 300, 10, 86_400, warnings),
                range(node.node("disband-confirm-seconds"), "clans.disband-confirm-seconds", 30, 5, 600, warnings),
                access,
                node.node("create-permission").getString(""),
                node.node("color-permission").getString(CLAN_COLOR_PERMISSION),
                rights(node.node("rights"), warnings),
                tag,
                places(node.node("show-in")),
                commands(node.node("commands"), "clans.commands", CLAN_COMMANDS, warnings),
                commands(node.node("chat-commands"), "clans.chat-commands", CLAN_CHAT_COMMANDS, warnings),
                switches(node.node("sub-commands"), "clans.sub-commands", SubCommands.CLANS, warnings));
    }

    private static Map<String, Boolean> places(ConfigurationNode node) {
        Map<String, Boolean> places = new LinkedHashMap<>();
        for (String place : Clans.PLACES) {
            places.put(place, node.node(place).getBoolean(true));
        }
        return places;
    }

    private static ClanRights rights(ConfigurationNode node, List<String> warnings) {
        Map<ClanAction, ClanRole> roles = new EnumMap<>(ClanAction.class);
        for (ClanAction action : ClanAction.values()) {
            if (!action.configurable()) {
                continue;
            }
            ConfigurationNode child = node.node(action.id());
            if (child.virtual()) {
                continue;
            }
            String raw = child.getString("");
            Optional<ClanRole> role = ClanRole.byId(raw);
            if (role.isEmpty()) {
                warnings.add("clans.rights." + action.id() + ": '" + raw
                        + "' is not member, moderator, co-leader or leader, using "
                        + ClanRights.fallback(action).id());
                continue;
            }
            if (!role.get().atLeast(ClanRights.lowest(action))) {
                warnings.add("clans.rights." + action.id() + ": needs at least " + ClanRights.lowest(action).id()
                        + ", using " + ClanRights.lowest(action).id());
            }
            roles.put(action, role.get());
        }
        return new ClanRights(roles);
    }

    private static Pattern pattern(ConfigurationNode node, List<String> warnings) {
        String raw = node.getString(ClanNaming.DEFAULT_PATTERN);
        try {
            return Pattern.compile(raw);
        } catch (PatternSyntaxException e) {
            warnings.add("clans.allowed-characters: '" + raw + "' is no valid pattern, using "
                    + ClanNaming.DEFAULT_PATTERN);
            return Pattern.compile(ClanNaming.DEFAULT_PATTERN);
        }
    }

    private static SubCommands switches(ConfigurationNode node, String path, SubCommands defaults,
                                        List<String> warnings) {
        Set<String> off = new LinkedHashSet<>();
        node.childrenMap().forEach((key, child) -> {
            String name = key.toString().toLowerCase(Locale.ROOT);
            if (!defaults.switches().containsKey(name)) {
                warnings.add(path + "." + name + ": unknown switch, use one of " + defaults.switches().keySet());
                return;
            }
            if (!child.getBoolean(true)) {
                off.add(name);
            }
        });
        return defaults.withOff(off);
    }

    private static boolean legacyMembersInvite(ConfigurationNode partyNode) {
        ConfigurationNode legacy = partyNode.node("members-can-invite");
        return !legacy.virtual() && legacy.getBoolean(false);
    }

    private static PartySettings partyDefaults(ConfigurationNode partyNode, List<String> warnings) {
        PartySettings fallback = legacyMembersInvite(partyNode) ? PartySettings.DEFAULT.withInvite(PartyRole.MEMBER)
                : PartySettings.DEFAULT;
        PartySettings settings = fallback;
        for (PartyOption option : PartyOption.values()) {
            ConfigurationNode node = partyNode.node("defaults", option.id());
            if (node.virtual()) {
                continue;
            }
            String raw = node.getString("");
            Optional<String> value = option.normalize(raw);
            if (value.isEmpty()) {
                warnings.add("party.defaults." + option.id() + ": '" + raw + "' is not one of " + option.options()
                        + ", using " + option.value(fallback));
                continue;
            }
            settings = option.apply(settings, value.get());
        }
        return settings;
    }

    private static int range(ConfigurationNode node, String path, int fallback, int min, int max,
                             List<String> warnings) {
        int value = node.getInt(fallback);
        if (value < min || value > max) {
            int clamped = Math.max(min, Math.min(max, value));
            warnings.add(path + ": " + value + " must be between " + min + " and " + max + ", using " + clamped);
            return clamped;
        }
        return value;
    }

    private static Map<String, Integer> limits(ConfigurationNode node, String path, List<String> warnings) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        node.childrenMap().forEach((key, child) -> {
            int value = child.getInt(-1);
            if (value < 1) {
                warnings.add(path + "." + key + ": needs a number above 0");
                return;
            }
            limits.put(key.toString(), value);
        });
        return limits;
    }

    private static List<String> commands(ConfigurationNode node, String path, List<String> fallback,
                                         List<String> warnings) {
        Set<String> names = new LinkedHashSet<>();
        for (String raw : strings(node)) {
            String name = raw.strip().toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (COMMAND.matcher(name).matches()) {
                names.add(name);
            } else {
                warnings.add(path + ": '" + raw + "' is no valid command name");
            }
        }
        if (names.isEmpty()) {
            warnings.add(path + ": needs at least one command name, using " + fallback);
            return fallback;
        }
        return List.copyOf(names);
    }

    private static Set<String> lowerSet(ConfigurationNode node, String path, List<String> warnings) {
        Set<String> values = new LinkedHashSet<>();
        for (String raw : strings(node)) {
            if (!raw.isBlank()) {
                values.add(raw.strip().toLowerCase(Locale.ROOT));
            }
        }
        if (!node.virtual() && !node.isList() && node.getString() == null) {
            warnings.add(path + ": expected a list of names");
        }
        return values;
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual()) {
            return List.of();
        }
        if (!node.isList()) {
            String single = node.getString();
            return single == null ? List.of() : List.of(single);
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static boolean fill(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }

    private static boolean fillList(ConfigurationNode node, List<String> values) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.setList(String.class, values);
        return true;
    }

    private static boolean fillSwitches(ConfigurationNode node, SubCommands defaults) throws SerializationException {
        boolean changed = false;
        for (String key : defaults.switches().keySet()) {
            changed |= fill(node.node(key), true);
        }
        return changed;
    }

    private static Map<String, List<String>> comments() {
        Map<String, List<String>> comments = new LinkedHashMap<>();
        comments.put("friends", List.of("Friend lists are stored in the shared database, so every server can read them."));
        comments.put("friends.max-friends", List.of("How many friends a player may have."));
        comments.put("friends.limit-permissions", List.of(
                "Bigger friend lists by permission: permission: limit. The highest matching limit wins."));
        comments.put("friends.request-expiry-hours", List.of("Open friend requests disappear after this time. 0 keeps them."));
        comments.put("friends.request-cooldown-seconds", List.of("Time between two friend requests of one player."));
        comments.put("friends.join-delay-seconds", List.of(
                "Friends hear about a join this long after the player reached a server.",
                "The wait lets vanished staff stay hidden, keep it at 3 or more."));
        comments.put("friends.join-summary", List.of("Tells joining players how many friend requests wait for them."));
        comments.put("friends.commands", List.of("Command and aliases. The first name is the main command."));
        comments.put("friends.sub-commands", List.of(
                "false switches a part of /friend off: requests (the list of open requests), jump (also the",
                "jump setting and the jump button) and settings (the personal settings and their menu)."));
        comments.put("party", List.of("Parties live in the memory of this proxy and end with a restart.",
                "With several proxies every proxy keeps its own parties."));
        comments.put("party.max-size", List.of("Players per party, the leader included."));
        comments.put("party.size-permissions", List.of(
                "Bigger parties by permission of the leader: permission: size. The highest matching size wins."));
        comments.put("party.invite-expiry-seconds", List.of("How long a party invite stays open."));
        comments.put("party.members-can-invite", List.of(
                "Replaced by party.defaults.invite. Only read once to fill that key, change the new key instead."));
        comments.put("party.defaults", List.of(
                "Settings every new party starts with. The leader and moderators change them with",
                "/party settings <setting> <value> or in the lobby menu.",
                "Roles: the leader may do everything. Moderators may invite, remove members and change settings.",
                "Members may chat, look at the party and leave."));
        comments.put("party.defaults.invite", List.of("Who may invite: leader, moderators or members."));
        comments.put("party.defaults.access", List.of(
                "Who may join without an invite through /party join <player>:",
                "invite-only, friends (friends of the leader) or open (everyone)."));
        comments.put("party.defaults.follow", List.of(
                "on: members follow the leader to other servers. Only works while follow.enabled is true."));
        comments.put("party.defaults.chat", List.of("Who may write in the party chat: members, moderators or leader."));
        comments.put("party.offline-grace-seconds", List.of(
                "Members who disconnect stay in the party this long. The leader role moves on right away,",
                "moderators first. 0 removes them at once."));
        comments.put("party.follow", List.of("Members follow the leader when the leader switches servers,",
                "for example through the navigator or /play, as long as the server has room.",
                "enabled: false switches following off for every party, whatever the party settings say."));
        comments.put("party.follow.into-lobbies", List.of("true also pulls members back when the leader goes to a lobby."));
        comments.put("party.follow.skip-groups", List.of("Server groups from servers.yml where members do not follow."));
        comments.put("party.chat-commands", List.of("Commands for party chat, for example /pc hello."));
        comments.put("party.sub-commands", List.of(
                "false switches a part of /party off. promote also takes demote with it, chat also the party",
                "chat commands and the chat setting, join also the access setting."));
        comments.put("messages", List.of("Private messages across the whole network with /msg and /r."));
        comments.put("messages.staff-spy", List.of(
                "true lets players with prismarin.msg.spy read private, party and clan messages after /socialspy.",
                "false also removes /socialspy."));
        comments.put("messages.sub-commands", List.of(
                "false removes the reply commands (reply) or /msgtoggle (toggle)."));
        comments.put("clans", List.of("Clans are stored in the shared database and stay after a restart.",
                "Roles from low to high: member, moderator, co-leader, leader."));
        comments.put("clans.max-members", List.of("Players per clan, the leader included."));
        comments.put("clans.size-permissions", List.of(
                "Bigger clans by permission of the leader: permission: size. The highest matching size wins."));
        comments.put("clans.name", List.of("Length of the clan name, for example /clan create Nightowls OWL."));
        comments.put("clans.tag", List.of("The short tag next to the names of members."));
        comments.put("clans.tag.format", List.of(
                "How the tag looks. %name% is the tag in the clan colour, %clan% the full clan name.",
                "& codes, hex (&#RRGGBB) and MiniMessage work."));
        comments.put("clans.tag.position", List.of("before or after the player name."));
        comments.put("clans.tag.chat", List.of("Shows the tag in the chat line. %clan_tag% in chat.line works as well."));
        comments.put("clans.tag.tablist", List.of(
                "Shows the tag in the tab list and above the head. Minecraft uses one team for both."));
        comments.put("clans.allowed-characters", List.of(
                "Regular expression every name and tag has to match. The default allows letters, digits and _."));
        comments.put("clans.blocked-names", List.of("Names and tags nobody may use, upper and lower case ignored."));
        comments.put("clans.invite-expiry-seconds", List.of("How long a clan invite stays open."));
        comments.put("clans.disband-confirm-seconds", List.of(
                "Time to repeat /clan disband confirm after /clan disband."));
        comments.put("clans.default-access", List.of(
                "How new clans start: invite-only or open (everyone may /clan join). Changeable with /clan settings."));
        comments.put("clans.create-permission", List.of("Permission for /clan create. Empty means everyone."));
        comments.put("clans.color-permission", List.of(
                "Permission for /clan color, on top of the color right below. Empty means everyone with the right."));
        comments.put("clans.rights", List.of(
                "Lowest role that may do something: member, moderator, co-leader or leader.",
                "Kicking, promoting and demoting also need a higher role than the target. Promoting goes up to",
                "co-leader, only the leader can hand over the lead and disband. color needs at least co-leader."));
        comments.put("clans.show-in", List.of(
                "Where the clan tag of other players shows up: friends (friend list and friends menu), party",
                "(party list and party menu) and player-info (/pinfo for staff). Nicked players never show one."));
        comments.put("clans.commands", List.of("Command and aliases. The first name is the main command."));
        comments.put("clans.chat-commands", List.of("Commands for clan chat, for example /cc hello."));
        comments.put("clans.sub-commands", List.of(
                "false switches a part of /clan off. invite also takes accept and deny with it, promote takes demote,",
                "chat also the clan chat commands, join only joining open clans, info the info about other clans."));
        comments.put("defaults", List.of(
                "Settings of new players. Players change their own with /friend settings or in the lobby menu.",
                "requests: everyone, friends-of-friends or nobody. notifications and jump: on or off.",
                "party-invites and messages: everyone, friends or nobody."));
        return Map.copyOf(comments);
    }
}
