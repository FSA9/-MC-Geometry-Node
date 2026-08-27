package com.mine.geometry_node.core.node.port;

import com.mine.geometry_node.core.node.value.color.ColorValue;
import com.mine.geometry_node.core.node.value.DialogueChoiceValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.node.value.QuestConditionValue;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.SlotRef;

import java.util.List;

/**
 * 端口数据类型枚举。
 * 定义节点端口可以传递的数据类型。
 */
public enum PortType {
    EXECUTION("执行", 0xFFFFFFFF, null, true),
    BEHAVIOR_STRUCTURE("行为结构", 0xFF5C9E72, null, true),

    INTEGER("整数", 0xFF4A90E2, 0),
    LONG("长整数", 0xFF3F7DC2, 0L),
    FLOAT("浮点数", 0xFF50C878, 0.0f),
    BOOLEAN("布尔", 0xFFE74C3C, false),
    STRING("字符串", 0xFF9B59B6, ""),
    PATH("路径", 0xFF5F6670, ""),
    RICH_TEXT("富文本", 0xFFD56BE8, RichTextValue.EMPTY),
    ENTITY("实体", 0xFFE91E63, null),
    ENTITY_TEMPLATE("实体模板", 0xFFFF5C8A, EntityTemplateValue.EMPTY),
    ITEM("物品", 0xFFFF8A65, null),
    ITEM_STACK("物品栈", 0xFFFF7043, null),
    SLOT("槽位", 0xFFB0BEC5, SlotRef.DEFAULT.serialize()),
    BLOCK("方块", 0xFF8D6E63, null),
    GEOMETRY("几何", 0xFF26A69A, GeometryValue.EMPTY),
    XYZ("XYZ", 0xFF00BCD4, List.of(0.0f, 0.0f, 0.0f)),
    COLOR("颜色", 0xFFFFD54F, ColorValue.WHITE),
    LIST("列表", 0xFFFF9800, List.of()),
    DIALOGUE_CHOICE("对话选项", 0xFFFF80AB, null),
    QUEST_CONDITION("任务条件", 0xFFFFA726, null),
    DICT("字典", 0xFFE67E22, java.util.Map.of()),
    SHOP("商店", 0xFFFFB74D, java.util.Map.of("offers", List.of())),
    ANY("任意", 0xFF95A5A6, null);

    private final String displayName;
    private final int color;
    private final Object defaultValue;
    private final boolean flow;

    PortType(String displayName, int color, Object defaultValue) {
        this(displayName, color, defaultValue, false);
    }

    PortType(String displayName, int color, Object defaultValue, boolean flow) {
        this.displayName = displayName;
        this.color = color;
        this.defaultValue = defaultValue;
        this.flow = flow;
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

    public boolean isFlow() {
        return flow;
    }

    /**
     * Tests specifically for the ordinary execution channel.
     * Use {@link #isFlow()} when classifying control-flow versus data ports.
     */
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

        // 控制流先于 ANY 和数据隐式转换处理，且只允许相同通道连接。
        if (outputport.isFlow() || inputport.isFlow()) {
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

        // Entity templates can be materialized as unspawned entities at runtime.
        if (outputport == ENTITY_TEMPLATE && inputport == ENTITY) {
            return true;
        }

        // 1. 基础数值/布尔互转
        boolean isOutMath = (outputport == INTEGER || outputport == LONG || outputport == FLOAT || outputport == BOOLEAN);
        boolean isInMath  = (inputport == INTEGER || inputport == LONG || inputport == FLOAT || inputport == BOOLEAN);
        if (isOutMath && isInMath) return true;

        // Scalar-to-vector broadcasting: v -> [v, v, v].
        if ((outputport == INTEGER || outputport == FLOAT) && inputport == XYZ) {
            return true;
        }

        // 2. 万物皆可转STRING
        if (inputport == STRING) {
            if (outputport == INTEGER || outputport == LONG || outputport == FLOAT || outputport == BOOLEAN ||
                    outputport == RICH_TEXT || outputport == ENTITY || outputport == BLOCK || outputport == SLOT || outputport == XYZ ||
                    outputport == ITEM || outputport == LIST || outputport == DICT || outputport == SHOP || outputport == PATH) {
                return true;
            }
        }

        // PATH uses String storage but keeps a distinct editor-facing type.
        if ((outputport == PATH && inputport == STRING) || (outputport == STRING && inputport == PATH)) {
            return true;
        }

        // 3. 字符串 (STRING) 反向解析
        if (outputport == STRING) {
            if (inputport == RICH_TEXT) return true; // 字符串包装为富文本
            if (inputport == ENTITY) return true; // 字符串尝试解析为 UUID 寻找实体
            if (inputport == BLOCK)  return true; // 字符串尝试解析为方块 Registry ID
            if (inputport == ITEM)   return true; // 字符串尝试解析为物品 Registry ID
            if (inputport == SLOT)   return true; // 字符串解析为槽位引用
            if (inputport == BOOLEAN) return true; // 字符串尝试解析为 "true"/"false"
            if (inputport == INTEGER || inputport == LONG || inputport == FLOAT) return true;
        }

        // 4. 富文本可降级为字符串
        if (outputport == RICH_TEXT && inputport == STRING) {
            return true;
        }

        // 5. 新颜色值与旧 ARGB 整数端口互通
        if ((outputport == COLOR && inputport == INTEGER) || (outputport == INTEGER && inputport == COLOR)) {
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
        if (value instanceof Long) return LONG;
        if (value instanceof Float || value instanceof Double) return FLOAT;
        if (value instanceof Boolean) return BOOLEAN;
        if (value instanceof String) return STRING;
        if (value instanceof RichTextValue) return RICH_TEXT;
        if (value instanceof ColorValue) return COLOR;
        if (value instanceof GeometryValue) return GEOMETRY;
        if (value instanceof SlotRef) return SLOT;
        if (value instanceof net.minecraft.world.entity.Entity) return ENTITY;
        if (value instanceof EntityTemplateValue) return ENTITY_TEMPLATE;
        if (value instanceof net.minecraft.world.item.ItemStack) return ITEM_STACK;
        if (value instanceof DialogueChoiceValue) return DIALOGUE_CHOICE;
        if (value instanceof QuestConditionValue) return QUEST_CONDITION;
        if (value instanceof java.util.List) return LIST;
        if (value instanceof java.util.Map) return DICT;
        if (value instanceof net.minecraft.world.phys.Vec3) return XYZ;
        // 如果都不是，默认返回 ANY
        return ANY;
    }
}
