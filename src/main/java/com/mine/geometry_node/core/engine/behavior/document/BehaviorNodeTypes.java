package com.mine.geometry_node.core.engine.behavior.document;

/** Stable ids for behavior-tree structural nodes. */
public final class BehaviorNodeTypes {
    public static final String ROOT = "geometry_node:behavior_root";
    public static final String SUBTREE = "geometry_node:behavior_subtree";
    public static final String SEQUENCE = "geometry_node:behavior_sequence";
    public static final String SELECTOR = "geometry_node:behavior_selector";
    public static final String CONDITION = "geometry_node:behavior_condition";
    public static final String GUARD = "geometry_node:behavior_guard";
    public static final String INVERTER = "geometry_node:behavior_inverter";
    public static final String HAS_VALID_TARGET = "geometry_node:behavior_has_valid_target";
    public static final String WAIT = "geometry_node:behavior_wait";
    public static final String IDLE = "geometry_node:behavior_idle";
    public static final String GET_BLACKBOARD = "geometry_node:behavior_get_blackboard";
    public static final String HAS_BLACKBOARD = "geometry_node:behavior_has_blackboard";
    public static final String SET_BLACKBOARD = "geometry_node:behavior_set_blackboard";
    public static final String CLEAR_BLACKBOARD = "geometry_node:behavior_clear_blackboard";
    public static final String REACTIVE_SEQUENCE = "geometry_node:behavior_reactive_sequence";
    public static final String PRIORITY_SELECTOR = "geometry_node:behavior_priority_selector";
    public static final String REPEAT = "geometry_node:behavior_repeat";
    public static final String RETRY = "geometry_node:behavior_retry";
    public static final String TIMEOUT = "geometry_node:behavior_timeout";
    public static final String COOLDOWN = "geometry_node:behavior_cooldown";
    public static final String ALWAYS_SUCCEED = "geometry_node:behavior_always_succeed";
    public static final String ALWAYS_FAIL = "geometry_node:behavior_always_fail";
    public static final String BLACKBOARD_VALUE_CHANGED = "geometry_node:behavior_blackboard_value_changed";
    public static final String CAN_NAVIGATE_TO = "geometry_node:behavior_can_navigate_to";
    public static final String SELECT_TARGET = "geometry_node:behavior_select_target";
    public static final String CLEAR_TARGET = "geometry_node:behavior_clear_target";
    public static final String MOVE_TO = "geometry_node:behavior_move_to";
    public static final String STOP_MOVING = "geometry_node:behavior_stop_moving";
    public static final String WANDER = "geometry_node:behavior_wander";
    public static final String LOOK_AT = "geometry_node:behavior_look_at";
    public static final String ATTACK_TARGET = "geometry_node:behavior_attack_target";
    public static final String PARENT_PORT = "behavior_parent";
    public static final String CHILDREN_PORT = "behavior_children";
    public static final String TICKS_PORT = "ticks";
    public static final String POLL_INTERVAL_PORT = "poll_interval";
    public static final String RECHECK_INTERVAL_PORT = "recheck_interval";
    public static final String SCHEDULE_OFFSET_PORT = "schedule_offset";
    public static final String SUBTREE_ASSET_PORT = "subtree_asset";
    public static final String BLACKBOARD_KEY_PORT = "input";
    public static final String BLACKBOARD_SCOPE_PORT = "blackboard_scope";
    public static final String BLACKBOARD_VALUE_PORT = "value";
    public static final String COUNT_PORT = "count";
    public static final String RETRY_INTERVAL_PORT = "retry_interval";
    public static final String COOLDOWN_TICKS_PORT = "cooldown_ticks";
    public static final String CANDIDATES_PORT = "candidates";
    public static final String TARGET_PORT = "target";
    public static final String TARGET_MODE_PORT = "target_mode";
    public static final String TARGET_ENTITY_PORT = "target_entity";
    public static final String TARGET_POSITION_PORT = "target_position";
    public static final String TARGET_MODE_ENTITY = "entity";
    public static final String TARGET_MODE_POSITION = "position";
    public static final String SPEED_PORT = "speed";
    public static final String ARRIVAL_DISTANCE_PORT = "arrival_distance";
    public static final String HORIZONTAL_RANGE_PORT = "horizontal_range";
    public static final String VERTICAL_RANGE_PORT = "vertical_range";
    public static final String DURATION_PORT = "duration";
    public static final String ATTACK_RANGE_PORT = "attack_range";
    public static final String ATTACK_COOLDOWN_PORT = "attack_cooldown";

    public static String childPort(int index) {
        if (index < 1) throw new IllegalArgumentException("Behavior child index must be positive");
        return CHILDREN_PORT + "_" + index;
    }

    public static int childPortIndex(String portId) {
        if (CHILDREN_PORT.equals(portId)) return 0;
        String prefix = CHILDREN_PORT + "_";
        if (portId == null || !portId.startsWith(prefix)) return -1;
        try {
            int index = Integer.parseInt(portId.substring(prefix.length()));
            return index > 0 ? index - 1 : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static boolean isChildPort(String portId) {
        return childPortIndex(portId) >= 0;
    }

    private BehaviorNodeTypes() {
    }
}
