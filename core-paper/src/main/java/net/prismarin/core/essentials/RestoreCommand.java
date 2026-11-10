package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

@Singleton
public final class RestoreCommand implements TabExecutor {

    private static final int FULL_FOOD = 20;

    private final EssentialsSupport support;

    @Inject
    public RestoreCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EssentialsFeature feature = command.getName().equalsIgnoreCase("heal")
                ? EssentialsFeature.HEAL : EssentialsFeature.FEED;
        if (!support.allowed(sender, feature.permission())) {
            return true;
        }
        Optional<Player> target = support.target(sender, args, 0, feature.permission("others"));
        if (target.isEmpty()) {
            return true;
        }
        Player player = target.get();
        String key = "essentials." + feature.key();
        if (player.isDead()) {
            support.send(sender, "essentials.heal.dead", Placeholder.unparsed("player", player.getName()));
            return true;
        }
        if (feature == EssentialsFeature.HEAL) {
            AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
            player.setHealth(health == null ? 20.0 : health.getValue());
            player.setFireTicks(0);
            player.setFreezeTicks(0);
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                    player.removePotionEffect(effect.getType());
                }
            }
        }
        player.setFoodLevel(FULL_FOOD);
        player.setSaturation(FULL_FOOD);
        player.setExhaustion(0.0F);
        support.send(player, key + ".self");
        if (!EssentialsSupport.same(sender, player)) {
            support.send(sender, key + ".other", Placeholder.unparsed("player", player.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        EssentialsFeature feature = command.getName().equalsIgnoreCase("heal")
                ? EssentialsFeature.HEAL : EssentialsFeature.FEED;
        if (args.length != 1 || !sender.hasPermission(feature.permission("others"))) {
            return List.of();
        }
        return support.names(sender, args[0]);
    }
}
