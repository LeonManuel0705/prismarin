package net.prismarin.velocity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuildConstantsTest {

    @Test
    void theVersionComesFromTheBuild() {
        assertTrue(BuildConstants.VERSION.matches("\\d+\\.\\d+\\.\\d+(-.+)?"), BuildConstants.VERSION);
    }
}
