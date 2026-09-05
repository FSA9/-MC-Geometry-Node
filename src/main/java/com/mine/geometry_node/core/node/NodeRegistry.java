package com.mine.geometry_node.core.node;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.api.GeometryNodePlugin;
import com.mine.geometry_node.api.NodeRegistrationContext;
import com.mine.geometry_node.api.MarkerRegistrationContext;
import com.mine.geometry_node.core.engine.system.marker.MarkerType;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckProvider;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckFactory;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.MissingNodeDefinitions;
import com.mine.geometry_node.core.node.group.GroupNodeDefinitions;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NodeRegistry {
    public static final NodeRegistry INSTANCE = new NodeRegistry();

    // A node and its optional runtime capabilities are committed as one registration.
    private final Map<String, RegisteredNode> registeredNodes = new LinkedHashMap<>();
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

                String addonId;
                try {
                    addonId = sanitizeAddonId(plugin.addonId(), plugin);
                } catch (Exception | LinkageError error) {
                    GeometryNode.LOGGER.error("Skipping node plugin with invalid metadata: provider={}",
                            plugin.getClass().getName(), error);
                    continue;
                }
                GeometryNode.LOGGER.info("Discovered node plugin: addon={}, provider={}",
                        addonId, plugin.getClass().getName());

                registerPluginMarkers(plugin, addonId);
                registerPluginNodes(plugin, addonId);
            }

            completed = true;
            GeometryNode.LOGGER.info("Node registry loaded: nodes={}", registeredNodes.size());
        } finally {
            initialized = completed;
            initializing = false;
        }
    }

    private void registerPluginMarkers(GeometryNodePlugin plugin, String addonId) {
        try {
            plugin.registerMarkerTypes(new PluginMarkerRegistrationContext(addonId));
        } catch (Exception | LinkageError error) {
            GeometryNode.LOGGER.error("Marker registration failed: addon={}, provider={}",
                    addonId, plugin.getClass().getName(), error);
        }
    }

    private void registerPluginNodes(GeometryNodePlugin plugin, String addonId) {
        try {
            plugin.registerNodes(new PluginNodeRegistrationContext(addonId));
        } catch (Exception | LinkageError error) {
            GeometryNode.LOGGER.error("Node plugin registration failed: addon={}, provider={}",
                    addonId, plugin.getClass().getName(), error);
        }
    }

    private void register(String path, BaseNode node, String addonId) {
        try {
            registerNode(path, node, addonId);
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

    private synchronized void registerNode(String path, BaseNode node, String addonId) {
        // 1. 基础校验
        if (node == null) {
            GeometryNode.LOGGER.error("Skipping node registration with a null node");
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
        RegisteredNode existing = registeredNodes.get(typeId);
        if (existing != null) {
            GeometryNode.LOGGER.error("Skipping duplicate node type: type={}, existing={}, new={}, addon={}",
                    typeId, existing.node().getClass().getName(), node.getClass().getName(), addonId);
            return;
        }

        BehaviorNodeExecutor behaviorExecutor = node instanceof BehaviorExecutableNode executable
                ? Objects.requireNonNull(executable.behaviorExecutor(),
                "Behavior executor cannot be null: " + typeId) : null;
        EventPrecheckFactory eventPrecheckFactory = node instanceof EventPrecheckProvider provider
                ? Objects.requireNonNull(provider.eventPrecheckFactory(),
                "Event precheck factory cannot be null: " + typeId) : null;

        NodeCategory category = getOrCreateCategory(path);
        registeredNodes.put(typeId,
                new RegisteredNode(node, def, behaviorExecutor, eventPrecheckFactory));
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

        RegisteredNode registration = registeredNodes.get(data.type);
        if (registration != null) {
            BaseNode node = registration.node();
            return node.hasDynamicDefinition() ? node.getDefinition(data) : registration.defaultDefinition();
        }
        NodeDef defaultDef = getDefaultDefinition(data.type);
        return defaultDef != null ? defaultDef : MissingNodeDefinitions.resolve(data);
    }

    @Nullable
    public BaseNode get(String typeId) {
        RegisteredNode registration = registeredNodes.get(typeId);
        return registration != null ? registration.node() : null;
    }

    @Nullable
    public NodeDef getDefaultDefinition(String typeId) {
        RegisteredNode registration = registeredNodes.get(typeId);
        return registration != null ? registration.defaultDefinition() : null;
    }

    @Nullable
    public BehaviorNodeExecutor getBehaviorExecutor(String typeId) {
        RegisteredNode registration = registeredNodes.get(typeId);
        return registration != null ? registration.behaviorExecutor() : null;
    }

    @Nullable
    public EventPrecheckFactory getEventPrecheckFactory(String typeId) {
        RegisteredNode registration = registeredNodes.get(typeId);
        return registration != null ? registration.eventPrecheckFactory() : null;
    }

    public boolean has(String typeId) {
        return registeredNodes.containsKey(typeId);
    }

    public Set<String> getAllTypeIds() {
        return Collections.unmodifiableSet(registeredNodes.keySet());
    }

    public Collection<NodeDef> getAllDefinitions() {
        return registeredNodes.values().stream()
                .map(RegisteredNode::defaultDefinition)
                .toList();
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
            try {
                MarkerTypeRegistry.INSTANCE.register(type);
            } catch (Exception | LinkageError error) {
                String typeId = type != null ? type.id() : "<null-marker>";
                GeometryNode.LOGGER.error("Skipping marker type after registration failure: addon={}, type={}",
                        addonId, typeId, error);
            }
        }
    }

    private record RegisteredNode(BaseNode node, NodeDef defaultDefinition,
                                  @Nullable BehaviorNodeExecutor behaviorExecutor,
                                  @Nullable EventPrecheckFactory eventPrecheckFactory) {
    }
}
