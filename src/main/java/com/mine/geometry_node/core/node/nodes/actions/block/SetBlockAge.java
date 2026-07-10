package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockAge extends AbstractSetBlockIntegerProperty {
    public static final String TYPE_ID = "set_block_age";

    public SetBlockAge() {
        super(TYPE_ID, BlockStateProperties.AGE_25.getName(), BlockStateProperties.AGE_25, 0, 0, 25);
    }
}
