package com.mine.geometry_node.client.runtime.model.runtime;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import java.io.IOException;
import java.util.*;

/** Immutable source counts computed once on the import worker, without copying vertex/index arrays. */
final class ModelGeometryStatistics {
    record Counts(long vertices, long triangles, long primitives) {
        Counts plus(Counts other) {
            return new Counts(Math.addExact(vertices, other.vertices), Math.addExact(triangles, other.triangles), Math.addExact(primitives, other.primitives));
        }
        Map<String,Object> report() { return Map.of("vertices",vertices,"triangles",triangles,"primitives",primitives); }
    }
    static final Counts ZERO = new Counts(0,0,0);
    private final Counts uniqueMeshes;
    private final List<Counts> scenes;
    private final int defaultScene;
    private final long sourceCpuGeometryBytes;
    private ModelGeometryStatistics(Counts uniqueMeshes, List<Counts> scenes, int defaultScene, long sourceCpuGeometryBytes) {
        this.uniqueMeshes=uniqueMeshes;this.scenes=List.copyOf(scenes);this.defaultScene=defaultScene;this.sourceCpuGeometryBytes=sourceCpuGeometryBytes;
    }
    static ModelGeometryStatistics prepare(GltfAsset asset, ModelCancellation token) throws IOException {
        List<Counts> meshes=new ArrayList<>(); Counts unique=ZERO;long cpu=0;
        for(var mesh:asset.meshes()) {
            Counts count=ZERO;
            for(var primitive:mesh.primitives()) {
                token.check();count=count.plus(new Counts(primitive.vertexCount(),primitive.indexCount()/3,1));cpu=Math.addExact(cpu,primitive.cpuBytes());
            }
            meshes.add(count);unique=unique.plus(count);
        }
        List<Counts> scenes=new ArrayList<>();
        for(var scene:asset.scenes()) {
            Counts count=ZERO;Deque<Integer> nodes=new ArrayDeque<>(scene.roots());Set<Integer> visited=new HashSet<>();
            while(!nodes.isEmpty()) {
                token.check();int index=nodes.removeFirst();
                if(!visited.add(index)) throw new IOException("duplicate/cyclic scene node in geometry statistics");
                var node=asset.nodes().get(index);if(node.mesh()>=0)count=count.plus(meshes.get(node.mesh()));nodes.addAll(node.children());
            }
            scenes.add(count);
        }
        return new ModelGeometryStatistics(unique,scenes,asset.defaultScene(),cpu);
    }
    Counts uniqueMeshes(){return uniqueMeshes;}
    Counts scene(int selected){int index=selected<0?defaultScene:selected;if(index<0&&!scenes.isEmpty())index=0;return index>=0&&index<scenes.size()?scenes.get(index):ZERO;}
    long sourceCpuGeometryBytes(){return sourceCpuGeometryBytes;}
    Map<String,Object> report(){return Map.of("sourceMeshes",uniqueMeshes.report(),"scenes",scenes.stream().map(Counts::report).toList(),
            "scope","source indexed vertices/triangles/primitives; scene counts include node mesh reuse, before BSP splits and GPU vertex expansion");}
}
