package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

@Singleton
public final class HatCommand implements TabExecutor {

    private final EssentialsSupport support;

    @Inject
    public HatCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Optional<Player> self = support.self(sender);
        if (self.isEmpty() || !support.allowed(sender, EssentialsFeature.HAT.permission())) {
            return true;
        }
        PlayerInventory inventory = self.get().getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        if (hand.getType().isAir()) {
            support.send(sender, "essentials.hat.empty");
            return true;
        }
        ItemStack previous = inventory.getHelmet();
        Component name = hand.effectiveName();
        inventory.setHelmet(hand);
        inventory.setItemInMainHand(previous);
        support.send(sender, "essentials.hat.done", Placeholder.component("item", name));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
