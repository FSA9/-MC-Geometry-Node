package com.mine.geometry_node.core.engine.system.quest.model;

import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.quest.BaseQuestConditionsNode;
import com.mine.geometry_node.core.node.nodes.quest.CreateQuestCondition;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import com.mine.geometry_node.core.node.value.RichTextValue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Static, player-independent condition text projected into the graph properties panel. */
public record QuestConditionOverview(
        List<String> visibility,
        List<String> acceptance,
        List<String> completion
) {
    public static final QuestConditionOverview EMPTY = new QuestConditionOverview(
            List.of(), List.of(), List.of());

    public QuestConditionOverview {
        visibility = immutableText(visibility);
        acceptance = immutableText(acceptance);
        completion = immutableText(completion);
    }

    public List<String> texts(QuestConditionKind kind) {
        return switch (kind) {
            case VISIBILITY -> visibility;
            case ACCEPTANCE -> acceptance;
            case COMPLETION -> completion;
        };
    }

    public static QuestConditionOverview fromGraph(NodeGraph graph) {
        if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) return EMPTY;

        Map<QuestConditionKind, List<String>> texts = new EnumMap<>(QuestConditionKind.class);
        for (QuestConditionKind kind : QuestConditionKind.all()) {
            texts.put(kind, new ArrayList<>());
        }
        collectScope(graph.nodes, texts);
        return new QuestConditionOverview(
                texts.get(QuestConditionKind.VISIBILITY),
                texts.get(QuestConditionKind.ACCEPTANCE),
                texts.get(QuestConditionKind.COMPLETION));
    }

    private static void collectScope(Map<String, NodeData> nodes, Map<QuestConditionKind, List<String>> texts) {
        if (nodes == null || nodes.isEmpty()) return;
        for (Map.Entry<String, NodeData> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            NodeData node = entry.getValue();
            if (node == null) continue;
            QuestConditionKind kind = findKind(node.type);
            if (kind != null) {
                int count = BaseQuestConditionsNode.resolveConditionCount(node);
                for (int i = 1; i <= count; i++) {
                    SourceRef source = findInputSource(
                            nodes,
                            nodeId,
                            BaseQuestConditionsNode.conditionPort(i));
                    NodeData conditionNode = resolveConditionNode(nodes, source, new HashSet<>());
                    Object raw = conditionNode != null && conditionNode.inputs != null
                            ? conditionNode.inputs.get(CreateQuestCondition.DISPLAY_TEXT_PORT)
                            : null;
                    String text = RichTextValue.from(raw).plain().trim();
                    if (!text.isEmpty()) texts.get(kind).add(text);
                }
            }
            if (node.subNodes != null && !node.subNodes.isEmpty()) {
                collectScope(node.subNodes, texts);
            }
        }
    }

    private static NodeData resolveConditionNode(
            Map<String, NodeData> nodes,
            SourceRef source,
            Set<String> visited) {
        if (source == null || !visited.add(source.nodeId())) return null;
        NodeData node = nodes.get(source.nodeId());
        if (node == null) return null;
        if (NodeDef.canonicalTypeId(CreateQuestCondition.TYPE_ID).equals(
                NodeDef.canonicalTypeId(node.type))
                && CreateQuestCondition.OUTPUT_PORT.equals(source.portName())) {
            return node;
        }
        if (!RerouteNodeSupport.isReroute(node)
                || !RerouteNodeSupport.OUTPUT_PORT.equals(source.portName())) {
            return null;
        }
        return resolveConditionNode(
                nodes,
                findInputSource(nodes, source.nodeId(), RerouteNodeSupport.INPUT_PORT),
                visited);
    }

    private static SourceRef findInputSource(
            Map<String, NodeData> nodes,
            String targetNodeId,
            String targetPortName) {
        if (targetNodeId == null || targetPortName == null) return null;
        for (Map.Entry<String, NodeData> entry : nodes.entrySet()) {
            NodeData source = entry.getValue();
            if (source == null || source.outputs == null) continue;
            for (Map.Entry<String, List<Connection>> output : source.outputs.entrySet()) {
                if (output.getValue() == null) continue;
                for (Connection connection : output.getValue()) {
                    if (connection != null
                            && targetNodeId.equals(connection.targetNodeId())
                            && targetPortName.equals(connection.targetPortName())) {
                        return new SourceRef(entry.getKey(), output.getKey());
                    }
                }
            }
        }
        return null;
    }

    private record SourceRef(String nodeId, String portName) {
    }

    private static QuestConditionKind findKind(String nodeTypeId) {
        if (nodeTypeId == null) return null;
        for (QuestConditionKind kind : QuestConditionKind.all()) {
            if (NodeDef.canonicalTypeId(kind.nodeTypeId()).equals(
                    NodeDef.canonicalTypeId(nodeTypeId))) return kind;
        }
        return null;
    }

    private static List<String> immutableText(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
