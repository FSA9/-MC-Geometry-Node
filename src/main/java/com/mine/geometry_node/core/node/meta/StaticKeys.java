package com.mine.geometry_node.core.node.meta;

public class StaticKeys {
    public static final MetaKey<String> SELECTION = new MetaKey<>("selection");

    /**
     * 当前动态输入端口数量
     */
    public static final MetaKey<Integer> DYNAMIC_BRANCH_INPUT_COUNT = new MetaKey<>("dynamic_branch_input_count");

    /**
     * 当前动态输出端口数量
     */
    public static final MetaKey<Integer> DYNAMIC_BRANCH_OUTPUT_COUNT = new MetaKey<>("dynamic_branch_output_count");
}