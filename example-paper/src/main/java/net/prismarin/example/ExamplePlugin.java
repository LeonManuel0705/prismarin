package net.prismarin.example;

import java.util.Objects;
import net.prismarin.api.Prismarin;
import net.prismarin.api.PrismarinProvider;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {

    private EventLog events;
    private StatsDisplay display;
    private RoundState round;
    private NetworkStats network;
    private Queueing queueing;
    private ArenaJoinMe arena;
    private ArenaStats arenaStats;
    private Cosmetics cosmetics;

    @Override
    public void onEnable() {
        Prismarin prismarin = PrismarinProvider.get();
        Texts texts = new Texts(getClassLoader(), prismarin.players());
        StatsStore stats = new StatsStore(prismarin.storage());
        getServer().getOnlinePlayers().forEach(player -> stats.load(player.getUniqueId(), player.getName()));
        events = new EventLog(this, stats, texts);
        events.subscribe(prismarin.events());
        display = new StatsDisplay(stats);
        display.register(prismarin.players());
        round = new RoundState(prismarin.gameState());
        round.open();
        network = new NetworkStats(prismarin);
        network.define();
        arenaStats = new ArenaStats(stats);
        arenaStats.register(prismarin);
        queueing = new Queueing(prismarin);
        queueing.subscribe(getLogger());
        arena = new ArenaJoinMe(this, prismarin, texts);
        arena.register();
        cosmetics = new Cosmetics(prismarin, getLogger());
        cosmetics.register();
        ExampleCommand command = new ExampleCommand(prismarin, new PlayerLookup(prismarin.players(), stats, texts),
                stats, new Rewards(prismarin), new Moderation(prismarin), round, queueing, texts, network, arena,
                cosmetics);
        PluginCommand example = Objects.requireNonNull(getCommand("example"), "example command");
        example.setExecutor(command);
        example.setTabCompleter(command);
        getLogger().info("Running on " + prismarin.serverId() + ", nicks "
                + (prismarin.nicks().isPresent() ? "on" : "off") + ", reports "
                + (prismarin.reports().isPresent() ? "on" : "off") + ", stats "
                + (prismarin.stats().isPresent() ? "on" : "off") + ", cosmetics "
                + (prismarin.cosmetics().isPresent() ? "on" : "off"));
    }

    @Override
    public void onDisable() {
        if (events != null) {
            events.unsubscribe();
        }
        if (display != null) {
            display.unregister();
        }
        if (round != null) {
            round.close();
        }
        if (network != null) {
            network.undefine();
        }
        if (arenaStats != null) {
            arenaStats.unregister();
        }
        if (queueing != null) {
            queueing.unsubscribe();
        }
        if (arena != null) {
            arena.unregister();
        }
        if (cosmetics != null) {
            cosmetics.unregister();
        }
    }
}
