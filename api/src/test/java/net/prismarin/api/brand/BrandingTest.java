package net.prismarin.api.brand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BrandingTest {

    @Test
    void titleIsBoldGradient() {
        assertEquals("<gradient:#22D3EE:#2DD4BF><b>PRISMARIN</b></gradient>", Branding.DEFAULT.title());
    }

    @Test
    void colorsMustBeHex() {
        assertThrows(IllegalArgumentException.class, () -> new Branding("X", "red", "#000000", ""));
        assertThrows(IllegalArgumentException.class, () -> new Branding("X", "#000000", "#00000", ""));
    }

    @Test
    void nameMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> new Branding(" ", "#000000", "#000000", ""));
    }
}
