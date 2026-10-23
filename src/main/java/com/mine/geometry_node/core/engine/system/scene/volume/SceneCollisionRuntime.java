package com.mine.geometry_node.core.engine.system.scene.volume;

import com.mine.geometry_node.core.engine.system.scene.SceneChange;
import com.mine.geometry_node.core.engine.system.scene.SceneDocument;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneTransforms;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.mine.geometry_node.core.engine.resource.residency.ResourceResidency;
import java.util.function.BiPredicate;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import java.util.*;
import java.time.Duration;
import com.mine.geometry_node.core.cache.*;

/** Shared by the authoritative server and client prediction; never consumes UI previews. */
public final class SceneCollisionRuntime {
    // One aggregate registration, with weak cache keys. Never retain runtimes or worlds globally.
    private static final Map<BoundedCache<?, ?>, Boolean> SHAPE_CACHES = new WeakHashMap<>();
    static {
        CacheRegistry.GLOBAL.register(new CacheDescriptor("geometry_node:scene_collision_shapes",
                "geometry_node.cache.scene_collision_shapes", CacheDescriptor.Domain.COMMON,
                CacheDescriptor.Storage.MEMORY, CacheDescriptor.Lifetime.WORLD), new ManagedCache() {
            public java.util.concurrent.CompletionStage<CacheStats> stats() {
                return CacheExecutors.submit(() -> CacheStats.memory(
                        cacheSnapshot().stream().mapToLong(BoundedCache::size).sum(), -1));
            }
            public java.util.concurrent.CompletionStage<Void> clear(CacheClearRequest request) {
                // Active colliders own their Prepared value independently; never clear applied here.
                return CacheExecutors.run(() -> cacheSnapshot().forEach(BoundedCache::clear));
            }
            public boolean supports(CacheClearRequest request) {
                return request.namespace() == null && request.reason() == CacheClearRequest.Reason.USER;
            }
        });
    }
    private static List<BoundedCache<?, ?>> cacheSnapshot() {
        synchronized (SHAPE_CACHES) { return List.copyOf(SHAPE_CACHES.keySet()); }
    }
    private final BiPredicate<Level, AABB> residency;
    public SceneCollisionRuntime() {
        this((level, bounds) -> ResourceResidency.isResident(level, bounds, null));
    }
    /** Explicit availability injection for isolated physics tests. */
    public SceneCollisionRuntime(BiPredicate<Level, AABB> residency) {
        this.residency = Objects.requireNonNull(residency);
        synchronized (SHAPE_CACHES) { SHAPE_CACHES.put(shapes, Boolean.TRUE); }
    }
    public record Key(UUID id) {}
    private record Applied(SceneObject object, ModelCollisionScene.Prepared prepared) {}
    private final Map<UUID, Applied> applied = new LinkedHashMap<>();
    private record Shape(String type, Map<String,String> parameters, Vec3 rotation, Vec3 scale) {}
    private final BoundedCache<Shape, ModelCollisionScene.Prepared> shapes = new BoundedCache<>(
            SceneDocument.MAX_OBJECTS, Long.MAX_VALUE, Duration.ZERO, ignored -> 1, ignored -> {});
    /** Same local mesh for physics, bounds checks, and editor helpers; position stays in the scene index. */
    public static ModelCollisionMesh mesh(SceneObject object) {
        return SceneCollisionTransforms.mesh(object);
    }
    /** Server preflight runs before SavedData mutation/acknowledgement; the prepared shape is reused on publish. */
    public void prepareChanges(List<SceneChange> changes) {
        for (SceneChange change : changes) {
            SceneObject object = change.replacement();
            if (object != null && object.enabled() && object.typeId().startsWith("collision/")) prepare(object);
        }
    }
    private ModelCollisionScene.Prepared prepare(SceneObject object) {
        Shape key = new Shape(object.typeId(), object.parameters(), object.rotation(), object.scale());
        long generation = shapes.generation();
        var cached = shapes.get(key);
        if (cached != null) return cached;
        try {
            var prepared = ModelCollisionScene.prepare(mesh(object));
            shapes.putIfCurrent(key, prepared, generation);
            return prepared;
        } catch (ModelProblem failure) {
            throw new IllegalArgumentException("Invalid authored collider: " + failure.getMessage(), failure);
        }
    }
    public void synchronize(Level level, List<SceneObject> objects) {
        Set<UUID> remaining = new HashSet<>(applied.keySet());
        for (SceneObject object : objects) {
            if (!object.enabled() || !object.typeId().startsWith("collision/")) continue;
            AABB bounds = SceneTransforms.bounds(object, SceneObjectTypes.require(object.typeId()).bounds().apply(object));
            if (!residency.test(level, bounds)) continue;
            remaining.remove(object.id());
            Applied previous = applied.get(object.id());
            if (previous != null && previous.object().equals(object)) continue;
            boolean sameShape = previous != null && previous.object().typeId().equals(object.typeId())
                    && previous.object().parameters().equals(object.parameters())
                    && previous.object().rotation().equals(object.rotation()) && previous.object().scale().equals(object.scale());
            var prepared = sameShape ? previous.prepared() : prepare(object);
            var p = object.position();
            if (!sameShape || !previous.object().position().equals(p))
                ModelCollisionScene.publish(level, new Key(object.id()), prepared, p.x, p.y, p.z);
            applied.put(object.id(), new Applied(object, prepared));
        }
        for (UUID id : remaining) { ModelCollisionScene.remove(level, new Key(id)); applied.remove(id); }
    }
    public void clear(Level level) {
        for (UUID id : applied.keySet()) ModelCollisionScene.remove(level, new Key(id));
        applied.clear();shapes.clear();
    }
}
