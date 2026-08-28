package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class ScopedStateNodeSupport {
    static final String SCOPE_PORT = "state_scope";
    static final ScopedStateScope DEFAULT_SCOPE = ScopedStateScope.OWNER;
    static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private static final String[] SCOPES = {
            ScopedStateScope.OWNER.name(),
            ScopedStateScope.SHARED.name(),
            ScopedStateScope.GROUP.name(),
            ScopedStateScope.WORLD.name()
    };
    private ScopedStateNodeSupport() {
    }

    static ScopedStateScope selectedScope(@Nullable NodeData instanceData) {
        Object value = instanceData != null ? instanceData.inputs.get(SCOPE_PORT) : null;
        return normalizeScope(value);
    }

    static ScopedStateScope selectedScope(ExecutionContext context) {
        return normalizeScope(context.getStaticInput(SCOPE_PORT));
    }

    static boolean usesEntity(ScopedStateScope scope) {
        return scope == ScopedStateScope.OWNER || scope == ScopedStateScope.GROUP;
    }

    static PortRow scopeRow(@Nullable PortDef output) {
        PortDef input = PortDef.create(SCOPE_PORT, "geometry_node.port.state_scope",
                PortType.STRING, DEFAULT_SCOPE.name()).hiddenPin();
        return new PortRow(input, output, UIHint.SELECT, null, Map.of(
                PortMetaKeys.OPTIONS, SCOPES
        ));
    }

    static PortRow dimensionRow(@Nullable PortDef output) {
        PortDef input = PortDef.create(StandardPorts.DIMENSION.getId(), "geometry_node.port.dimension",
                PortType.STRING, DEFAULT_DIMENSION).hiddenPin();
        return new PortRow(input, output, UIHint.SELECT, null,
                Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:dimension"));
    }

    @Nullable
    static ScopedStateTarget resolveTarget(ExecutionContext context, ScopedStateScope scope, @Nullable Entity entity) {
        return switch (scope) {
            case OWNER -> entity != null ? ScopedStateTarget.owner(entity) : null;
            case SHARED -> ScopedStateTarget.shared();
            case GROUP -> entity != null ? ScopedStateTarget.group(entity) : null;
            case WORLD -> {
                Object value = context.getStaticInput(StandardPorts.DIMENSION.getId());
                String dimension = value instanceof String text && !text.isBlank() ? text : DEFAULT_DIMENSION;
                yield ScopedStateTarget.world(dimension);
            }
            case INSTANCE -> null;
        };
    }

    static ScopedStateTarget requireTarget(
            ExecutionContext context, ScopedStateScope scope, @Nullable Entity entity) {
        ScopedStateTarget target = resolveTarget(context, scope, entity);
        if (target == null) {
            throw new IllegalStateException("Scoped state target is unavailable for " + scope);
        }
        return target;
    }

    static String requireKey(@Nullable String name) {
        String key = name != null ? name.trim() : "";
        if (key.isEmpty()) throw new IllegalStateException("Scoped state key cannot be empty");
        return key;
    }

    private static ScopedStateScope normalizeScope(@Nullable Object value) {
        if (value instanceof String scope) {
            try {
                ScopedStateScope parsed = ScopedStateScope.valueOf(scope.trim().toUpperCase(java.util.Locale.ROOT));
                if (parsed != ScopedStateScope.INSTANCE) return parsed;
            } catch (IllegalArgumentException ignored) {
                // Report the invalid persisted/manual value instead of silently changing its target.
            }
            if (!scope.isBlank()) throw new IllegalStateException("Unsupported blueprint scoped state scope: " + scope);
        }
        return DEFAULT_SCOPE;
    }
}
