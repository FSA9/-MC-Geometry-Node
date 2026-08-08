package com.mine.geometry_node.core.node.value.entity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.utils.EntityNbtCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Serializable spawn template captured from an existing entity.
 * Identity, transform and world relationships are deliberately excluded.
 */
public final class EntityTemplateValue {
    public static final String TYPE = "geometry_node:entity_template";
    public static final int VERSION = 1;
    public static final EntityTemplateValue EMPTY = new EntityTemplateValue("", new CompoundTag());

    private static final Gson GSON = new Gson();

    private final String entityTypeId;
    private final CompoundTag data;

    public EntityTemplateValue(String entityTypeId, CompoundTag data) {
        this.entityTypeId = normalizeTypeId(entityTypeId);
        this.data = normalizeData(data);
    }

    public String entityTypeId() {
        return entityTypeId;
    }

    public CompoundTag data() {
        return data.copy();
    }

    public boolean isEmpty() {
        return entityTypeId.isEmpty();
    }

    public static EntityTemplateValue capture(Entity entity) {
        if (entity == null) return EMPTY;
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return EMPTY;
        return new EntityTemplateValue(typeId.toString(), EntityNbtCompat.saveWithoutId(entity));
    }

    @Nullable
    public Entity create(Level level, Vec3 position) {
        if (level == null || isEmpty()) return null;
        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null) return null;

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (entityType == null || !entityType.canSerialize() || !entityType.canSummon()) return null;

        Entity entity = entityType.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) return null;

        try {
            EntityNbtCompat.load(entity, data.copy());
            Vec3 safePosition = position != null ? position : Vec3.ZERO;
            entity.snapTo(safePosition.x, safePosition.y, safePosition.z, 0.0f, 0.0f);
            entity.setDeltaMovement(Vec3.ZERO);
            return entity;
        } catch (RuntimeException exception) {
            entity.discard();
            return null;
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", TYPE);
        map.put("version", VERSION);
        map.put("entity_type", entityTypeId);
        map.put("data", data.toString());
        return map;
    }

    public String toJsonString() {
        return GSON.toJson(toMap());
    }

    public static EntityTemplateValue from(@Nullable Object value) {
        if (value == null) return EMPTY;
        if (value instanceof EntityTemplateValue template) return template;
        if (value instanceof Map<?, ?> map) return fromMap(map);
        if (value instanceof String string) {
            if (string.isBlank()) return EMPTY;
            try {
                JsonObject json = JsonParser.parseString(string).getAsJsonObject();
                Map<String, Object> map = GSON.fromJson(json, Map.class);
                return fromMap(map);
            } catch (Exception ignored) {
                return EMPTY;
            }
        }
        return EMPTY;
    }

    private static EntityTemplateValue fromMap(Map<?, ?> map) {
        Object marker = map.get("type");
        if (marker != null && !TYPE.equals(String.valueOf(marker))) return EMPTY;

        String entityType = map.containsKey("entity_type") ? String.valueOf(map.get("entity_type")) : "";
        Object rawData = map.get("data");
        if (entityType.isBlank() || rawData == null) return EMPTY;

        try {
            CompoundTag tag = rawData instanceof CompoundTag compound
                    ? compound.copy()
                    : TagParser.parseCompoundFully(String.valueOf(rawData));
            return new EntityTemplateValue(entityType, tag);
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    private static String normalizeTypeId(String value) {
        if (value == null || value.isBlank()) return "";
        Identifier id = Identifier.tryParse(value.trim());
        return id != null ? id.toString() : "";
    }

    private static CompoundTag normalizeData(CompoundTag source) {
        CompoundTag tag = source == null ? new CompoundTag() : source.copy();

        tag.remove("id");
        tag.remove("UUID");
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("Rotation");
        tag.remove("fall_distance");
        tag.remove("Fire");
        tag.remove("Air");
        tag.remove("OnGround");
        tag.remove("PortalCooldown");
        tag.remove("Passengers");
        tag.remove("leash");
        return tag;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EntityTemplateValue template)) return false;
        return entityTypeId.equals(template.entityTypeId) && data.equals(template.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityTypeId, data);
    }

    @Override
    public String toString() {
        return isEmpty() ? "" : entityTypeId;
    }
}
