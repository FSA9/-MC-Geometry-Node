package com.mine.geometry_node.core.engine.graph.runtime;

/** Controls how a bound graph process is closed after it stops receiving events. */
public enum GraphCloseMode {
    /** Abort all active, sleeping, and externally waiting execution immediately. */
    IMMEDIATE,

    /** Let existing execution finish, then destroy the process. */
    DRAIN
}
