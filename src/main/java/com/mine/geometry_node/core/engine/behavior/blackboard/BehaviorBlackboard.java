package com.mine.geometry_node.core.engine.behavior.blackboard;

import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateProvider;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.node.definition.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.function.UnaryOperator;
import com.mine.geometry_node.core.metadata.DataDictionary;
import com.mine.geometry_node.core.metadata.DataValuePolicy;
import com.mine.geometry_node.core.metadata.DataAccess;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;

/** Dynamic scoped blackboard facade. Keys are created by Set and removed by Clear. */
public final class BehaviorBlackboard {
    private final Map<ScopedStateScope, ScopedStateProvider> providers = new LinkedHashMap<>();
    private final int maxEntries;

    public BehaviorBlackboard(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
        installProvider(new MemoryProvider(ScopedStateScope.INSTANCE, maxEntries));
    }

    public void installProvider(ScopedStateProvider provider) {
        Objects.requireNonNull(provider, "provider");
        ScopedStateProvider existing = providers.putIfAbsent(provider.scope(), provider);
        if (existing != null && existing != provider) {
            throw new IllegalStateException("Blackboard provider already installed: " + provider.scope());
        }
    }

    @Nullable
    public Object get(ScopedStateScope scope, String name) {
        ScopedStateEntry entry = requireProvider(scope).get(requireName(name));
        return entry != null ? entry.value() : null;
    }

    public boolean contains(ScopedStateScope scope, String name) {
        return requireProvider(scope).hasRecord(requireName(name));
    }

    public DataAccess data(ScopedStateScope scope) { return requireProvider(scope).data(); }

    public void set(ScopedStateScope scope, String name, @Nullable Object value) {
        Objects.requireNonNull(scope, "scope");
        String key = requireName(name);
        if (value == null) {
            throw new ScopedStateAccessException("Blackboard value cannot be Java null: " + scope + "/" + key);
        }
        ScopedStateProvider provider = requireProvider(scope);
        if (scope == ScopedStateScope.INSTANCE
                && !provider.hasRecord(key) && provider.size() >= maxEntries) {
            throw new ScopedStateAccessException("Blackboard entry limit exceeded: " + maxEntries);
        }
        provider.data().put(key, value);
    }

    /** Removes the complete key/value record. Missing keys are an idempotent no-op. */
    public boolean clear(ScopedStateScope scope, String name) {
        Objects.requireNonNull(scope, "scope");
        String key = requireName(name);
        ScopedStateProvider provider = requireProvider(scope);
        return provider.remove(key);
    }

    public long revision() {
        long total = 0L;
        for (ScopedStateProvider provider : providers.values()) total += provider.revision();
        return total;
    }

    public ObservationToken observe(ScopedStateScope scope, String name) {
        String key = requireName(name);
        ScopedStateProvider provider = requireProvider(scope);
        ScopedStateEntry entry = provider.get(key);
        return new ObservationToken(provider.identity(), entry != null,
                entry != null ? entry.value() : null, provider.valuePolicy());
    }

    public Snapshot snapshot(int limit) {
        int boundedLimit = Math.max(0, limit);
        List<EntrySnapshot> result = new ArrayList<>();
        for (Map.Entry<ScopedStateScope, ScopedStateProvider> installed : providers.entrySet()) {
            if (result.size() >= boundedLimit) break;
            appendSnapshots(result, installed.getKey(), installed.getValue(),
                    boundedLimit - result.size());
        }
        int availableEntries = 0;
        for (ScopedStateProvider provider : providers.values()) {
            if (provider.available()) availableEntries += provider.size();
            if (availableEntries > result.size()) break;
        }
        return new Snapshot(result, availableEntries > result.size());
    }

    private static void appendSnapshots(List<EntrySnapshot> result, ScopedStateScope scope,
                                        ScopedStateProvider provider, int limit) {
        if (!provider.available() || limit <= 0) return;
        for (Map.Entry<String, ScopedStateEntry> stored : provider.entries(limit).entrySet()) {
            ScopedStateEntry entry = stored.getValue();
            result.add(new EntrySnapshot(stored.getKey(), scope,
                    provider.identity(), entry.type(), freeze(entry.value()), true));
        }
    }

    private ScopedStateProvider requireProvider(ScopedStateScope scope) {
        ScopedStateProvider provider = providers.get(Objects.requireNonNull(scope, "scope"));
        if (provider == null || !provider.available()) {
            throw new ScopedStateAccessException("Blackboard scope is unavailable: " + scope);
        }
        return provider;
    }

    private static String requireName(String name) {
        return name != null ? name : "";
    }

    private static Object freeze(Object value) {
        return GraphValueSnapshot.snapshot(value);
    }

    /** Stable change detector state; removed-key history is deliberately excluded. */
    public record ObservationToken(String providerIdentity, boolean present, @Nullable Object value,
                                   DataValuePolicy valuePolicy) {
        public ObservationToken(String providerIdentity, boolean present, @Nullable Object value) {
            this(providerIdentity, present, value, GraphMetadataValuePolicy.runtime());
        }
        public ObservationToken {
            providerIdentity = providerIdentity != null ? providerIdentity : "";
            Objects.requireNonNull(valuePolicy, "valuePolicy");
            // ScopedStateProvider#get already returns a detached read value.
            value = present ? value : null;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ObservationToken token
                    && present == token.present
                    && providerIdentity.equals(token.providerIdentity)
                    && valuePolicy.equivalent(value, token.value)
                    && token.valuePolicy.equivalent(token.value, value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerIdentity, present);
        }
    }

    public record EntrySnapshot(String name, ScopedStateScope scope, String providerIdentity,
                                PortType type, @Nullable Object value, boolean scopeAvailable) {
    }

    public record Snapshot(List<EntrySnapshot> entries, boolean truncated) {
        public Snapshot {
            entries = List.copyOf(entries);
        }
    }

    private static final class MemoryProvider implements ScopedStateProvider {
        private final ScopedStateScope scope;
        private Map<String, Object> values = Map.of();
        private final int maxEntries;
        private final DataValuePolicy policy = GraphMetadataValuePolicy.runtime();
        private boolean updating;
        private long revision;

        private MemoryProvider(ScopedStateScope scope, int maxEntries) {
            this.scope = scope;
            this.maxEntries = maxEntries;
        }

        @Override public ScopedStateScope scope() { return scope; }
        @Override public String identity() { return "instance"; }
        @Override public ScopedStateEntry get(String name) {
            Object value = values.get(name);
            return value != null ? new ScopedStateEntry(policy.snapshot(value), PortType.getTypeOf(value)) : null;
        }
        @Override public void put(String name, Object value) {
            data().put(name, value);
        }
        @Override public boolean remove(String name) {
            return data().remove(name);
        }
        @Override public boolean hasRecord(String name) { return values.containsKey(name); }
        @Override public Map<String, ScopedStateEntry> entries(int limit) {
            if (limit <= 0 || values.isEmpty()) return Map.of();
            Map<String, ScopedStateEntry> result = new LinkedHashMap<>();
            for (String name : values.keySet()) {
                result.put(name, get(name));
                if (result.size() >= limit) break;
            }
            return Collections.unmodifiableMap(result);
        }
        @Override public Map<String, Object> read() { return DataDictionary.snapshot(values, policy); }
        @Override public boolean update(UnaryOperator<Map<String, Object>> transformation) {
            if (updating) throw new IllegalStateException("Reentrant blackboard metadata mutation");
            updating = true;
            try {
                Map<String, Object> candidate = DataDictionary.snapshot(
                        Objects.requireNonNull(transformation.apply(read()), "transformation result"), policy);
                if (candidate.size() > maxEntries)
                    throw new ScopedStateAccessException("Blackboard entry limit exceeded: " + maxEntries);
                if (DataDictionary.equivalent(values, candidate, policy)) return false;
                values = candidate;
                revision++;
                return true;
            } finally { updating = false; }
        }
        @Override public long revision() { return revision; }
        @Override public int size() { return values.size(); }
    }
}
