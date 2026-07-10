package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockDisarmed extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_disarmed";

    public SetBlockDisarmed() {
        super(TYPE_ID, BlockStateProperties.DISARMED.getName(), BlockStateProperties.DISARMED, false);
    }
}
