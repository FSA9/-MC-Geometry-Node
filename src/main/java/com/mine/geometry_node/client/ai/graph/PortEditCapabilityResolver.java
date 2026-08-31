package com.mine.geometry_node.client.ai.graph;

import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;

/** Central fail-closed policy for port values writable by GraphPatch. */
public final class PortEditCapabilityResolver {
    private static final Capability WRITABLE = new Capability(true, "");

    public record Capability(boolean writable, String reason) {}

    private PortEditCapabilityResolver() {}

    public static Capability resolve(PortRow row) {
        if (row == null || row.leftPort() == null) return denied("input port is required");
        UIHint hint = row.uiHint() == null ? UIHint.DEFAULT : row.uiHint();
        if (hint == UIHint.BUTTON || hint == UIHint.ITEM_SLOT || hint == UIHint.ENTITY_TEMPLATE
                || hint == UIHint.CUSTOM || hint == UIHint.SLOT_REF) {
            return denied("port UI capability is not writable by GraphPatch: " + hint);
        }
        PortType type = row.leftPort().type() == null ? PortType.ANY : row.leftPort().type();
        return switch (type) {
            case INTEGER, LONG, FLOAT, BOOLEAN, STRING, PATH, XYZ, ANY -> WRITABLE;
            default -> denied("port type is not writable by GraphPatch: " + type);
        };
    }

    private static Capability denied(String reason) { return new Capability(false, reason); }
}
