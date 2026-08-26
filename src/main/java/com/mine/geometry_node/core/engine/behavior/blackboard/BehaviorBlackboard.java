package com.mine.geometry_node.core.engine.behavior.blackboard;

import com.mine.geometry_node.core.engine.behavior.contract.BlackboardScope;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorValueSemantics;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.node.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed blackboard facade. P3 installs an instance provider; later scopes use the same contract. */
public final class BehaviorBlackboard {
    private final BehaviorTreePlan.BlackboardSchema schema;
    private final Map<BlackboardScope, Provider> providers = new EnumMap<>(BlackboardScope.class);
    private final Map<BehaviorTreePlan.BlackboardSchema.Key, ChangeMetadata> lastChanges = new LinkedHashMap<>();
    private final int maxEntries;
    private long revision;

    public BehaviorBlackboard(BehaviorTreePlan.BlackboardSchema schema, int maxEntries) {
        this.schema = Objects.requireNonNull(schema, "schema");
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
        installProvider(new MemoryProvider(BlackboardScope.INSTANCE));
    }

    public void installProvider(Provider provider) {
        Objects.requireNonNull(provider, "provider");
        Provider existing = providers.putIfAbsent(provider.scope(), provider);
        if (existing != null && existing != provider) {
            throw new IllegalStateException("Blackboard provider already installed: " + provider.scope());
        }
        if (existing == null) {
            for (BehaviorTreePlan.BlackboardKey key : schema.declarations()) {
                if (key.scope() == provider.scope() && key.defaultValue() != null
                        && provider.get(key.name()) == null) {
                    write(key, key.defaultValue(), "<default>", 0L, true);
                }
            }
        }
    }

    @Nullable
    public Object get(BlackboardScope scope, String name) {
        requireDeclaration(scope, name);
        Entry entry = requireProvider(scope).get(name);
        return entry != null ? entry.value() : null;
    }

    public boolean contains(BlackboardScope scope, String name) {
        requireDeclaration(scope, name);
        return requireProvider(scope).get(name) != null;
    }

    public void set(BlackboardScope scope, String name, @Nullable Object value,
                    String sourceNodeId, long gameTick) {
        BehaviorTreePlan.BlackboardKey key = requireDeclaration(scope, name);
        if (value == null) {
            clear(scope, name, sourceNodeId, gameTick);
            return;
        }
        write(key, value, sourceNodeId, gameTick, false);
    }

    public boolean clear(BlackboardScope scope, String name, String sourceNodeId, long gameTick) {
        BehaviorTreePlan.BlackboardKey key = requireDeclaration(scope, name);
        if (!key.writable()) throw new BlackboardAccessException("Blackboard key is read-only: " + name);
        Provider provider = requireProvider(scope);
        if (provider.get(name) == null) return false;
        provider.remove(name);
        revision++;
        lastChanges.put(new BehaviorTreePlan.BlackboardSchema.Key(scope, name),
                new ChangeMetadata(revision, sourceNodeId != null ? sourceNodeId : "", gameTick));
        return true;
    }

    public long revision() {
        return revision;
    }

    public List<EntrySnapshot> snapshot() {
        List<EntrySnapshot> result = new ArrayList<>();
        for (BehaviorTreePlan.BlackboardKey key : schema.declarations()) {
            Provider provider = providers.get(key.scope());
            Entry entry = provider != null ? provider.get(key.name()) : null;
            ChangeMetadata change = lastChanges.get(
                    new BehaviorTreePlan.BlackboardSchema.Key(key.scope(), key.name()));
            result.add(new EntrySnapshot(key.name(), key.scope(), key.type(), key.writable(),
                    entry != null ? entry.value() : null,
                    entry != null ? entry.revision() : change != null ? change.revision() : 0L,
                    entry != null ? entry.sourceNodeId() : change != null ? change.sourceNodeId() : "",
                    entry != null ? entry.gameTick() : change != null ? change.gameTick() : 0L,
                    provider != null));
        }
        return List.copyOf(result);
    }

    private void write(BehaviorTreePlan.BlackboardKey key, Object value, String sourceNodeId,
                       long gameTick, boolean initializing) {
        if (!initializing && !key.writable()) {
            throw new BlackboardAccessException("Blackboard key is read-only: " + key.name());
        }
        if (!BehaviorValueSemantics.matches(value, key.type())) {
            throw new BlackboardAccessException("Blackboard value does not match "
                    + key.type() + ": " + key.name());
        }
        Provider provider = requireProvider(key.scope());
        if (key.scope() == BlackboardScope.INSTANCE && provider.get(key.name()) == null
                && provider.size() >= maxEntries) {
            throw new BlackboardAccessException("Blackboard entry limit exceeded: " + maxEntries);
        }
        revision++;
        String source = sourceNodeId != null ? sourceNodeId : "";
        provider.put(key.name(), new Entry(BehaviorValueSemantics.freezeAs(value, key.type()), revision,
                source, gameTick));
        lastChanges.put(new BehaviorTreePlan.BlackboardSchema.Key(key.scope(), key.name()),
                new ChangeMetadata(revision, source, gameTick));
    }

    private BehaviorTreePlan.BlackboardKey requireDeclaration(BlackboardScope scope, String name) {
        BehaviorTreePlan.BlackboardKey key = schema.find(
                Objects.requireNonNull(scope, "scope"), name != null ? name : "");
        if (key == null) throw new BlackboardAccessException("Undeclared blackboard key: " + scope + "/" + name);
        return key;
    }

    private Provider requireProvider(BlackboardScope scope) {
        Provider provider = providers.get(scope);
        if (provider == null) throw new BlackboardAccessException("Blackboard scope is unavailable: " + scope);
        return provider;
    }

    public interface Provider {
        BlackboardScope scope();
        @Nullable Entry get(String name);
        void put(String name, Entry entry);
        void remove(String name);
        int size();
    }

    public record Entry(Object value, long revision, String sourceNodeId, long gameTick) {
    }

    private record ChangeMetadata(long revision, String sourceNodeId, long gameTick) {
    }

    public record EntrySnapshot(String name, BlackboardScope scope, PortType type,
                                boolean writable, @Nullable Object value, long revision,
                                String sourceNodeId, long gameTick, boolean scopeAvailable) {
    }

    public static final class BlackboardAccessException extends IllegalStateException {
        public BlackboardAccessException(String message) {
            super(message);
        }
    }

    private static final class MemoryProvider implements Provider {
        private final BlackboardScope scope;
        private final Map<String, Entry> values = new LinkedHashMap<>();

        private MemoryProvider(BlackboardScope scope) {
            this.scope = scope;
        }

        @Override public BlackboardScope scope() { return scope; }
        @Override public Entry get(String name) { return values.get(name); }
        @Override public void put(String name, Entry entry) { values.put(name, entry); }
        @Override public void remove(String name) { values.remove(name); }
        @Override public int size() { return values.size(); }
    }
}
