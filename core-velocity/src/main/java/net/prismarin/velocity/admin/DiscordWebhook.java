package net.prismarin.velocity.admin;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class DiscordWebhook {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final int MAX_LENGTH = 1900;

    private DiscordWebhook() {
        throw new AssertionError();
    }

    public static void post(String url, String content) {
        if (url == null || !url.startsWith("https://")) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("content", content.length() > MAX_LENGTH ? content.substring(0, MAX_LENGTH) : content);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
}
