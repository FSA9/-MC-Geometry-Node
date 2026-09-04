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

    /** Returns the stable document ID of the node currently being evaluated. */
    default @Nullable String getCurrentNodeStableId() {
        return null;
    }

    @Nullable Object getVariable(String name);

    default boolean hasVariable(String name) {
        return getVariable(name) != null;
    }

    @Nullable Object getInputValue(String portName);

    /** Distinguishes a connected null value from an input with no provider. */
    default boolean hasInputConnection(String portName) {
        return false;
    }

    /**
     * Resolves the effective input value without collapsing connected null into
     * an unconnected static fallback.
     */
    default ResolvedGraphInput resolveInput(String portName) {
        boolean connected = hasInputConnection(portName);
        return new ResolvedGraphInput(connected,
                connected ? getInputValue(portName) : getStaticInput(portName));
    }

    @Nullable Object getStaticInput(String portName);

    @Nullable Object getEventData(String key);

    /**
     * Reads a result published by the current node during execution. Data-only
     * contexts have no execution results and therefore return {@code null}.
     */
    default @Nullable Object getNodeResult(String portName) {
        return null;
    }

    /** Returns whether the node currently being evaluated started this event flow. */
    default boolean isCurrentEventSourceNode() {
        return false;
    }

    boolean hasPort(String portName);

    @Nullable Object getScopedState(ScopedStateTarget target, String name);
}
