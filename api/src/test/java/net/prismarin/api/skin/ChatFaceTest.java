package net.prismarin.api.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class ChatFaceTest {

    private static ChatFace face() {
        List<Integer> pixels = new ArrayList<>(Collections.nCopies(64, 0x123456));
        pixels.set(0, 0xFF0000);
        pixels.set(1, 0xFF0000);
        pixels.set(7, 0x00FF00);
        pixels.set(63, 0xAB12CDEF);
        return new ChatFace(pixels, "abc", false);
    }

    @Test
    void everyPixelBecomesItsExactColour() {
        ChatFace face = face();
        assertEquals(TextColor.color(0xFF0000), face.color(0, 0));
        assertEquals(TextColor.color(0x00FF00), face.color(7, 0));
        assertEquals(0x12CDEF, face.rgb(7, 7));
        assertEquals("#12CDEF", face.color(7, 7).asHexString().toUpperCase(Locale.ROOT));
        assertThrows(IndexOutOfBoundsException.class, () -> face.rgb(8, 0));
    }

    @Test
    void aRowIsEightSymbolsWithNeighboursOfOneColourJoined() {
        Component line = face().line(0, "#");
        assertEquals("########", plain(line));
        List<Component> runs = line.children();
        assertEquals(3, runs.size());
        assertEquals("##", ((TextComponent) runs.get(0)).content());
        assertEquals(TextColor.color(0xFF0000), runs.get(0).color());
        assertEquals("#####", ((TextComponent) runs.get(1)).content());
        assertEquals(TextColor.color(0x00FF00), runs.get(2).color());
        List<Component> lines = face().lines();
        assertEquals(8, lines.size());
        assertEquals(ChatFace.SYMBOL.repeat(8), plain(lines.get(3)));
    }

    private static String plain(Component line) {
        StringBuilder text = new StringBuilder();
        for (Component child : line.children()) {
            text.append(((TextComponent) child).content());
        }
        return text.toString();
    }

    @Test
    void aFaceHasExactlySixtyFourPixels() {
        assertThrows(IllegalArgumentException.class, () -> new ChatFace(List.of(1, 2, 3), "x", false));
        assertEquals("", new ChatFace(Collections.nCopies(64, 0), null, true).source());
    }
}
