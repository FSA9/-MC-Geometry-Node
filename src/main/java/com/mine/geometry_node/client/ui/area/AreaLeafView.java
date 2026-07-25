package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.window.IToolWindow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.EnumMap;
import java.util.Map;

final class AreaLeafView extends LinearLayout implements AreaIconButton.HintSink {
    private final AreaLayoutRoot mRoot;
    private final AreaLeafNode mNode;
    private final VectorIconView mCurrentIcon;
    private final TextView mTitle;
    private final FrameLayout mContentFrame;
    private final Map<AreaEditorType, AreaIconButton> mEditorButtons = new EnumMap<>(AreaEditorType.class);
    private final float mHeaderTouchSlop;
    private AreaIconButton mHintButton;
    private IToolWindow mCurrentWindow;
    private boolean mWindowShown;
    private boolean mHeaderDragging;
    private float mHeaderDownRawX;
    private float mHeaderDownRawY;

    AreaLeafView(Context context, AreaLayoutRoot root, AreaLeafNode node) {
        super(context);
        mRoot = root;
        mNode = node;
        mHeaderTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
        setPadding(
                UIUtils.dp2pxInt(AreaStyle.PANE_GAP_DP),
                UIUtils.dp2pxInt(AreaStyle.PANE_GAP_DP),
                UIUtils.dp2pxInt(AreaStyle.PANE_GAP_DP),
                UIUtils.dp2pxInt(AreaStyle.PANE_GAP_DP)
        );
        setBackground(AreaStyle.rounded(AreaStyle.COLOR_PANE, AreaStyle.PANE_RADIUS_DP, 1, AreaStyle.COLOR_PANE_BORDER));

        LinearLayout header = createHeader(context);
        mCurrentIcon = new VectorIconView(context, node.editorType().iconKind(), AreaStyle.COLOR_ICON_SELECTED);
        mCurrentIcon.setOnTouchListener(this::handleHeaderTouch);
        header.addView(mCurrentIcon, iconParams());

        mTitle = UIUtils.createLockedTextView(context, "", 10.0f, AreaStyle.COLOR_TEXT);
        mTitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        mTitle.setSingleLine(true);
        mTitle.setOnTouchListener(this::handleHeaderTouch);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        titleParams.setMargins(UIUtils.dp2pxInt(5.0f), 0, UIUtils.dp2pxInt(4.0f), 0);
        header.addView(mTitle, titleParams);

        addEditorButtons(header, context);
        addHeaderDivider(header, context);
        addActionButton(header, context, VectorIconView.Kind.SPLIT_HORIZONTAL, "左右分割区域",
                () -> mRoot.splitLeaf(mNode, AreaSplitDirection.HORIZONTAL));
        addActionButton(header, context, VectorIconView.Kind.SPLIT_VERTICAL, "上下分割区域",
                () -> mRoot.splitLeaf(mNode, AreaSplitDirection.VERTICAL));
        addActionButton(header, context, VectorIconView.Kind.CLOSE, "关闭区域",
                () -> mRoot.closeLeaf(mNode));

        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(AreaStyle.HEADER_HEIGHT_DP)));

        mContentFrame = new FrameLayout(context);
        mContentFrame.setBackground(AreaStyle.rounded(AreaStyle.COLOR_CONTENT, 3.0f, 0, 0));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f);
        contentParams.topMargin = UIUtils.dp2pxInt(AreaStyle.PANE_GAP_DP);
        addView(mContentFrame, contentParams);

        updateHeaderState();
        attachEditor(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateHeaderState();
        attachEditor();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mCurrentWindow != null) {
            hideWindow(mCurrentWindow);
            mCurrentWindow = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void showButtonHint(AreaIconButton button, String hint) {
        mHintButton = button;
        if (hint != null && !hint.isBlank()) {
            mTitle.setText(hint);
            mTitle.setTextColor(AreaStyle.COLOR_TEXT);
        }
    }

    @Override
    public void clearButtonHint(AreaIconButton button) {
        if (mHintButton == button) {
            mHintButton = null;
            updateTitle();
        }
    }

    AreaLeafNode node() {
        return mNode;
    }

    void refreshChrome() {
        mHintButton = null;
        updateHeaderState();
    }

    void refreshFromNode() {
        refreshChrome();
        attachEditor();
    }

    void prepareForContentSwap() {
        mCurrentWindow = null;
        mWindowShown = false;
    }

    void setDragTargetHighlighted(boolean highlighted) {
        setBackground(AreaStyle.rounded(
                AreaStyle.COLOR_PANE,
                AreaStyle.PANE_RADIUS_DP,
                highlighted ? 2 : 1,
                highlighted ? AreaStyle.COLOR_ACCENT : AreaStyle.COLOR_PANE_BORDER
        ));
    }

    private LinearLayout createHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UIUtils.dp2pxInt(AreaStyle.HEADER_PADDING_X_DP), 0, UIUtils.dp2pxInt(4.0f), 0);
        header.setBackground(AreaStyle.rounded(AreaStyle.COLOR_HEADER, 3.5f, 0, 0));
        header.setOnTouchListener(this::handleHeaderTouch);
        return header;
    }

    private boolean handleHeaderTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                mHeaderDownRawX = event.getRawX();
                mHeaderDownRawY = event.getRawY();
                mHeaderDragging = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                float rawX = event.getRawX();
                float rawY = event.getRawY();
                if (!mHeaderDragging && draggedFarEnough(rawX, rawY)) {
                    mHeaderDragging = true;
                    mRoot.beginLeafDrag(mNode, rawX, rawY);
                } else if (mHeaderDragging) {
                    mRoot.updateLeafDrag(rawX, rawY);
                }
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (mHeaderDragging) {
                    mRoot.finishLeafDrag(event.getRawX(), event.getRawY());
                }
                mHeaderDragging = false;
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                if (mHeaderDragging) {
                    mRoot.cancelLeafDrag();
                }
                mHeaderDragging = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean draggedFarEnough(float rawX, float rawY) {
        float dx = rawX - mHeaderDownRawX;
        float dy = rawY - mHeaderDownRawY;
        return dx * dx + dy * dy >= mHeaderTouchSlop * mHeaderTouchSlop;
    }

    private void addEditorButtons(LinearLayout header, Context context) {
        for (AreaEditorType type : AreaEditorType.values()) {
            AreaIconButton button = new AreaIconButton(context, type.iconKind(), "切换到 " + type.displayName(), this);
            button.setOnClickListener(v -> switchEditor(type));
            mEditorButtons.put(type, button);
            header.addView(button, buttonParams());
        }
    }

    private void addActionButton(LinearLayout header, Context context, VectorIconView.Kind kind, String hint, Runnable action) {
        AreaIconButton button = new AreaIconButton(context, kind, hint, this);
        button.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            }
        });
        header.addView(button, buttonParams());
    }

    private void addHeaderDivider(LinearLayout header, Context context) {
        View divider = new View(context);
        divider.setBackground(AreaStyle.rect(0xFF151515));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.max(1, UIUtils.dp2pxInt(1.0f)),
                UIUtils.dp2pxInt(16.0f));
        params.setMargins(UIUtils.dp2pxInt(4.0f), 0, UIUtils.dp2pxInt(4.0f), 0);
        header.addView(divider, params);
    }

    private void switchEditor(AreaEditorType type) {
        if (type == null || type == mNode.editorType()) {
            return;
        }
        mNode.setEditorType(type);
        updateHeaderState();
        attachEditor();
    }

    private void attachEditor() {
        attachEditor(true);
    }

    private void attachEditor(boolean notifyShow) {
        IToolWindow window = mNode.window(getContext(), mRoot.editorRegistry());
        if (window == null) {
            if (mCurrentWindow != null) {
                hideWindow(mCurrentWindow);
            }
            mCurrentWindow = null;
            return;
        }

        boolean changedWindow = mCurrentWindow != window;
        if (changedWindow && mCurrentWindow != null) {
            hideWindow(mCurrentWindow);
        }

        View view = window.getView();
        if (view == null) {
            mCurrentWindow = null;
            return;
        }

        if (view.getParent() != mContentFrame) {
            detachFromParent(view);
            mContentFrame.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        showOnly(view);
        mCurrentWindow = window;
        if (notifyShow && (changedWindow || !mWindowShown)) {
            window.onShow();
            mWindowShown = true;
        } else if (!notifyShow) {
            mWindowShown = false;
        }
    }

    private void updateHeaderState() {
        AreaEditorType type = mNode.editorType();
        mCurrentIcon.setKind(type.iconKind());
        for (Map.Entry<AreaEditorType, AreaIconButton> entry : mEditorButtons.entrySet()) {
            entry.getValue().setSelectedState(entry.getKey() == type);
        }
        updateTitle();
    }

    private void updateTitle() {
        if (mHintButton != null) {
            return;
        }
        mTitle.setText(mNode.editorType().displayName());
        mTitle.setTextColor(mRoot.canCloseLeaf() ? AreaStyle.COLOR_TEXT : AreaStyle.COLOR_TEXT_MUTED);
    }

    private LinearLayout.LayoutParams iconParams() {
        int size = UIUtils.dp2pxInt(AreaStyle.HEADER_BUTTON_SIZE_DP);
        return new LinearLayout.LayoutParams(size, size);
    }

    private LinearLayout.LayoutParams buttonParams() {
        int size = UIUtils.dp2pxInt(AreaStyle.HEADER_BUTTON_SIZE_DP);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.leftMargin = UIUtils.dp2pxInt(AreaStyle.HEADER_BUTTON_GAP_DP);
        return params;
    }

    private static void detachFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup parent) {
            parent.removeView(view);
        }
    }

    private void hideWindow(IToolWindow window) {
        View view = window.getView();
        if (view != null) {
            view.setVisibility(View.GONE);
        }
        window.onHide();
        mWindowShown = false;
    }

    private void showOnly(View visibleView) {
        for (int i = 0; i < mContentFrame.getChildCount(); i++) {
            View child = mContentFrame.getChildAt(i);
            child.setVisibility(child == visibleView ? View.VISIBLE : View.GONE);
        }
    }
}
