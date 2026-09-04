package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class ScopedStateNodeSupport {
    static final String SCOPE_PORT = "state_scope";
    static final ScopedStateScope DEFAULT_SCOPE = ScopedStateScope.OWNER;
    static final String DEFAULT_DIMENSION = RegistryDataManager.DEFAULT_DIMENSION;

    private static final String[] SCOPES = ScopedStateScope.optionIds(ScopedStateScope.PERSISTENT);
    private ScopedStateNodeSupport() {
    }

    static ScopedStateScope selectedScope(@Nullable NodeData instanceData) {
        Object value = instanceData != null ? instanceData.inputs.get(SCOPE_PORT) : null;
        return normalizeScope(value);
    }

    static ScopedStateScope selectedScope(GraphDataContext context) {
        return normalizeScope(context.getStaticInput(SCOPE_PORT));
    }

    static boolean usesEntity(ScopedStateScope scope) {
        return scope == ScopedStateScope.OWNER || scope == ScopedStateScope.GROUP;
    }

    static void addScopeInput(NodeDef.Builder builder) {
        PortDef input = PortDef.create(SCOPE_PORT, "geometry_node.port.state_scope",
                PortType.STRING, DEFAULT_SCOPE.id());
        Map<com.mine.geometry_node.core.node.meta.MetaKey<?>, Object> params =
                Map.of(PortMetaKeys.OPTIONS, SCOPES);
        builder.addStaticInput(input, UIHint.SELECT, null, params);
    }

    static void addDimensionInput(NodeDef.Builder builder) {
        addDimensionInput(builder, true);
    }

    static void addDimensionInput(NodeDef.Builder builder, boolean passthrough) {
        PortDef input = StandardPorts.DIMENSION.toInput(DEFAULT_DIMENSION);
        Map<com.mine.geometry_node.core.node.meta.MetaKey<?>, Object> params =
                Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID);
        if (passthrough) builder.addPassthroughInput(input, UIHint.SELECT, null, params);
        else builder.addRow(new PortRow(input, null, UIHint.SELECT, null, params));
    }

    @Nullable
    static ScopedStateTarget resolveTarget(GraphDataContext context, ScopedStateScope scope, @Nullable Entity entity) {
        return switch (scope) {
            case OWNER -> entity != null ? ScopedStateTarget.owner(entity) : null;
            case SHARED -> ScopedStateTarget.shared();
            case GROUP -> entity != null ? ScopedStateTarget.group(entity) : null;
            case WORLD -> {
                String dimension = dimension(context);
                yield ScopedStateTarget.world(dimension);
            }
            case INSTANCE -> null;
        };
    }

    static String dimension(GraphDataContext context) {
        Object value = context.resolveInput(StandardPorts.DIMENSION.getId()).value();
        return value instanceof String text && !text.isBlank() ? text : DEFAULT_DIMENSION;
    }

    static String requireKey(@Nullable String name) {
        return name != null ? name : "";
    }

    private static ScopedStateScope normalizeScope(@Nullable Object value) {
        try {
            return ScopedStateScope.resolve(value, DEFAULT_SCOPE, ScopedStateScope.PERSISTENT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }
}
