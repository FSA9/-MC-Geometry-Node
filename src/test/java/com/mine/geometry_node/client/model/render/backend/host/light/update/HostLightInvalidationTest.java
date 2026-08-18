package com.mine.geometry_node.client.model.render.backend.host.light.update;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import com.mine.geometry_node.client.model.runtime.ModelInstanceId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostLightInvalidationTest {
    @Test
    void spatialAndNonSpatialTargetsCannotBeMixed() {
        ModelDimensionId dimension = new ModelDimensionId("minecraft:overworld");
        HostLightDirtyRegion spatial = new HostLightDirtyRegion(dimension,
                0, 0, 0, 15, 15, 15,
                Set.of(HostLightInvalidationKind.SOURCE, HostLightInvalidationKind.WORLD_OCCLUDER), 3);
        HostLightAssetInvalidation asset = new HostLightAssetInvalidation("asset-key",
                Set.of(HostLightInvalidationKind.MODEL_OCCLUDER, HostLightInvalidationKind.RECEIVER), 4);
        HostLightInstanceInvalidation instance = new HostLightInstanceInvalidation(new ModelInstanceId("fixture"),
                Set.of(HostLightInvalidationKind.MODEL_OCCLUDER, HostLightInvalidationKind.RECEIVER), 4);

        assertEquals(3, spatial.revision());
        assertEquals(4, asset.revision());
        assertEquals(4, instance.revision());
        assertEquals(Set.of(HostLightInvalidationKind.OUTPUT),
                new HostLightOutputInvalidation(7, 5).causes());
        assertThrows(IllegalArgumentException.class, () -> new HostLightDirtyRegion(dimension,
                0, 0, 0, 1, 1, 1, Set.of(HostLightInvalidationKind.RECEIVER), 1));
        assertThrows(IllegalArgumentException.class, () -> new HostLightInstanceInvalidation(
                new ModelInstanceId("fixture"), Set.of(HostLightInvalidationKind.OUTPUT), 1));
        assertThrows(IllegalArgumentException.class, () -> new HostLightAssetInvalidation(
                " ", Set.of(HostLightInvalidationKind.RECEIVER), 1));
        assertThrows(IllegalArgumentException.class, () -> new HostLightDirtyRegion(dimension,
                2, 0, 0, 1, 1, 1, Set.of(HostLightInvalidationKind.SOURCE), 1));
        assertThrows(IllegalArgumentException.class, () -> new HostLightDirtyRegion(dimension,
                1, 0, 0, 1, 1, 1, Set.of(HostLightInvalidationKind.SOURCE), 1));
    }
}
