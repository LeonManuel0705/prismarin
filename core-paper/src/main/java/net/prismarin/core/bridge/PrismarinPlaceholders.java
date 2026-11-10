package net.prismarin.core.bridge;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.Prismarin;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.RankDisplay;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerStatus;

public final class PrismarinPlaceholders {

    private static final String DATA_PREFIX = "data_";
    private static final String RANK_PREFIX = "rank_";
    private static final String REAL_RANK_PREFIX = "realrank_";

    private final Prismarin prismarin;
    private final NickService nicks;
    private final PlayerService players;
    private final Function<Component, String> text;

    public PrismarinPlaceholders(Prismarin prismarin) {
        this(prismarin, null);
    }

    public PrismarinPlaceholders(Prismarin prismarin, NickService nicks) {
        this(prismarin, nicks, null, PlainTextComponentSerializer.plainText()::serialize);
    }

    public PrismarinPlaceholders(Prismarin prismarin, NickService nicks, PlayerService players,
                                 Function<Component, String> text) {
        this.prismarin = prismarin;
        this.nicks = nicks;
        this.players = players;
        this.text = text;
    }

    public String resolveRelational(UUID viewer, boolean viewerSeesRealNames, UUID target, String params) {
        if (target == null) {
            return null;
        }
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "nick" -> {
                return nickName(target);
            }
            case "realname" -> {
                return viewerSeesRealNames || target.equals(viewer) ? realName(target) : nickName(target);
            }
            default -> {
            }
        }
        if (players == null) {
            return null;
        }
        Optional<NetworkPlayer> snapshot = players.online(target);
        if (key.startsWith(RANK_PREFIX)) {
            boolean revealed = viewerSeesRealNames || target.equals(viewer);
            return rankPart(key.substring(RANK_PREFIX.length()),
                    snapshot.map(found -> revealed ? found.realRank() : found.rank()).orElse(RankDisplay.NONE));
        }
        if (key.startsWith(DATA_PREFIX)) {
            return data(key.substring(DATA_PREFIX.length()), viewer, snapshot);
        }
        return switch (key) {
            case "visible" -> String.valueOf(snapshot.map(found -> players.canSee(viewer, found)).orElse(false));
            case "world" -> snapshot.filter(found -> players.canSee(viewer, found)).map(NetworkPlayer::world)
                    .orElse("");
            default -> null;
        };
    }

    public String resolve(UUID player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "brand" -> prismarin.branding().name();
            case "server" -> prismarin.serverId();
            case "server_name" -> prismarin.servers().byName(prismarin.serverId()).map(NetworkServer::displayName)
                    .orElse(prismarin.serverId());
            case "balance" -> balance(player, primary().orElse(null));
            case "level" -> profile(player).map(profile -> String.valueOf(profile.getLevel())).orElse("0");
            case "xp" -> profile(player).map(profile -> String.valueOf(profile.getXp())).orElse("0");
            case "xp_required" -> profile(player).map(profile -> String.valueOf(profile.xpForNextLevel())).orElse("0");
            case "locale" -> locale(player).map(Locale::toLanguageTag).orElse("");
            case "language" -> locale(player).map(PrismarinPlaceholders::languageName).orElse("");
            case "nick" -> nickName(player);
            case "realname" -> realName(player);
            case "nicked" -> String.valueOf(player != null && nicks != null && nicks.isNicked(player));
            case "world" -> snapshot(player).map(NetworkPlayer::world).orElse("");
            case "vanished" -> String.valueOf(snapshot(player).map(NetworkPlayer::vanished).orElse(false));
            case "network_online" -> String.valueOf(prismarin.serverStatus().statuses().stream()
                    .filter(ServerStatus::online).mapToInt(ServerStatus::players).sum());
            case "network_servers_online" -> String.valueOf(prismarin.serverStatus().statuses().stream()
                    .filter(ServerStatus::online).count());
            default -> resolvePrefixed(player, key);
        };
    }

    private String resolvePrefixed(UUID player, String key) {
        if (key.startsWith(RANK_PREFIX) && players != null) {
            return rankPart(key.substring(RANK_PREFIX.length()),
                    snapshot(player).map(NetworkPlayer::rank).orElse(RankDisplay.NONE));
        }
        if (key.startsWith(REAL_RANK_PREFIX) && players != null) {
            return rankPart(key.substring(REAL_RANK_PREFIX.length()),
                    snapshot(player).map(NetworkPlayer::realRank).orElse(RankDisplay.NONE));
        }
        if (key.startsWith(DATA_PREFIX) && players != null) {
            return data(key.substring(DATA_PREFIX.length()), null, snapshot(player));
        }
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

    private String rankPart(String part, RankDisplay rank) {
        return switch (part) {
            case "label" -> rank.label();
            case "prefix" -> text.apply(rank.prefix());
            case "suffix" -> text.apply(rank.suffix());
            case "color" -> rank.known() ? LegacyCodes.color(rank.nameColor()) : "";
            case "weight" -> String.valueOf(rank.weight());
            default -> null;
        };
    }

    private String data(String attribute, UUID viewer, Optional<NetworkPlayer> target) {
        if (players.attributes().get(attribute).isEmpty()) {
            return null;
        }
        return target.flatMap(found -> players.attributes().value(attribute, viewer, found)).orElse("");
    }

    private Optional<NetworkPlayer> snapshot(UUID player) {
        return player == null || players == null ? Optional.empty() : players.online(player);
    }

    private Optional<Locale> locale(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        if (players != null) {
            return Optional.of(players.locale(player));
        }
        return profile(player).map(PlayerProfile::getLocale).map(Locale::forLanguageTag);
    }

    static String languageName(Locale locale) {
        String name = locale.getDisplayLanguage(locale);
        if (name.isEmpty()) {
            return locale.toLanguageTag();
        }
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    private String nickName(UUID player) {
        if (player == null) {
            return "";
        }
        Optional<NetworkPlayer> here = snapshot(player);
        if (here.isPresent()) {
            return here.get().shownName();
        }
        Optional<String> nick = nicks == null ? Optional.empty() : nicks.shownNick(player);
        return nick.orElseGet(() -> realName(player));
    }

    private String realName(UUID player) {
        if (player == null) {
            return "";
        }
        Optional<NetworkPlayer> here = snapshot(player);
        if (here.isPresent()) {
            return here.get().realName();
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
