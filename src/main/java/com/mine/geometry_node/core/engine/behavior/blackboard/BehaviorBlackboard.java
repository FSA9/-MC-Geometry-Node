package com.mine.geometry_node.core.engine.behavior.blackboard;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorValueSemantics;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateChange;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateProvider;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateValueCodec;
import com.mine.geometry_node.core.node.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dynamic scoped blackboard facade. Keys are created by Set and removed by Clear. */
public final class BehaviorBlackboard {
    private static final int MAX_KEY_LENGTH = 256;
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
        return entry != null ? freeze(entry.value()) : null;
    }

    public boolean contains(ScopedStateScope scope, String name) {
        return requireProvider(scope).hasRecord(requireName(name));
    }

    public void set(ScopedStateScope scope, String name, @Nullable Object value,
                    String sourceNodeId, long gameTick) {
        Objects.requireNonNull(scope, "scope");
        String key = requireName(name);
        if (value == null) {
            throw new ScopedStateAccessException("Blackboard value cannot be Java null: " + scope + "/" + key);
        }
        if (scope.isPersistent()) ScopedStateValueCodec.validate(value, scope + "/" + key);
        ScopedStateProvider provider = requireProvider(scope);
        if (scope == ScopedStateScope.INSTANCE
                && !provider.hasRecord(key) && provider.size() >= maxEntries) {
            throw new ScopedStateAccessException("Blackboard entry limit exceeded: " + maxEntries);
        }
        String source = sourceNodeId != null ? sourceNodeId : "";
        provider.put(key, freeze(value), source, gameTick);
    }

    /** Removes the complete key/value record. Missing keys are an idempotent no-op. */
    public boolean clear(ScopedStateScope scope, String name, String sourceNodeId, long gameTick) {
        Objects.requireNonNull(scope, "scope");
        String key = requireName(name);
        ScopedStateProvider provider = requireProvider(scope);
        if (!provider.hasRecord(key)) return false;
        provider.remove(key, sourceNodeId != null ? sourceNodeId : "", gameTick);
        return true;
    }

    public long revision() {
        long total = 0L;
        for (ScopedStateProvider provider : providers.values()) total += provider.revision();
        return total;
    }

    public long revision(ScopedStateScope scope, String name) {
        String key = requireName(name);
        ScopedStateProvider provider = requireProvider(scope);
        ScopedStateEntry entry = provider.get(key);
        if (entry != null) return entry.revision();
        ScopedStateChange providerChange = provider.lastChange(key);
        if (providerChange != null) return providerChange.revision();
        return 0L;
    }

    public ObservationToken observe(ScopedStateScope scope, String name) {
        String key = requireName(name);
        ScopedStateProvider provider = requireProvider(scope);
        ScopedStateEntry entry = provider.get(key);
        return new ObservationToken(provider.identity(), entry != null,
                entry != null ? entry.revision() : 0L);
    }

    public List<EntrySnapshot> snapshot() {
        List<EntrySnapshot> result = new ArrayList<>();
        for (Map.Entry<ScopedStateScope, ScopedStateProvider> installed : providers.entrySet()) {
            appendSnapshots(result, installed.getKey(), installed.getValue());
        }
        return List.copyOf(result);
    }

    public List<EntrySnapshot> snapshot(ScopedStateScope scope) {
        List<EntrySnapshot> result = new ArrayList<>();
        ScopedStateProvider provider = providers.get(Objects.requireNonNull(scope, "scope"));
        if (provider != null) appendSnapshots(result, scope, provider);
        return List.copyOf(result);
    }

    private static void appendSnapshots(List<EntrySnapshot> result, ScopedStateScope scope,
                                        ScopedStateProvider provider) {
        if (!provider.available()) return;
        for (Map.Entry<String, ScopedStateEntry> stored : provider.entries().entrySet()) {
            ScopedStateEntry entry = stored.getValue();
            result.add(new EntrySnapshot(stored.getKey(), scope,
                    provider.identity(), entry.type(), freeze(entry.value()), entry.revision(),
                    entry.sourceNodeId(), entry.gameTick(), true));
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
        String normalized = name != null ? name.trim() : "";
        if (normalized.isEmpty()) throw new ScopedStateAccessException("Blackboard key cannot be empty");
        if (normalized.length() > MAX_KEY_LENGTH) {
            throw new ScopedStateAccessException("Blackboard key exceeds " + MAX_KEY_LENGTH + " characters");
        }
        return normalized;
    }

    private static Object freeze(Object value) {
        return BehaviorValueSemantics.freezeAs(value, PortType.ANY);
    }

    /** Stable change detector state; removed-key history is deliberately excluded. */
    public record ObservationToken(String providerIdentity, boolean present, long revision) {
        public ObservationToken {
            providerIdentity = providerIdentity != null ? providerIdentity : "";
            if (!present) revision = 0L;
        }
    }

    public record EntrySnapshot(String name, ScopedStateScope scope, String providerIdentity,
                                PortType type, @Nullable Object value, long revision,
                                String sourceNodeId, long gameTick, boolean scopeAvailable) {
    }

    private static final class MemoryProvider implements ScopedStateProvider {
        private final ScopedStateScope scope;
        private final Map<String, ScopedStateEntry> values = new LinkedHashMap<>();
        private final Map<String, ScopedStateChange> changes = new LinkedHashMap<>();
        private final int maxChanges;
        private long revision;

        private MemoryProvider(ScopedStateScope scope, int maxChanges) {
            this.scope = scope;
            this.maxChanges = maxChanges;
        }

        @Override public ScopedStateScope scope() { return scope; }
        @Override public String identity() { return "instance"; }
        @Override public ScopedStateEntry get(String name) {
            ScopedStateEntry entry = values.get(name);
            return entry != null ? new ScopedStateEntry(freeze(entry.value()), entry.type(), entry.revision(),
                    entry.sourceNodeId(), entry.gameTick()) : null;
        }
        @Override public ScopedStateEntry put(String name, Object value, String sourceNodeId, long gameTick) {
            Object frozen = freeze(value);
            ScopedStateEntry entry = new ScopedStateEntry(frozen, PortType.getTypeOf(value), ++revision,
                    sourceNodeId, gameTick);
            values.put(name, entry);
            recordChange(name, new ScopedStateChange(revision, sourceNodeId, gameTick));
            return entry;
        }
        @Override public ScopedStateChange remove(String name, String sourceNodeId, long gameTick) {
            values.remove(name);
            ScopedStateChange change = new ScopedStateChange(++revision, sourceNodeId, gameTick);
            recordChange(name, change);
            return change;
        }
        @Override public ScopedStateChange lastChange(String name) { return changes.get(name); }
        @Override public boolean hasRecord(String name) { return values.containsKey(name); }
        @Override public Map<String, ScopedStateEntry> entries() { return Map.copyOf(values); }
        @Override public long revision() { return revision; }
        @Override public int size() { return values.size(); }

        private void recordChange(String name, ScopedStateChange change) {
            changes.remove(name);
            changes.put(name, change);
            while (changes.size() > maxChanges) {
                changes.remove(changes.keySet().iterator().next());
            }
        }
    }
}
