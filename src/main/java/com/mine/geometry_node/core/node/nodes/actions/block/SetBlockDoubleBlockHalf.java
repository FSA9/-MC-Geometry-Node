package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockDoubleBlockHalf extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_double_block_half";

    public SetBlockDoubleBlockHalf() {
        super(TYPE_ID, "double_block_half", BlockStateProperties.DOUBLE_BLOCK_HALF.getName(), "lower", new String[]{"upper", "lower"});
    }
}
