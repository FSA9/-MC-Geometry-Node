package com.mine.geometry_node.core.engine.system.scene;

import com.mine.geometry_node.core.engine.system.scene.property.SceneProperty;

import com.mine.geometry_node.core.engine.system.scene.property.SceneInspectorSchemas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only ordinary entity envelope. Full native state remains in the server's opaque snapshot store. */
public final class SceneNativeEntities {
    public static final String TYPE_ID = "native/entity";
    public static final String ENTITY_TYPE = "entity_type";
    public static final String SNAPSHOT = "snapshot";
    private static final String NAME = "Entity";
    private static final Vec3 ONE = new Vec3(1, 1, 1);
    private static final List<String> BOUNDS_KEYS = List.of(
            "bounds_min_x", "bounds_min_y", "bounds_min_z", "bounds_max_x", "bounds_max_y", "bounds_max_z");

    private SceneNativeEntities() {}

    /** Riding hierarchies require a separate atomic lifecycle adapter, rather than incomplete copies. */
    public static boolean eligible(Entity entity) {
        return entity != null && !(entity instanceof Player) && !(entity instanceof Interaction)
                && !entity.isRemoved() && entity.isAlive() && !entity.isSpectator()
                && !entity.isPassenger() && !entity.isVehicle()
                && entity.getType().canSerialize() && entity.getType().canSummon();
    }

    public static SceneObject capture(Entity entity, UUID snapshotToken, long revision) {
        if (!eligible(entity)) throw new IllegalArgumentException("Entity does not support detached editing");
        return capture(entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.position(), entity.getBoundingBox(), snapshotToken, revision);
    }

    public static SceneObjectTypes.Type type() {
        List<SceneProperty> properties = new ArrayList<>();
        properties.add(new SceneProperty(ENTITY_TYPE, "geometry_node.node.get_entity_type", "minecraft:pig", text -> {
            Identifier id = Identifier.tryParse(text);
            if (id == null || !id.toString().equals(text)) throw new IllegalArgumentException("Invalid entity type");
        }));
        properties.add(new SceneProperty(SNAPSHOT, "geometry_node.game_view.scene.layer.entity",
                new UUID(0, 0).toString(), SceneNativeEntities::snapshotToken));
        for (String key : BOUNDS_KEYS) properties.add(SceneProperty.number(key, 0, -30_000_000, 30_000_000, false));
        return new SceneObjectTypes.Type(TYPE_ID, "geometry_node.game_view.scene.layer.entity", "native",
                properties, SceneNativeEntities::bounds, SceneNativeEntities::validate, SceneObjectTypes.Storage.NATIVE,
                false, SceneObjectTypes.HistoryPolicy.LIFECYCLE);
    }

    public static AABB bounds(SceneObject object) {
        Vec3 position = object.position();
        return new AABB(position.x + object.number(BOUNDS_KEYS.get(0)), position.y + object.number(BOUNDS_KEYS.get(1)),
                position.z + object.number(BOUNDS_KEYS.get(2)), position.x + object.number(BOUNDS_KEYS.get(3)),
                position.y + object.number(BOUNDS_KEYS.get(4)), position.z + object.number(BOUNDS_KEYS.get(5)));
    }

    public static void validate(SceneObject object) {
        if (!TYPE_ID.equals(object.typeId()) || !NAME.equals(object.name()) || !ONE.equals(object.color())
                || !object.enabled() || !Vec3.ZERO.equals(object.rotation()) || !ONE.equals(object.scale()))
            throw new IllegalArgumentException("Ordinary entity fields are read-only");
        if (object.parameters().size() != BOUNDS_KEYS.size() + 2)
            throw new IllegalArgumentException("Invalid ordinary entity fields");
        String entityType = object.parameters().get(ENTITY_TYPE);
        Identifier id = entityType == null ? null : Identifier.tryParse(entityType);
        if (id == null || !id.toString().equals(entityType)) throw new IllegalArgumentException("Invalid entity type");
        snapshotToken(object.parameters().get(SNAPSHOT));
        for (String key : BOUNDS_KEYS) {
            String value = object.parameters().get(key);
            if (value == null) throw new IllegalArgumentException("Missing entity bounds: " + key);
            double coordinate = Double.parseDouble(value);
            if (!Double.isFinite(coordinate) || Math.abs(coordinate) > 30_000_000)
                throw new IllegalArgumentException("Invalid entity bounds");
        }
        for (int axis = 0; axis < 3; axis++)
            if (object.number(BOUNDS_KEYS.get(axis)) > object.number(BOUNDS_KEYS.get(axis + 3)))
                throw new IllegalArgumentException("Inverted entity bounds");
    }

    /** No token or internal bounds fields are exposed as inspector controls. */
    public static SceneInspectorSchemas.Schema schema(SceneObject object) {
        validate(object);
        List<SceneInspectorSchemas.Field> fields = new ArrayList<>();
        fields.add(new SceneInspectorSchemas.Field(ENTITY_TYPE, Component.translatable("geometry_node.node.get_entity_type"),
                SceneInspectorSchemas.Kind.TEXT, List.of(), false, o -> o.parameters().get(ENTITY_TYPE), null));
        String[] axes = {"x", "y", "z"};
        for (int axis = 0; axis < axes.length; axis++) {
            int index = axis;
            fields.add(new SceneInspectorSchemas.Field("position." + axes[axis],
                    Component.translatable("geometry_node.port.xyz").append(" " + axes[axis].toUpperCase(java.util.Locale.ROOT)),
                    SceneInspectorSchemas.Kind.NUMBER, List.of(), false,
                    o -> Double.toString(index == 0 ? o.position().x : index == 1 ? o.position().y : o.position().z), null));
        }
        return new SceneInspectorSchemas.Schema(fields);
    }

    public static UUID snapshotToken(String text) {
        if (text == null) throw new IllegalArgumentException("Missing entity snapshot");
        UUID token = UUID.fromString(text);
        if (!token.toString().equals(text)) throw new IllegalArgumentException("Invalid entity snapshot token");
        return token;
    }

    private static SceneObject capture(UUID id, String entityType, Vec3 position, AABB bounds, UUID token, long revision) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(ENTITY_TYPE, entityType);
        parameters.put(SNAPSHOT, token.toString());
        double[] relative = {bounds.minX - position.x, bounds.minY - position.y, bounds.minZ - position.z,
                bounds.maxX - position.x, bounds.maxY - position.y, bounds.maxZ - position.z};
        // Subtracting different world positions produces tiny cancellation differences after a copy.
        // Micrometre precision is ample for selection bounds and keeps their immutable metadata stable.
        for (int i = 0; i < relative.length; i++) {
            double coordinate = Math.rint(relative[i] * 1_000_000) / 1_000_000;
            parameters.put(BOUNDS_KEYS.get(i), Double.toString(coordinate == 0 ? 0 : coordinate));
        }
        SceneObject object = new SceneObject(id, TYPE_ID, NAME, position, ONE, true, revision, parameters);
        validate(object);
        return object;
    }
}
