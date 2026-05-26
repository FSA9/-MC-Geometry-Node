package com.mine.geometry_node.client.ui.viewport.layers;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.view.View;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.graphics.RectF;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.visual.NodeVisualAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class NodeLayer extends FrameLayout {
    private final Viewport mViewport;
    private final Map<String, NodeVisualAdapter> mNodeVisuals = new HashMap<>();
    private final List<NodeVisualAdapter> mNodeOrder = new ArrayList<>();
    private final List<NodeVisualAdapter> mSelectedNodes = new ArrayList<>();

    // 复用的临时对象，避免在遍历和碰撞检测时频繁创建
    private final RectF mTmpNodeBounds = new RectF();
    private final RectF mTmpVisibleBounds = new RectF();
    private static final float CULL_PADDING_DP = 64f;

    public NodeLayer(Context context, Viewport viewport) {
        super(context);
        this.mViewport = viewport;
        setClipChildren(false);
        setWillNotDraw(false);
        setPivotX(0);
        setPivotY(0);
    }

    public Map<String, NodeVisualAdapter> getNodeVisuals() { return mNodeVisuals; }
    public List<NodeVisualAdapter> getSelectedNodeVisuals() { return mSelectedNodes; }

    public void clearNodeVisuals() {
        removeAllViews();
        mNodeVisuals.clear();
        mNodeOrder.clear();
        mSelectedNodes.clear();
    }

    public void addNodeVisual(String nodeId, NodeVisualAdapter node) {
        mNodeVisuals.put(nodeId, node);
        mNodeOrder.add(node);
        syncOverlayHost(node);
        invalidate();
    }

    public void removeNodeVisual(String nodeId) {
        NodeVisualAdapter node = mNodeVisuals.remove(nodeId);
        if (node != null) {
            mNodeOrder.remove(node);
            View overlayHost = node.getOverlayHostView();
            if (overlayHost != null && overlayHost.getParent() == this) removeView(overlayHost);
            mSelectedNodes.remove(node);
            invalidate();
        }
    }

    public NodeVisualAdapter getNodeVisual(String nodeId) { return mNodeVisuals.get(nodeId); }

    public boolean isNodeSelected(String nodeId) {
        NodeVisualAdapter node = mNodeVisuals.get(nodeId);
        return node != null && mSelectedNodes.contains(node);
    }

    public void updateSelectionState(List<String> selectedNodeIds) {
        for (NodeVisualAdapter node : mNodeVisuals.values()) { node.setSelected(false); }
        mSelectedNodes.clear();

        for (String id : selectedNodeIds) {
            NodeVisualAdapter node = mNodeVisuals.get(id);
            if (node != null) {
                node.setSelected(true);
                mSelectedNodes.add(node);
            }
        }
        invalidate();
    }

    public void clearSelection() {
        for (NodeVisualAdapter node : mSelectedNodes) { node.setSelected(false); }
        mSelectedNodes.clear();
        invalidate();
    }

    public void addToSelection(NodeVisualAdapter node) {
        if (!mSelectedNodes.contains(node)) {
            mSelectedNodes.add(node);
            node.setSelected(true);
            invalidate();
        }
    }

    public void updateNodePosition(String nodeId, float x, float y) {
        NodeVisualAdapter node = mNodeVisuals.get(nodeId);
        if (node != null) {
            node.setPreviewPosition(x, y);
            syncOverlayHost(node);
            invalidate();
        }
    }

    public void notifyNodeLayoutUpdate(String nodeId) {
        NodeVisualAdapter node = mNodeVisuals.get(nodeId);
        if (node != null) {
            node.updateNodeLayout();
            syncOverlayHost(node);
            invalidate();
        }
    }

    public void updateOverlayTransforms() {
        for (NodeVisualAdapter node : mNodeOrder) {
            syncOverlayHost(node);
        }
        invalidate();
    }

    public void updateOverlayForNode(NodeVisualAdapter node) {
        if (node != null && mNodeVisuals.containsValue(node)) {
            syncOverlayHost(node);
            invalidate();
        }
    }

    private void syncOverlayHost(NodeVisualAdapter node) {
        if (node == null) return;

        View overlayHost = node.getOverlayHostView();
        boolean hasFocus = overlayHost != null && overlayHost.hasFocus();
        boolean hasOverlay = overlayHost != null && node.hasOverlayViews() && (isNodeVisibleUi(node, CULL_PADDING_DP) || hasFocus);
        if (!hasOverlay) {
            if (overlayHost != null && overlayHost.getParent() == this) removeView(overlayHost);
            return;
        }

        if (overlayHost.getParent() != this) {
            addView(overlayHost);
        }

        float scale = mViewport.getCamera().getScale();
        int widthPx = UIUtils.dp2pxInt(UIConstants.Node.NODE_WIDTH);
        int heightPx = UIUtils.dp2pxInt(node.getTotalHeightDp());

        LayoutParams lp = (LayoutParams) overlayHost.getLayoutParams();
        if (lp == null) {
            lp = new LayoutParams(widthPx, heightPx);
        } else {
            lp.width = widthPx;
            lp.height = heightPx;
        }

        lp.leftMargin = Math.round(mViewport.getCamera().uiToScreenX(node.getUiX()));
        lp.topMargin = Math.round(mViewport.getCamera().uiToScreenY(node.getUiY()));
        overlayHost.setLayoutParams(lp);
        overlayHost.setPivotX(0);
        overlayHost.setPivotY(0);
        overlayHost.setScaleX(scale);
        overlayHost.setScaleY(scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (NodeVisualAdapter node : mNodeOrder) {
            if (isNodeVisibleUi(node, CULL_PADDING_DP)) {
                node.drawNode(canvas, mViewport.getCamera());
            }
        }
    }

    private boolean isNodeVisibleUi(NodeVisualAdapter node, float paddingDp) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return true;
        }
        mViewport.getCamera().getVisibleUiRect(mTmpVisibleBounds, getWidth(), getHeight(), paddingDp);
        node.getLogicalBounds(mTmpNodeBounds);
        return mTmpNodeBounds.intersects(mTmpVisibleBounds.left, mTmpVisibleBounds.top, mTmpVisibleBounds.right, mTmpVisibleBounds.bottom);
    }

    // --- 碰撞检测逻辑 ---

    public NodeVisualAdapter findNodeAt(float uiX, float uiY) {
        for (int i = mNodeOrder.size() - 1; i >= 0; i--) {
            NodeVisualAdapter node = mNodeOrder.get(i);
            node.getLogicalBounds(mTmpNodeBounds);
            mTmpNodeBounds.inset(-UIConstants.Node.PORT_VISUAL_RADIUS, -UIConstants.Node.PORT_VISUAL_RADIUS);
            if (mTmpNodeBounds.contains(uiX, uiY)) {
                return node;
            }
        }
        return null;
    }

    public Viewport.PortInfo findPortAt(float uiX, float uiY) {
        float dynamicMargin = UIConstants.Node.PORT_HITBOX_RADIUS;
        for (int i = mNodeOrder.size() - 1; i >= 0; i--) {
            NodeVisualAdapter node = mNodeOrder.get(i);
            node.getLogicalBounds(mTmpNodeBounds);
            mTmpNodeBounds.inset(-dynamicMargin, -dynamicMargin);
            if (mTmpNodeBounds.contains(uiX, uiY)) {
                float localX = uiX - node.getUiX();
                float localY = uiY - node.getUiY();

                String inPortId = node.hitTestPort(localX, localY, true, dynamicMargin);
                if (inPortId != null) return new Viewport.PortInfo(node, inPortId, true);

                String outPortId = node.hitTestPort(localX, localY, false, dynamicMargin);
                if (outPortId != null) return new Viewport.PortInfo(node, outPortId, false);
            }
            // 阻断穿透
            node.getLogicalBounds(mTmpNodeBounds);
            if (mTmpNodeBounds.contains(uiX, uiY)) return null;
        }
        return null;
    }

    public void updateBoxSelection(float uiX, float uiY, float uiW, float uiH) {
        clearSelection();
        float selRight = uiX + uiW;
        float selBottom = uiY + uiH;

        for (NodeVisualAdapter n : mNodeOrder) {
            n.getLogicalBounds(mTmpNodeBounds);
            if (mTmpNodeBounds.intersects(uiX, uiY, selRight, selBottom)) addToSelection(n);
        }
        invalidate();
    }

    public void moveSelectedNodes(float uiDx, float uiDy) {
        Set<String> affectedFrames = new HashSet<>();

        for (NodeVisualAdapter node : mSelectedNodes) {
            node.offsetPreviewPosition(uiDx, uiDy);
            syncOverlayHost(node);
            mViewport.updateConnectionsForNode(node.getNodeId());

            if (node.getNodeData().parentFrame != null) {
                affectedFrames.add(node.getNodeData().parentFrame);
            }
        }

        for (String frameId : affectedFrames) {
            mViewport.previewFrameBounds(frameId);
        }
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        return false;
    }
}
