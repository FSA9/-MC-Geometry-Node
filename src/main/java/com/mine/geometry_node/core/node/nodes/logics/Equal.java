package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.util.ValueMatchUtils;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class Equal extends BaseNode {

    public static final String TYPE_ID = "equal";
    private static final String PORT_A = "A";
    private static final String PORT_B = "B";
    private static final String COUNT_MODE = "count_mode";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.LOGIC, Component.translatable("geometry_node.node.equal"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.BOOL, "bool")
                        .input(PORT_A, "a")
                        .input(PORT_B, "b")
                        .input(StandardPorts.TYPE, "type")
                        .input(COUNT_MODE, "count_mode")
                        .build())
                .addRow(new PortRow(
                        new PortDef(PORT_A, Component.literal("A"), PortType.ANY, null, false),
                        StandardPorts.BOOL.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        new PortDef(PORT_B, Component.literal("B"), PortType.ANY, null, false),
                        null,
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput(ValueMatchUtils.MODE_COMPONENTS).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, ValueMatchUtils.MODE_OPTIONS)
                ))
                .addRow(new PortRow(
                        PortDef.create(COUNT_MODE, "geometry_node.port.count_mode", PortType.STRING, ValueMatchUtils.COUNT_IGNORE).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, ValueMatchUtils.COUNT_OPTIONS)
                ))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        Object a = getRawInput(context, PORT_A);
        Object b = getRawInput(context, PORT_B);
        String mode = getInput(context, StandardPorts.TYPE.getId(), String.class);
        String countMode = getInput(context, COUNT_MODE, String.class);

        return valuesEqual(a, b, mode, countMode, context);
    }

    static boolean valuesEqual(Object a, Object b, String rawMode, String rawCountMode, GraphDataContext context) {
        return ValueMatchUtils.valuesEqual(a, b, rawMode, rawCountMode, context);
    }
}
