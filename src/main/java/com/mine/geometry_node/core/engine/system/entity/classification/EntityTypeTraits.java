package com.mine.geometry_node.core.engine.system.entity.classification;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.*;

/** The deliberately bounded set of native entity-type tags exposed by the instance editor. */
public final class EntityTypeTraits {
    public static final List<String> IDS = List.of(
            "skeletons", "undead", "wither_friends", "zombies", "sensitive_to_smite", "sensitive_to_impaling",
            "powder_snow_walkable_mobs", "non_controlling_rider", "no_anger_from_wind_charge",
            "inverted_healing_and_harm", "immune_to_oozing", "immune_to_infested", "freeze_immune_entity_types",
            "freeze_hurts_extra_types", "fall_damage_immune", "can_float_while_ridden", "can_breathe_under_water",
            "burn_in_daylight", "arrows").stream().map(id -> "minecraft:" + id).toList();
    private static final Map<String, TagKey<EntityType<?>>> TAGS;
    static {
        Map<String, TagKey<EntityType<?>>> tags = new LinkedHashMap<>();
        IDS.forEach(id -> tags.put(id, TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(id))));
        TAGS = Collections.unmodifiableMap(tags);
    }
    private EntityTypeTraits() {}

    public static String labelKey(String id) {
        if (!TAGS.containsKey(id)) throw new IllegalArgumentException("Unsupported entity type tag");
        return "geometry_node.entity_editor.types." + id.substring("minecraft:".length());
    }
    public static Map<String, Boolean> validate(Map<?, ?> values) {
        Map<String, Boolean> checked = new LinkedHashMap<>();
        if (values.size() > IDS.size()) throw new IllegalArgumentException("Too many entity type tags");
        for (var entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String id) || !TAGS.containsKey(id)
                    || !(entry.getValue() instanceof Boolean value))
                throw new IllegalArgumentException("Unsupported entity type tag override");
            checked.put(id, value);
        }
        return Collections.unmodifiableMap(checked);
    }
    public static Map<String, Boolean> defaults(Entity entity) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        var nativeType = entity.getType().builtInRegistryHolder();
        TAGS.forEach((id, tag) -> values.put(id, nativeType.is(tag)));
        return Collections.unmodifiableMap(values);
    }
    public static EntityTypeTraitOverrides overrides(Entity entity) {
        return entity.hasData(GeometryNode.ENTITY_TYPE_TRAITS)
                ? entity.getData(GeometryNode.ENTITY_TYPE_TRAITS) : EntityTypeTraitOverrides.EMPTY;
    }
    /** Immutable replacement triggers NeoForge attachment synchronization, including after rollback. */
    public static void replace(Entity entity, EntityTypeTraitOverrides overrides) {
        entity.setData(GeometryNode.ENTITY_TYPE_TRAITS, overrides);
    }
}
