package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorNodeState;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorInstanceState;
import com.mine.geometry_node.core.engine.behavior.debug.BehaviorTreeDebugSnapshot;
import com.mine.geometry_node.core.node.port.PortType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded client-facing behavior debug snapshot or subscription status. */
public record PacketBehaviorDebugSnapshot(UUID instanceId, Status status, String detail,
                                          @Nullable Snapshot snapshot)
        implements CustomPacketPayload {
    public static final int MAX_NODES = 256;
    public static final int MAX_ACTIVE_PATH = 64;
    public static final int MAX_BLACKBOARD = 128;
    public static final int MAX_HISTORY = 64;
    public static final int MAX_ID_LENGTH = 512;
    public static final int MAX_NAME_LENGTH = 256;
    public static final int MAX_VALUE_LENGTH = 2_048;
    public static final int MAX_DETAIL_LENGTH = 4_096;
    public static final int MAX_CONTENT_BYTES = 256 * 1_024;
    private static final int MAX_NODE_CONTENT_BYTES = 128 * 1_024;
    private static final int MAX_BLACKBOARD_CONTENT_BYTES = 64 * 1_024;
    private static final int MAX_HISTORY_CONTENT_BYTES = 48 * 1_024;

    public static final Type<PacketBehaviorDebugSnapshot> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "behavior_debug_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketBehaviorDebugSnapshot> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> packet.write(buffer), PacketBehaviorDebugSnapshot::read);

    public PacketBehaviorDebugSnapshot {
        detail = bounded(detail, MAX_DETAIL_LENGTH);
        if ((status == Status.SNAPSHOT) != (snapshot != null)) {
            throw new IllegalArgumentException("Only snapshot status may carry a payload");
        }
    }

    public static PacketBehaviorDebugSnapshot status(UUID instanceId, Status status, String detail) {
        if (status == Status.SNAPSHOT) throw new IllegalArgumentException("Use snapshot() for snapshot status");
        return new PacketBehaviorDebugSnapshot(instanceId, status, detail, null);
    }

    public static PacketBehaviorDebugSnapshot snapshot(BehaviorTreeDebugSnapshot source) {
        List<Integer> activePath = source.activePath().stream().limit(MAX_ACTIVE_PATH).toList();
        ContentBudget totalBudget = new ContentBudget(MAX_CONTENT_BYTES);
        totalBudget.consume(512 + stringBytes(bounded(source.assetId(), MAX_ID_LENGTH))
                + stringBytes(bounded(source.ownerIdentity(), MAX_ID_LENGTH))
                + stringBytes(bounded(source.ownerName(), MAX_NAME_LENGTH))
                + stringBytes(bounded(source.dimension(), MAX_ID_LENGTH))
                + activePath.size() * 5);
        ContentBudget nodeBudget = new ContentBudget(MAX_NODE_CONTENT_BYTES);
        ContentBudget blackboardBudget = new ContentBudget(MAX_BLACKBOARD_CONTENT_BYTES);
        ContentBudget historyBudget = new ContentBudget(MAX_HISTORY_CONTENT_BYTES);

        Set<Integer> activeIndexes = new HashSet<>(activePath);
        Set<Integer> consideredIndexes = new HashSet<>();
        List<Node> nodes = new ArrayList<>(Math.min(source.nodes().size(), MAX_NODES));
        for (BehaviorTreeDebugSnapshot.NodeSnapshot node : source.nodes()) {
            if (!activeIndexes.contains(node.index())) continue;
            consideredIndexes.add(node.index());
            Node candidate = node(node);
            if (fits(totalBudget, nodeBudget, nodeBytes(candidate))) nodes.add(candidate);
        }
        for (BehaviorTreeDebugSnapshot.NodeSnapshot node : source.nodes()) {
            if (nodes.size() >= MAX_NODES) break;
            if (!consideredIndexes.add(node.index())) continue;
            Node candidate = node(node);
            if (fits(totalBudget, nodeBudget, nodeBytes(candidate))) nodes.add(candidate);
        }

        List<BlackboardEntry> blackboard = new ArrayList<>();
        for (BehaviorTreeDebugSnapshot.BlackboardSnapshot entry : source.blackboard()) {
            if (blackboard.size() >= MAX_BLACKBOARD) break;
            BlackboardEntry candidate = blackboard(entry);
            if (fits(totalBudget, blackboardBudget, blackboardBytes(candidate))) {
                blackboard.add(candidate);
            }
        }
        int historyOffset = Math.max(0, source.history().size() - MAX_HISTORY);
        List<Trace> history = new ArrayList<>();
        for (int index = source.history().size() - 1; index >= historyOffset; index--) {
            Trace candidate = trace(source.history().get(index));
            if (fits(totalBudget, historyBudget, traceBytes(candidate))) {
                history.add(0, candidate);
            }
        }
        Snapshot snapshot = new Snapshot(bounded(source.assetId(), MAX_ID_LENGTH),
                bounded(source.ownerIdentity(), MAX_ID_LENGTH), source.ownerId(),
                bounded(source.ownerName(), MAX_NAME_LENGTH), bounded(source.dimension(), MAX_ID_LENGTH),
                source.capturedGameTick(), source.state(), source.lastTreeResult(), source.stopReason(),
                source.lastEvaluationTick(), source.nextWakeTick(), activePath,
                source.activePath().size() > activePath.size(), nodes,
                source.nodesTruncated() || source.nodes().size() > nodes.size(),
                evaluation(source.evaluation()),
                budget(source.budget()), source.blackboardRevision(), blackboard,
                source.blackboard().size() > blackboard.size(), history,
                source.history().size() > history.size());
        return new PacketBehaviorDebugSnapshot(source.instanceId(), Status.SNAPSHOT, "", snapshot);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(instanceId);
        buffer.writeEnum(status);
        buffer.writeUtf(detail, MAX_DETAIL_LENGTH);
        buffer.writeBoolean(snapshot != null);
        if (snapshot != null) snapshot.write(buffer);
    }

    private static PacketBehaviorDebugSnapshot read(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException("Behavior debug packet exceeds byte budget");
        }
        int startIndex = buffer.readerIndex();
        UUID instanceId = buffer.readUUID();
        Status status = buffer.readEnum(Status.class);
        String detail = buffer.readUtf(MAX_DETAIL_LENGTH);
        Snapshot snapshot = buffer.readBoolean() ? Snapshot.read(buffer) : null;
        if (buffer.readerIndex() - startIndex > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException("Behavior debug packet exceeds byte budget");
        }
        return new PacketBehaviorDebugSnapshot(instanceId, status, detail, snapshot);
    }

    private static Node node(BehaviorTreeDebugSnapshot.NodeSnapshot source) {
        return new Node(source.index(), bounded(source.nodeId(), MAX_ID_LENGTH),
                bounded(source.nodeType(), MAX_ID_LENGTH), source.parentIndex(), source.state(),
                source.visits(), source.totalNanos(), source.lastNanos(), source.lastResult(),
                source.lastReason());
    }

    private static BlackboardEntry blackboard(BehaviorTreeDebugSnapshot.BlackboardSnapshot source) {
        return new BlackboardEntry(bounded(source.name(), MAX_NAME_LENGTH),
                bounded(source.scope(), MAX_NAME_LENGTH),
                bounded(source.providerIdentity(), MAX_ID_LENGTH), source.type(), source.present(),
                bounded(source.valueKind(), MAX_NAME_LENGTH),
                bounded(source.displayValue(), MAX_VALUE_LENGTH), source.scopeAvailable());
    }

    private static Trace trace(BehaviorTreeDebugSnapshot.TraceSnapshot source) {
        return new Trace(source.sequence(), source.gameTick(), source.nodeIndex(),
                bounded(source.nodeId(), MAX_ID_LENGTH), bounded(source.nodeType(), MAX_ID_LENGTH),
                source.reason(), source.result(), source.elapsedNanos(),
                bounded(source.failureCode(), MAX_NAME_LENGTH),
                bounded(source.detail(), MAX_DETAIL_LENGTH));
    }

    private static Evaluation evaluation(BehaviorTreeDebugSnapshot.EvaluationSnapshot source) {
        return new Evaluation(source.evaluations(), source.totalNanos(), source.lastNanos(),
                source.softTimeBudgetOverruns(), source.lastNodeVisits(), source.peakNodeVisits(),
                source.lastImmediateTransitions(), source.peakImmediateTransitions());
    }

    private static Budget budget(BehaviorTreeDebugSnapshot.BudgetSnapshot source) {
        return new Budget(source.instanceNanosPerEvaluation(), source.maxNodeVisitsPerEvaluation(),
                source.maxTreeDepth(), source.maxImmediateTransitions(), source.maxBlackboardEntries(),
                source.maxHistoryEntries());
    }

    private static String bounded(@Nullable String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static boolean fits(ContentBudget total, ContentBudget section, int bytes) {
        if (!total.canConsume(bytes) || !section.canConsume(bytes)) return false;
        total.consume(bytes);
        section.consume(bytes);
        return true;
    }

    private static int nodeBytes(Node node) {
        return 64 + stringBytes(node.nodeId()) + stringBytes(node.nodeType());
    }

    private static int blackboardBytes(BlackboardEntry entry) {
        return 64 + stringBytes(entry.name()) + stringBytes(entry.scope())
                + stringBytes(entry.providerIdentity())
                + stringBytes(entry.valueKind()) + stringBytes(entry.displayValue());
    }

    private static int traceBytes(Trace trace) {
        return 64 + stringBytes(trace.nodeId()) + stringBytes(trace.nodeType())
                + stringBytes(trace.failureCode()) + stringBytes(trace.detail());
    }

    private static int stringBytes(@Nullable String value) {
        return 5 + (value != null ? value.getBytes(StandardCharsets.UTF_8).length : 0);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, int max, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("Invalid behavior debug " + label + " count: " + count);
        }
        return count;
    }

    private static void writeResult(RegistryFriendlyByteBuf buffer, @Nullable BehaviorResult value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeEnum(value);
    }

    @Nullable
    private static BehaviorResult readResult(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readEnum(BehaviorResult.class) : null;
    }

    private static void writeReason(RegistryFriendlyByteBuf buffer,
                                    @Nullable BehaviorTerminationReason value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeEnum(value);
    }

    @Nullable
    private static BehaviorTerminationReason readReason(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readEnum(BehaviorTerminationReason.class) : null;
    }

    private static final class ContentBudget {
        private final int limit;
        private int used;

        private ContentBudget(int limit) {
            this.limit = limit;
        }

        private boolean canConsume(int bytes) {
            return bytes >= 0 && used <= limit - bytes;
        }

        private void consume(int bytes) {
            if (!canConsume(bytes)) throw new IllegalArgumentException("Behavior debug content budget exceeded");
            used += bytes;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Status {
        SNAPSHOT,
        CANCELLED,
        NOT_FOUND,
        PERMISSION_DENIED,
        OUT_OF_RANGE,
        LIMIT_REACHED
    }

    public record Snapshot(String assetId, String ownerIdentity, @Nullable UUID ownerId,
                           String ownerName, String dimension, long capturedGameTick,
                           BehaviorInstanceState state, @Nullable BehaviorResult lastTreeResult,
                           @Nullable BehaviorTerminationReason stopReason, long lastEvaluationTick,
                           long nextWakeTick, List<Integer> activePath, boolean activePathTruncated,
                           List<Node> nodes, boolean nodesTruncated, Evaluation evaluation,
                           Budget budget, long blackboardRevision,
                           List<BlackboardEntry> blackboard, boolean blackboardTruncated,
                           List<Trace> history, boolean historyTruncated) {
        public Snapshot {
            activePath = List.copyOf(activePath);
            nodes = List.copyOf(nodes);
            blackboard = List.copyOf(blackboard);
            history = List.copyOf(history);
            if (activePath.size() > MAX_ACTIVE_PATH || nodes.size() > MAX_NODES
                    || blackboard.size() > MAX_BLACKBOARD || history.size() > MAX_HISTORY) {
                throw new IllegalArgumentException("Behavior debug snapshot exceeds packet limits");
            }
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(assetId, MAX_ID_LENGTH);
            buffer.writeUtf(ownerIdentity, MAX_ID_LENGTH);
            buffer.writeBoolean(ownerId != null);
            if (ownerId != null) buffer.writeUUID(ownerId);
            buffer.writeUtf(ownerName, MAX_NAME_LENGTH);
            buffer.writeUtf(dimension, MAX_ID_LENGTH);
            buffer.writeLong(capturedGameTick);
            buffer.writeEnum(state);
            writeResult(buffer, lastTreeResult);
            writeReason(buffer, stopReason);
            buffer.writeLong(lastEvaluationTick);
            buffer.writeLong(nextWakeTick);
            buffer.writeVarInt(activePath.size());
            for (int nodeIndex : activePath) buffer.writeVarInt(nodeIndex);
            buffer.writeBoolean(activePathTruncated);
            buffer.writeVarInt(nodes.size());
            for (Node node : nodes) node.write(buffer);
            buffer.writeBoolean(nodesTruncated);
            evaluation.write(buffer);
            budget.write(buffer);
            buffer.writeLong(blackboardRevision);
            buffer.writeVarInt(blackboard.size());
            for (BlackboardEntry entry : blackboard) entry.write(buffer);
            buffer.writeBoolean(blackboardTruncated);
            buffer.writeVarInt(history.size());
            for (Trace trace : history) trace.write(buffer);
            buffer.writeBoolean(historyTruncated);
        }

        private static Snapshot read(RegistryFriendlyByteBuf buffer) {
            String assetId = buffer.readUtf(MAX_ID_LENGTH);
            String ownerIdentity = buffer.readUtf(MAX_ID_LENGTH);
            UUID ownerId = buffer.readBoolean() ? buffer.readUUID() : null;
            String ownerName = buffer.readUtf(MAX_NAME_LENGTH);
            String dimension = buffer.readUtf(MAX_ID_LENGTH);
            long capturedGameTick = buffer.readLong();
            BehaviorInstanceState state = buffer.readEnum(BehaviorInstanceState.class);
            BehaviorResult lastTreeResult = readResult(buffer);
            BehaviorTerminationReason stopReason = readReason(buffer);
            long lastEvaluationTick = buffer.readLong();
            long nextWakeTick = buffer.readLong();
            int pathCount = readCount(buffer, MAX_ACTIVE_PATH, "active path");
            List<Integer> activePath = new ArrayList<>(pathCount);
            for (int index = 0; index < pathCount; index++) activePath.add(buffer.readVarInt());
            boolean activePathTruncated = buffer.readBoolean();
            int nodeCount = readCount(buffer, MAX_NODES, "node");
            List<Node> nodes = new ArrayList<>(nodeCount);
            for (int index = 0; index < nodeCount; index++) nodes.add(Node.read(buffer));
            boolean nodesTruncated = buffer.readBoolean();
            Evaluation evaluation = Evaluation.read(buffer);
            Budget budget = Budget.read(buffer);
            long blackboardRevision = buffer.readLong();
            int blackboardCount = readCount(buffer, MAX_BLACKBOARD, "blackboard");
            List<BlackboardEntry> blackboard = new ArrayList<>(blackboardCount);
            for (int index = 0; index < blackboardCount; index++) {
                blackboard.add(BlackboardEntry.read(buffer));
            }
            boolean blackboardTruncated = buffer.readBoolean();
            int historyCount = readCount(buffer, MAX_HISTORY, "history");
            List<Trace> history = new ArrayList<>(historyCount);
            for (int index = 0; index < historyCount; index++) history.add(Trace.read(buffer));
            boolean historyTruncated = buffer.readBoolean();
            return new Snapshot(assetId, ownerIdentity, ownerId, ownerName, dimension,
                    capturedGameTick, state, lastTreeResult, stopReason, lastEvaluationTick,
                    nextWakeTick, activePath, activePathTruncated, nodes, nodesTruncated,
                    evaluation, budget, blackboardRevision, blackboard, blackboardTruncated,
                    history, historyTruncated);
        }
    }

    public record Node(int index, String nodeId, String nodeType, int parentIndex,
                       BehaviorNodeState state, long visits, long totalNanos, long lastNanos,
                       @Nullable BehaviorResult lastResult,
                       @Nullable BehaviorTerminationReason lastReason) {
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(index);
            buffer.writeUtf(nodeId, MAX_ID_LENGTH);
            buffer.writeUtf(nodeType, MAX_ID_LENGTH);
            buffer.writeVarInt(parentIndex);
            buffer.writeEnum(state);
            buffer.writeLong(visits);
            buffer.writeLong(totalNanos);
            buffer.writeLong(lastNanos);
            writeResult(buffer, lastResult);
            writeReason(buffer, lastReason);
        }

        private static Node read(RegistryFriendlyByteBuf buffer) {
            return new Node(buffer.readVarInt(), buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readUtf(MAX_ID_LENGTH), buffer.readVarInt(),
                    buffer.readEnum(BehaviorNodeState.class), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), readResult(buffer), readReason(buffer));
        }
    }

    public record Evaluation(long evaluations, long totalNanos, long lastNanos,
                             long softTimeBudgetOverruns, int lastNodeVisits,
                             int peakNodeVisits, int lastImmediateTransitions,
                             int peakImmediateTransitions) {
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeLong(evaluations);
            buffer.writeLong(totalNanos);
            buffer.writeLong(lastNanos);
            buffer.writeLong(softTimeBudgetOverruns);
            buffer.writeVarInt(lastNodeVisits);
            buffer.writeVarInt(peakNodeVisits);
            buffer.writeVarInt(lastImmediateTransitions);
            buffer.writeVarInt(peakImmediateTransitions);
        }

        private static Evaluation read(RegistryFriendlyByteBuf buffer) {
            return new Evaluation(buffer.readLong(), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record Budget(long instanceNanosPerEvaluation, int maxNodeVisitsPerEvaluation,
                         int maxTreeDepth, int maxImmediateTransitions,
                         int maxBlackboardEntries, int maxHistoryEntries) {
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeLong(instanceNanosPerEvaluation);
            buffer.writeVarInt(maxNodeVisitsPerEvaluation);
            buffer.writeVarInt(maxTreeDepth);
            buffer.writeVarInt(maxImmediateTransitions);
            buffer.writeVarInt(maxBlackboardEntries);
            buffer.writeVarInt(maxHistoryEntries);
        }

        private static Budget read(RegistryFriendlyByteBuf buffer) {
            return new Budget(buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record BlackboardEntry(String name, String scope, String providerIdentity, PortType type,
                                  boolean present, String valueKind, String displayValue,
                                  boolean scopeAvailable) {
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(name, MAX_NAME_LENGTH);
            buffer.writeUtf(scope, MAX_NAME_LENGTH);
            buffer.writeUtf(providerIdentity, MAX_ID_LENGTH);
            buffer.writeEnum(type);
            buffer.writeBoolean(present);
            buffer.writeUtf(valueKind, MAX_NAME_LENGTH);
            buffer.writeUtf(displayValue, MAX_VALUE_LENGTH);
            buffer.writeBoolean(scopeAvailable);
        }

        private static BlackboardEntry read(RegistryFriendlyByteBuf buffer) {
            return new BlackboardEntry(buffer.readUtf(MAX_NAME_LENGTH), buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readEnum(PortType.class), buffer.readBoolean(), buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_VALUE_LENGTH), buffer.readBoolean());
        }
    }

    public record Trace(long sequence, long gameTick, int nodeIndex, String nodeId,
                        String nodeType, BehaviorTerminationReason reason,
                        @Nullable BehaviorResult result, long elapsedNanos,
                        String failureCode, String detail) {
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeLong(sequence);
            buffer.writeLong(gameTick);
            buffer.writeVarInt(nodeIndex);
            buffer.writeUtf(nodeId, MAX_ID_LENGTH);
            buffer.writeUtf(nodeType, MAX_ID_LENGTH);
            buffer.writeEnum(reason);
            writeResult(buffer, result);
            buffer.writeLong(elapsedNanos);
            buffer.writeUtf(failureCode, MAX_NAME_LENGTH);
            buffer.writeUtf(detail, MAX_DETAIL_LENGTH);
        }

        private static Trace read(RegistryFriendlyByteBuf buffer) {
            return new Trace(buffer.readLong(), buffer.readLong(), buffer.readVarInt(),
                    buffer.readUtf(MAX_ID_LENGTH), buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readEnum(BehaviorTerminationReason.class), readResult(buffer),
                    buffer.readLong(), buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_DETAIL_LENGTH));
        }
    }
}
