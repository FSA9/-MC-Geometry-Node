package com.mine.geometry_node.core.node.nodes;

import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.port.PortRow;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record NodeDef(
        String typeId,
        NodeType category,
        Component displayName,
        String comment,
        Map<MetaKey<?>, Object> meta,
        List<PortRow> rows
) {
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

        private String comment = "";
        private final Map<MetaKey<?>, Object> meta = new HashMap<>();
        private final List<PortRow> rows = new ArrayList<>();

        private Builder(String typeId, NodeType category, Component displayName) {
            this.typeId = typeId;
            this.category = category;
            this.displayName = displayName;
        }

        public Builder comment(String comment) {
            this.comment = comment;
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

        public NodeDef build() {
            return new NodeDef(typeId, category, displayName, comment, Map.copyOf(meta), List.copyOf(rows));
        }
    }
}
