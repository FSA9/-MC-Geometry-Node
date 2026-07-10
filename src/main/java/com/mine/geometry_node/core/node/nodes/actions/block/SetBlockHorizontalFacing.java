package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockHorizontalFacing extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_horizontal_facing";

    public SetBlockHorizontalFacing() {
        super(TYPE_ID, BlockStateProperties.HORIZONTAL_FACING.getName(), BlockStateProperties.HORIZONTAL_FACING, "north");
    }
}
