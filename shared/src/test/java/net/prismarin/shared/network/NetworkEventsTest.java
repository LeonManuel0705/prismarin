package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.moderation.Report;
import net.prismarin.api.moderation.ReportFiledEvent;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileLoadedEvent;
import org.junit.jupiter.api.Test;

class NetworkEventsTest {

    @Test
    void filedReportsTravelToBackends() {
        Report report = report(List.of("<Leon> hi"));

        JsonObject message = NetworkEvents.encode(new ReportFiledEvent(report)).orElseThrow();
        Optional<PrismarinEvent> decoded = NetworkEvents.decode(JsonParser.parseString(message.toString())
                .getAsJsonObject());

        assertEquals(Optional.of(new ReportFiledEvent(report)), decoded);
    }

    @Test
    void longChatIsDroppedSoTheEventStillFits() {
        List<String> chat = new ArrayList<>();
        for (int index = 0; index < 400; index++) {
            chat.add("<Spammer> " + "x".repeat(100));
        }

        JsonObject message = NetworkEvents.encode(new ReportFiledEvent(report(chat))).orElseThrow();
        ReportFiledEvent event = (ReportFiledEvent) NetworkEvents.decode(message).orElseThrow();

        assertTrue(NetworkCalls.fits(message));
        assertEquals(List.of(), event.report().chat());
    }

    @Test
    void otherEventsAndGarbageAreIgnored() {
        PlayerProfile profile = PlayerProfile.create(UUID.randomUUID(), "Leon", "de");
        assertEquals(Optional.empty(), NetworkEvents.encode(new ProfileLoadedEvent(profile, true)));
        assertEquals(Optional.empty(), NetworkEvents.decode(null));
        assertEquals(Optional.empty(), NetworkEvents.decode(JsonParser.parseString("{\"event\":\"nope\"}")
                .getAsJsonObject()));
        assertEquals(Optional.empty(), NetworkEvents.decode(JsonParser.parseString(
                "{\"event\":\"report-filed\",\"report\":{\"id\":\"1\"}}").getAsJsonObject()));
    }

    private static Report report(List<String> chat) {
        return new Report("17", UUID.randomUUID(), "Steve", "SilentFox", "bw-1", "1.21.11",
                List.of(new Report.Reporter(UUID.randomUUID(), "Leon", "hacking Reach", 1000L)), chat, 1000L, "", 0L,
                true, "", 0L, null);
    }
}
