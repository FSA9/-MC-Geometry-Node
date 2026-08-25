package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.editor.terminal.command.GraphPatchApprovalPresenter;
import com.mine.geometry_node.client.ui.shell.layer.MainUiLayerManager;
import com.mine.geometry_node.client.ui.shell.layer.OverlayCloseReason;
import com.mine.geometry_node.client.ui.shell.layer.OverlayHandle;
import com.mine.geometry_node.client.ui.shell.layer.modal.ModalOptions;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Owns the single trusted graph approval belonging to one terminal tab. */
final class GraphPatchApprovalController implements GraphPatchApprovalPresenter, AutoCloseable {
    private final Context context;
    private final String runId;
    private MainUiLayerManager layerManager;
    private PendingApproval pending;
    private Runnable focusRestorer;
    private boolean closed;

    GraphPatchApprovalController(Context context, String runId) {
        this.context = Objects.requireNonNull(context, "context");
        this.runId = Objects.requireNonNull(runId, "runId");
    }

    void bind(MainUiLayerManager manager) {
        if (!closed) layerManager = Objects.requireNonNull(manager, "manager");
    }

    void setFocusRestorer(Runnable focusRestorer) {
        if (!closed) this.focusRestorer = Objects.requireNonNull(focusRestorer, "focusRestorer");
    }

    @Override
    public CompletableFuture<ApprovalOutcome> requestApproval(ApprovalSummary summary) {
        Objects.requireNonNull(summary, "summary");
        CompletableFuture<ApprovalOutcome> decision = new CompletableFuture<>();
        try {
            Runnable showTask = () -> showApproval(summary, decision);
            if (Core.isOnUiThread()) {
                showTask.run();
            } else if (!Core.getUiHandlerAsync().post(showTask)) {
                GeometryNode.LOGGER.warn("Graph approval UI queue unavailable: approval={}, run={}",
                        summary.approvalId(), runId);
                decision.complete(ApprovalOutcome.UI_UNAVAILABLE);
            }
        } catch (RuntimeException failure) {
            GeometryNode.LOGGER.error("Failed to schedule graph approval UI: approval={}, run={}",
                    summary.approvalId(), runId, failure);
            decision.complete(ApprovalOutcome.UI_UNAVAILABLE);
        }
        return decision;
    }

    private void showApproval(ApprovalSummary summary, CompletableFuture<ApprovalOutcome> decision) {
        if (decision.isDone()) return;
        if (closed) {
            decision.complete(ApprovalOutcome.CANCELLED);
            return;
        }
        if (pending != null && !pending.decision.isDone()) {
            decision.complete(ApprovalOutcome.ALREADY_PENDING);
            return;
        }
        if (layerManager == null || layerManager.isClosed()) {
            GeometryNode.LOGGER.warn("Graph approval UI unavailable: approval={}, run={}, managerBound={}",
                    summary.approvalId(), runId, layerManager != null);
            decision.complete(ApprovalOutcome.UI_UNAVAILABLE);
            return;
        }

        GraphPatchApprovalDialog dialog = null;
        OverlayHandle handle = null;
        PendingApproval created = null;
        try {
            dialog = new GraphPatchApprovalDialog(context, summary, decision);
            handle = layerManager.showModal(dialog,
                    new ModalOptions(false, false, null));
            created = new PendingApproval(summary.approvalId(), decision, handle);
            pending = created;
            PendingApproval mountedApproval = created;
            GraphPatchApprovalDialog mountedDialog = dialog;
            decision.whenComplete((outcome, failure) ->
                    mountedDialog.post(() -> finish(mountedApproval, outcome, failure)));
            GeometryNode.LOGGER.info("Graph approval shown: approval={}, run={}, modalCount={}",
                    summary.approvalId(), runId, layerManager.modalCount());
        } catch (RuntimeException failure) {
            if (pending == created) pending = null;
            try {
                if (handle != null && handle.isOpen()) {
                    handle.requestClose(OverlayCloseReason.PROGRAMMATIC);
                }
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            GeometryNode.LOGGER.error("Failed to show graph approval: approval={}, run={}",
                    summary.approvalId(), runId, failure);
            decision.complete(ApprovalOutcome.UI_UNAVAILABLE);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        PendingApproval active = pending;
        pending = null;
        if (active != null) {
            active.decision.complete(ApprovalOutcome.CANCELLED);
            active.handle.requestClose(OverlayCloseReason.PROGRAMMATIC);
        }
        layerManager = null;
    }

    private void finish(PendingApproval approval, ApprovalOutcome outcome, Throwable failure) {
        if (pending == approval) pending = null;
        approval.handle.requestClose(OverlayCloseReason.PROGRAMMATIC);
        Runnable restore = focusRestorer;
        if (restore != null) {
            try {
                restore.run();
            } catch (RuntimeException restoreFailure) {
                GeometryNode.LOGGER.warn("Failed to restore terminal focus after graph approval: approval={}, run={}",
                        approval.approvalId, runId, restoreFailure);
            }
        }
        if (approval.decision.isCancelled()) {
            GeometryNode.LOGGER.info("Graph approval cancelled: approval={}, run={}",
                    approval.approvalId, runId);
        } else if (failure != null) {
            GeometryNode.LOGGER.warn("Graph approval completed exceptionally: approval={}, run={}",
                    approval.approvalId, runId, failure);
        } else {
            GeometryNode.LOGGER.info("Graph approval completed: approval={}, run={}, outcome={}, modalCount={}",
                    approval.approvalId, runId, outcome,
                    layerManager == null ? -1 : layerManager.modalCount());
        }
    }

    private record PendingApproval(String approvalId, CompletableFuture<ApprovalOutcome> decision,
                                   OverlayHandle handle) {
    }
}
