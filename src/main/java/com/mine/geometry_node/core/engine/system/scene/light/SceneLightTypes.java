package com.mine.geometry_node.core.engine.system.scene.light;

import com.mine.geometry_node.core.engine.graph.light.LightDefinition;
import com.mine.geometry_node.core.engine.spatial.SpatialTransform;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectCapabilities;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneTransforms;
import com.mine.geometry_node.core.engine.system.scene.property.SceneNodeInputs;
import com.mine.geometry_node.core.node.value.light.LightConfig;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/** Scene-owned light definitions; rendering and validation consume the same effective dimensions. */
public final class SceneLightTypes {
    private SceneLightTypes() {}

    public static boolean isLight(String typeId) {
        return SceneObject.POINT_LIGHT.equals(typeId) || SceneObject.SPOT_LIGHT.equals(typeId)
                || SceneObject.AREA_LIGHT.equals(typeId);
    }

    public static String lightType(String typeId) {
        return switch (typeId) {
            case SceneObject.POINT_LIGHT -> "point";
            case SceneObject.SPOT_LIGHT -> "spot";
            case SceneObject.AREA_LIGHT -> "area";
            default -> throw new IllegalArgumentException("Not a scene light: " + typeId);
        };
    }

    public static void register() {
        for (String type : List.of(SceneObject.POINT_LIGHT, SceneObject.SPOT_LIGHT, SceneObject.AREA_LIGHT)) {
            SceneObjectTypes.register(new SceneObjectTypes.Type(type, "geometry_node.game_view.scene." + type,
                    "light", SceneNodeInputs.properties(type), SceneLightTypes::helperBounds,
                    SceneLightTypes::definition));
            var operations = EnumSet.of(SceneObjectCapabilities.Operation.MOVE);
            if (!SceneObject.POINT_LIGHT.equals(type)) operations.add(SceneObjectCapabilities.Operation.ROTATE);
            if (SceneObject.AREA_LIGHT.equals(type)) operations.add(SceneObjectCapabilities.Operation.SCALE);
            SceneObjectCapabilities.register(type, operations, SceneObject::withTransform);
        }
    }

    private static AABB helperBounds(SceneObject object) {
        if (SceneObject.AREA_LIGHT.equals(object.typeId()))
            return AABB.ofSize(object.position(), object.number("area_width"), object.number("area_height"), .1);
        return AABB.ofSize(object.position(), 1, 1, 1);
    }

    /** XYZ scene rotation is converted to the shared light transform's negative-yaw YXZ degrees. */
    public static LightDefinition definition(SceneObject object) {
        String type = lightType(object.typeId());
        boolean area = "area".equals(type);
        var config = LightConfig.DEFAULT.withSourceVisible(Boolean.parseBoolean(object.parameters().get("source_visible")));
        if (area) config = config.withArea(new LightConfig.Area(
                (float) (object.number("area_width") * object.scale().x),
                (float) (object.number("area_height") * object.scale().y)));
        else config = config.withShadow(new LightConfig.Shadow(config.shadow().enabled(), (float) object.number("source_size")));
        return new LightDefinition(type, object.color(), object.intensity(),
                (float) (object.range() * (area ? object.scale().z : 1)),
                "spot".equals(type) ? (float) object.number("cone_angle") : 60,
                "spot".equals(type) ? (float) object.number("edge_softness") : .25f,
                object.position(), SpatialTransform.eulerDegrees(SceneTransforms.rotation(object.rotation())), config);
    }

    /** Conservative support includes the emitter as well as the attenuation range. */
    public static AABB influenceBounds(LightDefinition definition) {
        double radius = definition.range() + ("area".equals(definition.type()) ? definition.emitterRadius() : 0);
        return AABB.ofSize(definition.position(), radius * 2, radius * 2, radius * 2);
    }
}
