package com.mine.geometry_node.client.ui.UICommand.history;

import com.google.gson.JsonElement;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Detached, in-memory document history. No JSON loading, default normalization or numeric conversion
 * takes place. Unchanged node/frame states are shared with the adjacent history snapshot; mutable
 * document objects are always newly allocated when restoring a snapshot.
 */
public final class GraphHistorySnapshot {
    private final String graphKind;
    private final List<String> tags;
    private final String comment;
    private final Value quest;
    private final String version;
    private final Map<String, NodeState> nodes;
    private final Map<String, FrameState> frames;

    private GraphHistorySnapshot(NodeGraph graph, GraphHistorySnapshot previous) {
        graphKind = graph.graphKind;
        tags = freezeList(graph.tags);
        comment = graph.comment;
        quest = new Value(graph.quest);
        version = graph.version;
        nodes = captureMap(graph.nodes, previous == null ? null : previous.nodes, NodeState::capture);
        frames = captureMap(graph.frames, previous == null ? null : previous.frames,
                (frame, ignored) -> FrameState.capture(frame));
    }

    public static GraphHistorySnapshot capture(NodeGraph graph, GraphHistorySnapshot previous) {
        GraphHistorySnapshot snapshot = new GraphHistorySnapshot(Objects.requireNonNull(graph), previous);
        return snapshot.sameState(previous) ? previous : snapshot;
    }

    /** Returns independently owned mutable data, including nested input values and group children. */
    public NodeGraph restore() {
        NodeGraph graph = new NodeGraph();
        graph.graphKind = graphKind;
        graph.tags = mutableList(tags);
        graph.comment = comment;
        graph.quest = (QuestDefinition) quest.restore();
        graph.version = version;
        graph.nodes = restoreNodes(nodes, null);
        graph.frames = frames == null ? null : new LinkedHashMap<>();
        if (frames != null) frames.forEach((id, frame) -> graph.frames.put(id, frame == null ? null : frame.restore()));
        return graph;
    }

    public boolean sameState(GraphHistorySnapshot other) {
        return other != null && Objects.equals(graphKind, other.graphKind)
                && Objects.equals(tags, other.tags) && Objects.equals(comment, other.comment)
                && Objects.equals(quest, other.quest) && Objects.equals(version, other.version)
                && Objects.equals(nodes, other.nodes) && Objects.equals(frames, other.frames);
    }

    private record NodeState(String id, String type, Value position, String customName, Integer customColor,
                             String comment, Map<String, Value> inputs, Map<String, Connection> execOutputs,
                             Map<String, List<Connection>> outputs, PortsState ports, String parentFrame,
                             Set<String> connectedInputs, Map<String, NodeState> children) {
        static NodeState capture(NodeData node, NodeState previous) {
            Map<String, Value> inputs = captureMap(node.inputs, previous == null ? null : previous.inputs,
                    (value, ignored) -> new Value(value));
            Map<String, List<Connection>> outputs = captureMap(node.outputs, null,
                    (links, ignored) -> freezeList(links));
            return new NodeState(node.id, node.type, new Value(node.uiPos), node.customName, node.customColor,
                    node.comment, inputs, freezeMap(node.execOutputs), outputs, PortsState.capture(node.portConfig),
                    node.parentFrame, node.connectedInputs == null ? null
                    : Collections.unmodifiableSet(new LinkedHashSet<>(node.connectedInputs)),
                    captureMap(node.subNodes, previous == null ? null : previous.children, NodeState::capture));
        }

        NodeData restore(NodeData parent) {
            NodeData node = new NodeData();
            node.id = id;
            node.type = type;
            node.uiPos = (float[]) position.restore();
            node.customName = customName;
            node.customColor = customColor;
            node.comment = comment;
            node.inputs = inputs == null ? null : new LinkedHashMap<>();
            if (inputs != null) inputs.forEach((key, value) -> node.inputs.put(key, value == null ? null : value.restore()));
            node.execOutputs = execOutputs == null ? null : new LinkedHashMap<>(execOutputs);
            node.outputs = outputs == null ? null : new LinkedHashMap<>();
            if (outputs != null) outputs.forEach((key, links) -> node.outputs.put(key, mutableList(links)));
            node.portConfig = ports == null ? null : ports.restore();
            node.parentFrame = parentFrame;
            node.connectedInputs = connectedInputs == null ? null : new LinkedHashSet<>(connectedInputs);
            node.parentGroupNode = parent;
            node.subNodes = restoreNodes(children, node);
            return node;
        }
    }

    private record PortState(String name, Boolean hidden, PortType type, Integer order) {
        static PortState capture(NodeData.PortConfig config, PortState ignored) {
            return new PortState(config.customName, config.hidden, config.type, config.order);
        }
        NodeData.PortConfig restore() {
            NodeData.PortConfig config = new NodeData.PortConfig();
            config.customName = name;
            config.hidden = hidden;
            config.type = type;
            config.order = order;
            return config;
        }
    }

    private record PortsState(Map<String, PortState> inputs, Map<String, PortState> execInputs,
                              Map<String, PortState> execOutputs, Map<String, PortState> outputs) {
        static PortsState capture(NodeData.PortsConfig ports) {
            return ports == null ? null : new PortsState(captureMap(ports.inputs, null, PortState::capture),
                    captureMap(ports.execInputs, null, PortState::capture),
                    captureMap(ports.execOutputs, null, PortState::capture),
                    captureMap(ports.outputs, null, PortState::capture));
        }
        // Legacy ports without an explicit order use map insertion order in GroupNodeDefinitions.
        @Override public boolean equals(Object other) {
            return other instanceof PortsState ports && sameOrderedMap(inputs, ports.inputs)
                    && sameOrderedMap(execInputs, ports.execInputs)
                    && sameOrderedMap(execOutputs, ports.execOutputs)
                    && sameOrderedMap(outputs, ports.outputs);
        }
        @Override public int hashCode() { return Objects.hash(inputs, execInputs, execOutputs, outputs); }

        NodeData.PortsConfig restore() {
            NodeData.PortsConfig ports = new NodeData.PortsConfig();
            ports.inputs = restorePorts(inputs);
            ports.execInputs = restorePorts(execInputs);
            ports.execOutputs = restorePorts(execOutputs);
            ports.outputs = restorePorts(outputs);
            return ports;
        }
    }

    private record FrameState(String id, String title, List<String> tags, Value position, Value size,
                              int color, String parentFrame) {
        static FrameState capture(FrameData frame) {
            return new FrameState(frame.id, frame.title, freezeList(frame.tags), new Value(frame.uiPos),
                    new Value(frame.uiSize), frame.color, frame.parentFrame);
        }
        FrameData restore() {
            FrameData frame = new FrameData();
            frame.id = id;
            frame.title = title;
            frame.tags = mutableList(tags);
            frame.uiPos = (float[]) position.restore();
            frame.uiSize = (float[]) size.restore();
            frame.color = color;
            frame.parentFrame = parentFrame;
            return frame;
        }
    }

    /** Mutable leaves are privately owned; neither capture nor restore exposes history's copy. */
    private static final class Value {
        private final Object value;
        Value(Object value) { this.value = copyValue(value); }
        Object restore() { return copyValue(value); }
        @Override public boolean equals(Object other) { return other instanceof Value v && sameValue(value, v.value); }
        @Override public int hashCode() { return 0; } // Used for equality, not as a hash-table key.
    }

    private static Object copyValue(Object value) {
        if (value == null) return null;
        if (value instanceof ItemStack stack) return stack.copy();
        if (value instanceof JsonElement json) return json.deepCopy();
        if (value instanceof Tag tag) return tag.copy();
        if (value instanceof BlockPos.MutableBlockPos position)
            return new BlockPos.MutableBlockPos(position.getX(), position.getY(), position.getZ());
        if (value instanceof AtomicInteger number) return new AtomicInteger(number.get());
        if (value instanceof AtomicLong number) return new AtomicLong(number.get());
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, element) -> copy.put(copyValue(key), copyValue(element)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(copyValue(element)));
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            set.forEach(element -> copy.add(copyValue(element)));
            return copy;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object copy = Array.newInstance(value.getClass().getComponentType(), length);
            for (int i = 0; i < length; i++) Array.set(copy, i, copyValue(Array.get(value, i)));
            return copy;
        }
        // Authoring value records may contain collections (rich text styles, quest predicates, etc.).
        if (value.getClass().isRecord()) {
            try {
                RecordComponent[] components = value.getClass().getRecordComponents();
                Class<?>[] types = new Class<?>[components.length];
                Object[] values = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    types[i] = components[i].getType();
                    var accessor = components[i].getAccessor();
                    accessor.trySetAccessible();
                    values[i] = copyValue(accessor.invoke(value));
                }
                var constructor = value.getClass().getDeclaredConstructor(types);
                constructor.trySetAccessible();
                return constructor.newInstance(values);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Cannot snapshot graph value " + value.getClass().getName(), failure);
            }
        }
        // Only explicitly immutable leaves may be shared. Unknown plugin values fail before a
        // mutation rather than silently making the undo history depend on a mutable live object.
        if (value instanceof String || value instanceof Boolean || value instanceof Character
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof BigInteger || value instanceof BigDecimal || value instanceof Enum<?>
                || value instanceof UUID || value instanceof Vec3 || value instanceof BlockPos
                || value instanceof Identifier || value instanceof EntityTemplateValue || value instanceof GeometryValue)
            return value;
        throw new IllegalArgumentException("Unsupported mutable graph history value: " + value.getClass().getName());
    }

    private static boolean sameValue(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first instanceof ItemStack a && second instanceof ItemStack b) return ItemStack.matches(a, b);
        if (first instanceof AtomicInteger a && second instanceof AtomicInteger b) return a.get() == b.get();
        if (first instanceof AtomicLong a && second instanceof AtomicLong b) return a.get() == b.get();
        if (first instanceof Map<?, ?> a && second instanceof Map<?, ?> b) {
            if (!a.keySet().equals(b.keySet())) return false;
            for (Object key : a.keySet()) if (!sameValue(a.get(key), b.get(key))) return false;
            return true;
        }
        if (first instanceof List<?> a && second instanceof List<?> b) {
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!sameValue(a.get(i), b.get(i))) return false;
            return true;
        }
        if (first.getClass().isArray() && first.getClass() == second.getClass()) {
            int length = Array.getLength(first);
            if (length != Array.getLength(second)) return false;
            for (int i = 0; i < length; i++) if (!sameValue(Array.get(first, i), Array.get(second, i))) return false;
            return true;
        }
        if (first instanceof Set<?> a && second instanceof Set<?> b) {
            if (a.size() != b.size()) return false;
            List<Object> remaining = new ArrayList<>(b);
            for (Object value : a) {
                int match = -1;
                for (int i = 0; i < remaining.size(); i++) {
                    if (sameValue(value, remaining.get(i))) { match = i; break; }
                }
                if (match < 0) return false;
                remaining.remove(match);
            }
            return true;
        }
        if (first.getClass().isRecord() && first.getClass() == second.getClass()) {
            try {
                for (RecordComponent component : first.getClass().getRecordComponents()) {
                    var accessor = component.getAccessor();
                    accessor.trySetAccessible();
                    if (!sameValue(accessor.invoke(first), accessor.invoke(second))) return false;
                }
                return true;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Cannot compare graph value " + first.getClass().getName(), failure);
            }
        }
        return Objects.equals(first, second);
    }

    private static <T, S> Map<String, S> captureMap(Map<String, T> source, Map<String, S> previous,
                                                   BiFunction<T, S, S> capture) {
        if (source == null) return null;
        Map<String, S> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            S old = previous == null ? null : previous.get(key);
            S next = value == null ? null : capture.apply(value, old);
            result.put(key, Objects.equals(next, old) ? old : next);
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, NodeData> restoreNodes(Map<String, NodeState> nodes, NodeData parent) {
        if (nodes == null) return null;
        Map<String, NodeData> result = new LinkedHashMap<>();
        nodes.forEach((key, node) -> result.put(key, node == null ? null : node.restore(parent)));
        return result;
    }

    private static Map<String, NodeData.PortConfig> restorePorts(Map<String, PortState> ports) {
        if (ports == null) return null;
        Map<String, NodeData.PortConfig> result = new LinkedHashMap<>();
        ports.forEach((key, port) -> result.put(key, port == null ? null : port.restore()));
        return result;
    }

    private static boolean sameOrderedMap(Map<?, ?> first, Map<?, ?> second) {
        if (first == second) return true;
        return first != null && second != null && first.equals(second)
                && new ArrayList<>(first.keySet()).equals(new ArrayList<>(second.keySet()));
    }

    private static <T> List<T> freezeList(List<T> list) {
        return list == null ? null : Collections.unmodifiableList(new ArrayList<>(list));
    }
    private static <T> List<T> mutableList(List<T> list) { return list == null ? null : new ArrayList<>(list); }
    private static <K, V> Map<K, V> freezeMap(Map<K, V> map) {
        return map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
