package com.mine.geometry_node.client.ui.components.integration.asset.dialog;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

public class TransferProgressDialog extends AssetDialogBase {
    private final TextView mStatus;
    private final TextView mProgress;
    private final UiActionButton mCancelButton;
    private Runnable mOnCancel;
    private boolean mFinished;
    private boolean mCommitting;

    public TransferProgressDialog(Context context, String title) {
        super(context, title);
        mStatus = label(context, tr("geometry_node.asset_transfer.status.preparing"), 13, UiThemes.EDITOR.text().normal());
        mProgress = label(context, "0 / 0", 13, UiThemes.EDITOR.status().info());
        mPanel.addView(mStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32)));
        mPanel.addView(mProgress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        mCancelButton = actionButton(context, tr("geometry_node.common.cancel"), UiActionButton.Role.SECONDARY);
        mCancelButton.setOnClickListener(v -> onCloseRequested());
        actions.addView(mCancelButton, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));
        setActions(actions);
    }
    public void setOnCancel(Runnable onCancel) { mOnCancel = onCancel; }
    public void update(String message, int processed, int total) {
        mStatus.setText(message == null || message.isEmpty() ? tr("geometry_node.asset_transfer.status.processing") : message);
        mProgress.setText(total > 0 ? processed + " / " + total : String.valueOf(processed));
    }
    /** Terminal success is explicit: progress text/counts may describe work awaiting commit. */
    public void complete(String message, int processed, int total) {
        update(message, processed, total);
        finish();
        requestClose();
    }
    public void enterCommitPhase() { mCommitting = true; mCancelButton.setEnabled(false); mCancelButton.setText(tr("geometry_node.asset_transfer.status.committing")); }
    public void fail(String message) {
        finish();
        mStatus.setText(message == null || message.isEmpty() ? tr("geometry_node.common.operation_failed") : message);
        mStatus.setTextColor(UiThemes.EDITOR.status().error());
    }
    public void cancelled() {
        finish();
        mStatus.setText(tr("geometry_node.common.cancelled")); mStatus.setTextColor(UiThemes.EDITOR.status().warning());
        mProgress.setText("");
    }
    public void closeSilently() {
        mFinished = true; mCommitting = false;
        requestClose();
    }
    private void finish() {
        mFinished = true;
        mCommitting = false;
        mCancelButton.setEnabled(true);
        mCancelButton.setText(tr("geometry_node.common.close"));
    }
    @Override protected void onCloseRequested() { if (mFinished) requestClose(); else if (!mCommitting && mOnCancel != null) mOnCancel.run(); }
    @Override public boolean onEscapePressed() { onCloseRequested(); return true; }
    private static String tr(String key) { return Component.translatable(key).getString(); }
}
