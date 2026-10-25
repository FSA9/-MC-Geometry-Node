package com.mine.geometry_node.core.engine.system.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntityFilter;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntityState;
import com.mine.geometry_node.core.engine.system.entityindex.ServerEntityIndexService;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectGraph;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectType;
import com.mine.geometry_node.core.engine.system.worldquery.provider.WorldObjectProvider;
import com.mine.geometry_node.core.engine.system.worldquery.provider.WorldObjectProviders;

import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.core.engine.system.scene.SceneSavedData;
import com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.schematic.SchematicProjectionService;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementStorage;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.light.GraphLightService;
import com.mine.geometry_node.core.engine.graph.collision.GeometryCollisionService;
import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.engine.graph.storage.ServerGraphRepository;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.*;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;
import java.util.*;
import com.mine.geometry_node.core.engine.metadata.*;
import com.mine.geometry_node.core.metadata.*;
import java.util.function.*;

final class BuiltinWorldObjectProviders {
    private static boolean initialized;
    private static final Map<String, BiFunction<WorldObjectProvider.Context, WorldObjectEntry, MetadataCapabilities.Access>> METADATA = new HashMap<>();
    private BuiltinWorldObjectProviders() {}
    static synchronized void init() {
        if (initialized) return;
        initialized = true;
        METADATA.put("scene", (c, e) -> SceneMetadataTargets.access(c.server(), e.dimension(), e.sceneObjectId()));
        METADATA.put("native", (c, e) -> {
            if (e.entityState() != null && !e.entityState().loaded()) return registeredSnapshotData(c, e);
            if (e.entityState() != null && !e.entityState().alive()) return null;
            ServerLevel level = level(c, e.dimension());
            var entity = level.getEntity(UUID.fromString(e.references().get("entityUuid")));
            if (entity == null || entity.isRemoved() || !entity.isAlive() || !Objects.equals(entity.getId(), e.entityId())) return null;
            var access = MetadataCapabilities.GLOBAL.resolve("geometry_node:entity", new EntityMetadataAdapter.Target(entity));
            // In-place player dimension transfer can keep the entity alive: the queried native identity includes its level.
            Integer expectedEntityId = e.entityId();
            Runnable current = () -> {
                if (entity.isRemoved() || !entity.isAlive() || entity.level() != level
                        || level.getEntity(entity.getUUID()) != entity || !Objects.equals(entity.getId(), expectedEntityId))
                    throw new IllegalStateException("Native world query target moved or was replaced");
            };
            TagAccess tags = access.requireTags();
            return new MetadataCapabilities.Access(new TagAccess(new MetadataStore<>() {
                public List<String> read() { current.run(); return tags.values(); }
                public boolean update(java.util.function.UnaryOperator<List<String>> change) {
                    current.run(); List<String> candidate = change.apply(tags.values()); current.run();
                    return tags.replace(candidate);
                }
            }, TagPolicy.EXACT), new DataAccess(new MetadataStore<>() {
                public Map<String, Object> read() { current.run(); return access.requireData().values(); }
                public boolean update(UnaryOperator<Map<String, Object>> change) {
                    current.run();
                    return access.requireData().update(values -> {
                        current.run(); Map<String, Object> candidate = change.apply(values); current.run(); return candidate;
                    });
                }
            }, GraphMetadataValuePolicy.scoped(entity.registryAccess())));
        });
        METADATA.put("schematic", (c, e) -> PlacementMetadataTargets.access(level(c, e.dimension()), e.references().get("placementKey")));
        BiFunction<WorldObjectProvider.Context, WorldObjectEntry, MetadataCapabilities.Access> graphMetadata = (c, e) ->
                RuntimeMetadataTargets.resolve(c.server(), GraphResourceReferences.parse(e.references().get("resourceId")));
        for (String id : List.of("geometry", "light", "projection", "area", "force_field")) METADATA.put(id, graphMetadata);
        METADATA.put("collision", (c, e) -> {
            if (e.references().containsKey("resourceId")) return graphMetadata.apply(c, e);
            ServerLevel level = level(c, e.dimension());
            return MetadataCapabilities.GLOBAL.resolve(RuntimeMetadataTargets.MODEL_COLLISION,
                    new RuntimeMetadataTargets.ModelCollisionTarget(level, WorldObjectEntries.modelCollisionId(e.references())));
        });
        register("scene", "persisted_all_dimensions", () -> SceneObjectTypes.all().stream()
                .filter(t -> t.storage() == SceneObjectTypes.Storage.AUTHORED)
                .map(t -> new WorldObjectType("scene", t.id(), t.labelKey())).toList(), (c, sink) ->
            SceneSavedData.get(c.server()).visitWorldQuery((dim, object) -> {
                if (!sink.scan()) return false;
                if (!c.acceptsDimension().test(dim)) return true;
                return sink.emit(new WorldObjectEntry(WorldObjectEntry.key("scene", dim, object.id().toString()),
                        "scene", object.typeId(), object.name(), dim, object.position(),
                        WorldObjectEntries.sceneBounds(object), object.tags(), "scene", "persistent",
                        object.id(), null, Map.of("enabled", Boolean.toString(object.enabled()))));
            }));
        register("collision", "server_collision_instances", () -> types("collision", "geometry_collision", "model_collision"), (c, sink) -> {
            for (ServerLevel level : c.levels()) {
                boolean[] continuing = {true};
                GeometryCollisionService.INSTANCE.visitWorldQuery(level, snapshot -> {
                    if (!sink.scan()) return continuing[0] = false;
                    return continuing[0] = sink.emit(WorldObjectEntries.withTags(WorldObjectEntries.graph("collision",
                            "geometry_collision", snapshot.id(), snapshot.position(), snapshot.bounds(),
                            GraphResourceIdCodec.encode(snapshot.id()), Map.of("enabled", Boolean.toString(snapshot.enabled()))),
                            snapshot.metadata().tagSnapshot()));
                });
                if (!continuing[0]) return;
                ModelCollisionScene.visitDefinitions(level, entry -> {
                    if (!sink.scan()) return continuing[0] = false;
                    // Authored and graph colliders have retained authoritative definitions above.
                    if (entry.instanceId() instanceof SceneCollisionRuntime.Key || entry.instanceId() instanceof GraphResourceId) return true;
                    if (!(entry.instanceId() instanceof String) && !(entry.instanceId() instanceof UUID)) return true;
                    return continuing[0] = sink.emit(WorldObjectEntries.modelCollision(dimension(level), entry));
                });
                if (!continuing[0]) return;
            }
        });
        register("geometry", "retained_server_geometry", () -> types("geometry", "geometry/mesh", "geometry/perfect_sphere", "geometry/perfect_cylinder"), (c, sink) -> {
            for (ServerLevel level : c.levels()) DebugRendererSessionManager.visitWorldQuery(level, entry -> {
                if (!sink.scan()) return false;
                // Debug owner resolves both live and last-known anchor poses into world coordinates.
                return sink.emit(entry);
            });
        });
        register("light", "server_graph_lights", () -> types("light", "light/point", "light/spot", "light/area"), (c, sink) -> {
            for (ServerLevel level : c.levels()) GraphLightService.INSTANCE.visitWorldQuery(level, snapshot -> {
                if (!sink.scan()) return false;
                var definition = snapshot.definition(); double radius = definition.range() + definition.emitterRadius();
                var pos = snapshot.position();
                return sink.emit(WorldObjectEntries.withTags(WorldObjectEntries.graph("light", "light/" + definition.type(), snapshot.id(), pos,
                        new AABB(pos, pos).inflate(radius), GraphResourceIdCodec.encode(snapshot.id()),
                        Map.of("enabled", Boolean.toString(definition.config().lifecycle().enabled()))),
                        GraphLightService.INSTANCE.metadata(level, snapshot.id()).requireTags().values()));
            });
        });
        register("projection", "active_server_projection_leases", () -> types("projection", "schematic_projection"), (c, sink) ->
            SchematicProjectionService.INSTANCE.visitWorldQuery(c.server(), snapshot -> {
                if (!sink.scan()) return false;
                if (!c.acceptsDimension().test(snapshot.dimension())) return true;
                return sink.emit(WorldObjectEntries.withTags(WorldObjectEntries.graph("projection", "schematic_projection", snapshot.id(),
                        snapshot.position(), snapshot.bounds(), GraphResourceIdCodec.encode(snapshot.id()), Map.of()),
                        SchematicProjectionService.INSTANCE.metadata(c.server(), snapshot.id()).requireTags().values()));
            }));
        register("schematic", "persisted_placement_records", () -> types("schematic", "schematic_placement"), (c, sink) -> {
            for (var record : SchematicPlacementStorage.get(c.server().overworld()).records()) {
                if (!sink.scan()) return;
                String dim = record.dimension().identifier().toString();
                AABB bounds = new AABB(record.boundsMin().getX(), record.boundsMin().getY(), record.boundsMin().getZ(),
                        record.boundsMax().getX() + 1.0, record.boundsMax().getY() + 1.0, record.boundsMax().getZ() + 1.0);
                if (!sink.emit(new WorldObjectEntry(WorldObjectEntry.key("schematic", dim, record.key()), "schematic", "schematic_placement",
                        "", dim, bounds.getCenter(), bounds, record.tags(), record.graphId(), "persistent", null, null,
                        Map.of("placementKey", record.key())))) return;
            }
        });
        register("area", "server_graph_areas", () -> types("area", "area/box", "area/sphere", "area/cylinder"), (c, sink) ->
            AreaResourceStore.INSTANCE.visitWorldQuery(c.server(), area -> {
                if (!sink.scan()) return false;
                ServerLevel level = findLevel(c, area.address().dimension()); if (level == null) return true;
                var resolved = area.resolveForQuery(level); if (resolved == null) return true;
                return sink.emit(WorldObjectEntries.withTags(WorldObjectEntries.graph("area", "area/" + resolved.shape().id(), area.owner(), resolved.center(),
                        WorldObjectEntries.rotatedBounds(resolved.center(), resolved.size(), resolved.rotation()), area.address().id(),
                        Map.of("areaId", area.address().id(), "incarnation", area.reference().incarnation().toString())),
                        AreaResourceStore.INSTANCE.metadataTags(c.server(), area.reference()).values()));
            }));
        register("force_field", "server_graph_force_fields", () -> types("force_field", "force_field"), (c, sink) ->
            ForceFieldResourceStore.INSTANCE.visitWorldQuery(c.server(), field -> {
                if (!sink.scan()) return false;
                ServerLevel level = findLevel(c, field.address().dimension()); if (level == null) return true;
                var area = AreaResourceStore.INSTANCE.get(c.server(), field.area()); if (area == null) return true;
                var resolved = area.resolveForQuery(level); if (resolved == null) return true;
                return sink.emit(WorldObjectEntries.withTags(WorldObjectEntries.graph("force_field", "force_field", field.owner(), resolved.center(),
                        WorldObjectEntries.rotatedBounds(resolved.center(), resolved.size(), resolved.rotation()), field.address().id(),
                        Map.of("forceFieldId", field.address().id(), "areaId", field.area().id())),
                        ForceFieldResourceStore.INSTANCE.metadata(c.server(), field.address()).requireTags().values()));
            }));
        register("native", "loaded_entities_and_registered_snapshots", () -> {
            List<WorldObjectType> result = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE)
                result.add(new WorldObjectType("native", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescriptionId()));
            return result;
        }, (c, sink) -> {
            if (c.entityFilter().scope() != WorldObjectEntityFilter.Scope.WORLD_OBJECTS) {
                boolean[] continuing = {true};
                // The directory merges by UUID across all server dimensions before applying this scope.
                ServerEntityIndexService.INSTANCE.visit(c.server(), indexed -> {
                    if (!sink.scan()) return continuing[0] = false;
                    WorldObjectEntry entry = indexed.entry();
                    if (!c.acceptsDimension().test(entry.dimension())) return true;
                    UUID uuid = UUID.fromString(entry.references().get("entityUuid"));
                    var state = new WorldObjectEntityState(uuid, indexed.loaded(), indexed.registered(), indexed.alive(),
                            indexed.capturedAtMillis(), indexed.revision(), indexed.factions());
                    Map<String, String> refs = new LinkedHashMap<>(entry.references());
                    if (!indexed.loaded() || !indexed.alive()) {
                        refs.remove(com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectTagEdits.TARGET_REFERENCE);
                        refs.remove(com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectDataEdits.TARGET_REFERENCE);
                    }
                    return continuing[0] = sink.emit(new WorldObjectEntry(entry.key(), entry.provider(), entry.type(), entry.name(),
                            entry.dimension(), entry.position(), entry.bounds(), entry.tags(), entry.source(), entry.lifetime(),
                            indexed.loaded() ? entry.sceneObjectId() : null, indexed.loaded() ? entry.entityId() : null,
                            refs, entry.graphs(), state));
                });
                if (!continuing[0]) return;
            }
            if (c.entityFilter().scope() == WorldObjectEntityFilter.Scope.ENTITIES) return;
            for (ServerLevel level : c.levels()) for (var entity : level.getAllEntities()) {
                if (!sink.scan()) return;
                if (entity.isRemoved() || !(entity instanceof net.minecraft.world.entity.Interaction)) continue;
                String dim = dimension(level);
                Map<String, String> references = new LinkedHashMap<>();
                references.put("entityUuid", entity.getUUID().toString());
                var attachment = entity.hasData(com.mine.geometry_node.GeometryNode.GRAPH_DATA_ATTACHMENT)
                        ? entity.getData(com.mine.geometry_node.GeometryNode.GRAPH_DATA_ATTACHMENT) : null;
                var graphs = attachment == null ? List.<WorldObjectGraph>of()
                        : WorldObjectGraphMetadata.collect(attachment.getBindings(),
                            id -> ServerGraphRepository.INSTANCE.getGraph(c.server(), id));
                if (!sink.emit(new WorldObjectEntry(WorldObjectEntry.key("native", "", entity.getUUID().toString()), "native",
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                        entity.hasCustomName() ? entity.getCustomName().getString() : "", dim,
                        entity.position(), entity.getBoundingBox(),
                        attachment == null ? List.of() : attachment.customTags(),
                        "", "loaded",
                        entity.getUUID(),
                        entity.getId(), references, graphs))) return;
            }
        });
    }
    private static MetadataCapabilities.Access registeredSnapshotData(WorldObjectProvider.Context context, WorldObjectEntry entry) {
        var state = entry.entityState();
        if (state == null || !state.registered()) return null;
        var index = ServerEntityIndexService.INSTANCE;
        var expected = index.record(context.server(), state.entityUuid()).orElse(null);
        if (expected == null || expected.revision() != state.revision()) return null;
        return new MetadataCapabilities.Access(null, new DataAccess(new MetadataStore<>() {
            public Map<String, Object> read() {
                var current = index.record(context.server(), state.entityUuid()).orElse(null);
                if (current == null || !current.equals(expected) || index.findLoaded(context.server(), state.entityUuid()).isPresent())
                    throw new IllegalStateException("Registered entity snapshot is no longer current");
                return current.metadata().data(context.server().registryAccess());
            }
            public boolean update(UnaryOperator<Map<String, Object>> change) {
                throw new UnsupportedOperationException("Registered entity snapshots are read only");
            }
        }, GraphMetadataValuePolicy.of(context.server().registryAccess())));
    }
    private static ServerLevel level(WorldObjectProvider.Context context, String dimension) {
        return context.levels().stream().filter(l -> dimension(l).equals(dimension)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Owner dimension unavailable"));
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
            public MetadataCapabilities.Access resolveMetadata(Context context, WorldObjectEntry entry) {
                var resolver = METADATA.get(id);
                return resolver == null ? null : resolver.apply(context, entry);
            }
            public void collect(Context context, Sink sink) { collect.accept(context, sink); }
        });
    }
}
