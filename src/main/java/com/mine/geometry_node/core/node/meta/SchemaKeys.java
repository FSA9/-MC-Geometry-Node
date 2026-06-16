package com.mine.geometry_node.core.node.meta;

public class SchemaKeys {

    /**
     * 最大输入动态端口数
     */
    public static final MetaKey<Integer> MAX_DYNAMIC_INPUT = new MetaKey<>("max_dynamic_input_number");

    /**
     * 最小动态输入端口数
     */
    public static final MetaKey<Integer> MIN_DYNAMIC_INPUT = new MetaKey<>("min_dynamic_input_number");

    /**
     * 最大输出动态端口数
     */
    public static final MetaKey<Integer> MAX_DYNAMIC_OUTPUT = new MetaKey<>("max_dynamic_output_number");

    /**
     * 最小动态输出端口数
     */
    public static final MetaKey<Integer> MIN_DYNAMIC_OUTPUT = new MetaKey<>("min_dynamic_output_number");
}
