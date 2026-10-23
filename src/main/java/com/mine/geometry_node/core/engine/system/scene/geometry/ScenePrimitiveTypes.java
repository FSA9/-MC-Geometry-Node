package com.mine.geometry_node.core.engine.system.scene.geometry;

import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.core.engine.system.scene.property.SceneProperty;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.*;

/** Shared shape representation; geometry, regions and colliders choose their own category and runtime. */
public final class ScenePrimitiveTypes {
    public record Primitive(String typeId, Function<SceneObject, GeometryValue.Primitive> primitive) {}
    private static final Map<String, Primitive> TYPES = new LinkedHashMap<>();
    private ScenePrimitiveTypes() {}
    public static synchronized void register(String category, String name, List<SceneProperty> fields,
            Function<SceneObject, GeometryValue.Primitive> primitive, Consumer<SceneObject> validate) {
        String id = category + "/" + name;
        if (TYPES.containsKey(id)) throw new IllegalArgumentException("Duplicate scene primitive: " + id);
        SceneObjectTypes.register(new SceneObjectTypes.Type(id, "geometry_node.game_view.scene." + name, category, fields,
                o -> { var mesh = primitive.apply(o); Vec3 half = mesh.size().scale(.5);
                    Vec3 center = o.position().add(mesh.center()); return new AABB(center.subtract(half), center.add(half)); }, validate));
        TYPES.put(id, new Primitive(id, primitive));
    }
    public static Primitive find(String typeId) {
        SceneObjectTypes.all();
        synchronized (ScenePrimitiveTypes.class) { return TYPES.get(typeId); }
    }
}
