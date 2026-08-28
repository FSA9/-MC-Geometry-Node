package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.node.NodeCapabilities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Resource-scoped ownership adapter for vanilla GoalSelector and Brain AI. */
public final class BehaviorNativeAiController {
    private static final Map<LivingEntity, EnumMap<NodeCapabilities.ResourceUse, Integer>> ACTIVE_BRAIN_LEASES
            = new WeakHashMap<>();
    private static final Map<Brain<?>, WeakReference<LivingEntity>> BRAIN_OWNERS = new WeakHashMap<>();
    private static final ThreadLocal<Brain<?>> ALLOWED_BRAIN_WRITE = new ThreadLocal<>();
    private static final Set<BehaviorNativeAiController> ACTIVE_TARGET_ASSIGNMENTS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private final Mob owner;
    private final EnumMap<Goal.Flag, GoalLease> goalLeases = new EnumMap<>(Goal.Flag.class);
    private final SelectorLeases goalSelector;
    private final SelectorLeases targetSelector;
    @Nullable private WeakReference<LivingEntity> assignedTarget;
    private boolean targetAssignmentActive;

    BehaviorNativeAiController(Mob owner) {
        this.owner = owner;
        this.goalSelector = new SelectorLeases(owner.goalSelector);
        this.targetSelector = new SelectorLeases(owner.targetSelector);
    }

    boolean acquire(Set<NodeCapabilities.ResourceUse> resources) {
        EnumSet<Goal.Flag> acquiredFlags = EnumSet.noneOf(Goal.Flag.class);
        EnumSet<NodeCapabilities.ResourceUse> acquiredBrainResources = normalizedResources(resources);
        Set<MemoryModuleType<?>> newlyControlledMemories = newlyControlledMemories(
                owner, acquiredBrainResources);
        Set<MemoryModuleType<?>> newlyBlockedBehaviorMemories = newlyBlockedBehaviorMemories(
                owner, acquiredBrainResources);
        boolean brainLeasesAdded = false;
        try {
            for (Goal.Flag flag : goalFlags(acquiredBrainResources)) {
                GoalLease lease = goalLeases.get(flag);
                if (lease == null) {
                    lease = new GoalLease(flag, goalSelector, targetSelector, goalLeases.keySet());
                    goalLeases.put(flag, lease);
                }
                lease.references++;
                acquiredFlags.add(flag);
            }

            addBrainLeases(owner, acquiredBrainResources);
            brainLeasesAdded = true;
            stopConflictingBrainBehaviors(newlyBlockedBehaviorMemories);
            clearControlledMemories(newlyControlledMemories);
            return true;
        } catch (RuntimeException exception) {
            if (brainLeasesAdded) removeBrainLeases(owner, acquiredBrainResources);
            releaseGoalFlags(acquiredFlags);
            return false;
        }
    }

    void release(Set<NodeCapabilities.ResourceUse> resources) {
        EnumSet<NodeCapabilities.ResourceUse> normalized = normalizedResources(resources);
        removeBrainLeases(owner, normalized);
        releaseGoalFlags(goalFlags(normalized));
    }

    @Nullable
    LivingEntity setAttackTarget(@Nullable LivingEntity target) {
        if (target == null) return clearAttackTarget();

        boolean acquiredNow = false;
        if (!targetAssignmentActive) {
            if (!acquire(Set.of(NodeCapabilities.ResourceUse.TARGET))) {
                return owner.getTargetUnchecked();
            }
            targetAssignmentActive = true;
            acquiredNow = true;
        }

        LivingEntity previous = assignedTarget != null ? assignedTarget.get() : null;
        writeAttackTarget(target);
        LivingEntity actual = owner.getTargetUnchecked();
        if (actual == target) {
            assignedTarget = new WeakReference<>(target);
            synchronized (ACTIVE_TARGET_ASSIGNMENTS) {
                ACTIVE_TARGET_ASSIGNMENTS.add(this);
            }
            return actual;
        }

        if (acquiredNow) {
            targetAssignmentActive = false;
            assignedTarget = null;
            release(Set.of(NodeCapabilities.ResourceUse.TARGET));
        } else if (previous != null && validAssignedTarget(previous)) {
            writeAttackTarget(previous);
        }
        return actual;
    }

    void maintainPersistentControls() {
        if (!targetAssignmentActive) return;
        LivingEntity target = assignedTarget != null ? assignedTarget.get() : null;
        if (!validAssignedTarget(target)) {
            releasePersistentControls();
            return;
        }
        if (owner.getTargetUnchecked() == target) return;
        writeAttackTarget(target);
        if (owner.getTargetUnchecked() != target) releasePersistentControls();
    }

    static void maintainPersistentControls(ServerLevel level) {
        List<BehaviorNativeAiController> active;
        synchronized (ACTIVE_TARGET_ASSIGNMENTS) {
            active = List.copyOf(ACTIVE_TARGET_ASSIGNMENTS);
        }
        for (BehaviorNativeAiController controller : active) {
            if (controller.owner.level() == level) controller.maintainPersistentControls();
        }
    }

    void releasePersistentControls() {
        if (!targetAssignmentActive) return;
        try {
            writeAttackTarget(null);
        } finally {
            assignedTarget = null;
            targetAssignmentActive = false;
            synchronized (ACTIVE_TARGET_ASSIGNMENTS) {
                ACTIVE_TARGET_ASSIGNMENTS.remove(this);
            }
            release(Set.of(NodeCapabilities.ResourceUse.TARGET));
        }
    }

    @Nullable
    private LivingEntity clearAttackTarget() {
        writeAttackTarget(null);
        LivingEntity actual = owner.getTargetUnchecked();
        if (actual == null && targetAssignmentActive) {
            assignedTarget = null;
            targetAssignmentActive = false;
            synchronized (ACTIVE_TARGET_ASSIGNMENTS) {
                ACTIVE_TARGET_ASSIGNMENTS.remove(this);
            }
            release(Set.of(NodeCapabilities.ResourceUse.TARGET));
        }
        return actual;
    }

    private boolean validAssignedTarget(@Nullable LivingEntity target) {
        return target != null && target != owner && target.isAlive() && !target.isRemoved()
                && target.level() == owner.level() && owner.canAttack(target);
    }

    private void writeAttackTarget(@Nullable LivingEntity target) {
        owner.setTarget(target);
        LivingEntity actual = owner.getTargetUnchecked();
        if (owner.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET) != null) {
            setControlledMemory(owner.getBrain(), MemoryModuleType.ATTACK_TARGET, actual);
        }
    }

    private void releaseGoalFlags(Set<Goal.Flag> flags) {
        for (Goal.Flag flag : flags) {
            GoalLease lease = goalLeases.get(flag);
            if (lease == null || --lease.references > 0) continue;
            goalLeases.remove(flag);
            lease.close(goalLeases.keySet());
        }
    }

    private void stopConflictingBrainBehaviors(Set<MemoryModuleType<?>> memories) {
        if (memories.isEmpty() || !(owner.level() instanceof ServerLevel level)) return;
        long tick = level.getGameTime();
        for (BehaviorControl<?> behavior : List.copyOf(owner.getBrain().getRunningBehaviors())) {
            if (!java.util.Collections.disjoint(memories, behavior.getRequiredMemories())) {
                invokeStop(behavior, level, owner, tick);
            }
        }
    }

    private void clearControlledMemories(Set<MemoryModuleType<?>> memories) {
        Brain<?> brain = owner.getBrain();
        for (MemoryModuleType<?> memory : memories) {
            // Keep the selected target available to actions that intentionally fall back to Mob#getTarget.
            if (memory == MemoryModuleType.ATTACK_TARGET
                    || memory == MemoryModuleType.ATTACK_COOLING_DOWN) continue;
            eraseControlledMemory(brain, memory);
        }
    }

    /** Called by the Brain mixin to keep native behaviors behind an active resource lease. */
    public static boolean tryStart(BehaviorControl<?> behavior, ServerLevel level,
                                   LivingEntity body, long timestamp) {
        if (isBehaviorBlocked(body, behavior)) return false;
        return invokeTryStart(behavior, level, body, timestamp);
    }

    /** Called by the Brain mixin to stop an already-running behavior before it can overwrite BT state. */
    public static void tickOrStop(BehaviorControl<?> behavior, ServerLevel level,
                                  LivingEntity body, long timestamp) {
        if (isBehaviorBlocked(body, behavior)) {
            invokeStop(behavior, level, body, timestamp);
            return;
        }
        invokeTickOrStop(behavior, level, body, timestamp);
    }

    /** Blocks control-memory writes made from inside a native Brain behavior. */
    public static boolean blocksMemoryWrite(Brain<?> brain, MemoryModuleType<?> memory) {
        return ALLOWED_BRAIN_WRITE.get() != brain && isMemoryBlocked(brain, memory);
    }

    /** Associates a replacement Brain with an entity that already owns active leases. */
    public static void onBrainTick(Brain<?> brain, LivingEntity body) {
        Set<MemoryModuleType<?>> staleMemories = Set.of();
        synchronized (ACTIVE_BRAIN_LEASES) {
            EnumMap<NodeCapabilities.ResourceUse, Integer> leases = ACTIVE_BRAIN_LEASES.get(body);
            if (leases == null) return;
            WeakReference<LivingEntity> current = BRAIN_OWNERS.get(brain);
            if (current != null && current.get() == body) return;
            BRAIN_OWNERS.put(brain, new WeakReference<>(body));
            staleMemories = controlledMemories(leases.keySet());
        }
        for (MemoryModuleType<?> memory : staleMemories) {
            if (memory != MemoryModuleType.ATTACK_TARGET
                    && memory != MemoryModuleType.ATTACK_COOLING_DOWN) {
                eraseControlledMemory(brain, memory);
            }
        }
    }

    public static <U> void setControlledMemory(Brain<?> brain, MemoryModuleType<U> memory, U value) {
        Brain<?> previous = ALLOWED_BRAIN_WRITE.get();
        ALLOWED_BRAIN_WRITE.set(brain);
        try {
            setMemory(brain, memory, value);
        } finally {
            if (previous == null) ALLOWED_BRAIN_WRITE.remove();
            else ALLOWED_BRAIN_WRITE.set(previous);
        }
    }

    private static void eraseControlledMemory(Brain<?> brain, MemoryModuleType<?> memory) {
        Brain<?> previous = ALLOWED_BRAIN_WRITE.get();
        ALLOWED_BRAIN_WRITE.set(brain);
        try {
            eraseMemory(brain, memory);
        } finally {
            if (previous == null) ALLOWED_BRAIN_WRITE.remove();
            else ALLOWED_BRAIN_WRITE.set(previous);
        }
    }

    private static boolean isBehaviorBlocked(LivingEntity body, BehaviorControl<?> behavior) {
        synchronized (ACTIVE_BRAIN_LEASES) {
            EnumMap<NodeCapabilities.ResourceUse, Integer> leases = ACTIVE_BRAIN_LEASES.get(body);
            return leases != null && blocksBehavior(leases.keySet(), behavior.getRequiredMemories());
        }
    }

    private static boolean isMemoryBlocked(Brain<?> brain, MemoryModuleType<?> memory) {
        synchronized (ACTIVE_BRAIN_LEASES) {
            WeakReference<LivingEntity> owner = BRAIN_OWNERS.get(brain);
            EnumMap<NodeCapabilities.ResourceUse, Integer> leases = owner != null
                    ? ACTIVE_BRAIN_LEASES.get(owner.get()) : null;
            return leases != null && controlsMemory(leases.keySet(), memory);
        }
    }

    private static boolean blocksBehavior(Set<NodeCapabilities.ResourceUse> resources,
                                          Set<MemoryModuleType<?>> requiredMemories) {
        for (MemoryModuleType<?> memory : requiredMemories) {
            if (blocksBehaviorUsing(resources, memory)) return true;
        }
        return false;
    }

    private static boolean blocksBehaviorUsing(Set<NodeCapabilities.ResourceUse> resources,
                                               MemoryModuleType<?> memory) {
        for (NodeCapabilities.ResourceUse resource : resources) {
            if ((resource == NodeCapabilities.ResourceUse.MOVEMENT
                    && (memory == MemoryModuleType.WALK_TARGET || memory == MemoryModuleType.PATH))
                    || (resource == NodeCapabilities.ResourceUse.LOOK
                    && memory == MemoryModuleType.LOOK_TARGET)
                    || (resource == NodeCapabilities.ResourceUse.COMBAT
                    && (memory == MemoryModuleType.WALK_TARGET
                    || memory == MemoryModuleType.PATH
                    || memory == MemoryModuleType.LOOK_TARGET
                    || memory == MemoryModuleType.ATTACK_TARGET
                    || memory == MemoryModuleType.ATTACK_COOLING_DOWN))) {
                return true;
            }
        }
        return false;
    }

    private static boolean controlsMemory(Set<NodeCapabilities.ResourceUse> resources,
                                          MemoryModuleType<?> memory) {
        for (NodeCapabilities.ResourceUse resource : resources) {
            if ((resource == NodeCapabilities.ResourceUse.MOVEMENT
                    && (memory == MemoryModuleType.WALK_TARGET || memory == MemoryModuleType.PATH))
                    || (resource == NodeCapabilities.ResourceUse.LOOK
                    && memory == MemoryModuleType.LOOK_TARGET)
                    || (resource == NodeCapabilities.ResourceUse.TARGET
                    && memory == MemoryModuleType.ATTACK_TARGET)
                    || (resource == NodeCapabilities.ResourceUse.COMBAT
                    && (memory == MemoryModuleType.WALK_TARGET
                    || memory == MemoryModuleType.PATH
                    || memory == MemoryModuleType.LOOK_TARGET
                    || memory == MemoryModuleType.ATTACK_TARGET
                    || memory == MemoryModuleType.ATTACK_COOLING_DOWN))) {
                return true;
            }
        }
        return false;
    }

    private static Set<MemoryModuleType<?>> newlyControlledMemories(
            LivingEntity body, Set<NodeCapabilities.ResourceUse> resources) {
        Set<MemoryModuleType<?>> result = controlledMemories(resources);
        synchronized (ACTIVE_BRAIN_LEASES) {
            EnumMap<NodeCapabilities.ResourceUse, Integer> leases = ACTIVE_BRAIN_LEASES.get(body);
            if (leases != null) result.removeIf(memory -> controlsMemory(leases.keySet(), memory));
        }
        return result;
    }

    private static Set<MemoryModuleType<?>> newlyBlockedBehaviorMemories(
            LivingEntity body, Set<NodeCapabilities.ResourceUse> resources) {
        Set<MemoryModuleType<?>> result = controlledMemories(resources);
        result.removeIf(memory -> !blocksBehaviorUsing(resources, memory));
        synchronized (ACTIVE_BRAIN_LEASES) {
            EnumMap<NodeCapabilities.ResourceUse, Integer> leases = ACTIVE_BRAIN_LEASES.get(body);
            if (leases != null) {
                result.removeIf(memory -> blocksBehaviorUsing(leases.keySet(), memory));
            }
        }
        return result;
    }

    private static void addBrainLeases(LivingEntity body, Set<NodeCapabilities.ResourceUse> resources) {
        if (resources.isEmpty()) return;
        synchronized (ACTIVE_BRAIN_LEASES) {
            EnumMap<NodeCapabilities.ResourceUse, Integer> leases = ACTIVE_BRAIN_LEASES.computeIfAbsent(
                    body, ignored -> new EnumMap<>(NodeCapabilities.ResourceUse.class));
            BRAIN_OWNERS.put(body.getBrain(), new WeakReference<>(body));
            for (NodeCapabilities.ResourceUse resource : resources) {
                leases.merge(resource, 1, Integer::sum);
            }
        }
    }

    private static void removeBrainLeases(LivingEntity body, Set<NodeCapabilities.ResourceUse> resources) {
        if (resources.isEmpty()) return;
        synchronized (ACTIVE_BRAIN_LEASES) {
            EnumMap<NodeCapabilities.ResourceUse, Integer> leases = ACTIVE_BRAIN_LEASES.get(body);
            if (leases == null) return;
            for (NodeCapabilities.ResourceUse resource : resources) {
                Integer references = leases.get(resource);
                if (references == null) continue;
                if (references <= 1) leases.remove(resource);
                else leases.put(resource, references - 1);
            }
            if (leases.isEmpty()) {
                ACTIVE_BRAIN_LEASES.remove(body);
                BRAIN_OWNERS.entrySet().removeIf(entry -> entry.getValue().get() == body);
            }
        }
    }

    private static EnumSet<NodeCapabilities.ResourceUse> normalizedResources(
            Set<NodeCapabilities.ResourceUse> resources) {
        EnumSet<NodeCapabilities.ResourceUse> result = EnumSet.noneOf(NodeCapabilities.ResourceUse.class);
        for (NodeCapabilities.ResourceUse resource : resources) {
            if (resource != NodeCapabilities.ResourceUse.NONE) result.add(resource);
        }
        return result;
    }

    private static EnumSet<Goal.Flag> goalFlags(Set<NodeCapabilities.ResourceUse> resources) {
        EnumSet<Goal.Flag> result = EnumSet.noneOf(Goal.Flag.class);
        for (NodeCapabilities.ResourceUse resource : resources) {
            switch (resource) {
                case MOVEMENT -> result.add(Goal.Flag.MOVE);
                case LOOK -> result.add(Goal.Flag.LOOK);
                case TARGET -> result.add(Goal.Flag.TARGET);
                case COMBAT -> result.addAll(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.TARGET));
                default -> {
                }
            }
        }
        return result;
    }

    private static Set<MemoryModuleType<?>> controlledMemories(Set<NodeCapabilities.ResourceUse> resources) {
        Set<MemoryModuleType<?>> result = new java.util.HashSet<>();
        for (NodeCapabilities.ResourceUse resource : resources) {
            switch (resource) {
                case MOVEMENT -> {
                    result.add(MemoryModuleType.WALK_TARGET);
                    result.add(MemoryModuleType.PATH);
                }
                case LOOK -> result.add(MemoryModuleType.LOOK_TARGET);
                case TARGET -> result.add(MemoryModuleType.ATTACK_TARGET);
                case COMBAT -> {
                    result.add(MemoryModuleType.WALK_TARGET);
                    result.add(MemoryModuleType.PATH);
                    result.add(MemoryModuleType.LOOK_TARGET);
                    result.add(MemoryModuleType.ATTACK_TARGET);
                    result.add(MemoryModuleType.ATTACK_COOLING_DOWN);
                }
                default -> {
                }
            }
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean invokeTryStart(BehaviorControl behavior, ServerLevel level,
                                          LivingEntity body, long timestamp) {
        return behavior.tryStart(level, body, timestamp);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invokeTickOrStop(BehaviorControl behavior, ServerLevel level,
                                         LivingEntity body, long timestamp) {
        behavior.tickOrStop(level, body, timestamp);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invokeStop(BehaviorControl behavior, ServerLevel level,
                                   LivingEntity body, long timestamp) {
        behavior.doStop(level, body, timestamp);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void eraseMemory(Brain brain, MemoryModuleType memory) {
        brain.eraseMemory(memory);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setMemory(Brain brain, MemoryModuleType memory, Object value) {
        brain.setMemory(memory, value);
    }

    private static final class GoalLease {
        private final Goal.Flag flag;
        private final SelectorLeases goals;
        private final SelectorLeases targets;
        private int references;

        private GoalLease(Goal.Flag flag, SelectorLeases goals, SelectorLeases targets,
                          Set<Goal.Flag> alreadyActive) {
            this.flag = flag;
            this.goals = goals;
            this.goals.acquire(flag);
            try {
                this.targets = targets;
                this.targets.acquire(flag);
            } catch (RuntimeException exception) {
                this.goals.release(flag, alreadyActive);
                throw exception;
            }
        }

        private void close(Set<Goal.Flag> stillActive) {
            targets.release(flag, stillActive);
            goals.release(flag, stillActive);
        }
    }

    private static final class SelectorLeases {
        private final GoalSelector selector;
        private final List<SavedGoal> displaced = new ArrayList<>();
        private final EnumMap<Goal.Flag, Goal> blockers = new EnumMap<>(Goal.Flag.class);

        private SelectorLeases(GoalSelector selector) {
            this.selector = selector;
        }

        private void acquire(Goal.Flag flag) {
            Goal blocker = blockingGoal(flag);
            List<WrappedGoal> conflicts = selector.getAvailableGoals().stream()
                    .filter(goal -> goal.getFlags().contains(flag))
                    .toList();
            List<SavedGoal> added = new ArrayList<>();
            try {
                for (WrappedGoal conflict : conflicts) {
                    if (blockers.containsValue(conflict.getGoal())
                            || containsDisplaced(conflict.getGoal())) continue;
                    SavedGoal saved = new SavedGoal(conflict.getPriority(), conflict.getGoal());
                    displaced.add(saved);
                    added.add(saved);
                    selector.removeGoal(conflict.getGoal());
                }
                selector.addGoal(Integer.MIN_VALUE, blocker);
                blockers.put(flag, blocker);
            } catch (RuntimeException exception) {
                selector.removeGoal(blocker);
                for (SavedGoal saved : added) {
                    if (!containsAvailable(saved.goal())) {
                        selector.addGoal(saved.priority(), saved.goal());
                    }
                    displaced.remove(saved);
                }
                throw exception;
            }
        }

        private void release(Goal.Flag flag, Set<Goal.Flag> stillActive) {
            Goal blocker = blockers.remove(flag);
            selector.removeGoal(blocker);
            var iterator = displaced.iterator();
            while (iterator.hasNext()) {
                SavedGoal saved = iterator.next();
                if (!java.util.Collections.disjoint(saved.goal().getFlags(), stillActive)) continue;
                selector.addGoal(saved.priority(), saved.goal());
                iterator.remove();
            }
        }

        private boolean containsDisplaced(Goal goal) {
            return displaced.stream().anyMatch(saved -> saved.goal() == goal);
        }

        private boolean containsAvailable(Goal goal) {
            return selector.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() == goal);
        }

        private static Goal blockingGoal(Goal.Flag flag) {
            return new Goal() {
                {
                    setFlags(EnumSet.of(flag));
                }

                @Override public boolean canUse() { return true; }
                @Override public boolean canContinueToUse() { return true; }
            };
        }
    }

    private record SavedGoal(int priority, Goal goal) {
    }
}
