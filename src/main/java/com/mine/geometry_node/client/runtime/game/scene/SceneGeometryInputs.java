package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.engine.system.scene.geometry.ScenePrimitiveTypes;
import com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime;
import com.mine.geometry_node.core.engine.graph.debug.CollisionDebugMeshFactory;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderShape;
import com.mine.geometry_node.core.engine.graph.debug.geometry.*;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.*;

/** Authored objects use the existing debug world renderer and presentation-owned mesh styles.
 * The cache bakes rotation/scale only; translation and object color never rebake vertices. */
public final class SceneGeometryInputs {
    private record Key(String type, Map<String,String> parameters, Vec3 rotation, Vec3 scale,
                       ScenePresentations.Presentation presentation) {}
    private static final Map<Key,GeometryDebugElement> CACHE = new LinkedHashMap<>(32,.75f,true);
    private static long generation = Long.MIN_VALUE;
    private SceneGeometryInputs() {}

    public static List<PacketGeometryDebugSnapshot.Mesh> visibleMeshes() {
        var level = Minecraft.getInstance().level;
        if (level == null) { CACHE.clear(); return List.of(); }
        var snapshot = EditViewportRuntime.renderedScene();
        if (snapshot == null) snapshot = ClientSceneService.INSTANCE.confirmedSnapshot();
        if (generation != snapshot.worldGeneration()) { generation = snapshot.worldGeneration(); CACHE.clear(); }
        if (!level.dimension().identifier().toString().equals(snapshot.dimension())) return List.of();
        List<PacketGeometryDebugSnapshot.Mesh> result = new ArrayList<>();
        for (var object : snapshot.objects()) {
            if (!object.enabled()) continue;
            var presentation = ScenePresentations.presentation(object);
            if (presentation == null) continue;
            if (!ViewportVisualization.inEditorPass() && presentation.layer() != ViewportVisualization.Layer.GEOMETRY) continue;
            if (ViewportVisualization.inEditorPass() && !ViewportVisualization.current().visible(presentation.layer())) continue;
            var mesh = mesh(object);
            if (mesh != null) result.add(mesh);
        }
        return result;
    }

    /** One authored object produces one draw input; runtime area/collision debug excludes these objects. */
    static PacketGeometryDebugSnapshot.Mesh mesh(SceneObject object) {
        var plugin = ScenePrimitiveTypes.find(object.typeId());
        var presentation = ScenePresentations.presentation(object);
        if (plugin == null || presentation == null) return null;
        Key key = new Key(object.typeId(), object.parameters(), object.rotation(), object.scale(), presentation);
        GeometryDebugElement mesh = CACHE.get(key);
        if (mesh == null) {
            mesh = presentation.meshFactory().build(object, plugin.primitive().apply(object));
            CACHE.put(key, mesh);
            while (CACHE.size() > SceneDocument.MAX_OBJECTS) CACHE.remove(CACHE.keySet().iterator().next());
        }
        Vec3 p = object.position().add(mesh.center()), size = mesh.size(), rotation = mesh.rotation();
        return new PacketGeometryDebugSnapshot.Mesh(object.id().toString(), "authored_scene", mesh.type(),
                presentation.style().color(), presentation.style().showPoints() && mesh.showPoints(),
                p.x, p.y, p.z, size.x, size.y, size.z, rotation.x, rotation.y, rotation.z,
                mesh.vertices(), mesh.edges(), mesh.faces());
    }

    static GeometryDebugElement primitiveMesh(SceneObject object, GeometryValue.Primitive primitive) {
        var mesh = GeometryDebugMeshFactory.buildPrimitiveMesh("scene", "scene", primitive, Vec3.ZERO);
        float[] vertices = mesh.vertices().clone();
        for (int i = 0; i < vertices.length; i += 3) {
            var v = SceneTransforms.local(object, new Vec3(vertices[i], vertices[i+1], vertices[i+2]));
            vertices[i] = (float) v.x; vertices[i+1] = (float) v.y; vertices[i+2] = (float) v.z;
        }
        return new GeometryDebugElement(mesh.id(), mesh.graphId(), mesh.type(), mesh.color(), mesh.showPoints(),
                SceneTransforms.local(object, mesh.center()), mesh.size(), Vec3.ZERO, vertices, mesh.edges(), mesh.faces());
    }

    static GeometryDebugElement collisionMesh(SceneObject object, GeometryValue.Primitive primitive) {
        // Physics, bounds and visualization share the full pivot/local/world transform contract.
        return CollisionDebugMeshFactory.buildMesh("scene", "scene", SceneCollisionRuntime.mesh(object));
    }

    static GeometryDebugElement areaMesh(SceneObject object, GeometryValue.Primitive primitive) {
        String shape = switch (primitive.type()) {
            case CUBE -> "box";
            case CYLINDER -> "cylinder";
            case UV_SPHERE -> "sphere";
        };
        // Scene TRS is XYZ radians; the existing analytic debug renderer expects YXZ degrees.
        Vector3d angles = SceneTransforms.rotation(object.rotation()).getEulerAnglesYXZ(new Vector3d());
        Vec3 rotation = new Vec3(Math.toDegrees(angles.x), Math.toDegrees(angles.y), Math.toDegrees(angles.z));
        return GeometryDebugMeshFactory.buildShapeMesh(new DebugRenderShape("scene", "scene", shape,
                SceneTransforms.local(object, primitive.center()), primitive.size().multiply(object.scale()),
                rotation, ScenePresentations.presentation(object).style().color()));
    }
}
