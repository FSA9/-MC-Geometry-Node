package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockLit extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_lit";

    public SetBlockLit() {
        super(TYPE_ID, BlockStateProperties.LIT.getName(), BlockStateProperties.LIT, false);
    }
}
