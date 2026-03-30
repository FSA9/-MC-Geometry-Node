package com.mine.geometry_node.core.node.nodes;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record NodeDef(
        String typeId,
        NodeType category,
        Component displayName,
        String comment,
        Map<String, Object> meta,
        List<PortRow> rows
) {
    public static Builder builder(String typeId, NodeType category, Component displayName) {
        return new Builder(typeId, category, displayName);
    }

    public static class Builder {
        private final String typeId;
        private final NodeType category;
        private final Component displayName;

        private String comment = "";
        private final Map<String, Object> meta = new HashMap<>();
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

        public Builder addMeta(String key, Object value) {
            /*
            * max_dynamic_input_number: （int）最大输入动态端口数
            * max_dynamic_output_number: （int）最大输出动态端口数
            * dynamic_branch_input_count:（int）当前动态输入端口数量
            * dynamic_branch_output_count:（int）当前动态输出端口数量
            */
            this.meta.put(key, value);
            return this;
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