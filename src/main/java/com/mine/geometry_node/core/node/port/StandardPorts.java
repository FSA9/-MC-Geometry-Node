package com.mine.geometry_node.core.node.port;

/**
 * [标准端口字典]
 * 统一定义全系统共用的端口 ID、类型和翻译键。
 */
public enum StandardPorts {
    // Flow
    FLOW_IN("flow_in", PortType.EXECUTION),
    FLOW_OUT("flow_out", PortType.EXECUTION),
    LOOP("loop", PortType.EXECUTION),
    COMPLETED("completed", PortType.EXECUTION),
    FLOW_TRUE("flow_true", PortType.EXECUTION),
    FLOW_FALSE("flow_false", PortType.EXECUTION),

    // Int
    INT("int", PortType.INTEGER),
    INDEX("index", PortType.INTEGER),
    TICK("tick", PortType.INTEGER),
    COLOR("color", PortType.INTEGER),

    // Bool
    BOOL("bool", PortType.BOOLEAN),
    DEBUG("debug", PortType.BOOLEAN),
    CASE("case", PortType.BOOLEAN),
    IS_HIT("is_hit", PortType.BOOLEAN),

    // Float
    VALUE("value", PortType.FLOAT),
    DIST("distance", PortType.FLOAT),
    RADIUS("radius", PortType.FLOAT),
    SIZE_1("size_1", PortType.FLOAT),
    TIME("time", PortType.FLOAT),

    // String
    NAME("name", PortType.STRING),
    TYPE("type", PortType.STRING),

    SCOPE("scope", PortType.STRING),
    EXPRESSION("expression", PortType.STRING),
    MESSAGE("message", PortType.STRING),
    DIMENSION("dimension", PortType.STRING),
    DAMAGE_TYPE("damage_type", PortType.STRING),

    // Entity
    ENTITY("entity", PortType.ENTITY),
    TRIGGER_ENTITY("trigger_entity", PortType.ENTITY),
    SOURCE_ENTITY("source_entity", PortType.ENTITY),
    ATTACK_SOURCE("attack_source", PortType.ENTITY),
    DIRECT_SOURCE("direct_source", PortType.ENTITY),

    // Block
    BLOCK_STATE("block_state", PortType.BLOCK),

    // Item
    ITEM("item", PortType.ITEM),

    // Item Stack
    ITEM_STACK("item_stack", PortType.ITEM_STACK),

    // LIST
    LIST("list", PortType.LIST),

    // XYZ
    XYZ("xyz", PortType.XYZ),
    START_POS("start_pos", PortType.XYZ),
    END_POS("end_pos", PortType.XYZ),
    CENTER("center", PortType.XYZ),
    VECTOR("vector", PortType.XYZ),
    ROTATION("rotation", PortType.XYZ),
    SIZE_3("size_3", PortType.XYZ),

    // ANY

    ANY_VALUE("any_value", PortType.ANY);

    private final String id;
    private final PortType type;

    StandardPorts(String id, PortType type) {
        this.id = id;
        this.type = type;
    }

    public String getId() { return id; }
    public PortType getType() { return type; }
    public String getTranslationKey() { return "geometry_node.port." + id; }

    // --- 快捷构建工厂 ---

    public PortDef toInput() {
        return PortDef.create(id, getTranslationKey(), type);
    }

    public PortDef toInput(Object defaultValueOverride) {
        return PortDef.create(id, getTranslationKey(), type, defaultValueOverride);
    }

    public PortDef toOutput() {
        return PortDef.create(id, getTranslationKey(), type);
    }

    public PortDef toExec() {
        if (type != PortType.EXECUTION) throw new IllegalStateException("Not exec port: " + id);
        return PortDef.exec(id, getTranslationKey());
    }

    // --- 增强构建工厂 ---

    public String getIdWithIndex(int index) {
        return id + "_" + index;
    }

    public PortDef toInputWithIndex(int index) {
        return PortDef.create(getIdWithIndex(index), getTranslationKey(), type);
    }

    public PortDef toInputWithIndex(int index, Object defaultValueOverride) {
        return PortDef.create(getIdWithIndex(index), getTranslationKey(), type, defaultValueOverride);
    }

    public PortDef toOutputWithIndex(int index) {
        return PortDef.create(getIdWithIndex(index), getTranslationKey(), type);
    }

    public PortDef toExecWithIndex(int index) {
        if (type != PortType.EXECUTION) throw new IllegalStateException("Not exec port: " + id);
        return PortDef.exec(getIdWithIndex(index), getTranslationKey());
    }
}