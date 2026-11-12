package net.prismarin.shared.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.prismarin.api.admin.UpdateInfo;
import net.prismarin.api.admin.UpdateService;

public final class UpdateChecker implements UpdateService {

    public static final String RELEASES = "https://api.github.com/repos/LeonManuel0705/prismarin/releases/latest";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");

    private final String current;
    private final Function<URI, CompletableFuture<String>> fetcher;
    private final Consumer<UpdateInfo> onFound;
    private final AtomicReference<UpdateInfo> available = new AtomicReference<>();

    public UpdateChecker(String current, Function<URI, CompletableFuture<String>> fetcher,
                         Consumer<UpdateInfo> onFound) {
        this.current = current;
        this.fetcher = fetcher;
        this.onFound = onFound;
    }

    public static UpdateChecker github(String current, Consumer<UpdateInfo> onFound) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        return new UpdateChecker(current, uri -> client.sendAsync(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "PRISMARIN-update-check")
                        .GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 ? response.body() : ""), onFound);
    }

    @Override
    public String currentVersion() {
        return current;
    }

    @Override
    public Optional<UpdateInfo> available() {
        return Optional.ofNullable(available.get());
    }

    @Override
    public CompletableFuture<Optional<UpdateInfo>> check() {
        return fetcher.apply(URI.create(RELEASES))
                .thenApply(this::parse)
                .exceptionally(error -> Optional.empty())
                .thenApply(found -> {
                    found.ifPresent(update -> {
                        UpdateInfo previous = available.getAndSet(update);
                        if (previous == null || !previous.latest().equals(update.latest())) {
                            onFound.accept(update);
                        }
                    });
                    return found;
                });
    }

    Optional<UpdateInfo> parse(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        Matcher tag = TAG.matcher(body);
        if (!tag.find()) {
            return Optional.empty();
        }
        String latest = tag.group(1).startsWith("v") ? tag.group(1).substring(1) : tag.group(1);
        if (!Versions.newer(latest, current)) {
            return Optional.empty();
        }
        Matcher url = URL.matcher(body);
        String link = url.find() ? url.group(1) : "https://github.com/LeonManuel0705/prismarin/releases";
        return Optional.of(new UpdateInfo(current, latest, link));
    }
}
