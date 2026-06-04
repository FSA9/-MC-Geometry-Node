package com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.List;

public class UploadFailureRetryDialog extends AssetDialogBase {
    private final Runnable mOnRetry;

    public UploadFailureRetryDialog(Context context, List<String> failedPaths, Runnable onRetry) {
        super(context, "上传失败");
        mOnRetry = onRetry;

        TextView message = label(context, "以下文件上传失败，是否全部重新上传？", 13, 0xFFFFC0C0);
        mPanel.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(30)
        ));

        ScrollView scrollView = new ScrollView(context);
        TextView list = label(context, buildFailureList(failedPaths), 12, 0xFFDDDDDD);
        list.setGravity(Gravity.LEFT | Gravity.TOP);
        list.setSingleLine(false);
        scrollView.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        mPanel.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(168)
        ));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);

        Button cancel = button(context, "取消", 0xFF4A4A4A);
        cancel.setOnClickListener(v -> dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));

        Button retry = button(context, "全部重新上传", 0xFF2F7DDE);
        retry.setOnClickListener(v -> {
            dismiss();
            if (mOnRetry != null) {
                mOnRetry.run();
            }
        });
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(128), UIUtils.dp2pxInt(32));
        retryLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(retry, retryLp);

        mPanel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(38)
        ));
    }

    private String buildFailureList(List<String> failedPaths) {
        if (failedPaths == null || failedPaths.isEmpty()) {
            return "- 未知文件";
        }

        StringBuilder builder = new StringBuilder();
        for (String path : failedPaths) {
            if (path == null || path.isEmpty()) continue;
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(path);
        }
        return builder.isEmpty() ? "- 未知文件" : builder.toString();
    }
}
