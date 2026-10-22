package com.mine.geometry_node.client.ui.components.integration.equipment;

import net.minecraft.ReportedException;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Render-thread failure latch. Tokens use identity, just like the published preview source. */
final class EquipmentPreviewPreparation {
    private static final long LOG_INTERVAL = Duration.ofSeconds(30).toNanos();
    private final LongSupplier clock;
    private final Consumer<RuntimeException> log;
    private Object failedSource;
    private boolean logged;
    private long lastLog;

    EquipmentPreviewPreparation(LongSupplier clock, Consumer<RuntimeException> log) {
        this.clock = clock;
        this.log = log;
    }

    void run(Object source, BooleanSupplier current, Runnable prepare, Runnable fallback) {
        if (failedSource == source) return;
        try {
            prepare.run();
            failedSource = null;
        } catch (RuntimeException failure) {
            if (failure instanceof ReportedException reported && reported.getCause() instanceof Error) throw failure;
            failedSource = source;
            if (!current.getAsBoolean()) return;
            fallback.run();
            long now = clock.getAsLong();
            if (!logged || now - lastLog >= LOG_INTERVAL) {
                logged = true;
                lastLog = now;
                log.accept(failure);
            }
        }
    }

    void reset() { failedSource = null; }
}
