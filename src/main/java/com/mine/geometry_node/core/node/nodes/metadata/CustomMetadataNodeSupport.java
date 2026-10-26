package com.mine.geometry_node.core.node.nodes.metadata;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataTarget;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.*;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** The eight nodes share target selection, while each owner retains its own capability contract. */
abstract class CustomMetadataNodeSupport extends BaseNode {
    static final String TARGET_TYPE = "target_type";
    static final String FACTION_ID = "faction_id";
    static final String TAGS = "tags";
    enum TargetType { ENTITY, DIMENSION, GLOBAL, RESOURCE, FACTION }
    private final boolean tags;

    CustomMetadataNodeSupport(boolean tags) { this.tags = tags; }

    final TargetType targetType(@Nullable Object value) {
        if (value == null || value instanceof String text && text.isBlank()) return TargetType.ENTITY;
        TargetType target = TargetType.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        if (tags && (target == TargetType.DIMENSION || target == TargetType.GLOBAL))
            throw new IllegalArgumentException("Target does not support CustomTag: " + target);
        return target;
    }

    final TargetType targetType(@Nullable NodeData data) {
        return targetType(data == null || data.inputs == null ? null : data.inputs.get(TARGET_TYPE));
    }

    final void addTargetInputs(NodeDef.Builder builder, TargetType selected) {
        String[] options = Arrays.stream(TargetType.values())
                .filter(value -> !tags || value != TargetType.DIMENSION && value != TargetType.GLOBAL)
                .map(value -> value.name().toLowerCase(Locale.ROOT)).toArray(String[]::new);
        String[] labels = Arrays.stream(options).map(id -> "geometry_node.metadata.target." + id).toArray(String[]::new);
        builder.addStaticInput(PortDef.create(TARGET_TYPE, "geometry_node.port.metadata_target", PortType.STRING, "entity"),
                UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, options, PortMetaKeys.OPTION_LABELS, labels));
        switch (selected) {
            case ENTITY -> builder.addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT);
            case DIMENSION -> builder.addPassthroughInput(StandardPorts.DIMENSION.toInput(RegistryDataManager.DEFAULT_DIMENSION),
                    UIHint.SELECT, null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID));
            case RESOURCE -> builder.addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT);
            case FACTION -> builder.addPassthroughInput(PortDef.create(FACTION_ID, "geometry_node.port.faction_id", PortType.STRING, ""),
                    UIHint.SELECT, null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.FACTION_REGISTRY_ID));
            case GLOBAL -> { }
        }
    }

    final @Nullable MetadataCapabilities.Access access(GraphDataContext context) {
        GraphMetadataTarget target = switch (targetType(context.getStaticInput(TARGET_TYPE))) {
            case ENTITY -> {
                Entity entity = getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class);
                yield entity == null ? null : new GraphMetadataTarget.EntityTarget(entity);
            }
            case DIMENSION -> {
                String dimension = getInput(context, StandardPorts.DIMENSION.getId(), String.class);
                yield dimension == null || dimension.isBlank() ? null : new GraphMetadataTarget.DimensionTarget(dimension);
            }
            case GLOBAL -> GraphMetadataTarget.GlobalTarget.INSTANCE;
            case RESOURCE -> {
                String reference = getInput(context, StandardPorts.RESOURCE_ID.getId(), String.class);
                yield reference == null || reference.isBlank() ? null
                        : new GraphMetadataTarget.ResourceTarget(GraphResourceReferences.parse(reference));
            }
            case FACTION -> {
                String id = getInput(context, FACTION_ID, String.class);
                yield id == null || id.isBlank() ? null : new GraphMetadataTarget.FactionTarget(UUID.fromString(id));
            }
        };
        return target == null ? null : context.metadata(target);
    }

    final String key(GraphDataContext context) {
        String key = getInput(context, StandardPorts.NAME.getId(), String.class);
        return key == null ? "" : key;
    }
}
