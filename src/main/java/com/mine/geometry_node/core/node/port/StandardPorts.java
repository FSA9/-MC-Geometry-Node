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
    CHOICE("choice", PortType.EXECUTION),
    CLOSED("closed", PortType.EXECUTION),

    // Int
    INT("int", PortType.INTEGER),
    MIN_INT("min_int", PortType.INTEGER),
    MAX_INT("max_int", PortType.INTEGER),
    LIMIT("limit", PortType.INTEGER),
    INDEX("index", PortType.INTEGER),
    ITERATION("iteration", PortType.INTEGER),
    TICK("tick", PortType.INTEGER),
    COUNT("count", PortType.INTEGER),
    REMOVED_COUNT("removed_count", PortType.INTEGER),
    VERTICES("vertices", PortType.INTEGER),
    RINGS("rings", PortType.INTEGER),
    VERTICES_X("vertices_x", PortType.INTEGER),
    VERTICES_Y("vertices_y", PortType.INTEGER),
    VERTICES_Z("vertices_z", PortType.INTEGER),
    SIDE_SEGMENTS("side_segments", PortType.INTEGER),
    FILL_SEGMENTS("fill_segments", PortType.INTEGER),
    MAX_BLOCKS("max_blocks", PortType.INTEGER),
    INTERVAL("interval", PortType.INTEGER),
    OFFSET("offset", PortType.INTEGER),
    TELEPORT_DURATION("teleport_duration", PortType.INTEGER),
    INTERPOLATION_DURATION("interpolation_duration", PortType.INTEGER),
    BRIGHTNESS("brightness", PortType.INTEGER),
    START_INTERPOLATION("start_interpolation", PortType.INTEGER),
    GLOW_COLOR("glow_color", PortType.INTEGER),
    TEXT_LINE_WIDTH("text_line_width", PortType.INTEGER),
    BACKGROUND_COLOR("background_color", PortType.INTEGER),

    // Long
    GAME_TIME("game_time", PortType.LONG),
    WORLD_TIME("world_time", PortType.LONG),

    // Bool
    BOOL("bool", PortType.BOOLEAN),
    CONDITION("condition", PortType.BOOLEAN),
    DEBUG("debug", PortType.BOOLEAN),
    CASE("case", PortType.BOOLEAN),
    IS_HIT("is_hit", PortType.BOOLEAN),
    IS_BLOCK_BREAK("is_block_break", PortType.BOOLEAN),
    IS_FIRE_GEN("is_fire_gen", PortType.BOOLEAN),
    PENETRATE_SOLID("penetrate_solid", PortType.BOOLEAN),
    PENETRATE_TRANS("penetrate_trans", PortType.BOOLEAN),
    PENETRATE_ENTITIES("penetrate_entities", PortType.BOOLEAN),
    SEE_THROUGH("see_through", PortType.BOOLEAN),
    TEXT_SHADOW("text_shadow", PortType.BOOLEAN),
    RESPONSIVE("responsive", PortType.BOOLEAN),
    GRAVITY("gravity", PortType.BOOLEAN),
    INVISIBLE("invisible", PortType.BOOLEAN),
    CHOICE_VISIBLE("choice_visible", PortType.BOOLEAN),
    CHOICE_ENABLED("choice_enabled", PortType.BOOLEAN),
    REPLACE_EXISTING("replace_existing", PortType.BOOLEAN),
    REPLACE_AIR("replace_air", PortType.BOOLEAN),
    REPLACE_BLOCKS("replace_blocks", PortType.BOOLEAN),
    ONLY_SELF_VISIBLE("only_self_visible", PortType.BOOLEAN),
    REPAIR_AIR("repair_air", PortType.BOOLEAN),
    AFFECT_ENTITIES("affect_entities", PortType.BOOLEAN),
    UNIQUE_IF_EXISTS("unique_if_exists", PortType.BOOLEAN),

    // Float
    VALUE("value", PortType.FLOAT),
    FLOAT("float", PortType.FLOAT),
    MIN_FLOAT("min_float", PortType.FLOAT),
    MAX_FLOAT("max_float", PortType.FLOAT),
    DIST("distance", PortType.FLOAT),
    RADIUS("radius", PortType.FLOAT),
    SIZE_1("size_1", PortType.FLOAT),
    TIME("time", PortType.FLOAT),
    VOLUME("volume", PortType.FLOAT),
    SPEED("speed", PortType.FLOAT),
    PITCH("pitch", PortType.FLOAT),
    YAW("yaw", PortType.FLOAT),
    X("X", PortType.FLOAT),
    Y("Y", PortType.FLOAT),
    Z("Z", PortType.FLOAT),
    SHADOW_RADIUS("shadow_radius", PortType.FLOAT),
    SHADOW_STRENGTH("shadow_strength", PortType.FLOAT),
    VIEW_RANGE("view_range", PortType.FLOAT),
    WIDTH("width", PortType.FLOAT),
    HEIGHT("height", PortType.FLOAT),
    DEPTH("depth", PortType.FLOAT),
    TEXT_OPACITY("text_opacity", PortType.FLOAT),
    ALPHA("alpha", PortType.FLOAT),
    FAC("fac", PortType.FLOAT),


    // String
    NAME("name", PortType.STRING),
    TITLE("title", PortType.STRING),
    STRING("string", PortType.STRING),
    PATH("path", PortType.PATH),
    TYPE("type", PortType.STRING),
    MATCH_MODE("match_mode", PortType.STRING),
    GAMEMODE("gamemode", PortType.STRING),
    SORT("sort", PortType.STRING),
    SHAPE("shape", PortType.STRING),
    TAG("tag", PortType.STRING),
    TEAM("team", PortType.STRING),
    PARTICLE("particle", PortType.STRING),
    SCOPE("scope", PortType.STRING),
    EXPRESSION("expression", PortType.STRING),
    MESSAGE("message", PortType.STRING),
    DIMENSION("dimension", PortType.STRING),
    DAMAGE_TYPE("damage_type", PortType.STRING),
    SOUND_TYPE("sound_type", PortType.STRING),
    ITEM_TYPE("item_type", PortType.STRING),
    PREDICATE("predicate", PortType.STRING),
    KEY("key", PortType.STRING),
    BILLBOARD("billboard", PortType.STRING),
    ENTRY_ID("entry_id", PortType.STRING),
    SHOP_ID("shop_id", PortType.STRING),
    OFFER_ID("offer_id", PortType.STRING),
    GRAPH_ID("graph_id", PortType.STRING),
    TEMPLATE("template", PortType.STRING),
    STYLE_ID("style_id", PortType.STRING),
    VARIABLE_NAME("variable_name", PortType.STRING),
    FILL_TYPE("fill_type", PortType.STRING),
    VOXEL_MODE("voxel_mode", PortType.STRING),

    // Entity
    ENTITY("entity", PortType.ENTITY),
    PLAYER("player", PortType.ENTITY),
    BUYER("buyer", PortType.ENTITY),
    SELLER("seller", PortType.ENTITY),
    SPEAKER_ENTITY("speaker_entity", PortType.ENTITY),
    TRIGGER_ENTITY("trigger_entity", PortType.ENTITY),
    SOURCE_ENTITY("source_entity", PortType.ENTITY),
    TARGET_ENTITY("target_entity", PortType.ENTITY),
    ATTACK_SOURCE("attack_source", PortType.ENTITY),
    DIRECT_SOURCE("direct_source", PortType.ENTITY),

    // Block
    BLOCK_STATE("block_state", PortType.BLOCK),

    // Geometry
    GEOMETRY("geometry", PortType.GEOMETRY),

    // Item
    ITEM("item", PortType.ITEM),

    // Item Stack
    ITEM_STACK("item_stack", PortType.ITEM_STACK),

    // Slot
    SLOT("slot", PortType.SLOT),

    // LIST
    LIST("list", PortType.LIST),
    LIST_XYZ("list_xyz", PortType.LIST),
    COSTS("costs", PortType.LIST),
    REWARDS("rewards", PortType.LIST),

    // DICT
    DICT("dict", PortType.DICT),
    DATA("data", PortType.DICT),
    BLOCK_STATS("block_stats", PortType.DICT),
    SHOP_DATA("shop_data", PortType.SHOP),

    // XYZ
    XYZ("xyz", PortType.XYZ),
    HIT_POS("hit_pos", PortType.XYZ),
    START_POS("start_pos", PortType.XYZ),
    END_POS("end_pos", PortType.XYZ),
    CENTER("center", PortType.XYZ),
    VECTOR("vector", PortType.XYZ),
    NORMAL("normal", PortType.XYZ),
    ROTATION("rotation", PortType.XYZ),
    MIRROR("mirror", PortType.XYZ),
    SIZE_3("size_3", PortType.XYZ),
    SPREAD("spread", PortType.XYZ),
    TRANSLATION("translation", PortType.XYZ),

    // Color
    COLOR("color", PortType.COLOR),

    // ANY

    ANY_VALUE("any_value", PortType.ANY),
    VARIABLE_VALUE("variable_value", PortType.ANY);

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
