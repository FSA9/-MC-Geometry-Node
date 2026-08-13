package com.mine.geometry_node.core.engine.system.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssetTransferPolicyTest {
    @Test
    void glbIsAFirstClassTransferableModelAsset() {
        assertEquals(AssetTransferPolicy.MODEL_TYPE_ID, AssetTransferPolicy.resolveTypeId("models/Ship.GLB"));
        assertTrue(AssetTransferPolicy.isTransferablePath("models/ship.glb"));
        assertFalse(AssetTransferPolicy.isGraphPath("models/ship.glb"));
    }
}
