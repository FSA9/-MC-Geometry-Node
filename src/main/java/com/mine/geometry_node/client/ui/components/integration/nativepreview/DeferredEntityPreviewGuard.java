package com.mine.geometry_node.client.ui.components.integration.nativepreview;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

import java.util.Objects;
import java.util.function.Consumer;

/** Opt-in recovery for deferred entity submission, before feature rendering touches shared GPU buffers. */
public final class DeferredEntityPreviewGuard {
    private static final ContextKey<Consumer<RuntimeException>> FAILURE = new ContextKey<>(
            Identifier.fromNamespaceAndPath("geometry_node", "entity_preview_submission_failure"));
    private static final ThreadLocal<Submission> ACTIVE = new ThreadLocal<>();

    private DeferredEntityPreviewGuard() {}

    /**
     * Mark a freshly extracted preview state before graphics.entity queues it. The callback runs
     * on the client/render thread and must check the source revision before changing the view.
     * Nothing is registered globally: discarded frames release their marker with the render state.
     */
    public static void mark(EntityRenderState state, Consumer<RuntimeException> onFailure) {
        state.setRenderData(FAILURE, Objects.requireNonNull(onFailure));
    }

    /** Called only by the GuiEntityRenderer integration. Other GUI and world states pass through. */
    public static void render(EntityRenderState state, SubmitNodeStorage storage, Runnable render) {
        Consumer<RuntimeException> onFailure = state.getRenderData(FAILURE);
        if (onFailure == null) {
            render.run();
            return;
        }
        state.setRenderData(FAILURE, null);
        Submission previous = ACTIVE.get();
        Submission submission = new Submission(state, storage);
        var orders = storage.getSubmitsPerOrder();
        // Preserve the actual collections, including pending unrelated nodes and their usage flags.
        var saved = new Int2ObjectAVLTreeMap<>(orders);
        orders.clear();
        ACTIVE.set(submission);
        try {
            render.run();
        } finally {
            try {
                storage.clear();
            } finally {
                orders.clear();
                orders.putAll(saved);
                if (previous == null) ACTIVE.remove();
                else ACTIVE.set(previous);
            }
        }
        // Notification errors and all errors outside submit deliberately propagate.
        if (submission.failure != null) onFailure.accept(submission.failure);
    }

    /** Recover only the marked state's own submission into its isolated node collections. */
    public static void submit(EntityRenderState state, SubmitNodeCollector collector, Runnable submit) {
        Submission submission = ACTIVE.get();
        if (submission == null || submission.state != state || submission.storage != collector) {
            submit.run();
            return;
        }
        try {
            submit.run();
        } catch (RuntimeException failure) {
            // Vanilla wraps even VM/linkage failures in ReportedException; those remain fatal.
            if (failure instanceof ReportedException reported && reported.getCause() instanceof Error) throw failure;
            submission.failure = failure;
        }
    }

    /** A failed submission must not execute any of its partial feature nodes. */
    public static void renderFeatures(SubmitNodeStorage storage, Runnable renderFeatures) {
        Submission submission = ACTIVE.get();
        if (submission == null || submission.storage != storage || submission.failure == null) renderFeatures.run();
    }

    private static final class Submission {
        final EntityRenderState state;
        final SubmitNodeStorage storage;
        RuntimeException failure;

        Submission(EntityRenderState state, SubmitNodeStorage storage) {
            this.state = state;
            this.storage = storage;
        }
    }
}
