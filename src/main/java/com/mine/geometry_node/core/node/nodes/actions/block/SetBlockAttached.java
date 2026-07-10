package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockAttached extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_attached";

    public SetBlockAttached() {
        super(TYPE_ID, BlockStateProperties.ATTACHED.getName(), BlockStateProperties.ATTACHED, false);
    }
}
