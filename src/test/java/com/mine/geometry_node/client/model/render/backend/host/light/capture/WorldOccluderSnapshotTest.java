package com.mine.geometry_node.client.model.render.backend.host.light.capture;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldOccluderSnapshotTest {
    private static final ModelDimensionId DIMENSION = new ModelDimensionId("minecraft:overworld");

    @Test
    void exactShapesBlockSolidRegionsButPreserveOpenings() {
        WorldOccluderShape lowerSlab = new WorldOccluderShape(
                new float[]{0, 0, 0, 1, 0.4F, 1}, false);
        WorldOccluderSnapshot snapshot = snapshot(3, 2, 1, List.of(lowerSlab),
                new short[]{0, 0, 0, 0, 1, 0});

        assertTrue(snapshot.blocksOpenSegment(0.5, 1.2, 0.5, 2.5, 1.2, 0.5));
        assertFalse(snapshot.blocksOpenSegment(0.5, 1.75, 0.5, 2.5, 1.75, 0.5));
    }

    @Test
    void gridTraversalDoesNotMissDiagonalBlockers() {
        WorldOccluderSnapshot snapshot = snapshot(3, 3, 3,
                List.of(WorldOccluderShape.fullCube(false)), ids(27, 13));

        assertTrue(snapshot.blocksOpenSegment(0.1, 0.1, 0.1, 2.9, 2.9, 2.9));
    }

    @Test
    void sourceFixtureCellCanBeExcludedWithoutIgnoringInterveningWalls() {
        WorldOccluderShape full = WorldOccluderShape.fullCube(false);
        WorldOccluderSnapshot fixtureOnly = snapshot(3, 1, 1, List.of(full), new short[]{0, 0, 1});
        WorldOccluderSnapshot wallAndFixture = snapshot(3, 1, 1, List.of(full), new short[]{0, 1, 1});

        assertFalse(fixtureOnly.blocksOpenSegmentToSource(0.5, 0.5, 0.5, 2.5, 0.5, 0.5));
        assertTrue(wallAndFixture.blocksOpenSegmentToSource(0.5, 0.5, 0.5, 2.5, 0.5, 0.5));
    }

    private static WorldOccluderSnapshot snapshot(int x, int y, int z,
                                                   List<WorldOccluderShape> palette, short[] ids) {
        return new WorldOccluderSnapshot(DIMENSION, 7, 0, 0, 0, x, y, z,
                palette, ids, true, false);
    }

    private static short[] ids(int size, int occupied) {
        short[] ids = new short[size];
        ids[occupied] = 1;
        return ids;
    }
}
