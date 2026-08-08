package com.mine.geometry_node.core.node.nodes.functions.color;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.MetaKey;
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

public class CombineColor extends BaseNode {
    public static final String TYPE_ID = "combine_color";

    private static final String[] MODES = {"RGB", "HSV", "HSL"};
    private static final String MODE_RGB = "RGB";
    private static final String MODE_HSV = "HSV";
    private static final String MODE_HSL = "HSL";
    private static final int CHANNEL_1 = 1;
    private static final int CHANNEL_2 = 2;
    private static final int CHANNEL_3 = 3;
    private static final Map<MetaKey<?>, Object> NORMALIZED_RANGE = Map.of(
            PortMetaKeys.NUMERIC_MIN, 0.0f,
            PortMetaKeys.NUMERIC_MAX, 1.0f
    );

    @Override
    public NodeDef getDefaultDefinition() {
        return getDefinition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        String mode = resolveMode(instanceData);
        String[] channelNames = channelNames(mode);

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.combine_color"));
        builder.addRow(new PortRow(null, StandardPorts.COLOR.toOutput(), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(
                StandardPorts.STRING.toInput(mode).hiddenPin(), null, UIHint.SELECT, null,
                Map.of(PortMetaKeys.OPTIONS, MODES)));
        builder.addRow(new PortRow(channelInput(CHANNEL_1, channelNames[0]), null, UIHint.INPUT, null, NORMALIZED_RANGE));
        builder.addRow(new PortRow(channelInput(CHANNEL_2, channelNames[1]), null, UIHint.INPUT, null, NORMALIZED_RANGE));
        builder.addRow(new PortRow(channelInput(CHANNEL_3, channelNames[2]), null, UIHint.INPUT, null, NORMALIZED_RANGE));
        builder.addRow(new PortRow(StandardPorts.ALPHA.toInput(1.0f), null, UIHint.INPUT, null, NORMALIZED_RANGE));
        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.COLOR.getId().equals(portName)) {
            return null;
        }

        String mode = resolveMode(getInput(context, StandardPorts.STRING.getId(), String.class));
        float c1 = normalizedInput(context, channelId(CHANNEL_1), 0.0f);
        float c2 = normalizedInput(context, channelId(CHANNEL_2), 0.0f);
        float c3 = normalizedInput(context, channelId(CHANNEL_3), 0.0f);
        float alpha = normalizedInput(context, StandardPorts.ALPHA.getId(), 1.0f);

        return switch (mode) {
            case MODE_HSV -> ColorValue.fromHsv(c1, c2, c3, alpha);
            case MODE_HSL -> ColorValue.fromHsl(c1, c2, c3, alpha);
            default -> ColorValue.rgb(c1, c2, c3, alpha);
        };
    }

    private static PortDef channelInput(int index, String displayName) {
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

    private float normalizedInput(ExecutionContext context, String portId, float fallback) {
        Float value = getInput(context, portId, Float.class);
        if (value == null) {
            value = fallback;
        }
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
