package com.mine.geometry_node.client.model.render.backend.host.entity;

import java.time.Duration;
import java.util.ArrayDeque;

/** Pure admission policy for lazily built static geometry variants. */
public final class HostStaticVariantAdmissionGate {
    public static final long DEFAULT_STABLE_NANOS = Duration.ofMillis(500).toNanos();
    public static final long CHANGE_WINDOW_NANOS = Duration.ofSeconds(2).toNanos();
    public static final int CHANGES_BEFORE_COOLDOWN = 3;
    public static final long COOLDOWN_NANOS = Duration.ofSeconds(5).toNanos();
    public static final long RECOVERY_STABLE_NANOS = Duration.ofSeconds(1).toNanos();
    public static final long BUDGET_RETRY_NANOS = Duration.ofMillis(250).toNanos();

    private final ArrayDeque<Long> changes = new ArrayDeque<>();
    // Each category remembers at most one exact key/generation. A gate is instance-local and
    // admits a single current candidate, so historical revisions must not accumulate here.
    private GenerationState failed;
    private GenerationState building;
    private BudgetWait budgetWait;
    private HostStaticAdmissionKey candidate;
    private long candidateSinceNanos;
    private long cooldownUntilNanos;
    private boolean recovering;

    public Decision evaluate(HostStaticAdmissionKey admissionKey, boolean variantExists,
                             long generation, long nowNanos) {
        requireKey(admissionKey);
        requireNonNegative("generation", generation);
        requireNonNegative("nowNanos", nowNanos);
        if (variantExists) {
            if (matches(building, admissionKey)) building = null;
            if (matches(budgetWait, admissionKey)) budgetWait = null;
            candidate = null;
            return Decision.HIT;
        }
        if (matches(failed, admissionKey, generation)) return Decision.FAILED;
        if (matches(budgetWait, admissionKey)) {
            if (budgetWait.generation() == generation && nowNanos < budgetWait.retryAtNanos()) {
                return Decision.BUDGET_WAIT;
            }
            budgetWait = null;
        }
        if (matches(building, admissionKey)) {
            if (building.generation() == generation) return Decision.BUILDING;
            building = null;
        }

        if (nowNanos < cooldownUntilNanos) {
            observeCandidate(admissionKey, nowNanos, false);
            return Decision.COOLDOWN;
        }
        if (recovering) {
            observeCandidate(admissionKey, nowNanos, false);
            long stableSince = Math.max(candidateSinceNanos, cooldownUntilNanos);
            if (elapsed(nowNanos, stableSince) < RECOVERY_STABLE_NANOS) return Decision.WAIT;
            recovering = false;
            changes.clear();
            return beginBuild(admissionKey, generation);
        }

        if (!admissionKey.equals(candidate)) {
            observeCandidate(admissionKey, nowNanos, true);
            pruneChanges(nowNanos);
            if (changes.size() >= CHANGES_BEFORE_COOLDOWN) {
                cooldownUntilNanos = saturatedAdd(nowNanos, COOLDOWN_NANOS);
                recovering = true;
                return Decision.COOLDOWN;
            }
        }
        return elapsed(nowNanos, candidateSinceNanos) >= DEFAULT_STABLE_NANOS
                ? beginBuild(admissionKey, generation) : Decision.WAIT;
    }

    public void recordFailure(HostStaticAdmissionKey admissionKey, long generation) {
        requireKey(admissionKey);
        requireNonNegative("generation", generation);
        if (matches(building, admissionKey)) building = null;
        failed = new GenerationState(admissionKey, generation);
        if (matches(budgetWait, admissionKey)) budgetWait = null;
    }

    public void recordSuccess(HostStaticAdmissionKey admissionKey, long generation) {
        requireKey(admissionKey);
        requireNonNegative("generation", generation);
        if (matches(building, admissionKey, generation)) building = null;
        if (matches(failed, admissionKey, generation)) failed = null;
        if (matches(budgetWait, admissionKey, generation)) budgetWait = null;
    }

    public void recordCancelled(HostStaticAdmissionKey admissionKey, long generation) {
        requireKey(admissionKey);
        requireNonNegative("generation", generation);
        if (matches(building, admissionKey, generation)) building = null;
    }

    public void recordBudgetWait(HostStaticAdmissionKey admissionKey, long generation, long nowNanos) {
        requireKey(admissionKey);
        requireNonNegative("generation", generation);
        requireNonNegative("nowNanos", nowNanos);
        if (matches(building, admissionKey, generation)) building = null;
        budgetWait = new BudgetWait(admissionKey, generation, saturatedAdd(nowNanos, BUDGET_RETRY_NANOS));
    }

    public void clearFailures() {
        failed = null;
        budgetWait = null;
    }

    boolean building() { return building != null; }

    int rememberedStateCount() {
        return (failed == null ? 0 : 1) + (building == null ? 0 : 1) + (budgetWait == null ? 0 : 1);
    }

    public void clear() {
        changes.clear();
        failed = null;
        building = null;
        budgetWait = null;
        candidate = null;
        candidateSinceNanos = 0L;
        cooldownUntilNanos = 0L;
        recovering = false;
    }

    private void observeCandidate(HostStaticAdmissionKey admissionKey, long nowNanos, boolean trackChange) {
        if (admissionKey.equals(candidate)) return;
        candidate = admissionKey;
        candidateSinceNanos = nowNanos;
        if (trackChange) changes.addLast(nowNanos);
    }

    private Decision beginBuild(HostStaticAdmissionKey admissionKey, long generation) {
        building = new GenerationState(admissionKey, generation);
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

    private static void requireKey(HostStaticAdmissionKey admissionKey) {
        if (admissionKey == null) throw new NullPointerException("admissionKey");
    }

    private static boolean matches(KeyedState state, HostStaticAdmissionKey key) {
        return state != null && state.key().equals(key);
    }

    private static boolean matches(KeyedState state, HostStaticAdmissionKey key, long generation) {
        return matches(state, key) && state.generation() == generation;
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

    private sealed interface KeyedState permits GenerationState, BudgetWait {
        HostStaticAdmissionKey key();
        long generation();
    }

    private record GenerationState(HostStaticAdmissionKey key, long generation) implements KeyedState {}
    private record BudgetWait(HostStaticAdmissionKey key, long generation,
                              long retryAtNanos) implements KeyedState {}
}
