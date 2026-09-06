package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

import java.util.Objects;

/** Encoded persistence data paired with its lazily hydrated runtime snapshot. */
final class PersistentScopedStateEntry {
    private final Tag encodedValue;
    private volatile GraphValueSnapshot.FrozenValue frozenValue;
    private volatile RuntimeException decodeFailure;

    private PersistentScopedStateEntry(Tag encodedValue,
                                       GraphValueSnapshot.FrozenValue frozenValue) {
        this.encodedValue = Objects.requireNonNull(encodedValue, "encodedValue").copy();
        this.frozenValue = frozenValue;
    }

    static PersistentScopedStateEntry loaded(Tag encodedValue) {
        return new PersistentScopedStateEntry(encodedValue, null);
    }

    static PersistentScopedStateEntry written(Tag encodedValue,
                                               GraphValueSnapshot.FrozenValue frozenValue) {
        return new PersistentScopedStateEntry(
                encodedValue, Objects.requireNonNull(frozenValue, "frozenValue"));
    }

    Tag encodedCopy() {
        return encodedValue.copy();
    }

    boolean equivalentTo(GraphValueSnapshot.FrozenValue candidate,
                         HolderLookup.Provider registries, String location) {
        try {
            return GraphValueSnapshot.equivalent(
                    frozen(registries, location).value(), candidate.value());
        } catch (ScopedStateAccessException ignored) {
            // A valid write must be able to replace a corrupt persisted value.
            return false;
        }
    }

    ScopedStateEntry read(HolderLookup.Provider registries, String location) {
        GraphValueSnapshot.FrozenValue current = frozen(registries, location);
        Object value = GraphValueSnapshot.read(current);
        return new ScopedStateEntry(value, PortType.getTypeOf(current.value()));
    }

    private GraphValueSnapshot.FrozenValue frozen(
            HolderLookup.Provider registries, String location) {
        GraphValueSnapshot.FrozenValue current = frozenValue;
        return current != null ? current : hydrate(registries, location);
    }

    private synchronized GraphValueSnapshot.FrozenValue hydrate(
            HolderLookup.Provider registries, String location) {
        if (frozenValue != null) return frozenValue;
        if (decodeFailure != null) throw decodeException(location, decodeFailure);
        try {
            Object decoded = GraphValueCodecRegistry.fromTag(encodedValue, registries);
            if (decoded == null) throw new IllegalArgumentException("Decoded value is null");
            frozenValue = GraphValueSnapshot.freeze(decoded);
            return frozenValue;
        } catch (RuntimeException exception) {
            decodeFailure = exception;
            throw decodeException(location, exception);
        }
    }

    private static ScopedStateAccessException decodeException(
            String location, RuntimeException cause) {
        return new ScopedStateAccessException(
                "Persistent scoped-state value cannot be decoded: " + location, cause);
    }
}
