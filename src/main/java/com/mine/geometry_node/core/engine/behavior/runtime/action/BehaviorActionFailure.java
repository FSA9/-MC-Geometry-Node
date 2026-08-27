package com.mine.geometry_node.core.engine.behavior.runtime.action;

import java.util.Objects;

/** Stable, queryable reason for an ordinary action failure. */
public record BehaviorActionFailure(String code, String detail) {
    public static final String MISSING_TARGET = "geometry_node:missing_target";
    public static final String INVALID_TARGET = "geometry_node:invalid_target";
    public static final String NO_CANDIDATE = "geometry_node:no_candidate";
    public static final String NO_PATH = "geometry_node:no_path";
    public static final String PATH_INTERRUPTED = "geometry_node:path_interrupted";
    public static final String REPATH_FAILED = "geometry_node:repath_failed";
    public static final String OUT_OF_RANGE = "geometry_node:out_of_range";
    public static final String CANNOT_ATTACK = "geometry_node:cannot_attack";
    public static final String COMMAND_REJECTED = "geometry_node:command_rejected";
    public static final String NO_DESTINATION = "geometry_node:no_destination";
    public static final String TARGET_LOST = "geometry_node:target_lost";
    public static final String ATTACK_REJECTED = "geometry_node:attack_rejected";

    public BehaviorActionFailure {
        Objects.requireNonNull(code, "code");
        if (code.isBlank() || code.indexOf(':') <= 0 || code.endsWith(":")) {
            throw new IllegalArgumentException("Action failure code must be a namespaced identifier");
        }
        detail = detail != null ? detail : "";
    }
}
