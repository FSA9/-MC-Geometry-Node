package com.mine.geometry_node.core.engine.blueprint.event.subscription;

/** Tick-driven Blueprint capabilities aggregated across an entity's bound graphs. */
public final class EntityTickCapabilities {
    public static final int NONE = 0;
    public static final int AREA_EVENT = 1;
    public static final int ENTITY_TICK_EVENT = 1 << 1;

    private EntityTickCapabilities() {
    }

    public static boolean includes(int capabilities, int capability) {
        return (capabilities & capability) != 0;
    }
}
