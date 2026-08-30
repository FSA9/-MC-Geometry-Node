package com.mine.geometry_node.client.ui.editor.asset.dialog;

import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

public class TransferProgressDialog extends AssetDialogBase {
    private final TextView mStatus;
    private final TextView mProgress;
    private final UiActionButton mCancelButton;
    private Runnable mOnCancel;
    private boolean mFinished;
    private boolean mCommitting;

    public TransferProgressDialog(Context context, String title) {
        super(context, title);
        mStatus = label(context, "准备中", 13, 0xFFDDDDDD);
        mProgress = label(context, "0 / 0", 13, 0xFF9FD0FF);
        mPanel.addView(mStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32)));
        mPanel.addView(mProgress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        mCancelButton = actionButton(context, "取消", UiActionButton.Role.SECONDARY);
        mCancelButton.setOnClickListener(v -> onCloseRequested());
        actions.addView(mCancelButton, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));
        setActions(actions);
    }

    public void setOnCancel(Runnable onCancel) {
        mOnCancel = onCancel;
    }

    public void update(String message, int processed, int total) {
        mStatus.setText(message == null || message.isEmpty() ? "处理中" : message);
        mProgress.setText(total > 0 ? processed + " / " + total : String.valueOf(processed));
        if (message != null && message.contains("完成")) {
            mFinished = true;
            mCommitting = false;
            mCancelButton.setEnabled(true);
            mCancelButton.setText("关闭");
            postDelayed(this::requestClose, 900);
        }
    }

    public void enterCommitPhase() {
        mCommitting = true;
        mCancelButton.setEnabled(false);
        mCancelButton.setText("提交中");
    }

    public void fail(String message) {
        mFinished = true;
        mCommitting = false;
        mCancelButton.setEnabled(true);
        mStatus.setText(message == null || message.isEmpty() ? "操作失败" : message);
        mStatus.setTextColor(0xFFFF7777);
        mCancelButton.setText("关闭");
    }

    public void cancelled() {
        mFinished = true;
        mCommitting = false;
        mCancelButton.setEnabled(true);
        mStatus.setText("已取消");
        mStatus.setTextColor(0xFFFFB86C);
        mProgress.setText("");
        mCancelButton.setText("关闭");
    }

    @Override
    protected void onCloseRequested() {
        if (mFinished) {
            requestClose();
        } else if (!mCommitting && mOnCancel != null) {
            mOnCancel.run();
        }
    }

    @Override
    public boolean onEscapePressed() {
        onCloseRequested();
        return true;
    }
}
