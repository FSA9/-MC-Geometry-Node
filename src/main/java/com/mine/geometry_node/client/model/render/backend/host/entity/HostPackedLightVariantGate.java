package com.mine.geometry_node.client.model.render.backend.host.entity;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Pure admission policy for lazily built packed-light geometry variants. */
public final class HostPackedLightVariantGate {
    public static final long DEFAULT_STABLE_NANOS = Duration.ofMillis(500).toNanos();
    public static final long CHANGE_WINDOW_NANOS = Duration.ofSeconds(2).toNanos();
    public static final int CHANGES_BEFORE_COOLDOWN = 3;
    public static final long COOLDOWN_NANOS = Duration.ofSeconds(5).toNanos();
    public static final long RECOVERY_STABLE_NANOS = Duration.ofSeconds(1).toNanos();
    public static final long BUDGET_RETRY_NANOS = Duration.ofMillis(250).toNanos();

    private final ArrayDeque<Long> changes = new ArrayDeque<>();
    private final Map<Integer, Long> failedGenerations = new HashMap<>();
    private final Map<Integer, Long> buildingGenerations = new HashMap<>();
    private final Map<Integer, BudgetWait> budgetWaits = new HashMap<>();
    private Integer candidate;
    private long candidateSinceNanos;
    private long cooldownUntilNanos;
    private boolean recovering;

    public Decision evaluate(int packedLight, boolean variantExists, long generation, long nowNanos) {
        requireNonNegative("generation", generation);
        requireNonNegative("nowNanos", nowNanos);
        if (variantExists) {
            buildingGenerations.remove(packedLight);
            budgetWaits.remove(packedLight);
            candidate = null;
            return Decision.HIT;
        }
        if (failedGenerations.getOrDefault(packedLight, -1L) == generation) return Decision.FAILED;
        BudgetWait budgetWait = budgetWaits.get(packedLight);
        if (budgetWait != null) {
            if (budgetWait.generation() == generation && nowNanos < budgetWait.retryAtNanos()) {
                return Decision.BUDGET_WAIT;
            }
            budgetWaits.remove(packedLight);
        }
        Long buildingGeneration = buildingGenerations.get(packedLight);
        if (buildingGeneration != null) {
            if (buildingGeneration == generation) return Decision.BUILDING;
            buildingGenerations.remove(packedLight);
        }

        if (nowNanos < cooldownUntilNanos) {
            observeCandidate(packedLight, nowNanos, false);
            return Decision.COOLDOWN;
        }
        if (recovering) {
            observeCandidate(packedLight, nowNanos, false);
            long stableSince = Math.max(candidateSinceNanos, cooldownUntilNanos);
            if (elapsed(nowNanos, stableSince) < RECOVERY_STABLE_NANOS) return Decision.WAIT;
            recovering = false;
            changes.clear();
            return beginBuild(packedLight, generation);
        }

        if (!Integer.valueOf(packedLight).equals(candidate)) {
            observeCandidate(packedLight, nowNanos, true);
            pruneChanges(nowNanos);
            if (changes.size() >= CHANGES_BEFORE_COOLDOWN) {
                cooldownUntilNanos = saturatedAdd(nowNanos, COOLDOWN_NANOS);
                recovering = true;
                return Decision.COOLDOWN;
            }
        }
        return elapsed(nowNanos, candidateSinceNanos) >= DEFAULT_STABLE_NANOS
                ? beginBuild(packedLight, generation) : Decision.WAIT;
    }

    public void recordFailure(int packedLight, long generation) {
        requireNonNegative("generation", generation);
        buildingGenerations.remove(packedLight);
        failedGenerations.put(packedLight, generation);
        budgetWaits.remove(packedLight);
    }

    public void recordSuccess(int packedLight, long generation) {
        requireNonNegative("generation", generation);
        buildingGenerations.remove(packedLight, generation);
        failedGenerations.remove(packedLight, generation);
        budgetWaits.remove(packedLight);
    }

    public void recordCancelled(int packedLight, long generation) {
        requireNonNegative("generation", generation);
        buildingGenerations.remove(packedLight, generation);
    }

    public void recordBudgetWait(int packedLight, long generation, long nowNanos) {
        requireNonNegative("generation", generation);
        requireNonNegative("nowNanos", nowNanos);
        buildingGenerations.remove(packedLight, generation);
        budgetWaits.put(packedLight, new BudgetWait(generation, saturatedAdd(nowNanos, BUDGET_RETRY_NANOS)));
    }

    public void clearFailures() {
        failedGenerations.clear();
        budgetWaits.clear();
    }

    boolean building() { return !buildingGenerations.isEmpty(); }

    public void clear() {
        changes.clear();
        failedGenerations.clear();
        buildingGenerations.clear();
        budgetWaits.clear();
        candidate = null;
        candidateSinceNanos = 0L;
        cooldownUntilNanos = 0L;
        recovering = false;
    }

    private void observeCandidate(int packedLight, long nowNanos, boolean trackChange) {
        if (Integer.valueOf(packedLight).equals(candidate)) return;
        candidate = packedLight;
        candidateSinceNanos = nowNanos;
        if (trackChange) changes.addLast(nowNanos);
    }

    private Decision beginBuild(int packedLight, long generation) {
        buildingGenerations.put(packedLight, generation);
        return Decision.BUILD;
    }

    private void pruneChanges(long nowNanos) {
        while (!changes.isEmpty() && elapsed(nowNanos, changes.getFirst()) > CHANGE_WINDOW_NANOS) {
            changes.removeFirst();
        }
    }

    private static long elapsed(long nowNanos, long thenNanos) {
        return nowNanos >= thenNanos ? nowNanos - thenNanos : 0L;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static void requireNonNegative(String name, long value) {
        if (value < 0L) throw new IllegalArgumentException(name + " must not be negative");
    }

    public enum Decision {
        HIT,
        BUILD,
        BUILDING,
        WAIT,
        COOLDOWN,
        FAILED,
        BUDGET_WAIT
    }

    private record BudgetWait(long generation, long retryAtNanos) {}
}
