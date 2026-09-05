package com.mine.geometry_node.core.engine.blueprint.runtime;

/** Controls how a bound Blueprint process is closed after it stops receiving events. */
public enum BlueprintCloseMode {
    /** Abort all active, sleeping, and externally waiting execution immediately. */
    IMMEDIATE,

    /** Let existing execution finish, then destroy the process. */
    DRAIN
}
