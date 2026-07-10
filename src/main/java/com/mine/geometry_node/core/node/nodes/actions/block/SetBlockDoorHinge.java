package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockDoorHinge extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_door_hinge";

    public SetBlockDoorHinge() {
        super(TYPE_ID, "hinge", BlockStateProperties.DOOR_HINGE, "left");
    }
}
