package net.prismarin.api.vanish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.staff.StaffModeApi;
import net.prismarin.api.staff.StaffModeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VanishApiTest {

    private static final class FakeVanish implements VanishService {

        private final Set<UUID> vanished = new HashSet<>();

        @Override
        public boolean vanished(UUID player) {
            return vanished.contains(player);
        }

        @Override
        public Set<UUID> vanishedPlayers() {
            return Set.copyOf(vanished);
        }

        @Override
        public int level(UUID player) {
            return vanished(player) ? 2 : 0;
        }

        @Override
        public boolean canSee(UUID viewer, UUID target) {
            return !vanished(target) || target.equals(viewer);
        }

        @Override
        public boolean vanish(UUID player) {
            return vanished.add(player);
        }

        @Override
        public boolean reveal(UUID player) {
            return vanished.remove(player);
        }
    }

    private static final class FakeStaffMode implements StaffModeService {

        private final Set<UUID> active = new HashSet<>();

        @Override
        public boolean active(UUID player) {
            return active.contains(player);
        }

        @Override
        public boolean enter(UUID player) {
            return active.add(player);
        }

        @Override
        public boolean leave(UUID player) {
            return active.remove(player);
        }

        @Override
        public boolean nickIncluded() {
            return true;
        }

        @Override
        public boolean vanishIncluded() {
            return false;
        }
    }

    @AfterEach
    void tearDown() {
        VanishApi.unregister();
        StaffModeApi.unregister();
    }

    @Test
    void toggleReturnsTheNewVanishState() {
        FakeVanish vanish = new FakeVanish();
        UUID player = UUID.randomUUID();

        assertTrue(vanish.toggle(player));
        assertTrue(vanish.vanished(player));
        assertFalse(vanish.toggle(player));
        assertFalse(vanish.vanished(player));
    }

    @Test
    void staffToggleReturnsTheNewState() {
        FakeStaffMode staff = new FakeStaffMode();
        UUID player = UUID.randomUUID();

        assertTrue(staff.toggle(player));
        assertFalse(staff.toggle(player));
    }

    @Test
    void holdersAreEmptyUntilRegistered() {
        assertTrue(VanishApi.service().isEmpty());
        assertTrue(StaffModeApi.staffMode().isEmpty());

        FakeVanish vanish = new FakeVanish();
        VanishApi.register(vanish);
        StaffModeApi.register(new FakeStaffMode());

        assertEquals(vanish, VanishApi.service().orElseThrow());
        assertTrue(StaffModeApi.staffMode().isPresent());
    }

    @Test
    void eventLevelFollowsTheState() {
        UUID player = UUID.randomUUID();

        assertEquals(0, new VanishChangedEvent(player, false, 5, VanishChangedEvent.Cause.COMMAND).level());
        assertEquals(1, new VanishChangedEvent(player, true, 0, VanishChangedEvent.Cause.JOIN).level());
        assertEquals(3, new VanishChangedEvent(player, true, 3, VanishChangedEvent.Cause.PLUGIN).level());
        assertThrows(NullPointerException.class, () -> new VanishChangedEvent(player, true, 1, null));
    }
}
