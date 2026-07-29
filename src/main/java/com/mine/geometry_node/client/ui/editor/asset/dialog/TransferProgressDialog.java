package com.mine.geometry_node.client.ui.editor.asset.dialog;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

public class TransferProgressDialog extends AssetDialogBase {
    private final TextView mStatus;
    private final TextView mProgress;
    private final Button mCancelButton;
    private Runnable mOnCancel;
    private boolean mFinished;

    public TransferProgressDialog(Context context, String title) {
        super(context, title);
        setOnClickListener(v -> {});
        mStatus = label(context, "准备中", 13, 0xFFDDDDDD);
        mProgress = label(context, "0 / 0", 13, 0xFF9FD0FF);
        mPanel.addView(mStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32)));
        mPanel.addView(mProgress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        mCancelButton = button(context, "取消", 0xFF4A4A4A);
        mCancelButton.setOnClickListener(v -> {
            if (mFinished) {
                dismiss();
                return;
            }
            if (mOnCancel != null) {
                mOnCancel.run();
            } else {
                dismiss();
            }
        });
        actions.addView(mCancelButton, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));
        mPanel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(36)));
    }

    public void setOnCancel(Runnable onCancel) {
        mOnCancel = onCancel;
    }

    public void update(String message, int processed, int total) {
        mStatus.setText(message == null || message.isEmpty() ? "处理中" : message);
        mProgress.setText(total > 0 ? processed + " / " + total : String.valueOf(processed));
        if (message != null && message.contains("完成")) {
            mFinished = true;
            mCancelButton.setText("关闭");
            postDelayed(this::dismiss, 900);
        }
    }

    public void fail(String message) {
        mFinished = true;
        mStatus.setText(message == null || message.isEmpty() ? "操作失败" : message);
        mStatus.setTextColor(0xFFFF7777);
        mCancelButton.setText("关闭");
    }

    public void cancelled() {
        mFinished = true;
        mStatus.setText("已取消");
        mStatus.setTextColor(0xFFFFB86C);
        mProgress.setText("");
        mCancelButton.setText("关闭");
    }
}
