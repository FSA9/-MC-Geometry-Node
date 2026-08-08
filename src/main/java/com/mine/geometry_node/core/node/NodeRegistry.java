package com.mine.geometry_node.core.node;

import com.mine.geometry_node.api.EventRegistrationContext;
import com.mine.geometry_node.api.GeometryNodePlugin;
import com.mine.geometry_node.api.GeometryNodeEvents;
import com.mine.geometry_node.api.NodeRegistrationContext;
import com.mine.geometry_node.api.EventDef;
import com.mine.geometry_node.api.EventScope;
import com.mine.geometry_node.api.GeometryEventDispatcher;
import com.mine.geometry_node.api.MarkerRegistrationContext;
import com.mine.geometry_node.core.engine.system.marker.MarkerType;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.group.GroupNodeDefinitions;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NodeRegistry {
    public static final NodeRegistry INSTANCE = new NodeRegistry();

    // 后端核心存储
    private final Map<String, BaseNode> registry = new HashMap<>();
    private final Map<String, NodeDef> defaultDefCache = new LinkedHashMap<>();
    private final Map<String, EventDef> eventRegistry = new LinkedHashMap<>();
    private final Map<String, String> eventOwners = new HashMap<>();
    private final Set<String> registeredEventIds = new HashSet<>();
    private boolean initialized = false;
    private String activeAddonId = "legacy";

    // 前端根目录
    public final NodeCategory ROOT = new NodeCategory("geometry_node.menu.root");

    private NodeRegistry() {}

    public synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        System.out.println("[NodeRegistry] Start node registry");

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
                System.err.println("[NodeRegistry] Skip invalid GeometryNodePlugin provider: " + error.getMessage());
                error.printStackTrace();
                continue;
            }

            String addonId = sanitizeAddonId(plugin.addonId(), plugin);
            System.out.println("[NodeRegistry] Find plugin: " + addonId + " (" + plugin.getClass().getName() + ")");

            try {
                plugin.registerMarkerTypes(new PluginMarkerRegistrationContext(addonId));

                PluginNodeRegistrationContext nodeContext = new PluginNodeRegistrationContext(addonId);
                plugin.registerNodes(nodeContext);
                if (nodeContext.registeredCount() == 0) {
                    String previousAddonId = activeAddonId;
                    activeAddonId = addonId;
                    try {
                        plugin.registerNodes(this);
                    } finally {
                        activeAddonId = previousAddonId;
                    }
                }

                PluginEventRegistrationContext eventContext = new PluginEventRegistrationContext(addonId);
                plugin.registerEvents(eventContext);
            } catch (Exception e) {
                System.err.println("[NodeRegistry] Plugin registration failed: " + addonId + " (" + plugin.getClass().getName() + ")");
                e.printStackTrace();
            }
        }

        System.out.println("[NodeRegistry] Load finished. Total nodes: " + registry.size() + ", total events: " + eventRegistry.size());
    }

    public void register(String path, BaseNode node) {
        register(path, node, activeAddonId);
    }

    public void register(String path, BaseNode node, String addonId) {
        NodeCategory category = getOrCreateCategory(path);
        register(category, node, addonId);
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
        register(category, node, activeAddonId);
    }

    public void register(NodeCategory category, BaseNode node, String addonId) {
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

        if (!isBuiltinAddon(addonId) && !isNamespaced(typeId)) {
            System.err.println("[NodeRegistry] Warning: Addon node type should be namespaced: " + typeId + " from " + addonId);
        }

        // 4. 重复检查
        BaseNode existing = registry.get(typeId);
        if (existing != null) {
            System.err.println("[NodeRegistry] Skip: Duplicate node type registered: " + typeId +
                    " existing=" + existing.getClass().getName() +
                    " new=" + node.getClass().getName() +
                    " addon=" + addonId);
            return;
        }

        registry.put(typeId, node);
        defaultDefCache.put(typeId, def);

        category.addNode(node);

        if (def.category() == NodeType.EVENT) {
            registerEvent(createEventDef(def), addonId);
        }
    }

    public void registerEvent(EventDef eventDef, String addonId) {
        if (eventDef == null) {
            System.err.println("[NodeRegistry] Skip: Cannot register null event definition");
            return;
        }

        String eventId = eventDef.eventId();
        if (!isBuiltinAddon(addonId) && !isNamespaced(eventId)) {
            System.err.println("[NodeRegistry] Warning: Addon event id should be namespaced: " + eventId + " from " + addonId);
        }

        String existingOwner = eventOwners.get(eventId);
        if (existingOwner != null && !existingOwner.equals(addonId)) {
            System.err.println("[NodeRegistry] Skip: Duplicate event registered: " + eventId +
                    " existingAddon=" + existingOwner +
                    " newAddon=" + addonId);
            return;
        }

        eventRegistry.put(eventId, eventDef);
        eventOwners.put(eventId, addonId);
        registeredEventIds.add(eventId);
    }

    @Nullable
    public NodeDef resolveDefinition(NodeData data) {
        if (data == null || data.type == null) return null;
        NodeDef groupDef = GroupNodeDefinitions.resolve(data);
        if (groupDef != null) return groupDef;

        BaseNode b = registry.get(data.type);
        if (b != null) {
            return b.getDefinition(data);
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

    public boolean hasEvent(String eventId) {
        return registeredEventIds.contains(eventId);
    }

    @Nullable
    public EventDef getEventDefinition(String eventId) {
        return eventRegistry.get(eventId);
    }

    public Collection<EventDef> getAllEventDefinitions() {
        return Collections.unmodifiableCollection(eventRegistry.values());
    }

    private static EventDef createEventDef(NodeDef def) {
        List<PortDef> outputs = new ArrayList<>();
        for (PortRow row : def.rows()) {
            PortDef rightPort = row.rightPort();
            if (rightPort != null && !rightPort.type().isFlow()) {
                outputs.add(rightPort);
            }
        }
        return new EventDef(def.typeId(), def.displayName(), EventScope.LEVEL, outputs);
    }

    private static String sanitizeAddonId(String addonId, GeometryNodePlugin plugin) {
        if (addonId == null || addonId.isBlank()) {
            return plugin.getClass().getName();
        }
        return addonId.trim();
    }

    private static boolean isNamespaced(String id) {
        int split = id.indexOf(':');
        return split > 0 && split < id.length() - 1;
    }

    private static boolean isBuiltinAddon(String addonId) {
        return "geometry_node".equals(addonId) || "legacy".equals(addonId);
    }

    private final class PluginNodeRegistrationContext implements NodeRegistrationContext {
        private final String addonId;
        private int registeredCount = 0;

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
            registeredCount++;
        }

        private int registeredCount() {
            return registeredCount;
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

    private final class PluginEventRegistrationContext implements EventRegistrationContext {
        private final String addonId;

        private PluginEventRegistrationContext(String addonId) {
            this.addonId = addonId;
        }

        @Override
        public String addonId() {
            return addonId;
        }

        @Override
        public GeometryEventDispatcher dispatcher() {
            return GeometryNodeEvents.dispatcher();
        }

        @Override
        public void registerEvent(EventDef eventDef) {
            NodeRegistry.this.registerEvent(eventDef, addonId);
        }
    }
}
