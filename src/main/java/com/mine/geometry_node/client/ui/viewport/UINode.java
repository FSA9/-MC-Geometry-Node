package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.UIHints.HintRendererFactory;
import com.mine.geometry_node.client.ui.viewport.UIHints.UIHintRenderer;
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
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;

import java.util.HashMap;
import java.util.Map;

public class UINode extends FrameLayout {
    private float mLogicX = 0;
    private float mLogicY = 0;
    private final NodeData mNodeData;
    private final NodeDef mNodeDef;
    private final EditorContext mEditorContext;
    private boolean mIsSelected = false;
    private int mTotalHeight; // 逻辑单位 DP

    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final Map<String, Float> mInputPortY = new HashMap<>(); // 逻辑单位 DP
    private final Map<String, Float> mOutputPortY = new HashMap<>(); // 逻辑单位 DP

    private final Map<String, TextView> mPortLabels = new HashMap<>();
    private final Map<Integer, View> mHintViews = new HashMap<>();

    private View mAddButton;
    private final Map<String, View> mRemoveButtons = new HashMap<>();

    public UINode(Context context, NodeData nodeData, NodeDef nodeDef, EditorContext editorContext) {
        super(context);
        this.mNodeData = nodeData;
        this.mNodeDef = nodeDef;
        this.mEditorContext = editorContext;

        setWillNotDraw(false);
        setClipChildren(false);
        mPaint.setAntiAlias(true);

        syncAndLayoutUI(context);
    }

    public String hitTestLabel(float localXpx, float localYpx) {
        for (Map.Entry<String, TextView> entry : mPortLabels.entrySet()) {
            View tv = entry.getValue();
            if (tv.getVisibility() == View.VISIBLE &&
                    localXpx >= tv.getLeft() && localXpx <= tv.getRight() &&
                    localYpx >= tv.getTop() && localYpx <= tv.getBottom()) {
                return entry.getKey();
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

    private TextView createLabel(Context context, String text, int gravity) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(UIConstants.CLR_GRAY_LABEL);
        tv.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        tv.setGravity(gravity | icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        tv.setClickable(false);
        tv.setFocusable(false);
        return tv;
    }

    private String getPortCategory(com.mine.geometry_node.core.node.port.PortDef port, boolean isLeft) {
        boolean isExec = port.type() == com.mine.geometry_node.core.node.port.PortType.EXECUTION;

        if (isLeft) {
            return isExec ? "exec_inputs" : "inputs";
        } else {
            return isExec ? "exec_outputs" : "outputs";
        }
    }

    private void syncAndLayoutUI(Context context) {
        mInputPortY.clear();
        mOutputPortY.clear();

        removeAllViews();
        mPortLabels.clear();
        mHintViews.clear();
        mRemoveButtons.clear();
        mAddButton = null;

        // --- 1. 重建 Header ---
        TextView titleView = new TextView(context);
        titleView.setText(mNodeDef.displayName().getString());
        titleView.setTextColor(UIConstants.CLR_WHITE);
        titleView.setTextSize(UIConstants.Node.TEXT_SIZE_HEADER);
        titleView.setGravity(icyllis.modernui.view.Gravity.CENTER);
        addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(UIConstants.Node.HEADER_HEIGHT)));

        float currentY = UIConstants.Node.HEADER_HEIGHT; // 逻辑单位 DP

        // --- 2. 遍历行，边动态构建边排版 ---
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float rowHeight = calculateRowHeight(row);
            float portCenterY = currentY + UIConstants.Node.ROW_HEIGHT / 2.0f;

            // 左侧端口处理
            if (row.leftPort() != null) {
                if (!row.leftPort().hidePin()) {
                    mInputPortY.put(row.leftPort().id(), portCenterY);
                }

                String category = getPortCategory(row.leftPort(), true);
                String defaultName = row.leftPort().displayName().getString();
                String effectiveName = mNodeData.getEffectivePortName(category, row.leftPort().id(), defaultName);

                TextView tv = createLabel(context, effectiveName, icyllis.modernui.view.Gravity.LEFT);
                mPortLabels.put(row.leftPort().id(), tv);

                // 立即计算 LayoutParams
                int leftMargin = UIConstants.Node.LABEL_MARGIN_PORT;
                if (row.uiHint() == UIHint.CHECKBOX && !mNodeData.isInputConnected(row.leftPort().id())) {
                    // Checkbox 间距计算
                    leftMargin = UIConstants.Node.LABEL_MARGIN_PORT + UIConstants.Node.CHECKBOX_DEFAULT_WIDTH + UIConstants.Node.MARGIN_CHECKBOX_GAP;
                }

                LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(UIConstants.Node.ROW_HEIGHT));
                lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
                lp.leftMargin = UIUtils.dp2pxInt(leftMargin);
                lp.topMargin = UIUtils.dp2pxInt(currentY);
                tv.setSingleLine(true);
                addView(tv, lp);
            }

            // 右侧端口处理
            if (row.rightPort() != null) {
                mOutputPortY.put(row.rightPort().id(), portCenterY);

                String category = getPortCategory(row.rightPort(), false);
                String defaultName = row.rightPort().displayName().getString();
                String effectiveName = mNodeData.getEffectivePortName(category, row.rightPort().id(), defaultName);

                TextView tv = createLabel(context, effectiveName, icyllis.modernui.view.Gravity.RIGHT);
                mPortLabels.put(row.rightPort().id(), tv);

                LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(UIConstants.Node.ROW_HEIGHT));
                lp.gravity = icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.TOP;
                lp.rightMargin = UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT);
                lp.topMargin = UIUtils.dp2pxInt(currentY);
                tv.setSingleLine(true);
                addView(tv, lp);
            }

            // Hint 内嵌输入组件处理
            if (row.uiHint() != null) {
                UIHintRenderer renderer = HintRendererFactory.getRenderer(row.uiHint());
                if (renderer != null) {
                    View hintView = renderer.createView(context, mNodeData, row, mEditorContext);
                    if (hintView != null) {
                        mHintViews.put(i, hintView);
                        addView(hintView, new LayoutParams(0, 0)); // 占位，由 renderer 内部 updateLayout 撑开

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

        // --- 3. Add 按钮 ---
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

        // --- 4. 刷新整体尺寸 ---
        mTotalHeight = (int) currentY;
        if (mNodeData.uiSize == null) mNodeData.uiSize = new float[2];
        mNodeData.uiSize[0] = UIConstants.Node.NODE_WIDTH;
        mNodeData.uiSize[1] = mTotalHeight;

        LayoutParams lp = new LayoutParams(UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH), UIUtils.dp2pxInt(mTotalHeight));
        lp.leftMargin = UIUtils.dp2pxInt(mLogicX);
        lp.topMargin = UIUtils.dp2pxInt(mLogicY);
        setLayoutParams(lp);

        invalidate();
    }

    public void updateNodeLayout() {
        syncAndLayoutUI(getContext());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight() > 0 ? getHeight() : UIUtils.dp2px(mTotalHeight);

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
                Float yDp = mInputPortY.get(row.leftPort().id());
                if (yDp != null) {
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(row.leftPort().type().getColor());
                    canvas.drawCircle(0, UIUtils.dp2px(yDp), UIUtils.dp2px(UIConstants.Node.PORT_VISUAL_RADIUS), mPaint);
                }
            }
            if (row.rightPort() != null) {
                Float yDp = mOutputPortY.get(row.rightPort().id());
                if (yDp != null) {
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(row.rightPort().type().getColor());
                    canvas.drawCircle(w, UIUtils.dp2px(yDp), UIUtils.dp2px(UIConstants.Node.PORT_VISUAL_RADIUS), mPaint);
                }
            }
        }
        super.onDraw(canvas);
    }

    private boolean isDynamicRow(PortRow row) { return row.hintParams() != null && Boolean.TRUE.equals(row.hintParams().get(PortMetaKeys.IS_DYNAMIC)); }

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) { return true; }
    @Override public boolean onInterceptHoverEvent(MotionEvent event) { return true; }
    @Override public PointerIcon onResolvePointerIcon(MotionEvent event) { return PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT); }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float lx = UIUtils.px2dp(ev.getX());
        float ly = UIUtils.px2dp(ev.getY());
        float interceptRadius = UIConstants.Node.PORT_HITBOX_RADIUS;
        if (hitTestPort(lx, ly, true, interceptRadius) != null || hitTestPort(lx, ly, false, interceptRadius) != null) return false;
        return super.dispatchTouchEvent(ev);
    }

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

    public String hitTestPort(float localXdp, float localYdp, boolean checkInput, float touchRadiusDp) {
        float targetX = checkInput ? 0 : UIConstants.Node.NODE_WIDTH;
        float dx = localXdp - targetX;
        Map<String, Float> map = checkInput ? mInputPortY : mOutputPortY;
        float thresholdSq = touchRadiusDp * touchRadiusDp;
        String best = null; float bestDistSq = Float.MAX_VALUE;
        for (Map.Entry<String, Float> entry : map.entrySet()) {
            float dy = localYdp - entry.getValue();
            float distSq = dx * dx + dy * dy;
            if (distSq <= thresholdSq && distSq < bestDistSq) { bestDistSq = distSq; best = entry.getKey(); }
        }
        return best;
    }

    private float calculateRowHeight(PortRow row) {
        float height = UIConstants.Node.ROW_HEIGHT;
        if (row.uiHint() == null) return height;
        if (!mNodeData.isInputConnected(row.leftPort() != null ? row.leftPort().id() : "")) {
            UIHintRenderer renderer = HintRendererFactory.getRenderer(row.uiHint());
            float extraRows = (renderer != null) ? renderer.getRequiredExtraRows(row) : 0.0f;
            height = UIConstants.Node.ROW_HEIGHT * (row.leftPort() != null || row.rightPort() != null ? 1.0f + extraRows : Math.max(1.0f, extraRows));
        }
        return height;
    }

    public void getPortPosition(String portId, boolean isInput, float[] outPos) {
        outPos[0] = isInput ? 0 : UIConstants.Node.NODE_WIDTH;
        Float y = isInput ? mInputPortY.get(portId) : mOutputPortY.get(portId);
        outPos[1] = (y != null) ? y : UIConstants.Node.HEADER_HEIGHT + UIConstants.Node.ROW_HEIGHT / 2.0f;
    }

    public NodeData getNodeData() { return mNodeData; }
    public NodeDef getNodeDef() { return mNodeDef; }
    public void setSelected(boolean selected) { if (mIsSelected != selected) { mIsSelected = selected; invalidate(); } }
    public boolean isSelected() { return mIsSelected; }

    public void getLogicalBounds(RectF outRect) {
        outRect.set(getTranslationX(), getTranslationY(), getTranslationX() + UIConstants.Node.NODE_WIDTH, getTranslationY() + mTotalHeight);
    }

    @Override
    public void setTranslationX(float translationX) {
        mLogicX = translationX;
        super.setTranslationX(0);
        updateMarginPos();
    }

    @Override
    public void setTranslationY(float translationY) {
        mLogicY = translationY;
        super.setTranslationY(0);
        updateMarginPos();
    }

    @Override
    public float getTranslationX() {
        return mLogicX;
    }

    @Override
    public float getTranslationY() {
        return mLogicY;
    }

    private void updateMarginPos() {
        icyllis.modernui.view.ViewGroup.LayoutParams params = getLayoutParams();
        if (params instanceof LayoutParams lp) {
            lp.leftMargin = UIUtils.dp2pxInt(mLogicX);
            lp.topMargin = UIUtils.dp2pxInt(mLogicY);
            setLayoutParams(lp);
        }
    }
}