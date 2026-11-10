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
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import net.prismarin.shared.config.YamlComments;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record SocialSettings(Friends friends, Party party, Messages messages,
                             Map<SocialSetting, String> defaults, List<String> warnings) {

    public static final String FILE_NAME = "social.yml";

    public record Friends(int maxFriends, Map<String, Integer> limitPermissions, int requestExpiryHours,
                          int requestCooldownSeconds, int joinDelaySeconds, boolean joinSummary,
                          List<String> commands) {

        public Friends {
            limitPermissions = Map.copyOf(limitPermissions);
            commands = List.copyOf(commands);
        }

        public long requestExpiryMillis() {
            return requestExpiryHours * 3_600_000L;
        }
    }

    public record Party(int maxSize, Map<String, Integer> sizePermissions, int inviteExpirySeconds,
                        PartySettings defaults, int offlineGraceSeconds, boolean follow, boolean followIntoLobbies,
                        Set<String> followSkipGroups, List<String> commands, List<String> chatCommands) {

        public Party {
            sizePermissions = Map.copyOf(sizePermissions);
            followSkipGroups = Set.copyOf(followSkipGroups);
            commands = List.copyOf(commands);
            chatCommands = List.copyOf(chatCommands);
        }

        public long inviteExpiryMillis() {
            return inviteExpirySeconds * 1000L;
        }

        public List<PartyOption> options() {
            return PartyOption.available(follow);
        }
    }

    public record Messages(List<String> commands, List<String> replyCommands, boolean staffSpy) {

        public Messages {
            commands = List.copyOf(commands);
            replyCommands = List.copyOf(replyCommands);
        }
    }

    public SocialSettings {
        defaults = Map.copyOf(defaults);
        warnings = List.copyOf(warnings);
    }

    public String defaultValue(SocialSetting setting) {
        return defaults.getOrDefault(setting, setting.fallback());
    }

    private static final Pattern COMMAND = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final List<String> FRIEND_COMMANDS = List.of("friend", "friends", "f");
    private static final List<String> PARTY_COMMANDS = List.of("party", "p");
    private static final List<String> PARTY_CHAT_COMMANDS = List.of("pc", "partychat");
    private static final List<String> MESSAGE_COMMANDS = List.of("msg", "tell", "w", "whisper", "m");
    private static final List<String> REPLY_COMMANDS = List.of("r", "reply");

    private static final List<String> HEADER = List.of(
            "PRISMARIN friends, parties and private messages on the proxy.",
            "Switch whole parts off in config.yml under modules: friends, party and private-messages.",
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
        ConfigurationNode messages = root.node("messages");
        changed |= fillList(messages.node("commands"), MESSAGE_COMMANDS);
        changed |= fillList(messages.node("reply-commands"), REPLY_COMMANDS);
        changed |= fill(messages.node("staff-spy"), false);
        for (SocialSetting setting : SocialSetting.values()) {
            changed |= fill(root.node("defaults", setting.id()), setting.fallback());
        }
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
                commands(friendsNode.node("commands"), "friends.commands", FRIEND_COMMANDS, warnings));
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
                commands(partyNode.node("chat-commands"), "party.chat-commands", PARTY_CHAT_COMMANDS, warnings));
        ConfigurationNode messagesNode = root.node("messages");
        Messages messages = new Messages(
                commands(messagesNode.node("commands"), "messages.commands", MESSAGE_COMMANDS, warnings),
                commands(messagesNode.node("reply-commands"), "messages.reply-commands", REPLY_COMMANDS, warnings),
                messagesNode.node("staff-spy").getBoolean(false));
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
        return new SocialSettings(friends, party, messages, defaults, warnings);
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
            warnings.add(path + ": expected a list of group names");
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
        comments.put("messages", List.of("Private messages across the whole network with /msg and /r."));
        comments.put("messages.staff-spy", List.of(
                "true lets players with prismarin.msg.spy read private and party messages after /socialspy."));
        comments.put("defaults", List.of(
                "Settings of new players. Players change their own with /friend settings or in the lobby menu.",
                "requests: everyone, friends-of-friends or nobody. notifications and jump: on or off.",
                "party-invites and messages: everyone, friends or nobody."));
        return Map.copyOf(comments);
    }
}
