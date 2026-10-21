package com.mine.geometry_node.core.engine.system.scene.volume;

import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.mine.geometry_node.core.engine.resource.residency.ResourceResidency;
import java.util.function.BiPredicate;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import java.util.*;

/** Shared by the authoritative server and client prediction; never consumes UI previews. */
public final class SceneCollisionRuntime {
    private final BiPredicate<Level, AABB> residency;
    public SceneCollisionRuntime() {
        this((level, bounds) -> ResourceResidency.isResident(level, bounds, null));
    }
    /** Explicit availability injection for isolated physics tests. */
    public SceneCollisionRuntime(BiPredicate<Level, AABB> residency) {
        this.residency = Objects.requireNonNull(residency);
    }
    public record Key(UUID id) {}
    private record Applied(SceneObject object, ModelCollisionScene.Prepared prepared) {}
    private final Map<UUID, Applied> applied = new LinkedHashMap<>();
    private record Shape(String type, Map<String,String> parameters, Vec3 rotation, Vec3 scale) {}
    private final Map<Shape, ModelCollisionScene.Prepared> shapes = new LinkedHashMap<>(32,.75f,true);
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
        var cached = shapes.get(key);
        if (cached != null) return cached;
        try {
            var prepared = ModelCollisionScene.prepare(mesh(object));
            shapes.put(key, prepared);
            while (shapes.size() > SceneDocument.MAX_OBJECTS) shapes.remove(shapes.keySet().iterator().next());
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
