package com.mine.geometry_node.core.node.nodes.data.value;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.TypeConverter;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class StringExpression extends BaseNode {
    public static final String TYPE_ID = "string_expression";
    private static final int DEFAULT_PORT_COUNT = 1;
    private static final int MAX_PORT_COUNT = 26;
    private static final String DEFAULT_EXPRESSION = "A";

    private static final String[] PORT_IDS = new String[MAX_PORT_COUNT + 1];
    private static final Component[] COMPONENT_KEYS = new Component[MAX_PORT_COUNT + 1];

    static {
        for (int i = 1; i <= MAX_PORT_COUNT; i++) {
            char varName = (char) ('A' + (i - 1));
            PORT_IDS[i] = "var_" + i;
            COMPONENT_KEYS[i] = Component.literal(String.valueOf(varName));
        }
    }

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(DEFAULT_PORT_COUNT);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDef(resolvePortCount(instanceData));
    }

    private NodeDef buildDef(int portCount) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .output(StandardPorts.STRING, "string")
                .input(StandardPorts.EXPRESSION, "expression");
        for (int i = 1; i <= portCount; i++) {
            comment.input(PORT_IDS[i], "variable");
        }

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.string_expression"))
                .comment(comment.build())
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, MAX_PORT_COUNT);

        builder.addRow(new PortRow(StandardPorts.EXPRESSION.toInput(DEFAULT_EXPRESSION), StandardPorts.STRING.toOutput(), UIHint.INPUT, null, null));

        for (int i = 1; i <= portCount; i++) {
            PortDef dynamicPort = new PortDef(PORT_IDS[i], COMPONENT_KEYS[i], PortType.ANY, null, false);
            builder.addRow(new PortRow(
                    dynamicPort, null, UIHint.DEFAULT, null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.DYNAMIC_INDEX, i)
            ));
        }

        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.STRING.getId().equals(portName)) {
            return null;
        }

        String expression = getInput(context, StandardPorts.EXPRESSION.getId(), String.class);
        if (expression == null || expression.isBlank()) {
            expression = DEFAULT_EXPRESSION;
        }

        int portCount = resolveRuntimePortCount(context);
        String[] values = new String[MAX_PORT_COUNT + 1];
        for (int i = 1; i <= portCount; i++) {
            values[i] = stringify(getRawInput(context, PORT_IDS[i]), context);
        }

        return evaluate(expression, values, portCount);
    }

    private static String evaluate(String expression, String[] values, int portCount) {
        StringBuilder result = new StringBuilder();
        StringBuilder token = new StringBuilder();
        Character quote = null;
        boolean escaping = false;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (quote != null) {
                if (escaping) {
                    appendEscaped(token, c);
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == quote) {
                    result.append(token);
                    token.setLength(0);
                    quote = null;
                } else {
                    token.append(c);
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                appendUnquoted(result, token, values, portCount);
                quote = c;
                continue;
            }

            if (c == '+') {
                appendUnquoted(result, token, values, portCount);
                continue;
            }

            token.append(c);
        }

        if (quote != null) {
            result.append(token);
        } else {
            appendUnquoted(result, token, values, portCount);
        }

        return result.toString();
    }

    private static void appendUnquoted(StringBuilder result, StringBuilder token, String[] values, int portCount) {
        String raw = token.toString();
        token.setLength(0);

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        int varIndex = variableIndex(trimmed);
        if (varIndex >= 1 && varIndex <= portCount) {
            result.append(values[varIndex] == null ? "" : values[varIndex]);
            return;
        }

        result.append(raw);
    }

    private static void appendEscaped(StringBuilder token, char c) {
        switch (c) {
            case 'n' -> token.append('\n');
            case 'r' -> token.append('\r');
            case 't' -> token.append('\t');
            case '\\' -> token.append('\\');
            case '"' -> token.append('"');
            case '\'' -> token.append('\'');
            default -> token.append(c);
        }
    }

    private static int variableIndex(String token) {
        if (token.length() != 1) {
            return -1;
        }
        char c = token.charAt(0);
        if (c < 'A' || c > 'Z') {
            return -1;
        }
        return c - 'A' + 1;
    }

    private static String stringify(Object raw, ExecutionContext context) {
        if (raw == null) {
            return "";
        }
        String converted = TypeConverter.convert(raw, String.class, context);
        return converted == null ? String.valueOf(raw) : converted;
    }

    private static int resolvePortCount(NodeData instanceData) {
        Object countObj = instanceData == null || instanceData.inputs == null
                ? null
                : instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        return resolvePortCount(countObj);
    }

    private static int resolveRuntimePortCount(ExecutionContext context) {
        return resolvePortCount(context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()));
    }

    private static int resolvePortCount(Object countObj) {
        int portCount = DEFAULT_PORT_COUNT;
        if (countObj instanceof Number number) {
            portCount = number.intValue();
        } else if (countObj instanceof String string) {
            try {
                portCount = Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(DEFAULT_PORT_COUNT, Math.min(portCount, MAX_PORT_COUNT));
    }
}
