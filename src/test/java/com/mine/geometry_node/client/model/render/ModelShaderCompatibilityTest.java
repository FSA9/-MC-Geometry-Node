package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.client.model.render.compat.ModelShaderBackendStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelShaderCompatibilityTest {
    @Test
    void fullBackendCannotHideLosses() {
        assertThrows(IllegalArgumentException.class, () -> new ModelShaderBackendStatus("invalid",
                ModelShaderBackendStatus.Fidelity.FULL, true, true, List.of("normal-map"), "invalid"));
    }

    @Test
    void unavailableBackendCannotBeSelected() {
        ModelShaderBackendStatus status = ModelShaderBackendStatus.unavailable("iris-unknown", true, "unsupported");
        assertFalse(status.selectable());
        assertEquals(ModelShaderBackendStatus.Fidelity.UNAVAILABLE, status.fidelity());
        assertTrue(status.shaderEnvironmentPresent());
    }
}
