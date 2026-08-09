package com.mine.geometry_node.client.ui.editor.asset.dialog;

import com.mine.geometry_node.client.ui.common.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.shell.layer.OverlayCloseReason;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

public class OverwriteConfirmDialog extends AssetDialogBase {
    public enum Decision {
        OVERWRITE_CURRENT,
        OVERWRITE_ALL,
        SKIP_CURRENT,
        SKIP_ALL,
        CANCEL
    }

    private final List<String> mConflicts;
    private final Consumer<Decision> mOnDecision;
    private final TextView mMessage;
    private int mIndex = 0;
    private boolean mClosingDecisionDelivered;

    public OverwriteConfirmDialog(Context context, List<String> conflicts, Consumer<Decision> onDecision) {
        super(context, "目标已存在");
        mConflicts = conflicts;
        mOnDecision = onDecision;

        mMessage = label(context, "", 13, 0xFFDDDDDD);
        mPanel.addView(mMessage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(42)));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        addAction(actions, "覆盖当前", () -> decide(Decision.OVERWRITE_CURRENT));
        addAction(actions, "覆盖全部", () -> decide(Decision.OVERWRITE_ALL));
        addAction(actions, "跳过当前", () -> decide(Decision.SKIP_CURRENT));
        addAction(actions, "跳过全部", () -> decide(Decision.SKIP_ALL));
        addAction(actions, "取消", () -> decide(Decision.CANCEL));
        setActions(actions);
        updateMessage();
    }

    private void addAction(LinearLayout actions, String text, Runnable action) {
        UiActionButton button = actionButton(getContext(), text,
                text.equals("取消") ? UiActionButton.Role.SECONDARY : UiActionButton.Role.PRIMARY);
        button.setOnClickListener(v -> {
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(88), UIUtils.dp2pxInt(32));
        lp.leftMargin = UIUtils.dp2pxInt(6);
        actions.addView(button, lp);
    }

    private void decide(Decision decision) {
        mOnDecision.accept(decision);
        if (decision == Decision.OVERWRITE_CURRENT || decision == Decision.SKIP_CURRENT) {
            mIndex++;
            if (mIndex < mConflicts.size()) {
                updateMessage();
                return;
            }
        }
        mClosingDecisionDelivered = true;
        requestClose();
    }

    private void updateMessage() {
        String target = mConflicts.isEmpty() ? "未知文件" : mConflicts.get(Math.min(mIndex, mConflicts.size() - 1));
        mMessage.setText("发现 " + mConflicts.size() + " 个冲突，当前 " + (mIndex + 1) + " / " + Math.max(1, mConflicts.size()) + ": " + target);
    }

    @Override
    protected void onWindowClosed(OverlayCloseReason reason) {
        if (!mClosingDecisionDelivered) {
            mClosingDecisionDelivered = true;
            mOnDecision.accept(Decision.CANCEL);
        }
        super.onWindowClosed(reason);
    }
}
