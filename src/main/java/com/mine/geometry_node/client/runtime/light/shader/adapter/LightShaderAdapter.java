package com.mine.geometry_node.client.runtime.light.shader.adapter;

/** A guarded receiver patch for one shaderpack family. */
public interface LightShaderAdapter {
    String name();
    boolean matches(String source);

    /** Returns null when any required insertion cannot be applied unambiguously. */
    String patch(String source);

    /** Fullscreen reconstruction may depend on the paired vertex shader's render scale. */
    default String patch(String source, String vertex) { return patch(source); }
}
