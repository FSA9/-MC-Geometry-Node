package com.mine.geometry_node.core.engine.system.model;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionBuilder;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionSettings;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.Matrix4f;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionMovement;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelConvexCollider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelCollisionMovementTest {
    private static final ModelCollisionMovement.VanillaClip FREE = (box, delta) -> delta;

    @Test void fastMovementCannotTunnelThroughThinWall() {
        var wall = cube(2, 0, -2, 2.001, 4, 2);
        Vec3 result = resolve(new AABB(0, 1, 0, 0.6, 2.8, 0.6), new Vec3(100, 0, 0), false, wall);
        assertEquals(1.4, result.x, 1e-6);
        assertEquals(0, result.y);
    }

    @Test void wallSlideRetainsTangentialMovement() {
        Vec3 result = resolve(new AABB(0, 1, 0, 0.6, 2.8, 0.6), new Vec3(3, 0, 1), false,
                cube(2, 0, -2, 3, 4, 4));
        assertEquals(1.4, result.x, 1e-6);
        assertEquals(1, result.z, 1e-6);
    }

    @Test void walkableRampLiftsWithoutChangingHorizontalSpeed() {
        Vec3 result = resolve(new AABB(0.4, 0.80001, 1, 0.8, 2.6, 1.6), new Vec3(0.5, -0.08, 0), true, ramp());
        assertEquals(0.5, result.x, 1e-6);
        assertEquals(0.49999, result.y, 2e-6);
    }

    @Test void standingOnRampDoesNotSlideUnderGravity() {
        Vec3 result = resolve(new AABB(0.4, 0.80001, 1, 0.8, 2.6, 1.6), new Vec3(0, -0.08, 0), true, ramp());
        assertEquals(0, result.x);
        assertEquals(-0.00001, result.y, 1e-6);
    }

    @Test void downhillMotionRemainsSupported() {
        Vec3 result = resolve(new AABB(0.4, 0.80001, 1, 0.8, 2.6, 1.6), new Vec3(-0.25, -0.08, 0), true, ramp());
        assertEquals(-0.25, result.x, 1e-6);
        assertEquals(-0.25001, result.y, 2e-6);
    }

    @Test void jumpAwayFromRampIsUnchanged() {
        Vec3 request = new Vec3(0, 0.42, 0);
        assertEquals(request, resolve(new AABB(0.4, 0.80001, 1, 0.8, 2.6, 1.6), request, true, ramp()));
    }

    @Test void rampLiftRespectsVanillaCeiling() {
        AABB box = new AABB(0.4, 0.80001, 1, 0.8, 2.60001, 1.6);
        var body = new ModelCollisionScene.Candidate(new ModelConvexCollider(ramp()), 0, 0, 0);
        Vec3 result = ModelCollisionMovement.resolve(box, new Vec3(1, -0.08, 0), true, List.of(body),
                (at, delta) -> new Vec3(delta.x, delta.y > 0 ? Math.min(delta.y, Math.max(0, 2.8 - at.maxY)) : delta.y, delta.z));
        assertTrue(box.maxY + result.y <= 2.8 + 1e-8);
        assertTrue(box.minY + result.y >= box.maxX + result.x - 1e-6);
        assertEquals(0.2, result.x, 2e-6);
    }

    @Test void translatedColliderPreservesFarWorldPrecision() {
        var candidate = new ModelCollisionScene.Candidate(new ModelConvexCollider(cube(2, 0, -2, 2.001, 4, 2)), 29_999_999, 0, 0);
        var hit = candidate.sweep(new AABB(29_999_999, 1, 0, 29_999_999.6, 2.8, 0.6), new Vec3(100, 0, 0));
        assertNotNull(hit);
        assertEquals(0.014, hit.time(), 1e-9);
    }

    @Test void touchingSurfaceAllowsTangentialMotion() {
        var collider = new ModelConvexCollider(cube(-5, -1, -5, 5, 0, 5));
        assertNull(collider.sweep(new AABB(0, 0, 0, 0.6, 1.8, 0.6), new Vec3(1, 0, 0)));
        assertNotNull(collider.sweep(new AABB(0, 0, 0, 0.6, 1.8, 0.6), new Vec3(0, -1, 0)));
    }

    @Test void stepsOntoShortModelStairAndRejectsTallStair() {
        AABB box = new AABB(0, 0, 0, 0.6, 1.8, 0.6);
        for (double height : new double[]{0.4, 1}) {
            var stair = new ModelCollisionScene.Candidate(new ModelConvexCollider(cube(0.8, -1, -2, 4, height, 2)), 0, 0, 0);
            Vec3 result = ModelCollisionMovement.resolve(box, new Vec3(1, -0.08, 0), true, 0.6, List.of(stair), FREE);
            if (height == 0.4) { assertEquals(1, result.x, 1e-6); assertEquals(0.4, result.y, 1e-6); }
            else assertEquals(0.2, result.x, 1e-6);
        }
    }

    @Test void exactOverlapDistinguishesSpaceAboveSlopeFromSolidBelow() {
        var collider = new ModelConvexCollider(ramp());
        assertTrue(collider.intersects(new AABB(1, 0.9, 1, 1.1, 1.1, 1.1)));
        assertFalse(collider.intersects(new AABB(1, 1.2, 1, 1.1, 1.3, 1.1)));
    }

    @Test void generatedClosedRampJoinsFloorWithoutSnagging() throws Exception {
        var parts = new java.util.ArrayList<ModelCollisionMesh.Part>();
        parts.addAll(generated(new float[]{-2,0,-2,0,0,-2,0,0,2,-2,0,2}).parts());
        parts.addAll(generated(new float[]{0,0,-2,4,2,-2,4,2,2,0,0,2}).parts());
        var candidates = parts.stream().map(p -> new ModelCollisionScene.Candidate(new ModelConvexCollider(p), 0, 0, 0)).toList();
        AABB box = new AABB(-0.9, 0.0006, -0.3, -0.3, 1.8006, 0.3);
        Vec3 result = ModelCollisionMovement.resolve(box, new Vec3(1, -0.08, 0), true, 0.6, candidates, FREE);
        assertEquals(1, result.x, 2e-6);
        assertEquals(0.35, box.minY + result.y, 0.002);
    }

    @Test void independentClosedFloorsPermitCrossingSeamBothWays() throws Exception {
        var parts = new java.util.ArrayList<ModelCollisionMesh.Part>();
        parts.addAll(generated(new float[]{-2,0,-2,0,0,-2,0,0,2,-2,0,2}).parts());
        parts.addAll(generated(new float[]{0,0,-2,2,0,-2,2,0,2,0,0,2}).parts());
        var candidates = parts.stream().map(p -> new ModelCollisionScene.Candidate(new ModelConvexCollider(p), 0, 0, 0)).toList();
        for (double direction : new double[]{-1, 1}) {
            AABB box = new AABB(-0.3 - direction * 0.6, 0.0006, -0.3, 0.3 - direction * 0.6, 1.8006, 0.3);
            Vec3 result = ModelCollisionMovement.resolve(box, new Vec3(direction, -0.08, 0), true, 0.6, candidates, FREE);
            assertEquals(direction, result.x, 2e-6);
            assertEquals(0, box.minY + result.y, 2e-6);
        }
    }

    private static ModelCollisionMesh generated(float[] positions) throws Exception {
        float[] closed = java.util.Arrays.copyOf(positions, 24);
        for (int i = 0; i < 12; i++) closed[i+12] = positions[i] - (i%3 == 1 ? 1 : 0);
        var primitive = new GltfAsset.Primitive(closed, null, null, null, null, null,
                new int[]{0,2,1,0,3,2,4,5,6,4,6,7,0,1,5,0,5,4,1,2,6,1,6,5,2,3,7,2,7,6,3,0,4,3,4,7},
                null, GltfAsset.PrimitiveMode.TRIANGLES, GltfAsset.Bounds.empty());
        var asset = new GltfAsset("test", List.of(new GltfAsset.Scene("", List.of(0))), 0,
                List.of(new GltfAsset.Node(0,-1,"",new Matrix4f(),0,List.of())), List.of(new GltfAsset.Mesh("",List.of(primitive))),List.of(),List.of(),
                new GltfAsset.Report("2.0","",List.of(),List.of(),List.of(),List.of(),List.of(),0,0,List.of()));
        return ModelCollisionBuilder.build(asset, 0, new Matrix4f(), new ModelCollisionSettings(0, 0.001), new ModelCancellation());
    }

    private static Vec3 resolve(AABB box, Vec3 movement, boolean grounded, ModelCollisionMesh.Part part) {
        return ModelCollisionMovement.resolve(box, movement, grounded,
                List.of(new ModelCollisionScene.Candidate(new ModelConvexCollider(part), 0, 0, 0)), FREE);
    }

    private static ModelCollisionMesh.Part ramp() {
        double s = Math.sqrt(0.5);
        return new ModelCollisionMesh.Part(new double[]{0,0,0, 4,0,0, 4,4,0, 0,0,4, 4,0,4, 4,4,4},
                new int[]{0,2,1, 3,4,5, 0,1,4,0,4,3, 1,2,5,1,5,4, 2,0,3,2,3,5},
                new double[]{0,-1,0,0, 1,0,0,4, -s,s,0,0, 0,0,-1,0, 0,0,1,4});
    }

    private static ModelCollisionMesh.Part cube(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new ModelCollisionMesh.Part(new double[]{x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0,
                x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1},
                new int[]{0,2,1,0,3,2, 4,5,6,4,6,7, 0,1,5,0,5,4, 3,7,6,3,6,2, 0,4,7,0,7,3, 1,2,6,1,6,5},
                new double[]{-1,0,0,-x0, 1,0,0,x1, 0,-1,0,-y0, 0,1,0,y1, 0,0,-1,-z0, 0,0,1,z1});
    }
}
