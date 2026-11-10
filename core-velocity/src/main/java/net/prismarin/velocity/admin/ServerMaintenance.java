package net.prismarin.velocity.admin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.shared.storage.DocumentStore;
import net.prismarin.velocity.locale.ProxyMessages;

public final class ServerMaintenance {

    public static final String BYPASS = "prismarin.maintenance.bypass";
    private static final String COLLECTION = "maintenance";
    private static final String DOCUMENT = "targets";

    private final DocumentStore documents;
    private final ServerDirectory directory;
    private final ProxyMessages messages;
    private final Gson gson = new Gson();
    private final Set<String> targets = new TreeSet<>();

    public ServerMaintenance(DocumentStore documents, ServerDirectory directory, ProxyMessages messages) {
        this.documents = documents;
        this.directory = directory;
        this.messages = messages;
        documents.find(COLLECTION, DOCUMENT).ifPresent(json -> targets.addAll(gson.fromJson(json,
                new TypeToken<Set<String>>() { }.getType())));
    }

    public synchronized boolean toggle(String target, boolean active) {
        String key = target.toLowerCase(Locale.ROOT);
        boolean changed = active ? targets.add(key) : targets.remove(key);
        if (changed) {
            documents.save(COLLECTION, DOCUMENT, gson.toJson(targets));
        }
        return changed;
    }

    public synchronized Set<String> targets() {
        return Set.copyOf(targets);
    }

    public synchronized boolean blocked(String server) {
        String name = server.toLowerCase(Locale.ROOT);
        if (targets.contains("server:" + name)) {
            return true;
        }
        return directory.byName(server).map(NetworkServer::group)
                .filter(group -> targets.contains("group:" + group)).isPresent();
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        String server = event.getOriginalServer().getServerInfo().getName();
        if (!blocked(server) || event.getPlayer().hasPermission(BYPASS)) {
            return;
        }
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        messages.send(event.getPlayer(), "maintenance.server-blocked", Placeholder.unparsed("server", server));
    }
}
