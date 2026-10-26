package com.mine.geometry_node.client.runtime.entity.appearance;

import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry metadata only; no level, proxy, preview snapshot or mutable render state is retained here. */
public final class EntityAppearanceCatalog {
    public record Entry(String targetTypeId, String descriptionId, boolean customAdapter) {}
    private static final Map<String, EntityAppearanceAdapter> ADAPTERS = new LinkedHashMap<>();

    private EntityAppearanceCatalog() {}

    /** Call on the client thread during setup, alongside the matching common schema registration. */
    public static void register(String targetTypeId, EntityAppearanceAdapter adapter) {
        EntityAppearanceSpec.of(targetTypeId);
        if (targetTypeId.isEmpty() || ADAPTERS.putIfAbsent(targetTypeId, Objects.requireNonNull(adapter)) != null)
            throw new IllegalArgumentException("Duplicate or empty client appearance adapter");
        ClientEntityAppearances.clear();
    }

    /** Candidates are verified against the actual detached instance and renderer when requested. */
    public static List<Entry> entries() {
        return BuiltInRegistries.ENTITY_TYPE.stream().filter(EntityAppearanceCatalog::candidate)
                .map(type -> {
                    String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                    return new Entry(id, type.getDescriptionId(), ADAPTERS.containsKey(id));
                }).sorted(Comparator.comparing(Entry::targetTypeId)).toList();
    }

    static boolean candidate(EntityType<?> type) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        return type.canSerialize() && type.canSummon()
                && !id.equals("minecraft:player") && !id.equals("minecraft:mannequin")
                && !id.equals("minecraft:interaction")
                && (DefaultAttributes.hasSupplier(type) || ADAPTERS.containsKey(id));
    }

    static EntityAppearanceAdapter adapter(String id) { return ADAPTERS.get(id); }
}
