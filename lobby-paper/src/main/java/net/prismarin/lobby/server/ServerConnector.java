package net.prismarin.lobby.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.prismarin.lobby.LobbyPlugin;
import org.bukkit.entity.Player;

@Singleton
public final class ServerConnector {

    public static final String CHANNEL = "BungeeCord";

    private final LobbyPlugin plugin;

    @Inject
    public ServerConnector(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect(Player player, String serverName) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
    }
}
