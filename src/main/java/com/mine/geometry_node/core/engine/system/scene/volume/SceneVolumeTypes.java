package com.mine.geometry_node.core.engine.system.scene.volume;

import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.engine.system.scene.geometry.ScenePrimitiveTypes;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneGeometryTypes;
import com.mine.geometry_node.core.engine.system.scene.property.SceneNodeInputs;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Authored volumes share primitive schemas and meshes, but retain separate runtime semantics. */
public final class SceneVolumeTypes {
    private SceneVolumeTypes() {}
    public static void register() {
        for (String shape : List.of("cube", "cylinder", "uv_sphere")) {
            ScenePrimitiveTypes.register("collision", shape, SceneNodeInputs.properties("collision/" + shape), SceneGeometryTypes::primitive,
                    o -> SceneCollisionRuntime.mesh(o));
            SceneTransforms.registerLocal("collision/" + shape, SceneCollisionTransforms::local);
        }
        // Analytic areas have no surface topology inputs. These constants only tessellate their editor helper.
        {
            ScenePrimitiveTypes.register("area", "cube", SceneNodeInputs.properties("area/cube"),
                    o -> GeometryValue.Primitive.cube(Vec3.ZERO, new Vec3(o.number("width"), o.number("height"), o.number("depth")), 2, 2, 2), SceneVolumeTypes::validate);
            ScenePrimitiveTypes.register("area", "cylinder", SceneNodeInputs.properties("area/cylinder"),
                    o -> GeometryValue.Primitive.cylinder(Vec3.ZERO, 16, 1, 1, (float)o.number("radius"), (float)o.number("height"), GeometryValue.CylinderFillType.NGON), SceneVolumeTypes::validate);
            ScenePrimitiveTypes.register("area", "uv_sphere", SceneNodeInputs.properties("area/uv_sphere"),
                    o -> GeometryValue.Primitive.uvSphere(Vec3.ZERO, 16, 8, (float)o.number("radius")), SceneVolumeTypes::validate);
        }
    }
    private static void validate(SceneObject object) {
        // Existing analytic region queries support spheres and circular cylinders, not ellipsoids.
        if (!object.typeId().startsWith("area/")) return;
        Vec3 s = object.scale();
        if (object.typeId().equals("area/uv_sphere") && (Math.abs(s.x-s.y)>1e-8 || Math.abs(s.x-s.z)>1e-8))
            throw new IllegalArgumentException("Sphere regions require uniform scale");
        if (object.typeId().equals("area/cylinder") && Math.abs(s.x-s.z)>1e-8)
            throw new IllegalArgumentException("Cylinder regions require matching X/Z scale");
    }
}
