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
import com.mine.geometry_node.client.ai.command.NodeCatalogIndex;
import com.mine.geometry_node.client.ai.graph.GraphPatchCodec;
import com.mine.geometry_node.client.ai.graph.GraphPatchContractValidator;
import com.mine.geometry_node.client.ai.graph.InputValueCodec;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdConnect;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdDisconnect;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveFrames;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveNodes;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdReplaceGraphState;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.group.GroupNodeFactory;
import com.mine.geometry_node.core.node.group.GroupNodeTypes;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortOptionContext;
import com.mine.geometry_node.core.node.definition.port.PortOptionResolver;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import icyllis.modernui.core.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Plans a patch against a snapshot, revalidates its revision, and commits it as one undoable command. */
public final class GraphPatchTransactionService {
    private static final Duration UI_HANDOFF_TIMEOUT = Duration.ofSeconds(30);
    private static final Gson GSON = new Gson();

    private final GraphSession session;
    private final BoundGraphScope scope;
    private final java.util.function.BooleanSupplier targetValidator;
    private final GraphPatchIdempotencyStore idempotencyStore;

    public GraphPatchTransactionService(GraphSession session, BoundGraphScope scope) {
        this(session, scope, () -> true, new GraphPatchIdempotencyStore());
    }

    public GraphPatchTransactionService(GraphSession session, BoundGraphScope scope,
                                        java.util.function.BooleanSupplier targetValidator,
                                        GraphPatchIdempotencyStore idempotencyStore) {
        this.session = Objects.requireNonNull(session, "document");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.targetValidator = Objects.requireNonNull(targetValidator, "targetValidator");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
    }

    public CommandResult apply(GraphPatch patch, CommandInvocationContext.CancellationToken cancellation) {
        synchronized (idempotencyStore) {
            return applySerialized(patch, cancellation);
        }
    }

    private CommandResult applySerialized(GraphPatch patch, CommandInvocationContext.CancellationToken cancellation) {
        if (Minecraft.getInstance().isSameThread() || Core.isOnUiThread()) {
            return CommandResult.failure("THREAD_VIOLATION", "GraphPatch 事务预检不能阻塞客户端或 UI 线程");
        }
        cancellation = cancellation == null ? CommandInvocationContext.CancellationToken.NONE : cancellation;
        try {
            String requestedHash = sha256(GraphPatchCodec.toJson(patch));
            GraphPatchIdempotencyStore.CompletedPatch previous = idempotencyStore.get(patch.idempotencyKey());
            if (previous != null) {
                return previous.patchHash().equals(requestedHash)
                        ? previous.result()
                        : CommandResult.failure("IDEMPOTENCY_CONFLICT", "idempotency_key 已用于不同 GraphPatch");
            }
            if (idempotencyStore.size() >= GraphPatchIdempotencyStore.MAX_KEYS) {
                return CommandResult.failure("IDEMPOTENCY_LIMIT_REACHED",
                        "当前 PowerShell Run 的 GraphPatch 幂等键已达到上限，请重启 Run");
            }
            PlanSnapshot snapshot = awaitUi(onUi(() -> capturePlanSnapshot(patch)), cancellation);
            PlannedPatch plan = plan(patch, requestedHash, snapshot);
            if (cancellation.isCancelled()) return CommandResult.failure("CANCELLED", "GraphPatch 在提交前已取消");
            CommandInvocationContext.CancellationToken commitCancellation = cancellation;
            CommandResult result = awaitUi(onUi(() -> commit(plan, commitCancellation)), cancellation);
            if (result.ok()) {
                idempotencyStore.put(patch.idempotencyKey(), plan.patchHash, result);
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

    private PlannedPatch plan(GraphPatch patch, String patchHash, PlanSnapshot snapshot) {
        List<GraphPatchContractValidator.Diagnostic> contract = GraphPatchContractValidator.validate(patch);
        if (!contract.isEmpty()) throw fail(contract.getFirst().code(), contract.getFirst().message());

        String beforeJson = snapshot.beforeJson;
        NodeGraph trialRoot = GraphJsonIO.fromJson(beforeJson);
        EditorContext trialContext = new EditorContext(trialRoot);
        enterScope(trialContext);
        NodeGraph trialScope = scope.resolve(trialRoot);
        if (trialScope == null) throw fail("GRAPH_SCOPE_CLOSED", "PowerShell 启动时绑定的 Group Scope 已不存在");

        Map<String, String> aliases = new LinkedHashMap<>();
        Map<String, String> frameAliases = new LinkedHashMap<>();
        Map<String, ResolvedPort> portAliases = new LinkedHashMap<>();
        Map<String, ResolvedBranch> branchAliases = new LinkedHashMap<>();
        for (int index = 0; index < patch.operations().size(); index++) {
            try {
                applyOperation(patch, index, patch.operations().get(index), trialContext, trialScope,
                        aliases, frameAliases, portAliases, branchAliases, snapshot.registryAccess);
            } catch (PatchFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw fail("PATCH_OPERATION_INVALID", "operation[" + index + "] 无效: " + safeMessage(failure));
            }
        }
        String afterJson = canonicalGraphJson(trialRoot);
        if (beforeJson.equals(afterJson)) throw fail("PATCH_NO_CHANGES", "GraphPatch 没有产生任何变化");
        try {
            GraphCompilationService.INSTANCE.compile(afterJson);
        } catch (RuntimeException failure) {
            throw fail("GRAPH_COMPILE_FAILED", "GraphPatch 事务预检编译失败: " + safeMessage(failure));
        }
        return new PlannedPatch(patch, patchHash, beforeJson, afterJson);
    }

    private void applyOperation(GraphPatch patch, int index, GraphPatch.Operation operation,
                                EditorContext context, NodeGraph graph, Map<String, String> aliases,
                                Map<String, String> frameAliases,
                                Map<String, ResolvedPort> portAliases,
                                Map<String, ResolvedBranch> branchAliases,
                                RegistryAccess registryAccess) {
        switch (operation) {
            case GraphPatch.AddNode add -> {
                String canonicalTypeId;
                try {
                    canonicalTypeId = NodeCatalogIndex.canonicalTypeId(add.typeId());
                } catch (IllegalArgumentException failure) {
                    throw fail("NODE_TYPE_INVALID", failure.getMessage());
                }
                if (!NodeRegistry.INSTANCE.has(canonicalTypeId)) {
                    throw fail("NODE_TYPE_NOT_FOUND", "节点类型不存在: " + add.typeId());
                }
                String nodeId = deterministicId("node", patch.idempotencyKey(), add.alias());
                if (graph.getNode(nodeId) != null) throw fail("NODE_ID_CONFLICT", "生成的节点 ID 已存在: " + nodeId);
                aliases.put(add.alias(), nodeId);
                NodeData node = new NodeData(nodeId, canonicalTypeId,
                        finiteFloat(add.position().x()), finiteFloat(add.position().y()));
                context.getGraphController().addNode(node);
                for (Map.Entry<String, JsonElement> property : add.properties().entrySet()) {
                    applyNodeProperty(context, node, property.getKey(), property.getValue(), frameAliases);
                }
            }
            case GraphPatch.RemoveNode remove -> {
                String nodeId = resolve(remove.node(), aliases);
                NodeData node = requireNode(graph, nodeId);
                if (GroupNodeFactory.isBoundaryNode(node)) {
                    throw fail("GROUP_BOUNDARY_IMMUTABLE", "group_in 和 group_out 是强制边界节点，不能删除");
                }
                new CmdRemoveNodes(context.getGraphController(), graph, List.of(nodeId)).execute();
            }
            case GraphPatch.MoveNode move -> {
                String nodeId = resolve(move.node(), aliases);
                requireNode(graph, nodeId);
                context.getGraphController().setNodePosition(nodeId, finiteFloat(move.position().x()), finiteFloat(move.position().y()));
            }
            case GraphPatch.SetPortValue set -> {
                ResolvedPort port = resolve(set.port(), aliases, portAliases);
                String nodeId = port.nodeId();
                NodeData node = requireNode(graph, nodeId);
                PortRow row = requireInputRow(node, port.portId());
                if (row.uiHint() == UIHint.SELECT) throw fail("SELECT_TOOL_REQUIRED", "SELECT 端口必须使用 set_select_value");
                if (hasInbound(graph, nodeId, port.portId())) {
                    throw fail("PORT_CONNECTED", "已连接的输入端口不能写入存储值");
                }
                if (set.expectedOldValue() == null && !isNewReference(set.port())) {
                    throw fail("EXPECTED_OLD_VALUE_REQUIRED", "修改现有节点端口必须提供 expected_old_value");
                }
                JsonElement actual = GSON.toJsonTree(node.inputs != null && node.inputs.containsKey(port.portId())
                        ? node.inputs.get(port.portId()) : row.leftPort().defaultValue());
                if (set.expectedOldValue() != null && !actual.equals(set.expectedOldValue())) {
                    throw fail("OLD_VALUE_CONFLICT", "端口旧值已变化: " + nodeId + "." + port.portId());
                }
                Object decoded = InputValueCodec.decode(row, set.value());
                context.getGraphController().setNodeInputValue(nodeId, port.portId(), decoded);
            }
            case GraphPatch.SetSelectValue set -> {
                ResolvedPort port = resolve(set.port(), aliases, portAliases);
                String nodeId = port.nodeId();
                NodeData node = requireNode(graph, nodeId);
                PortRow row = requireInputRow(node, port.portId());
                if (row.uiHint() != UIHint.SELECT) throw fail("PORT_OPTIONS_UNSUPPORTED", "指定端口不是 SELECT");
                PortOptionResolver.Resolution options = PortOptionResolver.resolve(row, registryAccess,
                        key -> Component.translatable(key).getString());
                if (!options.available()) throw fail("PORT_OPTIONS_UNAVAILABLE", "当前下拉选项注册表不可用");
                boolean newAlias = isNewReference(set.port());
                if ((!newAlias && set.optionContextToken() == null)
                        || (set.optionContextToken() != null
                        && !PortOptionContext.token(options).equals(set.optionContextToken()))) {
                    throw fail("OPTION_CONTEXT_CONFLICT",
                            "下拉选项上下文已变化，请重新调用 get_node_type_port_options 或 get_port_options");
                }
                Object actualRaw = node.inputs != null && node.inputs.containsKey(port.portId())
                        ? node.inputs.get(port.portId()) : row.leftPort().defaultValue();
                String actual = actualRaw == null ? "" : actualRaw.toString();
                if (!newAlias && set.expectedOldValue() == null) {
                    throw fail("EXPECTED_OLD_VALUE_REQUIRED", "修改现有 SELECT 端口必须提供 expected_old_value");
                }
                if (set.expectedOldValue() != null && !set.expectedOldValue().equals(actual)) {
                    throw fail("OLD_VALUE_CONFLICT", "下拉端口旧值已变化");
                }
                boolean valid = options.options().stream().anyMatch(option -> option.id().equals(set.optionId()));
                if (!valid) throw fail("OPTION_ID_INVALID", "option_id 不在当前合法选项中: " + set.optionId());
                context.getGraphController().setNodeInputValue(nodeId, port.portId(), set.optionId());
            }
            case GraphPatch.Connect connect -> {
                ResolvedPort from = resolve(connect.from(), aliases, portAliases);
                ResolvedPort to = resolve(connect.to(), aliases, portAliases);
                String outNode = from.nodeId();
                String inNode = to.nodeId();
                NodeData outNodeData = requireNode(graph, outNode);
                NodeData inNodeData = requireNode(graph, inNode);
                rejectImplicitRewire(graph, outNode, from.portId(), inNode, to.portId());
                CmdConnect command = new CmdConnect(context.getGraphController(), graph, outNode,
                        from.portId(), inNode, to.portId());
                if (!command.canExecute()) {
                    throw invalidConnection(context, index, outNodeData, from.portId(),
                            inNodeData, to.portId());
                }
                command.execute();
            }
            case GraphPatch.Disconnect disconnect -> {
                ResolvedPort from = resolve(disconnect.from(), aliases, portAliases);
                ResolvedPort to = resolve(disconnect.to(), aliases, portAliases);
                requireNode(graph, from.nodeId());
                requireNode(graph, to.nodeId());
                if (!context.getGraphController().hasConnection(
                        from.nodeId(), from.portId(), to.nodeId(), to.portId())) {
                    throw fail("CONNECTION_NOT_FOUND", "指定连接不存在");
                }
                new CmdDisconnect(context.getGraphController(), from.nodeId(), from.portId(),
                        to.nodeId(), to.portId()).execute();
            }
            case GraphPatch.SetNodeProperty set -> {
                NodeData node = requireNode(graph, resolve(set.node(), aliases));
                applyNodeProperty(context, node, set.property(), set.value(), frameAliases);
            }
            case GraphPatch.AddFrame add -> {
                requireRootScope();
                String frameId = deterministicId("frame", patch.idempotencyKey(), add.alias());
                if (context.getGraph().getFrame(frameId) != null) {
                    throw fail("FRAME_ID_CONFLICT", "生成的 Frame ID 已存在: " + frameId);
                }
                FrameData frame = new FrameData(frameId, finiteFloat(add.position().x()),
                        finiteFloat(add.position().y()));
                frame.title = add.title();
                frame.setSize(positiveFloat(add.width(), "Frame width"),
                        positiveFloat(add.height(), "Frame height"));
                context.getGraphController().addFrame(frame);
                frameAliases.put(add.alias(), frameId);
            }
            case GraphPatch.RemoveFrame remove -> {
                requireRootScope();
                String frameId = resolve(remove.frame(), frameAliases);
                FrameData frame = requireFrame(context.getGraph(), frameId);
                String oldParent = frame.parentFrame;
                new CmdRemoveFrames(context.getGraphController(), List.of(frameId)).execute();
                if (oldParent != null) context.getGraphController().updateFrameBounds(oldParent);
            }
            case GraphPatch.SetFrameProperty set -> {
                requireRootScope();
                FrameData frame = requireFrame(context.getGraph(), resolve(set.frame(), frameAliases));
                applyFrameProperty(context, frame, set.property(), set.value(), frameAliases);
            }
            case GraphPatch.AddDynamicBranch add -> addDynamicBranch(context, graph, add, aliases,
                    branchAliases, portAliases);
            case GraphPatch.RemoveDynamicBranch remove -> removeDynamicBranch(context, graph,
                    remove.branch(), aliases, branchAliases, portAliases);
            case GraphPatch.AddGroupVirtualPort add -> addGroupVirtualPort(context, graph, add,
                    aliases, portAliases);
            case GraphPatch.RemoveGroupVirtualPort remove -> {
                ResolvedPort port = resolve(remove.port(), aliases, portAliases);
                NodeData boundary = requireBoundaryNode(graph, port.nodeId());
                if (GroupNodeFactory.findBoundaryPortCategory(boundary, port.portId()) == null) {
                    throw fail("GROUP_PORT_NOT_FOUND", "图组虚拟端口不存在: " + port.portId());
                }
                context.getGraphController().removeGroupVirtualPort(port.nodeId(), port.portId());
            }
            case GraphPatch.RenamePort rename -> {
                ResolvedPort port = resolve(rename.port(), aliases, portAliases);
                NodeData node = requireNode(graph, port.nodeId());
                boolean inputSide = "input".equals(rename.direction());
                PortDef definition = findPort(requireDefinition(node), port.portId(), inputSide);
                if (definition == null) {
                    throw fail("PORT_NOT_FOUND", "指定方向不存在端口: " + port.portId());
                }
                String category = GroupNodeTypes.CATEGORY_INPUTS;
                if (inputSide && definition.type().isFlow()) category = GroupNodeTypes.CATEGORY_EXEC_INPUTS;
                if (!inputSide && !definition.type().isFlow()) category = GroupNodeTypes.CATEGORY_OUTPUTS;
                if (!inputSide && definition.type().isFlow()) category = GroupNodeTypes.CATEGORY_EXEC_OUTPUTS;
                context.getGraphController().setPortCustomName(port.nodeId(), category, port.portId(), rename.name());
            }
        }
    }

    private void addDynamicBranch(EditorContext context, NodeGraph graph,
                                  GraphPatch.AddDynamicBranch operation,
                                  Map<String, String> nodeAliases,
                                  Map<String, ResolvedBranch> branchAliases,
                                  Map<String, ResolvedPort> portAliases) {
        String nodeId = resolve(operation.node(), nodeAliases);
        NodeData node = requireNode(graph, nodeId);
        boolean inputSide = "input".equals(operation.direction());
        String countKey = inputSide
                ? StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()
                : StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id();
        NodeDef before = requireDefinition(node);
        int minimum = inputSide
                ? before.getMetaOrDefault(SchemaKeys.MIN_DYNAMIC_INPUT, 1)
                : before.getMetaOrDefault(SchemaKeys.MIN_DYNAMIC_OUTPUT, 1);
        Integer maximum = inputSide
                ? before.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).orElse(null)
                : before.getMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT).orElse(null);
        if (maximum == null) {
            throw fail("DYNAMIC_BRANCH_UNSUPPORTED",
                    "节点不支持 " + operation.direction() + " 动态分支: " + nodeId);
        }
        int current = dynamicCount(node, countKey, minimum);
        if (current >= maximum) throw fail("DYNAMIC_BRANCH_LIMIT", "动态分支已达到上限: " + maximum);

        Set<String> previousPorts = allPortIds(before);
        int newIndex = current + 1;
        context.getGraphController().setNodeInputValue(nodeId, countKey, newIndex);
        NodeDef after = requireDefinition(node);
        Set<String> addedPorts = new LinkedHashSet<>(allPortIds(after));
        addedPorts.removeAll(previousPorts);
        if (addedPorts.isEmpty()) {
            throw fail("DYNAMIC_BRANCH_SCHEMA_INVALID", "增加动态分支后没有生成新端口");
        }

        ResolvedBranch branch = new ResolvedBranch(nodeId, operation.direction(), newIndex);
        branchAliases.put(operation.alias(), branch);
        for (String portId : addedPorts) {
            String basePortId = dynamicPortBase(portId, newIndex);
            portAliases.put(operation.alias() + "." + basePortId, new ResolvedPort(nodeId, portId));
        }
    }

    private void removeDynamicBranch(EditorContext context, NodeGraph graph,
                                     GraphPatch.BranchRef reference,
                                     Map<String, String> nodeAliases,
                                     Map<String, ResolvedBranch> branchAliases,
                                     Map<String, ResolvedPort> portAliases) {
        ResolvedBranch branch;
        if (reference.alias() != null) {
            branch = branchAliases.get(reference.alias());
            if (branch == null) throw fail("PATCH_UNKNOWN_BRANCH_ALIAS", "分支 alias 不存在: " + reference.alias());
        } else {
            branch = new ResolvedBranch(resolve(reference.node(), nodeAliases),
                    reference.direction(), reference.index());
        }
        NodeData node = requireNode(graph, branch.nodeId());
        boolean inputSide = "input".equals(branch.direction());
        String countKey = inputSide
                ? StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()
                : StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id();
        NodeDef definition = requireDefinition(node);
        int minimum = inputSide
                ? definition.getMetaOrDefault(SchemaKeys.MIN_DYNAMIC_INPUT, 1)
                : definition.getMetaOrDefault(SchemaKeys.MIN_DYNAMIC_OUTPUT, 1);
        Integer maximum = inputSide
                ? definition.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).orElse(null)
                : definition.getMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT).orElse(null);
        if (maximum == null) throw fail("DYNAMIC_BRANCH_UNSUPPORTED", "节点不支持指定方向的动态分支");
        int current = dynamicCount(node, countKey, minimum);
        if (current <= minimum) throw fail("DYNAMIC_BRANCH_MINIMUM", "动态分支不能少于: " + minimum);
        if (branch.index() < 1 || branch.index() > current) {
            throw fail("DYNAMIC_BRANCH_NOT_FOUND", "动态分支序号不存在: " + branch.index());
        }
        context.getGraphController().removeDynamicBranch(
                branch.nodeId(), countKey, branch.index(), current);
        rebaseDynamicAliases(node, definition, branch, branchAliases, portAliases);
    }

    private static void addGroupVirtualPort(EditorContext context, NodeGraph graph,
                                            GraphPatch.AddGroupVirtualPort operation,
                                            Map<String, String> nodeAliases,
                                            Map<String, ResolvedPort> portAliases) {
        String boundaryId = resolve(operation.node(), nodeAliases);
        NodeData boundary = requireBoundaryNode(graph, boundaryId);
        String expectedDirection = boundary.isGroupInputNode() ? "input" : "output";
        if (!expectedDirection.equals(operation.direction())) {
            throw fail("GROUP_PORT_DIRECTION_INVALID",
                    "group_in 仅接受 input，group_out 仅接受 output");
        }
        PortType type;
        try {
            type = PortType.valueOf(operation.portType().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw fail("PORT_TYPE_INVALID", "未知端口类型: " + operation.portType());
        }
        NodeData groupNode = GroupNodeFactory.getBoundaryOwner(boundary);
        if (groupNode == null) throw fail("GROUP_BOUNDARY_INVALID", "图组边界缺少所属图组");
        String category = GroupNodeFactory.categoryFor("input".equals(operation.direction()), type);
        String portId = GroupNodeFactory.addPort(groupNode, category, operation.alias(), type, operation.alias());
        portAliases.put(operation.alias(), new ResolvedPort(boundaryId, portId));
    }

    private static void applyNodeProperty(EditorContext context, NodeData node, String property,
                                          JsonElement value, Map<String, String> frameAliases) {
        switch (property) {
            case "custom_name" -> node.customName = nullableString(value, property, true);
            case "custom_color" -> node.customColor = nullableInteger(value, property);
            case "comment" -> node.comment = nullableString(value, property, false);
            case "parent_frame" -> {
                if (context.isInsideGroupScope()) {
                    throw fail("FRAME_SCOPE_UNSUPPORTED", "Group Scope 内的节点不能归属根图 Frame");
                }
                String parent = resolveFrameValue(value, frameAliases);
                if (parent != null) requireFrame(context.getGraph(), parent);
                context.getGraphController().setElementParentFrame(node.id, true, parent);
            }
            default -> throw fail("NODE_PROPERTY_UNSUPPORTED", "不支持的节点属性: " + property);
        }
    }

    private static void applyFrameProperty(EditorContext context, FrameData frame, String property,
                                           JsonElement value, Map<String, String> frameAliases) {
        NodeGraph graph = context.getGraph();
        switch (property) {
            case "title" -> frame.title = requiredString(value, property);
            case "tags" -> frame.tags = stringList(value, property);
            case "color" -> frame.color = requiredInteger(value, property);
            case "position" -> {
                JsonObject object = requiredObject(value, property);
                requireExactFields(object, "x", "y");
                moveFrameTree(context, frame,
                        finiteFloat(requiredNumber(object, "x")), finiteFloat(requiredNumber(object, "y")));
            }
            case "size" -> {
                JsonObject object = requiredObject(value, property);
                requireExactFields(object, "width", "height");
                if (hasFrameChildren(graph, frame.id)) {
                    throw fail("FRAME_SIZE_DERIVED", "包含内容的 Frame 尺寸由内容边界决定，不能直接设置");
                }
                frame.setSize(positiveFloat(requiredNumber(object, "width"), "Frame width"),
                        positiveFloat(requiredNumber(object, "height"), "Frame height"));
                if (frame.parentFrame != null) context.getGraphController().updateFrameBounds(frame.parentFrame);
            }
            case "parent_frame" -> {
                String parent = resolveFrameValue(value, frameAliases);
                if (parent != null) {
                    requireFrame(graph, parent);
                    ensureFrameParentIsAcyclic(graph, frame.id, parent);
                }
                context.getGraphController().setElementParentFrame(frame.id, false, parent);
            }
            default -> throw fail("FRAME_PROPERTY_UNSUPPORTED", "不支持的 Frame 属性: " + property);
        }
    }

    private static void moveFrameTree(EditorContext context, FrameData root, float x, float y) {
        float dx = x - root.uiPos[0];
        float dy = y - root.uiPos[1];
        NodeGraph graph = context.getGraph();
        Set<String> frameIds = new LinkedHashSet<>();
        frameIds.add(root.id);
        boolean changed;
        do {
            changed = false;
            for (FrameData frame : graph.frames.values()) {
                if (frame.parentFrame != null && frameIds.contains(frame.parentFrame)
                        && frameIds.add(frame.id)) changed = true;
            }
        } while (changed);

        for (NodeData node : graph.nodes.values()) {
            if (node.parentFrame != null && frameIds.contains(node.parentFrame)) {
                node.setPosition(node.uiPos[0] + dx, node.uiPos[1] + dy);
            }
        }
        for (String frameId : frameIds) {
            FrameData frame = graph.getFrame(frameId);
            frame.setPosition(frame.uiPos[0] + dx, frame.uiPos[1] + dy);
        }
        if (root.parentFrame != null) context.getGraphController().updateFrameBounds(root.parentFrame);
    }

    private static boolean hasFrameChildren(NodeGraph graph, String frameId) {
        return graph.nodes.values().stream().anyMatch(node -> frameId.equals(node.parentFrame))
                || graph.frames.values().stream().anyMatch(frame -> frameId.equals(frame.parentFrame));
    }

    private CommandResult commit(PlannedPatch plan, CommandInvocationContext.CancellationToken cancellation) {
        Core.checkUiThread();
        requireOpenAndBound(plan.patch);
        if (!sha256(GraphPatchCodec.toJson(plan.patch)).equals(plan.patchHash)) {
            throw fail("PATCH_HASH_MISMATCH", "事务预检后的 GraphPatch hash 不匹配");
        }
        if (!canonicalGraphJson(session.editorContext.getGraph()).equals(plan.beforeJson)) {
            throw fail("REVISION_CONFLICT", "事务执行期间蓝图内容已变化，请重新读取并规划");
        }
        if (cancellation.isCancelled()) throw fail("CANCELLED", "GraphPatch 在提交前被取消");
        String changeId = UUID.randomUUID().toString();
        boolean executed = session.editorContext.getCommandManager().executeAsNewBaseline(new CmdReplaceGraphState(
                session.editorContext, plan.beforeJson, plan.afterJson, changeId));
        if (!executed) throw fail("PATCH_NO_CHANGES", "GraphPatch 提交未产生变化");
        GeometryNode.LOGGER.info("Graph patch committed: change={}, revision={}, operations={}",
                changeId, session.revision(), plan.patch.operations().size());
        JsonObject data = new JsonObject();
        data.addProperty("patch_hash", plan.patchHash);
        data.addProperty("change_id", changeId);
        data.addProperty("revision", session.revision());
        data.addProperty("operation_count", plan.patch.operations().size());
        return new CommandResult(true, "GRAPH_PATCH_APPLIED", "GraphPatch 已原子提交", data,
                List.of(), session.revision(), changeId, CommandResult.ClientAction.NONE);
    }

    private void requireOpenAndBound(GraphPatch patch) {
        if (!targetValidator.getAsBoolean()) throw fail("TARGET_CHANGED", "事务执行期间目标 Viewport、蓝图 Tab 或 Group Scope 已变化");
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

    private static NodeData requireBoundaryNode(NodeGraph graph, String nodeId) {
        NodeData node = requireNode(graph, nodeId);
        if (!GroupNodeFactory.isBoundaryNode(node)) {
            throw fail("GROUP_BOUNDARY_REQUIRED", "操作目标必须是当前图组 Scope 的 group_in 或 group_out 节点");
        }
        return node;
    }

    private static NodeDef requireDefinition(NodeData node) {
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (definition == null) throw fail("NODE_DEFINITION_UNAVAILABLE", "节点定义不可用: " + node.id);
        return definition;
    }

    private static FrameData requireFrame(NodeGraph graph, String frameId) {
        FrameData frame = graph.getFrame(frameId);
        if (frame == null) throw fail("FRAME_NOT_FOUND", "Frame 不存在: " + frameId);
        return frame;
    }

    private static String resolve(GraphPatch.NodeRef ref, Map<String, String> aliases) {
        if (ref.id() != null) return ref.id();
        String id = aliases.get(ref.alias());
        if (id == null) throw fail("PATCH_UNKNOWN_ALIAS", "节点 alias 不存在: " + ref.alias());
        return id;
    }

    private static String resolve(GraphPatch.FrameRef ref, Map<String, String> aliases) {
        if (ref.id() != null) return ref.id();
        String id = aliases.get(ref.alias());
        if (id == null) throw fail("PATCH_UNKNOWN_FRAME_ALIAS", "Frame alias 不存在: " + ref.alias());
        return id;
    }

    private static ResolvedPort resolve(GraphPatch.PortRef ref, Map<String, String> nodeAliases,
                                        Map<String, ResolvedPort> portAliases) {
        if (ref.alias() != null) {
            ResolvedPort port = portAliases.get(ref.alias());
            if (port == null) throw fail("PATCH_UNKNOWN_PORT_ALIAS", "端口 alias 不存在: " + ref.alias());
            return port;
        }
        return new ResolvedPort(resolve(ref.node(), nodeAliases), ref.portId());
    }

    private static boolean isNewReference(GraphPatch.PortRef ref) {
        return ref.alias() != null || (ref.node() != null && ref.node().alias() != null);
    }

    private static float finiteFloat(double value) {
        float converted = (float) value;
        if (!Float.isFinite(converted)) throw fail("POSITION_OUT_OF_RANGE", "节点坐标超出 float 范围");
        return converted;
    }

    private void requireRootScope() {
        if (!scope.groupPath().isEmpty()) {
            throw fail("FRAME_SCOPE_UNSUPPORTED", "Frame 仅存在于根图，不能在 Group Scope 中编辑");
        }
    }

    private static String deterministicId(String domain, String idempotencyKey, String alias) {
        return UUID.nameUUIDFromBytes((domain + "\u0000" + idempotencyKey + "\u0000" + alias)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static int dynamicCount(NodeData node, String key, int fallback) {
        Object value = node.inputs == null ? null : node.inputs.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static Set<String> allPortIds(NodeDef definition, boolean inputSide) {
        Set<String> result = new LinkedHashSet<>();
        for (PortRow row : definition.rows()) {
            PortDef port = inputSide ? row.leftPort() : row.rightPort();
            if (port != null) result.add(port.id());
        }
        return result;
    }

    private static Set<String> allPortIds(NodeDef definition) {
        Set<String> result = allPortIds(definition, true);
        result.addAll(allPortIds(definition, false));
        return result;
    }

    private static String dynamicPortBase(String portId, int index) {
        String suffix = "_" + index;
        return portId.endsWith(suffix) ? portId.substring(0, portId.length() - suffix.length()) : portId;
    }

    private static void rebaseDynamicAliases(NodeData node, NodeDef before, ResolvedBranch removed,
                                             Map<String, ResolvedBranch> branchAliases,
                                             Map<String, ResolvedPort> portAliases) {
        NodeDef after = requireDefinition(node);
        for (Map.Entry<String, ResolvedBranch> entry : new ArrayList<>(branchAliases.entrySet())) {
            ResolvedBranch branch = entry.getValue();
            if (!branch.nodeId().equals(removed.nodeId()) || !branch.direction().equals(removed.direction())) continue;
            String alias = entry.getKey();
            if (branch.index() == removed.index()) {
                branchAliases.remove(alias);
                portAliases.keySet().removeIf(key -> key.startsWith(alias + "."));
                continue;
            }
            if (branch.index() < removed.index()) continue;
            int rebasedIndex = branch.index() - 1;
            branchAliases.put(alias, new ResolvedBranch(branch.nodeId(), branch.direction(), rebasedIndex));
            Map<String, String> rebasedPorts = dynamicPortRebase(before, after, branch.index(), rebasedIndex);
            for (Map.Entry<String, ResolvedPort> portEntry : new ArrayList<>(portAliases.entrySet())) {
                if (!portEntry.getKey().startsWith(alias + ".")) continue;
                String candidate = rebasedPorts.get(portEntry.getValue().portId());
                if (candidate == null) {
                    portAliases.remove(portEntry.getKey());
                } else {
                    portAliases.put(portEntry.getKey(), new ResolvedPort(branch.nodeId(), candidate));
                }
            }
        }
    }

    private static Map<String, String> dynamicPortRebase(NodeDef before, NodeDef after,
                                                          int oldIndex, int newIndex) {
        Map<String, String> result = new LinkedHashMap<>();
        for (boolean inputSide : new boolean[]{true, false}) {
            List<String> oldPorts = dynamicPortIds(before, inputSide, oldIndex);
            List<String> newPorts = dynamicPortIds(after, inputSide, newIndex);
            if (oldPorts.size() != newPorts.size()) {
                throw fail("DYNAMIC_BRANCH_SCHEMA_INVALID",
                        "相邻动态分支的端口结构不一致，无法安全重排");
            }
            for (int index = 0; index < oldPorts.size(); index++) {
                result.put(oldPorts.get(index), newPorts.get(index));
            }
        }
        return result;
    }

    private static List<String> dynamicPortIds(NodeDef definition, boolean inputSide, int branchIndex) {
        List<String> result = new ArrayList<>();
        Integer activeIndex = null;
        for (PortRow row : definition.rows()) {
            boolean dynamic = row.hintParams() != null
                    && Boolean.TRUE.equals(row.hintParams().get(PortMetaKeys.IS_DYNAMIC));
            if (!dynamic) {
                activeIndex = null;
                continue;
            }
            Object declaredIndex = row.hintParams().get(PortMetaKeys.DYNAMIC_INDEX);
            if (declaredIndex instanceof Number number) activeIndex = number.intValue();
            PortDef port = inputSide ? row.leftPort() : row.rightPort();
            if (port != null && Objects.equals(activeIndex, branchIndex)) result.add(port.id());
        }
        return result;
    }

    private static float positiveFloat(double value, String field) {
        float converted = finiteFloat(value);
        if (converted <= 0.0f) throw fail("FRAME_SIZE_INVALID", field + " 必须大于 0");
        return converted;
    }

    private static String nullableString(JsonElement value, String field, boolean trim) {
        if (value == null || value.isJsonNull()) return null;
        String result = requiredString(value, field);
        if (trim) result = result.trim();
        return result.isEmpty() ? null : result;
    }

    private static String requiredString(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw fail("PROPERTY_VALUE_INVALID", field + " 必须是字符串");
        }
        return value.getAsString();
    }

    private static Integer nullableInteger(JsonElement value, String field) {
        return value == null || value.isJsonNull() ? null : requiredInteger(value, field);
    }

    private static int requiredInteger(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw fail("PROPERTY_VALUE_INVALID", field + " 必须是整数");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw fail("PROPERTY_VALUE_INVALID", field + " 超出整数范围");
        }
        return (int) number;
    }

    private static List<String> stringList(JsonElement value, String field) {
        if (value == null || !value.isJsonArray()) {
            throw fail("PROPERTY_VALUE_INVALID", field + " 必须是字符串数组");
        }
        List<String> result = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) result.add(requiredString(item, field));
        return result;
    }

    private static JsonObject requiredObject(JsonElement value, String field) {
        if (value == null || !value.isJsonObject()) {
            throw fail("PROPERTY_VALUE_INVALID", field + " 必须是对象");
        }
        return value.getAsJsonObject();
    }

    private static double requiredNumber(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw fail("PROPERTY_VALUE_INVALID", field + " 必须是数字");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw fail("PROPERTY_VALUE_INVALID", field + " 必须是有限数字");
        return number;
    }

    private static void requireExactFields(JsonObject object, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : object.keySet()) {
            if (!names.contains(key)) throw fail("PROPERTY_VALUE_INVALID", "未知字段: " + key);
        }
        for (String key : names) {
            if (!object.has(key)) throw fail("PROPERTY_VALUE_INVALID", "缺少字段: " + key);
        }
    }

    private static String resolveFrameValue(JsonElement value, Map<String, String> frameAliases) {
        if (value == null || value.isJsonNull()) return null;
        JsonObject object = requiredObject(value, "parent_frame");
        requireExactFrameRefFields(object);
        String id = optionalJsonString(object, "id");
        String alias = optionalJsonString(object, "alias");
        try {
            return resolve(new GraphPatch.FrameRef(id, alias), frameAliases);
        } catch (IllegalArgumentException failure) {
            throw fail("PROPERTY_VALUE_INVALID", failure.getMessage());
        }
    }

    private static void requireExactFrameRefFields(JsonObject object) {
        for (String key : object.keySet()) {
            if (!"id".equals(key) && !"alias".equals(key)) {
                throw fail("PROPERTY_VALUE_INVALID", "parent_frame 包含未知字段: " + key);
            }
        }
    }

    private static String optionalJsonString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return null;
        return requiredString(value, field);
    }

    private static void ensureFrameParentIsAcyclic(NodeGraph graph, String frameId, String parentId) {
        Set<String> visited = new java.util.HashSet<>();
        String current = parentId;
        while (current != null) {
            if (!visited.add(current) || frameId.equals(current)) {
                throw fail("FRAME_PARENT_CYCLE", "Frame parent_frame 会形成层级环");
            }
            FrameData parent = graph.getFrame(current);
            current = parent == null ? null : parent.parentFrame;
        }
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
            throw fail("OUTPUT_ALREADY_CONNECTED", "执行流输出已连接；GraphPatch 不执行隐式替换");
        }
        if (hasInbound(graph, inNodeId, inPortId)) {
            throw fail("INPUT_ALREADY_CONNECTED", "目标输入已连接；GraphPatch 不执行隐式替换");
        }
    }

    private static <T> UiHandoff<T> onUi(Supplier<T> action) {
        UiHandoff<T> handoff = new UiHandoff<>();
        Runnable task = () -> handoff.run(action);
        if (Core.isOnUiThread()) {
            task.run();
        } else {
            try {
                if (!Core.getUiHandlerAsync().post(task)) {
                    handoff.failPending(new IllegalStateException("ModernUI queue is unavailable"));
                }
            } catch (RuntimeException failure) {
                handoff.failPending(failure);
            }
        }
        return handoff;
    }

    private static <T> T awaitUi(UiHandoff<T> handoff,
                                 CommandInvocationContext.CancellationToken cancellation)
            throws InterruptedException, ExecutionException {
        long deadline = System.nanoTime() + UI_HANDOFF_TIMEOUT.toNanos();
        while (true) {
            if (cancellation.isCancelled() && handoff.cancelPending()) {
                throw fail("CANCELLED", "GraphPatch 等待编辑器 UI 时已取消");
            }
            try {
                return handoff.future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
            }
            if (System.nanoTime() >= deadline && handoff.cancelPending()) {
                throw fail("UI_UNAVAILABLE", "编辑器 UI 响应超时");
            }
        }
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static PatchFailure fail(String code, String message) { return new PatchFailure(code, message); }
    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank() ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private record PlannedPatch(GraphPatch patch, String patchHash, String beforeJson, String afterJson) {}
    private record PlanSnapshot(String beforeJson, RegistryAccess registryAccess) {}
    private record ResolvedPort(String nodeId, String portId) {}
    private record ResolvedBranch(String nodeId, String direction, int index) {}
    private enum UiHandoffState { PENDING, RUNNING, COMPLETED, CANCELLED }
    private static final class UiHandoff<T> {
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private final AtomicReference<UiHandoffState> state = new AtomicReference<>(UiHandoffState.PENDING);

        private void run(Supplier<T> action) {
            if (!state.compareAndSet(UiHandoffState.PENDING, UiHandoffState.RUNNING)) return;
            try {
                future.complete(action.get());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            } finally {
                state.set(UiHandoffState.COMPLETED);
            }
        }

        private boolean cancelPending() {
            if (!state.compareAndSet(UiHandoffState.PENDING, UiHandoffState.CANCELLED)) return false;
            future.cancel(false);
            return true;
        }

        private void failPending(Throwable failure) {
            if (!state.compareAndSet(UiHandoffState.PENDING, UiHandoffState.COMPLETED)) return;
            future.completeExceptionally(failure);
        }
    }
    private static final class PatchFailure extends RuntimeException {
        private final String code;
        private PatchFailure(String code, String message) { super(message); this.code = code; }
    }
}
