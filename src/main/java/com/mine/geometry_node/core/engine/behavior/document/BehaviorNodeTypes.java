package com.mine.geometry_node.core.engine.behavior.document;

/** Stable ids for behavior-tree structural nodes. */
public final class BehaviorNodeTypes {
    public static final String ROOT = "geometry_node:behavior_root";
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
    public static final String PARENT_PORT = "behavior_parent";
    public static final String CHILDREN_PORT = "behavior_children";
    public static final String TICKS_PORT = "ticks";
    public static final String POLL_INTERVAL_PORT = "poll_interval";
    public static final String BLACKBOARD_KEY_PORT = "key";
    public static final String BLACKBOARD_VALUE_PORT = "value";

    private BehaviorNodeTypes() {
    }
}
