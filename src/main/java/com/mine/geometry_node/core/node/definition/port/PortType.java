package com.mine.geometry_node.core.node.definition.port;

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
    EXECUTION(0xFFFFFFFF, null, true),
    BEHAVIOR_STRUCTURE(0xFF5C9E72, null, true),

    INTEGER(0xFF4A90E2, 0),
    LONG(0xFF3F7DC2, 0L),
    FLOAT(0xFF50C878, 0.0f),
    BOOLEAN(0xFFE74C3C, false),
    STRING(0xFF9B59B6, ""),
    PATH(0xFF5F6670, ""),
    RICH_TEXT(0xFFD56BE8, RichTextValue.EMPTY),
    ENTITY(0xFFE91E63, null),
    ENTITY_TEMPLATE(0xFFFF5C8A, EntityTemplateValue.EMPTY),
    ITEM(0xFFFF8A65, null),
    ITEM_STACK(0xFFFF7043, null),
    SLOT(0xFFB0BEC5, SlotRef.DEFAULT.serialize()),
    BLOCK(0xFF8D6E63, null),
    GEOMETRY(0xFF26A69A, GeometryValue.EMPTY),
    XYZ(0xFF00BCD4, List.of(0.0f, 0.0f, 0.0f)),
    COLOR(0xFFFFD54F, ColorValue.WHITE),
    LIST(0xFFFF9800, List.of()),
    DIALOGUE_CHOICE(0xFFFF80AB, null),
    QUEST_CONDITION(0xFFFFA726, null),
    DICT(0xFFE67E22, java.util.Map.of()),
    SHOP(0xFFFFB74D, java.util.Map.of("offers", List.of())),
    ANY(0xFF95A5A6, null);

    private final int color;
    private final Object defaultValue;
    private final boolean flow;

    PortType(int color, Object defaultValue) {
        this(color, defaultValue, false);
    }

    PortType(int color, Object defaultValue, boolean flow) {
        this.color = color;
        this.defaultValue = defaultValue;
        this.flow = flow;
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
        return PortConversionRegistry.isCompatible(outputport, inputport);
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
        if (value instanceof net.minecraft.world.item.Item) return ITEM;
        if (value instanceof net.minecraft.world.item.ItemStack) return ITEM_STACK;
        if (value instanceof net.minecraft.world.level.block.state.BlockState) return BLOCK;
        if (value instanceof DialogueChoiceValue) return DIALOGUE_CHOICE;
        if (value instanceof QuestConditionValue) return QUEST_CONDITION;
        if (value instanceof java.util.List) return LIST;
        if (value instanceof java.util.Map) return DICT;
        if (value instanceof net.minecraft.world.phys.Vec3) return XYZ;
        if (value instanceof net.minecraft.core.BlockPos) return XYZ;
        // 如果都不是，默认返回 ANY
        return ANY;
    }
}
