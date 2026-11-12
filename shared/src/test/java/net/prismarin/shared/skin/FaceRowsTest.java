package net.prismarin.shared.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class FaceRowsTest {

    private static List<String> plain(List<Component> lines) {
        return lines.stream().map(line -> PlainTextComponentSerializer.plainText().serialize(line)).toList();
    }

    @Test
    void textsStandNextToTheirFaceRows() {
        List<String> lines = plain(FaceRows.compose(DefaultFaces.STEVE_FACE, "#", "  ", Map.of(
                2, Component.text("Lea plays BedWars"), 4, Component.text("bw-2"), 7, Component.text("[Join]")),
                false, false));
        assertEquals(List.of("########", "########  Lea plays BedWars", "########", "########  bw-2", "########",
                "########", "########  [Join]", "########"), lines);
    }

    @Test
    void moreThanEightRowsContinueBelowTheFace() {
        List<String> lines = plain(FaceRows.compose(DefaultFaces.ALEX_FACE, "#", " ", Map.of(
                1, Component.text("top"), 10, Component.text("below"), 40, Component.text("ignored")), true, true));
        assertEquals(12, lines.size());
        assertEquals("", lines.getFirst());
        assertEquals("######## top", lines.get(1));
        assertEquals("", lines.get(9));
        assertEquals("below", lines.get(10));
        assertEquals("", lines.getLast());
    }

    @Test
    void withoutAFaceOnlyTheTextRowsAreLeft() {
        List<String> lines = plain(FaceRows.compose(null, "#", " ", Map.of(2, Component.text("only")), false,
                false));
        assertEquals(List.of("", "only"), lines);
        assertTrue(FaceRows.compose(null, "#", " ", Map.of(), false, false).isEmpty());
        assertEquals("a\nb", PlainTextComponentSerializer.plainText().serialize(FaceRows.join(List.of(
                Component.text("a"), Component.text("b")))));
    }

    @Test
    void pluginRowsReplaceAddAndRemoveConfiguredRows() {
        Map<Integer, String> merged = FaceRows.texts(Map.of(2, "joinme.face.title", 4, "joinme.face.details",
                7, "joinme.face.button"), Map.of(4, "<gold>Castle, 4x2</gold>", 7, " ", 9, "joinme.face.extra",
                0, "nothing", 99, "nothing"));
        assertEquals(Map.of(2, "joinme.face.title", 4, "<gold>Castle, 4x2</gold>", 9, "joinme.face.extra"), merged);
        assertTrue(FaceRows.KEY.matcher("joinme.face.title").matches());
        assertTrue(!FaceRows.KEY.matcher("<gold>Castle</gold>").matches());
        assertTrue(!FaceRows.KEY.matcher("plain").matches());
    }
}
