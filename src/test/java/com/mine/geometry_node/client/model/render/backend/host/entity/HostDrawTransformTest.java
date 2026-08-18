package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.runtime.ModelInstancePlacement;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostDrawTransformTest {
    @Test
    void resolvesWorldAndCameraRelativeCentersFromSameBakedTransform() {
        ModelInstancePlacement placement = new ModelInstancePlacement(new Vector3d(10, 20, 30),
                new Quaternionf(), new Vector3f(2), false, false, 1, 1, 1, 1);
        HostDrawTransform resolved = HostDrawTransform.resolve(placement,
                new Matrix4f().translate(1, 2, 3), new Vector3f(0.5F), 4, 5, 6).orElseThrow();

        assertEquals(new Vector3d(13, 25, 37), resolved.worldCenter());
        Vector3f cameraCenter = resolved.cameraRelative().transformPosition(new Vector3f(0.5F));
        assertEquals(9, cameraCenter.x, 1.0E-6F);
        assertEquals(20, cameraCenter.y, 1.0E-6F);
        assertEquals(31, cameraCenter.z, 1.0E-6F);
        assertFalse(resolved.mirrored());
    }

    @Test
    void preservesMirroredAndRejectsSingularContracts() {
        ModelInstancePlacement mirrored = new ModelInstancePlacement(new Vector3d(), new Quaternionf(),
                new Vector3f(-1, 1, 1), false, false, 1, 1, 1, 1);
        assertTrue(HostDrawTransform.resolve(mirrored, new Matrix4f(), new Vector3f(), 0, 0, 0)
                .orElseThrow().mirrored());

        assertTrue(HostDrawTransform.resolve(mirrored, new Matrix4f().scale(0),
                new Vector3f(), 0, 0, 0).isEmpty());
    }
}
