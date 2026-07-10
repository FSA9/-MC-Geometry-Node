package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockComparatorMode extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_comparator_mode";

    public SetBlockComparatorMode() {
        super(TYPE_ID, "comparator_mode", BlockStateProperties.MODE_COMPARATOR.getName(), "compare", new String[]{"compare", "subtract"});
    }
}
