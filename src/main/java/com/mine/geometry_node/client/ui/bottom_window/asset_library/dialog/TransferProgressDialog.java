package com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

public class TransferProgressDialog extends AssetDialogBase {
    private final TextView mStatus;
    private final TextView mProgress;

    public TransferProgressDialog(Context context, String title) {
        super(context, title);
        setOnClickListener(v -> {});
        mStatus = label(context, "准备中", 13, 0xFFDDDDDD);
        mProgress = label(context, "0 / 0", 13, 0xFF9FD0FF);
        mPanel.addView(mStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32)));
        mPanel.addView(mProgress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));
    }

    public void update(String message, int processed, int total) {
        mStatus.setText(message == null || message.isEmpty() ? "处理中" : message);
        mProgress.setText(processed + " / " + total);
        if (message != null && message.contains("完成")) {
            postDelayed(this::dismiss, 900);
        }
    }

    public void fail(String message) {
        mStatus.setText(message == null || message.isEmpty() ? "操作失败" : message);
        mStatus.setTextColor(0xFFFF7777);
    }
}
