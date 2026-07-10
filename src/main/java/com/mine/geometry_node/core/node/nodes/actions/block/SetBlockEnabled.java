package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockEnabled extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_enabled";

    public SetBlockEnabled() {
        super(TYPE_ID, BlockStateProperties.ENABLED.getName(), BlockStateProperties.ENABLED, true);
    }
}
