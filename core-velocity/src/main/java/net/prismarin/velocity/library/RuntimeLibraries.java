package net.prismarin.velocity.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class RuntimeLibraries {

    public static final String INDEX = "prismarin-libraries.txt";
    public static final String DEFAULT_REPOSITORY = "https://repo1.maven.org/maven2/";
    public static final String REPOSITORY_PROPERTY = "prismarin.libraryRepository";
    public static final String REPOSITORY_ENV = "PRISMARIN_LIBRARY_REPOSITORY";

    public record Library(String mode, String group, String artifact, String version, String sha256) {

        public String fileName() {
            return artifact + "-" + version + ".jar";
        }

        public String path() {
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + fileName();
        }
    }

    public interface Downloader {
        void download(URI source, Path target) throws IOException, InterruptedException;
    }

    private final List<Library> index;
    private final Path folder;
    private final String repository;
    private final Downloader downloader;
    private final Consumer<Path> classpath;
    private final Logger logger;
    private final Set<String> loaded = new HashSet<>();

    public RuntimeLibraries(List<Library> index, Path folder, String repository, Downloader downloader,
                            Consumer<Path> classpath, Logger logger) {
        this.index = List.copyOf(index);
        this.folder = folder;
        this.repository = repository.endsWith("/") ? repository : repository + "/";
        this.downloader = downloader;
        this.classpath = classpath;
        this.logger = logger;
    }

    public static RuntimeLibraries create(Path folder, Consumer<Path> classpath, Logger logger) {
        List<Library> index;
        try (InputStream stream = RuntimeLibraries.class.getClassLoader().getResourceAsStream(INDEX)) {
            index = stream == null ? List.of() : parse(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new RuntimeLibraries(index, folder, repository(), RuntimeLibraries::httpDownload, classpath, logger);
    }

    public static String repository() {
        String property = System.getProperty(REPOSITORY_PROPERTY);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        String env = System.getenv(REPOSITORY_ENV);
        return env != null && !env.isBlank() ? env.trim() : DEFAULT_REPOSITORY;
    }

    public static List<Library> parse(InputStream stream) throws IOException {
        List<Library> libraries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 3) {
                    continue;
                }
                String[] coordinates = parts[1].split(":");
                if (coordinates.length != 3) {
                    continue;
                }
                libraries.add(new Library(parts[0].toUpperCase(Locale.ROOT), coordinates[0], coordinates[1],
                        coordinates[2], parts[2].toLowerCase(Locale.ROOT)));
            }
        }
        return List.copyOf(libraries);
    }

    public List<Library> needed(Collection<String> modes) {
        Set<String> wanted = new HashSet<>();
        modes.forEach(mode -> wanted.add(mode.toUpperCase(Locale.ROOT)));
        return index.stream().filter(library -> wanted.contains(library.mode())).toList();
    }

    public synchronized void ensure(Collection<String> modes) {
        for (Library library : needed(modes)) {
            if (loaded.contains(library.fileName())) {
                continue;
            }
            Path file = folder.resolve(library.fileName());
            try {
                if (!matches(file, library.sha256())) {
                    fetch(library, file);
                }
                classpath.accept(file);
                loaded.add(library.fileName());
            } catch (IOException e) {
                throw new IllegalStateException("Could not load " + library.fileName() + " for storage mode "
                        + library.mode() + ". Put the file into " + folder + " by hand, allow downloads from "
                        + repository + " or pick another storage.mode.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while downloading " + library.fileName(), e);
            }
        }
    }

    private void fetch(Library library, Path file) throws IOException, InterruptedException {
        Files.createDirectories(folder);
        URI source = URI.create(repository + library.path());
        logger.info("Downloading {} for storage mode {} from {}", library.fileName(), library.mode(), repository);
        Path temporary = Files.createTempFile(folder, library.artifact(), ".part");
        try {
            downloader.download(source, temporary);
            if (!matches(temporary, library.sha256())) {
                throw new IOException("Checksum of " + library.fileName() + " does not match, refusing to load it");
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static boolean matches(Path file, String sha256) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        return sha256(file).equalsIgnoreCase(sha256);
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void httpDownload(URI source, Path target) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(source).timeout(Duration.ofMinutes(2))
                .header("User-Agent", "PRISMARIN").GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + source);
        }
    }
}
