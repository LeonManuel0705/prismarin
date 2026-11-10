package net.prismarin.core.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportsViewTest {

    @Test
    void readsTheMenuTheProxySent() {
        UUID target = UUID.randomUUID();
        ReportsView view = ReportsView.parse(JsonParser.parseString("{\"session\":7,\"title\":\"<white>Reports\","
                + "\"rows\":6,\"items\":[{\"slot\":10,\"icon\":\"PLAYER_HEAD\",\"name\":\"Steve\","
                + "\"lore\":[\"a\",\"b\"],\"skull\":\"" + target + "\",\"amount\":3,\"glow\":true,"
                + "\"action\":\"open:12345\",\"close\":false},{\"slot\":49,\"icon\":\"BARRIER\","
                + "\"name\":\"Close\",\"close\":true}]}").getAsJsonObject());
        assertEquals(7, view.session());
        assertEquals(6, view.rows());
        assertEquals(2, view.items().size());
        ReportsView.Item head = view.items().getFirst();
        assertEquals(List.of("a", "b"), head.lore());
        assertEquals(target, head.skull().orElseThrow());
        assertEquals(3, head.amount());
        assertTrue(head.glow());
        assertTrue(head.clickable());
        ReportsView.Item close = view.items().getLast();
        assertFalse(close.clickable());
        assertTrue(close.close());
        assertTrue(close.skull().isEmpty());
        assertEquals(1, close.amount());
    }

    @Test
    void dropsBrokenItemsAndClampsTheSize() {
        ReportsView view = ReportsView.parse(JsonParser.parseString("{\"rows\":9,\"items\":[{\"slot\":54},"
                + "{\"slot\":-1},\"nonsense\",{\"slot\":53,\"skull\":\"not-a-uuid\",\"amount\":500}]}")
                .getAsJsonObject());
        assertEquals(6, view.rows());
        assertEquals(0, view.session());
        assertEquals(1, view.items().size());
        assertEquals(64, view.items().getFirst().amount());
        assertTrue(view.items().getFirst().skull().isEmpty());
        assertEquals("PAPER", view.items().getFirst().icon());
        assertEquals(0, ReportsView.parse(JsonParser.parseString("{}").getAsJsonObject()).items().size());
    }
}
