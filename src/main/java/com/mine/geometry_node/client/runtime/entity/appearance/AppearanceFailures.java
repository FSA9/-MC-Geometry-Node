package com.mine.geometry_node.client.runtime.entity.appearance;

import net.minecraft.ReportedException;

/** Vanilla may wrap VM/linkage Errors as RuntimeException; those must never become visual fallbacks. */
final class AppearanceFailures {
    private AppearanceFailures() {}

    static void rethrowFatal(RuntimeException failure) {
        if (failure instanceof ReportedException reported && reported.getCause() instanceof Error) throw failure;
    }
}
