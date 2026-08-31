package com.mine.geometry_node.core.node.nodes.events.block;

import com.mine.geometry_node.core.engine.blueprint.multiblock.MultiblockStructureManager;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class OnMultiblockBuilt extends BaseEventNode {
    public static final String TYPE_ID = "on_multiblock_built";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_multiblock_built"))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput(MultiblockStructureManager.ANY_STRUCTURE_ID).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, MultiblockStructureManager.DYNAMIC_REGISTRY_ID)
                ))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.NAME.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.DIMENSION.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BLOCK_STATE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String expected = getInput(context, StandardPorts.TYPE.getId(), String.class);
        String actual = (String) context.getEventData(StandardPorts.NAME.getId());
        if (expected != null
                && !expected.isBlank()
                && !MultiblockStructureManager.ANY_STRUCTURE_ID.equals(expected)
                && !expected.equals(actual)) {
            return finish();
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
