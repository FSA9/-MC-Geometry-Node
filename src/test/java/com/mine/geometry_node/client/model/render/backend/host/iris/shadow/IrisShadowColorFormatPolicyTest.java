package com.mine.geometry_node.client.model.render.backend.host.iris.shadow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class IrisShadowColorFormatPolicyTest {
    @Test
    void acceptsVerifiedNormalizedAndFloatFormats() {
        assertEquals(IrisShadowColorFormatPolicy.Descriptor.RGBA,
                IrisShadowColorFormatPolicy.descriptor("RGBA8"));
        assertEquals(IrisShadowColorFormatPolicy.Descriptor.RGBA,
                IrisShadowColorFormatPolicy.descriptor("RGBA16"));
        assertEquals(IrisShadowColorFormatPolicy.Descriptor.RGBA,
                IrisShadowColorFormatPolicy.descriptor("RGBA16F"));
    }

    @Test
    void rejectsIntegerAndUnknownFormats() {
        assertThrows(IllegalStateException.class, () -> IrisShadowColorFormatPolicy.descriptor("RGBA16UI"));
        assertThrows(IllegalStateException.class, () -> IrisShadowColorFormatPolicy.descriptor("DEPTH32F"));
    }
}
