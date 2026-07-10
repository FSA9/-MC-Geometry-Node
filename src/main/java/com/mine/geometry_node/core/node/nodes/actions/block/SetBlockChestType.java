package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockChestType extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_chest_type";

    public SetBlockChestType() {
        super(TYPE_ID, "chest_type", BlockStateProperties.CHEST_TYPE.getName(), "single", new String[]{"single", "left", "right"});
    }
}
