package com.mine.geometry_node.client.model.render.backend.host.light.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostLightQuantizerTest {
    @Test
    void packsScalarLevelsIntoStandardEntityUv2() {
        assertEquals(0, HostLightQuantizer.packUv2(new HostScalarLightSample(0, 0)));
        assertEquals(0x00F000F0, HostLightQuantizer.packUv2(new HostScalarLightSample(15, 15)));
        assertEquals(0x00700030, HostLightQuantizer.packUv2(new HostScalarLightSample(3, 7)));
    }

    @Test
    void rejectsLevelsOutsideMinecraftScalarRange() {
        assertThrows(IllegalArgumentException.class, () -> new HostScalarLightSample(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new HostScalarLightSample(0, 16));
    }
}
