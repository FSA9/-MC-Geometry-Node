package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.Viewport.UIHints.HintRendererFactory;
import com.mine.geometry_node.client.ui.Viewport.UIHints.UIHintRenderer;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UINode extends FrameLayout {

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

    public record DynamicActionInfo(boolean isAdd, String referencePortId) {}

    private static class RowLayoutMetrics {
        float topY;        // DP
        float height;      // DP
        RectF btnHitbox;   // DP
        boolean isAddBtn;
        String refPortId;
    }

    private final List<RowLayoutMetrics> mRowMetrics = new ArrayList<>();

    public UINode(Context context, NodeData nodeData, NodeDef nodeDef, EditorContext editorContext) {
        super(context);
        this.mNodeData = nodeData;
        this.mNodeDef = nodeDef;
        this.mEditorContext = editorContext;

        setWillNotDraw(false);
        setClipChildren(false);
        mPaint.setAntiAlias(true);

        buildUIElements(context);
        updateNodeLayout();
    }

    private void buildUIElements(Context context) {
        TextView titleView = new TextView(context);
        titleView.setText(mNodeDef.displayName().getString());
        titleView.setTextColor(UIConstants.CLR_WHITE);
        titleView.setTextSize(UIConstants.Node.TEXT_SIZE_HEADER);
        titleView.setGravity(icyllis.modernui.view.Gravity.CENTER);
        titleView.setClickable(false);
        titleView.setFocusable(false);
        addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(UIConstants.Node.HEADER_HEIGHT)));

        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            if (row.leftPort() != null) {
                TextView tv = createLabel(context, row.leftPort().displayName().getString(), icyllis.modernui.view.Gravity.LEFT);
                mPortLabels.put(row.leftPort().id(), tv);
                addView(tv, new LayoutParams(LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(UIConstants.Node.ROW_HEIGHT)));
            }
            if (row.rightPort() != null) {
                TextView tv = createLabel(context, row.rightPort().displayName().getString(), icyllis.modernui.view.Gravity.RIGHT);
                mPortLabels.put(row.rightPort().id(), tv);
                addView(tv, new LayoutParams(LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(UIConstants.Node.ROW_HEIGHT)));
            }
            UIHint hint = row.uiHint();
            if (hint != null) {
                UIHintRenderer renderer = HintRendererFactory.getRenderer(hint);
                if (renderer != null) {
                    View hintView = renderer.createView(context, mNodeData, row, mEditorContext);
                    if (hintView != null) {
                        mHintViews.put(i, hintView);
                        addView(hintView, new LayoutParams(0, 0));
                    }
                }
            }
        }
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

    public void updateNodeLayout() {
        mInputPortY.clear();
        mOutputPortY.clear();
        mRowMetrics.clear();

        float currentY = UIConstants.Node.HEADER_HEIGHT; // DP

        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float rowHeight = calculateRowHeight(row); // 返回 DP
            float portCenterY = currentY + UIConstants.Node.ROW_HEIGHT / 2.0f;

            RowLayoutMetrics metrics = new RowLayoutMetrics();
            metrics.topY = currentY;
            metrics.height = rowHeight;

            // --- 3. 左侧标签排版 ---
            if (row.leftPort() != null) {
                mInputPortY.put(row.leftPort().id(), portCenterY);
                TextView tv = mPortLabels.get(row.leftPort().id());
                if (tv != null) {
                    LayoutParams lp = (LayoutParams) tv.getLayoutParams();

                    // 修复1：默认左边距严格对齐右边距
                    int leftMargin = UIConstants.Node.LABEL_MARGIN_PORT;

                    // 仅当明确是未连接的 CheckBox 时，才增加左边距留出框的位置
                    if (row.uiHint() == UIHint.CHECKBOX && !mNodeData.isInputConnected(row.leftPort().id())) {
                        View cbView = mHintViews.get(i);
                        int cbWidthDp = UIConstants.Node.CHECKBOX_DEFAULT_WIDTH;
                        if (cbView != null) {
                            cbView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                            if (cbView.getMeasuredWidth() > 0) cbWidthDp = (int)UIUtils.px2dp(cbView.getMeasuredWidth());
                        }
                        leftMargin = UIConstants.Node.LABEL_MARGIN_PORT + cbWidthDp + UIConstants.Node.MARGIN_CHECKBOX_GAP;
                    }

                    lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
                    lp.leftMargin = UIUtils.dp2pxInt(leftMargin);
                    lp.topMargin = UIUtils.dp2pxInt(currentY);
                    tv.setLayoutParams(lp);

                    // 限制最大宽度
//                    int maxLabelWidth = (UIConstants.Node.NODE_WIDTH / 2) - leftMargin - 4;
//                    tv.setMaxWidth(UIUtils.dp2pxInt(Math.max(10, maxLabelWidth)));
                    tv.setSingleLine(true);
                }
            }

            // --- 4. 右侧标签排版 ---
            if (row.rightPort() != null) {
                mOutputPortY.put(row.rightPort().id(), portCenterY);
                TextView tv = mPortLabels.get(row.rightPort().id());
                if (tv != null) {
                    LayoutParams lp = (LayoutParams) tv.getLayoutParams();
                    lp.gravity = icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.TOP;
                    lp.rightMargin = UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT);
                    lp.topMargin = UIUtils.dp2pxInt(currentY);
                    tv.setLayoutParams(lp);

                    // 限制最大宽度
//                    int maxLabelWidth = (UIConstants.Node.NODE_WIDTH / 2) - UIConstants.Node.LABEL_MARGIN_PORT - 4;
//                    tv.setMaxWidth(UIUtils.dp2pxInt(Math.max(10, maxLabelWidth)));
                    tv.setSingleLine(true);
                }
            }

            View hintView = mHintViews.get(i);
            if (row.uiHint() != null && hintView != null) {
                hintView.setVisibility(mNodeData.isInputConnected(row.leftPort() != null ? row.leftPort().id() : "") ? View.GONE : View.VISIBLE);
                UIHintRenderer renderer = HintRendererFactory.getRenderer(row.uiHint());
                if (renderer != null) renderer.updateLayout(hintView, row, currentY, UIConstants.Node.NODE_WIDTH);
            }

            if (isDynamicRow(row)) {
                boolean isLast = (i == mNodeDef.rows().size() - 1) || !isDynamicRow(mNodeDef.rows().get(i + 1));
                metrics.isAddBtn = isLast;
                String refId = row.leftPort() != null ? row.leftPort().id() : (row.rightPort() != null ? row.rightPort().id() : "");
                metrics.refPortId = refId;

                float cx, cy, rowBottom = currentY + rowHeight;
                if (isLast) { cx = UIConstants.Node.NODE_WIDTH / 2.0f; cy = rowBottom; }
                else { cx = UIConstants.Node.NODE_WIDTH - (UIConstants.Node.DYNAMIC_BTN_OFFSET_DP / UIConstants.mDensity); cy = rowBottom - (UIConstants.Node.ROW_HEIGHT / 2.0f); }
                float tol = UIConstants.Node.DYNAMIC_BTN_HITBOX_TOLERANCE_DP;
                metrics.btnHitbox = new RectF(cx - tol, cy - tol, cx + tol, cy + tol);
            }
            mRowMetrics.add(metrics);
            currentY += rowHeight;
        }

        mTotalHeight = (int) currentY;
        setLayoutParams(new LayoutParams(UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH), UIUtils.dp2pxInt(mTotalHeight)));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight() > 0 ? getHeight() : UIUtils.dp2px(mTotalHeight);

        float scaledRadius = UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().node.cornerRadius);
        float scaledHeaderH = UIUtils.dp2px(UIConstants.Node.HEADER_HEIGHT);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(UIConstants.CLR_BG_NODE_BODY);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        mPaint.setColor(mNodeDef.category().getColor());
        mTempRect.set(0, 0, w, scaledHeaderH);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, 0f, 0f, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mIsSelected ? UIConstants.Node.STROKE_WIDTH_SELECTED : UIConstants.Node.STROKE_WIDTH_NORMAL);
        mPaint.setColor(mIsSelected ? UIConstants.CLR_WHITE : UIConstants.CLR_NODE_OUTLINE);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, scaledRadius, scaledRadius, scaledRadius, scaledRadius, mPaint);

        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            RowLayoutMetrics metrics = mRowMetrics.get(i);
            float centerYpx = UIUtils.dp2px(metrics.topY + UIConstants.Node.ROW_HEIGHT / 2.0f);

            if (row.leftPort() != null) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(row.leftPort().type().getColor());
                canvas.drawCircle(0, centerYpx, UIUtils.dp2px(UIConstants.Node.PORT_VISUAL_RADIUS), mPaint);
            }
            if (row.rightPort() != null) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(row.rightPort().type().getColor());
                canvas.drawCircle(w, centerYpx, UIUtils.dp2px(UIConstants.Node.PORT_VISUAL_RADIUS), mPaint);
            }
            if (metrics.btnHitbox != null) {
                float cx = metrics.isAddBtn ? (w / 2.0f) : (w - UIUtils.dp2px(UIConstants.Node.DYNAMIC_BTN_OFFSET_DP));
                float cypx = UIUtils.dp2px(metrics.topY + metrics.height);
                if (!metrics.isAddBtn) cypx -= UIUtils.dp2px(UIConstants.Node.ROW_HEIGHT / 2.0f);
                drawDynamicButton(canvas, cx, cypx, metrics.isAddBtn);
            }
        }
        super.onDraw(canvas);
    }

    private void drawDynamicButton(Canvas canvas, float cx, float cy, boolean isAdd) {
        float halfSize = UIUtils.dp2px(UIConstants.Node.DYNAMIC_BTN_SIZE_DP / 2.0f);
        float iconHalf = UIUtils.dp2px(UIConstants.Node.DYNAMIC_BTN_ICON_SIZE_DP / 2.0f);

        mPaint.setColor(UIConstants.Node.CLR_DYNAMIC_BTN_BG);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, mPaint);

        mPaint.setColor(UIConstants.Node.CLR_DYNAMIC_BTN_FG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(UIConstants.Node.DYNAMIC_BTN_STROKE_WIDTH);
        canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, mPaint);

        if (isAdd) {
            canvas.drawLine(cx - iconHalf, cy, cx + iconHalf, cy, mPaint);
            canvas.drawLine(cx, cy - iconHalf, cx, cy + iconHalf, mPaint);
        } else {
            canvas.drawLine(cx - iconHalf, cy, cx + iconHalf, cy, mPaint);
        }
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

    public DynamicActionInfo hitTestDynamicButton(float localXdp, float localYdp) {
        float toleranceDp = UIConstants.Node.DYNAMIC_BTN_TOUCH_TOLERANCE_DP;
        for (RowLayoutMetrics metrics : mRowMetrics) {
            if (metrics.btnHitbox != null && metrics.btnHitbox.contains(localXdp, localYdp)) return new DynamicActionInfo(metrics.isAddBtn, metrics.refPortId);
        }
        return null;
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
    public void getLogicalBounds(RectF outRect) { outRect.set(getTranslationX(), getTranslationY(), getTranslationX() + UIConstants.Node.NODE_WIDTH, getTranslationY() + mTotalHeight); }
}