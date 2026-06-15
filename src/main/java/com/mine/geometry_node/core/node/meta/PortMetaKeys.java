package com.mine.geometry_node.core.node.meta;

public class PortMetaKeys {
    /**
     * 标记该端口行是否为动态端口
     */
    public static final MetaKey<Boolean> IS_DYNAMIC = new MetaKey<>("is_dynamic");

    public static final MetaKey<Boolean> IS_GROUP_VIRTUAL_DYNAMIC = new MetaKey<>("is_group_virtual_dynamic");

    public static final MetaKey<Integer> DYNAMIC_INDEX = new MetaKey<>("dynamic_index");

    /**
     * 下拉框控件的可选列表
     */
    public static final MetaKey<String[]> OPTIONS = new MetaKey<>("options");

    /**
     * [UI 指令] 动态注册表 ID (用于按需拉取数据，如 "minecraft:dimension")
     */
    public static final MetaKey<String> DYNAMIC_REGISTRY_ID = new MetaKey<>("dynamic_registry");

    /**
     * [UI 指令] 按钮显示文本。
     */
    public static final MetaKey<String> BUTTON_LABEL = new MetaKey<>("button_label");

    /**
     * [UI 指令] 按钮动作 ID，由客户端 UI 渲染器分发处理。
     */
    public static final MetaKey<String> BUTTON_ACTION = new MetaKey<>("button_action");

    /**
     * [UI 指令] 按钮背景色 ARGB。
     */
    public static final MetaKey<Integer> BUTTON_COLOR = new MetaKey<>("button_color");

    /**
     * [UI 指令] 按钮文字色 ARGB。
     */
    public static final MetaKey<Integer> BUTTON_TEXT_COLOR = new MetaKey<>("button_text_color");
}
