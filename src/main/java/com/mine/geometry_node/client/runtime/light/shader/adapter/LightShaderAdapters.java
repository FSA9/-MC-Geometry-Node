package com.mine.geometry_node.client.runtime.light.shader.adapter;

import java.util.List;

/** Explicit, immutable registration keeps matching deterministic during shader reloads. */
public final class LightShaderAdapters {
    private static final List<LightShaderAdapter> ADAPTERS = List.of(
            new BlissLightShaderAdapter(), new SeusLightShaderAdapter(),
            new KappaLightShaderAdapter(), new PhotonLightShaderAdapter(), new SundialLightShaderAdapter());

    private LightShaderAdapters() {}

    public static List<LightShaderAdapter> all() { return ADAPTERS; }
}
