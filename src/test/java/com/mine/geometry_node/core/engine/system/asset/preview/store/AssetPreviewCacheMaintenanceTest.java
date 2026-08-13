package com.mine.geometry_node.core.engine.system.asset.preview.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

class AssetPreviewCacheMaintenanceTest {
    @TempDir Path temporary;

    @Test
    void modelBytesAndRevisionMarkerAreEvictedAsOneLogicalArtifact() throws Exception {
        String key = "a".repeat(64);
        Path model = temporary.resolve("server/model-assets/aa/" + key + ".glb");
        Path marker = model.resolveSibling(key + ".glb.revision");
        Files.createDirectories(model.getParent());
        Files.write(model, new byte[12]);
        Files.writeString(marker, "revision");
        Files.setLastModifiedTime(model, FileTime.fromMillis(1));

        AssetPreviewCacheMaintenance.enforceLimit(temporary, 1, null);

        assertFalse(Files.exists(model));
        assertFalse(Files.exists(marker));
        assertEquals(0, AssetPreviewCacheMaintenance.size(temporary));
    }

    @Test
    void revisionMarkerIsNeverTreatedAsAnIndependentCandidate() throws Exception {
        String key = "a".repeat(64);
        Path model = temporary.resolve("server/model-assets/aa/" + key + ".glb");
        Path marker = model.resolveSibling(key + ".glb.revision");
        Files.createDirectories(model.getParent());
        Files.write(model, new byte[4]);
        Files.writeString(marker, "revision");

        AssetPreviewCacheMaintenance.enforceLimit(temporary, 1, model);

        assertTrue(Files.exists(model));
        assertTrue(Files.exists(marker));
    }
}
