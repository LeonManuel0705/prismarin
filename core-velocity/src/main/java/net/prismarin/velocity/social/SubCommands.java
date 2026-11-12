package net.prismarin.velocity.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SubCommands(List<String> actions, Map<String, List<String>> switches, Set<String> off) {

    public static final SubCommands FRIENDS = of(
            List.of("add", "accept", "deny", "cancel", "remove", "list", "requests", "jump", "settings", "help"),
            Map.of("requests", List.of("requests"), "jump", List.of("jump"), "settings", List.of("settings")),
            List.of("requests", "jump", "settings"));
    public static final SubCommands PARTY = of(
            List.of("invite", "accept", "deny", "join", "leave", "kick", "promote", "demote", "transfer", "disband",
                    "list", "warp", "chat", "settings", "help"),
            Map.of("join", List.of("join"), "kick", List.of("kick"), "promote", List.of("promote", "demote"),
                    "transfer", List.of("transfer"), "disband", List.of("disband"), "warp", List.of("warp"),
                    "chat", List.of("chat"), "settings", List.of("settings")),
            List.of("join", "kick", "promote", "transfer", "disband", "warp", "chat", "settings"));
    public static final SubCommands CLANS = of(
            List.of("create", "info", "invite", "accept", "deny", "join", "leave", "kick", "promote", "demote",
                    "transfer", "disband", "list", "chat", "color", "rename", "tag", "settings", "help"),
            Map.ofEntries(Map.entry("info", List.of("info")), Map.entry("invite", List.of("invite", "accept", "deny")),
                    Map.entry("join", List.of("join")), Map.entry("kick", List.of("kick")),
                    Map.entry("promote", List.of("promote", "demote")), Map.entry("transfer", List.of("transfer")),
                    Map.entry("disband", List.of("disband")), Map.entry("list", List.of("list")),
                    Map.entry("chat", List.of("chat")), Map.entry("color", List.of("color")),
                    Map.entry("rename", List.of("rename", "tag")), Map.entry("settings", List.of("settings"))),
            List.of("info", "invite", "join", "kick", "promote", "transfer", "disband", "list", "chat", "color",
                    "rename", "settings"));
    public static final SubCommands MESSAGES = of(List.of("reply", "toggle"),
            Map.of("reply", List.of("reply"), "toggle", List.of("toggle")), List.of("reply", "toggle"));

    public SubCommands {
        actions = List.copyOf(actions);
        Map<String, List<String>> copied = new LinkedHashMap<>();
        switches.forEach((key, value) -> copied.put(key, List.copyOf(value)));
        switches = Collections.unmodifiableMap(copied);
        off = Set.copyOf(off);
    }

    private static SubCommands of(List<String> actions, Map<String, List<String>> groups, List<String> order) {
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        for (String key : order) {
            ordered.put(key, groups.get(key));
        }
        return new SubCommands(actions, ordered, Set.of());
    }

    public SubCommands withOff(Set<String> disabled) {
        Set<String> known = new LinkedHashSet<>();
        for (String key : disabled) {
            if (switches.containsKey(key)) {
                known.add(key);
            }
        }
        return new SubCommands(actions, switches, known);
    }

    public boolean on(String switchName) {
        return !off.contains(switchName);
    }

    public boolean enabled(String action) {
        for (String key : off) {
            if (switches.getOrDefault(key, List.of()).contains(action)) {
                return false;
            }
        }
        return true;
    }

    public List<String> visible() {
        List<String> visible = new ArrayList<>();
        for (String action : actions) {
            if (enabled(action)) {
                visible.add(action);
            }
        }
        return List.copyOf(visible);
    }
}
