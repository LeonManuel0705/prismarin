package net.prismarin.api.admin;

import java.util.List;
import java.util.Map;

public record ReloadResult(List<String> reloaded, Map<String, String> failed) {

    public ReloadResult {
        reloaded = reloaded == null ? List.of() : List.copyOf(reloaded);
        failed = failed == null ? Map.of() : Map.copyOf(failed);
    }

    public boolean complete() {
        return failed.isEmpty();
    }
}
