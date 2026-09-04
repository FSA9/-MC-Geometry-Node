package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AddEntityTag extends BaseNode {

    public static final String TYPE_ID = "add_entity_tag";
    private static final int DEFAULT_TAG_COUNT = 1;
    private static final int MAX_TAG_COUNT = 30;

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDefinition(DEFAULT_TAG_COUNT);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDefinition(resolveTagCount(instanceData));
    }

    private NodeDef buildDefinition(int tagCount) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .input(StandardPorts.FLOW_IN, "flow_in")
                .input(StandardPorts.ENTITY, "entity")
                .output(StandardPorts.ENTITY, "entity")
                .output(StandardPorts.FLOW_OUT, "flow_out");
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.add_entity_tag"))
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, MAX_TAG_COUNT);

        builder.addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                UIHint.DEFAULT, null, null));
        builder.addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT);
        for (int i = 1; i <= tagCount; i++) {
            comment.input(StandardPorts.TAG.getIdWithIndex(i), "tag");
            comment.output(StandardPorts.TAG.getIdWithIndex(i), "tag");
            builder.addPassthroughInput(StandardPorts.TAG.toInputWithIndex(i, ""), UIHint.INPUT, null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.DYNAMIC_INDEX, i));
        }
        return builder.comment(comment.build()).build();
    }

    private static int resolveTagCount(NodeData instanceData) {
        if (instanceData == null) return DEFAULT_TAG_COUNT;
        Object value = instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        int count = DEFAULT_TAG_COUNT;
        if (value instanceof Number number) {
            count = number.intValue();
        } else if (value instanceof String string) {
            try {
                count = Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return DEFAULT_TAG_COUNT;
            }
        }
        return Math.max(DEFAULT_TAG_COUNT, Math.min(count, MAX_TAG_COUNT));
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);

        List<String> tagsToAdd = new ArrayList<>();
        int portCount = DEFAULT_TAG_COUNT;
        Object countObj = context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        if (countObj instanceof Number n) {
            portCount = Math.max(DEFAULT_TAG_COUNT, Math.min(n.intValue(), MAX_TAG_COUNT));
        }

        for (int i = 1; i <= portCount; i++) {
            String tag = getInput(context, StandardPorts.TAG.getIdWithIndex(i), String.class);
            if (tag != null && !tag.trim().isEmpty()) {
                tagsToAdd.add(tag.trim());
            }
        }

        if (!entities.isEmpty() && !tagsToAdd.isEmpty()) {
            for (Entity entity : entities) {
                for (String tag : tagsToAdd) {
                    entity.addTag(tag);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
