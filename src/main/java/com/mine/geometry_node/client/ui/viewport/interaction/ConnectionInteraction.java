package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

final class ConnectionInteraction {
    private final InteractionContext mContext;
    private InteractionManager.InteractionListener mListener;

    private Viewport.PortInfo mDraftStartPort = null;
    private float mDraftCurrentUiX, mDraftCurrentUiY;
    private final Paint mDraftLinePaint = new Paint();
    private final float[] mTempPos = new float[2];

    private final List<Float> mCutPath = new ArrayList<>();
    private final Paint mCutLinePaint = new Paint();

    ConnectionInteraction(InteractionContext context) {
        this.mContext = context;
        initPaints();
    }

    void setListener(InteractionManager.InteractionListener listener) {
        this.mListener = listener;
    }

    private void initPaints() {
        mDraftLinePaint.setAntiAlias(true);
        mDraftLinePaint.setStyle(Paint.Style.STROKE);
        mDraftLinePaint.setColor(UIConstants.ViewPort.Connection.CLR_DRAFT_LINE);

        mCutLinePaint.setAntiAlias(true);
        mCutLinePaint.setStyle(Paint.Style.STROKE);
        mCutLinePaint.setColor(0xFFFF4444);
        mCutLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mCutLinePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    void beginConnection(Viewport.PortInfo port, float uiX, float uiY) {
        mDraftStartPort = port;
        mDraftCurrentUiX = uiX;
        mDraftCurrentUiY = uiY;
    }

    void updateDraft(float uiX, float uiY) {
        mDraftCurrentUiX = uiX;
        mDraftCurrentUiY = uiY;
        mContext.invalidate();
    }

    void finalizeConnection(float endUiX, float endUiY) {
        Viewport.PortInfo endPort = mContext.findPortAt(endUiX, endUiY);
        if (isValidConnection(mDraftStartPort, endPort)) {
            Viewport.PortInfo input = mDraftStartPort.isInput ? mDraftStartPort : endPort;
            Viewport.PortInfo output = mDraftStartPort.isInput ? endPort : mDraftStartPort;
            if (!mContext.hasConnection(output.node, output.portId, input.node, input.portId)) {
                if (mListener != null) {
                    mListener.onConnectPorts(output.node.getNodeId(), output.portId, input.node.getNodeId(), input.portId);
                }
            }
        }
        mDraftStartPort = null;
    }

    void beginCut(float uiX, float uiY) {
        mCutPath.clear();
        mCutPath.add(uiX);
        mCutPath.add(uiY);
    }

    void updateCut(float uiX, float uiY) {
        if (mCutPath.size() >= 2) {
            float lastPathX = mCutPath.get(mCutPath.size() - 2);
            float lastPathY = mCutPath.get(mCutPath.size() - 1);

            float distSq = (uiX - lastPathX) * (uiX - lastPathX) + (uiY - lastPathY) * (uiY - lastPathY);
            float minSegmentUi = UIUtils.dp2px(2);
            if (distSq > minSegmentUi * minSegmentUi) {
                mCutPath.add(uiX);
                mCutPath.add(uiY);
                mContext.cutIntersectingConnections(lastPathX, lastPathY, uiX, uiY, mListener);
            }
        }
        mContext.invalidate();
    }

    void clearCut() {
        mCutPath.clear();
    }

    void drawDraftLine(Canvas canvas) {
        if (mDraftStartPort == null) return;

        ViewportCamera camera = mContext.getCamera();
        mDraftLinePaint.setStrokeWidth(UIUtils.dp2px(UIConstants.ViewPort.Connection.LINE_WIDTH_DRAFT) * camera.getScale());
        mDraftStartPort.node.getPortPosition(mDraftStartPort.portId, mDraftStartPort.isInput, mTempPos);
        canvas.drawLine(camera.uiToScreenX(mDraftStartPort.node.getUiX() + mTempPos[0]),
                camera.uiToScreenY(mDraftStartPort.node.getUiY() + mTempPos[1]),
                camera.uiToScreenX(mDraftCurrentUiX), camera.uiToScreenY(mDraftCurrentUiY), mDraftLinePaint);
    }

    void drawCutPath(Canvas canvas) {
        if (mCutPath.size() < 4) return;

        ViewportCamera camera = mContext.getCamera();
        mCutLinePaint.setStrokeWidth(UIUtils.dp2px(2.0f) * camera.getScale());
        for (int i = 0; i < mCutPath.size() - 2; i += 2) {
            float sx1 = camera.uiToScreenX(mCutPath.get(i));
            float sy1 = camera.uiToScreenY(mCutPath.get(i + 1));
            float sx2 = camera.uiToScreenX(mCutPath.get(i + 2));
            float sy2 = camera.uiToScreenY(mCutPath.get(i + 3));
            canvas.drawLine(sx1, sy1, sx2, sy2, mCutLinePaint);
        }
    }

    private boolean isValidConnection(Viewport.PortInfo s, Viewport.PortInfo e) {
        if (s == null || e == null || s.node.getNodeId().equals(e.node.getNodeId()) || s.isInput == e.isInput) return false;
        Viewport.PortInfo output = s.isInput ? e : s;
        Viewport.PortInfo input = s.isInput ? s : e;
        PortType outputType = getPortType(output);
        PortType inputType = getPortType(input);
        boolean typeCompatible = isExecutionToVirtualAny(output, outputType, input, inputType)
                || PortType.isCompatible(outputType, inputType);
        return typeCompatible && mContext.canConnectPorts(output.node.getNodeId(), output.portId, input.node.getNodeId(), input.portId);
    }

    private boolean isExecutionToVirtualAny(Viewport.PortInfo output, PortType outputType, Viewport.PortInfo input, PortType inputType) {
        return (outputType == PortType.EXECUTION && inputType == PortType.ANY && isGroupVirtualBoundaryPort(input))
                || (inputType == PortType.EXECUTION && outputType == PortType.ANY && isGroupVirtualBoundaryPort(output));
    }

    private boolean isGroupVirtualBoundaryPort(Viewport.PortInfo portInfo) {
        if (portInfo == null || portInfo.node == null || portInfo.node.getNodeData() == null) return false;
        return portInfo.node.getNodeData().isGroupInputNode() || portInfo.node.getNodeData().isGroupOutputNode();
    }

    private PortType getPortType(Viewport.PortInfo portInfo) {
        if (portInfo == null || portInfo.node == null) return null;
        for (PortRow row : portInfo.node.getNodeDef().rows()) {
            if (portInfo.isInput) {
                if (row.leftPort() != null && row.leftPort().id().equals(portInfo.portId)) return row.leftPort().type();
            } else {
                if (row.rightPort() != null && row.rightPort().id().equals(portInfo.portId)) return row.rightPort().type();
            }
        }
        return null;
    }
}
