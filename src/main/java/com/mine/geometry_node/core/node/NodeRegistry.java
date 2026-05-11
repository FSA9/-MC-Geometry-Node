package com.mine.geometry_node.core.node;

import com.mine.geometry_node.api.GeometryNodePlugin;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NodeRegistry {
    public static final NodeRegistry INSTANCE = new NodeRegistry();

    // 后端核心存储
    private final Map<String, BaseNode> registry = new HashMap<>();
    private final Map<String, NodeDef> defaultDefCache = new LinkedHashMap<>();

    // 前端根目录
    public final NodeCategory ROOT = new NodeCategory("geometry_node.menu.root");

    private NodeRegistry() {}

    public void init() {
        System.out.println("[NodeRegistry] Start node registry");

        ServiceLoader<GeometryNodePlugin> loader = ServiceLoader.load(GeometryNodePlugin.class);

        for (GeometryNodePlugin plugin : loader) {
            System.out.println("[NodeRegistry] Find node: " + plugin.getClass().getSimpleName());
            plugin.registerNodes(this);
        }

        System.out.println("[NodeRegistry] Load finished. Total nodes: " + registry.size());
    }

    public void register(String path, BaseNode node) {
        NodeCategory category = getOrCreateCategory(path);
        register(category, node);
    }

    public NodeCategory getOrCreateCategory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return ROOT;
        }

        String[] parts = path.split("/");
        NodeCategory current = ROOT;

        for (String part : parts) {
            String cleanPart = part.trim();
            if (cleanPart.isEmpty()) continue;

            String currentTranslationKey = "geometry_node.menu." + cleanPart;

            NodeCategory child = current.getChild(currentTranslationKey);
            if (child == null) {
                child = new NodeCategory(currentTranslationKey);
                current.addChild(child);
            }
            current = child;
        }

        return current;
    }

    public void register(NodeCategory category, BaseNode node) {
        // 1. 基础校验
        if (node == null || category == null) {
            System.err.println("[NodeRegistry] Skip: Cannot register null node or null category");
            return; // 跳过，不中断后续
        }

        // 2. 获取定义并校验（防止 NPE）
        NodeDef def = node.getDefaultDefinition();
        if (def == null) {
            System.err.println("[NodeRegistry] Skip: Node " + node.getClass().getSimpleName() + " returned a null definition!");
            return; // 跳过这个非法节点
        }

        // 3. 获取 ID 并校验
        String typeId = def.typeId();
        if (typeId == null || typeId.isEmpty()) {
            System.err.println("[NodeRegistry] Skip: Node " + node.getClass().getSimpleName() + " has a null or empty typeId!");
            return;
        }

        // 4. 重复检查
        if (registry.containsKey(typeId)) {
            System.err.println("[NodeRegistry] Skip: Duplicate node type registered: " + typeId);
            return;
        }

        registry.put(typeId, node);
        defaultDefCache.put(typeId, def);

        category.addNode(node);
    }

    @Nullable
    public NodeDef resolveDefinition(NodeData data) {
        if (data == null || data.type == null) return null;
        BaseNode b = registry.get(data.type);
        if (b != null) {
            return b.getDefinition(data);
        }
        return getDefaultDefinition(data.type);
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