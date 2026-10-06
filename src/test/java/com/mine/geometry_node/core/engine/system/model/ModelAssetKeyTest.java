package com.mine.geometry_node.core.engine.system.model;

import com.mine.geometry_node.core.engine.system.model.asset.ModelAssetKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelAssetKeyTest {
    private static ModelAssetKey key(String path) {
        return new ModelAssetKey("local-session:test", path, "a".repeat(64), "STATIC_BASIC", 2);
    }

    @Test void logicalFileNamesPreserveSpacesUnicodeAndRepeatedDots() {
        for (String path : new String[]{"House v2.glb", "\u6a21\u578b/\u623f\u5c4b.glb", "house..v2.glb"})
            assertEquals(path, key(path).logicalId());
    }

    @Test void logicalIdsRemainConfinedRelativePaths() {
        for (String path : new String[]{"", "/house.glb", "../house.glb", "dir/../house.glb", "dir//house.glb",
                "./house.glb", "C:/house.glb", "dir\\house.glb", "house\0.glb"})
            assertThrows(IllegalArgumentException.class, () -> key(path), path);
    }
}
