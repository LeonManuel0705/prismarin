package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.velocity.locale.ProxyMessages;

public record AdminContext(ProxyServer proxy, CoreServices services, ProxyMessages messages, VanishRegistry vanish,
                           ModerationChannel channel, NetworkConnector connector, ServerDirectory directory,
                           NetworkStatusService status, AdminSettings settings) {

    public record Target(UUID uuid, String name) {
    }

    public static String actor(CommandSource source) {
        return source instanceof Player player ? player.getUsername() : "Console";
    }

    public boolean allowed(CommandSource source, String permission) {
        if (source.hasPermission(permission)) {
            return true;
        }
        messages.send(source, "general.no-permission");
        return false;
    }

    public void send(CommandSource source, String key, TagResolver... resolvers) {
        messages.send(source, key, resolvers);
    }

    public CompletableFuture<Optional<Target>> resolve(String name) {
        Optional<Player> online = proxy.getPlayer(name);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(
                    new Target(online.get().getUniqueId(), online.get().getUsername())));
        }
        return services.profiles().lookup(name).thenCompose(uuid -> uuid
                .map(found -> services.profiles().find(found).thenApply(profile -> Optional.of(
                        new Target(found, profile.map(value -> value.getName()).orElse(name)))))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    public void notifyStaff(String permission, String key, TagResolver... resolvers) {
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(permission)) {
                messages.send(player, key, resolvers);
            }
        }
        messages.send(proxy.getConsoleCommandSource(), key, resolvers);
    }

    public static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
