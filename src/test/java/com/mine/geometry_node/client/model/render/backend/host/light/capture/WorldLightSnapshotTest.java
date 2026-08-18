package com.mine.geometry_node.client.model.render.backend.host.light.capture;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldLightSnapshotTest {
    @Test
    void ownsImmutableCopiesAndKeepsChannelsSeparate() {
        byte[] block = {1, 2};
        WorldLightSnapshot snapshot = new WorldLightSnapshot(new ModelDimensionId("minecraft:overworld"), 7,
                10, 20, 30, 2, 1, 1, block, new byte[]{3, 4}, new byte[]{5, 6}, new byte[]{7, 8});
        block[0] = 99;

        assertEquals(1, snapshot.blockLight(0, 0, 0));
        assertEquals(4, snapshot.skyLight(1, 0, 0));
        assertEquals(6, snapshot.emission(1, 0, 0));
        assertEquals(8, snapshot.opacity(1, 0, 0));
        assertEquals(8, snapshot.residentBytes());
        assertThrows(IndexOutOfBoundsException.class, () -> snapshot.blockLight(2, 0, 0));
    }

    @Test
    void rejectsIncompleteChannels() {
        assertThrows(IllegalArgumentException.class, () -> new WorldLightSnapshot(
                new ModelDimensionId("minecraft:overworld"), 0, 0, 0, 0, 2, 1, 1,
                new byte[1], new byte[2], new byte[2], new byte[2]));
    }
}
