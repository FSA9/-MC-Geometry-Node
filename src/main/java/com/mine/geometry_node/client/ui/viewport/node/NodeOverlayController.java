package com.mine.geometry_node.client.ui.viewport.node;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdAddBranch;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdAddGroupVirtualPort;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveBranch;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveGroupVirtualPort;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.HintRendererFactory;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers.UIHintRenderer;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers.UIItemSlot;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

import java.util.HashMap;
import java.util.Map;

final class NodeOverlayController {
    static final int COMMENT_POPUP_WIDTH_DP = 10 * UIConstants.GRID_SIZE;

    private static NodeOverlayController sOpenCommentOverlay;

    private final UINode mHost;
    private final NodeData mNodeData;
    private final NodeDef mNodeDef;
    private final EditorContext mEditorContext;
    private final NodeLayoutEngine mLayoutEngine;

    private final Map<Integer, View> mHintViews = new HashMap<>();
    private final Map<String, View> mRemoveButtons = new HashMap<>();

    private View mAddButton;
    private TextView mCommentButton;
    private TextView mCommentTooltip;
    private int mTotalHeight;
    private boolean mMounted;
    private boolean mHasOverlayViews;

    NodeOverlayController(UINode host, NodeData nodeData, NodeDef nodeDef, EditorContext editorContext, NodeLayoutEngine layoutEngine) {
        this.mHost = host;
        this.mNodeData = nodeData;
        this.mNodeDef = nodeDef;
        this.mEditorContext = editorContext;
        this.mLayoutEngine = layoutEngine;
    }

    void setLayout(NodeLayout layout) {
        mTotalHeight = layout != null ? layout.totalHeight : 0;
        mHasOverlayViews = computeOverlayPresence();
    }

    boolean ensureMounted(Context context) {
        if (mMounted) {
            return true;
        }
        if (!hasOverlayViews()) {
            return false;
        }
        rebuild(context);
        return mMounted;
    }

    void rebuild(Context context) {
        release();
        if (!hasOverlayViews()) {
            return;
        }

        float currentY = UIConstants.Node.HEADER_HEIGHT;
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float rowHeight = mLayoutEngine.calculateRowHeight(mNodeData, row);

            addHintView(context, i, row, currentY);
            addDynamicRemoveButton(context, row, currentY);

            currentY += rowHeight;
        }

        addDynamicAddButton(context, currentY);
        createCommentOverlayIfNeeded(context);
        mMounted = hasMountedViews();
    }

    void release() {
        if (!mMounted && mHost.getChildCount() == 0) {
            clearViewRefs();
            return;
        }
        mHost.removeAllViews();
        clearViewRefs();
    }

    boolean isMounted() {
        return mMounted;
    }

    boolean isCommentPopupVisible() {
        return sOpenCommentOverlay == this
                && mCommentTooltip != null
                && mCommentTooltip.getVisibility() == View.VISIBLE;
    }

    boolean hasOverlayViews() {
        return mHasOverlayViews;
    }

    private boolean computeOverlayPresence() {
        if (hasDynamicAddButton() || hasCommentOverlay()) {
            return true;
        }
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            if (hasHintOverlay(row) || hasDynamicRemoveButton(row)) {
                return true;
            }
        }
        return false;
    }

    int getOverlayWidthDp() {
        return hasCommentOverlay()
                ? UIConstants.Node.NODE_WIDTH + COMMENT_POPUP_WIDTH_DP
                : UIConstants.Node.NODE_WIDTH;
    }

    int getOverlayHeightDp() {
        return hasCommentOverlay()
                ? Math.max(mTotalHeight, estimateCommentPopupHeightDp())
                : mTotalHeight;
    }

    void onOverlayScaleChanged(float scale) {
        for (View view : mHintViews.values()) {
            if (view instanceof UIItemSlot itemSlot) {
                itemSlot.setViewportScale(scale);
            }
        }
    }

    boolean mayContainInteractiveView(float localXpx, float localYpx) {
        if (!hasOverlayViews()) {
            return false;
        }
        float widthPx = UIUtils.dp2px(getOverlayWidthDp());
        float heightPx = UIUtils.dp2px(getOverlayHeightDp());
        return localXpx >= 0 && localXpx < widthPx && localYpx >= 0 && localYpx < heightPx;
    }

    View findInteractiveViewAt(float localXpx, float localYpx) {
        View hit = findInteractiveChildAt(mCommentButton, localXpx, localYpx);
        if (hit != null) return hit;

        hit = findInteractiveChildAt(mAddButton, localXpx, localYpx);
        if (hit != null) return hit;

        for (View view : mRemoveButtons.values()) {
            hit = findInteractiveChildAt(view, localXpx, localYpx);
            if (hit != null) return hit;
        }

        for (View view : mHintViews.values()) {
            hit = findInteractiveChildAt(view, localXpx, localYpx);
            if (hit != null) return hit;
        }
        return null;
    }

    boolean isCommentButtonView(View view) {
        return view == mCommentButton;
    }

    static boolean closeOpenCommentPopup() {
        if (sOpenCommentOverlay != null) {
            sOpenCommentOverlay.hideCommentPopup();
            sOpenCommentOverlay = null;
            return true;
        }
        return false;
    }

    private void clearViewRefs() {
        mHintViews.clear();
        mRemoveButtons.clear();
        mAddButton = null;
        mCommentButton = null;
        mCommentTooltip = null;
        mMounted = false;
        if (sOpenCommentOverlay == this) {
            sOpenCommentOverlay = null;
        }
    }

    private boolean hasMountedViews() {
        return mAddButton != null || mCommentButton != null || !mRemoveButtons.isEmpty() || !mHintViews.isEmpty();
    }

    private void addHintView(Context context, int rowIndex, PortRow row, float currentY) {
        if (!hasHintOverlay(row)) return;

        UIHintRenderer renderer = HintRendererFactory.getRenderer(row.uiHint());
        if (renderer == null) return;

        View hintView = renderer.createView(context, mNodeData, row, mEditorContext);
        if (hintView == null) return;

        mHintViews.put(rowIndex, hintView);
        mHost.addView(hintView, new FrameLayout.LayoutParams(0, 0));

        boolean isConnected = mNodeData.isInputConnected(row.leftPort() != null ? row.leftPort().id() : "");
        hintView.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        renderer.updateLayout(hintView, row, currentY, UIConstants.Node.NODE_WIDTH);
    }

    private void addDynamicRemoveButton(Context context, PortRow row, float currentY) {
        if (!hasDynamicRemoveButton(row)) return;

        String portId = row.leftPort() != null ? row.leftPort().id() : (row.rightPort() != null ? row.rightPort().id() : "");
        Integer removeIndex = row.hintParams() != null ? (Integer) row.hintParams().get(PortMetaKeys.DYNAMIC_INDEX) : null;
        if (removeIndex == null) return;

        View button = createDynamicButton(context, "-", false, portId, removeIndex);
        mRemoveButtons.put(portId, button);

        float buttonWidth = 16.0f;
        float buttonHeight = UIHintUtils.getStandardInputHeight();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(buttonWidth), UIUtils.dp2pxInt(buttonHeight));
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.topMargin = UIUtils.dp2pxInt(currentY + (UIConstants.Node.ROW_HEIGHT - buttonHeight) / 2.0f);

        if (row.leftPort() != null) {
            lp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH - UIConstants.Node.LABEL_MARGIN_PORT - buttonWidth);
        } else {
            lp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT);
        }
        mHost.addView(button, lp);
    }

    private void addDynamicAddButton(Context context, float currentY) {
        if (!hasDynamicAddButton()) return;

        mAddButton = createDynamicButton(context, "+", true, null, null);

        float inputBoxHeight = UIHintUtils.getStandardInputHeight();
        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - inputBoxHeight) / 2.0f;
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = UIConstants.Node.NODE_WIDTH - UIConstants.Node.LABEL_MARGIN_PORT;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(endX - startX),
                UIUtils.dp2pxInt(inputBoxHeight)
        );
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);
        lp.topMargin = UIUtils.dp2pxInt(currentY + verticalMargin);
        mHost.addView(mAddButton, lp);
    }

    private View createDynamicButton(Context context, String text, boolean isAdd, String refPortId, Integer removeIndex) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        button.setTextColor(UIConstants.Node.CLR_DYNAMIC_BTN_FG);

        ShapeDrawable bgDrawable = new ShapeDrawable();
        bgDrawable.setColor(UIConstants.Node.CLR_DYNAMIC_BTN_BG);
        bgDrawable.setCornerRadius(UIUtils.dp2px(2.0f));
        bgDrawable.setStroke(UIUtils.dp2pxInt(1), UIConstants.Node.CLR_DYNAMIC_BTN_STROKE);
        button.setBackground(bgDrawable);

        button.setOnClickListener(v -> handleDynamicButtonClick(isAdd, refPortId, removeIndex));
        return button;
    }

    private void handleDynamicButtonClick(boolean isAdd, String refPortId, Integer removeIndex) {
        if (mEditorContext == null) return;

        if (isGroupVirtualDynamicNode()) {
            if (isAdd) {
                CmdAddGroupVirtualPort cmd = new CmdAddGroupVirtualPort(mEditorContext.getGraphController(), mNodeData.id);
                mEditorContext.getCommandManager().execute(cmd);
            } else if (refPortId != null && !refPortId.isBlank()) {
                CmdRemoveGroupVirtualPort cmd = new CmdRemoveGroupVirtualPort(mEditorContext.getGraphController(), mNodeData.id, refPortId);
                mEditorContext.getCommandManager().execute(cmd);
            }
            return;
        }

        boolean isInputDynamic = mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
        String propertyKey = isInputDynamic ? StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id() : StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id();
        int minCount = isInputDynamic
                ? mNodeDef.getMetaOrDefault(SchemaKeys.MIN_DYNAMIC_INPUT, 1)
                : mNodeDef.getMetaOrDefault(SchemaKeys.MIN_DYNAMIC_OUTPUT, 1);
        int currentCount = getDynamicCount(propertyKey, minCount);

        if (isAdd) {
            int maxCount = isInputDynamic ? mNodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_INPUT, 10) : mNodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_OUTPUT, 10);
            if (currentCount < maxCount) {
                CmdAddBranch cmd = new CmdAddBranch(mEditorContext.getGraphController(), mNodeData.id, propertyKey, currentCount);
                mEditorContext.getCommandManager().execute(cmd);
            }
            return;
        }

        if (currentCount > minCount && removeIndex != null) {
            CmdRemoveBranch cmd = new CmdRemoveBranch(mEditorContext.getGraphController(), mEditorContext.getCurrentGraph(), mNodeData.id, propertyKey, currentCount, removeIndex);
            mEditorContext.getCommandManager().execute(cmd);
        }
    }

    private int getDynamicCount(String propertyKey, int fallback) {
        Object countObj = mNodeData.inputs.get(propertyKey);
        if (countObj instanceof Number num) {
            return num.intValue();
        }
        if (countObj instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private void createCommentOverlayIfNeeded(Context context) {
        String comment = mNodeDef.comment();
        if (comment == null || comment.isBlank()) {
            return;
        }

        mCommentButton = UIUtils.createLockedTextView(context, "▼", 8.0f, 0xDDFFFFFF);
        mCommentButton.setGravity(Gravity.CENTER);
        mCommentButton.setBackground(null);
        mCommentButton.setOnClickListener(v -> toggleCommentPopup());

        int buttonSize = UIUtils.dp2pxInt(12);
        FrameLayout.LayoutParams buttonLp = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        buttonLp.gravity = Gravity.TOP | Gravity.LEFT;
        buttonLp.topMargin = UIUtils.dp2pxInt((UIConstants.Node.HEADER_HEIGHT - 12) / 2.0f);
        buttonLp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH - 5 - 12);
        mHost.addView(mCommentButton, buttonLp);

        mCommentTooltip = UIUtils.createLockedTextView(context, comment.trim(), 9.0f, UIConstants.CLR_WHITE);
        mCommentTooltip.setGravity(Gravity.LEFT | Gravity.TOP);
        mCommentTooltip.setSingleLine(false);
        mCommentTooltip.setHorizontallyScrolling(false);
        mCommentTooltip.setMinLines(1);
        mCommentTooltip.setPadding(UIUtils.dp2pxInt(7), UIUtils.dp2pxInt(5), UIUtils.dp2pxInt(7), UIUtils.dp2pxInt(5));
        mCommentTooltip.setBackground(createRectDrawable(0xF0222222, 4.0f, 1, 0xFF555555));
        mCommentTooltip.setVisibility(View.GONE);
        mCommentTooltip.setEnabled(false);

        int tooltipWidth = UIUtils.dp2pxInt(COMMENT_POPUP_WIDTH_DP);
        FrameLayout.LayoutParams tooltipLp = new FrameLayout.LayoutParams(tooltipWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        tooltipLp.gravity = Gravity.TOP | Gravity.LEFT;
        tooltipLp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH);
        tooltipLp.topMargin = 0;
        mHost.addView(mCommentTooltip, tooltipLp);
    }

    private void toggleCommentPopup() {
        if (mCommentTooltip == null) {
            return;
        }
        if (sOpenCommentOverlay == this && mCommentTooltip.getVisibility() == View.VISIBLE) {
            closeOpenCommentPopup();
            return;
        }

        closeOpenCommentPopup();
        sOpenCommentOverlay = this;
        mCommentTooltip.setVisibility(View.VISIBLE);
    }

    private void hideCommentPopup() {
        if (mCommentTooltip != null && mCommentTooltip.getVisibility() != View.GONE) {
            mCommentTooltip.setVisibility(View.GONE);
        }
    }

    private int estimateCommentPopupHeightDp() {
        String comment = mNodeDef.comment();
        if (comment == null || comment.isBlank()) {
            return mTotalHeight;
        }

        int usableWidthDp = Math.max(1, COMMENT_POPUP_WIDTH_DP - 14);
        int maxUnitsPerLine = Math.max(1, usableWidthDp / 7);
        int lines = 0;
        for (String paragraph : comment.trim().split("\\R", -1)) {
            int units = estimateTextUnits(paragraph);
            lines += Math.max(1, (int) Math.ceil(units / (double) maxUnitsPerLine));
        }
        return Math.max(UIConstants.Node.HEADER_HEIGHT, lines * 15 + 16);
    }

    private int estimateTextUnits(String text) {
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            units += c <= 0x7F ? 1 : 2;
        }
        return units;
    }

    private View findInteractiveChildAt(View view, float parentLocalXpx, float parentLocalYpx) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (parentLocalXpx < view.getLeft() || parentLocalXpx >= view.getRight()
                || parentLocalYpx < view.getTop() || parentLocalYpx >= view.getBottom()) {
            return null;
        }

        if (view instanceof UIItemSlot) {
            return view;
        }

        float childLocalXpx = parentLocalXpx - view.getLeft();
        float childLocalYpx = parentLocalYpx - view.getTop();
        if (view instanceof ViewGroup group) {
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View hit = findInteractiveChildAt(group.getChildAt(i), childLocalXpx, childLocalYpx);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return view;
    }

    private boolean isDynamicRow(PortRow row) {
        return row.hintParams() != null && Boolean.TRUE.equals(row.hintParams().get(PortMetaKeys.IS_DYNAMIC));
    }

    private boolean hasHintOverlay(PortRow row) {
        return row.uiHint() != null
                && !isInputConnected(row)
                && HintRendererFactory.getRenderer(row.uiHint()) != null;
    }

    private boolean hasDynamicRemoveButton(PortRow row) {
        return isDynamicRow(row)
                && row.hintParams() != null
                && row.hintParams().get(PortMetaKeys.DYNAMIC_INDEX) instanceof Integer;
    }

    private boolean hasDynamicAddButton() {
        return mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent()
                || mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT).isPresent()
                || isGroupVirtualDynamicNode();
    }

    private boolean hasCommentOverlay() {
        String comment = mNodeDef.comment();
        return comment != null && !comment.isBlank();
    }

    private boolean isInputConnected(PortRow row) {
        return mNodeData.isInputConnected(row.leftPort() != null ? row.leftPort().id() : "");
    }

    private boolean isGroupVirtualDynamicNode() {
        return mNodeData != null && (mNodeData.isGroupInputNode() || mNodeData.isGroupOutputNode());
    }

    private ShapeDrawable createRectDrawable(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }
}
