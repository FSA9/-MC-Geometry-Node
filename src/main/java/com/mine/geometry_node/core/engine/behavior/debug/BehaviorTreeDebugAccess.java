package com.mine.geometry_node.core.engine.behavior.debug;

import java.util.UUID;

/** Lightweight immutable metadata used to authorize a full debug snapshot. */
public record BehaviorTreeDebugAccess(UUID instanceId, UUID ownerId, String dimension,
                                  double ownerX, double ownerY, double ownerZ,
                                  boolean positionKnown, boolean active) {
}
