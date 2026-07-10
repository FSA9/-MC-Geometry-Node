package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockOpen extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_open";

    public SetBlockOpen() {
        super(TYPE_ID, BlockStateProperties.OPEN.getName(), BlockStateProperties.OPEN, false);
    }
}
