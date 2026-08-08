package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.TypeConverter;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Formats dialogue text with strict, case-sensitive placeholders.
 *
 * Syntax:
 * - {name}: replaced by the dynamic variable named "name".
 * - {{ and }}: escaped literal braces.
 * - Missing or invalid placeholders are kept unchanged.
 */
public class FormatDialogueText extends BaseNode {
    public static final String TYPE_ID = "format_dialogue_text";
    public static final int DEFAULT_VARIABLE_COUNT = 1;
    public static final int MAX_VARIABLE_COUNT = 20;

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(DEFAULT_VARIABLE_COUNT);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDef(resolveVariableCount(instanceData));
    }

    private NodeDef buildDef(int variableCount) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .output(StandardPorts.STRING, "string")
                .input(StandardPorts.TEMPLATE, "template")
                .input(StandardPorts.VARIABLE_NAME.getIdWithIndex(1), "variable_name")
                .input(StandardPorts.VARIABLE_VALUE.getIdWithIndex(1), "variable_value");

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.format_dialogue_text"))
                .comment(comment.build())
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, MAX_VARIABLE_COUNT);

        builder.addRow(new PortRow(
                StandardPorts.TEMPLATE.toInput(""),
                StandardPorts.STRING.toOutput(),
                UIHint.INPUT, null, null
        ));

        for (int i = 1; i <= variableCount; i++) {
            builder.addRow(new PortRow(
                    StandardPorts.VARIABLE_NAME.toInputWithIndex(i, "").hiddenPin(),
                    null,
                    UIHint.INPUT, null, dynamicGroupHead(i)
            ));
            builder.addRow(new PortRow(
                    StandardPorts.VARIABLE_VALUE.toInputWithIndex(i),
                    null,
                    UIHint.DEFAULT, null, dynamicGroupRow()
            ));
        }

        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.STRING.getId().equals(portName)) {
            return null;
        }

        String template = getInput(context, StandardPorts.TEMPLATE.getId(), String.class);
        if (template == null || template.isEmpty()) {
            return "";
        }

        Map<String, String> variables = collectVariables(context);
        return format(template, variables);
    }

    private Map<String, String> collectVariables(ExecutionContext context) {
        Map<String, String> variables = new HashMap<>();
        int variableCount = resolveRuntimeVariableCount(context);

        for (int i = 1; i <= variableCount; i++) {
            String name = getInput(context, variableNamePort(i), String.class);
            if (name == null || name.isBlank()) {
                continue;
            }

            String variableName = name.trim();
            Object rawValue = getRawInput(context, variableValuePort(i));
            if (rawValue == null) {
                continue;
            }

            String value = TypeConverter.convert(rawValue, String.class, context);
            variables.put(variableName, value == null ? String.valueOf(rawValue) : value);
        }

        return variables;
    }

    private static String format(String template, Map<String, String> variables) {
        StringBuilder out = new StringBuilder(template.length());

        for (int i = 0; i < template.length(); i++) {
            char current = template.charAt(i);

            if (current == '{') {
                if (i + 1 < template.length() && template.charAt(i + 1) == '{') {
                    out.append('{');
                    i++;
                    continue;
                }

                int close = template.indexOf('}', i + 1);
                if (close < 0) {
                    out.append(template.substring(i));
                    break;
                }

                String key = template.substring(i + 1, close);
                if (isValidPlaceholder(key) && variables.containsKey(key)) {
                    out.append(variables.get(key));
                } else {
                    out.append('{').append(key).append('}');
                }
                i = close;
                continue;
            }

            if (current == '}' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
                out.append('}');
                i++;
                continue;
            }

            out.append(current);
        }

        return out.toString();
    }

    private static boolean isValidPlaceholder(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isWhitespace(c) || c == '{' || c == '}') {
                return false;
            }
        }
        return true;
    }

    private static int resolveVariableCount(NodeData instanceData) {
        Object countObj = instanceData == null || instanceData.inputs == null
                ? null
                : instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        return resolveVariableCount(countObj);
    }

    private static int resolveRuntimeVariableCount(ExecutionContext context) {
        return resolveVariableCount(context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()));
    }

    private static int resolveVariableCount(Object countObj) {
        int variableCount = DEFAULT_VARIABLE_COUNT;
        if (countObj instanceof Number number) {
            variableCount = number.intValue();
        } else if (countObj instanceof String string) {
            try {
                variableCount = Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(DEFAULT_VARIABLE_COUNT, Math.min(variableCount, MAX_VARIABLE_COUNT));
    }

    private static String variableNamePort(int index) {
        return StandardPorts.VARIABLE_NAME.getIdWithIndex(index);
    }

    private static String variableValuePort(int index) {
        return StandardPorts.VARIABLE_VALUE.getIdWithIndex(index);
    }

    private static Map<MetaKey<?>, Object> dynamicGroupHead(int index) {
        return Map.of(
                PortMetaKeys.IS_DYNAMIC, true,
                PortMetaKeys.DYNAMIC_INDEX, index
        );
    }

    private static Map<MetaKey<?>, Object> dynamicGroupRow() {
        return Map.of(PortMetaKeys.IS_DYNAMIC, true);
    }
}
