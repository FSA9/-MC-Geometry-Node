package com.mine.geometry_node.core.engine.graph.debug;

/** Wire IDs are explicit: independent of enum ordering and UI labels. */
public enum ViewportDebugLayer {
    GEOMETRY(0, DebugRenderChannel.GEOMETRY), SCHEMATIC(1, DebugRenderChannel.SCHEMATIC),
    AREA(2, DebugRenderChannel.AREA), COLLISION(3, null), MODEL_COLLISION(4, null),
    INTERACTION(5, DebugRenderChannel.INTERACTION), PATHFINDING(6, DebugRenderChannel.PATHFINDING), FORCE_FIELD(7, DebugRenderChannel.FORCE_FIELD);
    public final int id;
    public final DebugRenderChannel channel;
    ViewportDebugLayer(int id, DebugRenderChannel channel) { this.id=id; this.channel=channel; }
    public int bit() { return 1 << id; }
    public static int validMask() { return (1 << values().length) - 1; }
    public static ViewportDebugLayer fromId(int id) {
        for (var layer : values()) if (layer.id == id) return layer;
        throw new IllegalArgumentException("Unknown viewport debug layer");
    }
}
