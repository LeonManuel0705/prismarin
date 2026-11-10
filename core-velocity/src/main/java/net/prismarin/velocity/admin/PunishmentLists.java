package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.velocity.social.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PunishmentLists {

    static final int PAGE_SIZE = 8;

    private static final Logger LOGGER = LoggerFactory.getLogger("PrismarinCore");
    private static final String KEY = "punishment.list.";

    private final AdminContext context;
    private final PunishmentFlow flow;
    private final NickDirectory nicks;

    PunishmentLists(AdminContext context, PunishmentFlow flow) {
        this.context = context;
        this.flow = flow;
        this.nicks = new NickDirectory(context.services().documents());
    }

    SimpleCommand command(PunishmentType type) {
        String name = name(type);
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!allowed(source, type)) {
                    context.send(source, "general.no-permission");
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length > 1) {
                    context.send(source, "punishment.usage." + name + "list");
                    return;
                }
                show(source, type, args.length == 1 ? PunishmentCommands.page(args[0]) : 1);
            }
        };
    }

    static boolean allowed(CommandSource source, PunishmentType type) {
        String name = name(type);
        return source.hasPermission("prismarin.staff." + name + "list") || source.hasPermission("prismarin.staff." + name)
                || source.hasPermission("prismarin.staff.history") || source.hasPermission("prismarin." + name + "list");
    }

    static Page<Punishment> page(List<Punishment> active, int page) {
        return Page.of(active, Math.max(1, page) - 1, PAGE_SIZE);
    }

    static String publicName(String realName, Optional<String> nick) {
        return nick.filter(value -> !value.isBlank()).orElse(realName);
    }

    void show(CommandSource source, PunishmentType type, int page) {
        context.services().punishments().inForce(type).thenAccept(active -> {
            String name = name(type);
            Page<Punishment> shown = page(active, page);
            context.send(source, KEY + name + "-header", Placeholder.unparsed("count", String.valueOf(shown.total())),
                    Placeholder.unparsed("page", String.valueOf(shown.page() + 1)),
                    Placeholder.unparsed("pages", String.valueOf(shown.pages())));
            if (shown.entries().isEmpty()) {
                context.send(source, KEY + name + "-empty");
                return;
            }
            boolean staff = source.hasPermission(StaffCommands.STAFF);
            for (Punishment punishment : shown.entries()) {
                source.sendMessage(entry(source, punishment, type, staff));
            }
            if (shown.pages() > 1) {
                source.sendMessage(pager(source, name, shown.page() + 1, shown.pages()));
            }
        }).exceptionally(error -> {
            LOGGER.warn("Listing punishments failed", error);
            context.send(source, "punishment.failed");
            return null;
        });
    }

    private Component entry(CommandSource source, Punishment punishment, PunishmentType type, boolean staff) {
        Map<String, TagResolver> placeholders = flow.placeholders(source, punishment, !staff);
        Optional<String> nick = nicks.shownNick(punishment.target())
                .filter(value -> !value.equalsIgnoreCase(punishment.targetName()));
        Component player = staff && nick.isPresent()
                ? context.messages().render(source, "punishment.staff.nicked",
                        Placeholder.unparsed("player", punishment.targetName()),
                        Placeholder.unparsed("nick", nick.get()))
                : Component.text(staff ? punishment.targetName() : publicName(punishment.targetName(), nick));
        placeholders.put("player", Placeholder.component("player", player));
        TagResolver[] resolvers = PunishmentFlow.array(placeholders);
        Component hover = context.messages().render(source, KEY + "hover", resolvers);
        Component line = context.messages().render(source, KEY + "entry", resolvers);
        if (staff && (source.hasPermission("prismarin.staff.check") || source.hasPermission("prismarin.staff.history"))) {
            hover = hover.append(context.messages().render(source, KEY + "hover-check"));
            line = line.clickEvent(ClickEvent.runCommand("/check " + punishment.targetName()));
        }
        line = line.hoverEvent(HoverEvent.showText(hover));
        String revoke = type == PunishmentType.BAN ? "unban" : "unmute";
        if (staff && (source.hasPermission("prismarin.staff." + revoke)
                || source.hasPermission("prismarin.staff." + name(type)))) {
            line = line.append(Component.text(" ")).append(context.messages().render(source, KEY + revoke)
                    .clickEvent(ClickEvent.suggestCommand("/" + revoke + " " + punishment.targetName() + " "))
                    .hoverEvent(HoverEvent.showText(context.messages().render(source, KEY + revoke + "-hover",
                            resolvers))));
        }
        return line;
    }

    private Component pager(CommandSource source, String name, int page, int pages) {
        Component line = Component.text(" ");
        if (page > 1) {
            line = line.append(context.messages().render(source, "punishment.history.previous")
                    .clickEvent(ClickEvent.runCommand("/" + name + "list " + (page - 1))));
            line = line.append(Component.text(" "));
        }
        if (page < pages) {
            line = line.append(context.messages().render(source, "punishment.history.next")
                    .clickEvent(ClickEvent.runCommand("/" + name + "list " + (page + 1))));
        }
        return line;
    }

    private static String name(PunishmentType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
