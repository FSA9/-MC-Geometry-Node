package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionFailure;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionStep;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BehaviorPatrolNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_patrol";
    public static final String MODE_EXACT = "exact";
    public static final String MODE_REGION = "region";

    private static final String MIN_WAIT_PORT = StandardPorts.TICK.getIdWithIndex(1);
    private static final String MAX_WAIT_PORT = StandardPorts.TICK.getIdWithIndex(2);
    private static final int WAIT_NAVIGATION_CHECK_INTERVAL = 5;
    private static final BehaviorNodeExecutor EXECUTOR = new PatrolExecutor();

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return definition(instanceData);
    }

    private static NodeDef definition(@Nullable NodeData instanceData) {
        String mode = patrolMode(instanceData);
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_patrol"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .text("exact")
                        .text("region")
                        .text("waiting")
                        .text("lifecycle")
                        .build())
                .addRow(BehaviorEntityNodeSupport.parentRow())
                .addPassthroughInput(StandardPorts.LIST_XYZ.toInput(List.of()), UIHint.DEFAULT)
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.SPEED, 1.0f))
                .addPassthroughInput(StandardPorts.PATROL_MODE.toInput(mode).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, new String[]{MODE_EXACT, MODE_REGION},
                                PortMetaKeys.OPTION_LABELS,
                                new String[]{"geometry_node.behavior.patrol_mode.exact",
                                        "geometry_node.behavior.patrol_mode.region"}));
        if (MODE_REGION.equals(mode)) {
            builder.addRow(BehaviorEntityNodeSupport.input(StandardPorts.PATROL_RADIUS, 3.0f));
        } else {
            builder.addRow(BehaviorEntityNodeSupport.input(StandardPorts.ARRIVAL_DISTANCE, 1.5f));
        }
        return builder
                .addPassthroughInput(StandardPorts.TICK.toInputWithIndex(1, 0)
                        .withDisplayName("geometry_node.port.tick.patrol_wait_min"), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .addPassthroughInput(StandardPorts.TICK.toInputWithIndex(2, 0)
                        .withDisplayName("geometry_node.port.tick.patrol_wait_max"), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .addPassthroughInput(StandardPorts.LOOP_ENABLED.toInput(true), UIHint.CHECKBOX)
                .build();
    }

    private static String patrolMode(@Nullable NodeData instanceData) {
        Object value = instanceData != null
                ? instanceData.inputs.get(StandardPorts.PATROL_MODE.getId()) : null;
        return MODE_REGION.equals(value) ? MODE_REGION : MODE_EXACT;
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return EXECUTOR;
    }

    @Override
    public Set<Resource> requiredResources() {
        return Set.of(Resource.MOVEMENT);
    }

    private static final class PatrolExecutor extends BehaviorActionExecutor<PatrolState> {
        @Override
        protected BehaviorActionStep<PatrolState> start(BehaviorNodeContext context) {
            Mob owner = requireOwner(context);
            List<Vec3> waypoints = readWaypoints(context);
            double speed = positiveFloat(context, StandardPorts.SPEED.getId());
            PatrolMode mode = readMode(context);
            double tolerance = nonNegativeFloat(context, mode == PatrolMode.EXACT
                    ? StandardPorts.ARRIVAL_DISTANCE.getId()
                    : StandardPorts.PATROL_RADIUS.getId());
            int minimumWait = nonNegativeInt(context, MIN_WAIT_PORT);
            int maximumWait = nonNegativeInt(context, MAX_WAIT_PORT);
            if (maximumWait < minimumWait) {
                throw new BehaviorContractViolation(
                        "Maximum patrol wait cannot be less than minimum patrol wait");
            }
            Boolean loop = context.requiredInput(StandardPorts.LOOP_ENABLED.getId(), Boolean.class);
            PatrolState state = new PatrolState(owner, waypoints, 0, speed, mode,
                    tolerance, minimumWait, maximumWait, loop, Phase.PLANNING, 0L);
            DebugRendererSessionManager.recordPatrolRoute(owner, waypoints, 0, loop);
            return planCurrentWaypoint(context, state);
        }

        @Override
        protected BehaviorActionStep<PatrolState> tick(BehaviorNodeContext context,
                                                       PatrolState state) {
            Mob owner = requireOwner(context);
            if (state.owner() != owner || state.owner().isRemoved()
                    || state.owner().level() != owner.level()) {
                return failure(state, BehaviorActionFailure.PATH_INTERRUPTED,
                        "Patrol navigation owner became unavailable");
            }
            return switch (state.phase()) {
                case PLANNING -> planCurrentWaypoint(context, state);
                case NAVIGATING -> updateNavigation(context, state);
                case WAITING -> updateWaiting(context, state);
            };
        }

        @Override
        protected void stop(BehaviorNodeContext context, @Nullable PatrolState state,
                            BehaviorTerminationReason reason) {
            Mob owner = state != null ? state.owner()
                    : context.owner() instanceof Mob mob ? mob : null;
            if (owner == null) return;
            owner.getNavigation().stop();
            DebugRendererSessionManager.clearRequestedPathTarget(owner);
            DebugRendererSessionManager.clearPatrolRoute(owner);
        }

        private static BehaviorActionStep<PatrolState> planCurrentWaypoint(
                BehaviorNodeContext context, PatrolState state) {
            Vec3 waypoint = state.currentWaypoint();
            DebugRendererSessionManager.recordRequestedPathTarget(state.owner(), waypoint);
            if (withinDistance(state.owner(), waypoint, state.tolerance())) {
                return arrive(context, state);
            }

            int accuracy = state.mode() == PatrolMode.EXACT
                    ? 1 : Math.max(1, (int) Math.ceil(state.tolerance()));
            Path path = state.owner().getNavigation().createPath(
                    BlockPos.containing(waypoint), accuracy);
            if (!acceptablePath(state, path, waypoint)
                    || !state.owner().getNavigation().moveTo(path, state.speed())) {
                return failure(state, BehaviorActionFailure.NO_PATH,
                        "No acceptable path can be created to patrol waypoint " + state.index());
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(state.withPhase(Phase.NAVIGATING, 0L));
        }

        private static BehaviorActionStep<PatrolState> updateNavigation(
                BehaviorNodeContext context, PatrolState state) {
            Vec3 waypoint = state.currentWaypoint();
            DebugRendererSessionManager.recordRequestedPathTarget(state.owner(), waypoint);
            if (withinDistance(state.owner(), waypoint, state.tolerance())) {
                return arrive(context, state);
            }
            if (state.owner().getNavigation().isDone()) {
                return failure(state, BehaviorActionFailure.PATH_INTERRUPTED,
                        "Patrol path ended before waypoint " + state.index() + " was reached");
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(state);
        }

        private static BehaviorActionStep<PatrolState> updateWaiting(
                BehaviorNodeContext context, PatrolState state) {
            // Brain-driven mobs may submit native movement again while the action is waiting.
            state.owner().getNavigation().stop();
            if (context.gameTick() < state.waitDeadline()) {
                context.requestWakeupAt(Math.min(state.waitDeadline(),
                        safeAdd(context.gameTick(), WAIT_NAVIGATION_CHECK_INTERVAL)));
                return BehaviorActionStep.running(state);
            }
            return advance(context, state);
        }

        private static BehaviorActionStep<PatrolState> arrive(
                BehaviorNodeContext context, PatrolState state) {
            state.owner().getNavigation().stop();
            DebugRendererSessionManager.clearRequestedPathTarget(state.owner());
            DebugRendererSessionManager.recordPatrolRoute(state.owner(), state.waypoints(),
                    state.index() + 1, state.loop());
            long wait = randomWait(context, state.minimumWait(), state.maximumWait());
            if (wait == 0L) return advance(context, state);
            long deadline = safeAdd(context.gameTick(), wait);
            context.requestWakeupAt(deadline);
            return BehaviorActionStep.running(state.withPhase(Phase.WAITING, deadline));
        }

        private static BehaviorActionStep<PatrolState> advance(
                BehaviorNodeContext context, PatrolState state) {
            int next = state.index() + 1;
            if (next >= state.waypoints().size()) {
                if (!state.loop()) return BehaviorActionStep.success(state);
                next = 0;
            }
            context.requestWakeupAfter(1);
            return BehaviorActionStep.running(state.withIndex(next));
        }

        private static boolean acceptablePath(PatrolState state, @Nullable Path path,
                                              Vec3 waypoint) {
            if (path == null || path.getNodeCount() == 0) return false;
            if (state.mode() == PatrolMode.EXACT) return path.canReach();
            Vec3 endpoint = path.getEntityPosAtNode(state.owner(), path.getNodeCount() - 1);
            return endpoint.distanceToSqr(waypoint) <= state.tolerance() * state.tolerance();
        }
    }

    private static List<Vec3> readWaypoints(BehaviorNodeContext context) {
        Object raw = context.input(StandardPorts.LIST_XYZ.getId());
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new BehaviorContractViolation("Patrol requires at least one waypoint");
        }
        List<Vec3> waypoints = new ArrayList<>(values.size());
        for (Object value : values) {
            Vec3 waypoint = TypeConverter.convert(value, Vec3.class, null);
            if (waypoint == null || !finite(waypoint)) {
                throw new BehaviorContractViolation("Patrol waypoint does not match XYZ");
            }
            waypoints.add(waypoint);
        }
        return List.copyOf(waypoints);
    }

    private static PatrolMode readMode(BehaviorNodeContext context) {
        String mode = context.requiredInput(StandardPorts.PATROL_MODE.getId(), String.class);
        return switch (mode) {
            case MODE_EXACT -> PatrolMode.EXACT;
            case MODE_REGION -> PatrolMode.REGION;
            default -> throw new BehaviorContractViolation("Unknown patrol mode: " + mode);
        };
    }

    private static Mob requireOwner(BehaviorNodeContext context) {
        if (context.owner() instanceof Mob mob) return mob;
        throw new BehaviorContractViolation("Behavior owner is not a Mob");
    }

    private static int nonNegativeInt(BehaviorNodeContext context, String port) {
        Integer value = context.requiredInput(port, Integer.class);
        if (value < 0) throw new BehaviorContractViolation(port + " cannot be negative");
        return value;
    }

    private static double positiveFloat(BehaviorNodeContext context, String port) {
        Float value = context.requiredInput(port, Float.class);
        if (!Float.isFinite(value) || value <= 0.0f || value > 16.0f) {
            throw new BehaviorContractViolation(port + " must be finite and within (0, 16]");
        }
        return value;
    }

    private static double nonNegativeFloat(BehaviorNodeContext context, String port) {
        Float value = context.requiredInput(port, Float.class);
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new BehaviorContractViolation(port + " must be a non-negative finite number");
        }
        return value;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static boolean withinDistance(Mob owner, Vec3 target, double distance) {
        return owner.position().distanceToSqr(target) <= distance * distance;
    }

    private static long randomWait(BehaviorNodeContext context, int minimum, int maximum) {
        if (minimum == maximum) return minimum;
        long bound = (long) maximum - minimum + 1L;
        return minimum + Math.floorMod(context.random().nextLong(), bound);
    }

    private static long safeAdd(long tick, long delay) {
        return delay > Long.MAX_VALUE - tick ? Long.MAX_VALUE : tick + delay;
    }

    private static <S> BehaviorActionStep<S> failure(S state, String code, String detail) {
        return BehaviorActionStep.failure(state, code, detail);
    }

    private enum PatrolMode {
        EXACT,
        REGION
    }

    private enum Phase {
        PLANNING,
        NAVIGATING,
        WAITING
    }

    private record PatrolState(Mob owner, List<Vec3> waypoints, int index, double speed,
                               PatrolMode mode, double tolerance, int minimumWait,
                               int maximumWait, boolean loop, Phase phase,
                               long waitDeadline) {
        private Vec3 currentWaypoint() {
            return waypoints.get(index);
        }

        private PatrolState withIndex(int value) {
            return new PatrolState(owner, waypoints, value, speed, mode, tolerance,
                    minimumWait, maximumWait, loop, Phase.PLANNING, 0L);
        }

        private PatrolState withPhase(Phase value, long deadline) {
            return new PatrolState(owner, waypoints, index, speed, mode, tolerance,
                    minimumWait, maximumWait, loop, value, deadline);
        }
    }
}
