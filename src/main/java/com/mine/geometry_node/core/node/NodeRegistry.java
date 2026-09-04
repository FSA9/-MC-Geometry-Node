package com.mine.geometry_node.core.node;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.api.GeometryNodePlugin;
import com.mine.geometry_node.api.NodeRegistrationContext;
import com.mine.geometry_node.api.MarkerRegistrationContext;
import com.mine.geometry_node.core.engine.system.marker.MarkerType;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckProvider;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckRegistry;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.MissingNodeDefinitions;
import com.mine.geometry_node.core.node.group.GroupNodeDefinitions;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutorRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NodeRegistry {
    public static final NodeRegistry INSTANCE = new NodeRegistry();

    // 后端核心存储
    private final Map<String, BaseNode> registry = new HashMap<>();
    private final Map<String, NodeDef> defaultDefCache = new LinkedHashMap<>();
    private boolean initialized = false;
    private boolean initializing = false;

    // 前端根目录
    public final NodeCategory ROOT = new NodeCategory("geometry_node.menu.root");

    private NodeRegistry() {}

    public synchronized void init() {
        if (initialized || initializing) {
            return;
        }
        initializing = true;

        GeometryNode.LOGGER.info("Starting node registry");

        boolean completed = false;
        try {
            ServiceLoader<GeometryNodePlugin> loader = ServiceLoader.load(GeometryNodePlugin.class);
            Iterator<GeometryNodePlugin> iterator = loader.iterator();

            while (true) {
                GeometryNodePlugin plugin;
                try {
                    if (!iterator.hasNext()) {
                        break;
                    }
                    plugin = iterator.next();
                } catch (ServiceConfigurationError error) {
                    GeometryNode.LOGGER.error("Skipping invalid GeometryNodePlugin provider", error);
                    continue;
                }

                String addonId = sanitizeAddonId(plugin.addonId(), plugin);
                GeometryNode.LOGGER.info("Discovered node plugin: addon={}, provider={}",
                        addonId, plugin.getClass().getName());

                try {
                    plugin.registerMarkerTypes(new PluginMarkerRegistrationContext(addonId));

                    plugin.registerNodes(new PluginNodeRegistrationContext(addonId));
                } catch (Exception e) {
                    GeometryNode.LOGGER.error("Node plugin registration failed: addon={}, provider={}",
                            addonId, plugin.getClass().getName(), e);
                }
            }

            completed = true;
            GeometryNode.LOGGER.info("Node registry loaded: nodes={}", registry.size());
        } finally {
            initialized = completed;
            initializing = false;
        }
    }

    private void register(String path, BaseNode node, String addonId) {
        try {
            NodeCategory category = getOrCreateCategory(path);
            registerNode(category, node, addonId);
        } catch (Exception | LinkageError error) {
            logNodeRegistrationFailure(path, node, addonId, error);
        }
    }

    private NodeCategory getOrCreateCategory(String path) {
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

    private synchronized void registerNode(NodeCategory category, BaseNode node, String addonId) {
        // 1. 基础校验
        if (node == null || category == null) {
            GeometryNode.LOGGER.error("Skipping node registration with a null node or category");
            return; // 跳过，不中断后续
        }

        // 2. 获取定义并校验（防止 NPE）
        NodeDef def = node.getDefaultDefinition();
        if (def == null) {
            GeometryNode.LOGGER.error("Skipping node with a null definition: node={}",
                    node.getClass().getName());
            return; // 跳过这个非法节点
        }

        // 3. 获取 ID 并校验
        String typeId = def.typeId();
        if (typeId == null || typeId.isEmpty()) {
            GeometryNode.LOGGER.error("Skipping node with an empty type ID: node={}",
                    node.getClass().getName());
            return;
        }

        if (!isBuiltinAddon(addonId) && !typeId.startsWith(addonId + ":")) {
            throw new IllegalArgumentException("Addon node type must use namespace '"
                    + addonId + "': " + typeId);
        }

        // 4. 重复检查
        BaseNode existing = registry.get(typeId);
        if (existing != null) {
            GeometryNode.LOGGER.error("Skipping duplicate node type: type={}, existing={}, new={}, addon={}",
                    typeId, existing.getClass().getName(), node.getClass().getName(), addonId);
            return;
        }

        BehaviorNodeExecutor behaviorExecutor = node instanceof BehaviorExecutableNode executable
                ? Objects.requireNonNull(executable.behaviorExecutor(),
                "Behavior executor cannot be null: " + typeId) : null;
        if (behaviorExecutor != null) {
            BehaviorNodeExecutorRegistry.INSTANCE.register(typeId, behaviorExecutor);
        }
        if (node instanceof EventPrecheckProvider provider) {
            EventPrecheckRegistry.register(typeId,
                    Objects.requireNonNull(provider.eventPrecheckFactory(),
                            "Event precheck factory cannot be null: " + typeId));
        }

        registry.put(typeId, node);
        defaultDefCache.put(typeId, def);

        category.addNode(node);
    }

    private static void logNodeRegistrationFailure(String path, @Nullable BaseNode node,
                                                   String addonId, Throwable error) {
        String nodeClass = node != null ? node.getClass().getName() : "<null-node>";
        GeometryNode.LOGGER.error("Skipping node after registration failure: addon={}, path={}, node={}",
                addonId, path, nodeClass, error);
    }

    @Nullable
    public NodeDef resolveDefinition(NodeData data) {
        if (data == null || data.type == null) return null;
        NodeDef groupDef = GroupNodeDefinitions.resolve(data);
        if (groupDef != null) return groupDef;

        BaseNode b = registry.get(data.type);
        if (b != null) {
            return b.hasDynamicDefinition() ? b.getDefinition(data) : defaultDefCache.get(data.type);
        }
        NodeDef defaultDef = getDefaultDefinition(data.type);
        return defaultDef != null ? defaultDef : MissingNodeDefinitions.resolve(data);
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

    private static String sanitizeAddonId(String addonId, GeometryNodePlugin plugin) {
        if (addonId == null || addonId.isBlank()) {
            return plugin.getClass().getName();
        }
        return addonId.trim();
    }

    private static boolean isBuiltinAddon(String addonId) {
        return "geometry_node".equals(addonId);
    }

    private final class PluginNodeRegistrationContext implements NodeRegistrationContext {
        private final String addonId;
        private PluginNodeRegistrationContext(String addonId) {
            this.addonId = addonId;
        }

        @Override
        public String addonId() {
            return addonId;
        }

        @Override
        public void registerNode(String menuPath, BaseNode node) {
            NodeRegistry.this.register(menuPath, node, addonId);
        }
    }

    private static final class PluginMarkerRegistrationContext implements MarkerRegistrationContext {
        private final String addonId;

        private PluginMarkerRegistrationContext(String addonId) {
            this.addonId = addonId;
        }

        @Override
        public String addonId() {
            return addonId;
        }

        @Override
        public void registerMarkerType(MarkerType type) {
            MarkerTypeRegistry.INSTANCE.register(type);
        }
    }
}
