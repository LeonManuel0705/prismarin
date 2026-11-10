package net.prismarin.core.nick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

public final class NickNames {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 16;

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{" + MIN_LENGTH + "," + MAX_LENGTH + "}");
    private static final int PATTERNS = 6;

    private static final List<String> ADJECTIVES = List.of(
            "Swift", "Silent", "Lucky", "Frosty", "Mellow", "Rusty", "Sunny", "Clever", "Brave", "Quiet",
            "Wild", "Sneaky", "Fuzzy", "Cosmic", "Crispy", "Lazy", "Jolly", "Rapid", "Misty", "Golden",
            "Velvet", "Salty", "Sleepy", "Bold", "Witty", "Noble", "Rowdy", "Dusty", "Mighty", "Shy",
            "Happy", "Grumpy", "Icy", "Stormy", "Cozy", "Fancy", "Spicy", "Tiny", "Lunar", "Solar");

    private static final List<String> NOUNS = List.of(
            "Fox", "Otter", "Panda", "Falcon", "Tiger", "Wolf", "Badger", "Raven", "Koala", "Lynx",
            "Gecko", "Moose", "Turtle", "Comet", "Cactus", "Muffin", "Pickle", "Biscuit", "Waffle", "Pebble",
            "Maple", "Cobalt", "Ember", "Walrus", "Penguin", "Ferret", "Hawk", "Bison", "Cricket", "Pigeon",
            "Noodle", "Taco", "Pretzel", "Llama", "Squid", "Beetle", "Hedgehog", "Sparrow", "Yeti", "Golem");

    private final List<String> preferred;
    private final boolean generator;
    private final List<String> blocked;

    public NickNames(List<String> preferred, boolean generator, List<String> blocked) {
        this.preferred = List.copyOf(preferred);
        this.generator = generator;
        this.blocked = blocked.stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .filter(word -> !word.isBlank())
                .toList();
    }

    public static boolean validFormat(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public boolean allowed(String name) {
        if (!validFormat(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return blocked.stream().noneMatch(lower::contains);
    }

    public Optional<String> choose(Predicate<String> free, RandomGenerator random, int generatedAttempts) {
        for (String candidate : candidates(random, generatedAttempts)) {
            if (allowed(candidate) && free.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public List<String> candidates(RandomGenerator random, int generatedAttempts) {
        List<String> shuffled = new ArrayList<>(preferred);
        Collections.shuffle(shuffled, random);
        Set<String> ordered = new LinkedHashSet<>(shuffled);
        if (generator) {
            for (int i = 0; i < generatedAttempts; i++) {
                ordered.add(generate(random));
            }
        }
        return List.copyOf(ordered);
    }

    public String generate(RandomGenerator random) {
        String adjective = pick(ADJECTIVES, random);
        String noun = pick(NOUNS, random);
        String name = switch (random.nextInt(PATTERNS)) {
            case 0 -> adjective + noun;
            case 1 -> adjective + noun + random.nextInt(1, 100);
            case 2 -> adjective.toLowerCase(Locale.ROOT) + "_" + noun.toLowerCase(Locale.ROOT);
            case 3 -> noun + random.nextInt(100, 1000);
            case 4 -> (adjective + noun).toLowerCase(Locale.ROOT) + random.nextInt(1, 10);
            default -> "x" + adjective + noun;
        };
        if (name.length() > MAX_LENGTH) {
            return noun + random.nextInt(10, 100);
        }
        return name;
    }

    private static String pick(List<String> words, RandomGenerator random) {
        return words.get(random.nextInt(words.size()));
    }
}
