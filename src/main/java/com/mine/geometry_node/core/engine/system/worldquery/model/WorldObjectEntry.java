package com.mine.geometry_node.core.engine.system.worldquery.model;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable metadata only. An empty name/tags/source means the owner supplies no such metadata. */
public record WorldObjectEntry(String key, String provider, String type, String name, String dimension,
                               Vec3 position, AABB bounds, List<String> tags, String source, String lifetime,
                               @Nullable UUID sceneObjectId, @Nullable Integer entityId,
                               Map<String, String> references, List<WorldObjectGraph> graphs,
                               @Nullable WorldObjectEntityState entityState) {
    public static final int MAX_TEXT = 8192;
    public static final int MAX_TAGS = 1024;
    public static final int MAX_REFERENCES = 16;
    public static final int MAX_GRAPHS = 256;
    /** Compatibility constructor for providers without entity state. */
    public WorldObjectEntry(String key, String provider, String type, String name, String dimension,
                            Vec3 position, AABB bounds, List<String> tags, String source, String lifetime,
                            @Nullable UUID sceneObjectId, @Nullable Integer entityId,
                            Map<String, String> references, List<WorldObjectGraph> graphs) {
        this(key, provider, type, name, dimension, position, bounds, tags, source, lifetime,
                sceneObjectId, entityId, references, graphs, null);
    }
    /** Compatibility constructor for providers without bound graph metadata. */
    public WorldObjectEntry(String key, String provider, String type, String name, String dimension,
                            Vec3 position, AABB bounds, List<String> tags, String source, String lifetime,
                            @Nullable UUID sceneObjectId, @Nullable Integer entityId, Map<String, String> references) {
        this(key, provider, type, name, dimension, position, bounds, tags, source, lifetime,
                sceneObjectId, entityId, references, List.of());
    }
    public WorldObjectEntry {
        for (String value : List.of(key, provider, type, name, dimension, source, lifetime)) validateText(value);
        if (key.isEmpty() || provider.isEmpty() || type.isEmpty() || dimension.isEmpty())
            throw new IllegalArgumentException("Missing world object identity");
        Objects.requireNonNull(position); Objects.requireNonNull(bounds);
        if (!finite(position.x, position.y, position.z, bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ)) throw new IllegalArgumentException("Nonfinite world object bounds");
        if (graphs.size() > MAX_GRAPHS) throw new IllegalArgumentException("Too many bound graphs");
        tags = List.copyOf(tags); references = Map.copyOf(references); graphs = List.copyOf(graphs);
        if (tags.size() > MAX_TAGS || references.size() > MAX_REFERENCES) throw new IllegalArgumentException("Too much metadata");
        tags.forEach(WorldObjectEntry::validateText);
        references.forEach((k, v) -> { validateText(k); validateText(v); });
    }
    public static void validateText(String text) {
        if (text == null || text.length() > MAX_TEXT) throw new IllegalArgumentException("World object text too long");
    }
    /** Registry ID represented by this native snapshot; item entries expose the carried item ID. */
    public String registryType() {
        if (!provider.equals("native")) return "";
        return type.equals("minecraft:item") ? references.getOrDefault("itemId", "") : type;
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    /** Conservative wire size, including UTF-8, count prefixes and optional fields. */
    public int estimatedBytes() {
        int size = 256;
        for (String value : List.of(key, provider, type, name, dimension, source, lifetime)) size += bytes(value);
        for (String value : tags) size += bytes(value);
        for (var entry : references.entrySet()) size += bytes(entry.getKey()) + bytes(entry.getValue());
        for (var graph : graphs) size += graph.estimatedBytes();
        if (entityState != null) size += 53 + 16 * entityState.factions().size();
        return size;
    }
    private static int bytes(String value) { return 5 + value.getBytes(StandardCharsets.UTF_8).length; }
    /** Length framing avoids collisions when a provider's own IDs contain delimiters. */
    public static String key(String provider, String dimension, String identity) {
        return provider.length() + ":" + provider + dimension.length() + ":" + dimension + identity;
    }
}
