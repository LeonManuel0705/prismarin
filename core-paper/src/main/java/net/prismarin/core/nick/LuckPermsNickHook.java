package net.prismarin.core.nick;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PrefixNode;

final class LuckPermsNickHook {

    private static final String NAME_COLOR_META = "name-color";

    private final Map<UUID, List<Node>> applied = new ConcurrentHashMap<>();

    Optional<FakeRankResolver.GroupLook> group(String name) {
        return Optional.ofNullable(luckPerms().getGroupManager().getGroup(name)).map(LuckPermsNickHook::look);
    }

    List<FakeRankResolver.GroupLook> groups() {
        return luckPerms().getGroupManager().getLoadedGroups().stream().map(LuckPermsNickHook::look).toList();
    }

    private static FakeRankResolver.GroupLook look(Group group) {
        CachedMetaData meta = group.getCachedData().getMetaData();
        OptionalInt weight = group.getWeight();
        return new FakeRankResolver.GroupLook(meta.getPrefix(), weight, meta.getMetaValue(NAME_COLOR_META));
    }

    boolean groupHas(String name, String permission) {
        Group group = luckPerms().getGroupManager().getGroup(name);
        return group != null && group.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    Optional<Boolean> hasAny(UUID player, List<String> permissions) {
        User user = luckPerms().getUserManager().getUser(player);
        if (user == null) {
            return Optional.empty();
        }
        CachedPermissionData data = user.getCachedData().getPermissionData();
        return Optional.of(permissions.stream().anyMatch(permission -> data.checkPermission(permission).asBoolean()));
    }

    void disguise(UUID player, String prefix, int priority, String nameColor) {
        undisguise(player);
        User user = luckPerms().getUserManager().getUser(player);
        if (user == null) {
            return;
        }
        List<Node> nodes = List.of(
                PrefixNode.builder(prefix, priority).build(),
                MetaNode.builder(NAME_COLOR_META, nameColor).build());
        nodes.forEach(node -> user.transientData().add(node));
        applied.put(player, nodes);
    }

    void undisguise(UUID player) {
        List<Node> nodes = applied.remove(player);
        if (nodes == null) {
            return;
        }
        User user = luckPerms().getUserManager().getUser(player);
        if (user != null) {
            nodes.forEach(node -> user.transientData().remove(node));
        }
    }

    void undisguiseAll() {
        List.copyOf(applied.keySet()).forEach(this::undisguise);
    }

    private static LuckPerms luckPerms() {
        return LuckPermsProvider.get();
    }
}
