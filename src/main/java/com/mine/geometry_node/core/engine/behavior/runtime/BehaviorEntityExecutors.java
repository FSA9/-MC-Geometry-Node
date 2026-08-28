package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionFailure;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionStep;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorBudgetExceededException;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.engine.behavior.runtime.action.InstantBehaviorActionExecutor;
import com.mine.geometry_node.core.engine.blueprint.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.behavior.condition.BehaviorUtilityConditionNode;
import com.mine.geometry_node.core.node.nodes.behavior.entity.BehaviorEntityActionNode;
import com.mine.geometry_node.mixin.MobNavigationInvoker;
import com.mine.geometry_node.mixin.PathNavigationAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** Server-side executors for entity target, navigation, and look actions. */
final class BehaviorEntityExecutors {
    private static final int MAX_TARGET_CANDIDATES = 4_096;
    private static final int WANDER_ATTEMPTS = 10;
    private static final double WANDER_ARRIVAL_DISTANCE = 1.0D;
    private static final long MOVE_REPATH_INTERVAL = 10L;

    private BehaviorEntityExecutors() {
    }

    static void register(BehaviorNodeExecutorRegistry registry) {
        registry.register(BehaviorEntityActionNode.Kind.SELECT_TARGET.typeId(), new SelectTargetExecutor());
        registry.register(BehaviorEntityActionNode.Kind.CLEAR_TARGET.typeId(), new ClearTargetExecutor());
        registry.register(BehaviorEntityActionNode.Kind.MOVE_TO.typeId(), new MoveToExecutor());
        registry.register(BehaviorEntityActionNode.Kind.STOP_MOVING.typeId(), new StopMovingExecutor());
        registry.register(BehaviorEntityActionNode.Kind.WANDER.typeId(), new WanderExecutor());
        registry.register(BehaviorEntityActionNode.Kind.LOOK_AT.typeId(), new LookAtExecutor());
        registry.register(BehaviorEntityActionNode.Kind.ATTACK_TARGET.typeId(), new AttackTargetExecutor());
        registry.register(BehaviorUtilityConditionNode.Kind.CAN_NAVIGATE_TO.typeId(), new CanNavigateToExecutor());
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
                if (!(value instanceof LivingEntity candidate)
                        || !validAttackTarget(owner, candidate)) continue;
                double distance = owner.distanceToSqr(candidate);
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) return failure(BehaviorActionFailure.NO_CANDIDATE,
                    "No legal target candidate is available");
            return setTarget(owner, nearest) == nearest
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
            return setTarget(owner, null) == null
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
            PathNavigation probe = isolatedNavigation(owner);
            Path path = probe.createPath(target, 1);
            return path != null && path.canReach()
                    ? BehaviorActionStep.success()
                    : failure(BehaviorActionFailure.NO_PATH,
                    "No complete path can be created to the target");
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
                    safeAdd(context.gameTick(), MOVE_REPATH_INTERVAL)));
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
            if (owner.getNavigation().isDone()) {
                return failure(state, BehaviorActionFailure.PATH_INTERRUPTED,
                        "Navigation ended before the target was reached");
            }
            MoveState next = state;
            if (state.target() instanceof Entity && context.gameTick() >= state.nextRepathTick()) {
                if (!startNavigation(owner, state.target(), state.speed())) {
                    return failure(state, BehaviorActionFailure.REPATH_FAILED,
                            "Dynamic target path could not be refreshed");
                }
                next = new MoveState(state.target(), state.speed(), state.arrivalDistance(),
                        safeAdd(context.gameTick(), MOVE_REPATH_INTERVAL));
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(next);
        }

        @Override
        protected void stop(BehaviorNodeContext context, @Nullable MoveState state,
                            BehaviorTerminationReason reason) {
            Mob owner = owner(context);
            if (owner != null) owner.getNavigation().stop();
            if (owner != null && reason != BehaviorTerminationReason.COMPLETED_FAILURE) {
                DebugRendererSessionManager.clearRequestedPathTarget(owner);
            }
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
            Mob navigator = navigationOwner(owner);
            PathNavigation probe = isolatedNavigation(owner);
            BlockPos origin = navigator.blockPosition();
            for (int attempt = 0; attempt < WANDER_ATTEMPTS; attempt++) {
                BlockPos candidate = origin.offset(
                        context.random().nextInt(horizontal * 2 + 1) - horizontal,
                        context.random().nextInt(vertical * 2 + 1) - vertical,
                        context.random().nextInt(horizontal * 2 + 1) - horizontal);
                if (candidate.equals(origin)) continue;
                Path path = probe.createPath(candidate, 1);
                if (path == null || !path.canReach()
                        || !owner.getNavigation().moveTo(path, speed)) continue;
                Vec3 destination = path.getEntityPosAtNode(
                        navigator, path.getNodeCount() - 1);
                context.requestWakeupAfter(1);
                return BehaviorActionStep.running(new WanderState(navigator, destination,
                        Math.max(WANDER_ARRIVAL_DISTANCE, navigator.getBbWidth())));
            }
            return failure(BehaviorActionFailure.NO_DESTINATION,
                    "No reachable wander destination was found");
        }

        @Override
        protected BehaviorActionStep<WanderState> tick(BehaviorNodeContext context,
                                                       WanderState state) {
            Mob owner = requireOwner(context);
            if (state.navigator().isRemoved()
                    || state.navigator().level() != owner.level()) {
                return failure(state, BehaviorActionFailure.PATH_INTERRUPTED,
                        "Wander navigation owner became unavailable");
            }
            if (withinDistance(state.navigator(), state.destination(), state.arrivalDistance())) {
                state.navigator().getNavigation().stop();
                return BehaviorActionStep.success(state);
            }
            if (state.navigator().getNavigation().isDone()) {
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
            if (state != null) state.navigator().getNavigation().stop();
            else if (owner != null) owner.getNavigation().stop();
        }
    }

    private static final class LookAtExecutor extends BehaviorActionExecutor<LookState> {
        @Override
        protected BehaviorActionStep<LookState> start(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            int duration = nonNegativeInt(context, StandardPorts.DURATION.getId());
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
            return setTarget(owner, target) == target
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
        Entity converted = context.input(port, Entity.class);
        if (converted != null) return new EntityTarget(converted, true);
        if (raw instanceof UUID || raw instanceof String text && validUuid(text)) {
            return new EntityTarget(null, true);
        }
        throw new BehaviorContractViolation(port + " does not match Entity");
    }

    private static MoveTarget resolveMoveTarget(BehaviorNodeContext context, Mob owner) {
        String mode = context.optionalTypedInput(StandardPorts.TARGET_MODE.getId(), String.class);
        if (BehaviorEntityActionNode.TARGET_MODE_POSITION.equals(mode)) {
            Object raw = context.input(StandardPorts.TARGET_POSITION.getId());
            Vec3 position = context.optionalTypedInput(
                    StandardPorts.TARGET_POSITION.getId(), Vec3.class);
            if (position != null && !finite(position)) {
                throw new BehaviorContractViolation("Move target position must be finite");
            }
            return new MoveTarget(position, raw != null);
        }
        if (mode != null && !mode.isBlank()
                && !BehaviorEntityActionNode.TARGET_MODE_ENTITY.equals(mode)) {
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

    private static PathNavigation isolatedNavigation(Mob owner) {
        Mob navigator = navigationOwner(owner);
        PathNavigation live = navigator.getNavigation();
        PathNavigation probe = ((MobNavigationInvoker) (Object) navigator)
                .geometryNode$createNavigation(navigator.level());
        NodeEvaluator source = live.getNodeEvaluator();
        NodeEvaluator target = probe.getNodeEvaluator();
        target.setCanFloat(source.canFloat());
        target.setCanOpenDoors(source.canOpenDoors());
        target.setCanPassDoors(source.canPassDoors());
        target.setCanWalkOverFences(source.canWalkOverFences());
        PathNavigationAccessor tuning = (PathNavigationAccessor) live;
        probe.setMaxVisitedNodesMultiplier(tuning.geometryNode$getMaxVisitedNodesMultiplier());
        probe.setRequiredPathLength(tuning.geometryNode$getRequiredPathLength());
        return probe;
    }

    private static Mob navigationOwner(Mob owner) {
        return owner.getControlledVehicle() instanceof Mob riding ? riding : owner;
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

    @Nullable
    private static LivingEntity setTarget(Mob owner, @Nullable LivingEntity target) {
        owner.setTarget(target);
        LivingEntity actual = owner.getTargetUnchecked();
        if (owner.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET) != null) {
            BehaviorNativeAiController.setControlledMemory(
                    owner.getBrain(), MemoryModuleType.ATTACK_TARGET, actual);
        }
        return actual;
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
                             long nextRepathTick) {
    }

    private record WanderState(Mob navigator, Vec3 destination, double arrivalDistance) {
    }

    private record LookState(Entity target, long deadline) {
    }

    private record EntityTarget(@Nullable Entity target, boolean supplied) {
    }

    private record MoveTarget(@Nullable Object target, boolean supplied) {
    }
}
