package com.mine.geometry_node.client.ai.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Declarative graph edit plan. Execution is intentionally outside this contract. */
public record GraphPatch(
        SessionRef session,
        ScopeRef scope,
        GraphRevision expectedRevision,
        String idempotencyKey,
        List<Operation> operations
) {
    public static final int MAX_OPERATIONS = 256;

    public GraphPatch {
        session = Objects.requireNonNull(session, "document");
        scope = Objects.requireNonNull(scope, "scope");
        expectedRevision = Objects.requireNonNull(expectedRevision, "expectedRevision");
        idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey");
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        if (operations.isEmpty()) throw new IllegalArgumentException("operations cannot be empty");
        if (operations.size() > MAX_OPERATIONS) throw new IllegalArgumentException("too many operations");
    }

    public record SessionRef(String id) {
        public SessionRef { id = requireNonBlank(id, "document.id"); }
    }

    /** Scope remains fixed even if the user changes the currently visible group. */
    public record ScopeRef(String id) {
        public ScopeRef { id = requireNonBlank(id, "scope.id"); }
    }

    public record GraphRevision(long value) {
        public GraphRevision {
            if (value < 0) throw new IllegalArgumentException("revision cannot be negative");
        }

        public GraphRevision next() {
            if (value == Long.MAX_VALUE) throw new IllegalStateException("revision overflow");
            return new GraphRevision(value + 1);
        }
    }

    /** Exactly one of id and alias is set. Aliases refer to nodes created earlier in this patch. */
    public record NodeRef(String id, String alias) {
        public NodeRef {
            id = normalizeOptional(id);
            alias = normalizeOptional(alias);
            if ((id == null) == (alias == null)) throw new IllegalArgumentException("node ref requires exactly one of id or alias");
        }

        public static NodeRef id(String id) { return new NodeRef(id, null); }
        public static NodeRef alias(String alias) { return new NodeRef(null, alias); }
    }

    /** References either a concrete node port or a port created earlier in this patch. */
    public record PortRef(NodeRef node, String portId, String alias) {
        public PortRef {
            portId = normalizeOptional(portId);
            alias = normalizeOptional(alias);
            boolean concrete = node != null && portId != null && alias == null;
            boolean generated = node == null && portId == null && alias != null;
            if (!concrete && !generated) {
                throw new IllegalArgumentException(
                        "port ref requires either node + portId or exactly one alias");
            }
        }

        public PortRef(NodeRef node, String portId) { this(node, portId, null); }
        public static PortRef alias(String alias) { return new PortRef(null, null, alias); }
    }

    /** Frame aliases have a separate namespace from node aliases. */
    public record FrameRef(String id, String alias) {
        public FrameRef {
            id = normalizeOptional(id);
            alias = normalizeOptional(alias);
            if ((id == null) == (alias == null)) throw new IllegalArgumentException("frame ref requires exactly one of id or alias");
        }

        public static FrameRef id(String id) { return new FrameRef(id, null); }
        public static FrameRef alias(String alias) { return new FrameRef(null, alias); }
    }

    /** A concrete dynamic branch or one created earlier in the same patch. */
    public record BranchRef(NodeRef node, String direction, Integer index, String alias) {
        public BranchRef {
            direction = normalizeOptional(direction);
            alias = normalizeOptional(alias);
            boolean concrete = node != null && direction != null && index != null && index > 0 && alias == null;
            boolean generated = node == null && direction == null && index == null && alias != null;
            if (!concrete && !generated) {
                throw new IllegalArgumentException(
                        "branch ref requires node + direction + positive index or exactly one alias");
            }
            if (concrete) direction = requireOneOf(direction, "direction", "input", "output");
        }

        public static BranchRef alias(String alias) { return new BranchRef(null, null, null, alias); }
    }

    public record Position(double x, double y) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("position must be finite");
        }
    }

    public sealed interface Operation permits AddNode, RemoveNode, MoveNode, Connect, Disconnect, SetPortValue,
            SetSelectValue, SetNodeProperty, AddFrame, RemoveFrame, SetFrameProperty, AddDynamicBranch, RemoveDynamicBranch,
            AddGroupVirtualPort, RemoveGroupVirtualPort, RenamePort {
        String op();
    }

    public record AddNode(String alias, String typeId, Position position, Map<String, JsonElement> properties) implements Operation {
        public AddNode {
            alias = requireNonBlank(alias, "alias");
            typeId = requireNonBlank(typeId, "typeId");
            position = Objects.requireNonNull(position, "position");
            properties = copyJsonMap(properties);
        }
        @Override public String op() { return "add_node"; }
        @Override public Map<String, JsonElement> properties() { return copyJsonMap(properties); }
    }

    public record RemoveNode(NodeRef node) implements Operation {
        public RemoveNode { node = Objects.requireNonNull(node, "node"); }
        @Override public String op() { return "remove_node"; }
    }

    public record MoveNode(NodeRef node, Position position) implements Operation {
        public MoveNode {
            node = Objects.requireNonNull(node, "node");
            position = Objects.requireNonNull(position, "position");
        }
        @Override public String op() { return "move_node"; }
    }

    public record Connect(PortRef from, PortRef to) implements Operation {
        public Connect {
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
        }
        @Override public String op() { return "connect"; }
    }

    public record Disconnect(PortRef from, PortRef to) implements Operation {
        public Disconnect {
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
        }
        @Override public String op() { return "disconnect"; }
    }

    public record SetPortValue(PortRef port, JsonElement value, JsonElement expectedOldValue) implements Operation {
        public SetPortValue {
            port = Objects.requireNonNull(port, "port");
            value = copyJson(value);
            expectedOldValue = copyOptionalJson(expectedOldValue);
        }
        @Override public String op() { return "set_port_value"; }
        @Override public JsonElement value() { return copyJson(value); }
        @Override public JsonElement expectedOldValue() { return copyOptionalJson(expectedOldValue); }
    }

    /** optionId is the stable value; label is display-only and never used to apply the edit. */
    public record SetSelectValue(PortRef port, String optionId, String expectedOldValue,
                                 String optionContextToken) implements Operation {
        public SetSelectValue {
            port = Objects.requireNonNull(port, "port");
            optionId = requireNonBlank(optionId, "optionId");
            expectedOldValue = normalizeOptional(expectedOldValue);
            optionContextToken = normalizeOptional(optionContextToken);
        }
        @Override public String op() { return "set_select_value"; }
    }

    public record SetNodeProperty(NodeRef node, String property, JsonElement value) implements Operation {
        public SetNodeProperty {
            node = Objects.requireNonNull(node, "node");
            property = requireNonBlank(property, "property");
            value = copyJson(value);
        }
        @Override public String op() { return "set_node_property"; }
        @Override public JsonElement value() { return copyJson(value); }
    }

    public record AddFrame(String alias, String title, Position position, double width, double height) implements Operation {
        public AddFrame {
            alias = requireNonBlank(alias, "alias");
            title = Objects.requireNonNull(title, "title");
            position = Objects.requireNonNull(position, "position");
            if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) {
                throw new IllegalArgumentException("frame dimensions must be positive and finite");
            }
        }
        @Override public String op() { return "add_frame"; }
    }

    public record RemoveFrame(FrameRef frame) implements Operation {
        public RemoveFrame { frame = Objects.requireNonNull(frame, "frame"); }
        @Override public String op() { return "remove_frame"; }
    }

    public record SetFrameProperty(FrameRef frame, String property, JsonElement value) implements Operation {
        public SetFrameProperty {
            frame = Objects.requireNonNull(frame, "frame");
            property = requireNonBlank(property, "property");
            value = copyJson(value);
        }
        @Override public String op() { return "set_frame_property"; }
        @Override public JsonElement value() { return copyJson(value); }
    }

    public record AddDynamicBranch(NodeRef node, String direction, String alias) implements Operation {
        public AddDynamicBranch {
            node = Objects.requireNonNull(node, "node");
            direction = requireOneOf(direction, "direction", "input", "output");
            alias = requireNonBlank(alias, "alias");
            if (alias.indexOf('.') >= 0) throw new IllegalArgumentException("branch alias cannot contain '.'");
        }
        @Override public String op() { return "add_dynamic_branch"; }
    }

    public record RemoveDynamicBranch(BranchRef branch) implements Operation {
        public RemoveDynamicBranch {
            branch = Objects.requireNonNull(branch, "branch");
        }
        @Override public String op() { return "remove_dynamic_branch"; }
    }

    public record AddGroupVirtualPort(NodeRef node, String direction, String portType, String alias) implements Operation {
        public AddGroupVirtualPort {
            node = Objects.requireNonNull(node, "node");
            direction = requireOneOf(direction, "direction", "input", "output");
            portType = requireNonBlank(portType, "portType");
            alias = requireNonBlank(alias, "alias");
            if (alias.indexOf('.') >= 0) throw new IllegalArgumentException("group port alias cannot contain '.'");
        }
        @Override public String op() { return "add_group_virtual_port"; }
    }

    public record RemoveGroupVirtualPort(PortRef port) implements Operation {
        public RemoveGroupVirtualPort {
            port = Objects.requireNonNull(port, "port");
        }
        @Override public String op() { return "remove_group_virtual_port"; }
    }

    public record RenamePort(PortRef port, String direction, String name) implements Operation {
        public RenamePort {
            port = Objects.requireNonNull(port, "port");
            direction = requireOneOf(direction, "direction", "input", "output");
            name = requireNonBlank(name, "name");
        }
        @Override public String op() { return "rename_port"; }
    }

    static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }

    private static String requireOneOf(String value, String field, String first, String second) {
        value = requireNonBlank(value, field);
        if (!value.equals(first) && !value.equals(second)) throw new IllegalArgumentException(field + " must be " + first + " or " + second);
        return value;
    }

    static String normalizeOptional(String value) { return value == null || value.isBlank() ? null : value; }
    static JsonElement copyJson(JsonElement value) { return value == null ? JsonNull.INSTANCE : value.deepCopy(); }
    static JsonElement copyOptionalJson(JsonElement value) { return value == null ? null : value.deepCopy(); }

    static Map<String, JsonElement> copyJsonMap(Map<String, JsonElement> values) {
        if (values == null || values.isEmpty()) return Map.of();
        return values.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> copyJson(entry.getValue())));
    }
}
