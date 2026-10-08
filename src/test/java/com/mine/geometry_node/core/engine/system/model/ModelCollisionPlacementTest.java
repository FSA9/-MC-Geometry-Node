package com.mine.geometry_node.core.engine.system.model;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.collision.*;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelCollisionPlacementTest {
    @TempDir Path cache;

    @Test void differentOrientationsReuseDiskGeometryWithoutReusingWorldOrientation() throws Exception {
        var primitive = new GltfAsset.Primitive(new float[]{0,0,0,3,0,0,3,1,0,0,1,0,0,0,2,3,0,2,3,1,2,0,1,2}, null,null,null,null,null,
                new int[]{0,2,1,0,3,2,4,5,6,4,6,7,0,1,5,0,5,4,3,7,6,3,6,2,0,4,7,0,7,3,1,2,6,1,6,5},
                null,GltfAsset.PrimitiveMode.TRIANGLES,GltfAsset.Bounds.empty());
        var asset = new GltfAsset("test",List.of(new GltfAsset.Scene("",List.of(0))),0,
                List.of(new GltfAsset.Node(0,-1,"",new Matrix4f(),0,List.of())),
                List.of(new GltfAsset.Mesh("",List.of(primitive))),List.of(),List.of(),
                new GltfAsset.Report("2.0","",List.of(),List.of(),List.of(),List.of(),List.of(),0,0,List.of()));
        String key = null;
        for (float angle : new float[]{0, 0.36f, 1.3f}) {
            var rotation = new Quaternionf().rotationXYZ(angle, angle * 0.7f, -angle);
            var transform = new ModelTransform(100, 20, -100, rotation.x, rotation.y, rotation.z, rotation.w, -2,3,4);
            var result = ModelCollisionCache.prepare(cache, asset,0,
                    new Matrix4f().scaling(transform.sx(),transform.sy(),transform.sz()),
                    ModelCollisionSettings.DEFAULT,new ModelCancellation());
            assertEquals(key != null, result.cacheHit());
            if (key != null) assertEquals(key, result.key());
            key = result.key();
            var exactRotation = new org.joml.Quaterniond(transform.qx(),transform.qy(),transform.qz(),transform.qw()).normalize();
            var placed = ModelCollisionTransforms.transform(result.mesh(), new org.joml.Matrix4d()
                    .rotation(exactRotation).scale(transform.sx(), transform.sy(), transform.sz()), new ModelCancellation());
            double[] before = result.mesh().parts().getFirst().vertices();
            double[] after = placed.parts().getFirst().vertices();
            for (int i = 0; i < before.length; i += 3) {
                Vector3d expected = exactRotation.transform(new Vector3d(before[i],before[i+1],before[i+2])
                        .mul(transform.sx(), transform.sy(), transform.sz()));
                assertEquals(expected.x, after[i], 1e-10);
                assertEquals(expected.y, after[i+1], 1e-10);
                assertEquals(expected.z, after[i+2], 1e-10);
            }
            for (var part : placed.parts()) {
                double[] planes = part.planes(), vertices = part.vertices();
                for (int p = 0; p < planes.length; p += 4)
                    for (int v = 0; v < vertices.length; v += 3)
                        assertTrue(planes[p]*vertices[v]+planes[p+1]*vertices[v+1]+planes[p+2]*vertices[v+2]-planes[p+3] <= 1e-7);
            }
        }
        try (var entries = java.nio.file.Files.list(cache)) { assertEquals(2, entries.count()); }
    }
}
