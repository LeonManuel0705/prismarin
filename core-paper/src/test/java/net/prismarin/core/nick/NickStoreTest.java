package net.prismarin.core.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.nick.NickService;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NickStoreTest {

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long EXPIRY = 30L * DAY;

    @TempDir
    Path directory;

    private JsonFileDocumentStore documents;
    private NickStore store;
    private final UUID leon = UUID.randomUUID();
    private final UUID tim = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        documents = new JsonFileDocumentStore(directory);
        store = new NickStore(documents);
    }

    @Test
    void claimedNamesAreTakenForOthersOnly() {
        NickIdentity identity = new NickIdentity("SilentFox", "notch");
        assertTrue(store.claim(leon, identity, "bw-1", 1_000L, EXPIRY));

        assertTrue(store.claimedByOther("silentfox", tim, 2_000L, EXPIRY));
        assertFalse(store.claimedByOther("SILENTFOX", leon, 2_000L, EXPIRY));
        assertFalse(store.claim(tim, new NickIdentity("SilentFox", null), "bw-2", 2_000L, EXPIRY));
        assertEquals(leon, store.claim("silentFox").orElseThrow().owner());
        assertEquals("bw-1", store.claim("silentFox").orElseThrow().server());
        assertEquals(Optional.of(identity), store.owner(leon));
    }

    @Test
    void aLostClaimLeavesTheOwnerDocumentAlone() {
        store.claim(tim, new NickIdentity("LuckyOtter", null), "bw-2", 1_000L, EXPIRY);
        store.claim(leon, new NickIdentity("SilentFox", null), "bw-1", 1_000L, EXPIRY);

        assertFalse(store.claim(tim, new NickIdentity("SilentFox", null), "bw-2", 2_000L, EXPIRY));
        assertEquals("LuckyOtter", store.owner(tim).orElseThrow().name());
    }

    @Test
    void theKindSurvivesClaimsOwnersAndRepairs() {
        store.claim(leon, new NickIdentity("SilentFox", "notch", NickIdentity.Kind.ROUND), "bw-1", 0L, EXPIRY);
        assertEquals(NickIdentity.Kind.ROUND, store.owner(leon).orElseThrow().kind());
        assertEquals(NickIdentity.Kind.ROUND, store.claim("SilentFox").orElseThrow().kind());

        documents.delete(NickStore.OWNERS, leon.toString());
        NickIdentity repaired = store.resolve(leon, Map.of(NickService.SETTING_NAME, "SilentFox"), DAY, EXPIRY)
                .orElseThrow();
        assertTrue(repaired.roundScoped());

        store.claim(tim, new NickIdentity("LuckyOtter", null, NickIdentity.Kind.AUTO), "bw-2", 0L, EXPIRY);
        assertEquals(NickIdentity.Kind.AUTO, store.owner(tim).orElseThrow().kind());
        assertEquals(NickIdentity.Kind.MANUAL, new NickIdentity("Manual", null).kind());
        assertEquals(NickIdentity.Kind.MANUAL, new NickIdentity("Manual", null, null).kind());
    }

    @Test
    void storedKindsStayReadableAcrossVersions() {
        documents.save(NickStore.OWNERS, leon.toString(), "{\"name\":\"OldAuto\",\"auto\":true}");
        documents.save(NickStore.OWNERS, tim.toString(), "{\"name\":\"OldManual\"}");
        UUID other = UUID.randomUUID();
        documents.save(NickStore.OWNERS, other.toString(), "{\"name\":\"Odd\",\"auto\":\"later\"}");

        assertEquals(NickIdentity.Kind.AUTO, store.owner(leon).orElseThrow().kind());
        assertEquals(NickIdentity.Kind.MANUAL, store.owner(tim).orElseThrow().kind());
        assertEquals(NickIdentity.Kind.MANUAL, store.owner(other).orElseThrow().kind());
        assertFalse(store.owner(other).orElseThrow().roundScoped());
    }

    @Test
    void expiredClaimsAreFreeAgain() {
        store.claim(leon, new NickIdentity("SilentFox", null), "bw-1", 0L, EXPIRY);

        assertFalse(store.claimedByOther("SilentFox", tim, EXPIRY, EXPIRY));
        assertTrue(store.claim(tim, new NickIdentity("SilentFox", null), "bw-2", EXPIRY, EXPIRY));
        assertEquals(tim, store.claim("SilentFox").orElseThrow().owner());
    }

    @Test
    void releaseRemovesOnlyOwnClaims() {
        store.claim(leon, new NickIdentity("SilentFox", null), "bw-1", 0L, EXPIRY);
        store.release(tim, "SilentFox");
        assertTrue(store.claim("SilentFox").isPresent());

        store.release(leon, "SilentFox");
        assertTrue(store.claim("SilentFox").isEmpty());
        assertTrue(store.owner(leon).isEmpty());
    }

    @Test
    void releaseClaimKeepsTheOwnerDocument() {
        store.claim(leon, new NickIdentity("SilentFox", null), "bw-1", 0L, EXPIRY);
        store.claim(leon, new NickIdentity("LuckyOtter", null), "bw-1", 0L, EXPIRY);
        store.releaseClaim(leon, "SilentFox");

        assertTrue(store.claim("SilentFox").isEmpty());
        assertEquals("LuckyOtter", store.owner(leon).orElseThrow().name());
    }

    @Test
    void resolvePrefersTheOwnerDocumentOverStaleSettings() {
        store.claim(leon, new NickIdentity("LuckyOtter", "jeb_"), "bw-1", 0L, EXPIRY);
        Map<String, String> settings = new HashMap<>(Map.of(NickService.SETTING_NAME, "OldNick"));

        assertEquals(Optional.of(new NickIdentity("LuckyOtter", "jeb_")),
                store.resolve(leon, settings, DAY, EXPIRY));
    }

    @Test
    void resolveDropsANickSomeoneElseTookOver() {
        store.claim(leon, new NickIdentity("SilentFox", null), "bw-1", 0L, EXPIRY);
        store.claim(tim, new NickIdentity("SilentFox", null), "bw-2", EXPIRY, EXPIRY);

        assertEquals(Optional.empty(), store.resolve(leon, Map.of(), EXPIRY + 1L, EXPIRY));
        assertTrue(store.owner(leon).isEmpty());
    }

    @Test
    void resolveRepairsAMissingOwnerDocumentFromTheSettings() {
        store.claim(leon, new NickIdentity("SilentFox", "notch"), "bw-1", 0L, EXPIRY);
        documents.delete(NickStore.OWNERS, leon.toString());

        Optional<NickIdentity> resolved = store.resolve(leon,
                Map.of(NickService.SETTING_NAME, "silentfox"), DAY, EXPIRY);

        assertEquals(Optional.of(new NickIdentity("SilentFox", "notch")), resolved);
        assertEquals(resolved, store.owner(leon));
    }

    @Test
    void resolveIgnoresSettingsWithoutOwnClaim() {
        store.claim(tim, new NickIdentity("SilentFox", null), "bw-2", 0L, EXPIRY);
        assertEquals(Optional.empty(), store.resolve(leon, Map.of(NickService.SETTING_NAME, "SilentFox"), DAY, EXPIRY));
        assertEquals(Optional.empty(), store.resolve(leon, Map.of(NickService.SETTING_NAME, "Unknown"), DAY, EXPIRY));
        assertEquals(Optional.empty(), store.resolve(leon, Map.of(), DAY, EXPIRY));
    }

    @Test
    void skinsRoundTripAndAge() {
        store.saveSkin(new NickStore.StoredSkin("Notch", "value", "signature", 100L));

        NickStore.StoredSkin skin = store.skin("notch").orElseThrow();
        assertEquals("Notch", skin.name());
        assertEquals("value", skin.value());
        assertEquals("signature", skin.signature());
        assertTrue(skin.fresh(150L, 100L));
        assertFalse(skin.fresh(200L, 100L));
        assertTrue(store.skin("jeb_").isEmpty());
    }

    @Test
    void brokenDocumentsAreIgnored() {
        documents.save(NickStore.CLAIMS, "brokenone", "{not json");
        documents.save(NickStore.CLAIMS, "nouuid", "{\"name\":\"NoUuid\"}");
        documents.save(NickStore.SKINS, "unsigned", "{\"name\":\"Unsigned\",\"value\":\"v\"}");

        assertTrue(store.claim("brokenOne").isEmpty());
        assertTrue(store.claim("NoUuid").isEmpty());
        assertTrue(store.skin("Unsigned").isEmpty());
        assertFalse(store.claimedByOther("NoUuid", leon, 0L, EXPIRY));
    }

    @Test
    void identityNormalisesTheSkinKey() {
        assertEquals(NickIdentity.OWN_SKIN, new NickIdentity("Fox", null).skin());
        assertEquals(NickIdentity.OWN_SKIN, new NickIdentity("Fox", " ").skin());
        assertEquals("notch", new NickIdentity("Fox", "Notch").skin());
        assertTrue(new NickIdentity("Fox", NickIdentity.DEFAULT_SKIN).defaultSkin());
        assertTrue(new NickIdentity("Fox", "x").sameName("fOX"));
    }
}
