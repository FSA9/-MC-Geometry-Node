package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockExtended extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_extended";

    public SetBlockExtended() {
        super(TYPE_ID, BlockStateProperties.EXTENDED.getName(), BlockStateProperties.EXTENDED, false);
    }
}
