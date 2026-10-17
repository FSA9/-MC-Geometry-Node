package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.core.engine.graph.debug.DebugRenderStyle;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Plugins own client appearance independently of schemas and editor property forms. */
public final class ScenePresentations {
    private static final Map<String, Presentation> TYPES = new LinkedHashMap<>();
    private static final Map<String, Presentation> CATEGORIES = new LinkedHashMap<>();
    static {
        registerCategory("geometry", new Presentation(ViewportVisualization.Layer.GEOMETRY, false));
        registerCategory("area", new Presentation(ViewportVisualization.Layer.AREA, false));
        registerCategory("collision", new Presentation(ViewportVisualization.Layer.COLLISION, false));
        register(SceneObject.POINT_LIGHT, new Presentation(ViewportVisualization.Layer.LIGHT, true));
    }
    private ScenePresentations() {}

    /** Builds a cached local mesh, including scale/rotation but excluding world translation. */
    @FunctionalInterface
    public interface MeshFactory {
        GeometryDebugElement build(SceneObject object, GeometryValue.Primitive primitive);
    }

    public record Presentation(ViewportVisualization.Layer layer, boolean pointMarker,
                               DebugRenderStyle style, MeshFactory meshFactory) {
        public Presentation {
            Objects.requireNonNull(layer);
            Objects.requireNonNull(style);
            Objects.requireNonNull(meshFactory);
        }

        public Presentation(ViewportVisualization.Layer layer, boolean pointMarker) {
            this(layer, pointMarker, switch (layer) {
                case AREA -> DebugRenderStyle.AREA;
                case COLLISION, MODEL_COLLISION -> DebugRenderStyle.COLLISION;
                default -> DebugRenderStyle.GEOMETRY;
            }, switch (layer) {
                case AREA -> SceneGeometryInputs::areaMesh;
                case COLLISION, MODEL_COLLISION -> SceneGeometryInputs::collisionMesh;
                default -> SceneGeometryInputs::primitiveMesh;
            });
        }
    }

    public static synchronized void register(String type, Presentation presentation) {
        if (TYPES.putIfAbsent(Objects.requireNonNull(type), Objects.requireNonNull(presentation)) != null)
            throw new IllegalArgumentException("Duplicate presentation: " + type);
    }

    public static synchronized void registerCategory(String category, Presentation presentation) {
        if (CATEGORIES.putIfAbsent(Objects.requireNonNull(category), Objects.requireNonNull(presentation)) != null)
            throw new IllegalArgumentException("Duplicate presentation category: " + category);
    }

    public static synchronized Presentation presentation(SceneObject object) {
        var explicit = TYPES.get(object.typeId());
        return explicit != null ? explicit : CATEGORIES.get(SceneObjectTypes.require(object.typeId()).category());
    }

    public static ViewportVisualization.Layer layer(SceneObject object) {
        var presentation = presentation(object);
        return presentation == null ? null : presentation.layer();
    }

    public static boolean pointMarker(SceneObject object) {
        var presentation = presentation(object);
        return presentation != null && presentation.pointMarker();
    }

    public static synchronized boolean selectable(ViewportVisualization.Layer layer) {
        return CATEGORIES.values().stream().anyMatch(p -> p.layer() == layer)
                || TYPES.values().stream().anyMatch(p -> p.layer() == layer);
    }
}
