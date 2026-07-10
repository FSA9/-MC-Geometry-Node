package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockTriggered extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_triggered";

    public SetBlockTriggered() {
        super(TYPE_ID, BlockStateProperties.TRIGGERED.getName(), BlockStateProperties.TRIGGERED, false);
    }
}
