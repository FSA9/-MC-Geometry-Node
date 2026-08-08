package com.mine.geometry_node.core.engine.system.chunk_loading;

import com.mine.geometry_node.GeometryNode;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-wide source of truth for entity chunk-loading configuration.
 */
public final class EntityChunkLoadingStorage extends SavedData {
    private static final String TAG_CONFIGS = "Configs";
    private static final Codec<EntityChunkLoadingStorage> CODEC = CompoundTag.CODEC.xmap(
            EntityChunkLoadingStorage::load,
            storage -> storage.saveToTag(new CompoundTag())
    );

    public static final SavedDataType<EntityChunkLoadingStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "entity_chunk_loading"),
            EntityChunkLoadingStorage::new,
            CODEC
    );

    private final Map<UUID, EntityChunkLoadingConfig> configs = new LinkedHashMap<>();

    public static EntityChunkLoadingStorage get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public synchronized Optional<EntityChunkLoadingConfig> get(UUID entityId) {
        return Optional.ofNullable(configs.get(entityId));
    }

    public synchronized Collection<EntityChunkLoadingConfig> all() {
        return List.copyOf(configs.values());
    }

    public synchronized Optional<EntityChunkLoadingConfig> put(EntityChunkLoadingConfig config) {
        EntityChunkLoadingConfig previous = configs.put(config.entityId(), config);
        setDirty();
        return Optional.ofNullable(previous);
    }

    public synchronized Optional<EntityChunkLoadingConfig> remove(UUID entityId) {
        EntityChunkLoadingConfig removed = configs.remove(entityId);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    private CompoundTag saveToTag(CompoundTag root) {
        ListTag list = new ListTag();
        synchronized (this) {
            for (EntityChunkLoadingConfig config : configs.values()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("Entity", config.entityId().toString());
                tag.putString("Dimension", config.dimension().identifier().toString());
                tag.putInt("ChunkX", config.centerChunkX());
                tag.putInt("ChunkZ", config.centerChunkZ());
                tag.putInt("Radius", config.radius());
                list.add(tag);
            }
        }
        root.put(TAG_CONFIGS, list);
        return root;
    }

    private static EntityChunkLoadingStorage load(CompoundTag root) {
        EntityChunkLoadingStorage storage = new EntityChunkLoadingStorage();
        ListTag list = root.getListOrEmpty(TAG_CONFIGS);
        for (int index = 0; index < list.size(); index++) {
            readConfig(list.getCompoundOrEmpty(index)).ifPresent(config -> storage.configs.put(config.entityId(), config));
        }
        return storage;
    }

    private static Optional<EntityChunkLoadingConfig> readConfig(CompoundTag tag) {
        try {
            UUID entityId = UUID.fromString(tag.getStringOr("Entity", ""));
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.parse(tag.getStringOr("Dimension", ""))
            );
            return Optional.of(new EntityChunkLoadingConfig(
                    entityId,
                    dimension,
                    tag.getIntOr("ChunkX", 0),
                    tag.getIntOr("ChunkZ", 0),
                    tag.getIntOr("Radius", EntityChunkLoadingConfig.MIN_RADIUS)
            ));
        } catch (IllegalArgumentException exception) {
            GeometryNode.LOGGER.warn("Skipping invalid entity chunk-loading configuration: {}", exception.getMessage());
            return Optional.empty();
        }
    }
}
