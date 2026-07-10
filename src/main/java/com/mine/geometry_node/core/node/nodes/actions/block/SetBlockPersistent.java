package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockPersistent extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_persistent";

    public SetBlockPersistent() {
        super(TYPE_ID, BlockStateProperties.PERSISTENT.getName(), BlockStateProperties.PERSISTENT, false);
    }
}
