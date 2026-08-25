package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.ui.common.UiActionButton;
import com.mine.geometry_node.client.ui.editor.terminal.command.GraphPatchApprovalPresenter;
import com.mine.geometry_node.client.ui.shell.layer.OverlayCloseReason;
import com.mine.geometry_node.client.ui.shell.layer.modal.ModalWindowView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.concurrent.CompletableFuture;

/** Trusted modal shown outside PTY content for an MCP graph write. */
final class GraphPatchApprovalDialog extends ModalWindowView {
    private final CompletableFuture<GraphPatchApprovalPresenter.ApprovalOutcome> decision;
    private boolean decided;
    private OverlayCloseReason closeReason;

    GraphPatchApprovalDialog(Context context, GraphPatchApprovalPresenter.ApprovalSummary summary,
                             CompletableFuture<GraphPatchApprovalPresenter.ApprovalOutcome> decision) {
        super(context, "AI 蓝图修改确认", MovementMode.DRAGGABLE);
        this.decision = decision;
        setMinimumSizeDp(480, 300);
        setMaximumSizeDp(720, 620);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView warning = text(context,
                "此修改来自外部 Agent。请核对完整状态 Diff；批准会清空较早的 Undo/Redo 历史。"
                        + "终端中的确认文字不会批准本次操作。",
                13f, 0xFFFFC46B);
        content.addView(warning, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        StringBuilder details = new StringBuilder();
        details.append("Revision: ").append(summary.expectedRevision())
                .append("\nOperations: ").append(summary.operationCount())
                .append("\nPatch hash: ").append(summary.patchHash()).append("\n\n");
        for (String change : summary.changes()) details.append("• ").append(change).append('\n');
        TextView diff = text(context, details.toString(), 12f, 0xFFE0E0E0);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(diff, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(220));
        scrollParams.topMargin = UIUtils.dp2pxInt(12);
        content.addView(scroll, scrollParams);
        setContent(content);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        UiActionButton reject = UiActionButton.create(context, "拒绝", UiActionButton.Role.SECONDARY,
                ignored -> decide(GraphPatchApprovalPresenter.ApprovalOutcome.REJECTED));
        actions.addView(reject, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));
        UiActionButton approve = UiActionButton.create(context, "批准并应用", UiActionButton.Role.DANGER,
                ignored -> decide(GraphPatchApprovalPresenter.ApprovalOutcome.APPROVED));
        LinearLayout.LayoutParams approveParams = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(116), UIUtils.dp2pxInt(32));
        approveParams.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(approve, approveParams);
        setActions(actions);
    }

    private void decide(GraphPatchApprovalPresenter.ApprovalOutcome outcome) {
        if (decided) return;
        decided = true;
        decision.complete(outcome);
        requestClose();
    }

    @Override
    protected void onCloseRequested() {
        decide(GraphPatchApprovalPresenter.ApprovalOutcome.DISMISSED);
    }

    @Override
    protected void onWindowClosed(OverlayCloseReason reason) {
        closeReason = reason;
    }

    @Override
    protected void onWindowDestroyed() {
        if (decided || decision.isDone()) return;
        decided = true;
        GraphPatchApprovalPresenter.ApprovalOutcome outcome;
        if (closeReason == OverlayCloseReason.HOST_DESTROYED) {
            outcome = GraphPatchApprovalPresenter.ApprovalOutcome.HOST_DESTROYED;
        } else if (closeReason == OverlayCloseReason.PROGRAMMATIC) {
            outcome = GraphPatchApprovalPresenter.ApprovalOutcome.UI_UNAVAILABLE;
        } else {
            outcome = GraphPatchApprovalPresenter.ApprovalOutcome.DISMISSED;
        }
        decision.complete(outcome);
    }

    private static TextView text(Context context, String value, float size, int color) {
        TextView view = UIUtils.createLockedTextView(context, value, size, color);
        view.setTextIsSelectable(true);
        return view;
    }
}
