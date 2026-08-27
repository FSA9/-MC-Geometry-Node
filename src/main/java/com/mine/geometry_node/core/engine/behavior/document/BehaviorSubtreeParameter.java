package com.mine.geometry_node.core.engine.behavior.document;

import com.google.gson.annotations.SerializedName;
import com.mine.geometry_node.core.node.port.PortType;

/** Public input/output contract exposed by a behavior-tree asset. */
public final class BehaviorSubtreeParameter {
    public enum Direction { INPUT, OUTPUT }

    public String name = "";
    public Direction direction = Direction.INPUT;
    public PortType type = PortType.ANY;

    @SerializedName("blackboard_key")
    public String blackboardKey = "";

    public void restoreDocumentDefaults() {
        if (name == null) name = "";
        if (direction == null) direction = Direction.INPUT;
        if (type == null) type = PortType.ANY;
        if (blackboardKey == null) blackboardKey = "";
    }
}
