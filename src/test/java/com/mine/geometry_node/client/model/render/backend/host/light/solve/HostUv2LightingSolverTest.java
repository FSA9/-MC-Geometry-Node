package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldLightSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderShape;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.source.*;
import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostUv2LightingSolverTest {
    private static final ModelDimensionId DIMENSION = new ModelDimensionId("minecraft:overworld");
    private final HostUv2LightingSolver solver = new HostUv2LightingSolver(
            HostUv2LightingSolver.Parameters.defaults());

    @Test
    void directLightUsesBlockLevelUnitsAndIgnoresFixtureShape() {
        int packed = solve(new short[]{0, 0, 1});

        assertEquals(12, (packed >>> 4) & 15);
    }

    @Test
    void interveningWorldShapeBlocksDirectLight() {
        int packed = solve(new short[]{0, 1, 1});

        assertEquals(0, (packed >>> 4) & 15);
    }

    private int solve(short[] ids) {
        WorldLightSnapshot world = new WorldLightSnapshot(DIMENSION, 4,
                0, 0, 0, 3, 1, 1, new byte[3], new byte[3], new byte[3], new byte[3]);
        WorldOccluderSnapshot occluders = new WorldOccluderSnapshot(DIMENSION, 4,
                0, 0, 0, 3, 1, 1, List.of(WorldOccluderShape.fullCube(false)), ids, true, false);
        HostLightSourceId id = new HostLightSourceId(DIMENSION, "minecraft",
                HostLightSourceKind.PLACED_BLOCK, 0, 0, 0, "2,0,0");
        HostLightSource source = new HostLightSource(id, 1, 2.5, 0.5, 0.5,
                1, 1, 1, 15, 10);
        HostLightSourceSnapshot sources = new HostLightSourceSnapshot(DIMENSION, 1, List.of(source));
        return solver.solve(List.of(new HostUv2LightingSolver.Receiver(
                        0.5, 0.5, 0.5, 1, 0, 0)), sources, world, occluders, null,
                HostVoxelLightTransport.Cancellation.NONE)[0];
    }
}
