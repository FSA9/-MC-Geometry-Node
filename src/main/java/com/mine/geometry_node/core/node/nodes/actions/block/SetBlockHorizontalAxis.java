package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockHorizontalAxis extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_horizontal_axis";

    public SetBlockHorizontalAxis() {
        super(TYPE_ID, BlockStateProperties.HORIZONTAL_AXIS.getName(), BlockStateProperties.HORIZONTAL_AXIS, "x");
    }
}
