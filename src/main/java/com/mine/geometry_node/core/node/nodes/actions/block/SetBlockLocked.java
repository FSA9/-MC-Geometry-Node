package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockLocked extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_locked";

    public SetBlockLocked() {
        super(TYPE_ID, BlockStateProperties.LOCKED.getName(), BlockStateProperties.LOCKED, false);
    }
}
