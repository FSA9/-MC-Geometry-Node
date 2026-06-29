package com.mine.geometry_node.client.ui.viewport.node;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

public class UIRerouteNode extends FrameLayout implements NodeVisualAdapter {
    private static final float DIAMETER_DP = UIConstants.Node.PORT_VISUAL_RADIUS * 2.0f;
    private static final float PORT_CENTER_DP = UIConstants.Node.PORT_VISUAL_RADIUS;

    private final NodeData mNodeData;
    private final NodeDef mNodeDef;
    private final Paint mPaint = new Paint();

    private float mLogicX;
    private float mLogicY;
    private boolean mSelected;

    public UIRerouteNode(Context context, NodeData nodeData, NodeDef nodeDef) {
        super(context);
        this.mNodeData = nodeData;
        this.mNodeDef = nodeDef;
        setWillNotDraw(true);
        mPaint.setAntiAlias(true);
        int sizePx = UIUtils.dp2pxInt(DIAMETER_DP);
        setLayoutParams(new LayoutParams(sizePx, sizePx));
    }

    @Override
    public NodeData getNodeData() {
        return mNodeData;
    }

    @Override
    public NodeDef getNodeDef() {
        return mNodeDef;
    }

    @Override
    public void drawNode(Canvas canvas, ViewportCamera camera) {
        canvas.save();
        canvas.translate(camera.uiToScreenX(mLogicX), camera.uiToScreenY(mLogicY));
        canvas.scale(camera.getScale(), camera.getScale());
        drawLocal(canvas);
        canvas.restore();
    }

    private void drawLocal(Canvas canvas) {
        float center = UIUtils.dp2px(PORT_CENTER_DP);
        float radius = UIUtils.dp2px(UIConstants.Node.PORT_VISUAL_RADIUS);
        int portColor = RerouteNodeSupport.resolveLockedType(mNodeData).getColor();

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(portColor);
        canvas.drawCircle(center, center, radius, mPaint);

        if (mSelected) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(UIConstants.Node.STROKE_WIDTH_SELECTED);
            mPaint.setColor(UIConstants.CLR_WHITE);
            canvas.drawCircle(center, center, radius, mPaint);
        }
    }

    @Override
    public String hitTestPort(float localXdp, float localYdp, boolean checkInput, float touchRadiusDp) {
        float portX = PORT_CENTER_DP;
        float dx = localXdp - portX;
        float dy = localYdp - PORT_CENTER_DP;
        return dx * dx + dy * dy <= touchRadiusDp * touchRadiusDp
                ? (checkInput ? RerouteNodeSupport.INPUT_PORT : RerouteNodeSupport.OUTPUT_PORT)
                : null;
    }

    @Override
    public String hitTestLabel(float localXpx, float localYpx) {
        return null;
    }

    @Override
    public View findInteractiveViewAt(float localXpx, float localYpx) {
        return null;
    }

    @Override
    public View getOverlayHostView() {
        return null;
    }

    @Override
    public boolean hasOverlayViews() {
        return false;
    }

    @Override
    public int getTotalHeightDp() {
        return Math.round(DIAMETER_DP);
    }

    @Override
    public void updateNodeLayout() {
    }

    @Override
    public void getPortPosition(String portId, boolean isInput, float[] outPos) {
        outPos[0] = PORT_CENTER_DP;
        outPos[1] = PORT_CENTER_DP;
    }

    @Override
    public void setSelected(boolean selected) {
        if (mSelected != selected) {
            mSelected = selected;
            invalidate();
        }
    }

    @Override
    public boolean isSelected() {
        return mSelected;
    }

    @Override
    public void getLogicalBounds(RectF outRect) {
        outRect.set(mLogicX, mLogicY, mLogicX + DIAMETER_DP, mLogicY + DIAMETER_DP);
    }

    @Override
    public float getVisualWidthDp() {
        return DIAMETER_DP;
    }

    @Override
    public float getVisualHeightDp() {
        return DIAMETER_DP;
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
