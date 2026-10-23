package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapGeometry;
import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapMeshCounts;
import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapSources;
import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.core.engine.system.scene.SceneDocument;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneTransforms;
import com.mine.geometry_node.core.engine.system.scene.geometry.ScenePrimitiveTypes;
import com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime;
import com.mine.geometry_node.core.engine.system.scene.volume.SceneVolumeRuntime;
import com.mine.geometry_node.core.engine.resource.residency.ResourceResidency;
import com.mine.geometry_node.core.engine.graph.debug.CollisionDebugMeshFactory;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderShape;
import com.mine.geometry_node.core.engine.graph.debug.geometry.*;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.*;
import java.util.function.Predicate;

/** Authored objects use the existing debug world renderer and presentation-owned mesh styles.
 * The cache bakes rotation/scale only; translation and object color never rebake vertices. */
public final class SceneGeometryInputs {
    private record Key(String type, Map<String,String> parameters, Vec3 rotation, Vec3 scale,
                       ScenePresentations.Presentation presentation) {}
    private static final Map<Key,GeometryDebugElement> CACHE = new LinkedHashMap<>(32,.75f,true);
    private record WorldKey(Key shape, Vec3 position) {}
    private static final SnapCache<Key> SNAP_LOCAL = new SnapCache<>();
    private static final SnapCache<WorldKey> SNAP_WORLD = new SnapCache<>();
    static {
        com.mine.geometry_node.client.runtime.cache.ClientRuntimeCacheRegistration.register("runtime.scene_geometry", com.mine.geometry_node.core.cache.CacheDescriptor.Storage.MEMORY,
                () -> com.mine.geometry_node.core.cache.CacheStats.memory(CACHE.size() + SNAP_LOCAL.meshes.size() + SNAP_WORLD.meshes.size(), -1),
                SceneGeometryInputs::clearCachedGeometry);
    }

    private static void clearCachedGeometry() {
        CACHE.clear();
        SNAP_LOCAL.clear();
        SNAP_WORLD.clear();
    }

    private static long generation = Long.MIN_VALUE;
    private SceneGeometryInputs() {}

    /** Render-thread only. Neither cache retains worlds, objects, or unbounded mesh histories. */
    public static void beginWorld(long worldGeneration) {
        if (generation == worldGeneration) return;
        generation = worldGeneration;
        CACHE.clear();
        SNAP_LOCAL.clear();
        SNAP_WORLD.clear();
    }

    private static final class SnapCache<K> {
        private static final int MAX_ENTRIES = 128;
        // Count all topology records, not just vertices. Both caches together retain at most 131072.
        private static final long MAX_ELEMENTS = 65536;
        private final Map<K, ViewportSnapGeometry.Mesh> meshes = new LinkedHashMap<>(32, .75f, true);
        private long elements;

        ViewportSnapGeometry.Mesh get(K key) {
            return meshes.get(key);
        }

        void put(K key, ViewportSnapGeometry.Mesh mesh) {
            long cost = ViewportSnapMeshCounts.of(mesh).elements();
            if (cost > MAX_ELEMENTS) return;
            var previous = meshes.remove(key);
            if (previous != null) elements -= ViewportSnapMeshCounts.of(previous).elements();
            meshes.put(key, mesh);
            elements += cost;
            var iterator = meshes.entrySet().iterator();
            while (meshes.size() > MAX_ENTRIES || elements > MAX_ELEMENTS) {
                elements -= ViewportSnapMeshCounts.of(iterator.next().getValue()).elements();
                iterator.remove();
            }
        }

        void clear() {
            meshes.clear();
            elements = 0;
        }
    }

    public static List<PacketGeometryDebugSnapshot.Mesh> visibleMeshes() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            CACHE.clear();
            SNAP_LOCAL.clear();
            SNAP_WORLD.clear();
            generation = Long.MIN_VALUE;
            return List.of();
        }
        var snapshot = EditViewportRuntime.renderedScene();
        if (snapshot == null) snapshot = ClientSceneService.INSTANCE.confirmedSnapshot();
        beginWorld(snapshot.worldGeneration());
        if (!level.dimension().identifier().toString().equals(snapshot.dimension())) return List.of();
        return visibleMeshes(snapshot.objects(), ViewportVisualization.inEditorPass(), ViewportVisualization.current(),
                bounds -> ResourceResidency.isResident(level, bounds, null));
    }

    /** Residency gates world draw inputs only; selection and snap geometry still use retained definitions. */
    static List<PacketGeometryDebugSnapshot.Mesh> visibleMeshes(List<SceneObject> objects, boolean editorPass,
            ViewportVisualization.Snapshot visualization, Predicate<AABB> residency) {
        List<PacketGeometryDebugSnapshot.Mesh> result = new ArrayList<>();
        for (var object : objects) {
            if (!object.enabled()) continue;
            var presentation = ScenePresentations.presentation(object);
            if (presentation == null) continue;
            if (!editorPass && presentation.layer() != ViewportVisualization.Layer.GEOMETRY) continue;
            if (editorPass && !visualization.visible(presentation.layer())) continue;
            AABB bounds = switch (object.typeId()) {
                case "area/cube", "area/cylinder", "area/uv_sphere" -> SceneVolumeRuntime.resolved(object).bounds();
                default -> ViewportSceneMath.focusBounds(object);
            };
            if (!residency.test(bounds)) continue;
            var mesh = mesh(object);
            if (mesh != null) result.add(mesh);
        }
        return result;
    }

    /** One authored object produces one draw input; runtime area/collision debug excludes these objects. */
    static PacketGeometryDebugSnapshot.Mesh mesh(SceneObject object) {
        var mesh = localMesh(object);
        if (mesh == null) return null;
        var presentation = ScenePresentations.presentation(object);
        Vec3 p = object.position().add(mesh.center()), size = mesh.size(), rotation = mesh.rotation();
        return new PacketGeometryDebugSnapshot.Mesh(object.id().toString(), "authored_scene", mesh.type(),
                presentation.style().color(), presentation.style().showPoints() && mesh.showPoints(),
                p.x, p.y, p.z, size.x, size.y, size.z, rotation.x, rotation.y, rotation.z,
                mesh.vertices(), mesh.edges(), mesh.faces());
    }

    private static Key key(SceneObject object, ScenePresentations.Presentation presentation) {
        return new Key(object.typeId(), object.parameters(), object.rotation(), object.scale(), presentation);
    }

    private static GeometryDebugElement localMesh(SceneObject object) {
        var plugin = ScenePrimitiveTypes.find(object.typeId());
        var presentation = ScenePresentations.presentation(object);
        if (plugin == null || presentation == null) return null;
        Key key = key(object, presentation);
        GeometryDebugElement mesh = CACHE.get(key);
        if (mesh == null) {
            mesh = presentation.meshFactory().build(object, plugin.primitive().apply(object));
            CACHE.put(key, mesh);
            while (CACHE.size() > SceneDocument.MAX_OBJECTS) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return mesh;
    }

    /** Snap targets use the same transformed mesh as the visible debug geometry; render-thread only. */
    public static ViewportSnapGeometry.Mesh snapMesh(SceneObject object) {
        return snapMesh(object, new ViewportSnapSources.Collector());
    }

    /** Budget is checked before snap tessellation, closure hashing, and world-space allocation. */
    public static boolean captureSnapMesh(SceneObject object, ViewportSnapSources.Collector collector) {
        if (ScenePrimitiveTypes.find(object.typeId()) == null || ScenePresentations.presentation(object) == null) {
            return collector.addBox(ViewportSceneMath.bounds(object));
        }
        var mesh = snapMesh(object, collector);
        return mesh != null && collector.add(mesh);
    }

    private static ViewportSnapGeometry.Mesh snapMesh(SceneObject object, ViewportSnapSources.Collector collector) {
        if (!collector.canAdd(0, 0, 0)) return null;
        var presentation = ScenePresentations.presentation(object);
        if (presentation == null || ScenePrimitiveTypes.find(object.typeId()) == null) return null;
        Key shapeKey = key(object, presentation);
        WorldKey worldKey = new WorldKey(shapeKey, object.position());
        var world = SNAP_WORLD.get(worldKey);
        if (world != null) return ViewportSnapMeshCounts.of(world).fits(collector) ? world : null;

        var local = SNAP_LOCAL.get(shapeKey);
        if (local == null) {
            var input = localMesh(object);
            if (input.vertices().length == 0) {
                var primitive = ScenePrimitiveTypes.find(object.typeId()).primitive().apply(object);
                if (!ViewportSnapMeshCounts.primitive(primitive).fits(collector)) return null;
                input = primitiveMesh(object, primitive);
            }
            int vertexCount = input.vertices().length / 3;
            int edgeCount = input.edges().length / 2;
            if (!collector.canAdd(vertexCount, edgeCount, 0)) return null;
            int faceCount = 0;
            int[] indices = input.faces();
            for (int i = 0; i + 3 < indices.length; i += 4) {
                faceCount += indices[i + 3] != indices[i] && indices[i + 3] != indices[i + 2] ? 2 : 1;
                if (!collector.canAdd(vertexCount, edgeCount, faceCount)) return null;
            }
            local = snapTopology(input, vertexCount, edgeCount, faceCount);
            SNAP_LOCAL.put(shapeKey, local);
        }
        if (!ViewportSnapMeshCounts.of(local).fits(collector)) return null;
        world = translate(local, object.position());
        SNAP_WORLD.put(worldKey, world);
        return world;
    }

    private static ViewportSnapGeometry.Mesh snapTopology(GeometryDebugElement input, int vertexCount,
                                                          int edgeCount, int faceCount) {
        float[] positions = input.vertices();
        int[] edgeIndices = input.edges(), faceIndices = input.faces();
        var vertices = new ArrayList<Vec3>(vertexCount);
        var edges = new ArrayList<ViewportSnapGeometry.Edge>(edgeCount);
        var faces = new ArrayList<ViewportSnapGeometry.Triangle>(faceCount);
        for (int i = 0; i < positions.length; i += 3) {
            vertices.add(input.center().add(positions[i], positions[i + 1], positions[i + 2]));
        }
        for (int i = 0; i + 1 < edgeIndices.length; i += 2) {
            edges.add(new ViewportSnapGeometry.Edge(vertices.get(edgeIndices[i]), vertices.get(edgeIndices[i + 1])));
        }
        for (int i = 0; i + 3 < faceIndices.length; i += 4) {
            int a = faceIndices[i], b = faceIndices[i + 1], c = faceIndices[i + 2], d = faceIndices[i + 3];
            faces.add(new ViewportSnapGeometry.Triangle(vertices.get(a), vertices.get(b), vertices.get(c)));
            if (d != a && d != c) {
                faces.add(new ViewportSnapGeometry.Triangle(vertices.get(a), vertices.get(c), vertices.get(d)));
            }
        }
        return new ViewportSnapGeometry.Mesh(vertices, edges, faces);
    }

    private static ViewportSnapGeometry.Mesh translate(ViewportSnapGeometry.Mesh local, Vec3 position) {
        if (position.equals(Vec3.ZERO)) return local;
        var points = new IdentityHashMap<Vec3, Vec3>();
        var vertices = new ArrayList<Vec3>(local.vertices().size());
        var edges = new ArrayList<ViewportSnapGeometry.Edge>(local.edges().size());
        var faces = new ArrayList<ViewportSnapGeometry.Triangle>(local.faces().size());
        for (var vertex : local.vertices()) {
            Vec3 translated = vertex.add(position);
            points.put(vertex, translated);
            vertices.add(translated);
        }
        for (var edge : local.edges()) {
            edges.add(new ViewportSnapGeometry.Edge(points.get(edge.a()), points.get(edge.b())));
        }
        for (var face : local.faces()) {
            faces.add(new ViewportSnapGeometry.Triangle(points.get(face.a()), points.get(face.b()), points.get(face.c())));
        }
        // Translation preserves topology; never recompute closure in world coordinates.
        return new ViewportSnapGeometry.Mesh(vertices, edges, faces, local.closed());
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
