package com.mine.geometry_node.core.engine.blueprint;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.service.PersistentAttributeTarget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.Map;

/**
 * Blueprint runtime facade. The blueprint VM implementation lives under this
 * runtime package; cross-runtime code should depend on this facade when it
 * needs blueprint semantics.
 */
public final class BlueprintRuntime implements GraphRuntime {
    public static final BlueprintRuntime INSTANCE = new BlueprintRuntime();

    private BlueprintRuntime() {
    }

    @Override
    public GraphKind kind() {
        return GraphKind.BLUEPRINT;
    }

    @Override
    public String id() {
        return "geometry_node:blueprint";
    }

    @Override
    public void init() {
        DynamicGraphManager.setReloadListener(GraphEngine::refreshGraphSubscriptions);
        GraphEngineServices.INSTANCE.setPersistentAttributeStore(new BlueprintPersistentAttributeStore());
    }

    @Nullable
    public RuntimeGraphIndex getGraphIndex(String graphId) {
        return GraphEngine.getGraphIndex(graphId);
    }

    public void bindGraph(Entity entity, String graphId) {
        GraphEngine.bindGraph(entity, graphId);
    }

    public void bindGlobalGraph(ServerLevel level, String graphId) {
        GraphEngine.bindGlobalGraph(level, graphId);
    }

    public void unbindGraph(Entity entity, String graphId) {
        GraphEngine.unbindGraph(entity, graphId);
    }

    public void unbindGlobalGraph(ServerLevel level, String graphId) {
        GraphEngine.unbindGlobalGraph(level, graphId);
    }

    public void unbindAllGraphs(Entity entity) {
        GraphEngine.unbindAllGraphs(entity);
    }

    public void unbindAllGlobalGraphs(ServerLevel level) {
        GraphEngine.unbindAllGlobalGraphs(level);
    }

    public Set<String> getBoundGraphs(Entity entity) {
        return GraphEngine.getBoundGraphs(entity);
    }

    public Set<String> getGlobalBoundGraphs(ServerLevel level) {
        return GraphEngine.getGlobalBoundGraphs(level);
    }

    public void dispatchEvent(@NotNull ServerLevel level, @Nullable Entity target, String eventNodeId,
                              @Nullable Map<String, Object> eventData) {
        GraphEngine.dispatchEvent(level, target, eventNodeId, eventData);
    }

    public void dispatchCustomEvent(@NotNull ServerLevel currentLevel, String frequency,
                                    @Nullable Map<String, Object> eventData) {
        GraphEngine.dispatchCustomEvent(currentLevel, frequency, eventData);
    }

    public void refreshGraphSubscriptions(MinecraftServer server, String graphId,
                                          @Nullable RuntimeGraphIndex oldIndex,
                                          @Nullable RuntimeGraphIndex newIndex) {
        GraphEngine.refreshGraphSubscriptions(server, graphId, oldIndex, newIndex);
    }

    private static final class BlueprintPersistentAttributeStore implements GraphEngineServices.PersistentAttributeStore {
        @Override
        public void set(@Nullable GraphRuntimeContext context,
                        @Nullable PersistentAttributeTarget target,
                        String name,
                        @Nullable Object value) {
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            PersistentAttributeTarget resolvedTarget = target != null ? target : PersistentAttributeTarget.global();
            if (resolvedTarget instanceof PersistentAttributeTarget.EntityTarget entityTarget) {
                if (entityTarget.entity() == null) {
                    return;
                }
                EntityGraphAttachment attachment = entityTarget.entity().getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                if (attachment != null) {
                    attachment.setAttribute(name, value);
                }
                return;
            }
            if (context == null) {
                return;
            }
            LevelGraphAttachment attachment = LevelGraphAttachment.get(context.level().getServer().overworld());
            if (resolvedTarget instanceof PersistentAttributeTarget.GlobalTarget) {
                attachment.setAttribute(name, value);
            } else if (resolvedTarget instanceof PersistentAttributeTarget.ScopeTarget scopeTarget) {
                if (scopeTarget.scopeId() == null || scopeTarget.scopeId().isBlank()) {
                    return;
                }
                attachment.setAttribute(scopeKey(scopeTarget.scopeId(), name), value);
            }
        }

        @Override
        public @Nullable Object get(@Nullable GraphRuntimeContext context,
                                    @Nullable PersistentAttributeTarget target,
                                    String name) {
            if (name == null || name.trim().isEmpty()) {
                return null;
            }
            PersistentAttributeTarget resolvedTarget = target != null ? target : PersistentAttributeTarget.global();
            if (resolvedTarget instanceof PersistentAttributeTarget.EntityTarget entityTarget) {
                if (entityTarget.entity() == null) {
                    return null;
                }
                EntityGraphAttachment attachment = entityTarget.entity().getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                return attachment != null ? attachment.getAttribute(name) : null;
            }
            if (context == null) {
                return null;
            }
            LevelGraphAttachment attachment = LevelGraphAttachment.get(context.level().getServer().overworld());
            if (resolvedTarget instanceof PersistentAttributeTarget.GlobalTarget) {
                return attachment.getAttribute(name);
            }
            if (resolvedTarget instanceof PersistentAttributeTarget.ScopeTarget scopeTarget) {
                if (scopeTarget.scopeId() == null || scopeTarget.scopeId().isBlank()) {
                    return null;
                }
                return attachment.getAttribute(scopeKey(scopeTarget.scopeId(), name));
            }
            return null;
        }

        private static String scopeKey(String scopeId, String name) {
            return "scope:" + scopeId + ":" + name;
        }
    }
}
