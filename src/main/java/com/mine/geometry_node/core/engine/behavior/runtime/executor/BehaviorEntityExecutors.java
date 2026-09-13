package com.mine.geometry_node.core.engine.behavior.runtime.executor;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionFailure;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionStep;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorBudgetExceededException;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.engine.behavior.runtime.action.InstantBehaviorActionExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.navigation.BehaviorNavigationService;
import com.mine.geometry_node.core.engine.behavior.runtime.navigation.WanderCandidateProviders;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.behavior.entity.BehaviorMoveToNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Server-side executors for entity target, navigation, and look actions. */
public final class BehaviorEntityExecutors {
    private static final int MAX_TARGET_CANDIDATES = 4_096;
    private static final int WANDER_ATTEMPTS = 3;
    private static final double WANDER_ARRIVAL_DISTANCE = 1.0D;
    private static final long MOVE_REPATH_MIN_INTERVAL = 10L;
    private static final long MOVE_REPATH_FORCE_INTERVAL = 40L;
    private static final double MOVE_REPATH_DISTANCE_SQR = 1.0D;
    private static final long FOLLOW_IDLE_CHECK_INTERVAL = 5L;

    private BehaviorEntityExecutors() {
    }

    public static BehaviorNodeExecutor selectTarget() {
        return new SelectTargetExecutor();
    }

    public static BehaviorNodeExecutor clearTarget() {
        return new ClearTargetExecutor();
    }

    public static BehaviorNodeExecutor moveTo() {
        return new MoveToExecutor();
    }

    public static BehaviorNodeExecutor follow() {
        return new FollowExecutor();
    }

    public static BehaviorNodeExecutor stopMoving() {
        return new StopMovingExecutor();
    }

    public static BehaviorNodeExecutor wander() {
        return new WanderExecutor();
    }

    public static BehaviorNodeExecutor lookAt() {
        return new LookAtExecutor();
    }

    public static BehaviorNodeExecutor setAttackTarget() {
        return new AttackTargetExecutor();
    }

    public static BehaviorNodeExecutor canNavigateTo() {
        return new CanNavigateToExecutor();
    }

    private static final class SelectTargetExecutor extends InstantBehaviorActionExecutor {
        @Override
        protected BehaviorActionStep<Void> execute(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            Object candidates = context.input(StandardPorts.CANDIDATES.getId());
            if (candidates == null) return failure(BehaviorActionFailure.NO_CANDIDATE,
                    "Target candidates are unavailable");
            if (!(candidates instanceof Iterable<?> values)) {
                throw new BehaviorContractViolation("candidates must be iterable");
            }

            LivingEntity nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            int inspected = 0;
            for (Object value : values) {
                if (++inspected > MAX_TARGET_CANDIDATES) {
                    throw new BehaviorBudgetExceededException("Target candidate limit exceeded");
                }
                LivingEntity candidate = context.convertValue(value, LivingEntity.class);
                if (candidate == null
                        || !validAttackTarget(owner, candidate)) continue;
                double distance = owner.distanceToSqr(candidate);
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) return failure(BehaviorActionFailure.NO_CANDIDATE,
                    "No legal target candidate is available");
            return context.setAttackTarget(nearest) == nearest
                    ? BehaviorActionStep.success()
                    : failure(BehaviorActionFailure.COMMAND_REJECTED,
                    "The requested target was rejected or replaced");
        }
    }

    private static final class ClearTargetExecutor extends InstantBehaviorActionExecutor {
        @Override
        protected BehaviorActionStep<Void> execute(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            Optional<?> brainTarget = owner.getBrain()
                    .getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
            if (currentTarget(owner) == null
                    && (brainTarget == null || brainTarget.isEmpty())) {
                return BehaviorActionStep.success();
            }
            return context.setAttackTarget(null) == null
                    ? BehaviorActionStep.success()
                    : failure(BehaviorActionFailure.COMMAND_REJECTED,
                    "The target clear request was rejected");
        }
    }

    private static final class StopMovingExecutor extends InstantBehaviorActionExecutor {
        @Override
        protected BehaviorActionStep<Void> execute(BehaviorNodeContext context) {
            requireOwner(context).stopInPlace();
            return BehaviorActionStep.success();
        }
    }

    private static final class CanNavigateToExecutor extends InstantBehaviorActionExecutor {
        @Override
        protected BehaviorActionStep<Void> execute(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            EntityTarget resolution = resolveEntityTarget(
                    context, owner, StandardPorts.TARGET.getId());
            Entity target = resolution.target();
            if (target == null) return failure(resolution.supplied()
                            ? BehaviorActionFailure.INVALID_TARGET
                            : BehaviorActionFailure.MISSING_TARGET,
                    resolution.supplied() ? "Navigation target cannot be resolved"
                            : "Navigation target is unavailable");
            if (!validEntityTarget(owner, target)) {
                return failure(BehaviorActionFailure.INVALID_TARGET,
                        "Navigation target is dead, removed, or in another world");
            }
            BehaviorNavigationService.ProbeResult result = BehaviorNavigationService.INSTANCE
                    .probeReachability(owner, target, context.gameTick());
            return switch (result.status()) {
                case REACHABLE -> BehaviorActionStep.success();
                case UNREACHABLE -> failure(BehaviorActionFailure.NO_PATH,
                        "No complete path can be created to the target");
                case UNSUPPORTED -> failure(BehaviorActionFailure.NAVIGATION_PROBE_UNSUPPORTED,
                        "The active navigation type cannot be probed without mutation");
                case DEFERRED -> throw new IllegalStateException(
                        "Deferred navigation probes require a schedulable condition executor");
            };
        }
    }

    private static final class MoveToExecutor extends BehaviorActionExecutor<MoveState> {
        @Override
        protected BehaviorActionStep<MoveState> start(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            double speed = positiveFloat(context, StandardPorts.SPEED.getId());
            double arrival = nonNegativeFloat(context, StandardPorts.ARRIVAL_DISTANCE.getId());
            MoveTarget resolution = resolveMoveTarget(context, owner);
            Object target = resolution.target();
            if (target == null) return failure(resolution.supplied()
                            ? BehaviorActionFailure.INVALID_TARGET
                            : BehaviorActionFailure.MISSING_TARGET,
                    resolution.supplied() ? "Move target cannot be resolved"
                            : "Move target is unavailable");
            if (!validMoveTarget(owner, target)) return failure(BehaviorActionFailure.INVALID_TARGET,
                    "Move target is dead, removed, or in another world");

            Vec3 position = requireTargetPosition(target);
            if (withinDistance(owner, position, arrival)) {
                DebugRendererSessionManager.clearRequestedPathTarget(owner);
                return BehaviorActionStep.success();
            }
            DebugRendererSessionManager.recordRequestedPathTarget(owner, position);
            if (!startNavigation(owner, target, speed)) {
                return failure(BehaviorActionFailure.NO_PATH,
                        "No path can be started to the move target");
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(new MoveState(target, speed, arrival,
                    position, safeAdd(context.gameTick(), MOVE_REPATH_MIN_INTERVAL),
                    safeAdd(context.gameTick(), MOVE_REPATH_FORCE_INTERVAL)));
        }

        @Override
        protected BehaviorActionStep<MoveState> tick(BehaviorNodeContext context, MoveState state) {
            Mob owner = requireOwner(context);
            if (!validMoveTarget(owner, state.target())) {
                return failure(state, BehaviorActionFailure.TARGET_LOST,
                        "Move target became invalid");
            }
            Vec3 position = requireTargetPosition(state.target());
            DebugRendererSessionManager.recordRequestedPathTarget(owner, position);
            if (withinDistance(owner, position, state.arrivalDistance())) {
                owner.getNavigation().stop();
                DebugRendererSessionManager.clearRequestedPathTarget(owner);
                return BehaviorActionStep.success(state);
            }
            MoveState next = state;
            long tick = context.gameTick();
            boolean dynamicTarget = state.target() instanceof Entity;
            boolean targetMoved = dynamicTarget
                    && movedForRepath(state.plannedTargetPosition(), position);
            boolean repathDue = dynamicTarget
                    && tick >= state.nextAllowedRepathTick()
                    && (targetMoved || tick >= state.forcedRepathTick());
            if (owner.getNavigation().isDone() && !repathDue) {
                return failure(state, BehaviorActionFailure.PATH_INTERRUPTED,
                        "Navigation ended before the target was reached");
            }
            if (repathDue) {
                if (!startNavigation(owner, state.target(), state.speed())) {
                    return failure(state, BehaviorActionFailure.REPATH_FAILED,
                            "Dynamic target path could not be refreshed");
                }
                next = new MoveState(state.target(), state.speed(), state.arrivalDistance(),
                        position, safeAdd(tick, MOVE_REPATH_MIN_INTERVAL),
                        safeAdd(tick, MOVE_REPATH_FORCE_INTERVAL));
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(next);
        }

        @Override
        protected void stop(BehaviorNodeContext context, @Nullable MoveState state,
                            BehaviorTerminationReason reason) {
            Mob owner = owner(context);
            if (owner != null) owner.getNavigation().stop();
            if (owner != null) DebugRendererSessionManager.clearRequestedPathTarget(owner);
        }
    }

    private static final class FollowExecutor extends BehaviorActionExecutor<FollowState> {
        @Override
        protected BehaviorActionStep<FollowState> start(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            double speed = positiveFloat(context, StandardPorts.SPEED.getId());
            double minimumDistance = nonNegativeFloat(context, StandardPorts.MIN_DISTANCE.getId());
            double maximumDistance = nonNegativeFloat(context, StandardPorts.MAX_DISTANCE.getId());
            if (maximumDistance < minimumDistance) {
                throw new BehaviorContractViolation(
                        "max_distance cannot be less than min_distance");
            }

            EntityTarget resolution = resolveEntityTarget(
                    context, owner, StandardPorts.TARGET.getId());
            Entity target = resolution.target();
            if (target == null) return failure(resolution.supplied()
                            ? BehaviorActionFailure.INVALID_TARGET
                            : BehaviorActionFailure.MISSING_TARGET,
                    resolution.supplied() ? "Follow target cannot be resolved"
                            : "Follow target is unavailable");
            if (!validEntityTarget(owner, target)) {
                return failure(BehaviorActionFailure.INVALID_TARGET,
                        "Follow target is dead, removed, self, or in another world");
            }
            if (!withinDistance(owner, target.position(), maximumDistance)) {
                return failure(BehaviorActionFailure.OUT_OF_RANGE,
                        "Follow target is outside the maximum distance");
            }

            DebugRendererSessionManager.recordFollowTarget(owner, target);
            if (withinDistance(owner, target.position(), minimumDistance)) {
                owner.getNavigation().stop();
                context.requestWakeupAfter(FOLLOW_IDLE_CHECK_INTERVAL);
                return BehaviorActionStep.running(new FollowState(target, speed,
                        minimumDistance, maximumDistance, false,
                        target.position(), context.gameTick(), context.gameTick()));
            }
            DebugRendererSessionManager.recordRequestedPathTarget(owner, target.position());
            if (!startNavigation(owner, target, speed)) {
                return failure(BehaviorActionFailure.NO_PATH,
                        "No path can be started to the follow target");
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(new FollowState(target, speed,
                    minimumDistance, maximumDistance, true,
                    target.position(),
                    safeAdd(context.gameTick(), MOVE_REPATH_MIN_INTERVAL),
                    safeAdd(context.gameTick(), MOVE_REPATH_FORCE_INTERVAL)));
        }

        @Override
        protected BehaviorActionStep<FollowState> tick(BehaviorNodeContext context,
                                                       FollowState state) {
            Mob owner = requireOwner(context);
            Entity target = state.target();
            if (!validEntityTarget(owner, target)) {
                return failure(state, BehaviorActionFailure.TARGET_LOST,
                        "Follow target became invalid");
            }
            if (!withinDistance(owner, target.position(), state.maximumDistance())) {
                return failure(state, BehaviorActionFailure.OUT_OF_RANGE,
                        "Follow target moved outside the maximum distance");
            }

            DebugRendererSessionManager.recordFollowTarget(owner, target);
            if (withinDistance(owner, target.position(), state.minimumDistance())) {
                // Brain-driven mobs may submit a new vanilla path while Follow is waiting.
                owner.getNavigation().stop();
                DebugRendererSessionManager.clearRequestedPathTarget(owner);
                context.requestWakeupAfter(FOLLOW_IDLE_CHECK_INTERVAL);
                return BehaviorActionStep.running(state.withNavigation(false,
                        target.position(), context.gameTick(), context.gameTick()));
            }

            Vec3 targetPosition = target.position();
            DebugRendererSessionManager.recordRequestedPathTarget(owner, targetPosition);
            long tick = context.gameTick();
            boolean navigationUnavailable = !state.navigating()
                    || owner.getNavigation().isDone();
            boolean repathAllowed = !state.navigating()
                    || tick >= state.nextAllowedRepathTick();
            boolean mustRepath = repathAllowed
                    && (navigationUnavailable
                    || movedForRepath(state.plannedTargetPosition(), targetPosition)
                    || tick >= state.forcedRepathTick());
            FollowState next = state;
            if (mustRepath) {
                if (!startNavigation(owner, target, state.speed())) {
                    return failure(state, BehaviorActionFailure.REPATH_FAILED,
                            "Follow target path could not be refreshed");
                }
                next = state.withNavigation(true,
                        targetPosition, safeAdd(tick, MOVE_REPATH_MIN_INTERVAL),
                        safeAdd(tick, MOVE_REPATH_FORCE_INTERVAL));
            }
            if (navigationUnavailable && !repathAllowed) {
                context.requestWakeupAt(state.nextAllowedRepathTick());
            } else {
                context.requestWakeupAfter(1);
            }
            return BehaviorActionStep.running(next);
        }

        @Override
        protected void stop(BehaviorNodeContext context, @Nullable FollowState state,
                            BehaviorTerminationReason reason) {
            Mob owner = owner(context);
            if (owner == null) return;
            owner.getNavigation().stop();
            DebugRendererSessionManager.clearRequestedPathTarget(owner);
            DebugRendererSessionManager.clearFollowTarget(owner);
        }
    }

    private static final class WanderExecutor extends BehaviorActionExecutor<WanderState> {
        @Override
        protected BehaviorActionStep<WanderState> start(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            int horizontal = positiveInt(context, StandardPorts.HORIZONTAL_RANGE.getId());
            int vertical = nonNegativeInt(context, StandardPorts.VERTICAL_RANGE.getId());
            if (horizontal > 128 || vertical > 64) {
                throw new BehaviorContractViolation(
                        "Wander range exceeds 128 horizontal / 64 vertical");
            }
            double speed = positiveFloat(context, StandardPorts.SPEED.getId());
            owner.getNavigation().stop();
            return BehaviorActionStep.running(new WanderState(owner, speed, horizontal, vertical,
                    0, Long.MIN_VALUE, null,
                    Math.max(WANDER_ARRIVAL_DISTANCE, owner.getBbWidth())));
        }

        @Override
        protected BehaviorActionStep<WanderState> tick(BehaviorNodeContext context,
                                                       WanderState state) {
            Mob owner = requireOwner(context);
            if (state.owner() != owner || owner.isRemoved()) {
                return failure(state, BehaviorActionFailure.PATH_INTERRUPTED,
                        "Wander navigation owner became unavailable");
            }
            if (!state.navigating()) {
                return findDestination(context, owner, state);
            }
            Vec3 destination = state.destination();
            if (destination == null) {
                throw new BehaviorContractViolation("Wander navigation destination is missing");
            }
            if (withinDistance(owner, destination, state.arrivalDistance())) {
                owner.getNavigation().stop();
                return BehaviorActionStep.success(state);
            }
            if (owner.getNavigation().isDone()) {
                return failure(state, BehaviorActionFailure.PATH_INTERRUPTED,
                        "Wander path ended before its destination was reached");
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(state);
        }

        @Override
        protected void stop(BehaviorNodeContext context, @Nullable WanderState state,
                            BehaviorTerminationReason reason) {
            Mob owner = owner(context);
            if (state != null) state.owner().getNavigation().stop();
            if (owner != null && (state == null || state.owner() != owner)) {
                owner.getNavigation().stop();
            }
        }

        private BehaviorActionStep<WanderState> findDestination(BehaviorNodeContext context,
                                                                 Mob owner,
                                                                 WanderState state) {
            long tick = context.gameTick();
            if (state.lastAttemptTick() == tick) {
                context.requestWakeupAfter(1);
                return BehaviorActionStep.running(state);
            }

            Vec3 candidate = WanderCandidateProviders.findCandidate(owner,
                    state.horizontalRange(), state.verticalRange(), context.random());
            if (candidate != null) {
                BehaviorNavigationService.LivePathResult request =
                        BehaviorNavigationService.INSTANCE.requestLivePath(
                        owner, BlockPos.containing(candidate), 1);
                if (request.status() == BehaviorNavigationService.ProbeStatus.DEFERRED) {
                    context.requestWakeupAfter(1);
                    return BehaviorActionStep.running(
                            state.searching(state.attempts(), tick));
                }
                Path path = request.path();
                if (path != null && path.canReach()
                        && owner.getNavigation().moveTo(path, state.speed())) {
                    int attempts = state.attempts() + 1;
                    Vec3 destination = path.getEntityPosAtNode(owner, path.getNodeCount() - 1);
                    context.requestWakeupAfter(1);
                    return BehaviorActionStep.running(
                            state.navigating(attempts, tick, destination));
                }
            }

            int attempts = state.attempts() + 1;
            WanderState next = state.searching(attempts, tick);
            if (attempts >= WANDER_ATTEMPTS) {
                return failure(next, BehaviorActionFailure.NO_DESTINATION,
                        "No reachable wander destination was found");
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(next);
        }
    }

    private static final class LookAtExecutor extends BehaviorActionExecutor<LookState> {
        @Override
        protected BehaviorActionStep<LookState> start(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            int duration = nonNegativeInt(context, StandardPorts.TICK.getId());
            EntityTarget resolution = resolveEntityTarget(
                    context, owner, StandardPorts.TARGET.getId());
            Entity target = resolution.target();
            if (target == null) return failure(resolution.supplied()
                            ? BehaviorActionFailure.INVALID_TARGET
                            : BehaviorActionFailure.MISSING_TARGET,
                    resolution.supplied() ? "Look target cannot be resolved"
                            : "Look target is unavailable");
            if (!validEntityTarget(owner, target)) return failure(BehaviorActionFailure.INVALID_TARGET,
                    "Look target is dead, removed, or in another world");
            return BehaviorActionStep.running(
                    new LookState(target, safeAdd(context.gameTick(), duration)));
        }

        @Override
        protected BehaviorActionStep<LookState> tick(BehaviorNodeContext context, LookState state) {
            Mob owner = requireOwner(context);
            if (!validEntityTarget(owner, state.target())) {
                return failure(state, BehaviorActionFailure.TARGET_LOST,
                        "Look target became invalid");
            }
            owner.getLookControl().setLookAt(state.target());
            if (context.gameTick() >= state.deadline()) return BehaviorActionStep.success(state);
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(state);
        }
    }

    private static final class AttackTargetExecutor extends InstantBehaviorActionExecutor {
        @Override
        protected BehaviorActionStep<Void> execute(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            double range = nonNegativeFloat(context, StandardPorts.TARGET_RANGE.getId());
            EntityTarget resolution = resolveEntityTarget(
                    context, owner, StandardPorts.TARGET.getId());
            Entity resolved = resolution.target();
            if (resolved == null) return failure(resolution.supplied()
                            ? BehaviorActionFailure.INVALID_TARGET
                            : BehaviorActionFailure.MISSING_TARGET,
                    resolution.supplied() ? "Attack target cannot be resolved"
                            : "Attack target is unavailable");
            if (!(resolved instanceof LivingEntity target)) {
                return failure(BehaviorActionFailure.INVALID_TARGET,
                        "Attack target is not a living entity");
            }
            if (!validEntityTarget(owner, target)) return failure(BehaviorActionFailure.INVALID_TARGET,
                    "Attack target is dead, removed, or in another world");
            if (!owner.canAttack(target)) return failure(BehaviorActionFailure.CANNOT_ATTACK,
                    "The owner cannot attack this target");
            if (!withinDistance(owner, target.position(), range)) {
                return failure(BehaviorActionFailure.OUT_OF_RANGE,
                        "Target is outside the assignment range");
            }
            return context.setAttackTarget(target) == target
                    ? BehaviorActionStep.success()
                    : failure(BehaviorActionFailure.COMMAND_REJECTED,
                    "The requested attack target was rejected or replaced");
        }
    }

    private static EntityTarget resolveEntityTarget(BehaviorNodeContext context, Mob owner,
                                                    String port) {
        Object raw = context.input(port);
        if (raw == null) return new EntityTarget(currentTarget(owner), false);
        if (raw instanceof Entity entity) return new EntityTarget(entity, true);
        Entity converted = context.inputFromList(port, 0, Entity.class);
        if (converted != null) return new EntityTarget(converted, true);
        Object candidate = raw;
        if (raw instanceof List<?> values) {
            candidate = values.isEmpty() ? null : values.get(0);
        }
        if (candidate == null || candidate instanceof UUID
                || candidate instanceof String text && validUuid(text)) {
            return new EntityTarget(null, true);
        }
        throw new BehaviorContractViolation(port + " does not match Entity");
    }

    private static MoveTarget resolveMoveTarget(BehaviorNodeContext context, Mob owner) {
        String mode = context.optionalTypedInput(StandardPorts.TARGET_MODE.getId(), String.class);
        if (BehaviorMoveToNode.TARGET_MODE_POSITION.equals(mode)) {
            Object raw = context.input(StandardPorts.TARGET_POSITION.getId());
            Vec3 position = context.optionalTypedInput(
                    StandardPorts.TARGET_POSITION.getId(), Vec3.class);
            if (position != null && !finite(position)) {
                throw new BehaviorContractViolation("Move target position must be finite");
            }
            return new MoveTarget(position, raw != null);
        }
        if (mode != null && !mode.isBlank()
                && !BehaviorMoveToNode.TARGET_MODE_ENTITY.equals(mode)) {
            throw new BehaviorContractViolation("Unknown move target mode: " + mode);
        }
        EntityTarget target = resolveEntityTarget(
                context, owner, StandardPorts.TARGET_ENTITY.getId());
        return new MoveTarget(target.target(), target.supplied());
    }

    private static boolean startNavigation(Mob owner, Object target, double speed) {
        if (target instanceof Entity entity) return owner.getNavigation().moveTo(entity, speed);
        Vec3 position = requireTargetPosition(target);
        return owner.getNavigation().moveTo(position.x, position.y, position.z, speed);
    }

    private static boolean validMoveTarget(Mob owner, Object target) {
        return target instanceof Vec3 position ? finite(position)
                : target instanceof Entity entity && validEntityTarget(owner, entity);
    }

    private static boolean validEntityTarget(Mob owner, Entity target) {
        return target != owner && target.isAlive() && !target.isRemoved()
                && target.level() == owner.level();
    }

    private static boolean validAttackTarget(Mob owner, LivingEntity target) {
        return validEntityTarget(owner, target) && owner.canAttack(target);
    }

    private static Vec3 requireTargetPosition(Object target) {
        if (target instanceof Entity entity) return entity.position();
        if (target instanceof Vec3 position && finite(position)) return position;
        throw new BehaviorContractViolation("Move target has no finite position");
    }

    private static boolean finite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    private static boolean withinDistance(Entity entity, Vec3 target, double distance) {
        return entity.position().distanceToSqr(target) <= distance * distance;
    }

    private static boolean movedForRepath(Vec3 plannedPosition, Vec3 currentPosition) {
        return plannedPosition.distanceToSqr(currentPosition) >= MOVE_REPATH_DISTANCE_SQR;
    }

    private static boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Nullable
    private static LivingEntity currentTarget(Mob owner) {
        return owner.getTargetUnchecked();
    }

    private static Mob requireOwner(BehaviorNodeContext context) {
        Mob owner = owner(context);
        if (owner == null) throw new BehaviorContractViolation("Behavior owner is not a Mob");
        return owner;
    }

    @Nullable
    private static Mob owner(BehaviorNodeContext context) {
        return context.owner() instanceof Mob mob ? mob : null;
    }

    private static int positiveInt(BehaviorNodeContext context, String port) {
        Integer value = context.requiredInput(port, Integer.class);
        if (value <= 0) throw new BehaviorContractViolation(port + " must be positive");
        return value;
    }

    private static int nonNegativeInt(BehaviorNodeContext context, String port) {
        Integer value = context.requiredInput(port, Integer.class);
        if (value < 0) throw new BehaviorContractViolation(port + " cannot be negative");
        return value;
    }

    private static double positiveFloat(BehaviorNodeContext context, String port) {
        Float value = context.requiredInput(port, Float.class);
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new BehaviorContractViolation(port + " must be a positive finite number");
        }
        if (value > 16.0f) throw new BehaviorContractViolation(port + " cannot exceed 16");
        return value;
    }

    private static double nonNegativeFloat(BehaviorNodeContext context, String port) {
        Float value = context.requiredInput(port, Float.class);
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new BehaviorContractViolation(port + " must be a non-negative finite number");
        }
        return value;
    }

    private static long safeAdd(long tick, long delay) {
        return delay > Long.MAX_VALUE - tick ? Long.MAX_VALUE : tick + delay;
    }

    private static <S> BehaviorActionStep<S> failure(String code, String detail) {
        return BehaviorActionStep.failure(code, detail);
    }

    private static <S> BehaviorActionStep<S> failure(S state, String code, String detail) {
        return BehaviorActionStep.failure(state, code, detail);
    }

    private record MoveState(Object target, double speed, double arrivalDistance,
                             Vec3 plannedTargetPosition, long nextAllowedRepathTick,
                             long forcedRepathTick) {
    }

    private record FollowState(Entity target, double speed, double minimumDistance,
                               double maximumDistance, boolean navigating,
                               Vec3 plannedTargetPosition, long nextAllowedRepathTick,
                               long forcedRepathTick) {
        private FollowState withNavigation(boolean value, Vec3 plannedPosition,
                                           long nextAllowedTick, long forcedTick) {
            if (navigating == value
                    && plannedTargetPosition.equals(plannedPosition)
                    && nextAllowedRepathTick == nextAllowedTick
                    && forcedRepathTick == forcedTick) return this;
            return new FollowState(target, speed, minimumDistance, maximumDistance,
                    value, plannedPosition, nextAllowedTick, forcedTick);
        }
    }

    private record WanderState(Mob owner, double speed, int horizontalRange, int verticalRange,
                               int attempts, long lastAttemptTick,
                               @Nullable Vec3 destination, double arrivalDistance) {
        private boolean navigating() {
            return destination != null;
        }

        private WanderState searching(int value, long tick) {
            return new WanderState(owner, speed, horizontalRange, verticalRange,
                    value, tick, null, arrivalDistance);
        }

        private WanderState navigating(int value, long tick, Vec3 valueDestination) {
            return new WanderState(owner, speed, horizontalRange, verticalRange,
                    value, tick, valueDestination, arrivalDistance);
        }
    }

    private record LookState(Entity target, long deadline) {
    }

    private record EntityTarget(@Nullable Entity target, boolean supplied) {
    }

    private record MoveTarget(@Nullable Object target, boolean supplied) {
    }

}
