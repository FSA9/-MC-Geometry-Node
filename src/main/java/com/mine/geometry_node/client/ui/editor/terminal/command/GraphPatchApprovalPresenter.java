package com.mine.geometry_node.client.ui.editor.terminal.command;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Trusted GeometryNode UI boundary for graph-write approval. */
@FunctionalInterface
public interface GraphPatchApprovalPresenter {
    CompletionStage<ApprovalOutcome> requestApproval(ApprovalSummary summary);

    enum ApprovalOutcome {
        APPROVED,
        REJECTED,
        DISMISSED,
        CANCELLED,
        HOST_DESTROYED,
        UI_UNAVAILABLE,
        ALREADY_PENDING
    }

    record ApprovalSummary(String approvalId, String patchHash, long expectedRevision,
                           int operationCount, List<String> changes) {
        public ApprovalSummary { changes = List.copyOf(changes == null ? List.of() : changes); }
    }
}
