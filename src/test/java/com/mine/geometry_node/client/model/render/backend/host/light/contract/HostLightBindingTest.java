package com.mine.geometry_node.client.model.render.backend.host.light.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostLightBindingTest {
    @Test
    void constantPreservesExistingPackedLightForEveryVertex() {
        HostLightBinding binding = HostLightBinding.constant(0x001200A0);

        assertEquals(HostLightBinding.Mode.CONSTANT, binding.identity().mode());
        assertEquals(0x001200A0, binding.packedLight(0));
        assertEquals(0x001200A0, binding.packedLight(99));
    }

    @Test
    void fieldIdentityUsesContentRevisionRatherThanSamplerObject() {
        HostLightFieldId firstRevision = new HostLightFieldId("asset/instance", 4);
        HostLightBinding first = HostLightBinding.field(firstRevision, vertex -> vertex + 10);
        HostLightBinding equivalentIdentity = HostLightBinding.field(firstRevision, vertex -> 0);
        HostLightBinding nextRevision = HostLightBinding.field(new HostLightFieldId("asset/instance", 5), vertex -> 0);

        assertEquals(first.identity(), equivalentIdentity.identity());
        assertNotEquals(first.identity(), nextRevision.identity());
        assertEquals(13, first.packedLight(3));
    }

    @Test
    void rejectsInvalidFieldIdentityAndVertex() {
        assertThrows(IllegalArgumentException.class, () -> new HostLightFieldId(" ", 0));
        assertThrows(IllegalArgumentException.class, () -> new HostLightFieldId("asset", -1));
        assertThrows(IllegalArgumentException.class, () -> HostLightBinding.constant(0).packedLight(-1));
    }
}
