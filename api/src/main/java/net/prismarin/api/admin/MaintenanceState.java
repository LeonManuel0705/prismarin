package net.prismarin.api.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public record MaintenanceState(boolean network, Set<String> servers, Set<String> groups) {

    public static final Pattern NAME = Pattern.compile("[^\\s:]{1,64}");
    public static final String SERVER_PREFIX = "server:";
    public static final String GROUP_PREFIX = "group:";
    public static final MaintenanceState OPEN = new MaintenanceState(false, Set.of(), Set.of());

    public MaintenanceState {
        servers = normalized(servers);
        groups = normalized(groups);
    }

    public static MaintenanceState of(boolean network, Collection<String> targets) {
        Set<String> servers = new TreeSet<>();
        Set<String> groups = new TreeSet<>();
        if (targets != null) {
            for (String target : targets) {
                String value = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
                if (value.startsWith(SERVER_PREFIX) && validName(value.substring(SERVER_PREFIX.length()))) {
                    servers.add(value.substring(SERVER_PREFIX.length()));
                } else if (value.startsWith(GROUP_PREFIX) && validName(value.substring(GROUP_PREFIX.length()))) {
                    groups.add(value.substring(GROUP_PREFIX.length()));
                }
            }
        }
        return new MaintenanceState(network, servers, groups);
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static String name(String name) {
        String value = name == null ? "" : name.trim();
        if (!validName(value)) {
            throw new IllegalArgumentException("'" + name + "' is no server or group name");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public boolean serverBlocked(String server) {
        return server != null && servers.contains(server.toLowerCase(Locale.ROOT));
    }

    public boolean groupBlocked(String group) {
        return group != null && groups.contains(group.toLowerCase(Locale.ROOT));
    }

    public boolean blocks(String server, String group) {
        return serverBlocked(server) || groupBlocked(group);
    }

    public boolean anything() {
        return network || !servers.isEmpty() || !groups.isEmpty();
    }

    public List<String> targets() {
        List<String> targets = new ArrayList<>();
        servers.forEach(server -> targets.add(SERVER_PREFIX + server));
        groups.forEach(group -> targets.add(GROUP_PREFIX + group));
        return List.copyOf(targets);
    }

    private static Set<String> normalized(Set<String> names) {
        Set<String> sorted = new TreeSet<>();
        if (names != null) {
            names.stream().filter(MaintenanceState::validName).map(name -> name.toLowerCase(Locale.ROOT))
                    .forEach(sorted::add);
        }
        return Collections.unmodifiableSet(sorted);
    }
}
