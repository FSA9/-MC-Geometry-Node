package com.mine.geometry_node.client.ui.viewport.layers;

import com.mine.geometry_node.client.ui.viewport.interaction.InteractionManager;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.viewport.UINode;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.core.node.NodeData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConnectionLayer {
    private final Viewport mViewport;
    private final Paint mConnectionPaint = new Paint();
    private final List<VisualConnection> mVisualConnections = new ArrayList<>();

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
        // 架构更新：利用 Viewport 的 isReady 替代深层判空
        if (!mViewport.isReady()) return;

        float scaledLineWidth = UIConstants.ViewPort.LINE_WIDTH_CONNECTION * camera.getScale();
        mConnectionPaint.setStrokeWidth(scaledLineWidth);

        for (int i = 0; i < mVisualConnections.size(); i++) {
            VisualConnection vc = mVisualConnections.get(i);
            canvas.drawLine(
                    camera.uiToScreenX(vc.startUiX), camera.uiToScreenY(vc.startUiY),
                    camera.uiToScreenX(vc.endUiX), camera.uiToScreenY(vc.endUiY),
                    mConnectionPaint
            );
        }
    }

    public void rebuildVisualConnections() {
        mVisualConnections.clear();

        // 架构更新：利用 Viewport 的 isReady 替代深层判空
        if (!mViewport.isReady()) return;

        // 架构更新：从 Controller 中获取由其管理的 Session 和 Graph 数据
        com.mine.geometry_node.core.node.NodeGraph graph = mViewport.getController().getCurrentSession().editorContext.getGraph();
        if (graph == null) return;

        Map<String, UINode> nodeViews = mViewport.getNodeViews();

        for (NodeData outData : graph.nodes.values()) {
            UINode outUi = nodeViews.get(outData.id);
            if (outUi == null) continue;

            // 处理数据连接
            if (outData.outputs != null) {
                for (Map.Entry<String, List<com.mine.geometry_node.core.node.Connection>> entry : outData.outputs.entrySet()) {
                    String outPortId = entry.getKey();
                    if (entry.getValue() == null) continue;
                    for (com.mine.geometry_node.core.node.Connection link : entry.getValue()) {
                        UINode inUi = nodeViews.get(link.targetNodeId());
                        if (inUi != null) {
                            VisualConnection vc = new VisualConnection(outUi, outPortId, inUi, link.targetPortName(), false);
                            vc.updateUiCoordinates(mTempOutPos, mTempInPos);
                            mVisualConnections.add(vc);
                        }
                    }
                }
            }

            // 处理执行连接
            if (outData.execOutputs != null) {
                for (Map.Entry<String, com.mine.geometry_node.core.node.Connection> entry : outData.execOutputs.entrySet()) {
                    String execOutPortId = entry.getKey();
                    com.mine.geometry_node.core.node.Connection link = entry.getValue();

                    UINode inUi = nodeViews.get(link.targetNodeId());
                    if (inUi != null) {
                        VisualConnection vc = new VisualConnection(outUi, execOutPortId, inUi, link.targetPortName(), true);
                        vc.updateUiCoordinates(mTempOutPos, mTempInPos);
                        mVisualConnections.add(vc);
                    }
                }
            }
        }
        mViewport.invalidate();
    }

    public void updateConnectionsForNode(String nodeId) {
        boolean needsInvalidate = false;
        for (int i = 0; i < mVisualConnections.size(); i++) {
            VisualConnection vc = mVisualConnections.get(i);
            if (vc.outNode.getNodeData().id.equals(nodeId) || vc.inNode.getNodeData().id.equals(nodeId)) {
                vc.updateUiCoordinates(mTempOutPos, mTempInPos);
                needsInvalidate = true;
            }
        }
        if (needsInvalidate) {
            mViewport.invalidate();
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
                    vc.outNode.getNodeData().id, vc.outPortId,
                    vc.inNode.getNodeData().id, vc.inPortId
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
        final UINode outNode;
        final String outPortId;
        final UINode inNode;
        final String inPortId;
        final boolean isExecution;

        float startUiX, startUiY;
        float endUiX, endUiY;

        VisualConnection(UINode outNode, String outPortId, UINode inNode, String inPortId, boolean isExecution) {
            this.outNode = outNode;
            this.outPortId = outPortId;
            this.inNode = inNode;
            this.inPortId = inPortId;
            this.isExecution = isExecution;
        }

        void updateUiCoordinates(float[] tempOutPos, float[] tempInPos) {
            outNode.getPortPosition(outPortId, false, tempOutPos);
            startUiX = outNode.getTranslationX() + tempOutPos[0];
            startUiY = outNode.getTranslationY() + tempOutPos[1];

            inNode.getPortPosition(inPortId, true, tempInPos);
            endUiX = inNode.getTranslationX() + tempInPos[0];
            endUiY = inNode.getTranslationY() + tempInPos[1];
        }
    }
}