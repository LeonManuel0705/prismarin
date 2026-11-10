package net.prismarin.velocity.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportAction;
import net.prismarin.shared.text.LegacyColors;

public final class ReportActions {

    public static final int MAX = 14;

    private static final int PER_ROW = 7;
    private static final int FIRST_ROW = 18;

    private ReportActions() {
        throw new AssertionError();
    }

    public static String fill(String command, Report report, String staff) {
        return command.replace("%player%", report.targetName())
                .replace("%uuid%", report.target().toString())
                .replace("%id%", report.id())
                .replace("%server%", report.server())
                .replace("%staff%", staff);
    }

    public static Component label(ReportAction action, Locale locale) {
        return MiniMessage.miniMessage().deserialize(LegacyColors.toMiniMessage(action.name(locale)));
    }

    public static List<Integer> slots(int count) {
        List<Integer> slots = new ArrayList<>();
        int remaining = Math.max(0, Math.min(count, MAX));
        for (int row = 0; remaining > 0; row++) {
            int inRow = Math.min(PER_ROW, remaining);
            int base = FIRST_ROW + row * 9;
            for (int index = 0; index < inRow; index++) {
                slots.add(inRow <= 4 ? base + 4 - (inRow - 1) + index * 2 : base + (9 - inRow) / 2 + index);
            }
            remaining -= inRow;
        }
        return slots;
    }
}
