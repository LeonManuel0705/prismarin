package net.prismarin.velocity.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.prismarin.api.moderation.ReportAction;

public final class ReportRegistry {

    private final List<ReportReason> configuredReasons;
    private final Map<String, ReportReason> addedReasons = new LinkedHashMap<>();
    private final Map<String, ReportAction> configuredActions = new LinkedHashMap<>();
    private final Map<String, ReportAction> addedActions = new LinkedHashMap<>();

    public ReportRegistry(List<ReportReason> configuredReasons, List<ReportAction> configuredActions) {
        this.configuredReasons = List.copyOf(configuredReasons);
        configuredActions.forEach(action -> this.configuredActions.putIfAbsent(action.id(), action));
    }

    public synchronized List<ReportReason> reasons() {
        List<ReportReason> all = new ArrayList<>(configuredReasons);
        all.addAll(addedReasons.values());
        return List.copyOf(all);
    }

    public synchronized Optional<ReportReason> reason(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.toLowerCase(Locale.ROOT);
        return reasons().stream().filter(reason -> reason.id().equals(key)).findFirst();
    }

    public synchronized void register(ReportReason reason) {
        if (configuredReasons.stream().anyMatch(existing -> existing.id().equals(reason.id()))) {
            throw new IllegalArgumentException("the reason " + reason.id() + " already comes from admin.yml");
        }
        addedReasons.put(reason.id(), reason);
    }

    public synchronized void unregisterReason(String id) {
        addedReasons.remove(id.toLowerCase(Locale.ROOT));
    }

    public synchronized List<ReportAction> actions() {
        List<ReportAction> all = new ArrayList<>(configuredActions.values());
        all.addAll(addedActions.values());
        return List.copyOf(all);
    }

    public synchronized Optional<ReportAction> action(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.toLowerCase(Locale.ROOT);
        return Optional.ofNullable(configuredActions.get(key)).or(() -> Optional.ofNullable(addedActions.get(key)));
    }

    public synchronized void register(ReportAction action) {
        if (configuredActions.containsKey(action.id())) {
            throw new IllegalArgumentException("the action " + action.id() + " already comes from admin.yml");
        }
        addedActions.put(action.id(), action);
    }

    public synchronized void unregisterAction(String id) {
        addedActions.remove(id.toLowerCase(Locale.ROOT));
    }
}
