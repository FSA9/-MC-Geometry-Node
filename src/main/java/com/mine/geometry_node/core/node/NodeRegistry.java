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

    // 唯一保留的前端根目录
    public final NodeCategory ROOT = new NodeCategory("geometry_node.menu.root");

    private NodeRegistry() {}

    public void init() {
        System.out.println("[GeometryNode] 开始检索并加载节点插件...");

        ServiceLoader<GeometryNodePlugin> loader = ServiceLoader.load(GeometryNodePlugin.class);

        for (GeometryNodePlugin plugin : loader) {
            System.out.println("[GeometryNode] 发现节点插件: " + plugin.getClass().getSimpleName());
            try {
                plugin.registerNodes(this);
            } catch (Exception e) {
                System.err.println("[GeometryNode] 插件加载失败: " + plugin.getClass().getName());
                e.printStackTrace();
            }
        }

        System.out.println("[GeometryNode] 加载完毕，当前共注册 " + registry.size() + " 个节点。");
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