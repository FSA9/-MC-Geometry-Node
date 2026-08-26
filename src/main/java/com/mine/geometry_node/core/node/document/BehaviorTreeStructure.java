package com.mine.geometry_node.core.node.document;

import com.google.gson.annotations.SerializedName;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorBlackboardDeclaration;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorSubtreeDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editable behavior-tree hierarchy. Control children are stored separately
 * from execution and data connections because their order is runtime meaning.
 */
public final class BehaviorTreeStructure {
    @SerializedName("children")
    private Map<String, List<String>> children = new LinkedHashMap<>();

    @SerializedName("blackboard")
    private List<BehaviorBlackboardDeclaration> blackboard = new ArrayList<>();

    @SerializedName("subtrees")
    private List<BehaviorSubtreeDependency> subtreeDependencies = new ArrayList<>();

    public List<String> childrenOf(String parentId) {
        List<String> result = children.get(parentId);
        return result != null ? Collections.unmodifiableList(result) : List.of();
    }

    public Map<String, List<String>> relationships() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : children.entrySet()) {
            snapshot.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /** Counts stored hierarchy edges without allocating a relationship snapshot. */
    public int relationshipCountUpTo(int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
        long count = 0;
        for (List<String> childIds : children.values()) {
            if (childIds == null) continue;
            count += childIds.size();
            if (count > limit) return limit + 1;
        }
        return (int) count;
    }

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

    public void replaceRelationships(Map<String, ? extends List<String>> relationships) {
        children = new LinkedHashMap<>();
        if (relationships == null) return;
        for (Map.Entry<String, ? extends List<String>> entry : relationships.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                children.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
    }

    public void setChildren(String parentId, List<String> orderedChildren) {
        if (parentId == null || parentId.isBlank()) {
            throw new IllegalArgumentException("Behavior parent id cannot be empty");
        }
        if (orderedChildren == null || orderedChildren.isEmpty()) {
            children.remove(parentId);
            return;
        }
        children.put(parentId, new ArrayList<>(orderedChildren));
    }

    public void addChild(String parentId, String childId, int index) {
        if (parentId == null || parentId.isBlank() || childId == null || childId.isBlank()) {
            throw new IllegalArgumentException("Behavior parent and child ids cannot be empty");
        }
        List<String> ordered = children.computeIfAbsent(parentId, ignored -> new ArrayList<>());
        if (ordered.contains(childId)) return;
        int insertionIndex = Math.max(0, Math.min(index, ordered.size()));
        ordered.add(insertionIndex, childId);
    }

    public int removeChild(String parentId, String childId) {
        List<String> ordered = children.get(parentId);
        if (ordered == null) return -1;
        int index = ordered.indexOf(childId);
        if (index < 0) return -1;
        ordered.remove(index);
        if (ordered.isEmpty()) children.remove(parentId);
        return index;
    }

    public String parentOf(String childId) {
        for (Map.Entry<String, List<String>> entry : children.entrySet()) {
            if (entry.getValue().contains(childId)) return entry.getKey();
        }
        return null;
    }

    public boolean contains(String parentId, String childId) {
        return childrenOf(parentId).contains(childId);
    }

    public void removeNode(String nodeId) {
        if (nodeId == null) return;
        children.remove(nodeId);
        for (List<String> childIds : children.values()) {
            childIds.removeIf(nodeId::equals);
        }
        children.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** Restores collection invariants without discarding invalid editable data. */
    public void restoreDocumentDefaults() {
        if (children == null) {
            children = new LinkedHashMap<>();
        }
        Map<String, List<String>> restored = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : children.entrySet()) {
            if (entry.getKey() == null) continue;
            restored.put(entry.getKey(), entry.getValue() != null
                    ? new ArrayList<>(entry.getValue()) : new ArrayList<>());
        }
        children = restored;
        if (blackboard == null) blackboard = new ArrayList<>();
        blackboard.removeIf(java.util.Objects::isNull);
        if (subtreeDependencies == null) subtreeDependencies = new ArrayList<>();
        subtreeDependencies.removeIf(java.util.Objects::isNull);
        subtreeDependencies.forEach(BehaviorSubtreeDependency::restoreDocumentDefaults);
    }
}
