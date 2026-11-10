package net.prismarin.velocity.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public record FollowPlan(List<UUID> move, List<UUID> noRoom) {

    public record Member(UUID uuid, String server) {
    }

    public FollowPlan {
        move = List.copyOf(move);
        noRoom = List.copyOf(noRoom);
    }

    public static boolean follows(boolean enabled, boolean intoLobbies, Set<String> skipGroups, String group,
                                  boolean lobby) {
        if (!enabled) {
            return false;
        }
        if (lobby && !intoLobbies) {
            return false;
        }
        return group == null || !skipGroups.contains(group.toLowerCase(Locale.ROOT));
    }

    public static FollowPlan plan(UUID leader, String target, List<Member> members, int freeSlots) {
        List<UUID> move = new ArrayList<>();
        List<UUID> noRoom = new ArrayList<>();
        int room = freeSlots;
        for (Member member : members) {
            if (member.uuid().equals(leader) || target.equalsIgnoreCase(member.server())) {
                continue;
            }
            if (room < 0) {
                move.add(member.uuid());
                continue;
            }
            if (room > 0) {
                move.add(member.uuid());
                room--;
            } else {
                noRoom.add(member.uuid());
            }
        }
        return new FollowPlan(move, noRoom);
    }

    public static int freeSlots(int maxPlayers, int connected) {
        if (maxPlayers <= 0) {
            return -1;
        }
        return Math.max(0, maxPlayers - connected);
    }
}
