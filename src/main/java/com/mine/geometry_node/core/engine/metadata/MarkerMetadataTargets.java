package com.mine.geometry_node.core.engine.metadata;

import com.mine.geometry_node.core.engine.system.marker.MarkerStorage;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerInstance;
import com.mine.geometry_node.core.metadata.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.function.*;

/** Persistent marker metadata belongs to its address incarnation, never a query snapshot. */
public final class MarkerMetadataTargets {
    public static final String KIND = "geometry_node:marker";
    private MarkerMetadataTargets() {}

    public static void register(MetadataCapabilities registry) {
        registry.register(KIND, Target.class, EnumSet.allOf(MetadataCapabilities.Capability.class), MarkerMetadataTargets::resolve);
    }

    public static Target target(MinecraftServer server, MarkerAddress address) {
        Runnable check = () -> RuntimeMetadataTargets.requireThread(server);
        check.run();
        return new Target(MarkerStorage.get(server), address, server.registryAccess(), check);
    }

    public static final class Target {
        private final MarkerStorage owner;
        private final MarkerAddress address;
        private final UUID incarnation;
        private final HolderLookup.Provider registries;
        private final Runnable check;

        Target(MarkerStorage owner, MarkerAddress address, HolderLookup.Provider registries, Runnable check) {
            check.run();
            this.owner = owner; this.address = address; this.registries = registries; this.check = check;
            incarnation = owner.get(address).orElseThrow(() -> new IllegalStateException("Marker no longer exists")).incarnation();
        }

        private MarkerInstance current() {
            check.run();
            MarkerInstance marker = owner.get(address).orElseThrow(() -> new IllegalStateException("Marker no longer exists"));
            if (!marker.incarnation().equals(incarnation)) throw new IllegalStateException("Stale marker metadata target");
            return marker;
        }

        private <V> MetadataStore<V> store(Function<OwnerMetadata, V> read, BiFunction<OwnerMetadata, V, OwnerMetadata> replace) {
            return new MetadataStore<>() {
                public V read() { return read.apply(current().metadata()); }
                public boolean update(UnaryOperator<V> transformation) {
                    current();
                    return owner.updateMetadata(address, incarnation,
                            value -> replace.apply(value, transformation.apply(read.apply(value))));
                }
            };
        }
    }

    private static MetadataCapabilities.Access resolve(Target target) {
        target.current();
        return new MetadataCapabilities.Access(
                new TagAccess(target.store(OwnerMetadata::tags, OwnerMetadata::withTags), TagPolicy.EXACT),
                new DataAccess(target.store(value -> value.data(target.registries),
                        (value, data) -> value.withData(data, target.registries)), GraphMetadataValuePolicy.of(target.registries)));
    }
}
