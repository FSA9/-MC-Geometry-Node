package com.mine.geometry_node.core.node.definition.node;

/** Categories used to group node definitions and select their header color. */
public enum NodeType {
    EVENT(0xFFD32F2F),
    FLOW_CONTROL(0xFF616161),
    ACTION(0xFF1976D2),
    DIALOGUE(0xFFC2185B),
    QUEST(0xFFFF9E3D),
    MATH(0xFF2E7D32),
    LOGIC(0xFF455A64),
    DATA(0xFF00796B),
    VARIABLE(0xFF7B1FA2),
    CUSTOM(0xFFBF360C);

    private final int color;

    NodeType(int color) {
        this.color = color;
    }

    /** Returns the ARGB header color for this category. */
    public int getColor() {
        return color;
    }
}
