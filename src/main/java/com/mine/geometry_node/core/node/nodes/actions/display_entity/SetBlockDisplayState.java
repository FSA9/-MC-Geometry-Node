package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

public class SetBlockDisplayState extends BaseNode {

    public static final String TYPE_ID = "set_block_display_state";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_block_display_state"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.BLOCK_STATE.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput().hiddenPin(), null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.getAllBlocks().toArray(new String[0]))
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return next(StandardPorts.FLOW_OUT.getId());

        BlockState blockState = getInput(context, StandardPorts.BLOCK_STATE.getId(), BlockState.class);
        if (blockState == null) {
            String selectedBlockId = getInput(context, StandardPorts.STRING.getId(), String.class);
            if (selectedBlockId != null && !selectedBlockId.isEmpty()) {
                blockState = TypeConverter.convert(selectedBlockId, BlockState.class, context);
            }
        }
        if (blockState == null) return next(StandardPorts.FLOW_OUT.getId());

        for (Entity entity : entities) {
            if (entity instanceof Display.BlockDisplay blockDisplayEntity) {
                CompoundTag nbt = EntityNbtCompat.saveWithoutId(blockDisplayEntity);

                nbt.put("block_state", NbtUtils.writeBlockState(blockState));

                EntityNbtCompat.load(blockDisplayEntity, nbt);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
