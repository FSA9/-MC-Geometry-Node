package com.mine.geometry_node.core.engine.system.scene.volume;

import com.mine.geometry_node.core.engine.graph.collision.GeometryCollisionMeshBuilder;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneDocument;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneTransforms;
import com.mine.geometry_node.core.engine.system.scene.geometry.ScenePrimitiveTypes;
import com.mine.geometry_node.core.engine.system.scene.property.SceneNodeInputs;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.Map;

/** CreateGeometryCollision's pivot/local/world transform, including its negative-Y degree convention. */
public final class SceneCollisionTransforms {
    private static final Vec3 FLIP_Y = new Vec3(1, -1, 1);
    private record Shape(String type, Map<String,String> parameters, Vec3 rotation, Vec3 scale) {}
    private static final Map<Shape, ModelCollisionMesh> MESHES = new LinkedHashMap<>(32, .75f, true);
    private SceneCollisionTransforms() {}
    public static Vec3 nodeAngles(Vec3 sceneRadians) { return SceneTransforms.yxzDegrees(sceneRadians).multiply(FLIP_Y); }
    public static Vec3 sceneAngles(Vec3 nodeDegrees) { return SceneTransforms.xyzRadians(nodeDegrees.multiply(FLIP_Y)); }
    public static Vec3 local(SceneObject object, Vec3 point) {
        Vec3 pivot = SceneNodeInputs.vector(object, "pivot");
        Vec3 angles = sceneAngles(SceneNodeInputs.vector(object, "local_rotation"));
        point = SceneTransforms.rotate(SceneTransforms.rotation(angles), point.subtract(pivot).multiply(object.scale()))
                .add(pivot).add(SceneNodeInputs.vector(object, "translation"));
        return SceneTransforms.rotate(SceneTransforms.rotation(object.rotation()), point);
    }
    /** Position is deliberately kept outside this local mesh to retain precision at large world coordinates. */
    public static ModelCollisionMesh mesh(SceneObject object) {
        if (!object.typeId().startsWith("collision/")) throw new IllegalArgumentException("Not an authored collider");
        Shape key = new Shape(object.typeId(), object.parameters(), object.rotation(), object.scale());
        // Validation, rendering and physics share successful immutable meshes. No world/object references are retained.
        synchronized (MESHES) {
            ModelCollisionMesh cached = MESHES.get(key);
            if (cached != null) return cached;
            ModelCollisionMesh mesh = GeometryCollisionMeshBuilder.build(
                    GeometryValue.of(ScenePrimitiveTypes.find(object.typeId()).primitive().apply(object)),
                    nodeAngles(object.rotation()), SceneNodeInputs.vector(object, "pivot"), SceneNodeInputs.vector(object, "translation"),
                    SceneNodeInputs.vector(object, "local_rotation"), object.scale());
            MESHES.put(key, mesh);
            while (MESHES.size() > SceneDocument.MAX_OBJECTS) MESHES.remove(MESHES.keySet().iterator().next());
            return mesh;
        }
    }
}
