package com.mine.geometry_node.client.model.render.backend.host.material;

import com.mine.geometry_node.client.model.render.integration.ModelCompatibilityLoss;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HostMaterialProjectionTest {
    @Test
    void gpuSkinningLossMakesEntityProjectionUnselectable() {
        assertThrows(IllegalArgumentException.class, () -> new HostMaterialProjection(true,
                HostMaterialProfile.HOST_NATIVE_ENTITY, 0, true, true, true, true, false,
                Set.of(ModelCompatibilityLoss.GPU_SKINNING_UNREPRESENTABLE)));
        assertFalse(new HostMaterialProjection(false, HostMaterialProfile.HOST_NATIVE_ENTITY,
                0, true, true, true, true, false,
                Set.of(ModelCompatibilityLoss.GPU_SKINNING_UNREPRESENTABLE)).selectable());
    }
}
