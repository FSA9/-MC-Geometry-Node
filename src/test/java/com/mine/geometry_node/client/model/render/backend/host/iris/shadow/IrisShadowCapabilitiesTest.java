package com.mine.geometry_node.client.model.render.backend.host.iris.shadow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IrisShadowCapabilitiesTest {
    @Test
    void distinguishesSingleAndMultipleColorTargets() {
        assertFalse(new IrisShadowCapabilities(1, 1, List.of("RGBA8"), true).multiRenderTarget());
        assertTrue(new IrisShadowCapabilities(2, 2, List.of("RGBA16", "RGBA16"), true).multiRenderTarget());
    }
}
