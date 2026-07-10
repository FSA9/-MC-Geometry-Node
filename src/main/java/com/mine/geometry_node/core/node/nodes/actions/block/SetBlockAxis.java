package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockAxis extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_axis";

    public SetBlockAxis() {
        super(TYPE_ID, BlockStateProperties.AXIS.getName(), BlockStateProperties.AXIS, "y");
    }
}
