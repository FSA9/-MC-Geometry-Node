package com.mine.geometry_node.client.model.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RemoteModelAssetRevisionTest {
    @TempDir Path temporary;
    @Test
    void normalizesRemotePathAndBuildsStableRevisionIdentity() {
        RemoteModelAssetRevision revision = new RemoteModelAssetRevision("models\\creature.GLB", 42, 99);

        assertEquals("models/creature.GLB", revision.remotePath());
        assertEquals("models/creature.GLB\0" + "42\0" + "99\0v1", revision.canonical());
    }

    @Test
    void rejectsNonModelAndTraversalPaths() {
        assertThrows(IllegalArgumentException.class, () -> new RemoteModelAssetRevision("image.png", 1, 1));
        assertThrows(RuntimeException.class, () -> new RemoteModelAssetRevision("../escape.glb", 1, 1));
    }

    @Test
    void clearAllLeavesUnrelatedRootChildrenUntouched() throws Exception {
        Path unrelated = temporary.resolve("unrelated/model-assets/keep.glb");
        Path owned = temporary.resolve("a".repeat(64)).resolve("model-assets/drop.glb");
        Files.createDirectories(unrelated.getParent());
        Files.createDirectories(owned.getParent());
        Files.write(unrelated, new byte[] {1});
        Files.write(owned, new byte[] {2});

        ClientModelAssetCache.clearAllUnder(temporary);

        assertTrue(Files.exists(unrelated));
        assertFalse(Files.exists(owned));
    }
}
