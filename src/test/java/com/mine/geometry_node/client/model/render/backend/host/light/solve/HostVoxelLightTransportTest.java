package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldLightSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderShape;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderSnapshot;
import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostVoxelLightTransportTest {
    private static final ModelDimensionId DIMENSION = new ModelDimensionId("minecraft:overworld");

    @Test
    void emissionPropagatesThroughOpenCells() {
        WorldLightSnapshot world = world(new byte[]{15, 0, 0});
        HostVoxelLightTransport.Result result = transport(world, new short[3]);

        assertEquals(15, result.block(0, 0, 0));
        assertEquals(14, result.block(1, 0, 0));
        assertEquals(13, result.block(2, 0, 0));
    }

    @Test
    void solidWorldShapeStopsPropagation() {
        WorldLightSnapshot world = world(new byte[]{15, 0, 0});
        HostVoxelLightTransport.Result result = transport(world, new short[]{0, 1, 0});

        assertEquals(0, result.block(2, 0, 0));
    }

    @Test
    void cancellationIsCheckedDuringTransport() {
        WorldLightSnapshot world = world(new byte[]{15, 0, 0});
        assertThrows(TestCancellation.class, () -> new HostVoxelLightTransport().propagate(
                world, occluders(new short[3]), null,
                HostVoxelLightTransport.Parameters.defaults(), () -> { throw new TestCancellation(); }));
    }

    private static HostVoxelLightTransport.Result transport(WorldLightSnapshot world, short[] ids) {
        return new HostVoxelLightTransport().propagate(world, occluders(ids), null,
                HostVoxelLightTransport.Parameters.defaults(), HostVoxelLightTransport.Cancellation.NONE);
    }

    private static WorldLightSnapshot world(byte[] emission) {
        return new WorldLightSnapshot(DIMENSION, 7, 0, 0, 0, 3, 1, 1,
                new byte[3], new byte[3], emission, new byte[3]);
    }

    private static WorldOccluderSnapshot occluders(short[] ids) {
        return new WorldOccluderSnapshot(DIMENSION, 7, 0, 0, 0, 3, 1, 1,
                List.of(WorldOccluderShape.fullCube(false)), ids, true, false);
    }

    private static final class TestCancellation extends RuntimeException {}
}
