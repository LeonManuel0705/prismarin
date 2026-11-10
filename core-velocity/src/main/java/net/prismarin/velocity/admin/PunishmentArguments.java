package net.prismarin.velocity.admin;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.moderation.Durations;

public final class PunishmentArguments {

    public enum Command {
        BAN(PunishmentType.BAN, false, true),
        TEMPBAN(PunishmentType.BAN, true, true),
        IPBAN(PunishmentType.BAN, false, false),
        MUTE(PunishmentType.MUTE, false, true),
        TEMPMUTE(PunishmentType.MUTE, true, true),
        KICK(PunishmentType.KICK, false, true),
        WARN(PunishmentType.WARN, false, true),
        PUNISH(null, false, true);

        private final PunishmentType type;
        private final boolean needsDuration;
        private final boolean templates;

        Command(PunishmentType type, boolean needsDuration, boolean templates) {
            this.type = type;
            this.needsDuration = needsDuration;
            this.templates = templates;
        }

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }

        public PunishmentType type() {
            return type;
        }
    }

    public sealed interface Parsed permits Usage, BadDuration, UnknownTemplate, Menu, Template, Manual {
    }

    public record Usage() implements Parsed {
    }

    public record BadDuration(String text) implements Parsed {
    }

    public record UnknownTemplate(String target, String id) implements Parsed {
    }

    public record Menu(String target) implements Parsed {
    }

    public record Template(String target, String id, String note) implements Parsed {
    }

    public record Manual(String target, PunishmentType type, Duration duration, String reason, boolean address)
            implements Parsed {

        public PunishmentKind kind() {
            return address ? PunishmentKind.IPBAN : PunishmentKind.of(type, duration == null, false);
        }
    }

    private PunishmentArguments() {
        throw new AssertionError();
    }

    public static Parsed parse(Command command, String[] args, Predicate<String> isTemplate) {
        if (args.length == 0 || args[0].isBlank()) {
            return new Usage();
        }
        String target = args[0];
        if (command == Command.PUNISH) {
            if (args.length == 1) {
                return new Menu(target);
            }
            return isTemplate.test(args[1]) ? template(target, args) : new UnknownTemplate(target, args[1]);
        }
        if (args.length == 1) {
            return command == Command.KICK ? new Manual(target, PunishmentType.KICK, null, "", false) : new Usage();
        }
        if (command.templates && isTemplate.test(args[1])) {
            return template(target, args);
        }
        if (!command.type.lasting()) {
            String reason = rest(args, 1);
            return reason.isEmpty() && command != Command.KICK ? new Usage()
                    : new Manual(target, command.type, null, reason, false);
        }
        Optional<Duration> duration = Durations.parse(args[1]);
        if (command.needsDuration && duration.isEmpty()) {
            return new BadDuration(args[1]);
        }
        if (duration.isEmpty()) {
            String reason = rest(args, 1);
            return reason.isEmpty() ? new Usage()
                    : new Manual(target, command.type, null, reason, command == Command.IPBAN);
        }
        String reason = rest(args, 2);
        if (reason.isEmpty()) {
            return new Usage();
        }
        return new Manual(target, command.type, duration.get(), reason, command == Command.IPBAN);
    }

    private static Template template(String target, String[] args) {
        return new Template(target, args[1].toLowerCase(Locale.ROOT), rest(args, 2));
    }

    static String rest(String[] args, int from) {
        if (from >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }
}
