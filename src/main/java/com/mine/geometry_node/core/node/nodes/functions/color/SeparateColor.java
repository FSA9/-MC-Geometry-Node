package com.mine.geometry_node.core.node.nodes.functions.color;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;

public class SeparateColor extends BaseNode {
    public static final String TYPE_ID = "separate_color";

    private static final String[] MODES = {"RGB", "HSV", "HSL"};
    private static final String MODE_RGB = "RGB";
    private static final String MODE_HSV = "HSV";
    private static final String MODE_HSL = "HSL";
    private static final int CHANNEL_1 = 1;
    private static final int CHANNEL_2 = 2;
    private static final int CHANNEL_3 = 3;

    @Override
    public NodeDef getDefaultDefinition() {
        return getDefinition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        String mode = resolveMode(instanceData);
        String[] channelNames = channelNames(mode);

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.separate_color"));
        builder.addRow(new PortRow(null, outputPort(CHANNEL_1, channelNames[0]), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(null, outputPort(CHANNEL_2, channelNames[1]), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(null, outputPort(CHANNEL_3, channelNames[2]), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(null, StandardPorts.ALPHA.toOutput(), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(
                StandardPorts.STRING.toInput(mode).hiddenPin(), null, UIHint.SELECT, null,
                Map.of(PortMetaKeys.OPTIONS, MODES)));
        builder.addRow(new PortRow(StandardPorts.COLOR.toInput(ColorValue.WHITE), null, UIHint.INPUT, null, null));
        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        ColorValue color = getInput(context, StandardPorts.COLOR.getId(), ColorValue.class);
        if (color == null) {
            color = ColorValue.WHITE;
        }

        String mode = resolveMode(getInput(context, StandardPorts.STRING.getId(), String.class));
        float[] channels = switch (mode) {
            case MODE_HSV -> color.toHsv();
            case MODE_HSL -> color.toHsl();
            default -> new float[]{color.r(), color.g(), color.b()};
        };

        if (channelId(CHANNEL_1).equals(portName)) {
            return channels[0];
        }
        if (channelId(CHANNEL_2).equals(portName)) {
            return channels[1];
        }
        if (channelId(CHANNEL_3).equals(portName)) {
            return channels[2];
        }
        if (StandardPorts.ALPHA.getId().equals(portName)) {
            return color.a();
        }
        return null;
    }

    private static PortDef outputPort(int index, String displayName) {
        return new PortDef(channelId(index), Component.literal(displayName), PortType.FLOAT, 0.0f, false);
    }

    private static String channelId(int index) {
        return StandardPorts.FLOAT.getIdWithIndex(index);
    }

    private static String[] channelNames(String mode) {
        return switch (mode) {
            case MODE_HSV -> new String[]{"H", "S", "V"};
            case MODE_HSL -> new String[]{"H", "S", "L"};
            default -> new String[]{"R", "G", "B"};
        };
    }

    private static String resolveMode(NodeData instanceData) {
        if (instanceData == null || instanceData.inputs == null) {
            return MODE_RGB;
        }
        return resolveMode(instanceData.inputs.get(StandardPorts.STRING.getId()));
    }

    private static String resolveMode(Object rawMode) {
        if (rawMode == null) {
            return MODE_RGB;
        }
        String mode = String.valueOf(rawMode).trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case MODE_HSV -> MODE_HSV;
            case MODE_HSL -> MODE_HSL;
            default -> MODE_RGB;
        };
    }
}
