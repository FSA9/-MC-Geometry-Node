package com.mine.geometry_node.client.ui.editor.graph.node;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.ViewportCamera;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;

import java.util.HashMap;
import java.util.Map;

public class UINode extends FrameLayout implements NodeVisualAdapter {
    public static final int COMMENT_POPUP_WIDTH_DP = NodeOverlayController.COMMENT_POPUP_WIDTH_DP;

    private float mLogicX = 0;
    private float mLogicY = 0;
    private final NodeData mNodeData;
    private final NodeDef mNodeDef;
    private boolean mIsSelected = false;
    private int mWidth; // 逻辑单位 DP
    private int mTotalHeight; // 逻辑单位 DP

    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final NodeLayoutEngine mLayoutEngine = new NodeLayoutEngine();
    private final NodeOverlayController mOverlayController;
    private NodeLayout mLayout;

    public UINode(Context context, NodeData nodeData, NodeDef nodeDef, EditorContext editorContext) {
        super(context);
        this.mNodeData = nodeData;
        this.mNodeDef = nodeDef;
        this.mOverlayController = new NodeOverlayController(this, nodeData, nodeDef, editorContext, mLayoutEngine);

        setWillNotDraw(false);
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

    private void syncAndLayoutUI(Context context) {
        boolean overlayWasMounted = mOverlayController.isMounted();
        mLayout = mLayoutEngine.build(mNodeData, mNodeDef);
        mOverlayController.setLayout(mLayout);
        if (overlayWasMounted) {
            mOverlayController.rebuild(context);
        }

        // --- 3. 刷新整体尺寸 ---
        mWidth = mLayout.width;
        mTotalHeight = mLayout.totalHeight;

        LayoutParams lp = new LayoutParams(UIUtils.dp2pxInt(mWidth), UIUtils.dp2pxInt(mTotalHeight));
        setLayoutParams(lp);
    }

    public static boolean closeOpenCommentPopup() {
        return NodeOverlayController.closeOpenCommentPopup();
    }

    public static boolean isCommentButton(View view) {
        return view != null
                && view.getParent() instanceof UINode node
                && node.mOverlayController.isCommentButtonView(view);
    }

    public static boolean isCommentView(View view) {
        return view != null
                && view.getParent() instanceof UINode node
                && node.mOverlayController.isCommentView(view);
    }

    @Override
    public void updateNodeLayout() {
        syncAndLayoutUI(getContext());
    }

    @Override
    public boolean hasOverlayViews() {
        return mOverlayController.hasOverlayViews();
    }

    @Override
    public boolean ensureOverlayViews() {
        return mOverlayController.ensureMounted(getContext());
    }

    @Override
    public void releaseOverlayViews() {
        mOverlayController.release();
    }

    @Override
    public boolean isOverlayActive() {
        return mOverlayController.isMounted()
                && (findFocus() != null || mOverlayController.isCommentPopupVisible());
    }

    public int getOverlayWidthDp() {
        return mOverlayController.getOverlayWidthDp();
    }

    public int getOverlayHeightDp() {
        return mOverlayController.getOverlayHeightDp();
    }

    @Override
    public void onOverlayScaleChanged(float scale) {
        mOverlayController.onOverlayScaleChanged(scale);
    }

    @Override
    public void onOverlayTransformChanged(float scale, float windowLeftPx, float windowTopPx) {
        onOverlayTransformChanged(scale, windowLeftPx, windowTopPx, 0);
    }

    @Override
    public void onOverlayTransformChanged(float scale, float windowLeftPx, float windowTopPx, int overlayOrder) {
        mOverlayController.onOverlayTransformChanged(scale, windowLeftPx, windowTopPx, overlayOrder);
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

    public void drawNodeForExport(Canvas canvas, ViewportCamera camera) {
        boolean overlayWasMounted = mOverlayController.isMounted();
        if (!mOverlayController.ensureMounted(getContext())) {
            drawNode(canvas, camera);
            return;
        }

        boolean selected = mIsSelected;
        int commentVisibility = -1;
        boolean canvasSaved = false;
        try {
            int widthPx = UIUtils.dp2pxInt(mWidth);
            int heightPx = UIUtils.dp2pxInt(mTotalHeight);
            int left = getLeft();
            int top = getTop();
            measure(
                    MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY)
            );
            layout(left, top, left + widthPx, top + heightPx);

            commentVisibility = mOverlayController.hideCommentPopupForExport();
            mIsSelected = false;
            canvas.save();
            canvasSaved = true;
            canvas.translate(camera.uiToScreenX(mLogicX), camera.uiToScreenY(mLogicY));
            canvas.scale(camera.getScale(), camera.getScale());
            draw(canvas);
        } finally {
            if (canvasSaved) canvas.restore();
            mIsSelected = selected;
            mOverlayController.restoreCommentPopupAfterExport(commentVisibility);
            if (!overlayWasMounted) {
                mOverlayController.release();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawNodeLocal(canvas);
    }

    private void drawNodeLocal(Canvas canvas) {
        float w = UIUtils.dp2px(mWidth);
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

    @Override
    public View findInteractiveViewAt(float localXpx, float localYpx) {
        if (!mOverlayController.isMounted() && !mOverlayController.mayContainInteractiveView(localXpx, localYpx)) {
            return null;
        }
        if (!mOverlayController.isMounted() && !mOverlayController.ensureMounted(getContext())) {
            return null;
        }
        return mOverlayController.findInteractiveViewAt(localXpx, localYpx);
    }

    @Override
    public View getOverlayHostView() {
        return this;
    }

    @Override
    public String hitTestPort(float localXdp, float localYdp, boolean checkInput, float touchRadiusDp) {
        float targetX = checkInput ? 0 : mWidth;
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
        outPos[0] = isInput ? 0 : mWidth;
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
        outRect.set(mLogicX, mLogicY, mLogicX + mWidth, mLogicY + mTotalHeight);
    }

    @Override
    public float getVisualWidthDp() {
        return mWidth;
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
