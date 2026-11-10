package net.prismarin.shared.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.social.FriendInfo;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FriendDocumentsTest {

    @TempDir
    Path directory;

    private final UUID leon = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID sam = UUID.randomUUID();

    @Test
    void readsTheFriendListTheProxyWrote() {
        JsonFileDocumentStore store = new JsonFileDocumentStore(directory);
        store.save(FriendDocuments.FRIENDS, leon.toString(), "{\"uuid\":\"" + leon + "\",\"friends\":{\"" + alex
                + "\":10,\"" + sam + "\":20,\"broken\":5},\"incoming\":{},\"outgoing\":{},\"settings\":{}}");
        store.save(FriendDocuments.PRESENCE, alex.toString(), "{\"uuid\":\"" + alex + "\",\"name\":\"Alex\"}");
        FriendDocuments friends = new FriendDocuments(store);
        assertEquals(Map.of(alex, 10L, sam, 20L), friends.friendTimes(leon));
        assertTrue(friends.areFriends(leon, alex));
        assertFalse(friends.areFriends(alex, leon));
        assertFalse(friends.areFriends(leon, leon));
        List<FriendInfo> list = friends.friends(leon, uuid -> uuid.equals(sam) ? Optional.of("Sam") : Optional.empty());
        assertEquals(List.of(new FriendInfo(alex, "Alex", 10L), new FriendInfo(sam, "Sam", 20L)), list);
    }

    @Test
    void missingOrBrokenDocumentsMeanNoFriends() {
        JsonFileDocumentStore store = new JsonFileDocumentStore(directory);
        store.save(FriendDocuments.FRIENDS, alex.toString(), "not json");
        FriendDocuments friends = new FriendDocuments(store);
        assertTrue(friends.friendTimes(leon).isEmpty());
        assertTrue(friends.friendTimes(alex).isEmpty());
        assertEquals(Optional.empty(), friends.knownName(sam));
    }
}
