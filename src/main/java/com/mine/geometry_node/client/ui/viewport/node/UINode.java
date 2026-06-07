package com.mine.geometry_node.client.ui.viewport.node;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.HintRendererFactory;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIItemSlot;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintRenderer;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;

import java.util.HashMap;
import java.util.Map;

public class UINode extends FrameLayout implements NodeVisualAdapter {
    private static UINode sOpenCommentNode;
    public static final int COMMENT_POPUP_WIDTH_DP = 10 * UIConstants.GRID_SIZE;

    private float mLogicX = 0;
    private float mLogicY = 0;
    private final NodeData mNodeData;
    private final NodeDef mNodeDef;
    private final EditorContext mEditorContext;
    private boolean mIsSelected = false;
    private int mTotalHeight; // 逻辑单位 DP

    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final NodeLayoutEngine mLayoutEngine = new NodeLayoutEngine();
    private NodeLayout mLayout;

    private final Map<Integer, View> mHintViews = new HashMap<>();

    private View mAddButton;
    private final Map<String, View> mRemoveButtons = new HashMap<>();
    private TextView mCommentButton;
    private TextView mCommentTooltip;

    public UINode(Context context, NodeData nodeData, NodeDef nodeDef, EditorContext editorContext) {
        super(context);
        this.mNodeData = nodeData;
        this.mNodeDef = nodeDef;
        this.mEditorContext = editorContext;

        setWillNotDraw(true);
        setClipChildren(false);
        mPaint.setAntiAlias(true);

        syncAndLayoutUI(context);
    }

    public String hitTestLabel(float localXpx, float localYpx) {
        if (mLayout == null) return null;
        for (NodeLayout.LabelRun label : mLayout.labels) {
            if (label.hitRect.contains(localXpx, localYpx)) {
                return label.portId;
            }
        }
        return null;
    }

    private View createDynamicButton(Context context, String text, boolean isAdd, String refPortId, Integer removeIndex) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setGravity(icyllis.modernui.view.Gravity.CENTER);
        btn.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        btn.setTextColor(UIConstants.Node.CLR_DYNAMIC_BTN_FG);

        icyllis.modernui.graphics.drawable.ShapeDrawable bgDrawable = new icyllis.modernui.graphics.drawable.ShapeDrawable();
        bgDrawable.setColor(UIConstants.Node.CLR_DYNAMIC_BTN_BG);
        bgDrawable.setCornerRadius(UIUtils.dp2px(2.0f));
        bgDrawable.setStroke(UIUtils.dp2pxInt(1), UIConstants.Node.CLR_DYNAMIC_BTN_STROKE);
        btn.setBackground(bgDrawable);

        btn.setOnClickListener(v -> {
            if (mEditorContext == null) return;

            if (isGroupVirtualDynamicNode()) {
                if (isAdd) {
                    com.mine.geometry_node.client.ui.UICommand.commands.CmdAddGroupVirtualPort cmd =
                            new com.mine.geometry_node.client.ui.UICommand.commands.CmdAddGroupVirtualPort(mEditorContext.getGraphController(), mNodeData.id);
                    mEditorContext.getCommandManager().execute(cmd);
                } else if (refPortId != null && !refPortId.isBlank()) {
                    com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveGroupVirtualPort cmd =
                            new com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveGroupVirtualPort(mEditorContext.getGraphController(), mNodeData.id, refPortId);
                    mEditorContext.getCommandManager().execute(cmd);
                }
                return;
            }

            boolean isInputDynamic = mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
            String propertyKey = isInputDynamic ? StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id() : StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id();
            int minCount = isInputDynamic ? 1 : mNodeDef.getMetaOrDefault(SchemaKeys.MIN_DYNAMIC_OUTPUT, 1);

            int currentCount = minCount;
            Object countObj = mNodeData.inputs.get(propertyKey);
            if (countObj instanceof Number num) {
                currentCount = num.intValue();
            } else if (countObj instanceof String str) {
                try { currentCount = Integer.parseInt(str); } catch (Exception ignored) {}
            }

            if (isAdd) {
                int maxCount = isInputDynamic ? mNodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_INPUT, 10) : mNodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_OUTPUT, 10);
                if (currentCount < maxCount) {
                    com.mine.geometry_node.client.ui.UICommand.commands.CmdAddBranch cmd = new com.mine.geometry_node.client.ui.UICommand.commands.CmdAddBranch(mEditorContext.getGraphController(), mNodeData.id, propertyKey, currentCount);
                    mEditorContext.getCommandManager().execute(cmd);
                }
            } else {
                if (currentCount > minCount && removeIndex != null) {
                    com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveBranch cmd = new com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveBranch(mEditorContext.getGraphController(), mEditorContext.getCurrentGraph(), mNodeData.id, propertyKey, currentCount, removeIndex);
                    mEditorContext.getCommandManager().execute(cmd);
                }
            }
        });
        return btn;
    }

    private void syncAndLayoutUI(Context context) {
        mLayout = mLayoutEngine.build(mNodeData, mNodeDef);

        removeAllViews();
        mHintViews.clear();
        mRemoveButtons.clear();
        mAddButton = null;
        mCommentButton = null;
        mCommentTooltip = null;
        if (sOpenCommentNode == this) {
            sOpenCommentNode = null;
        }

        float currentY = UIConstants.Node.HEADER_HEIGHT; // 逻辑单位 DP

        // --- 1. 遍历行，仅重建真实交互控件 ---
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float rowHeight = mLayoutEngine.calculateRowHeight(mNodeData, row);

            // Hint 内嵌输入组件处理
            if (row.uiHint() != null) {
                UIHintRenderer renderer = HintRendererFactory.getRenderer(row.uiHint());
                if (renderer != null) {
                    View hintView = renderer.createView(context, mNodeData, row, mEditorContext);
                    if (hintView != null) {
                        mHintViews.put(i, hintView);
                        addView(hintView, new LayoutParams(0, 0));

                        boolean isConnected = mNodeData.isInputConnected(row.leftPort() != null ? row.leftPort().id() : "");
                        hintView.setVisibility(isConnected ? View.GONE : View.VISIBLE);
                        renderer.updateLayout(hintView, row, currentY, UIConstants.Node.NODE_WIDTH);
                    }
                }
            }

            // 动态删除按钮处理
            if (isDynamicRow(row)) {
                String portId = row.leftPort() != null ? row.leftPort().id() : (row.rightPort() != null ? row.rightPort().id() : "");
                Integer removeIndex = row.hintParams() != null ? (Integer) row.hintParams().get(PortMetaKeys.DYNAMIC_INDEX) : null;

                if (removeIndex != null) {
                    View btn = createDynamicButton(context, "-", false, portId, removeIndex);
                    mRemoveButtons.put(portId, btn);

                    float btnWidth = 16.0f;
                    float btnHeight = com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils.getStandardInputHeight();
                    LayoutParams lp = new LayoutParams(UIUtils.dp2pxInt(btnWidth), UIUtils.dp2pxInt(btnHeight));
                    lp.topMargin = UIUtils.dp2pxInt(currentY + (UIConstants.Node.ROW_HEIGHT - btnHeight) / 2f);

                    if (row.leftPort() != null) {
                        lp.gravity = icyllis.modernui.view.Gravity.TOP | icyllis.modernui.view.Gravity.LEFT;
                        lp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH - UIConstants.Node.LABEL_MARGIN_PORT - btnWidth);
                    } else {
                        lp.gravity = icyllis.modernui.view.Gravity.TOP | icyllis.modernui.view.Gravity.LEFT;
                        lp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT);
                    }
                    addView(btn, lp);
                }
            }

            currentY += rowHeight;
        }

        // --- 2. Add 按钮 ---
        boolean isInputDynamic = mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
        boolean isOutputDynamic = mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT).isPresent();
        boolean isGroupVirtualDynamic = isGroupVirtualDynamicNode();
        if (isInputDynamic || isOutputDynamic || isGroupVirtualDynamic) {
            mAddButton = createDynamicButton(context, "+", true, null, null);

            float inputBoxHeight = com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils.getStandardInputHeight();
            float verticalMargin = (UIConstants.Node.ROW_HEIGHT - inputBoxHeight) / 2.0f;
            float startX = UIConstants.Node.LABEL_MARGIN_PORT;
            float endX = UIConstants.Node.NODE_WIDTH - UIConstants.Node.LABEL_MARGIN_PORT;

            LayoutParams lp = new LayoutParams(UIUtils.dp2pxInt(endX - startX), UIUtils.dp2pxInt(inputBoxHeight));
            lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
            lp.leftMargin = UIUtils.dp2pxInt(startX);
            lp.topMargin = UIUtils.dp2pxInt(currentY + verticalMargin);

            addView(mAddButton, lp);
            currentY += UIConstants.Node.ROW_HEIGHT;
        }

        createCommentOverlayIfNeeded(context);

        // --- 3. 刷新整体尺寸 ---
        mTotalHeight = mLayout.totalHeight;

        LayoutParams lp = new LayoutParams(UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH), UIUtils.dp2pxInt(mTotalHeight));
        setLayoutParams(lp);
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
        LayoutParams buttonLp = new LayoutParams(buttonSize, buttonSize);
        buttonLp.gravity = Gravity.TOP | Gravity.LEFT;
        buttonLp.topMargin = UIUtils.dp2pxInt((UIConstants.Node.HEADER_HEIGHT - 12) / 2.0f);
        buttonLp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH - 5 - 12);
        addView(mCommentButton, buttonLp);

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
        LayoutParams tooltipLp = new LayoutParams(tooltipWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        tooltipLp.gravity = Gravity.TOP | Gravity.LEFT;
        tooltipLp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH);
        tooltipLp.topMargin = 0;
        addView(mCommentTooltip, tooltipLp);
    }

    private void toggleCommentPopup() {
        if (mCommentTooltip == null) {
            return;
        }
        if (sOpenCommentNode == this && mCommentTooltip.getVisibility() == View.VISIBLE) {
            closeOpenCommentPopup();
            return;
        }

        closeOpenCommentPopup();
        sOpenCommentNode = this;
        mCommentTooltip.setVisibility(View.VISIBLE);
    }

    public static boolean closeOpenCommentPopup() {
        if (sOpenCommentNode != null) {
            sOpenCommentNode.hideCommentPopup();
            sOpenCommentNode = null;
            return true;
        }
        return false;
    }

    public static boolean isCommentButton(View view) {
        return view != null
                && view.getParent() instanceof UINode node
                && node.mCommentButton == view;
    }

    private void hideCommentPopup() {
        if (mCommentTooltip != null && mCommentTooltip.getVisibility() != View.GONE) {
            mCommentTooltip.setVisibility(View.GONE);
        }
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

    @Override
    public void updateNodeLayout() {
        syncAndLayoutUI(getContext());
    }

    @Override
    public boolean hasOverlayViews() {
        return mAddButton != null || mCommentButton != null || !mRemoveButtons.isEmpty() || !mHintViews.isEmpty();
    }

    public int getOverlayWidthDp() {
        return mCommentButton != null
                ? UIConstants.Node.NODE_WIDTH + COMMENT_POPUP_WIDTH_DP
                : UIConstants.Node.NODE_WIDTH;
    }

    public int getOverlayHeightDp() {
        return mCommentButton != null
                ? Math.max(mTotalHeight, estimateCommentPopupHeightDp())
                : mTotalHeight;
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

    private static int estimateTextUnits(String text) {
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            units += c <= 0x7F ? 1 : 2;
        }
        return units;
    }

    @Override
    public void onOverlayScaleChanged(float scale) {
        for (View view : mHintViews.values()) {
            if (view instanceof UIItemSlot itemSlot) {
                itemSlot.setViewportScale(scale);
            }
        }
    }

    @Override
    public int getTotalHeightDp() {
        return mTotalHeight;
    }

    @Override
    public void drawNode(Canvas canvas, ViewportCamera camera) {
        canvas.save();
        canvas.translate(camera.uiToScreenX(mLogicX), camera.uiToScreenY(mLogicY));
        canvas.scale(camera.getScale(), camera.getScale());
        drawNodeLocal(canvas);
        canvas.restore();
    }

    private void drawNodeLocal(Canvas canvas) {
        float w = UIUtils.dp2px(UIConstants.Node.NODE_WIDTH);
        float h = UIUtils.dp2px(mTotalHeight);

        float scaledRadius = UIUtils.dp2px(com.mine.geometry_node.client.ui.persistence.config.ConfigManager.INSTANCE.getConfig().node.cornerRadius);
        float scaledHeaderH = UIUtils.dp2px(UIConstants.Node.HEADER_HEIGHT);

        // 画节点背景
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(UIConstants.CLR_BG_NODE_BODY);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        // 画节点头部
        mPaint.setColor(mNodeData.getHeaderColor(mNodeDef.category().getColor()));
        mTempRect.set(0, 0, w, scaledHeaderH);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, 0f, 0f, mPaint);

        // 画节点边框
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mIsSelected ? UIConstants.Node.STROKE_WIDTH_SELECTED : UIConstants.Node.STROKE_WIDTH_NORMAL);
        mPaint.setColor(mIsSelected ? UIConstants.CLR_WHITE : UIConstants.CLR_NODE_OUTLINE);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        // 画输入/输出端口的彩色圆点
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);

            if (row.leftPort() != null && !row.leftPort().hidePin()) {
                Float yDp = mLayout != null ? mLayout.inputPortY.get(row.leftPort().id()) : null;
                if (yDp != null) {
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(row.leftPort().type().getColor());
                    canvas.drawCircle(0, UIUtils.dp2px(yDp), UIUtils.dp2px(UIConstants.Node.PORT_VISUAL_RADIUS), mPaint);
                }
            }
            if (row.rightPort() != null) {
                Float yDp = mLayout != null ? mLayout.outputPortY.get(row.rightPort().id()) : null;
                if (yDp != null) {
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(row.rightPort().type().getColor());
                    canvas.drawCircle(w, UIUtils.dp2px(yDp), UIUtils.dp2px(UIConstants.Node.PORT_VISUAL_RADIUS), mPaint);
                }
            }
        }
        drawTextRuns(canvas);
    }

    private void drawTextRuns(Canvas canvas) {
        if (mLayout == null) return;

        mLayoutEngine.getHeaderPaint().setColor(UIConstants.CLR_WHITE);
        canvas.drawShapedText(mLayout.titleText, mLayout.titleX, mLayout.titleBaseline, mLayoutEngine.getHeaderPaint());

        mLayoutEngine.getLabelPaint().setColor(UIConstants.CLR_GRAY_LABEL);
        for (NodeLayout.LabelRun label : mLayout.labels) {
            canvas.drawShapedText(label.shapedText, label.x, label.baseline, mLayoutEngine.getLabelPaint());
        }
    }

    private boolean isDynamicRow(PortRow row) { return row.hintParams() != null && Boolean.TRUE.equals(row.hintParams().get(PortMetaKeys.IS_DYNAMIC)); }

    private boolean isGroupVirtualDynamicNode() {
        return mNodeData != null && (mNodeData.isGroupInputNode() || mNodeData.isGroupOutputNode());
    }

    @Override
    public View findInteractiveViewAt(float localXpx, float localYpx) {
        View hit = findInteractiveChildAt(mCommentButton, localXpx, localYpx);
        if (hit != null) return hit;

        // 检测 Add 按钮
        hit = findInteractiveChildAt(mAddButton, localXpx, localYpx);
        if (hit != null) return hit;

        // 检测 Remove 按钮
        for (View v : mRemoveButtons.values()) {
            hit = findInteractiveChildAt(v, localXpx, localYpx);
            if (hit != null) return hit;
        }

        // 检测输入框等 Hint
        for (View v : mHintViews.values()) {
            hit = findInteractiveChildAt(v, localXpx, localYpx);
            if (hit != null) return hit;
        }
        return null;
    }

    private View findInteractiveChildAt(View view, float parentLocalXpx, float parentLocalYpx) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (parentLocalXpx < view.getLeft() || parentLocalXpx >= view.getRight()
                || parentLocalYpx < view.getTop() || parentLocalYpx >= view.getBottom()) {
            return null;
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

    @Override
    public View getOverlayHostView() {
        return this;
    }

    @Override
    public String hitTestPort(float localXdp, float localYdp, boolean checkInput, float touchRadiusDp) {
        float targetX = checkInput ? 0 : UIConstants.Node.NODE_WIDTH;
        float dx = localXdp - targetX;
        Map<String, Float> map = mLayout != null ? (checkInput ? mLayout.inputPortY : mLayout.outputPortY) : new HashMap<>();
        float thresholdSq = touchRadiusDp * touchRadiusDp;
        String best = null; float bestDistSq = Float.MAX_VALUE;
        for (Map.Entry<String, Float> entry : map.entrySet()) {
            float dy = localYdp - entry.getValue();
            float distSq = dx * dx + dy * dy;
            if (distSq <= thresholdSq && distSq < bestDistSq) { bestDistSq = distSq; best = entry.getKey(); }
        }
        return best;
    }

    @Override
    public void getPortPosition(String portId, boolean isInput, float[] outPos) {
        outPos[0] = isInput ? 0 : UIConstants.Node.NODE_WIDTH;
        Float y = mLayout != null ? (isInput ? mLayout.inputPortY.get(portId) : mLayout.outputPortY.get(portId)) : null;
        outPos[1] = (y != null) ? y : UIConstants.Node.HEADER_HEIGHT + UIConstants.Node.ROW_HEIGHT / 2.0f;
    }

    @Override
    public NodeData getNodeData() { return mNodeData; }
    @Override
    public NodeDef getNodeDef() { return mNodeDef; }
    @Override
    public void setSelected(boolean selected) { if (mIsSelected != selected) { mIsSelected = selected; invalidate(); } }
    @Override
    public boolean isSelected() { return mIsSelected; }

    @Override
    public void getLogicalBounds(RectF outRect) {
        outRect.set(mLogicX, mLogicY, mLogicX + UIConstants.Node.NODE_WIDTH, mLogicY + mTotalHeight);
    }

    @Override
    public void setPreviewPosition(float x, float y) {
        mLogicX = x;
        mLogicY = y;
    }

    @Override
    public void offsetPreviewPosition(float dx, float dy) {
        setPreviewPosition(mLogicX + dx, mLogicY + dy);
    }

    @Override
    public float getUiX() {
        return mLogicX;
    }

    @Override
    public float getUiY() {
        return mLogicY;
    }

}
