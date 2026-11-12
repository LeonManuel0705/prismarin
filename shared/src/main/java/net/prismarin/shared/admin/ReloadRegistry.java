package net.prismarin.shared.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.prismarin.api.admin.Reloadable;
import net.prismarin.api.admin.ReloadResult;
import net.prismarin.api.admin.ReloadService;
import net.prismarin.api.event.Subscription;

public final class ReloadRegistry implements ReloadService {

    private record Part(String name, Reloadable reloadable) {
    }

    private final List<Part> parts = new CopyOnWriteArrayList<>();

    @Override
    public Subscription register(String name, Reloadable part) {
        Part entry = new Part(name, part);
        parts.add(entry);
        return () -> parts.remove(entry);
    }

    @Override
    public List<String> parts() {
        return parts.stream().map(Part::name).toList();
    }

    @Override
    public synchronized ReloadResult reloadAll() {
        List<String> reloaded = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();
        for (Part part : parts) {
            try {
                part.reloadable().reload();
                reloaded.add(part.name());
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                failed.put(part.name(), reason);
            }
        }
        return new ReloadResult(reloaded, failed);
    }
}
