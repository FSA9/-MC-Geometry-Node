package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockPowered extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_powered";

    public SetBlockPowered() {
        super(TYPE_ID, BlockStateProperties.POWERED.getName(), BlockStateProperties.POWERED, false);
    }
}
