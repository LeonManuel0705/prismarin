package net.prismarin.core.bridge;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.Prismarin;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerStatus;

public final class PrismarinPlaceholders {

    private final Prismarin prismarin;
    private final NickService nicks;

    public PrismarinPlaceholders(Prismarin prismarin) {
        this(prismarin, null);
    }

    public PrismarinPlaceholders(Prismarin prismarin, NickService nicks) {
        this.prismarin = prismarin;
        this.nicks = nicks;
    }

    public String resolveRelational(UUID viewer, boolean viewerSeesRealNames, UUID target, String params) {
        if (target == null) {
            return null;
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "nick" -> nickName(target);
            case "realname" -> viewerSeesRealNames || target.equals(viewer) ? realName(target) : nickName(target);
            default -> null;
        };
    }

    public String resolve(UUID player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "brand" -> prismarin.branding().name();
            case "server" -> prismarin.serverId();
            case "balance" -> balance(player, primary().orElse(null));
            case "level" -> profile(player).map(profile -> String.valueOf(profile.getLevel())).orElse("0");
            case "xp" -> profile(player).map(profile -> String.valueOf(profile.getXp())).orElse("0");
            case "xp_required" -> profile(player).map(profile -> String.valueOf(profile.xpForNextLevel())).orElse("0");
            case "locale" -> profile(player).map(PlayerProfile::getLocale).orElse("");
            case "nick" -> nickName(player);
            case "realname" -> realName(player);
            case "nicked" -> String.valueOf(player != null && nicks != null && nicks.isNicked(player));
            case "network_online" -> String.valueOf(prismarin.serverStatus().statuses().stream()
                    .filter(ServerStatus::online).mapToInt(ServerStatus::players).sum());
            case "network_servers_online" -> String.valueOf(prismarin.serverStatus().statuses().stream()
                    .filter(ServerStatus::online).count());
            default -> resolvePrefixed(player, key);
        };
    }

    private String resolvePrefixed(UUID player, String key) {
        if (key.startsWith("balance_")) {
            return economy().currency(key.substring("balance_".length()))
                    .map(currency -> balance(player, currency))
                    .orElse(null);
        }
        if (key.startsWith("server_online_")) {
            return status(key.substring("server_online_".length()))
                    .map(status -> String.valueOf(status.players())).orElse(null);
        }
        if (key.startsWith("server_max_")) {
            return status(key.substring("server_max_".length()))
                    .map(status -> String.valueOf(status.maxPlayers())).orElse(null);
        }
        if (key.startsWith("server_status_")) {
            return status(key.substring("server_status_".length()))
                    .map(status -> status.online() ? "online" : "offline").orElse(null);
        }
        if (key.startsWith("server_state_")) {
            return status(key.substring("server_state_".length()))
                    .map(status -> !status.online() ? "offline"
                            : status.full() && status.state().joinable() ? "full"
                            : status.state().name().toLowerCase(Locale.ROOT))
                    .orElse(null);
        }
        if (key.startsWith("group_joinable_")) {
            String group = key.substring("group_joinable_".length());
            return String.valueOf(prismarin.servers().byGroup(group).stream()
                    .map(NetworkServer::name)
                    .map(this::status)
                    .flatMap(Optional::stream)
                    .filter(ServerStatus::joinable)
                    .count());
        }
        if (key.startsWith("server_ping_")) {
            return status(key.substring("server_ping_".length()))
                    .map(status -> String.valueOf(Math.max(0L, status.latencyMillis()))).orElse(null);
        }
        if (key.startsWith("group_online_")) {
            String group = key.substring("group_online_".length());
            return String.valueOf(prismarin.servers().byGroup(group).stream()
                    .map(NetworkServer::name)
                    .map(this::status)
                    .flatMap(Optional::stream)
                    .filter(ServerStatus::online)
                    .mapToInt(ServerStatus::players)
                    .sum());
        }
        return null;
    }

    private String nickName(UUID player) {
        if (player == null) {
            return "";
        }
        Optional<String> nick = nicks == null ? Optional.empty() : nicks.shownNick(player);
        return nick.orElseGet(() -> realName(player));
    }

    private String realName(UUID player) {
        if (player == null) {
            return "";
        }
        Optional<String> real = nicks == null ? Optional.empty() : nicks.realName(player);
        return real.or(() -> profile(player).map(PlayerProfile::getName)).orElse("");
    }

    private EconomyService economy() {
        return prismarin.economy();
    }

    private Optional<Currency> primary() {
        return economy().enabled() ? Optional.of(economy().primaryCurrency()) : Optional.empty();
    }

    private String balance(UUID player, Currency currency) {
        if (currency == null || !economy().enabled()) {
            return "0";
        }
        return profile(player).map(profile -> String.valueOf(profile.balance(currency))).orElse("0");
    }

    private Optional<PlayerProfile> profile(UUID player) {
        return player == null ? Optional.empty() : prismarin.profiles().getCached(player);
    }

    private Optional<ServerStatus> status(String name) {
        return prismarin.serverStatus().status(name);
    }
}
