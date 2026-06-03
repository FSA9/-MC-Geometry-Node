package com.mine.geometry_node.core.node.nodes;

/**
 * [枚举] 节点类型定义
 * 用于区分节点的功能分类，并统一管理标题栏的背景颜色。
 */
public enum NodeType {
    
    // --- 核心流程 ---
    EVENT("事件", 0xFFD32F2F),        // 红色
    FLOW_CONTROL("控制流", 0xFF607D8B), // 灰色

    // --- 行为与副作用 ---
    ACTION("动作", 0xFF1976D2),       // 蓝色
    DIALOGUE("对话", 0xFFC2185B),     // 洋红色

    // --- 数据计算 ---
    MATH("数学", 0xFF388E3C),         // 绿色
    LOGIC("逻辑运算", 0xFF455A64),     // 深灰/蓝灰
    DATA("数据/属性", 0xFF0097A7),     // 青色
    
    // --- 变量与杂项 ---
    VARIABLE("变量", 0xFF7B1FA2),     // 紫色
    CUSTOM("自定义", 0xFFE65100);     // 橙色

    private final String displayName;
    private final int color;

    NodeType(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取该类型对应的标题栏颜色 (ARGB)
     */
    public int getColor() {
        return color;
    }
}
