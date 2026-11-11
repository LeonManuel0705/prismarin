package net.prismarin.example;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import net.prismarin.api.Prismarin;
import net.prismarin.api.player.PlayerService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

final class ExampleCommand implements TabExecutor {

    private static final String STAFF = "prismarin.example.staff";
    private static final List<String> EVERYONE = List.of("whois", "win", "top", "report");
    private static final List<String> STAFF_ONLY = List.of("reward", "punish", "alert", "state", "maintenance");

    private final Prismarin prismarin;
    private final PlayerLookup lookup;
    private final StatsStore stats;
    private final Rewards rewards;
    private final Moderation moderation;
    private final RoundState round;
    private final Texts texts;

    ExampleCommand(Prismarin prismarin, PlayerLookup lookup, StatsStore stats, Rewards rewards, Moderation moderation,
                   RoundState round, Texts texts) {
        this.prismarin = prismarin;
        this.lookup = lookup;
        this.stats = stats;
        this.rewards = rewards;
        this.moderation = moderation;
        this.round = round;
        this.texts = texts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (STAFF_ONLY.contains(action) && !sender.hasPermission(STAFF)) {
            texts.send(sender, "no-permission");
            return true;
        }
        switch (action) {
            case "whois" -> withTarget(sender, args, () -> lookup.whois(sender, args[1]));
            case "win" -> win(sender);
            case "top" -> top(sender);
            case "report" -> withTarget(sender, args, () -> report(sender, args[1]));
            case "reward" -> reward(sender, args);
            case "punish" -> punish(sender, args);
            case "alert" -> alert(sender, args);
            case "state" -> state(sender, args);
            case "maintenance" -> maintenance(sender);
            default -> texts.send(sender, "usage");
        }
        return true;
    }

    private void win(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            texts.send(sender, "players-only");
            return;
        }
        stats.recordWin(player.getUniqueId(), player.getName()).whenComplete((updated, error) -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            texts.send(sender, "win", "wins", String.valueOf(updated.wins()), "games",
                    String.valueOf(updated.games()));
        });
    }

    private void top(CommandSender sender) {
        stats.top(5).whenComplete((best, error) -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            texts.send(sender, "top-header");
            for (int index = 0; index < best.size(); index++) {
                texts.send(sender, "top-entry", "place", String.valueOf(index + 1), "name", best.get(index).name(),
                        "wins", String.valueOf(best.get(index).wins()));
            }
        });
    }

    private void report(CommandSender sender, String typed) {
        lookup.resolve(sender, typed).thenCompose(found -> found
                        .map(target -> moderation.report(PlayerLookup.viewerId(sender), sender.getName(), target.uuid()))
                        .orElseThrow(() -> new IllegalArgumentException(typed + " is unknown")))
                .whenComplete((submission, error) -> {
                    if (error != null) {
                        failed(sender, error);
                        return;
                    }
                    texts.send(sender, "reported", "id", submission.report().id(), "outcome",
                            submission.outcome().name().toLowerCase(Locale.ROOT));
                });
    }

    private void reward(CommandSender sender, String[] args) {
        if (args.length != 3) {
            texts.send(sender, "usage");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            texts.send(sender, "usage");
            return;
        }
        lookup.resolve(sender, args[1]).thenCompose(found -> found
                        .map(target -> rewards.pay(target.uuid(), amount).thenApply(result -> {
                            if (!result.success()) {
                                throw new IllegalStateException(result.failureReason().name());
                            }
                            texts.send(sender, "reward", "name", target.shownName(), "amount", String.valueOf(amount),
                                    "currency", rewards.currency().id(), "balance",
                                    String.valueOf(result.newBalance()));
                            return result;
                        }))
                        .orElseThrow(() -> new IllegalArgumentException(args[1] + " is unknown")))
                .exceptionally(error -> {
                    failed(sender, error);
                    return null;
                });
    }

    private void punish(CommandSender sender, String[] args) {
        if (args.length != 3) {
            texts.send(sender, "usage");
            return;
        }
        lookup.resolve(sender, args[1]).thenCompose(found -> found
                        .map(target -> moderation.punish(target.uuid(), args[2], sender.getName())
                                .thenApply(result -> {
                                    texts.send(sender, "punished", "name", target.shownName(),
                                            "type", result.punishment().type().name().toLowerCase(Locale.ROOT),
                                            "template", result.preview().template(),
                                            "level", String.valueOf(result.preview().level()),
                                            "levels", String.valueOf(result.preview().levels()));
                                    return result;
                                }))
                        .orElseThrow(() -> new IllegalArgumentException(args[1] + " is unknown")))
                .exceptionally(error -> {
                    failed(sender, error);
                    return null;
                });
    }

    private void alert(CommandSender sender, String[] args) {
        if (args.length < 3) {
            texts.send(sender, "usage");
            return;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        moderation.alert(sender.getName(), args[1], reason).whenComplete((delivery, error) -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            texts.send(sender, "alert", "delivery", delivery.name().toLowerCase(Locale.ROOT));
        });
    }

    private void state(CommandSender sender, String[] args) {
        if (args.length != 2) {
            texts.send(sender, "usage");
            return;
        }
        round.set(args[1]).ifPresentOrElse(
                state -> texts.send(sender, "state", "state", state.name().toLowerCase(Locale.ROOT)),
                () -> texts.send(sender, "usage"));
    }

    private void maintenance(CommandSender sender) {
        prismarin.maintenance().ifPresentOrElse(service -> service.state().whenComplete((state, error) -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            texts.send(sender, "maintenance", "network", String.valueOf(state.network()),
                    "servers", String.join(", ", state.servers()), "groups", String.join(", ", state.groups()));
        }), () -> texts.send(sender, "failed", "reason", "maintenance is off"));
    }

    private void withTarget(CommandSender sender, String[] args, Runnable action) {
        if (args.length != 2) {
            texts.send(sender, "usage");
            return;
        }
        action.run();
    }

    private void failed(CommandSender sender, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        texts.send(sender, "failed", "reason", String.valueOf(cause.getMessage()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            Stream<String> options = sender.hasPermission(STAFF)
                    ? Stream.concat(EVERYONE.stream(), STAFF_ONLY.stream()) : EVERYONE.stream();
            return options.filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("state")) {
            PlayerService players = prismarin.players();
            return players.suggestions(PlayerLookup.viewerId(sender), args[1]);
        }
        return List.of();
    }
}
