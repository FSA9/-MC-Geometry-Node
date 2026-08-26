package com.mine.geometry_node.core.engine.behavior.contract;

import java.util.Objects;

/** Determinism rules shared by every behavior-tree evaluator implementation. */
public record BehaviorEvaluationPolicy(SnapshotBoundary snapshotBoundary,
                                       TimeConsistency timeConsistency,
                                       RandomConsistency randomConsistency,
                                       WorldQueryConsistency worldQueryConsistency,
                                       ReentryPolicy reentryPolicy) {
    public static final BehaviorEvaluationPolicy DEFAULT = new BehaviorEvaluationPolicy(
            SnapshotBoundary.EVALUATION_EPOCH,
            TimeConsistency.CAPTURE_AT_EPOCH_START,
            RandomConsistency.INSTANCE_SEEDED_STREAM,
            WorldQueryConsistency.MEMOIZE_PER_NODE_AND_INPUT,
            ReentryPolicy.COALESCE_TO_NEXT_PASS);

    public BehaviorEvaluationPolicy {
        snapshotBoundary = Objects.requireNonNull(snapshotBoundary, "snapshotBoundary");
        timeConsistency = Objects.requireNonNull(timeConsistency, "timeConsistency");
        randomConsistency = Objects.requireNonNull(randomConsistency, "randomConsistency");
        worldQueryConsistency = Objects.requireNonNull(worldQueryConsistency, "worldQueryConsistency");
        reentryPolicy = Objects.requireNonNull(reentryPolicy, "reentryPolicy");
    }

    public enum SnapshotBoundary { EVALUATION_EPOCH }
    public enum TimeConsistency { CAPTURE_AT_EPOCH_START }
    public enum RandomConsistency { INSTANCE_SEEDED_STREAM }
    public enum WorldQueryConsistency { MEMOIZE_PER_NODE_AND_INPUT }
    public enum ReentryPolicy { COALESCE_TO_NEXT_PASS }
}
