package com.mine.geometry_node.core.engine.system.model;

import static org.junit.jupiter.api.Assertions.*;

import com.mine.geometry_node.core.engine.system.model.collision.*;
import com.mine.geometry_node.core.engine.system.model.io.*;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ModelCollisionFixtureTest {
    @TempDir Path cache;

    @Test
    void exportedFixturePreservesHolesObjectBoundariesAndSuccessfulComponents() throws Exception {
        var token = new ModelCancellation();
        var asset =
                new GltfImporter()
                        .load(
                                new FileModelResourceProvider(Path.of("docs/model")),
                                "collision-test.glb",
                                token);
        assertEquals(12, asset.nodes().size());
        var mesh =
                ModelCollisionBuilder.build(
                        asset, 0, new Matrix4f(), new ModelCollisionSettings(.5, .01), token);
        assertEquals(
                1,
                mesh.skippedTriangles(),
                "only the intentional open triangle is omitted: " + mesh.generationIssues());
        assertEquals(1, mesh.incompleteObjects());
        assertEquals(1, mesh.parts().stream().filter(p -> p.minZ() < 1 && p.maxX() < 4).count());
        assertEquals(
                4,
                mesh.parts().stream()
                        .filter(p -> p.minZ() < 1 && p.minX() >= 6 && p.maxX() < 10)
                        .count());
        assertTrue(contains(mesh, .18, 1, .1), "near railing gap filled");
        assertFalse(contains(mesh, 6.4, 1, .1), "wide railing gap preserved");
        assertFalse(contains(mesh, 1.5, 1, 6.25), "door opening preserved");
        assertFalse(contains(mesh, 7.5, 1.5, 7.5), "room stays hollow");
        assertTrue(contains(mesh, 6.3, .2, 13), "object after failed component remains present");
        assertFalse(contains(mesh, 13.1, .5, 12.5), "separate nodes must not merge");
        System.out.printf(
                "fixture: parts=%d skippedTriangles=%d incompleteObjects=%d%n",
                mesh.parts().size(), mesh.skippedTriangles(), mesh.incompleteObjects());
    }

    private static boolean contains(ModelCollisionMesh mesh, double x, double y, double z) {
        for (var part : mesh.parts()) {
            boolean inside = true;
            var planes = part.planes();
            for (int i = 0; i < planes.length; i += 4)
                if (planes[i] * x + planes[i + 1] * y + planes[i + 2] * z > planes[i + 3] + 1e-7) {
                    inside = false;
                    break;
                }
            if (inside) return true;
        }
        return false;
    }
}
