package com.mine.geometry_node.core.engine.graph.compile;

import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable runtime node schema published after graph flattening. */
public record CanonicalNodeSchema(
        String typeId,
        Map<String, PortDef> inputs,
        Map<String, PortDef> outputs,
        Set<String> dataPassthroughOutputs,
        Set<String> portIds
) {
    public CanonicalNodeSchema {
        typeId = typeId != null ? typeId : "unknown";
        inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        dataPassthroughOutputs = Set.copyOf(dataPassthroughOutputs);
        portIds = Set.copyOf(portIds);
    }

    public static CanonicalNodeSchema from(String typeId, @Nullable NodeDef definition) {
        Map<String, PortDef> inputs = new LinkedHashMap<>();
        Map<String, PortDef> outputs = new LinkedHashMap<>();
        Set<String> passthroughs = new LinkedHashSet<>();
        Set<String> ports = new LinkedHashSet<>();

        if (definition != null) {
            for (PortRow row : definition.rows()) {
                addPort(row.leftPort(), inputs, ports);
                addPort(row.rightPort(), outputs, ports);
                if (row.dataPassthrough()) passthroughs.add(row.rightPort().id());
            }
        }
        return new CanonicalNodeSchema(typeId, inputs, outputs, passthroughs, ports);
    }

    private static void addPort(@Nullable PortDef port, Map<String, PortDef> target,
                                Set<String> ports) {
        if (port == null) return;
        // NodeDef owns registration-time structural validation; this stage only indexes it.
        target.put(port.id(), port);
        ports.add(port.id());
    }

    public boolean isDataPassthroughOutput(String portId) {
        return dataPassthroughOutputs.contains(portId);
    }
}
