package net.prismarin.api.moderation;

import java.util.List;

public interface ChatFilterService {

    ChatFilterResult check(String text);

    ChatFilterResult filter(ChatMessage message);

    List<String> rules();

    void registerRule(ChatFilterRule rule);

    void unregisterRule(String id);
}
