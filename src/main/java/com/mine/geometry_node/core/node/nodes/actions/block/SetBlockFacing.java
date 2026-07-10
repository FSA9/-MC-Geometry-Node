package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockFacing extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_facing";

    public SetBlockFacing() {
        super(TYPE_ID, BlockStateProperties.FACING.getName(), BlockStateProperties.FACING, "north");
    }
}
