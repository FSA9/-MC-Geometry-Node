package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockAttachFace extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_attach_face";

    public SetBlockAttachFace() {
        super(TYPE_ID, BlockStateProperties.ATTACH_FACE.getName(), BlockStateProperties.ATTACH_FACE, "wall");
    }
}
