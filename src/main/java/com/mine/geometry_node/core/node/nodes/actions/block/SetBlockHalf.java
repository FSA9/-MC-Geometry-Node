package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockHalf extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_half";

    public SetBlockHalf() {
        super(TYPE_ID, BlockStateProperties.HALF.getName(), BlockStateProperties.HALF, "bottom");
    }
}
