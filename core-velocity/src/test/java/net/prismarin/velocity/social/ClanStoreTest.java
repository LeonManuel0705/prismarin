package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import net.prismarin.shared.messaging.LocalMessageBus;
import net.prismarin.shared.storage.DocumentStore;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.storage.SqlDatabase;
import net.prismarin.shared.storage.SqlDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClanStoreTest {

    @TempDir
    Path directory;

    private final UUID leader = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();

    private Clan clan() {
        return new Clan(UUID.randomUUID(), "Nightowls", "OWL", "gold", ClanAccess.OPEN, leader,
                List.of(new ClanMember(leader, "Leon", ClanRole.LEADER, 1L),
                        new ClanMember(member, "Alex", ClanRole.MODERATOR, 2L)),
                Set.of(UUID.randomUUID()), Map.of("wars:points", "7"), 1L);
    }

    @Test
    void clansSurviveARestartOnLocalFiles() {
        roundTrip(new JsonFileDocumentStore(directory));
    }

    @Test
    void clansSurviveARestartOnSqlite() {
        SqlDatabase database = SqlDatabase.sqlite(directory.resolve("prismarin.db"));
        try {
            roundTrip(new SqlDocumentStore(database));
        } finally {
            database.close();
        }
    }

    private void roundTrip(DocumentStore documents) {
        ClanStore store = new ClanStore(documents, null);
        Clan clan = clan();
        store.persist(clan.id(), () -> Optional.of(clan));
        ClanStore restarted = new ClanStore(documents, null);
        List<Clan> loaded = restarted.loadAll();
        assertEquals(1, loaded.size());
        Clan back = loaded.getFirst();
        assertEquals(clan.name(), back.name());
        assertEquals(clan.members(), back.members());
        assertEquals(clan.data(), back.data());
        assertEquals(Set.of(), back.invited());
        assertEquals(List.of(back.id()), restarted.conflicts("NIGHTOWLS", "new").stream().map(Clan::id).toList());
        assertEquals(List.of(back.id()), restarted.conflicts("Larks", "owl").stream().map(Clan::id).toList());
        assertEquals(List.of(), restarted.conflicts("Larks", "LRK"));
        ClanManager manager = new ClanManager(ClanRights.DEFAULT);
        loaded.forEach(manager::load);
        assertEquals(Optional.of(ClanRole.MODERATOR), manager.clanOf(member).flatMap(found -> found.role(member)));
        restarted.persist(clan.id(), Optional::empty);
        assertEquals(Optional.empty(), restarted.load(clan.id()));
        assertEquals(List.of(), new ClanStore(documents, null).loadAll());
    }

    @Test
    void otherProxiesHearAboutChangesButNotTheirOwn() {
        LocalMessageBus bus = new LocalMessageBus("proxy");
        DocumentStore documents = new JsonFileDocumentStore(directory);
        ClanStore first = new ClanStore(documents, bus);
        ClanStore second = new ClanStore(documents, bus);
        List<UUID> heardByFirst = new ArrayList<>();
        List<UUID> heardBySecond = new ArrayList<>();
        first.listen(heardByFirst::add);
        second.listen(heardBySecond::add);
        Clan clan = clan();
        first.persist(clan.id(), () -> Optional.of(clan));
        assertEquals(List.of(), heardByFirst);
        assertEquals(List.of(clan.id()), heardBySecond);
        assertTrue(second.load(clan.id()).isPresent());
    }
}
