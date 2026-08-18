package com.mine.geometry_node.client.model.render.backend.host.light.contract;

/** Renderer-neutral Minecraft scalar light levels. RGB and direction are deliberately absent in F1. */
public record HostScalarLightSample(int block, int sky) {
    public HostScalarLightSample {
        requireLevel(block, "block");
        requireLevel(sky, "sky");
    }

    private static void requireLevel(int value, String name) {
        if (value < 0 || value > 15) throw new IllegalArgumentException(name + " light must be in [0, 15]");
    }
}
