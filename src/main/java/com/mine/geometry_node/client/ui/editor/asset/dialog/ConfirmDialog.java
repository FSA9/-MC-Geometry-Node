package com.mine.geometry_node.client.ui.editor.asset.dialog;

import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

public class ConfirmDialog extends AssetDialogBase {
    private final Runnable mOnConfirm;

    public ConfirmDialog(Context context, String title, String message, String confirmText, Runnable onConfirm) {
        super(context, title);
        mOnConfirm = onConfirm;

        TextView messageView = label(context, message, 13, 0xFFDDDDDD);
        mPanel.addView(messageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(46)
        ));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);

        UiActionButton cancel = actionButton(context, "取消", UiActionButton.Role.SECONDARY);
        cancel.setOnClickListener(v -> requestClose());
        actions.addView(cancel, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));

        UiActionButton confirm = actionButton(context, confirmText, UiActionButton.Role.DANGER);
        confirm.setOnClickListener(v -> {
            if (requestClose() && mOnConfirm != null) {
                mOnConfirm.run();
            }
        });
        LinearLayout.LayoutParams confirmLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32));
        confirmLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(confirm, confirmLp);

        setActions(actions);
    }
}
