package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MakeList extends BaseNode {

    public static final String TYPE_ID = "make_list";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(1);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        int portCount = 1;
        if (instanceData != null && instanceData.inputs.containsKey(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id())) {
            Object countObj = instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
            if (countObj instanceof Number n) {
                portCount = Math.max(1, n.intValue());
            } else if (countObj instanceof String s) {
                try { portCount = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
        }
        portCount = Math.max(1, Math.min(portCount, 30));
        return buildDef(portCount);
    }

    private NodeDef buildDef(int portCount) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.make_list"))
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 30);

        builder.addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(StandardPorts.LIST.toInput(), null, UIHint.DEFAULT, null, null));
        for (int i = 1; i <= portCount; i++) {
            PortDef itemPort = new PortDef("list_item_" + i, Component.literal("Item " + i), PortType.ANY, null, false);

            builder.addRow(new PortRow(
                    itemPort,
                    null,
                    UIHint.DEFAULT,
                    null,
                    Map.of(
                            PortMetaKeys.IS_DYNAMIC, true,
                            PortMetaKeys.DYNAMIC_INDEX, i
                    )
            ));
        }

        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.LIST.getId().equals(portName)) return null;

        List<Object> resultList = new ArrayList<>();

        List<Object> baseList = getInputList(context, StandardPorts.LIST.getId(), Object.class);
        if (baseList != null && !baseList.isEmpty()) {
            resultList.addAll(baseList);
        }

        int portCount = 1;
        Object countObj = context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        if (countObj instanceof Number n) portCount = Math.max(1, n.intValue());
        else if (countObj instanceof String s) {
            try { portCount = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }

        for (int i = 1; i <= portCount; i++) {
            Object value = getRawInput(context, "list_item_" + i);
            if (value != null) {
                resultList.add(value);
            }
        }

        return resultList;
    }
}