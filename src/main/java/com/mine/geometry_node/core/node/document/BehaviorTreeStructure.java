package com.mine.geometry_node.core.node.document;

import com.google.gson.annotations.SerializedName;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorBlackboardDeclaration;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorSubtreeDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Graph-level behavior-tree metadata. The hierarchy is represented by the
 * explicit structure connections stored on each node.
 */
public final class BehaviorTreeStructure {
    @SerializedName("blackboard")
    private List<BehaviorBlackboardDeclaration> blackboard = new ArrayList<>();

    @SerializedName("subtrees")
    private List<BehaviorSubtreeDependency> subtreeDependencies = new ArrayList<>();

    public List<BehaviorBlackboardDeclaration> blackboardDeclarations() {
        return Collections.unmodifiableList(blackboard);
    }

    public void setBlackboardDeclarations(List<BehaviorBlackboardDeclaration> declarations) {
        blackboard = declarations != null ? new ArrayList<>(declarations) : new ArrayList<>();
    }

    public List<BehaviorSubtreeDependency> subtreeDependencies() {
        return Collections.unmodifiableList(subtreeDependencies);
    }

    public void setSubtreeDependencies(List<BehaviorSubtreeDependency> dependencies) {
        subtreeDependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
    }

    /** Restores collection invariants without discarding invalid editable data. */
    public void restoreDocumentDefaults() {
        if (blackboard == null) blackboard = new ArrayList<>();
        blackboard.removeIf(java.util.Objects::isNull);
        if (subtreeDependencies == null) subtreeDependencies = new ArrayList<>();
        subtreeDependencies.removeIf(java.util.Objects::isNull);
        subtreeDependencies.forEach(BehaviorSubtreeDependency::restoreDocumentDefaults);
    }
}
