package net.prismarin.core.moderation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ReportsView(int session, String title, int rows, List<Item> items) {

    public record Item(int slot, String icon, String name, List<String> lore, Optional<UUID> skull, int amount,
                       boolean glow, String action, boolean close) {

        public Item {
            lore = List.copyOf(lore);
            action = action == null ? "" : action;
        }

        public boolean clickable() {
            return !action.isEmpty();
        }
    }

    public ReportsView {
        rows = Math.max(1, Math.min(6, rows));
        items = List.copyOf(items);
    }

    public static ReportsView parse(JsonObject message) {
        int rows = integer(message, "rows", 6);
        int size = Math.max(1, Math.min(6, rows)) * 9;
        List<Item> items = new ArrayList<>();
        JsonElement array = message.get("items");
        if (array != null && array.isJsonArray()) {
            for (JsonElement element : array.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                int slot = integer(item, "slot", -1);
                if (slot < 0 || slot >= size) {
                    continue;
                }
                List<String> lore = new ArrayList<>();
                JsonElement lines = item.get("lore");
                if (lines != null && lines.isJsonArray()) {
                    lines.getAsJsonArray().forEach(line -> lore.add(line.isJsonPrimitive() ? line.getAsString() : ""));
                }
                items.add(new Item(slot, text(item, "icon", "PAPER"), text(item, "name", ""), lore,
                        uuid(text(item, "skull", "")), Math.max(1, Math.min(64, integer(item, "amount", 1))),
                        bool(item, "glow"), text(item, "action", ""), bool(item, "close")));
            }
        }
        return new ReportsView(integer(message, "session", 0), text(message, "title", ""), rows, items);
    }

    private static Optional<UUID> uuid(String text) {
        try {
            return text.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String text(JsonObject object, String field, String fallback) {
        JsonElement element = object.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static int integer(JsonObject object, String field, int fallback) {
        JsonElement element = object.get(field);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
