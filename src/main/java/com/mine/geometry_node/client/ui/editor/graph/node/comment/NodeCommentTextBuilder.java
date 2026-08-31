package com.mine.geometry_node.client.ui.editor.graph.node.comment;

import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NodeCommentTextBuilder {
    private static final String SECTION_OUTPUTS = "geometry_node.comment.section.outputs";
    private static final String SECTION_INPUTS = "geometry_node.comment.section.inputs";
    private static final String PORT_LINE = "geometry_node.comment.port_line";
    private static final String COMMENT_KEY_MARKER = ".comment.";
    private static final String COMMON_COMMENT_PREFIX = "geometry_node.comment.common.";

    private NodeCommentTextBuilder() {
    }

    public static String build(NodeDef nodeDef) {
        if (nodeDef == null) {
            return "";
        }

        NodeComment nodeComment = nodeDef.nodeComment();
        if (nodeComment != null && !nodeComment.isEmpty()) {
            String structured = buildStructured(nodeDef, nodeComment);
            if (!structured.isBlank()) {
                return structured;
            }
        }

        String legacyComment = nodeDef.comment();
        return legacyComment == null ? "" : legacyComment.trim();
    }

    public static boolean hasComment(NodeDef nodeDef) {
        return !build(nodeDef).isBlank();
    }

    private static String buildStructured(NodeDef nodeDef, NodeComment nodeComment) {
        StringBuilder builder = new StringBuilder();
        appendTextKeys(builder, nodeComment.textKeys());

        Map<String, PortDef> outputPorts = collectPorts(nodeDef.rows(), false);
        appendPortSection(builder, SECTION_OUTPUTS, nodeComment.outputs(), outputPorts);

        Map<String, PortDef> inputPorts = collectPorts(nodeDef.rows(), true);
        appendPortSection(builder, SECTION_INPUTS, nodeComment.inputs(), inputPorts);
        return builder.toString().trim();
    }

    private static void appendTextKeys(StringBuilder builder, List<String> textKeys) {
        for (String textKey : textKeys) {
            String text = translate(textKey).trim();
            if (!text.isBlank()) {
                appendLine(builder, text);
            }
        }
    }

    private static void appendPortSection(
            StringBuilder builder,
            String sectionKey,
            List<NodeComment.PortComment> comments,
            Map<String, PortDef> ports
    ) {
        if (comments.isEmpty()) {
            return;
        }

        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        appendLine(builder, translate(sectionKey));

        for (NodeComment.PortComment comment : comments) {
            String description = translate(comment.textKey()).trim();
            if (description.isBlank()) {
                continue;
            }
            String portName = resolvePortName(comment.portId(), ports);
            appendLine(builder, translate(PORT_LINE, portName, description));
        }
    }

    private static Map<String, PortDef> collectPorts(List<PortRow> rows, boolean inputSide) {
        Map<String, PortDef> ports = new LinkedHashMap<>();
        for (PortRow row : rows) {
            PortDef port = inputSide ? row.leftPort() : row.rightPort();
            if (port != null && !port.id().isBlank()) {
                ports.put(port.id(), port);
            }
        }
        return ports;
    }

    private static String resolvePortName(String portId, Map<String, PortDef> ports) {
        PortDef port = ports.get(portId);
        if (port == null) {
            return portId;
        }
        String displayName = port.displayName().getString().trim();
        return displayName.isBlank() ? portId : displayName;
    }

    private static void appendLine(StringBuilder builder, String text) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(text);
    }

    private static String translate(String key, Object... args) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String text = Component.translatable(key, args).getString();
        if (!text.equals(key)) {
            return text;
        }

        String fallbackKey = commonFallbackKey(key);
        if (fallbackKey.equals(key)) {
            return text;
        }

        String fallbackText = Component.translatable(fallbackKey, args).getString();
        return fallbackText.equals(fallbackKey) ? text : fallbackText;
    }

    private static String commonFallbackKey(String key) {
        int markerIndex = key.indexOf(COMMENT_KEY_MARKER);
        if (markerIndex < 0) {
            return key;
        }
        String suffix = key.substring(markerIndex + COMMENT_KEY_MARKER.length());
        return suffix.isBlank() ? key : COMMON_COMMENT_PREFIX + suffix;
    }
}
