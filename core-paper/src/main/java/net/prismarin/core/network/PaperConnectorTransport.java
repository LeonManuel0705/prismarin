package net.prismarin.core.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.shared.status.SelectingConnector;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PaperConnectorTransport implements SelectingConnector.Transport {

    private final Plugin plugin;

    public PaperConnectorTransport(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Boolean> connect(UUID playerId, String server) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                result.complete(false);
                return;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF("Connect");
                out.writeUTF(server);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            player.sendPluginMessage(plugin, NetworkMenu.CHANNEL, bytes.toByteArray());
            result.complete(true);
        });
        return result;
    }
}
