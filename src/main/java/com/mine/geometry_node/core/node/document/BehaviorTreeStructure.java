package com.mine.geometry_node.core.node.document;

import com.google.gson.annotations.SerializedName;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorSubtreeParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Graph-level behavior-tree metadata. The hierarchy is represented by the
 * explicit structure connections stored on each node.
 */
public final class BehaviorTreeStructure {
    @SerializedName("parameters")
    private List<BehaviorSubtreeParameter> parameters = new ArrayList<>();

    public List<BehaviorSubtreeParameter> parameters() {
        return Collections.unmodifiableList(parameters);
    }

    public void setParameters(List<BehaviorSubtreeParameter> declarations) {
        parameters = declarations != null ? new ArrayList<>(declarations) : new ArrayList<>();
    }

    /** Restores collection invariants without discarding invalid editable data. */
    public void restoreDocumentDefaults() {
        if (parameters == null) parameters = new ArrayList<>();
        parameters.removeIf(java.util.Objects::isNull);
        parameters.forEach(BehaviorSubtreeParameter::restoreDocumentDefaults);
    }
}
