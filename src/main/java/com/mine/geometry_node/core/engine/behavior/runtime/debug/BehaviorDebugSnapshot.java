package com.mine.geometry_node.core.engine.behavior.runtime.debug;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboard;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorNodeState;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorInstanceState;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorTreeInstance;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.node.port.PortType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, transport-safe observation of one server-authoritative behavior instance.
 * It deliberately contains no runtime object references and cannot mutate the instance.
 */
public record BehaviorDebugSnapshot(
        UUID instanceId,
        String assetId,
        String ownerIdentity,
        @Nullable UUID ownerId,
        String ownerName,
        String dimension,
        long capturedGameTick,
        BehaviorInstanceState state,
        @Nullable BehaviorResult lastTreeResult,
        @Nullable BehaviorTerminationReason stopReason,
        long lastEvaluationTick,
        long nextWakeTick,
        List<Integer> activePath,
        List<NodeSnapshot> nodes,
        boolean nodesTruncated,
        EvaluationSnapshot evaluation,
        BudgetSnapshot budget,
        long blackboardRevision,
        List<BlackboardSnapshot> blackboard,
        List<TraceSnapshot> history
) {
    private static final int MAX_VALUE_TEXT = 2_048;
    private static final int MAX_DETAIL_TEXT = 4_096;
    private static final int MAX_VALUE_DEPTH = 4;
    private static final int MAX_COLLECTION_ENTRIES = 32;
    private static final int MAX_SNAPSHOT_NODES = 256;

    public BehaviorDebugSnapshot {
        activePath = List.copyOf(activePath);
        nodes = List.copyOf(nodes);
        blackboard = List.copyOf(blackboard);
        history = List.copyOf(history);
    }

    /** Must be called on the owning server thread so the copied view has one snapshot boundary. */
    public static BehaviorDebugSnapshot capture(BehaviorTreeInstance instance) {
        BehaviorTreePlan plan = instance.plan();
        Entity owner = instance.host().owner();
        ServerLevel level = instance.host().level();

        List<Integer> activePath = instance.activePath();
        LinkedHashSet<Integer> selectedNodes = new LinkedHashSet<>(activePath);
        for (int index = 0; index < plan.getNodeCount()
                && selectedNodes.size() < MAX_SNAPSHOT_NODES; index++) {
            selectedNodes.add(index);
        }
        List<NodeSnapshot> nodes = new ArrayList<>(selectedNodes.size());
        for (int index : selectedNodes) {
            if (index < 0 || index >= plan.getNodeCount()) continue;
            BehaviorTreeInstance.NodeMetrics metrics = instance.nodeMetrics(index);
            nodes.add(new NodeSnapshot(index, plan.getNodeId(index), plan.getNodeAssetId(index),
                    plan.getNodeType(index), plan.getParent(index), instance.nodeState(index), metrics.visits(),
                    metrics.totalNanos(), metrics.lastNanos(), metrics.lastResult(),
                    metrics.lastReason()));
        }

        List<BehaviorTreeInstance.BlackboardFrameSnapshot> frames = instance.blackboardFrameSnapshots();
        List<BlackboardSnapshot> blackboard = new ArrayList<>();
        long blackboardRevision = 1L;
        for (BehaviorTreeInstance.BlackboardFrameSnapshot frame : frames) {
            blackboardRevision = 31L * blackboardRevision + frame.frameId();
            blackboardRevision = 31L * blackboardRevision + frame.revision();
            for (BehaviorBlackboard.EntrySnapshot entry : frame.entries()) {
                blackboard.add(blackboardSnapshot(frame, entry));
            }
        }
        List<TraceSnapshot> history = instance.history().stream()
                .map(event -> new TraceSnapshot(event.sequence(), event.gameTick(), event.nodeIndex(),
                        event.nodeId(), event.nodeType(), event.reason(), event.result(),
                        event.elapsedNanos(), bounded(event.failureCode(), 256),
                        bounded(event.detail(), MAX_DETAIL_TEXT)))
                .toList();

        BehaviorTreeInstance.EvaluationMetrics metrics = instance.evaluationMetrics();
        BehaviorRuntimeBudget configuredBudget = instance.budget();
        long hostTick = instance.host().gameTick();
        long captureTick = instance.lastEvaluationTick() != Long.MIN_VALUE
                ? Math.max(hostTick, instance.lastEvaluationTick()) : hostTick;
        return new BehaviorDebugSnapshot(instance.instanceId(), instance.graphId(),
                instance.host().identity(), owner != null ? owner.getUUID() : null,
                owner != null ? bounded(owner.getName().getString(), 256) : "",
                level != null ? level.dimension().identifier().toString() : "",
                captureTick, instance.state(), instance.lastTreeResult(),
                instance.stopReason(), instance.lastEvaluationTick(), instance.nextWakeTick(),
                activePath, nodes, plan.getNodeCount() > nodes.size(),
                new EvaluationSnapshot(metrics.evaluations(), metrics.totalNanos(), metrics.lastNanos(),
                        metrics.softTimeBudgetOverruns(), metrics.lastNodeVisits(),
                        metrics.peakNodeVisits(), metrics.lastImmediateTransitions(),
                        metrics.peakImmediateTransitions()),
                new BudgetSnapshot(configuredBudget.instanceNanosPerEvaluation(),
                        configuredBudget.maxNodeVisitsPerEvaluation(), configuredBudget.maxTreeDepth(),
                        configuredBudget.maxImmediateTransitions(),
                        configuredBudget.maxBlackboardEntriesPerInstance(),
                        configuredBudget.maxHistoryEntriesPerInstance()),
                blackboardRevision, blackboard, history);
    }

    private static BlackboardSnapshot blackboardSnapshot(
            BehaviorTreeInstance.BlackboardFrameSnapshot frame,
            BehaviorBlackboard.EntrySnapshot entry) {
        Object value = entry.value();
        return new BlackboardSnapshot(frame.frameId(), frame.assetId(), frame.callNodePath(), frame.revision(),
                entry.name(), entry.scope().name(), entry.providerIdentity(), entry.type(),
                value != null, valueKind(value), observedText(value),
                entry.revision(), entry.sourceNodeId(), entry.gameTick(), entry.scopeAvailable());
    }

    private static String valueKind(@Nullable Object value) {
        if (value == null) return "null";
        if (value instanceof Entity) return "entity";
        if (value instanceof Map<?, ?>) return "map";
        if (value instanceof List<?>) return "list";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof String) return "string";
        return value.getClass().getSimpleName();
    }

    private static String observedText(@Nullable Object value) {
        StringBuilder output = new StringBuilder();
        appendValue(output, value, 0);
        return output.toString();
    }

    private static void appendValue(StringBuilder output, @Nullable Object value, int depth) {
        if (output.length() >= MAX_VALUE_TEXT) return;
        if (value == null) {
            appendBounded(output, "null");
        } else if (value instanceof Entity entity) {
            appendBounded(output, entity.getUUID().toString());
        } else if (depth >= MAX_VALUE_DEPTH) {
            appendBounded(output, "...");
        } else if (value instanceof Map<?, ?> map) {
            appendBounded(output, "{");
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count > 0) appendBounded(output, ", ");
                if (count++ >= MAX_COLLECTION_ENTRIES) {
                    appendBounded(output, "...");
                    break;
                }
                appendValue(output, entry.getKey(), depth + 1);
                appendBounded(output, "=");
                appendValue(output, entry.getValue(), depth + 1);
            }
            appendBounded(output, "}");
        } else if (value instanceof List<?> list) {
            appendBounded(output, "[");
            for (int index = 0; index < Math.min(list.size(), MAX_COLLECTION_ENTRIES); index++) {
                if (index > 0) appendBounded(output, ", ");
                appendValue(output, list.get(index), depth + 1);
            }
            if (list.size() > MAX_COLLECTION_ENTRIES) appendBounded(output, ", ...");
            appendBounded(output, "]");
        } else {
            if (value instanceof String text) {
                appendBounded(output, text);
            } else {
                appendBounded(output, String.valueOf(value));
            }
        }
    }

    private static void appendBounded(StringBuilder output, CharSequence value) {
        int remaining = MAX_VALUE_TEXT - output.length();
        if (remaining <= 0 || value == null) return;
        if (value.length() <= remaining) {
            output.append(value);
            return;
        }
        if (remaining <= 3) {
            output.append(value, 0, remaining);
            return;
        }
        output.append(value, 0, remaining - 3).append("...");
    }

    private static String bounded(@Nullable String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    public record NodeSnapshot(int index, String nodeId, String nodeAssetId, String nodeType, int parentIndex,
                               BehaviorNodeState state, long visits, long totalNanos,
                               long lastNanos, @Nullable BehaviorResult lastResult,
                               @Nullable BehaviorTerminationReason lastReason) {
    }

    public record EvaluationSnapshot(long evaluations, long totalNanos, long lastNanos,
                                     long softTimeBudgetOverruns, int lastNodeVisits,
                                     int peakNodeVisits, int lastImmediateTransitions,
                                     int peakImmediateTransitions) {
    }

    public record BudgetSnapshot(long instanceNanosPerEvaluation, int maxNodeVisitsPerEvaluation,
                                 int maxTreeDepth, int maxImmediateTransitions,
                                 int maxBlackboardEntries, int maxHistoryEntries) {
    }

    public record BlackboardSnapshot(int frameId, String frameAssetId, String callNodePath,
                                     long frameRevision,
                                     String name, String scope,
                                     String providerIdentity, PortType type,
                                     boolean present, String valueKind, String displayValue,
                                     long revision, String sourceNodeId, long gameTick,
                                     boolean scopeAvailable) {
    }

    public record TraceSnapshot(long sequence, long gameTick, int nodeIndex, String nodeId,
                                String nodeType, BehaviorTerminationReason reason,
                                @Nullable BehaviorResult result, long elapsedNanos,
                                String failureCode, String detail) {
    }
}
