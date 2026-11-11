package net.prismarin.api.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorageNamesTest {

    @Test
    void namespacesAreLowercasedAndTrimmed() {
        assertEquals("bedwars", StorageNames.namespace(" BedWars "));
        assertEquals("my-plugin2", StorageNames.namespace("My-Plugin2"));
        assertEquals("x".repeat(24), StorageNames.namespace("x".repeat(24)));
    }

    @Test
    void namespacesWithoutAllowedShapeAreRejected() {
        for (String invalid : new String[] {null, "", "a", "-bedwars", "bed_wars", "bed wars", "bed.wars",
                "x".repeat(25), "ext_x", "ü-plugin"}) {
            assertThrows(IllegalArgumentException.class, () -> StorageNames.namespace(invalid), String.valueOf(invalid));
            assertFalse(StorageNames.validNamespace(invalid), String.valueOf(invalid));
        }
    }

    @Test
    void reservedNamespacesAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> StorageNames.namespace("PRISMARIN"));
        assertTrue(error.getMessage().contains("reserved"));
        assertFalse(StorageNames.validNamespace("prismarin"));
    }

    @Test
    void collectionsAllowShortNamesButNoUnderscore() {
        assertEquals("s", StorageNames.collection("S"));
        assertEquals("round-stats", StorageNames.collection("round-stats"));
        assertEquals("x".repeat(32), StorageNames.collection("x".repeat(32)));
        for (String invalid : new String[] {null, "", "-stats", "round_stats", "x".repeat(33), "a/b"}) {
            assertThrows(IllegalArgumentException.class, () -> StorageNames.collection(invalid),
                    String.valueOf(invalid));
        }
    }

    @Test
    void idsKeepTheirCaseAndHaveALengthLimit() {
        assertEquals("Leon ä/1", StorageNames.id("Leon ä/1"));
        assertEquals("x".repeat(128), StorageNames.id("x".repeat(128)));
        assertThrows(IllegalArgumentException.class, () -> StorageNames.id(""));
        assertThrows(IllegalArgumentException.class, () -> StorageNames.id(null));
        assertThrows(IllegalArgumentException.class, () -> StorageNames.id("x".repeat(129)));
        assertTrue(StorageNames.validId("a"));
    }
}
