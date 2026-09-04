package com.mine.geometry_node.core.node.definition.node;

import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record NodeDef(
        String typeId,
        NodeType category,
        Component displayName,
        NodeComment nodeComment,
        Map<MetaKey<?>, Object> meta,
        List<PortRow> rows
) {
    public static final String BUILTIN_NAMESPACE = "geometry_node";

    public NodeDef {
        typeId = canonicalTypeId(typeId);
        Objects.requireNonNull(category, "Node category cannot be null: " + typeId);
        Objects.requireNonNull(displayName, "Node display name cannot be null: " + typeId);
        nodeComment = nodeComment == null ? NodeComment.EMPTY : nodeComment;
        meta = meta == null ? Map.of() : Map.copyOf(meta);
        List<PortRow> normalizedRows = rows == null ? List.of() : new ArrayList<>(rows);
        validatePorts(typeId, normalizedRows);
        rows = List.copyOf(normalizedRows);
    }

    /** Expands a built-in declaration ID while preserving explicit addon namespaces. */
    public static String canonicalTypeId(String declaredTypeId) {
        requireNonBlank(declaredTypeId, "node type ID");
        String normalized = declaredTypeId.trim();
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid node type ID: whitespace is not allowed");
        }
        int separator = normalized.indexOf(':');
        if (separator < 0) return BUILTIN_NAMESPACE + ":" + normalized;
        if (separator == 0 || separator == normalized.length() - 1
                || separator != normalized.lastIndexOf(':')) {
            throw new IllegalArgumentException("Invalid node type ID: " + normalized);
        }
        return normalized;
    }

    /** Returns the model-facing short ID for a built-in node, or an empty string for addons. */
    public static String builtinShortTypeId(String canonicalTypeId) {
        if (canonicalTypeId == null) return "";
        String prefix = BUILTIN_NAMESPACE + ":";
        String normalized = canonicalTypeId.trim();
        return normalized.startsWith(prefix) ? normalized.substring(prefix.length()) : "";
    }

    private static void validatePorts(String typeId, List<PortRow> rows) {
        Map<String, PortDef> inputs = new LinkedHashMap<>();
        Map<String, PortDef> outputs = new LinkedHashMap<>();
        Map<String, PortDef> portsById = new LinkedHashMap<>();
        Set<String> passthroughIds = new HashSet<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            PortRow row = rows.get(rowIndex);
            if (row == null) {
                throw invalid(typeId, "row " + rowIndex + " is null");
            }
            validatePort(typeId, rowIndex, "input", row.leftPort(), inputs, portsById);
            validatePort(typeId, rowIndex, "output", row.rightPort(), outputs, portsById);
            if (row.dataPassthrough()) {
                passthroughIds.add(row.leftPort().id());
            }
        }

        for (String inputId : inputs.keySet()) {
            if (outputs.containsKey(inputId) && !passthroughIds.contains(inputId)) {
                throw invalid(typeId, "port ID '" + inputId
                        + "' is both input and output without an explicit passthrough row");
            }
        }
    }

    private static void validatePort(String typeId, int rowIndex, String direction,
                                     @Nullable PortDef port,
                                     Map<String, PortDef> directionalPorts,
                                     Map<String, PortDef> portsById) {
        if (port == null) return;

        requireNonBlank(port.id(), direction + " port ID at row " + rowIndex + " of " + typeId);
        if (port.type() == null) {
            throw invalid(typeId, direction + " port '" + port.id()
                    + "' at row " + rowIndex + " has no type");
        }
        if (port.displayName() == null) {
            throw invalid(typeId, direction + " port '" + port.id()
                    + "' at row " + rowIndex + " has no display name");
        }

        PortDef duplicate = directionalPorts.putIfAbsent(port.id(), port);
        if (duplicate != null) {
            throw invalid(typeId, "duplicate " + direction + " port ID '" + port.id()
                    + "' at row " + rowIndex);
        }

        PortDef existing = portsById.putIfAbsent(port.id(), port);
        if (existing != null && existing.type() != port.type()) {
            throw invalid(typeId, "port ID '" + port.id() + "' uses conflicting types "
                    + existing.type() + " and " + port.type());
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid " + field + ": value cannot be blank");
        }
    }

    private static IllegalArgumentException invalid(String typeId, String detail) {
        return new IllegalArgumentException("Invalid node definition '" + typeId + "': " + detail);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMeta(MetaKey<T> key) {
        Object value = meta.get(key);
        return value == null ? Optional.empty() : Optional.of((T) value);
    }

    public <T> T getMetaOrDefault(MetaKey<T> key, T defaultValue) {
        return getMeta(key).orElse(defaultValue);
    }

    public static Builder builder(String typeId, NodeType category, Component displayName) {
        return new Builder(typeId, category, displayName);
    }

    public static class Builder {
        private final String typeId;
        private final NodeType category;
        private final Component displayName;

        private NodeComment nodeComment = NodeComment.EMPTY;
        private final Map<MetaKey<?>, Object> meta = new HashMap<>();
        private final List<PortRow> rows = new ArrayList<>();

        private Builder(String typeId, NodeType category, Component displayName) {
            this.typeId = typeId;
            this.category = category;
            this.displayName = displayName;
        }

        public Builder comment(NodeComment comment) {
            this.nodeComment = comment == null ? NodeComment.EMPTY : comment;
            return this;
        }

        public <T> Builder addMeta(MetaKey<T> key, T value) {
            if (key != null && value != null) {
                this.meta.put(key, value);
            }
            return this;
        }

        public Builder uiWidth(int width) {
            return addMeta(SchemaKeys.UI_WIDTH, width);
        }

        public Builder addRow(PortRow row) {
            if (row != null) {
                this.rows.add(row);
            }
            return this;
        }

        public Builder addPassthroughInput(PortDef input, UIHint uiHint) {
            return addPassthroughInput(input, uiHint, null, null);
        }

        public Builder addPassthroughInput(PortDef input, UIHint uiHint,
                                           @Nullable String customWidgetId,
                                           @Nullable Map<MetaKey<?>, Object> hintParams) {
            return addRow(PortRow.passthrough(input, uiHint, customWidgetId, hintParams));
        }

        /**
         * Adds an editor-visible configuration value that cannot participate in data flow.
         * Runtime systems read these values from the compiled node's static inputs.
         */
        public Builder addStaticInput(PortDef input, UIHint uiHint) {
            return addStaticInput(input, uiHint, null, null);
        }

        public Builder addStaticInput(PortDef input, UIHint uiHint,
                                      @Nullable String customWidgetId,
                                      @Nullable Map<MetaKey<?>, Object> hintParams) {
            return addRow(new PortRow(input.hiddenPin(), null, uiHint, customWidgetId, hintParams));
        }

        public NodeDef build() {
            return new NodeDef(typeId, category, displayName, nodeComment, Map.copyOf(meta), List.copyOf(rows));
        }
    }
}
