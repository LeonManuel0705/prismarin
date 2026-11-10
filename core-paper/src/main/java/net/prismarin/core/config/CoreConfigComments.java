package net.prismarin.core.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.prismarin.shared.config.ConfigComments;

public final class CoreConfigComments {

    private static final Map<String, List<String>> PAPER = Map.ofEntries(
            Map.entry("menu", List.of("Sound played when a player clicks a button in a PRISMARIN menu.")),
            Map.entry("game-state", List.of("Tells lobbies whether this game server is waiting, running or full.",
                    "Game plugins can set it through the API, otherwise it is read from a placeholder.")),
            Map.entry("game-state.placeholder", List.of("PlaceholderAPI placeholder of your game plugin that shows the",
                    "state, for example %bedwars_state%. Leave empty when a plugin sets the state through the API.")),
            Map.entry("game-state.write-motd", List.of("Puts the state into the server list MOTD so lobbies see it",
                    "without Redis.")),
            Map.entry("game-state.mapping", List.of("Words in the placeholder text that mean each state, compared",
                    "without case.")),
            Map.entry("vanish", List.of("Vanish hides staff completely, like SuperVanish, instead of an invisibility effect.")),
            Map.entry("vanish.silent-containers", List.of("Open chests and other containers without animation or sound.")),
            Map.entry("vanish.silent-blocks", List.of("Break blocks without sound and particles for other players.")),
            Map.entry("vanish.flight", List.of("Allow flying while vanished. It is taken away again on unvanish.")),
            Map.entry("vanish.invincible", List.of("No damage while vanished, except the void and /kill.")),
            Map.entry("vanish.no-hunger", List.of("Food does not drop while vanished.")),
            Map.entry("essentials.pingtest-seconds", List.of("How long /pingtest measures. Minecraft reports a new ping",
                    "about every 15 seconds, so 30 seconds or more give several real readings.")),
            Map.entry("vanish.inspect-inventories", List.of("Right click a player while vanished to watch his inventory,",
                    "armour and off hand live. Needs prismarin.vanish.inspect.")),
            Map.entry("staff-mode", List.of("/mod (or /staff) turns the staff mode on and off. It stays on across servers.")),
            Map.entry("staff-mode.nick", List.of("Staff are nicked while in staff mode and get nicked again after every",
                    "server switch, even when they unnick in between.")),
            Map.entry("staff-mode.vanish", List.of("Staff are vanished while in staff mode. Both options can be on.")),
            Map.entry("vanish.mobs-ignore", List.of("Mobs neither attack nor look at vanished players.")),
            Map.entry("essentials", List.of("Basic admin commands of the essentials module. The whole module steps aside",
                    "when EssentialsX or CMI is installed. Every command has its permission prismarin.essentials.<name>,",
                    "acting on another player needs .others. Vanished players are never listed or targeted",
                    "by players who cannot see them.")),
            Map.entry("essentials.commands", List.of("false removes a single command.")),
            Map.entry("essentials.commands.tpall", List.of("/tpall brings every visible, not vanished player to you.")),
            Map.entry("essentials.commands.tphere", List.of("/tphere <player>")),
            Map.entry("essentials.commands.heal", List.of("/heal [player]: health, hunger, fire and harmful effects.")),
            Map.entry("essentials.commands.feed", List.of("/feed [player]")),
            Map.entry("essentials.commands.fly", List.of("/fly [player] toggles flying. Flight given by vanish stays",
                    "after unvanish once it was toggled with /fly.")),
            Map.entry("essentials.commands.gamemode", List.of("/gm <0-3|s|c|a|sp> [player] plus /gms, /gmc, /gma, /gmsp.")),
            Map.entry("essentials.commands.speed", List.of("/speed <1-10|reset> [player], the fly speed while flying.")),
            Map.entry("essentials.commands.clearinventory", List.of("/clearinventory [player], also /ci. The vanilla",
                    "/clear stays untouched for command blocks and data packs.")),
            Map.entry("essentials.commands.invsee", List.of("/invsee <player>. Changing items needs",
                    "prismarin.essentials.invsee.modify, otherwise it is view only.")),
            Map.entry("essentials.commands.enderchest", List.of("/enderchest [player], also /ec. Other players need",
                    ".others, changing their items .modify.")),
            Map.entry("essentials.commands.time", List.of("/day and /night [world]")),
            Map.entry("essentials.commands.weather", List.of("/sun, /rain and /thunder [world]")),
            Map.entry("essentials.commands.ping", List.of("/ping [player], allowed for everyone by default.")),
            Map.entry("essentials.commands.hat", List.of("/hat puts the item in your hand on your head.")),
            Map.entry("essentials.commands.skull", List.of("/skull [name] gives you a player head.")));

    private CoreConfigComments() {
        throw new AssertionError();
    }

    public static void apply(Path file) {
        ConfigComments.apply(file, PAPER);
    }
}
