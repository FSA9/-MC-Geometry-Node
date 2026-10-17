package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.core.engine.graph.debug.DebugRenderStyle;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderShape;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugMeshFactory;
import com.mine.geometry_node.core.engine.system.scene.SceneNativeInteractions;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.world.phys.Vec3;
import java.util.function.Function;
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
    private static final Map<String, Function<SceneObject, PacketGeometryDebugSnapshot.Mesh>> DIAGNOSTICS = new LinkedHashMap<>();
    private static final Map<SceneObject, PacketGeometryDebugSnapshot.Mesh> DIAGNOSTIC_CACHE = new LinkedHashMap<>(16, .75f, true);
    static {
        registerCategory("geometry", new Presentation(ViewportVisualization.Layer.GEOMETRY, false));
        registerCategory("area", new Presentation(ViewportVisualization.Layer.AREA, false));
        registerCategory("collision", new Presentation(ViewportVisualization.Layer.COLLISION, false));
        register(SceneObject.POINT_LIGHT, new Presentation(ViewportVisualization.Layer.LIGHT, true));
        // Native geometry remains supplied by the existing interaction debug provider.
        register(SceneNativeInteractions.TYPE_ID, new Presentation(ViewportVisualization.Layer.INTERACTION, false));
        register(com.mine.geometry_node.core.engine.system.scene.SceneNativeEntities.TYPE_ID,
                new Presentation(ViewportVisualization.Layer.ENTITY, false));
        registerDiagnostic(SceneNativeInteractions.TYPE_ID, object -> {
            var box = SceneNativeInteractions.bounds(object);
            var mesh = GeometryDebugMeshFactory.buildShapeMesh(
                    new DebugRenderShape(object.id().toString(), "interaction", "box",
                            box.getCenter(), new Vec3(box.getXsize(), box.getYsize(), box.getZsize()),
                            Vec3.ZERO, DebugRenderChannel.INTERACTION.color()));
            var p = mesh.center(); var size = mesh.size(); var rotation = mesh.rotation();
            return new PacketGeometryDebugSnapshot.Mesh(
                    mesh.id(), mesh.graphId(), mesh.type(), mesh.color(), mesh.showPoints(), p.x, p.y, p.z,
                    size.x, size.y, size.z, rotation.x, rotation.y, rotation.z, mesh.vertices(), mesh.edges(), mesh.faces());
        });
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

    /** Native diagnostic shapes can reflect the same detached preview as the gizmo and inspector. */
    public static synchronized void registerDiagnostic(String type, Function<SceneObject, PacketGeometryDebugSnapshot.Mesh> factory) {
        if (DIAGNOSTICS.putIfAbsent(Objects.requireNonNull(type), Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate scene diagnostic: " + type);
    }
    public static synchronized PacketGeometryDebugSnapshot.Mesh diagnostic(SceneObject object) {
        var factory = DIAGNOSTICS.get(object.typeId());
        if (factory == null) return null;
        var cached = DIAGNOSTIC_CACHE.get(object);
        if (cached != null) return cached;
        var mesh = Objects.requireNonNull(factory.apply(object));
        DIAGNOSTIC_CACHE.put(object, mesh);
        while (DIAGNOSTIC_CACHE.size() > 256) DIAGNOSTIC_CACHE.remove(DIAGNOSTIC_CACHE.keySet().iterator().next());
        return mesh;
    }

    public static synchronized Presentation presentation(SceneObject object) {
        var explicit = TYPES.get(object.typeId());
        return explicit != null ? explicit : CATEGORIES.get(SceneObjectTypes.require(object.typeId()).category());
    }

    public static ViewportVisualization.Layer layer(SceneObject object) {
        var presentation = presentation(object);
        return presentation == null ? null : presentation.layer();
    }
    public static synchronized ViewportVisualization.Layer layer(String typeId) {
        var presentation = TYPES.get(typeId);
        if (presentation == null) presentation = CATEGORIES.get(SceneObjectTypes.require(typeId).category());
        return presentation == null ? null : presentation.layer();
    }

    public static boolean pointMarker(SceneObject object) {
        var presentation = presentation(object);
        return presentation != null && presentation.pointMarker();
    }

    public static synchronized boolean selectable(ViewportVisualization.Layer layer) {
        return CATEGORIES.values().stream().anyMatch(p -> p.layer() == layer)
                || TYPES.values().stream().anyMatch(p -> p.layer() == layer)
                || com.mine.geometry_node.client.runtime.game.selection.ViewportSelectionSources.selectable(layer);
    }
}
