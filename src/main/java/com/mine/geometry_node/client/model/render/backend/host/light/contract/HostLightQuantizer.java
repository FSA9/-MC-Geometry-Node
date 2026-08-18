package com.mine.geometry_node.client.model.render.backend.host.light.contract;

import java.util.Objects;

/** Deterministic scalar-to-standard-ENTITY UV2 packing; no RGB, normal or projection policy lives here. */
public final class HostLightQuantizer {
    private HostLightQuantizer() {
    }

    public static int packUv2(HostScalarLightSample sample) {
        Objects.requireNonNull(sample, "sample");
        return (sample.block() << 4) | (sample.sky() << 20);
    }
}
