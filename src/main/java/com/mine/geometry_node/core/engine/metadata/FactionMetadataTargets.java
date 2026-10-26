package com.mine.geometry_node.core.engine.metadata;

import com.mine.geometry_node.core.engine.system.faction.service.ServerFactionService;
import com.mine.geometry_node.core.metadata.*;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.*;

/** Faction metadata belongs to the authored faction identity, never an entity membership snapshot. */
public final class FactionMetadataTargets {
    public static final String KIND = "geometry_node:faction";
    private FactionMetadataTargets() {}

    public static void register(MetadataCapabilities registry) {
        registry.register(KIND, Target.class, EnumSet.allOf(MetadataCapabilities.Capability.class), FactionMetadataTargets::resolve);
    }

    public static Target target(MinecraftServer server, UUID id) {
        Target target = new Target(Objects.requireNonNull(server), Objects.requireNonNull(id));
        target.current();
        return target;
    }

    public static MetadataCapabilities.Access access(MinecraftServer server, UUID id) {
        return MetadataCapabilities.GLOBAL.resolve(KIND, target(server, id));
    }

    public record Target(MinecraftServer server, UUID id) {
        public Target { Objects.requireNonNull(server); Objects.requireNonNull(id); }
        private OwnerMetadata current() {
            try { return ServerFactionService.INSTANCE.metadata(server, id); }
            catch (IOException failure) { throw new UncheckedIOException("Faction metadata storage failed", failure); }
        }
        private <V> MetadataStore<V> store(Function<OwnerMetadata, V> read, BiFunction<OwnerMetadata, V, OwnerMetadata> replace) {
            return new MetadataStore<>() {
                @Override public V read() { return read.apply(current()); }
                @Override public boolean update(UnaryOperator<V> transformation) {
                    try {
                        return ServerFactionService.INSTANCE.updateMetadata(server, id,
                                metadata -> replace.apply(metadata, transformation.apply(read.apply(metadata))));
                    } catch (IOException failure) { throw new UncheckedIOException("Faction metadata storage failed", failure); }
                }
            };
        }
    }

    private static MetadataCapabilities.Access resolve(Target target) {
        target.current();
        return new MetadataCapabilities.Access(
                new TagAccess(target.store(OwnerMetadata::tags, OwnerMetadata::withTags), TagPolicy.EXACT),
                new DataAccess(target.store(metadata -> metadata.data(target.server.registryAccess()),
                        (metadata, values) -> metadata.withData(values, target.server.registryAccess())),
                        GraphMetadataValuePolicy.of(target.server.registryAccess())));
    }
}
