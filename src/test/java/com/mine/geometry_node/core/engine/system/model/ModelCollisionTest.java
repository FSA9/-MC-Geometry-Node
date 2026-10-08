package com.mine.geometry_node.core.engine.system.model;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.collision.*;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ModelCollisionTest {
    @TempDir Path cache;
    private static final float[] CUBE = {0,0,0,1,0,0,1,1,0,0,1,0,0,0,1,1,0,1,1,1,1,0,1,1};
    private static final int[] CUBE_TRIANGLES = {0,2,1,0,3,2,4,5,6,4,6,7,0,1,5,0,5,4,3,7,6,3,6,2,0,4,7,0,7,3,1,2,6,1,6,5};
    private record Shape(float[] vertices, int[] triangles) {}

    @Test void closedCubeIsOneSolidAndNearbyCubesMergeInThreeDimensions() throws Exception {
        var single = build(new Shape(CUBE, CUBE_TRIANGLES), 0);
        assertEquals(1, single.parts().size());
        assertTrue(contains(single, .5, .5, .5));
        assertEquals(1, volume(single), 1e-8);
        Shape pair = boxes(new double[][]{{0,0,0,1,1,1},{1.2,0,0,2.2,1,1}});
        var merged = build(pair, .5);
        assertEquals(1, merged.parts().size());
        assertTrue(contains(merged, 1.1, .5, .5));
        assertEquals(2, build(pair, .1).parts().size());
    }

    @Test void parallelClosedBoardsBridgeOnlyPermittedNormalGap() throws Exception {
        Shape pair = boxes(new double[][]{{0,0,0,1,.05,1},{0,.25,0,1,.30,1}});
        var merged = build(pair, .5);
        assertEquals(1, merged.parts().size());
        assertTrue(contains(merged, .5, .15, .5));
        var separate = build(pair, .1);
        assertEquals(2, separate.parts().size());
        assertFalse(contains(separate, .5, .15, .5));
    }

    @Test void touchingClosedBoxesNeverBecomeOpenOrLoseTheirInteriors() throws Exception {
        for (double[] next : new double[][]{{1,0,0,2,1,1},{1,1,0,2,2,1},{1,1,1,2,2,2}}) {
            var mesh = build(boxes(new double[][]{{0,0,0,1,1,1}, next}), 0);
            assertTrue(contains(mesh, .5, .5, .5));
            assertTrue(contains(mesh, (next[0]+next[3])*.5, (next[1]+next[4])*.5, (next[2]+next[5])*.5));
            assertEquals(2, volume(mesh), 1e-6, "shared face/edge/vertex is not a hole or extra solid");
        }
    }

    @Test void nestedOutwardAndInwardBoundariesProduceSolidWallsAndEmptyRoom() throws Exception {
        Shape shell = hollowBox();
        var mesh = build(shell, .5);
        assertFalse(contains(mesh, 2, 2, 2));
        assertTrue(contains(mesh, .5, 2, 2), "walls must have volume, not surface skins");
        assertEquals(56, volume(mesh), 1e-6);
    }

    @Test void inwardBoundaryTouchingBothCapsDoesNotFillThroughOpening() throws Exception {
        Shape outer = boxes(new double[][]{{0,0,0,4,4,1}});
        Shape inner = boxes(new double[][]{{1,1,0,3,3,1}});
        var mesh = build(combine(outer, new Shape(inner.vertices, reversed(inner.triangles))), 0);
        assertFalse(contains(mesh, 2, 2, .5));
        assertTrue(contains(mesh, .5, 2, .5));
        assertEquals(12, volume(mesh), 1e-6);
    }

    @Test void fittingErrorCannotEraseThinWallsAroundLargeEmptyRoom() throws Exception {
        Shape outer = boxes(new double[][]{{0,0,0,4,4,4}});
        Shape inner = boxes(new double[][]{{.001,.001,.001,3.999,3.999,3.999}});
        var mesh = build(combine(outer, new Shape(inner.vertices, reversed(inner.triangles))), 0);
        assertFalse(contains(mesh, 2, 2, 2), "surface proximity is not proof that a room is solid");
        assertTrue(contains(mesh, .0005, 2, 2));
        assertTrue(volume(mesh) < .2, "a thin enclosing wall must not become a 64-block filled room");
    }

    @Test void materialBoundariesDoNotSplitClosedCollisionTopology() throws Exception {
        var original = asset(new Shape(CUBE, CUBE_TRIANGLES));
        var split = new GltfAsset("split", original.scenes(), 0, original.nodes(),
                List.of(new GltfAsset.Mesh("", List.of(primitive(CUBE, Arrays.copyOfRange(CUBE_TRIANGLES, 0, 18)),
                        primitive(CUBE, Arrays.copyOfRange(CUBE_TRIANGLES, 18, 36))))),
                List.of(), List.of(), original.report());
        var mesh = ModelCollisionBuilder.build(split, 0, new Matrix4f(), ModelCollisionSettings.DEFAULT, new ModelCancellation());
        assertEquals(1, mesh.parts().size());
        assertTrue(contains(mesh, .5, .5, .5));
    }

    @Test void inwardObjectIsSkippedWithoutSubtractingFromAnotherObject() throws Exception {
        int[] inward = reversed(CUBE_TRIANGLES);
        assertThrows(IOException.class, () -> build(new Shape(CUBE, inward), 0));
        var original = asset(new Shape(CUBE, CUBE_TRIANGLES));
        var nodes = List.of(new GltfAsset.Node(0, -1, "outer", new Matrix4f().scale(4), 0, List.of()),
                new GltfAsset.Node(1, -1, "inner", new Matrix4f().translation(1, 1, 1).scale(2), 1, List.of()));
        var nested = new GltfAsset("nested", List.of(new GltfAsset.Scene("", List.of(0, 1))), 0, nodes,
                List.of(original.meshes().getFirst(), new GltfAsset.Mesh("inner", List.of(primitive(CUBE, inward)))),
                List.of(), List.of(), original.report());
        var result = ModelCollisionBuilder.build(nested, 0, new Matrix4f(), ModelCollisionSettings.DEFAULT, new ModelCancellation());
        assertTrue(contains(result, 2, 2, 2));
        assertTrue(contains(result, .5, 2, 2));
        assertEquals(64, volume(result), 1e-6);
        assertEquals(12, result.skippedTriangles());
        assertEquals(1, result.incompleteObjects());
    }

    @Test void mirroredNonuniformNodeTransformKeepsClosedVolume() throws Exception {
        var mesh = ModelCollisionBuilder.build(asset(new Shape(CUBE, CUBE_TRIANGLES)), 0,
                new Matrix4f().scale(-2, 3, 4), new ModelCollisionSettings(0, .0001), new ModelCancellation());
        assertEquals(1, mesh.parts().size());
        assertTrue(contains(mesh, -1, 1.5, 2));
        assertFalse(contains(mesh, 1, 1.5, 2));
        assertEquals(24, volume(mesh), 1e-6);
    }

    @Test void inclinedClosedRampPreservesSlopeAndInterior() throws Exception {
        float[] ramp = {0,0,0,2,0,0,2,1,0,0,0,2,2,0,2,2,1,2};
        int[] triangles = {0,2,1,3,4,5,0,1,4,0,4,3,1,2,5,1,5,4,2,0,3,2,3,5};
        var mesh = build(new Shape(ramp, triangles), 0);
        assertEquals(1, mesh.parts().size());
        assertTrue(contains(mesh, 1, .2, 1));
        assertFalse(contains(mesh, 1, .7, 1));
        assertEquals(2, volume(mesh), 1e-6);
        boolean slope = false;
        double[] planes = mesh.parts().getFirst().planes();
        for (int i = 0; i < planes.length; i += 4)
            slope |= Math.abs(Math.abs(planes[i]) - 1 / Math.sqrt(5)) < 1e-7
                    && Math.abs(Math.abs(planes[i+1]) - 2 / Math.sqrt(5)) < 1e-7;
        assertTrue(slope, "inclined faces must remain planes, not voxel steps");
    }

    @Test void railingBarsRemainSolidWithoutFillingWideGaps() throws Exception {
        double[][] bars = new double[20][];
        for (int i = 0; i < bars.length; i++) bars[i] = new double[]{i*2,0,0,i*2+.2,3,.2};
        var mesh = build(boxes(bars), .5);
        assertEquals(bars.length, mesh.parts().size());
        for (int i = 0; i < bars.length; i++) {
            assertTrue(contains(mesh, i*2+.1, 1.5, .1));
            assertFalse(contains(mesh, i*2+1, 1.5, .1));
        }
    }

    @Test void nearbyRailingBarsBecomeOneClosedBox() throws Exception {
        double[][] bars = new double[20][];
        for (int i = 0; i < bars.length; i++) bars[i] = new double[]{i*.3,0,0,i*.3+.1,1,.1};
        var merged = build(boxes(bars), .5);
        assertEquals(1, merged.parts().size());
        assertEquals(6, merged.parts().getFirst().faceCount());
        assertEquals(8, merged.parts().getFirst().vertexCount());
        for (int i = 0; i < bars.length; i++) assertTrue(contains(merged, i*.3+.05, .5, .05));
        assertTrue(contains(merged, .2, .5, .05));
        assertFalse(contains(merged, 2, 1.1, .05));
        assertEquals(20, build(boxes(bars), .1).parts().size());
    }

    @Test void closedDoorFrameKeepsOpeningAndWallInteriors() throws Exception {
        var mesh = build(boxes(new double[][]{{0,0,0,1,4,.5},{3,0,0,4,4,.5},{1,3,0,3,4,.5}}), .5);
        assertTrue(contains(mesh, .5, 1.5, .25));
        assertTrue(contains(mesh, 2, 3.5, .25));
        assertFalse(contains(mesh, 2, 1.5, .25));
        assertEquals(5, volume(mesh), 1e-6);
    }

    @Test void partialOpenSourceRetainsClosedComponentsAndCachesOmissions() throws Exception {
        Shape cube = new Shape(CUBE, CUBE_TRIANGLES);
        Shape mixed = combine(cube, new Shape(new float[]{3,0,0,4,0,0,3,1,0}, new int[]{0,1,2}));
        var partial = build(mixed, 0);
        assertTrue(contains(partial, .5, .5, .5));
        assertEquals(1, partial.skippedTriangles());
        assertEquals(1, partial.incompleteObjects());
        var first = ModelCollisionCache.prepare(cache, asset(mixed), 0, new Matrix4f(),
                ModelCollisionSettings.DEFAULT, new ModelCancellation());
        var hit = ModelCollisionCache.prepare(cache, asset(mixed), 0, new Matrix4f(),
                ModelCollisionSettings.DEFAULT, new ModelCancellation());
        assertTrue(hit.cacheHit());
        assertEquals(first.mesh().skippedTriangles(), hit.mesh().skippedTriangles());
        assertTrue(Files.readString(cache.resolve(hit.key() + ".json")).contains("\"complete\": false"));
        assertThrows(IOException.class, () -> build(new Shape(CUBE, Arrays.copyOfRange(CUBE_TRIANGLES, 6, 36)), 0));
    }

    @Test void fittingErrorControlsCertifiedSmallCrackRepair() throws Exception {
        float[] cracked = Arrays.copyOf(CUBE, 27);
        cracked[25] = .004f;
        int[] triangles = CUBE_TRIANGLES.clone(); triangles[0] = 8;
        var source = asset(new Shape(cracked, triangles));
        assertThrows(IOException.class, () -> ModelCollisionBuilder.build(source, 0, new Matrix4f(),
                new ModelCollisionSettings(0, .0001), new ModelCancellation()));
        var repaired = ModelCollisionBuilder.build(source, 0, new Matrix4f(),
                new ModelCollisionSettings(0, .02), new ModelCancellation());
        assertEquals(1, repaired.parts().size());
        assertTrue(contains(repaired, .5, .5, .5));
        assertEquals(1, volume(repaired), 1e-6);
    }

    @Test void largeSeparatedComponentsAreAllIncludedRegardlessOfTriangleOrder() throws Exception {
        double[][] cubes = new double[96][];
        for (int i = 0; i < cubes.length; i++) cubes[i] = new double[]{i*2,0,0,i*2+1,1,1};
        Shape geometry = boxes(cubes);
        Random random = new Random(37);
        for (int i = geometry.triangles.length / 3 - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            for (int j = 0; j < 3; j++) {
                int swap = geometry.triangles[i*3+j];
                geometry.triangles[i*3+j] = geometry.triangles[other*3+j];
                geometry.triangles[other*3+j] = swap;
            }
        }
        var mesh = build(geometry, .5);
        assertEquals(cubes.length, mesh.parts().size());
        for (int i = 0; i < cubes.length; i++) assertTrue(contains(mesh, i*2+.5, .5, .5));
        assertEquals(cubes.length, volume(mesh), 1e-6);
    }

    @Test void cacheIsContentAddressedAndCorruptPayloadRebuilds() throws Exception {
        var asset = asset(new Shape(CUBE, CUBE_TRIANGLES));
        var first = ModelCollisionCache.prepare(cache, asset, 0, new Matrix4f(), ModelCollisionSettings.DEFAULT, new ModelCancellation());
        var second = ModelCollisionCache.prepare(cache, asset, 0, new Matrix4f(), ModelCollisionSettings.DEFAULT, new ModelCancellation());
        assertFalse(first.cacheHit()); assertTrue(second.cacheHit()); assertEquals(first.key(), second.key());
        assertTrue(first.stageNanos().containsKey("volumeGeneration"));
        assertTrue(first.stageNanos().containsKey("convexMerge"));
        assertEquals(java.util.Set.of("contentHash", "cacheRead"), second.stageNanos().keySet());
        assertTrue(first.stageNanos().values().stream().allMatch(nanos -> nanos >= 0));
        assertTrue(contains(second.mesh(), .5, .5, .5));
        var scaled = ModelCollisionCache.prepare(cache, asset, 0, new Matrix4f().scale(2), ModelCollisionSettings.DEFAULT, new ModelCancellation());
        assertNotEquals(first.key(), scaled.key()); assertEquals(1, volume(scaled.mesh()), 1e-6);
        var placed = ModelCollisionTransforms.transform(scaled.mesh(), new org.joml.Matrix4d().scale(-2, 3, 4), new ModelCancellation());
        assertEquals(24, volume(placed), 1e-6);
        assertTrue(contains(placed, -1, 1.5, 2));
        var changedQuality = ModelCollisionCache.prepare(cache, asset, 0, new Matrix4f(),
                new ModelCollisionSettings(.25, .02), new ModelCancellation());
        assertNotEquals(first.key(), changedQuality.key());
        byte[] data = Files.readAllBytes(cache.resolve(first.key()+".mcol")); data[data.length-1] ^= 1;
        Files.write(cache.resolve(first.key()+".mcol"), data);
        var rebuilt = ModelCollisionCache.prepare(cache, asset, 0, new Matrix4f(), ModelCollisionSettings.DEFAULT, new ModelCancellation());
        assertFalse(rebuilt.cacheHit()); assertTrue(rebuilt.cacheFailure().contains("rejected"));
        assertTrue(contains(rebuilt.mesh(), .5, .5, .5));
    }

    @Test void cancelledGenerationNeverProducesPartialCollider() {
        ModelCancellation token = new ModelCancellation(); token.cancel();
        assertThrows(IOException.class, () -> ModelCollisionBuilder.build(asset(new Shape(CUBE, CUBE_TRIANGLES)), 0,
                new Matrix4f(), ModelCollisionSettings.DEFAULT, token));
    }

    private static Shape hollowBox() {
        Shape outer = boxes(new double[][]{{0,0,0,4,4,4}}), inner = boxes(new double[][]{{1,1,1,3,3,3}});
        return combine(outer, new Shape(inner.vertices, reversed(inner.triangles)));
    }
    private static int[] reversed(int[] indices) {
        int[] result = indices.clone();
        for (int i = 0; i < result.length; i += 3) { int a = result[i+1]; result[i+1] = result[i+2]; result[i+2] = a; }
        return result;
    }
    private static Shape boxes(double[][] bounds) {
        List<Shape> shapes = new ArrayList<>();
        for (double[] b : bounds) {
            float[] vertices = CUBE.clone();
            for (int i = 0; i < vertices.length; i++) vertices[i] = (float)(b[i%3] + vertices[i] * (b[i%3+3] - b[i%3]));
            shapes.add(new Shape(vertices, CUBE_TRIANGLES));
        }
        return combine(shapes.toArray(Shape[]::new));
    }
    private static Shape combine(Shape... shapes) {
        int n = Arrays.stream(shapes).mapToInt(s -> s.vertices.length).sum(), m = Arrays.stream(shapes).mapToInt(s -> s.triangles.length).sum();
        float[] vertices = new float[n]; int[] triangles = new int[m]; int v = 0, t = 0;
        for (Shape shape : shapes) {
            System.arraycopy(shape.vertices, 0, vertices, v, shape.vertices.length);
            for (int index : shape.triangles) triangles[t++] = index + v/3;
            v += shape.vertices.length;
        }
        return new Shape(vertices, triangles);
    }
    private static ModelCollisionMesh build(Shape shape, double merge) throws Exception {
        return ModelCollisionBuilder.build(asset(shape), 0, new Matrix4f(), new ModelCollisionSettings(merge, .01), new ModelCancellation());
    }
    private static boolean contains(ModelCollisionMesh mesh, double x, double y, double z) {
        return mesh.parts().stream().anyMatch(part -> {
            double[] p = part.planes();
            for (int i = 0; i < p.length; i += 4) if (p[i]*x+p[i+1]*y+p[i+2]*z > p[i+3]+1e-8) return false;
            return true;
        });
    }
    private static double volume(ModelCollisionMesh mesh) {
        double sum = 0;
        for (var part : mesh.parts()) {
            double[] p = part.vertices(); int[] t = part.triangles(); double volume = 0;
            for (int i = 0; i < t.length; i += 3) {
                int a = t[i]*3, b = t[i+1]*3, c = t[i+2]*3;
                volume += new Vector3d(p[a],p[a+1],p[a+2]).dot(new Vector3d(p[b],p[b+1],p[b+2]).cross(new Vector3d(p[c],p[c+1],p[c+2])));
            }
            assertTrue(Math.abs(volume) > 1e-12, "each collision part must enclose volume");
            sum += Math.abs(volume)/6;
        }
        return sum;
    }
    private static GltfAsset.Primitive primitive(float[] vertices, int[] triangles) {
        return new GltfAsset.Primitive(vertices, null, null, null, null, null, triangles, null, GltfAsset.PrimitiveMode.TRIANGLES, GltfAsset.Bounds.empty());
    }
    private static GltfAsset asset(Shape shape) {
        return new GltfAsset("test", List.of(new GltfAsset.Scene("", List.of(0))), 0,
                List.of(new GltfAsset.Node(0,-1,"",new Matrix4f(),0,List.of())),
                List.of(new GltfAsset.Mesh("", List.of(primitive(shape.vertices, shape.triangles)))), List.of(), List.of(),
                new GltfAsset.Report("2.0","",List.of(),List.of(),List.of(),List.of(),List.of(),0,0,List.of()));
    }
}
