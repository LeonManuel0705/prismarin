package net.prismarin.api.moderation;

import java.util.Optional;
import java.util.regex.Pattern;

public interface ChatFilterRule {

    Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    String id();

    Optional<ChatFilterMatch> check(ChatMessage message);
}
