package com.mine.geometry_node.core.engine.behavior.contract;

/**
 * Default server budget and deterministic safety limits. Time limits are soft
 * scheduling budgets; structural limits are hard per-evaluation ceilings.
 */
public record BehaviorRuntimeBudget(int targetLoadedInstances,
                                    int maxDueInstancesPerTick,
                                    long globalNanosPerTick,
                                    long worldNanosPerTick,
                                    long instanceNanosPerEvaluation,
                                    int maxNodeVisitsPerEvaluation,
                                    int maxTreeDepth,
                                    int maxQueuedWakeupsPerWorld,
                                    int maxBlackboardEntriesPerInstance,
                                    int maxHistoryEntriesPerInstance) {
    public static final BehaviorRuntimeBudget DEFAULT = new BehaviorRuntimeBudget(
            1_000, 500, 2_000_000L, 1_500_000L, 100_000L,
            256, 64, 4_096, 256, 128);

    public BehaviorRuntimeBudget {
        requirePositive(targetLoadedInstances, "targetLoadedInstances");
        requirePositive(maxDueInstancesPerTick, "maxDueInstancesPerTick");
        requirePositive(globalNanosPerTick, "globalNanosPerTick");
        requirePositive(worldNanosPerTick, "worldNanosPerTick");
        requirePositive(instanceNanosPerEvaluation, "instanceNanosPerEvaluation");
        requirePositive(maxNodeVisitsPerEvaluation, "maxNodeVisitsPerEvaluation");
        requirePositive(maxTreeDepth, "maxTreeDepth");
        requirePositive(maxQueuedWakeupsPerWorld, "maxQueuedWakeupsPerWorld");
        requirePositive(maxBlackboardEntriesPerInstance, "maxBlackboardEntriesPerInstance");
        requirePositive(maxHistoryEntriesPerInstance, "maxHistoryEntriesPerInstance");
        if (worldNanosPerTick > globalNanosPerTick) {
            throw new IllegalArgumentException("world budget cannot exceed global budget");
        }
        if (instanceNanosPerEvaluation > worldNanosPerTick) {
            throw new IllegalArgumentException("instance budget cannot exceed world budget");
        }
        if (maxDueInstancesPerTick > targetLoadedInstances) {
            throw new IllegalArgumentException("due instances cannot exceed the target loaded population");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
