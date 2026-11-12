package net.prismarin.core.joinme;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.ViewerRules;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.joinme.JoinMePlaceholders;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Singleton
public final class JoinMeView {

    public static final String CLICK = "/joinme join ";

    private final LocaleService locale;
    private final ProfileService profiles;
    private final ServerDirectory directory;
    private final CoreConfig config;

    @Inject
    public JoinMeView(LocaleService locale, ProfileService profiles, ServerDirectory directory, CoreConfig config) {
        this.locale = locale;
        this.profiles = profiles;
        this.directory = directory;
        this.config = config;
    }

    public Locale language(CommandSender sender) {
        return sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
    }

    public Component render(CommandSender sender, String key, TagResolver... resolvers) {
        return locale.render(key, language(sender), resolvers);
    }

    public TagResolver resolver(CommandSender viewer, JoinMe joinMe) {
        boolean revealed = !(viewer instanceof Player player) || ViewerRules.revealsRealName(player.getUniqueId(),
                joinMe.creator(), player.hasPermission(PlayerService.REAL_NAME_PERMISSION));
        Map<String, String> values = JoinMePlaceholders.values(joinMe, revealed, System.currentTimeMillis());
        Map<String, Component> rich = new HashMap<>();
        rich.put("group", locale.format(directory.group(joinMe.group()).map(ServerGroup::displayName)
                .orElseGet(() -> joinMe.group().isEmpty() ? "?" : joinMe.group().substring(0, 1)
                        .toUpperCase(Locale.ROOT) + joinMe.group().substring(1))));
        rich.put("server_display", locale.format(directory.byName(joinMe.server()).map(NetworkServer::displayName)
                .orElse(joinMe.server())));
        rich.put("state", render(viewer, "joinme.state." + joinMe.state().name().toLowerCase(Locale.ROOT)));
        return JoinMePlaceholders.resolver(values, rich);
    }

    public Component entry(CommandSender viewer, JoinMe joinMe) {
        TagResolver resolver = resolver(viewer, joinMe);
        return render(viewer, "joinme.list-entry", resolver)
                .clickEvent(ClickEvent.runCommand(CLICK + joinMe.id()))
                .hoverEvent(HoverEvent.showText(render(viewer, "joinme.list-hover", resolver)));
    }
}
