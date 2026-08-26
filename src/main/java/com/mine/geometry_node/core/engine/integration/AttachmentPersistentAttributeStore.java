package com.mine.geometry_node.core.engine.integration;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.service.PersistentAttributeTarget;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Current attachment-backed persistent attribute adapter, installed by the application root. */
public final class AttachmentPersistentAttributeStore implements GraphEngineServices.PersistentAttributeStore {
    @Override
    public void set(@Nullable GraphRuntimeContext context, @Nullable PersistentAttributeTarget target,
                    String name, @Nullable Object value) {
        if (name == null || name.trim().isEmpty()) return;
        PersistentAttributeTarget resolved = target != null ? target : PersistentAttributeTarget.global();
        if (resolved instanceof PersistentAttributeTarget.EntityTarget entityTarget) {
            Entity entity = entityTarget.entity();
            if (entity == null) return;
            EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
            if (attachment != null) attachment.setAttribute(name, value);
            return;
        }
        if (context == null) return;
        LevelGraphAttachment attachment = LevelGraphAttachment.get(context.level().getServer().overworld());
        if (resolved instanceof PersistentAttributeTarget.GlobalTarget) {
            attachment.setAttribute(name, value);
        } else if (resolved instanceof PersistentAttributeTarget.ScopeTarget scopeTarget
                && scopeTarget.scopeId() != null && !scopeTarget.scopeId().isBlank()) {
            attachment.setAttribute(scopeKey(scopeTarget.scopeId(), name), value);
        }
    }

    @Override
    public @Nullable Object get(@Nullable GraphRuntimeContext context, @Nullable PersistentAttributeTarget target,
                                String name) {
        if (name == null || name.trim().isEmpty()) return null;
        PersistentAttributeTarget resolved = target != null ? target : PersistentAttributeTarget.global();
        if (resolved instanceof PersistentAttributeTarget.EntityTarget entityTarget) {
            Entity entity = entityTarget.entity();
            if (entity == null) return null;
            EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
            return attachment != null ? attachment.getAttribute(name) : null;
        }
        if (context == null) return null;
        LevelGraphAttachment attachment = LevelGraphAttachment.get(context.level().getServer().overworld());
        if (resolved instanceof PersistentAttributeTarget.GlobalTarget) return attachment.getAttribute(name);
        if (resolved instanceof PersistentAttributeTarget.ScopeTarget scopeTarget
                && scopeTarget.scopeId() != null && !scopeTarget.scopeId().isBlank()) {
            return attachment.getAttribute(scopeKey(scopeTarget.scopeId(), name));
        }
        return null;
    }

    private static String scopeKey(String scopeId, String name) {
        return "scope:" + scopeId + ":" + name;
    }
}
