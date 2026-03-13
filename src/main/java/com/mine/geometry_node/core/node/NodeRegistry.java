package com.mine.geometry_node.core.node;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.actions.entity.AddForce;
import com.mine.geometry_node.core.node.nodes.actions.entity.SendMessage;
import com.mine.geometry_node.core.node.nodes.actions.visual.DrawDebugLine;
import com.mine.geometry_node.core.node.nodes.data.GetEntityAttribute;
import com.mine.geometry_node.core.node.nodes.data.SetEntityAttribute;
import com.mine.geometry_node.core.node.nodes.events.OnBlockBreak;
import com.mine.geometry_node.core.node.nodes.events.OnBlockPlace;
import com.mine.geometry_node.core.node.nodes.events.entity.OnEntityDeath;
import com.mine.geometry_node.core.node.nodes.logics.ForEach;
import com.mine.geometry_node.core.node.nodes.logics.Switch;
import com.mine.geometry_node.core.node.nodes.functions.graph.ReceiveBlueprint;
import com.mine.geometry_node.core.node.nodes.functions.graph.TriggerBlueprint;
import com.mine.geometry_node.core.node.nodes.functions.time.Function_Delay_s;
import com.mine.geometry_node.core.node.nodes.maths.operation.Math_Operation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NodeRegistry {
    public static final NodeRegistry INSTANCE = new NodeRegistry();
    // 后端核心存储
    private final Map<String, BaseNode> registry = new HashMap<>();
    private final Map<String, NodeDef> defaultDefCache = new LinkedHashMap<>();

    // 前端目录树
    public final NodeCategory ROOT = new NodeCategory("geometry_node.menu.root");

    // 一级目录
    public final NodeCategory ACTIONS = new NodeCategory("geometry_node.menu.actions");
    public final NodeCategory EVENTS = new NodeCategory("geometry_node.menu.events");
    public final NodeCategory FLOWS = new NodeCategory("geometry_node.menu.flow");
    public final NodeCategory FUNCTIONS = new NodeCategory("geometry_node.menu.functions");
    public final NodeCategory LOGICS = new NodeCategory("geometry_node.menu.logics");
    public final NodeCategory MATHS = new NodeCategory("geometry_node.menu.maths");
    public final NodeCategory ATTRIBUTES = new NodeCategory("geometry_node.menu.attributes");

    // 二级目录
    public final NodeCategory MATHS_OPERATION = new NodeCategory("geometry_node.menu.operation");
    public final NodeCategory COMM = new NodeCategory("geometry_node.menu.graph");
    public final NodeCategory TEXT = new NodeCategory("geometry_node.menu.text");
    public final NodeCategory TIME = new NodeCategory("geometry_node.menu.time");
    public final NodeCategory VECTOR = new NodeCategory("geometry_node.menu.vector");

    private NodeRegistry() {
        ROOT.addChild(EVENTS)
            .addChild(ACTIONS)
            .addChild(ATTRIBUTES)
            .addChild(FLOWS)
            .addChild(MATHS)
            .addChild(LOGICS)
            .addChild(FUNCTIONS);

        MATHS.addChild(MATHS_OPERATION);

        FUNCTIONS.addChild(COMM)
                .addChild(TEXT)
                .addChild(TIME)
                .addChild(VECTOR);
    }

    public void init() {
        System.out.println("[GeometryNode] Registering built-in nodes...");

        // Events
        register(EVENTS, new OnBlockBreak());
        register(EVENTS, new OnBlockPlace());
        register(EVENTS, new OnEntityDeath());

        // Actions
        register(ACTIONS, new SendMessage());
        register(ACTIONS, new AddForce());
        register(ACTIONS, new DrawDebugLine());

        // Attribute
        register(ATTRIBUTES, new SetEntityAttribute());
        register(ATTRIBUTES, new GetEntityAttribute());

        // Flows
        register(FLOWS, new Switch());

        // Logics
        register(LOGICS, new ForEach());

        // Maths
        register(MATHS_OPERATION, new Math_Operation());

        // Functions
        register(FUNCTIONS, new Function_Delay_s());
        register(COMM, new ReceiveBlueprint());
        register(COMM, new TriggerBlueprint());

        System.out.println("[GeometryNode] Successfully registered " + registry.size() + " nodes.");
    }

    public void register(NodeCategory category, BaseNode node) {
        if (node == null || category == null) {
            throw new IllegalArgumentException("Cannot register null node or null category");
        }

        NodeDef def = node.getDefaultDefinition();
        String typeId = def.typeId();

        if (registry.containsKey(typeId)) {
            throw new IllegalStateException("Duplicate node type registered: " + typeId);
        }

        registry.put(typeId, node);
        defaultDefCache.put(typeId, def);

        category.addNode(node);
    }

    @Nullable
    public BaseNode get(String typeId) {
        return registry.get(typeId);
    }

    @Nullable
    public NodeDef getDefaultDefinition(String typeId) {
        return defaultDefCache.get(typeId);
    }

    public boolean has(String typeId) {
        return registry.containsKey(typeId);
    }

    public Set<String> getAllTypeIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    public Collection<NodeDef> getAllDefinitions() {
        return Collections.unmodifiableCollection(defaultDefCache.values());
    }
}