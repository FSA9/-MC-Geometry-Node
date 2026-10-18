package com.mine.geometry_node.core.engine.graph.debug;

public enum DebugRenderChannel {
    AREA("area", DebugRenderStyle.AREA.color()),
    GEOMETRY("geometry", DebugRenderStyle.GEOMETRY.color()),
    SCHEMATIC("schematic", 0xFFB58CFF),
    INTERACTION("interaction", 0xFFF2B36D),
    PATHFINDING("pathfinding", 0xFF4FC3E8),
    FORCE_FIELD("force_field", 0xFFCE93D8);

    private final String id;
    private final int color;

    DebugRenderChannel(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }
}
