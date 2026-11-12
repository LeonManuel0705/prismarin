package net.prismarin.api.reward;

import java.util.Objects;

public record VoteSite(String id, String name, String url, String service) {

    public VoteSite {
        Objects.requireNonNull(id, "id");
        name = name == null || name.isBlank() ? id : name;
        url = url == null ? "" : url;
        service = service == null ? "" : service;
    }
}
