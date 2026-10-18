package com.mine.geometry_node.core.engine.system.worldquery;

import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.schematic.SchematicProjectionService;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementStorage;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.light.GraphLightService;
import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.*;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.*;

final class BuiltinWorldObjectProviders {
    private static boolean initialized;
    private BuiltinWorldObjectProviders() {}
    static synchronized void init() {
        if (initialized) return;
        initialized = true;
        register("scene", "persisted_all_dimensions", () -> SceneObjectTypes.all().stream()
                .filter(t -> t.storage() == SceneObjectTypes.Storage.AUTHORED)
                .map(t -> new WorldObjectType("scene", t.id(), t.labelKey())).toList(), (c, sink) ->
            SceneSavedData.get(c.server()).visitWorldQuery((dim, object) -> {
                if (!sink.scan()) return false;
                if (!c.acceptsDimension().test(dim)) return true;
                return sink.emit(new WorldObjectEntry(WorldObjectEntry.key("scene", dim, object.id().toString()),
                        "scene", object.typeId(), object.name(), dim, object.position(),
                        SceneObjectTypes.require(object.typeId()).bounds().apply(object), List.of(), "scene", "persistent",
                        object.id(), null, Map.of("enabled", Boolean.toString(object.enabled()))));
            }));
        register("collision", "server_collision_instances", () -> types("collision", "geometry_collision", "model_collision"), (c, sink) -> {
            for (ServerLevel level : c.levels()) ModelCollisionScene.visitWorldQuery(level, entry -> {
                if (!sink.scan()) return false;
                // Authored colliders already have a canonical scene identity and richer persisted metadata.
                if (entry.instanceId() instanceof SceneCollisionRuntime.Key) return true;
                var local = entry.geometry().bounds(); if (local == null) return true;
                Vec3 pos = new Vec3(entry.x(), entry.y(), entry.z()); AABB bounds = local.move(pos);
                if (entry.instanceId() instanceof GraphResourceId graph) {
                    if (entry.excludedEntity() != null && level.getEntity(entry.excludedEntity()) == null) return true;
                    return sink.emit(WorldObjectEntries.graph("collision", "geometry_collision", graph, pos, bounds,
                            GraphResourceIdCodec.encode(graph), Map.of()));
                }
                // The model bridge publishes stable String instance IDs; never invent an ID via Object.toString().
                if (!(entry.instanceId() instanceof String) && !(entry.instanceId() instanceof UUID)) return true;
                String id = entry.instanceId().toString(), dim = dimension(level);
                return sink.emit(new WorldObjectEntry(WorldObjectEntry.key("collision", dim, id), "collision", "model_collision",
                        "", dim, pos, bounds, List.of(), "", "runtime", null, null, Map.of("collisionInstanceId", id)));
            });
        });
        register("geometry", "retained_server_geometry", () -> types("geometry", "geometry/mesh", "geometry/perfect_sphere", "geometry/perfect_cylinder"), (c, sink) -> {
            for (ServerLevel level : c.levels()) DebugRendererSessionManager.visitWorldQuery(level, entry -> {
                if (!sink.scan()) return false;
                String anchorId = entry.references().get("anchorEntityUuid");
                if (anchorId == null) return sink.emit(entry);
                var anchor = level.getEntity(UUID.fromString(anchorId));
                if (anchor == null || anchor.isRemoved()) return true;
                return sink.emit(new WorldObjectEntry(entry.key(), entry.provider(), entry.type(), entry.name(), entry.dimension(),
                        entry.position().add(anchor.position()), entry.bounds().move(anchor.position()), entry.tags(), entry.source(),
                        entry.lifetime(), null, null, entry.references()));
            });
        });
        register("light", "server_graph_lights", () -> types("light", "light/point", "light/spot", "light/area"), (c, sink) -> {
            for (ServerLevel level : c.levels()) GraphLightService.INSTANCE.visitWorldQuery(level, snapshot -> {
                if (!sink.scan()) return false;
                var definition = snapshot.definition(); double radius = definition.range() + definition.emitterRadius();
                var pos = snapshot.position();
                return sink.emit(WorldObjectEntries.graph("light", "light/" + definition.type(), snapshot.id(), pos,
                        new AABB(pos, pos).inflate(radius), GraphResourceIdCodec.encode(snapshot.id()),
                        Map.of("enabled", Boolean.toString(definition.config().lifecycle().enabled()))));
            });
        });
        register("projection", "active_server_projection_leases", () -> types("projection", "schematic_projection"), (c, sink) ->
            SchematicProjectionService.INSTANCE.visitWorldQuery(c.server(), snapshot -> {
                if (!sink.scan()) return false;
                if (!c.acceptsDimension().test(snapshot.dimension())) return true;
                return sink.emit(WorldObjectEntries.graph("projection", "schematic_projection", snapshot.id(),
                        snapshot.position(), snapshot.bounds(), GraphResourceIdCodec.encode(snapshot.id()), Map.of()));
            }));
        register("schematic", "persisted_placement_records", () -> types("schematic", "schematic_placement"), (c, sink) -> {
            for (var record : SchematicPlacementStorage.get(c.server().overworld()).records()) {
                if (!sink.scan()) return;
                String dim = record.dimension().identifier().toString();
                AABB bounds = new AABB(record.boundsMin().getX(), record.boundsMin().getY(), record.boundsMin().getZ(),
                        record.boundsMax().getX() + 1.0, record.boundsMax().getY() + 1.0, record.boundsMax().getZ() + 1.0);
                if (!sink.emit(new WorldObjectEntry(WorldObjectEntry.key("schematic", dim, record.key()), "schematic", "schematic_placement",
                        "", dim, bounds.getCenter(), bounds, List.of(), record.graphId(), "persistent", null, null,
                        Map.of("placementKey", record.key())))) return;
            }
        });
        register("area", "server_graph_areas", () -> types("area", "area/box", "area/sphere", "area/cylinder"), (c, sink) ->
            AreaResourceStore.INSTANCE.visitWorldQuery(c.server(), area -> {
                if (!sink.scan()) return false;
                ServerLevel level = findLevel(c, area.address().dimension()); if (level == null) return true;
                var resolved = area.resolve(level); if (resolved == null) return true;
                return sink.emit(WorldObjectEntries.graph("area", "area/" + resolved.shape().id(), area.owner(), resolved.center(),
                        WorldObjectEntries.rotatedBounds(resolved.center(), resolved.size(), resolved.rotation()), area.address().id(),
                        Map.of("areaId", area.address().id(), "incarnation", area.reference().incarnation().toString())));
            }));
        register("force_field", "server_graph_force_fields", () -> types("force_field", "force_field"), (c, sink) ->
            ForceFieldResourceStore.INSTANCE.visitWorldQuery(c.server(), field -> {
                if (!sink.scan()) return false;
                ServerLevel level = findLevel(c, field.address().dimension()); if (level == null) return true;
                var area = AreaResourceStore.INSTANCE.get(c.server(), field.area()); if (area == null) return true;
                var resolved = area.resolve(level); if (resolved == null) return true;
                return sink.emit(WorldObjectEntries.graph("force_field", "force_field", field.owner(), resolved.center(),
                        WorldObjectEntries.rotatedBounds(resolved.center(), resolved.size(), resolved.rotation()), field.address().id(),
                        Map.of("forceFieldId", field.address().id(), "areaId", field.area().id())));
            }));
        register("native", "loaded_native_entities_only", () -> {
            List<WorldObjectType> result = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE)
                result.add(new WorldObjectType("native", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescriptionId()));
            return result;
        }, (c, sink) -> {
            for (ServerLevel level : c.levels()) for (var entity : level.getAllEntities()) {
                if (!sink.scan()) return;
                if (entity.isRemoved()) continue;
                String dim = dimension(level);
                if (!sink.emit(new WorldObjectEntry(WorldObjectEntry.key("native", "", entity.getUUID().toString()), "native",
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                        entity.hasCustomName() ? entity.getCustomName().getString() : "", dim,
                        entity.position(), entity.getBoundingBox(), entity.entityTags().stream().sorted().toList(), "", "loaded",
                        (entity instanceof net.minecraft.world.entity.Interaction || SceneNativeEntities.eligible(entity)) ? entity.getUUID() : null,
                        entity.getId(), Map.of("entityUuid", entity.getUUID().toString())))) return;
            }
        });
    }
    private static ServerLevel findLevel(WorldObjectProvider.Context context,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        for (ServerLevel level : context.levels()) if (level.dimension().equals(dimension)) return level;
        return null;
    }
    private static String dimension(ServerLevel level) { return level.dimension().identifier().toString(); }
    private static List<WorldObjectType> types(String provider, String... ids) {
        return Arrays.stream(ids).map(id -> new WorldObjectType(provider, id, "")).toList();
    }
    private static void register(String id, String coverage, Supplier<List<WorldObjectType>> types,
                                 BiConsumer<WorldObjectProvider.Context, WorldObjectProvider.Sink> collect) {
        WorldObjectProviders.register(new WorldObjectProvider() {
            public String id() { return id; }
            public String coverage() { return coverage; }
            public List<WorldObjectType> types() { return types.get(); }
            public void collect(Context context, Sink sink) { collect.accept(context, sink); }
        });
    }
}
