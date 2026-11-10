package net.prismarin.core.essentials;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

@Singleton
public final class SkullCommand implements TabExecutor {

    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final EssentialsSupport support;

    @Inject
    public SkullCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Optional<Player> self = support.self(sender);
        if (self.isEmpty() || !support.allowed(sender, EssentialsFeature.SKULL.permission())) {
            return true;
        }
        Player player = self.get();
        if (args.length > 1) {
            support.send(player, "essentials.skull.usage");
            return true;
        }
        if (args.length == 0) {
            give(player, player.getPlayerProfile(), player.getName());
            return true;
        }
        String name = args[0];
        if (!NAME.matcher(name).matches()) {
            support.send(player, "essentials.skull.invalid", Placeholder.unparsed("player", name));
            return true;
        }
        Optional<Player> online = support.find(player, name).filter(found -> found.getName().equalsIgnoreCase(name));
        if (online.isPresent()) {
            give(player, online.get().getPlayerProfile(), online.get().getName());
            return true;
        }
        support.send(player, "essentials.skull.loading", Placeholder.unparsed("player", name));
        PlayerProfile profile = support.plugin().getServer().createProfile(name);
        support.plugin().getServer().getScheduler().runTaskAsynchronously(support.plugin(), () -> {
            boolean found = complete(profile);
            support.plugin().getServer().getScheduler().runTask(support.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (found) {
                    give(player, profile, profile.getName() == null ? name : profile.getName());
                } else {
                    support.send(player, "essentials.skull.unknown", Placeholder.unparsed("player", name));
                }
            });
        });
        return true;
    }

    private static boolean complete(PlayerProfile profile) {
        try {
            return profile.complete(true) && profile.getId() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void give(Player player, PlayerProfile profile, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        }
        player.getInventory().addItem(head).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        support.send(player, "essentials.skull.given", Placeholder.unparsed("player", name));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? support.names(sender, args[0]) : List.of();
    }
}
