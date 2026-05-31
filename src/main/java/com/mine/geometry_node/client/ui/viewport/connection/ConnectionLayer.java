package com.mine.geometry_node.client.ui.viewport.connection;

import com.mine.geometry_node.client.ui.viewport.interaction.InteractionManager;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConnectionLayer {
    private final Viewport mViewport;
    private final Paint mConnectionPaint = new Paint();
    private final List<VisualConnection> mVisualConnections = new ArrayList<>();
    private final Map<String, List<VisualConnection>> mConnectionsByNodeId = new HashMap<>();
    private final RectF mTmpVisibleBounds = new RectF();
    private static final float CULL_PADDING_DP = 96f;

    private final float[] mTempOutPos = new float[2];
    private final float[] mTempInPos  = new float[2];

    public ConnectionLayer(Viewport viewport) {
        this.mViewport = viewport;
        mConnectionPaint.setAntiAlias(true);
        mConnectionPaint.setStyle(Paint.Style.STROKE);
        mConnectionPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_CONNECTION);
        mConnectionPaint.setColor(0xFFE0E0E0);
    }

    public void draw(Canvas canvas, ViewportCamera camera) {
        if (!mViewport.isReady()) return;

        float scaledLineWidth = UIConstants.ViewPort.LINE_WIDTH_CONNECTION * camera.getScale();
        mConnectionPaint.setStrokeWidth(scaledLineWidth);

        boolean canCull = mViewport.getWidth() > 0 && mViewport.getHeight() > 0;
        if (canCull) {
            camera.getVisibleUiRect(mTmpVisibleBounds, mViewport.getWidth(), mViewport.getHeight(), CULL_PADDING_DP);
        }

        for (int i = 0; i < mVisualConnections.size(); i++) {
            VisualConnection vc = mVisualConnections.get(i);
            if (canCull && !vc.intersects(mTmpVisibleBounds.left, mTmpVisibleBounds.top, mTmpVisibleBounds.right, mTmpVisibleBounds.bottom)) continue;

            canvas.drawLine(
                    camera.uiToScreenX(vc.startUiX), camera.uiToScreenY(vc.startUiY),
                    camera.uiToScreenX(vc.endUiX), camera.uiToScreenY(vc.endUiY),
                    mConnectionPaint
            );
        }
    }

    public void rebuildVisualConnections(NodeGraph graph, Map<String, ? extends ConnectionNodeVisual> nodeVisuals) {
        mVisualConnections.clear();
        mConnectionsByNodeId.clear();

        if (graph == null) return;

        for (NodeData outData : graph.nodes.values()) {
            ConnectionNodeVisual outUi = nodeVisuals.get(outData.id);
            if (outUi == null) continue;

            // 处理数据连接
            if (outData.outputs != null) {
                for (Map.Entry<String, List<com.mine.geometry_node.core.node.Connection>> entry : outData.outputs.entrySet()) {
                    String outPortId = entry.getKey();
                    if (entry.getValue() == null) continue;
                    for (com.mine.geometry_node.core.node.Connection link : entry.getValue()) {
                        ConnectionNodeVisual inUi = nodeVisuals.get(link.targetNodeId());
                        if (inUi != null) {
                            VisualConnection vc = new VisualConnection(outUi, outPortId, inUi, link.targetPortName(), false);
                            vc.updateUiCoordinates(mTempOutPos, mTempInPos);
                            mVisualConnections.add(vc);
                            indexVisualConnection(vc);
                        }
                    }
                }
            }

            // 处理执行连接
            if (outData.execOutputs != null) {
                for (Map.Entry<String, com.mine.geometry_node.core.node.Connection> entry : outData.execOutputs.entrySet()) {
                    String execOutPortId = entry.getKey();
                    com.mine.geometry_node.core.node.Connection link = entry.getValue();

                    ConnectionNodeVisual inUi = nodeVisuals.get(link.targetNodeId());
                    if (inUi != null) {
                        VisualConnection vc = new VisualConnection(outUi, execOutPortId, inUi, link.targetPortName(), true);
                        vc.updateUiCoordinates(mTempOutPos, mTempInPos);
                        mVisualConnections.add(vc);
                        indexVisualConnection(vc);
                    }
                }
            }
        }
        mViewport.invalidate();
    }

    public void updateConnectionsForNode(String nodeId) {
        List<VisualConnection> nodeConnections = mConnectionsByNodeId.get(nodeId);
        if (nodeConnections == null || nodeConnections.isEmpty()) {
            return;
        }

        for (int i = 0; i < nodeConnections.size(); i++) {
            nodeConnections.get(i).updateUiCoordinates(mTempOutPos, mTempInPos);
        }
        mViewport.invalidate();
    }

    private void indexVisualConnection(VisualConnection vc) {
        String outNodeId = vc.outNode.getNodeId();
        String inNodeId = vc.inNode.getNodeId();

        mConnectionsByNodeId.computeIfAbsent(outNodeId, ignored -> new ArrayList<>()).add(vc);
        if (!outNodeId.equals(inNodeId)) {
            mConnectionsByNodeId.computeIfAbsent(inNodeId, ignored -> new ArrayList<>()).add(vc);
        }
    }

    public void intersectAndCut(float lastUiX, float lastUiY, float currentUiX, float currentUiY, InteractionManager.InteractionListener listener) {
        if (listener == null || mVisualConnections.isEmpty()) return;

        List<VisualConnection> cutConnections = new ArrayList<>();

        for (int i = 0; i < mVisualConnections.size(); i++) {
            VisualConnection vc = mVisualConnections.get(i);

            if (linesIntersect(lastUiX, lastUiY, currentUiX, currentUiY,
                    vc.startUiX, vc.startUiY, vc.endUiX, vc.endUiY)) {
                cutConnections.add(vc);
            }
        }

        for (VisualConnection vc : cutConnections) {
            listener.onDisconnectPorts(
                    vc.outNode.getNodeId(), vc.outPortId,
                    vc.inNode.getNodeId(), vc.inPortId
            );
        }
    }

    private boolean linesIntersect(float x1, float y1, float x2, float y2,
                                   float x3, float y3, float x4, float y4) {
        float denominator = ((x2 - x1) * (y4 - y3)) - ((y2 - y1) * (x4 - x3));
        if (denominator == 0) return false;

        float num1 = ((y1 - y3) * (x4 - x3)) - ((x1 - x3) * (y4 - y3));
        float num2 = ((y1 - y3) * (x2 - x1)) - ((x1 - x3) * (y2 - y1));

        float r = num1 / denominator;
        float s = num2 / denominator;

        return (r >= 0 && r <= 1) && (s >= 0 && s <= 1);
    }

    private static class VisualConnection {
        final ConnectionNodeVisual outNode;
        final String outPortId;
        final ConnectionNodeVisual inNode;
        final String inPortId;
        final boolean isExecution;

        float startUiX, startUiY;
        float endUiX, endUiY;

        VisualConnection(ConnectionNodeVisual outNode, String outPortId, ConnectionNodeVisual inNode, String inPortId, boolean isExecution) {
            this.outNode = outNode;
            this.outPortId = outPortId;
            this.inNode = inNode;
            this.inPortId = inPortId;
            this.isExecution = isExecution;
        }

        void updateUiCoordinates(float[] tempOutPos, float[] tempInPos) {
            outNode.getPortPosition(outPortId, false, tempOutPos);
            startUiX = outNode.getUiX() + tempOutPos[0];
            startUiY = outNode.getUiY() + tempOutPos[1];

            inNode.getPortPosition(inPortId, true, tempInPos);
            endUiX = inNode.getUiX() + tempInPos[0];
            endUiY = inNode.getUiY() + tempInPos[1];
        }

        boolean intersects(float l, float t, float r, float b) {
            float minX = Math.min(startUiX, endUiX);
            float maxX = Math.max(startUiX, endUiX);
            float minY = Math.min(startUiY, endUiY);
            float maxY = Math.max(startUiY, endUiY);
            return maxX >= l && minX <= r && maxY >= t && minY <= b;
        }
    }
}
