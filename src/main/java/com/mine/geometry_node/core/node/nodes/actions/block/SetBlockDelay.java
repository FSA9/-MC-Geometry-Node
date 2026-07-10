package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockDelay extends AbstractSetBlockIntegerProperty {
    public static final String TYPE_ID = "set_block_delay";

    public SetBlockDelay() {
        super(TYPE_ID, BlockStateProperties.DELAY.getName(), BlockStateProperties.DELAY, 1, 1, 4);
    }
}
