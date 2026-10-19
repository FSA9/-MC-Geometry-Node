package com.mine.geometry_node.core.metadata;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Common dictionary operations, with one atomic owner commit for a batch. */
public final class DataAccess {
    private final MetadataStore<Map<String, Object>> store;
    private final DataValuePolicy policy;
    public DataAccess(MetadataStore<Map<String, Object>> store, DataValuePolicy policy) {
        this.store = Objects.requireNonNull(store); this.policy = Objects.requireNonNull(policy);
    }
    public Map<String, Object> values() { return DataDictionary.snapshot(store.read(), policy); }
    public boolean contains(String key) { return values().containsKey(policy.validateKey(key)); }
    public Object get(String key) { return values().get(policy.validateKey(key)); }
    public Map<String, Object> find(Predicate<String> keys) {
        Objects.requireNonNull(keys);
        Map<String, Object> result = new LinkedHashMap<>();
        values().forEach((key, value) -> { if (keys.test(key)) result.put(key, value); });
        return Collections.unmodifiableMap(result);
    }
    public boolean put(String key, Object value) { return putAll(Collections.singletonMap(key, value)); }
    public boolean putAll(Map<String, ?> additions) { return patch(additions, Set.of()); }
    public boolean remove(String key) { return patch(Map.of(), Set.of(key)); }
    public boolean clear() { return replace(Map.of()); }
    /** Executes a complete dictionary transformation inside the owner's atomic update boundary. */
    public boolean update(UnaryOperator<Map<String, Object>> transformation) {
        Objects.requireNonNull(transformation);
        return store.update(current -> DataDictionary.snapshot(
                Objects.requireNonNull(transformation.apply(current), "transformation result"), policy));
    }
    public boolean replace(Map<String, ?> values) {
        Map<String, Object> copy = DataDictionary.snapshot(values, policy);
        return store.update(ignored -> DataDictionary.snapshot(copy, policy));
    }
    /** Removed keys are removed first; additions win when a key occurs in both. */
    public boolean patch(Map<String, ?> additions, Collection<String> removals) {
        Map<String, Object> copy = DataDictionary.snapshot(additions, policy);
        List<String> removed = removals.stream().map(policy::validateKey).toList();
        return store.update(current -> {
            Map<String, Object> next = new LinkedHashMap<>(current);
            removed.forEach(next::remove); next.putAll(copy);
            return DataDictionary.snapshot(next, policy);
        });
    }
}
