package com.mine.geometry_node.client.ui.persistence.config;

import com.mine.geometry_node.client.ui.shortcut.KeyScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry consumed by persistence, runtime APIs and the global settings dialog. */
public final class ConfigRegistry {
    public static final ConfigRegistry INSTANCE = new ConfigRegistry();

    private final Map<String, ConfigCategory> mCategories = new LinkedHashMap<>();
    private final Map<String, ConfigEntry<?>> mEntries = new LinkedHashMap<>();

    private ConfigRegistry() {
        BuiltinConfigEntries.register(this);
    }

    public synchronized void registerCategory(ConfigCategory category) {
        Objects.requireNonNull(category, "category");
        ConfigCategory previous = mCategories.putIfAbsent(category.id(), category);
        if (previous != null && !previous.equals(category)) {
            throw new IllegalArgumentException("Duplicate config category id: " + category.id());
        }
    }

    public synchronized <T> ConfigEntry<T> register(ConfigEntry<T> entry) {
        Objects.requireNonNull(entry, "entry");
        registerCategory(entry.category());
        if (mEntries.putIfAbsent(entry.id(), entry) != null) {
            throw new IllegalArgumentException("Duplicate config entry id: " + entry.id());
        }
        return entry;
    }

    public synchronized ConfigEntry<?> find(String id) {
        return id != null ? mEntries.get(id) : null;
    }

    public synchronized List<ConfigCategory> categories() {
        return mCategories.values().stream()
                .sorted(Comparator.comparingInt(ConfigCategory::order).thenComparing(ConfigCategory::id))
                .toList();
    }

    public synchronized List<ConfigEntry<?>> entries() {
        return sortedEntries(mEntries.values());
    }

    public synchronized List<ConfigEntry<?>> entries(ConfigCategory category) {
        if (category == null) return List.of();
        return sortedEntries(mEntries.values().stream().filter(entry -> entry.category().id().equals(category.id())).toList());
    }

    /** Applies every registered entry's canonical validation contract in place. */
    public synchronized boolean normalize(AppConfig config) {
        Objects.requireNonNull(config, "config");
        boolean changed = false;
        for (ConfigEntry<?> entry : mEntries.values()) changed |= normalizeEntry(entry, config);
        return changed;
    }

    public synchronized List<ConfigEntry<String>> shortcutConflicts(
            ConfigEntry<String> target, String candidate, AppConfig config) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        InputBinding binding = InputBinding.parse(candidate);
        if (binding == null || target.keyScope() == null) return List.of();

        List<ConfigEntry<String>> conflicts = new ArrayList<>();
        for (ConfigEntry<?> raw : mEntries.values()) {
            if (raw == target || raw.keyScope() == null || !scopesOverlap(target.keyScope(), raw.keyScope())) continue;
            if (raw.editorType() != ConfigEntry.EditorType.KEY_BINDING
                    && raw.editorType() != ConfigEntry.EditorType.SHORTCUT) continue;
            @SuppressWarnings("unchecked") ConfigEntry<String> entry = (ConfigEntry<String>) raw;
            InputBinding existing = InputBinding.parse(entry.get(config));
            if (existing != null && binding.device() == existing.device() && binding.text().equals(existing.text())) {
                conflicts.add(entry);
            }
        }
        return List.copyOf(conflicts);
    }

    private static List<ConfigEntry<?>> sortedEntries(Iterable<ConfigEntry<?>> source) {
        List<ConfigEntry<?>> result = new ArrayList<>();
        source.forEach(result::add);
        result.sort(Comparator.comparingInt((ConfigEntry<?> entry) -> entry.category().order())
                .thenComparing(entry -> entry.category().id())
                .thenComparingInt(ConfigEntry::order)
                .thenComparing(ConfigEntry::id));
        return List.copyOf(result);
    }

    private static <T> boolean normalizeEntry(ConfigEntry<T> entry, AppConfig config) {
        T before = entry.get(config);
        T after = entry.normalize(before);
        if (Objects.equals(before, after)) return false;
        entry.set(config, after);
        return true;
    }

    private static boolean scopesOverlap(KeyScope first, KeyScope second) {
        return first == second;
    }
}
