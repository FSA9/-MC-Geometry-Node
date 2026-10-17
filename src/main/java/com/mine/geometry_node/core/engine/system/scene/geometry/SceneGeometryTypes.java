package com.mine.geometry_node.core.engine.system.scene.geometry;

import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.Function;
import com.mine.geometry_node.core.engine.system.scene.property.SceneNodeInputs;

/** Parametric geometry plugins reuse the graph geometry primitives, without a graph instance. */
public final class SceneGeometryTypes {
    private SceneGeometryTypes(){}
    public static void register(){
        for (String shape : List.of("cube", "cylinder", "uv_sphere"))
            register(shape, SceneNodeInputs.properties("geometry/" + shape), SceneGeometryTypes::primitive);
    }
    public static synchronized void register(String name,List<SceneProperty> fields,Function<SceneObject,GeometryValue.Primitive> primitive){
        ScenePrimitiveTypes.register("geometry",name,fields,primitive,o->{});
    }
    /** CreateMesh inputs are interpreted identically by authored geometry and the composed collider. */
    public static GeometryValue.Primitive primitive(SceneObject object) {
        boolean collision = object.typeId().startsWith("collision/");
        Vec3 center = SceneNodeInputs.vector(object, "center");
        return switch (object.typeId().substring(object.typeId().indexOf('/') + 1)) {
            case "cube" -> GeometryValue.Primitive.cube(center,
                    new Vec3(object.number("width"), object.number("height"), object.number("depth")),
                    collision ? 2 : (int)object.number("vertices_x"), collision ? 2 : (int)object.number("vertices_y"), collision ? 2 : (int)object.number("vertices_z"));
            case "cylinder" -> GeometryValue.Primitive.cylinder(center, (int)object.number("segments"),
                    collision ? 1 : (int)object.number("side_segments"), collision ? 1 : (int)object.number("fill_segments"),
                    (float)object.number("radius"), (float)object.number("height"),
                    collision ? GeometryValue.CylinderFillType.NGON : GeometryValue.CylinderFillType.fromId(object.parameters().get("fill_type")));
            case "uv_sphere" -> GeometryValue.Primitive.uvSphere(center, (int)object.number("segments"),
                    (int)object.number("rings"), (float)object.number("radius"));
            default -> throw new IllegalArgumentException("Unsupported mesh primitive");
        };
    }
}
