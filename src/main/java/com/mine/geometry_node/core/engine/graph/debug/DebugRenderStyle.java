package com.mine.geometry_node.core.engine.graph.debug;

/** Shared appearance for authored scene objects and runtime debug surfaces. */
public record DebugRenderStyle(int color, boolean showPoints) {
    public static final DebugRenderStyle GEOMETRY = new DebugRenderStyle(0xFFFFFFFF, true);
    public static final DebugRenderStyle AREA = new DebugRenderStyle(0xFF4FC3E8, true);
    public static final DebugRenderStyle COLLISION = new DebugRenderStyle(0xFF56E39F, false);
}
