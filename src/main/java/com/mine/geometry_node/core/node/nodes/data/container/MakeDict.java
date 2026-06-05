package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

public class MakeDict extends BaseNode {

    public static final String TYPE_ID = "make_dict";

    @Override
    public NodeDef getDefaultDefinition() {
        return getDefinition(null);
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
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.make_dict"))
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 30);

        builder.addRow(new PortRow(null, StandardPorts.DICT.toOutput(), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(StandardPorts.DICT.toInput(), null, UIHint.DEFAULT, null, null));

        for (int i = 1; i <= portCount; i++) {
            PortDef keyPort = new PortDef("dict_key_" + i, Component.literal("Key " + i), PortType.STRING, "", true);
            builder.addRow(new PortRow(
                    keyPort,
                    null,
                    UIHint.INPUT,
                    null,
                    Map.of(
                            PortMetaKeys.IS_DYNAMIC, true,
                            PortMetaKeys.DYNAMIC_INDEX, i
                    )
            ));
            PortDef valPort = new PortDef("dict_value_" + i, Component.literal("Value " + i), PortType.ANY, null, false);
            builder.addRow(new PortRow(
                    valPort,
                    null,
                    UIHint.DEFAULT,
                    null,
                    Map.of(
                            PortMetaKeys.IS_DYNAMIC, true
                    )
            ));
        }

        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.DICT.getId().equals(portName)) return null;

        Map<String, Object> resultDict = new HashMap<>();

        Map<String, Object> baseDict = getInputDict(context, StandardPorts.DICT.getId());
        if (baseDict != null) {
            resultDict.putAll(baseDict);
        }

        int portCount = 1;
        Object countObj = context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        if (countObj instanceof Number n) portCount = Math.max(1, n.intValue());

        for (int i = 1; i <= portCount; i++) {
            String key = getInput(context, "dict_key_" + i, String.class);
            Object value = getRawInput(context, "dict_value_" + i);

            if (key != null && !key.trim().isEmpty() && value != null) {
                resultDict.put(key.trim(), value);
            }
        }

        return resultDict;
    }
}