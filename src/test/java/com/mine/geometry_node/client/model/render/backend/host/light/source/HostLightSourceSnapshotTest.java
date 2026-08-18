package com.mine.geometry_node.client.model.render.backend.host.light.source;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostLightSourceSnapshotTest {
    private static final ModelDimensionId OVERWORLD = new ModelDimensionId("minecraft:overworld");

    @Test
    void sourceIdentityExcludesRevisionAndSnapshotSortsByStableId() {
        HostLightSourceId secondId = id("second");
        HostLightSourceId firstId = id("first");
        HostLightSource firstRevision = source(firstId, 1);
        HostLightSource nextRevision = source(firstId, 2);
        HostLightSourceSnapshot snapshot = new HostLightSourceSnapshot(OVERWORLD, 8,
                List.of(source(secondId, 1), firstRevision));

        assertEquals(firstRevision.id(), nextRevision.id());
        assertEquals(List.of(firstId, secondId), snapshot.sources().stream().map(HostLightSource::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.sources().clear());
    }

    @Test
    void rejectsDuplicateIdsCrossDimensionAndInvalidNumericContent() {
        HostLightSource duplicate = source(id("same"), 1);
        assertThrows(IllegalArgumentException.class,
                () -> new HostLightSourceSnapshot(OVERWORLD, 1, List.of(duplicate, source(id("same"), 2))));
        HostLightSource nether = source(new HostLightSourceId(new ModelDimensionId("minecraft:the_nether"),
                "minecraft", HostLightSourceKind.PLACED_BLOCK, 0, 0, 0, "lamp"), 1);
        assertThrows(IllegalArgumentException.class,
                () -> new HostLightSourceSnapshot(OVERWORLD, 1, List.of(nether)));
        assertThrows(IllegalArgumentException.class, () -> new HostLightSource(id("bad"), 1,
                Double.NaN, 0, 0, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new HostLightSource(id("bad"), 1,
                0, 0, 0, -1, 1, 1, 1, 1));
    }

    private static HostLightSourceId id(String localIdentity) {
        return new HostLightSourceId(OVERWORLD, "minecraft", HostLightSourceKind.PLACED_BLOCK,
                0, 4, 0, localIdentity);
    }

    private static HostLightSource source(HostLightSourceId id, long revision) {
        return new HostLightSource(id, revision, 1.5, 64.5, 2.5,
                1, 0.8f, 0.6f, 15, 8);
    }
}
