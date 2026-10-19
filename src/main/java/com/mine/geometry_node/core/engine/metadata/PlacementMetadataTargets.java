package com.mine.geometry_node.core.engine.metadata;

import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.SchematicPlacementRecord;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementStorage;
import com.mine.geometry_node.core.metadata.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.*;

/** Persistent placement owners; repair retains incarnation, replacing/reusing a key does not. */
public final class PlacementMetadataTargets {
    public static final String KIND = "geometry_node:schematic_placement";
    private PlacementMetadataTargets() {}

    public static void register(MetadataCapabilities registry) {
        registry.register(KIND, Target.class, EnumSet.allOf(MetadataCapabilities.Capability.class), PlacementMetadataTargets::resolve);
    }
    public static Target target(ServerLevel level, String key) {
        Runnable check = () -> {
            if (!level.getServer().isSameThread() || level.getServer().isStopped())
                throw new IllegalStateException("Placement metadata requires the live owner server thread");
        };
        check.run();
        return new Target(SchematicPlacementStorage.get(level), level.dimension(), key, level.registryAccess(), check);
    }
    public static MetadataCapabilities.Access access(ServerLevel level, String key) {
        return MetadataCapabilities.GLOBAL.resolve(KIND, target(level, key));
    }
    public static List<String> tags(ServerLevel level, String key) { return target(level, key).current().tags(); }

    public static final class Target {
        private final SchematicPlacementStorage owner;
        private final ResourceKey<Level> dimension;
        private final String key;
        private final UUID incarnation;
        private final HolderLookup.Provider registries;
        private final Runnable check;

        Target(SchematicPlacementStorage owner, ResourceKey<Level> dimension, String key, HolderLookup.Provider registries, Runnable check) {
            check.run();
            this.owner = owner; this.dimension = dimension; this.key = Objects.requireNonNull(key).trim();
            this.registries = registries; this.check = check;
            incarnation = owner.get(dimension, key).orElseThrow(() -> new IllegalStateException("Placement no longer exists")).incarnation();
        }
        private SchematicPlacementRecord current() {
            check.run();
            SchematicPlacementRecord record = owner.get(dimension, key).orElseThrow(() -> new IllegalStateException("Placement no longer exists"));
            if (!record.incarnation().equals(incarnation)) throw new IllegalStateException("Stale placement metadata target");
            return record;
        }
        private <V> MetadataStore<V> store(Function<OwnerMetadata, V> read, BiFunction<OwnerMetadata, V, OwnerMetadata> replace) {
            return new MetadataStore<>() {
                public V read() { return read.apply(current().metadata()); }
                public boolean update(UnaryOperator<V> transformation) {
                    current();
                    return owner.updateMetadata(dimension, key, incarnation,
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
