package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.client.ui.viewport.connection.ConnectionLayer;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ConnectionInteraction {
    private final InteractionContext mContext;
    private InteractionManager.InteractionListener mListener;

    private Viewport.PortInfo mDraftStartPort = null;
    private float mDraftCurrentUiX, mDraftCurrentUiY;
    private final Paint mDraftLinePaint = new Paint();
    private final float[] mTempPos = new float[2];

    private final List<Float> mCutPath = new ArrayList<>();
    private final Paint mCutLinePaint = new Paint();
    private final Set<ConnectionKey> mHandledInsertKeys = new HashSet<>();
    private boolean mInsertRerouteMode;

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
        ConnectionCandidate candidate = resolveConnectionCandidate(mDraftStartPort, endPort);
        if (candidate != null) {
            Viewport.PortInfo output = candidate.output;
            Viewport.PortInfo input = candidate.input;
            if (!mContext.hasConnection(output.node, output.portId, input.node, input.portId)) {
                if (mListener != null) {
                    mListener.onConnectPorts(output.node.getNodeId(), output.portId, input.node.getNodeId(), input.portId);
                }
            }
        }
        mDraftStartPort = null;
    }

    private ConnectionCandidate resolveConnectionCandidate(Viewport.PortInfo start, Viewport.PortInfo end) {
        ConnectionCandidate direct = createCandidateIfValid(start, end);
        if (direct != null) return direct;

        Viewport.PortInfo alternateStart = alternateReroutePort(start);
        ConnectionCandidate startSwapped = createCandidateIfValid(alternateStart, end);
        if (startSwapped != null) return startSwapped;

        Viewport.PortInfo alternateEnd = alternateReroutePort(end);
        ConnectionCandidate endSwapped = createCandidateIfValid(start, alternateEnd);
        if (endSwapped != null) return endSwapped;

        return createCandidateIfValid(alternateStart, alternateEnd);
    }

    private ConnectionCandidate createCandidateIfValid(Viewport.PortInfo start, Viewport.PortInfo end) {
        if (start == null || end == null || start.node.getNodeId().equals(end.node.getNodeId()) || start.isInput == end.isInput) {
            return null;
        }
        Viewport.PortInfo output = start.isInput ? end : start;
        Viewport.PortInfo input = start.isInput ? start : end;
        if (!mContext.canConnectPorts(output.node.getNodeId(), output.portId, input.node.getNodeId(), input.portId)) {
            return null;
        }
        return new ConnectionCandidate(output, input);
    }

    private Viewport.PortInfo alternateReroutePort(Viewport.PortInfo port) {
        if (port == null || port.node == null || !RerouteNodeSupport.isReroute(port.node.getNodeData())) {
            return null;
        }
        return new Viewport.PortInfo(
                port.node,
                port.isInput ? RerouteNodeSupport.OUTPUT_PORT : RerouteNodeSupport.INPUT_PORT,
                !port.isInput
        );
    }

    void beginCut(float uiX, float uiY, boolean insertRerouteMode) {
        mCutPath.clear();
        mHandledInsertKeys.clear();
        mInsertRerouteMode = insertRerouteMode;
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
                if (mInsertRerouteMode) {
                    insertRerouteOnIntersectingConnections(lastPathX, lastPathY, uiX, uiY);
                } else {
                    mContext.cutIntersectingConnections(lastPathX, lastPathY, uiX, uiY, mListener);
                }
            }
        }
        mContext.invalidate();
    }

    void clearCut() {
        mCutPath.clear();
        mHandledInsertKeys.clear();
        mInsertRerouteMode = false;
    }

    private void insertRerouteOnIntersectingConnections(float lastUiX, float lastUiY, float currentUiX, float currentUiY) {
        if (mListener == null) return;

        for (ConnectionLayer.ConnectionHit hit : mContext.findIntersectingConnections(lastUiX, lastUiY, currentUiX, currentUiY)) {
            ConnectionKey key = new ConnectionKey(
                    hit.outNodeId(), hit.outPortId(), hit.inNodeId(), hit.inPortId());
            if (mHandledInsertKeys.add(key)) {
                mListener.onInsertReroute(hit);
            }
        }
    }

    private record ConnectionKey(String outNodeId, String outPortId,
                                 String inNodeId, String inPortId) {
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

    private record ConnectionCandidate(Viewport.PortInfo output, Viewport.PortInfo input) {
    }
}
