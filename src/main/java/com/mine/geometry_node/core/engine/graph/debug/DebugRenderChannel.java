package com.mine.geometry_node.core.engine.graph.debug;

public enum DebugRenderChannel {
    AREA("area:", 0xFF4FC3E8),
    GEOMETRY("geometry:", 0xFFFFFFFF),
    SCHEMATIC("schematic:", 0xFFB58CFF),
    INTERACTION("interaction:", 0xFFF2B36D),
    PATHFINDING("pathfinding:", 0xFF4FC3E8);

    private final String sourcePrefix;
    private final int color;

    DebugRenderChannel(String sourcePrefix, int color) {
        this.sourcePrefix = sourcePrefix;
        this.color = color;
    }

    public String sourcePrefix() {
        return sourcePrefix;
    }

    public int color() {
        return color;
    }

    public boolean owns(String sourceKey) {
        return sourceKey != null && sourceKey.startsWith(sourcePrefix);
    }
}
