package com.mine.geometry_node.core.engine.graph.scoped;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side capacity policy for each scoped-state namespace. */
public final class ScopedStateServerConfig {
    public static final int DEFAULT_PRIVATE_MAX_ENTRIES = 1_024;
    public static final int DEFAULT_PUBLIC_MAX_ENTRIES = 8_192;
    public static final int HARD_MAX_ENTRIES = 65_536;

    private static ModConfigSpec.IntValue privateMaxEntries;
    private static ModConfigSpec.IntValue publicMaxEntries;

    private ScopedStateServerConfig() {
    }

    /** Adds this section to the mod's existing authoritative server config spec. */
    public static synchronized void register(ModConfigSpec.Builder builder) {
        if (privateMaxEntries != null || publicMaxEntries != null) {
            throw new IllegalStateException("Scoped-state server settings are already registered");
        }
        builder.push("scopedState");
        privateMaxEntries = builder
                .comment("Maximum entries in each private subsystem namespace bucket, such as shop data.")
                .defineInRange("privateNamespaceMaxEntries",
                        DEFAULT_PRIVATE_MAX_ENTRIES, 1, HARD_MAX_ENTRIES);
        publicMaxEntries = builder
                .comment("Maximum entries in each public scoped-state bucket used by graph nodes and blackboards.")
                .defineInRange("publicNamespaceMaxEntries",
                        DEFAULT_PUBLIC_MAX_ENTRIES, 1, HARD_MAX_ENTRIES);
        builder.pop();
    }

    public static int maxEntries(ScopedStateNamespace namespace) {
        return switch (namespace) {
            case PUBLIC -> requireRegistered(publicMaxEntries).getAsInt();
            case SHOP -> requireRegistered(privateMaxEntries).getAsInt();
        };
    }

    private static ModConfigSpec.IntValue requireRegistered(ModConfigSpec.IntValue value) {
        if (value == null) {
            throw new IllegalStateException("Scoped-state server settings are not registered");
        }
        return value;
    }
}
