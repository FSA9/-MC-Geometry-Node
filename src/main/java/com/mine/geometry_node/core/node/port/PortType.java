package com.mine.geometry_node.core.node.port;

import com.mine.geometry_node.core.node.value.ColorValue;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 端口数据类型枚举。
 * 定义节点端口可以传递的数据类型。
 */
public enum PortType {
    EXECUTION("执行", 0xFFFFFFFF, null),

    INTEGER("整数", 0xFF4A90E2, 0),
    FLOAT("浮点数", 0xFF50C878, 0.0f),
    BOOLEAN("布尔", 0xFFE74C3C, false),
    STRING("字符串", 0xFF9B59B6, ""),
    RICH_TEXT("富文本", 0xFFD56BE8, RichTextValue.EMPTY),
    ENTITY("实体", 0xFFE91E63, null),
    ITEM("物品", 0xFFE91E63, null),
    ITEM_STACK("物品栈", 0xFFFF5252, null),
    BLOCK("方块", 0xFF8D6E63, null),
    GEOMETRY("几何", 0xFF26A69A, GeometryValue.EMPTY),
    XYZ("XYZ", 0xFF00BCD4, List.of(0.0f, 0.0f, 0.0f)),
    COLOR("颜色", 0xFFFFD54F, ColorValue.WHITE),
    LIST("列表", 0xFFFF9800, List.of()),
    DICT("字典", 0xFFE67E22, java.util.Map.of()),
    SHOP("商店", 0xFFFFB74D, java.util.Map.of("offers", List.of())),
    ANY("任意", 0xFF95A5A6, null);

    private final String displayName;
    private final int color;
    private final Object defaultValue;

    PortType(String displayName, int color, Object defaultValue) {
        this.displayName = displayName;
        this.color = color;
        this.defaultValue = defaultValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public boolean isExecution() {
        return this == EXECUTION;
    }
    
    /**
     * 检查两个端口类型是否兼容。
     * 采用静态方法设计，便于在连接逻辑中直接调用。
     *
     * @param outputport （数据源）
     * @param inputport （数据去向）
     * @return 如果允许连接返回 true
     */
    public static boolean isCompatible(PortType outputport, PortType inputport) {
        // null 检查
        if (outputport == null || inputport == null) return false;

        // 执行流独立
        if (outputport == EXECUTION || inputport == EXECUTION) {
            return outputport == inputport;
        }

        // ANY 接收一切
        if (outputport == ANY || inputport == ANY) {
            return true;
        }

        // 同类兼容
        if (outputport == inputport) return true;

        // Shop data is stored as a map, but kept as a distinct editor-facing type.
        if ((outputport == SHOP && inputport == DICT) || (outputport == DICT && inputport == SHOP)) {
            return true;
        }

        // --- 隐式类型转换白名单 ---

        // 1. 基础三剑客互转 (INT, FLOAT, BOOLEAN)
        boolean isOutMath = (outputport == INTEGER || outputport == FLOAT || outputport == BOOLEAN);
        boolean isInMath  = (inputport == INTEGER || inputport == FLOAT || inputport == BOOLEAN);
        if (isOutMath && isInMath) return true;

        // 2. 万物皆可转STRING
        if (inputport == STRING) {
            if (outputport == INTEGER || outputport == FLOAT || outputport == BOOLEAN ||
                    outputport == RICH_TEXT || outputport == ENTITY || outputport == BLOCK || outputport == XYZ ||
                    outputport == ITEM || outputport == LIST || outputport == DICT || outputport == SHOP) {
                return true;
            }
        }

        // 3. 字符串 (STRING) 反向解析
        if (outputport == STRING) {
            if (inputport == RICH_TEXT) return true; // 字符串包装为富文本
            if (inputport == ENTITY) return true; // 字符串尝试解析为 UUID 寻找实体
            if (inputport == BLOCK)  return true; // 字符串尝试解析为方块 Registry ID
            if (inputport == ITEM)   return true; // 字符串尝试解析为物品 Registry ID
            if (inputport == BOOLEAN) return true; // 字符串尝试解析为 "true"/"false"
            if (inputport == INTEGER || inputport == FLOAT) return true;
        }

        // 4. 富文本可降级为字符串
        if (outputport == RICH_TEXT && inputport == STRING) {
            return true;
        }

        // 5. 新颜色值与旧 ARGB 整数端口互通
        if ((outputport == COLOR && inputport == INTEGER) || (outputport == INTEGER && inputport == COLOR)) {
            return true;
        }

        // 6. 列表聚合 (LIST -> ENTITY)
        // 允许将实体列表连入单个实体端口，由底层动作节点自动拆解执行
        if (outputport == LIST && inputport == ENTITY) {
            return true;
        }

        return false;
    }

    /**
     * 根据 Java 对象的实际类型，反推它的几何节点端口类型
     */
    public static PortType getTypeOf(Object value) {
        if (value == null) return ANY;
        if (value instanceof Integer) return INTEGER;
        if (value instanceof Float || value instanceof Double) return FLOAT;
        if (value instanceof Boolean) return BOOLEAN;
        if (value instanceof String) return STRING;
        if (value instanceof RichTextValue) return RICH_TEXT;
        if (value instanceof ColorValue) return COLOR;
        if (value instanceof GeometryValue) return GEOMETRY;
        if (value instanceof net.minecraft.world.entity.Entity) return ENTITY;
        if (value instanceof net.minecraft.world.item.ItemStack) return ITEM_STACK;
        if (value instanceof java.util.List) return LIST;
        if (value instanceof java.util.Map) return DICT;
        if (value instanceof net.minecraft.world.phys.Vec3) return XYZ;
        // 如果都不是，默认返回 ANY
        return ANY;
    }
}
