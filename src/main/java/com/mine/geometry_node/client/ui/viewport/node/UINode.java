package com.mine.geometry_node.client.ui.viewport.node;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.UIHints.HintRendererFactory;
import com.mine.geometry_node.client.ui.viewport.UIHints.UIItemSlot;
import com.mine.geometry_node.client.ui.viewport.UIHints.UIHintRenderer;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.UIHint;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;

import java.util.HashMap;
import java.util.Map;

public class UINode extends FrameLayout implements NodeVisualAdapter {
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
        btn.setTextColor(UIConstants.CLR_WHITE);

        icyllis.modernui.graphics.drawable.ShapeDrawable bgDrawable = new icyllis.modernui.graphics.drawable.ShapeDrawable();
        bgDrawable.setColor(0xFF333333);
        bgDrawable.setCornerRadius(UIUtils.dp2px(com.mine.geometry_node.client.ui.persistence.ConfigManager.INSTANCE.getConfig().node.cornerRadius));
        bgDrawable.setStroke(UIUtils.dp2pxInt(1), 0xFF444444);
        btn.setBackground(bgDrawable);

        btn.setOnClickListener(v -> {
            if (mEditorContext == null) return;

            boolean isInputDynamic = mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
            String propertyKey = isInputDynamic ? StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id() : StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id();

            int currentCount = 1;
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
                if (currentCount > 1 && removeIndex != null) {
                    com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveBranch cmd = new com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveBranch(mEditorContext.getGraphController(), mEditorContext.getGraph(), mNodeData.id, propertyKey, currentCount, removeIndex);
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

                    int btnSize = UIUtils.dp2pxInt(16);
                    LayoutParams lp = new LayoutParams(btnSize, btnSize);
                    lp.topMargin = UIUtils.dp2pxInt(currentY + (UIConstants.Node.ROW_HEIGHT - 16) / 2f);

                    if (row.leftPort() != null) {
                        lp.gravity = icyllis.modernui.view.Gravity.TOP | icyllis.modernui.view.Gravity.RIGHT;
                        lp.rightMargin = UIUtils.dp2pxInt(8);
                    } else {
                        lp.gravity = icyllis.modernui.view.Gravity.TOP | icyllis.modernui.view.Gravity.LEFT;
                        lp.leftMargin = UIUtils.dp2pxInt(8);
                    }
                    addView(btn, lp);
                }
            }

            currentY += rowHeight;
        }

        // --- 2. Add 按钮 ---
        boolean isInputDynamic = mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
        boolean isOutputDynamic = mNodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT).isPresent();
        if (isInputDynamic || isOutputDynamic) {
            mAddButton = createDynamicButton(context, "+ Add Item", true, null, null);

            float inputBoxHeight = com.mine.geometry_node.client.ui.viewport.UIHints.UIHintUtils.getStandardInputHeight();
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

        // --- 3. 刷新整体尺寸 ---
        mTotalHeight = mLayout.totalHeight;

        LayoutParams lp = new LayoutParams(UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH), UIUtils.dp2pxInt(mTotalHeight));
        setLayoutParams(lp);
    }

    @Override
    public void updateNodeLayout() {
        syncAndLayoutUI(getContext());
    }

    @Override
    public boolean hasOverlayViews() {
        return mAddButton != null || !mRemoveButtons.isEmpty() || !mHintViews.isEmpty();
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

        float scaledRadius = UIUtils.dp2px(com.mine.geometry_node.client.ui.persistence.ConfigManager.INSTANCE.getConfig().node.cornerRadius);
        float scaledHeaderH = UIUtils.dp2px(UIConstants.Node.HEADER_HEIGHT);

        // 画节点背景
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(UIConstants.CLR_BG_NODE_BODY);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        // 画节点头部
        mPaint.setColor(mNodeDef.category().getColor());
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

    @Override
    public View findInteractiveViewAt(float localXpx, float localYpx) {
        // 检测 Add 按钮
        if (mAddButton != null && mAddButton.getVisibility() == View.VISIBLE && localXpx >= mAddButton.getLeft() && localXpx < mAddButton.getRight() && localYpx >= mAddButton.getTop() && localYpx < mAddButton.getBottom()) {
            return mAddButton;
        }
        // 检测 Remove 按钮
        for (View v : mRemoveButtons.values()) {
            if (v.getVisibility() == View.VISIBLE && localXpx >= v.getLeft() && localXpx < v.getRight() && localYpx >= v.getTop() && localYpx < v.getBottom()) return v;
        }
        // 检测输入框等 Hint
        for (View v : mHintViews.values()) {
            if (v.getVisibility() == View.VISIBLE && localXpx >= v.getLeft() && localXpx < v.getRight() && localYpx >= v.getTop() && localYpx < v.getBottom()) return v;
        }
        return null;
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
