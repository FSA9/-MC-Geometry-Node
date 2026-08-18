package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightFieldId;
import com.mine.geometry_node.client.model.render.backend.host.lod.HostModelLodPlan;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.mine.geometry_node.client.model.runtime.ModelInstancePlacement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HostResolvedDrawTest {
    @Test
    void staticIdentityCarriesResolvedTransformLodAndLightRevision() {
        ModelInstancePlacement placement = new ModelInstancePlacement(new Vector3d(4, 5, 6),
                new Quaternionf(), new Vector3f(1), false, false, 1, 1, 1, 1);
        HostDrawTransform transform = HostDrawTransform.resolve(
                placement, new Matrix4f(), new Vector3f(), 0, 0, 0).orElseThrow();
        HostModelLodPlan.Level lod = new HostModelLodPlan.Level(9, 12, 0.25F, 2);
        HostResolvedDraw first = resolved(transform, lod, new HostLightFieldId("instance/light", 3));
        HostResolvedDraw sameRevision = resolved(transform, lod, new HostLightFieldId("instance/light", 3));
        HostResolvedDraw nextRevision = resolved(transform, lod, new HostLightFieldId("instance/light", 4));
        Object instance = new Object();
        Object layout = new Object();

        HostStaticVariantKey firstKey = first.staticVariantKey(
                instance, 7, 0, layout, 11);
        assertEquals(firstKey, sameRevision.staticVariantKey(
                instance, 7, 0, layout, 11));
        assertNotEquals(firstKey, nextRevision.staticVariantKey(
                instance, 7, 0, layout, 11));
        assertEquals(9, firstKey.firstTriangle());
        assertEquals(12, firstKey.triangleCount());
    }

    @Test
    void staticKeyRetainsFieldIdentityButNotBindingOrSampler() {
        HostLightFieldId id = new HostLightFieldId("instance/light", 8);
        HostLightBinding binding = HostLightBinding.field(id, vertex -> vertex * 2);
        HostStaticVariantKey key = key(binding);

        assertEquals(binding.identity(), key.lightIdentity());
        assertThrows(IllegalStateException.class, key::packedLight);
        for (var field : HostStaticVariantKey.class.getDeclaredFields()) {
            assertFalse(HostLightBinding.class.isAssignableFrom(field.getType()),
                    "static key must not retain a binding or its sampler");
        }
    }

    private static HostResolvedDraw resolved(HostDrawTransform transform, HostModelLodPlan.Level lod,
                                             HostLightFieldId id) {
        return new HostResolvedDraw(transform, lod, HostLightBinding.field(id, ignored -> 0),
                1, 1, 1, 1, false, false, false);
    }

    private static HostStaticVariantKey key(HostLightBinding binding) {
        return new HostStaticVariantKey(new Object(), 0, new Matrix4f(), new org.joml.Matrix3f(),
                0, binding, false, 1, 1, 1, 1, 0, 1, new Object(), 0);
    }
}
