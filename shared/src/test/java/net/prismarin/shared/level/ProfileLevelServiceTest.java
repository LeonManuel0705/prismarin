package net.prismarin.shared.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.level.LevelChange;
import net.prismarin.api.level.LevelChangedEvent;
import net.prismarin.api.level.LevelProgress;
import net.prismarin.api.level.LevelService;
import net.prismarin.api.level.XpGainedEvent;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.profile.ProfileServiceImpl;
import net.prismarin.shared.storage.JsonFileProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileLevelServiceTest {

    @TempDir
    Path dataDirectory;

    private final SimpleEventBus events = new SimpleEventBus();
    private final List<PrismarinEvent> published = new CopyOnWriteArrayList<>();
    private final UUID uuid = UUID.randomUUID();
    private JsonFileProfileRepository repository;
    private ExecutorService executor;
    private ProfileServiceImpl profiles;
    private ProfileLevelService levels;

    @BeforeEach
    void setUp() {
        repository = new JsonFileProfileRepository(dataDirectory);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        profiles = new ProfileServiceImpl(repository, "de", executor, events);
        levels = new ProfileLevelService(profiles);
        events.subscribe(XpGainedEvent.class, published::add);
        events.subscribe(LevelChangedEvent.class, published::add);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void addXpLevelsUpAndFiresBothEventsInOrder() {
        profiles.load(uuid, "Leon").join();

        LevelChange change = levels.addXp(uuid, 1300L, "bedwars-win").join();

        assertEquals(LevelProgress.of(0, 0L), change.before());
        assertEquals(LevelProgress.of(2, 50L), change.after());
        assertEquals(2, change.levelsGained());
        assertEquals(2, published.size());
        XpGainedEvent gained = assertInstanceOf(XpGainedEvent.class, published.get(0));
        assertEquals(1300L, gained.amount());
        assertEquals("bedwars-win", gained.reason());
        LevelChangedEvent level = assertInstanceOf(LevelChangedEvent.class, published.get(1));
        assertEquals(0, level.previousLevel());
        assertEquals(2, level.level());
        assertEquals(2, repository.find(uuid).orElseThrow().getLevel());
    }

    @Test
    void addXpBelowThresholdFiresOnlyXpEvent() {
        profiles.load(uuid, "Leon").join();

        levels.addXp(uuid, 100L).join();

        assertEquals(1, published.size());
        assertEquals(LevelService.REASON_PLUGIN, ((XpGainedEvent) published.getFirst()).reason());
        assertEquals(LevelProgress.of(0, 100L), levels.progress(uuid).orElseThrow());
    }

    @Test
    void negativeXpIsRejected() {
        profiles.load(uuid, "Leon").join();

        CompletionException error = assertThrows(CompletionException.class,
                () -> levels.addXp(uuid, -1L, "x").join());

        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertTrue(published.isEmpty());
    }

    @Test
    void setLevelResetsXpAndClamps() {
        profiles.load(uuid, "Leon").join();
        levels.addXp(uuid, 200L).join();
        published.clear();

        LevelChange change = levels.setLevel(uuid, 5, LevelService.REASON_COMMAND).join();
        LevelChange clamped = levels.setLevel(uuid, -4, LevelService.REASON_COMMAND).join();

        assertEquals(LevelProgress.of(5, 0L), change.after());
        assertEquals(LevelProgress.of(0, 0L), clamped.after());
        assertEquals(2, published.size());
        assertEquals("command", ((LevelChangedEvent) published.getFirst()).reason());
    }

    @Test
    void setLevelToSameLevelFiresNothing() {
        profiles.load(uuid, "Leon").join();

        LevelChange change = levels.setLevel(uuid, 0, "x").join();

        assertFalse(change.levelChanged());
        assertTrue(published.isEmpty());
    }

    @Test
    void addLevelsKeepsXpAndCanRemove() {
        profiles.load(uuid, "Leon").join();
        levels.addXp(uuid, 120L).join();

        levels.addLevels(uuid, 3, "quest").join();
        LevelChange down = levels.addLevels(uuid, -10, "punish").join();

        assertEquals(LevelProgress.of(3, 120L), down.before());
        assertEquals(LevelProgress.of(0, 120L), down.after());
        LevelChangedEvent last = (LevelChangedEvent) published.getLast();
        assertFalse(last.levelledUp());
        assertEquals("punish", last.reason());
    }

    @Test
    void offlineProfilesChangeAndFireToo() {
        repository.save(PlayerProfile.create(uuid, "Leon", "de"));

        levels.addXp(uuid, 500L, "offline").join();

        assertEquals(1, repository.find(uuid).orElseThrow().getLevel());
        assertTrue(levels.progress(uuid).isEmpty());
        assertEquals(LevelProgress.of(1, 0L), levels.find(uuid).join().orElseThrow());
        assertEquals(2, published.size());
    }

    @Test
    void directProfileChangesOfCachedProfilesFireEvents() {
        PlayerProfile profile = profiles.load(uuid, "Leon").join();

        profile.addXp(800L, "direct");

        assertEquals(2, published.size());
        assertEquals("direct", ((LevelChangedEvent) published.get(1)).reason());
    }

    @Test
    void unknownPlayerFails() {
        assertThrows(CompletionException.class, () -> levels.addXp(UUID.randomUUID(), 10L, "x").join());
    }

    @Test
    void clampKeepsTheLevelInRange() {
        assertEquals(0, ProfileLevelService.clamp(-5L));
        assertEquals(LevelProgress.MAX_LEVEL, ProfileLevelService.clamp(Long.MAX_VALUE));
        assertEquals(42, ProfileLevelService.clamp(42L));
    }
}
