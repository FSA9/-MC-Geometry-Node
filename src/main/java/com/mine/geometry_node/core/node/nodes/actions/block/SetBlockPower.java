package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockPower extends AbstractSetBlockIntegerProperty {
    public static final String TYPE_ID = "set_block_power";

    public SetBlockPower() {
        super(TYPE_ID, BlockStateProperties.POWER.getName(), BlockStateProperties.POWER, 0, 0, 15);
    }
}
