package com.mine.geometry_node.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Authoritative limits for schematic files loaded by server-side graph actions. */
public final class SchematicServerConfig {
    private static final long MEBIBYTE = 1024L * 1024L;
    private static ModConfigSpec.IntValue maxFileSizeMiB;

    private SchematicServerConfig() {
    }

    public static void register(ModConfigSpec.Builder builder) {
        builder.push("schematic");
        maxFileSizeMiB = builder
                .comment("Maximum schematic source-file size in MiB for server-side graph actions.")
                .defineInRange("maxFileSizeMiB", 256, 1, 256);
        builder.pop();
    }

    public static long maxFileBytes() {
        if (maxFileSizeMiB == null) {
            throw new IllegalStateException("Schematic server config is not registered");
        }
        return maxFileSizeMiB.getAsInt() * MEBIBYTE;
    }
}
