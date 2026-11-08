package net.prismarin.shared.text;

import java.util.UUID;

@FunctionalInterface
public interface PlaceholderExpander {

    PlaceholderExpander NONE = (viewer, text) -> text;

    String expand(UUID viewer, String text);
}
