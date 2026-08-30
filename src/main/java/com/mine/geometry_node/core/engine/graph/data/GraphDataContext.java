package com.mine.geometry_node.core.engine.graph.data;

import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Read-mostly capabilities required by graph data nodes, without control-flow APIs. */
public interface GraphDataContext {
    ServerLevel getLevel();

    @Nullable Entity getEntity();

    @Nullable Entity getGraphOwnerEntity();

    String getGraphId();

    GraphBindingKey getGraphBindingKey();

    default @Nullable UUID getGraphProcessInstanceId() {
        return null;
    }

    @Nullable Object getVariable(String name);

    default boolean hasVariable(String name) {
        return getVariable(name) != null;
    }

    @Nullable Object getInputValue(String portName);

    @Nullable Object getStaticInput(String portName);

    @Nullable Object getEventData(String key);

    boolean hasPort(String portName);

    @Nullable Object getScopedState(ScopedStateTarget target, String name);
}
