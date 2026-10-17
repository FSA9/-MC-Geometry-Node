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
    public void prepareChanges(List<SceneChange> changes) {
        for (SceneChange change : changes) if (change.replacement() != null) SceneObjectTypes.validate(change.replacement());
        collisions.prepareChanges(changes);
    }
    public static AreaAddress address(ServerLevel level, UUID id) { return new AreaAddress(level.dimension(), "scene/" + id); }
    public void synchronize(ServerLevel level, List<SceneObject> objects) {
        collisions.synchronize(level, objects);
        Set<UUID> removed = new HashSet<>(areas.keySet());
        for (SceneObject object : objects) {
            if (!object.enabled() || !object.typeId().startsWith("area/")) continue;
            removed.remove(object.id());
            SceneObject previous = areas.get(object.id());
            if (object.equals(previous)) continue;
            if (previous != null && resolved(previous).equals(resolved(object))) {
                areas.put(object.id(), object);continue; // Names/colors must not restart area enter/exit state.
            }
            AreaResourceStore.INSTANCE.putAuthored(level.getServer(), AreaResource.authored(address(level, object.id()),
                    object.id(), object.revision(), level.getGameTime(), resolved(object)));
            areas.put(object.id(), object);
        }
        for (UUID id : removed) { AreaResourceStore.INSTANCE.removeAuthored(level.getServer(), address(level, id)); areas.remove(id); }
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
    }
}
