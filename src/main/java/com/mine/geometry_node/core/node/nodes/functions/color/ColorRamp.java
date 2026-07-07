package com.mine.geometry_node.core.node.nodes.functions.color;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.ColorGradientValue;
import com.mine.geometry_node.core.node.value.ColorValue;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class ColorRamp extends BaseNode {
    public static final String TYPE_ID = "color_ramp";
    public static final String GRADIENT_INPUT = "color_ramp";

    private static final Map<MetaKey<?>, Object> NORMALIZED_RANGE = Map.of(
            PortMetaKeys.NUMERIC_MIN, 0.0f,
            PortMetaKeys.NUMERIC_MAX, 1.0f
    );

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.color_ramp"))
                .uiWidth(120)
                .addRow(new PortRow(null, StandardPorts.COLOR.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ALPHA.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, null, UIHint.CUSTOM, GRADIENT_INPUT, null))
                .addRow(new PortRow(StandardPorts.FAC.toInput(0.5f), null, UIHint.INPUT, null, NORMALIZED_RANGE))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        ColorValue sampled = sample(context);
        if (StandardPorts.COLOR.getId().equals(portName)) {
            return sampled;
        }
        if (StandardPorts.ALPHA.getId().equals(portName)) {
            return sampled.a();
        }
        return null;
    }

    private ColorValue sample(ExecutionContext context) {
        ColorGradientValue gradient = ColorGradientValue.from(context.getStaticInput(GRADIENT_INPUT));
        Float fac = getInput(context, StandardPorts.FAC.getId(), Float.class);
        return gradient.sample(fac != null ? fac : 0.5f);
    }
}
