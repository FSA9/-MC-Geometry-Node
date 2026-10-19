package com.mine.geometry_node.core.metadata;

import java.util.*;

/** Reusable tag API; adapters supply storage, never their own CRUD rules. */
public final class TagAccess {
    private final MetadataStore<List<String>> store;
    private final TagPolicy policy;

    public TagAccess(MetadataStore<List<String>> store, TagPolicy policy) {
        this.store = Objects.requireNonNull(store);
        this.policy = Objects.requireNonNull(policy);
    }

    public List<String> values() { return TagSet.normalize(store.read(), policy); }
    public boolean contains(String tag) { return TagSet.contains(values(), tag, policy); }
    public boolean containsAll(Collection<String> tags) { return TagSet.containsAll(values(), tags, policy); }
    public boolean add(String tag) { return addAll(List.of(tag)); }
    public boolean remove(String tag) { return removeAll(List.of(tag)); }
    public boolean addAll(Collection<String> tags) {
        List<String> additions = TagSet.normalize(tags, policy);
        return store.update(current -> TagSet.add(current, additions, policy));
    }
    public boolean removeAll(Collection<String> tags) {
        List<String> removals = TagSet.normalize(tags, policy);
        return store.update(current -> TagSet.remove(current, removals, policy));
    }
    public boolean replace(Collection<String> tags) {
        List<String> value = TagSet.normalize(tags, policy);
        return store.update(ignored -> value);
    }
    public boolean clear() { return replace(List.of()); }
}
