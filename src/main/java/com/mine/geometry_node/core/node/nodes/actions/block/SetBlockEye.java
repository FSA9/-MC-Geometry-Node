package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockEye extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_eye";

    public SetBlockEye() {
        super(TYPE_ID, BlockStateProperties.EYE.getName(), BlockStateProperties.EYE, false);
    }
}
