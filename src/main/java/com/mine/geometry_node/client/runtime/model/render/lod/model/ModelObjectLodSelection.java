package com.mine.geometry_node.client.runtime.model.render.lod.model;

import java.util.Objects;
import java.util.function.IntFunction;

/** One object's requested/effective choice, reused by all render passes of the same frame. */
public final class ModelObjectLodSelection {
    private int level = -1;
    private long selectionFrame = -1;
    private ModelLodPolicy policy;
    private boolean iris, resident;
    private ModelLodResidency.Selection selection;

    public int level() { return level; }
    public long selectionFrame() { return selectionFrame; }
    public boolean iris() { return iris; }
    public boolean resident() { return resident; }
    public ModelLodResidency.Selection selection() { return selection; }

    public boolean matches(long frame, ModelLodPolicy policy, boolean iris, boolean resident) {
        return selection != null && selectionFrame == frame && Objects.equals(this.policy, policy)
                && this.iris == iris && this.resident == resident;
    }

    public ModelLodResidency.Selection select(long frame, ModelLodPolicy policy, double distance,
                                             boolean hasOpaque, boolean iris, boolean resident,
                                             IntFunction<ModelLodResidency.Selection> resolver) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(resolver, "resolver");
        if (matches(frame, policy, iris, resident)) return selection;
        int requested = hasOpaque ? policy.choose(distance, level) : 0;
        var resolved = Objects.requireNonNull(resolver.apply(requested), "resolved selection");
        this.level = requested;
        this.selection = resolved;
        this.selectionFrame = frame;
        this.policy = policy;
        this.iris = iris;
        this.resident = resident;
        return resolved;
    }
}
