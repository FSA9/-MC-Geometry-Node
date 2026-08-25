package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.graph.GraphPatch;
import com.mine.geometry_node.client.ai.graph.GraphPatchCodec;
import com.mine.geometry_node.client.ai.graph.GraphPatchContractValidator;
import com.mine.geometry_node.client.ai.graph.InputValueCodec;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdConnect;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdReplaceGraphState;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.engine.blueprint.compile.BlueprintCompiler;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortOptionContext;
import com.mine.geometry_node.core.node.port.PortOptionResolver;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import icyllis.modernui.core.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** P5 dry-run, approval, revision revalidation and one-command commit pipeline. */
public final class GraphPatchTransactionService {
    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_IDEMPOTENCY_KEYS = 1_024;
    private static final Gson GSON = new Gson();

    private final GraphSession session;
    private final BoundGraphScope scope;
    private final GraphPatchApprovalPresenter approvalPresenter;
    private final Map<String, CompletedPatch> completed = new LinkedHashMap<>();

    public GraphPatchTransactionService(GraphSession session, BoundGraphScope scope,
                                        GraphPatchApprovalPresenter approvalPresenter) {
        this.session = Objects.requireNonNull(session, "session");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.approvalPresenter = Objects.requireNonNull(approvalPresenter, "approvalPresenter");
    }

    public synchronized CommandResult apply(GraphPatch patch, CommandInvocationContext.CancellationToken cancellation) {
        if (Minecraft.getInstance().isSameThread() || Core.isOnUiThread()) {
            return CommandResult.failure("THREAD_VIOLATION", "GraphPatch 审批不能阻塞客户端或 UI 线程");
        }
        cancellation = cancellation == null ? CommandInvocationContext.CancellationToken.NONE : cancellation;
        try {
            String requestedHash = sha256(GraphPatchCodec.toJson(patch));
            synchronized (completed) {
                CompletedPatch previous = completed.get(patch.idempotencyKey());
                if (previous != null) {
                    return previous.patchHash.equals(requestedHash)
                            ? previous.result
                            : CommandResult.failure("IDEMPOTENCY_CONFLICT", "idempotency_key 已用于不同 GraphPatch");
                }
                if (completed.size() >= MAX_IDEMPOTENCY_KEYS) {
                    return CommandResult.failure("IDEMPOTENCY_LIMIT_REACHED",
                            "当前 PowerShell Run 的 GraphPatch 幂等键已达到上限，请重启 Run");
                }
            }
            PlanSnapshot snapshot = onUi(() -> capturePlanSnapshot(patch)).get();
            PlannedPatch plan = plan(patch, snapshot);
            if (cancellation.isCancelled()) return CommandResult.failure("CANCELLED", "GraphPatch 在审批前已取消");
            CompletionStage<GraphPatchApprovalPresenter.ApprovalOutcome> decision =
                    onUi(() -> approvalPresenter.requestApproval(plan.summary)).get();
            ApprovalDecision approved = awaitDecision(decision.toCompletableFuture(), cancellation);
            if (approved == ApprovalDecision.CANCELLED) return CommandResult.failure("CANCELLED", "GraphPatch 在审批期间被取消");
            if (approved == ApprovalDecision.TIMED_OUT) return CommandResult.failure("APPROVAL_TIMEOUT", "GraphPatch 原生审批已超时");
            if (approved == ApprovalDecision.REJECTED) return CommandResult.failure("APPROVAL_REJECTED", "用户拒绝了 GraphPatch");
            if (approved == ApprovalDecision.DISMISSED) return CommandResult.failure("APPROVAL_DISMISSED", "GraphPatch 审批窗口已关闭");
            if (approved == ApprovalDecision.UI_UNAVAILABLE) return CommandResult.failure("APPROVAL_UI_UNAVAILABLE", "无法显示 GraphPatch 原生审批窗口");
            if (approved == ApprovalDecision.ALREADY_PENDING) return CommandResult.failure("APPROVAL_ALREADY_PENDING", "当前 Terminal Run 已有 GraphPatch 等待审批");
            CommandInvocationContext.CancellationToken commitCancellation = cancellation;
            CommandResult result = onUi(() -> commit(plan, commitCancellation)).get();
            if (result.ok()) {
                synchronized (completed) {
                    completed.put(patch.idempotencyKey(), new CompletedPatch(plan.patchHash, result));
                }
            }
            return result;
        } catch (PatchFailure failure) {
            return CommandResult.failure(failure.code, failure.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return CommandResult.failure("CANCELLED", "GraphPatch 调用被中断");
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof PatchFailure patchFailure) {
                return CommandResult.failure(patchFailure.code, patchFailure.getMessage());
            }
            GeometryNode.LOGGER.error("GraphPatch execution failed", cause == null ? failure : cause);
            return CommandResult.failure("GRAPH_PATCH_INTERNAL_ERROR", "GraphPatch 执行失败");
        }
    }

    private PlanSnapshot capturePlanSnapshot(GraphPatch patch) {
        Core.checkUiThread();
        requireOpenAndBound(patch);
        return new PlanSnapshot(canonicalGraphJson(session.editorContext.getGraph()), registryAccess());
    }

    private PlannedPatch plan(GraphPatch patch, PlanSnapshot snapshot) {
        if (patch.approvalId() != null) throw fail("APPROVAL_ID_FORBIDDEN", "approval_id 由 GeometryNode 生成，调用方不得提供");
        List<GraphPatchContractValidator.Diagnostic> contract = GraphPatchContractValidator.validate(patch);
        if (!contract.isEmpty()) throw fail(contract.getFirst().code(), contract.getFirst().message());

        String beforeJson = snapshot.beforeJson;
        NodeGraph trialRoot = GraphJsonIO.fromJson(beforeJson);
        EditorContext trialContext = new EditorContext(trialRoot);
        enterScope(trialContext);
        NodeGraph trialScope = scope.resolve(trialRoot);
        if (trialScope == null) throw fail("GRAPH_SCOPE_CLOSED", "PowerShell 启动时绑定的 Group Scope 已不存在");

        Map<String, String> aliases = new LinkedHashMap<>();
        for (int index = 0; index < patch.operations().size(); index++) {
            try {
                applyOperation(patch, index, patch.operations().get(index), trialContext, trialScope,
                        aliases, snapshot.registryAccess);
            } catch (PatchFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw fail("PATCH_OPERATION_INVALID", "operation[" + index + "] 无效: " + safeMessage(failure));
            }
        }
        String afterJson = canonicalGraphJson(trialRoot);
        if (beforeJson.equals(afterJson)) throw fail("PATCH_NO_CHANGES", "GraphPatch 没有产生任何变化");
        try {
            BlueprintCompiler.compile(new StringReader(afterJson));
        } catch (RuntimeException failure) {
            throw fail("GRAPH_COMPILE_FAILED", "GraphPatch dry-run 编译失败: " + safeMessage(failure));
        }
        String patchHash = sha256(GraphPatchCodec.toJson(patch));
        String approvalId = UUID.randomUUID().toString();
        List<String> actualDiff = graphDiff(beforeJson, afterJson);
        actualDiff.add("Undo 历史: 批准后本次事务成为新的历史基线，较早的 Undo/Redo 记录将清空");
        GraphPatchApprovalPresenter.ApprovalSummary summary = new GraphPatchApprovalPresenter.ApprovalSummary(
                approvalId, patchHash, patch.expectedRevision().value(), patch.operations().size(), actualDiff);
        return new PlannedPatch(patch, patchHash, beforeJson, afterJson, summary);
    }

    private void applyOperation(GraphPatch patch, int index, GraphPatch.Operation operation,
                                EditorContext context, NodeGraph graph, Map<String, String> aliases,
                                RegistryAccess registryAccess) {
        switch (operation) {
            case GraphPatch.AddNode add -> {
                if (!NodeRegistry.INSTANCE.has(add.typeId())) throw fail("NODE_TYPE_NOT_FOUND", "节点类型不存在: " + add.typeId());
                if (!add.properties().isEmpty()) throw unsupported(index, operation);
                String nodeId = deterministicNodeId(patch.idempotencyKey(), add.alias());
                if (graph.getNode(nodeId) != null) throw fail("NODE_ID_CONFLICT", "生成的节点 ID 已存在: " + nodeId);
                aliases.put(add.alias(), nodeId);
                context.getGraphController().addNode(new NodeData(nodeId, add.typeId(),
                        finiteFloat(add.position().x()), finiteFloat(add.position().y())));
            }
            case GraphPatch.MoveNode move -> {
                String nodeId = resolve(move.node(), aliases);
                requireNode(graph, nodeId);
                context.getGraphController().setNodePosition(nodeId, finiteFloat(move.position().x()), finiteFloat(move.position().y()));
            }
            case GraphPatch.SetPortValue set -> {
                String nodeId = resolve(set.port().node(), aliases);
                NodeData node = requireNode(graph, nodeId);
                PortRow row = requireInputRow(node, set.port().portId());
                if (row.uiHint() == UIHint.SELECT) throw fail("SELECT_TOOL_REQUIRED", "SELECT 端口必须使用 set_select_value");
                if (hasInbound(graph, nodeId, set.port().portId())) {
                    throw fail("PORT_CONNECTED", "已连接的输入端口不能写入存储值");
                }
                if (set.expectedOldValue() == null && set.port().node().alias() == null) {
                    throw fail("EXPECTED_OLD_VALUE_REQUIRED", "修改现有节点端口必须提供 expected_old_value");
                }
                JsonElement actual = GSON.toJsonTree(node.inputs != null && node.inputs.containsKey(set.port().portId())
                        ? node.inputs.get(set.port().portId()) : row.leftPort().defaultValue());
                if (set.expectedOldValue() != null && !actual.equals(set.expectedOldValue())) {
                    throw fail("OLD_VALUE_CONFLICT", "端口旧值已变化: " + nodeId + "." + set.port().portId());
                }
                Object decoded = InputValueCodec.decode(row, set.value());
                context.getGraphController().setNodeInputValue(nodeId, set.port().portId(), decoded);
            }
            case GraphPatch.SetSelectValue set -> {
                String nodeId = resolve(set.port().node(), aliases);
                NodeData node = requireNode(graph, nodeId);
                PortRow row = requireInputRow(node, set.port().portId());
                if (row.uiHint() != UIHint.SELECT) throw fail("PORT_OPTIONS_UNSUPPORTED", "指定端口不是 SELECT");
                PortOptionResolver.Resolution options = PortOptionResolver.resolve(row, registryAccess,
                        key -> Component.translatable(key).getString());
                if (!options.available()) throw fail("PORT_OPTIONS_UNAVAILABLE", "当前下拉选项注册表不可用");
                boolean newAlias = set.port().node().alias() != null;
                if ((!newAlias && set.optionContextToken() == null)
                        || (set.optionContextToken() != null
                        && !PortOptionContext.token(options).equals(set.optionContextToken()))) {
                    throw fail("OPTION_CONTEXT_CONFLICT",
                            "下拉选项上下文已变化，请重新调用 get_node_type_port_options 或 get_port_options");
                }
                Object actualRaw = node.inputs != null && node.inputs.containsKey(set.port().portId())
                        ? node.inputs.get(set.port().portId()) : row.leftPort().defaultValue();
                String actual = actualRaw == null ? "" : actualRaw.toString();
                if (set.port().node().alias() == null && set.expectedOldValue() == null) {
                    throw fail("EXPECTED_OLD_VALUE_REQUIRED", "修改现有 SELECT 端口必须提供 expected_old_value");
                }
                if (set.expectedOldValue() != null && !set.expectedOldValue().equals(actual)) {
                    throw fail("OLD_VALUE_CONFLICT", "下拉端口旧值已变化");
                }
                boolean valid = options.options().stream().anyMatch(option -> option.id().equals(set.optionId()));
                if (!valid) throw fail("OPTION_ID_INVALID", "option_id 不在当前合法选项中: " + set.optionId());
                context.getGraphController().setNodeInputValue(nodeId, set.port().portId(), set.optionId());
            }
            case GraphPatch.Connect connect -> {
                String outNode = resolve(connect.from().node(), aliases);
                String inNode = resolve(connect.to().node(), aliases);
                NodeData outNodeData = requireNode(graph, outNode);
                NodeData inNodeData = requireNode(graph, inNode);
                rejectImplicitRewire(graph, outNode, connect.from().portId(), inNode, connect.to().portId());
                CmdConnect command = new CmdConnect(context.getGraphController(), graph, outNode,
                        connect.from().portId(), inNode, connect.to().portId());
                if (!command.canExecute()) {
                    throw invalidConnection(context, index, outNodeData, connect.from().portId(),
                            inNodeData, connect.to().portId());
                }
                command.execute();
            }
            default -> throw unsupported(index, operation);
        }
    }

    private CommandResult commit(PlannedPatch plan, CommandInvocationContext.CancellationToken cancellation) {
        Core.checkUiThread();
        requireOpenAndBound(plan.patch);
        if (!sha256(GraphPatchCodec.toJson(plan.patch)).equals(plan.patchHash)) {
            throw fail("PATCH_HASH_MISMATCH", "审批后的 GraphPatch hash 不匹配");
        }
        if (!canonicalGraphJson(session.editorContext.getGraph()).equals(plan.beforeJson)) {
            throw fail("REVISION_CONFLICT", "审批期间蓝图内容已变化，请重新读取并规划");
        }
        if (cancellation.isCancelled()) throw fail("CANCELLED", "GraphPatch 在提交前被取消");
        String changeId = UUID.randomUUID().toString();
        session.editorContext.getCommandManager().clearHistory();
        boolean executed = session.editorContext.getCommandManager().execute(new CmdReplaceGraphState(
                session.editorContext, plan.beforeJson, plan.afterJson, changeId));
        if (!executed) throw fail("PATCH_NO_CHANGES", "GraphPatch 提交未产生变化");
        GeometryNode.LOGGER.info("Graph patch committed: approval={}, change={}, revision={}, operations={}",
                plan.summary.approvalId(), changeId, session.revision(), plan.patch.operations().size());
        JsonObject data = new JsonObject();
        data.addProperty("approval_id", plan.summary.approvalId());
        data.addProperty("patch_hash", plan.patchHash);
        data.addProperty("change_id", changeId);
        data.addProperty("revision", session.revision());
        data.addProperty("operation_count", plan.patch.operations().size());
        return new CommandResult(true, "GRAPH_PATCH_APPLIED", "GraphPatch 已原子提交", data,
                List.of(), session.revision(), changeId, CommandResult.ClientAction.NONE);
    }

    private void requireOpenAndBound(GraphPatch patch) {
        if (!DocumentManager.INSTANCE.getSessions().contains(session)) throw fail("GRAPH_SESSION_CLOSED", "绑定的蓝图会话已关闭");
        if (!session.sessionId().toString().equals(patch.session().id())) throw fail("GRAPH_SESSION_MISMATCH", "GraphPatch session_id 与当前 Run 不匹配");
        if (!scope.id().equals(patch.scope().id())) throw fail("GRAPH_SCOPE_MISMATCH", "GraphPatch scope_id 与当前 Run 不匹配");
        if (session.revision() != patch.expectedRevision().value()) throw fail("REVISION_CONFLICT", "蓝图 revision 已变化");
        if (scope.resolve(session.editorContext.getGraph()) == null) throw fail("GRAPH_SCOPE_CLOSED", "绑定的 Group Scope 已不存在");
    }

    private void enterScope(EditorContext context) {
        NodeGraph current = context.getGraph();
        for (String id : scope.groupPath()) {
            NodeData group = current.getNode(id);
            if (group == null || !context.enterGroupScope(group)) throw fail("GRAPH_SCOPE_CLOSED", "绑定的 Group Scope 已不存在");
            current = context.getCurrentGraph();
        }
    }

    private static PortRow requireInputRow(NodeData node, String portId) {
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (definition != null) for (PortRow row : definition.rows()) {
            if (row.leftPort() != null && row.leftPort().id().equals(portId)) return row;
        }
        throw fail("PORT_NOT_FOUND", "节点不存在输入端口: " + portId);
    }

    private static PatchFailure invalidConnection(EditorContext context, int operationIndex,
                                                   NodeData outputNode, String outputPortId,
                                                   NodeData inputNode, String inputPortId) {
        NodeDef outputDefinition = NodeRegistry.INSTANCE.resolveDefinition(outputNode);
        NodeDef inputDefinition = NodeRegistry.INSTANCE.resolveDefinition(inputNode);
        PortDef outputPort = findPort(outputDefinition, outputPortId, false);
        PortDef inputPort = findPort(inputDefinition, inputPortId, true);
        String reason;
        if (Objects.equals(outputNode.id, inputNode.id)) {
            reason = "不能连接同一个节点";
        } else if (outputPort == null) {
            boolean wrongSide = findPort(outputDefinition, outputPortId, true) != null;
            reason = wrongSide ? "from.port_id 是输入端口，不是输出端口"
                    : "源节点不存在该输出端口；available_outputs=" + portIds(outputDefinition, false);
        } else if (inputPort == null) {
            boolean wrongSide = findPort(inputDefinition, inputPortId, false) != null;
            reason = wrongSide ? "to.port_id 是输出端口，不是输入端口"
                    : "目标节点不存在该输入端口；available_inputs=" + portIds(inputDefinition, true);
        } else {
            PortType outputType = context.getGraphController()
                    .getResolvedPortType(outputNode.id, outputPortId, false);
            PortType inputType = context.getGraphController()
                    .getResolvedPortType(inputNode.id, inputPortId, true);
            if (!PortType.isCompatible(outputType, inputType)) {
                reason = "端口类型不兼容: output_type=" + typeName(outputType)
                        + ", input_type=" + typeName(inputType);
            } else {
                reason = "连接被 Group、Reroute 或当前 Scope 的端口规则拒绝";
            }
        }
        return fail("PORT_CONNECTION_INVALID", "operation[" + operationIndex + "] connect 无效: " + reason
                + "; from=" + nodeRef(outputNode) + "." + outputPortId
                + "; to=" + nodeRef(inputNode) + "." + inputPortId);
    }

    private static PortDef findPort(NodeDef definition, String portId, boolean inputSide) {
        if (definition == null) return null;
        for (PortRow row : definition.rows()) {
            PortDef port = inputSide ? row.leftPort() : row.rightPort();
            if (port != null && Objects.equals(port.id(), portId)) return port;
        }
        return null;
    }

    private static String portIds(NodeDef definition, boolean inputSide) {
        if (definition == null) return "[]";
        List<String> ids = new ArrayList<>();
        for (PortRow row : definition.rows()) {
            PortDef port = inputSide ? row.leftPort() : row.rightPort();
            if (port != null) ids.add(port.id());
            if (ids.size() == 20) break;
        }
        return ids.toString();
    }

    private static String nodeRef(NodeData node) {
        return (node.type == null || node.type.isBlank() ? "unknown" : node.type) + "[" + node.id + "]";
    }

    private static String typeName(PortType type) {
        return type == null ? "missing" : type.name();
    }

    private static NodeData requireNode(NodeGraph graph, String nodeId) {
        NodeData node = graph.getNode(nodeId);
        if (node == null) throw fail("NODE_NOT_FOUND", "节点不存在: " + nodeId);
        return node;
    }

    private static String resolve(GraphPatch.NodeRef ref, Map<String, String> aliases) {
        if (ref.id() != null) return ref.id();
        String id = aliases.get(ref.alias());
        if (id == null) throw fail("PATCH_UNKNOWN_ALIAS", "节点 alias 不存在: " + ref.alias());
        return id;
    }

    private static float finiteFloat(double value) {
        float converted = (float) value;
        if (!Float.isFinite(converted)) throw fail("POSITION_OUT_OF_RANGE", "节点坐标超出 float 范围");
        return converted;
    }

    private static String deterministicNodeId(String idempotencyKey, String alias) {
        return UUID.nameUUIDFromBytes((idempotencyKey + "\u0000" + alias).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static ApprovalDecision awaitDecision(
                                                   CompletableFuture<GraphPatchApprovalPresenter.ApprovalOutcome> decision,
                                                   CommandInvocationContext.CancellationToken cancellation) throws InterruptedException {
        long deadline = System.nanoTime() + APPROVAL_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (cancellation.isCancelled()) {
                decision.cancel(false);
                return ApprovalDecision.CANCELLED;
            }
            try {
                GraphPatchApprovalPresenter.ApprovalOutcome outcome = decision.get(100, TimeUnit.MILLISECONDS);
                return switch (outcome) {
                    case APPROVED -> ApprovalDecision.APPROVED;
                    case REJECTED -> ApprovalDecision.REJECTED;
                    case DISMISSED -> ApprovalDecision.DISMISSED;
                    case CANCELLED, HOST_DESTROYED -> ApprovalDecision.CANCELLED;
                    case UI_UNAVAILABLE -> ApprovalDecision.UI_UNAVAILABLE;
                    case ALREADY_PENDING -> ApprovalDecision.ALREADY_PENDING;
                };
            } catch (TimeoutException ignored) {
            } catch (CancellationException cancelled) {
                return ApprovalDecision.CANCELLED;
            } catch (ExecutionException failure) {
                return ApprovalDecision.UI_UNAVAILABLE;
            }
        }
        decision.cancel(false);
        return ApprovalDecision.TIMED_OUT;
    }

    private static boolean hasInbound(NodeGraph graph, String nodeId, String portId) {
        for (NodeData source : graph.nodes.values()) {
            if (source.outputs != null) for (List<Connection> connections : source.outputs.values()) {
                if (connections != null && connections.stream().anyMatch(link ->
                        link.targetNodeId().equals(nodeId) && link.targetPortName().equals(portId))) return true;
            }
            if (source.execOutputs != null && source.execOutputs.values().stream().anyMatch(link ->
                    link.targetNodeId().equals(nodeId) && link.targetPortName().equals(portId))) return true;
        }
        return false;
    }

    private static void rejectImplicitRewire(NodeGraph graph, String outNodeId, String outPortId,
                                             String inNodeId, String inPortId) {
        NodeData outNode = graph.getNode(outNodeId);
        if (outNode != null && outNode.outputs != null) {
            List<Connection> existing = outNode.outputs.get(outPortId);
            if (existing != null && existing.stream().anyMatch(link ->
                    link.targetNodeId().equals(inNodeId) && link.targetPortName().equals(inPortId))) {
                throw fail("CONNECTION_EXISTS", "指定连接已经存在");
            }
        }
        if (outNode != null && outNode.execOutputs != null && outNode.execOutputs.containsKey(outPortId)) {
            throw fail("OUTPUT_ALREADY_CONNECTED", "执行流输出已连接，P5 首版不做隐式替换");
        }
        if (hasInbound(graph, inNodeId, inPortId)) {
            throw fail("INPUT_ALREADY_CONNECTED", "目标输入已连接，P5 首版不做隐式替换");
        }
    }

    private static <T> CompletableFuture<T> onUi(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable task = () -> {
            try { result.complete(action.get()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        };
        if (Core.isOnUiThread()) {
            task.run();
        } else {
            try {
                if (!Core.getUiHandlerAsync().post(task)) {
                    result.completeExceptionally(new IllegalStateException("ModernUI queue is unavailable"));
                }
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }
        return result;
    }

    private static RegistryAccess registryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    private static String canonicalGraphJson(NodeGraph graph) {
        return GSON.toJson(sort(JsonParser.parseString(GraphJsonIO.toJson(graph))));
    }

    private static JsonElement sort(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted(Comparator.naturalOrder())
                    .forEach(key -> sorted.add(key, sort(value.getAsJsonObject().get(key))));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement item : value.getAsJsonArray()) array.add(sort(item));
            return array;
        }
        return value.deepCopy();
    }

    private static List<String> graphDiff(String beforeJson, String afterJson) {
        ArrayList<String> changes = new ArrayList<>();
        collectDiff("$", JsonParser.parseString(beforeJson), JsonParser.parseString(afterJson), changes);
        if (changes.size() > 512) throw fail("DIFF_TOO_LARGE", "GraphPatch Diff 超过 512 项，请缩小 Patch");
        return changes;
    }

    private static void collectDiff(String path, JsonElement before, JsonElement after, List<String> changes) {
        if (Objects.equals(before, after)) return;
        if (before != null && after != null && before.isJsonObject() && after.isJsonObject()) {
            java.util.TreeSet<String> keys = new java.util.TreeSet<>();
            keys.addAll(before.getAsJsonObject().keySet());
            keys.addAll(after.getAsJsonObject().keySet());
            for (String key : keys) {
                collectDiff(path + "." + key, before.getAsJsonObject().get(key), after.getAsJsonObject().get(key), changes);
                if (changes.size() > 512) return;
            }
            return;
        }
        if (before != null && after != null && before.isJsonArray() && after.isJsonArray()) {
            int size = Math.max(before.getAsJsonArray().size(), after.getAsJsonArray().size());
            for (int index = 0; index < size; index++) {
                JsonElement oldItem = index < before.getAsJsonArray().size() ? before.getAsJsonArray().get(index) : null;
                JsonElement newItem = index < after.getAsJsonArray().size() ? after.getAsJsonArray().get(index) : null;
                collectDiff(path + "[" + index + "]", oldItem, newItem, changes);
                if (changes.size() > 512) return;
            }
            return;
        }
        String oldValue = before == null ? "<missing>" : abbreviate(before);
        String newValue = after == null ? "<missing>" : abbreviate(after);
        changes.add(path + ": " + oldValue + " -> " + newValue);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static PatchFailure unsupported(int index, GraphPatch.Operation operation) {
        return fail("UNSUPPORTED_OPERATION", "P5 首版暂不支持 operation[" + index + "]: " + operation.op());
    }

    private static PatchFailure fail(String code, String message) { return new PatchFailure(code, message); }
    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank() ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static String abbreviate(Object value) {
        String text = value instanceof JsonElement json ? GSON.toJson(json) : String.valueOf(value);
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }

    private record PlannedPatch(GraphPatch patch, String patchHash, String beforeJson, String afterJson,
                                GraphPatchApprovalPresenter.ApprovalSummary summary) {}
    private record PlanSnapshot(String beforeJson, RegistryAccess registryAccess) {}
    private record CompletedPatch(String patchHash, CommandResult result) {}
    private enum ApprovalDecision {
        APPROVED, REJECTED, DISMISSED, CANCELLED, TIMED_OUT, UI_UNAVAILABLE, ALREADY_PENDING
    }
    private static final class PatchFailure extends RuntimeException {
        private final String code;
        private PatchFailure(String code, String message) { super(message); this.code = code; }
    }
}
