package com.mine.geometry_node.core.engine.system.scene.volume;

import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.engine.system.scene.geometry.ScenePrimitiveTypes;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.*;

/** Level-local derived runtime. SavedData remains authoritative; unchanged objects never rebuild. */
public final class SceneVolumeRuntime {
    private final SceneCollisionRuntime collisions = new SceneCollisionRuntime();
    private final Map<UUID, SceneObject> areas = new LinkedHashMap<>();
    private final Map<UUID, UUID> areaIncarnations = new HashMap<>();
    public void prepareChanges(List<SceneChange> changes) {
        for (SceneChange change : changes) if (change.replacement() != null) SceneObjectTypes.validate(change.replacement());
        collisions.prepareChanges(changes);
    }
    public static AreaAddress address(ServerLevel level, UUID id) { return new AreaAddress(level.dimension(), "scene/" + id); }
    public void synchronize(ServerLevel level, List<SceneObject> objects) {
        collisions.synchronize(level, objects);
        SceneDocument document = SceneSavedData.get(level.getServer()).document(level.dimension().identifier().toString());
        Set<UUID> removed = new HashSet<>(areas.keySet());
        for (SceneObject object : objects) {
            if (!object.enabled() || !object.typeId().startsWith("area/")) continue;
            removed.remove(object.id());
            SceneObject previous = areas.get(object.id());
            UUID incarnation = Objects.requireNonNull(document.incarnation(object.id()), "Authored area no longer exists");
            boolean sameIncarnation = incarnation.equals(areaIncarnations.get(object.id()));
            if (sameIncarnation && object.equals(previous)) continue;
            if (sameIncarnation && previous != null && resolved(previous).equals(resolved(object))) {
                areas.put(object.id(), object);continue; // Names/colors must not restart area enter/exit state.
            }
            AreaResourceStore.INSTANCE.putAuthored(level.getServer(), authoredResource(address(level, object.id()),
                    document, object, level.getGameTime()));
            areas.put(object.id(), object);
            areaIncarnations.put(object.id(), incarnation);
        }
        for (UUID id : removed) {
            AreaResourceStore.INSTANCE.removeAuthored(level.getServer(), address(level, id));
            areas.remove(id); areaIncarnations.remove(id);
        }
    }
    /** Scene object identity selects metadata; the document token selects the live area lifetime. */
    public static AreaResource authoredResource(AreaAddress address, SceneDocument document, SceneObject object, long gameTime) {
        UUID incarnation = Objects.requireNonNull(document.incarnation(object.id()), "Authored area no longer exists");
        return AreaResource.authored(address, object.id(), incarnation, object.revision(), gameTime, resolved(object));
    }
    public static AreaResource.Resolved resolved(SceneObject object) {
        AreaShape shape = switch (object.typeId()) {
            case "area/cube" -> AreaShape.BOX;
            case "area/cylinder" -> AreaShape.CYLINDER;
            case "area/uv_sphere" -> AreaShape.SPHERE;
            default -> throw new IllegalArgumentException("Not an authored area");
        };
        Vec3 size = ScenePrimitiveTypes.find(object.typeId()).primitive().apply(object).size().multiply(object.scale());
        // Existing region APIs take YXZ degrees; scene transforms use XYZ radians.
        Vector3d angles = SceneTransforms.rotation(object.rotation()).getEulerAnglesYXZ(new Vector3d());
        return new AreaResource.Resolved(shape, object.position(), size,
                new Vec3(Math.toDegrees(angles.x), Math.toDegrees(angles.y), Math.toDegrees(angles.z)));
    }
    public void clear(ServerLevel level) {
        collisions.clear(level);
        for (UUID id : areas.keySet()) AreaResourceStore.INSTANCE.removeAuthored(level.getServer(), address(level, id));
        areas.clear();
        areaIncarnations.clear();
    }
}
