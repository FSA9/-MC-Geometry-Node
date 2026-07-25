package com.mine.geometry_node.client.ui.viewport.node;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.view.View;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.graphics.RectF;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.Viewport;

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

    public List<NodeVisualAdapter> getNodeVisuals(List<String> nodeIds) {
        List<NodeVisualAdapter> nodes = new ArrayList<>();
        if (nodeIds == null) return nodes;
        for (String nodeId : nodeIds) {
            NodeVisualAdapter node = mNodeVisuals.get(nodeId);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    public void clearNodeVisuals() {
        for (NodeVisualAdapter node : mNodeOrder) {
            node.releaseOverlayViews();
        }
        removeAllViews();
        mNodeVisuals.clear();
        mNodeOrder.clear();
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
            node.releaseOverlayViews();
            invalidate();
        }
    }

    public NodeVisualAdapter getNodeVisual(String nodeId) { return mNodeVisuals.get(nodeId); }

    public void applySelection(List<String> selectedNodeIds) {
        Set<String> selectedNodeIdSet = selectedNodeIds != null ? new HashSet<>(selectedNodeIds) : new HashSet<>();
        boolean canCull = prepareVisibleBounds(CULL_PADDING_DP);
        for (NodeVisualAdapter node : mNodeVisuals.values()) {
            node.setSelected(selectedNodeIdSet.contains(node.getNodeId()));
            syncOverlayHost(node, canCull);
        }
        bringSelectedNodesToFront(getNodeVisuals(selectedNodeIds));
        invalidate();
    }

    private void bringSelectedNodesToFront(List<NodeVisualAdapter> selectedNodes) {
        if (selectedNodes == null || selectedNodes.isEmpty()) return;
        mNodeOrder.removeAll(selectedNodes);
        mNodeOrder.addAll(selectedNodes);
        for (NodeVisualAdapter node : selectedNodes) {
            bringOverlayHostToFront(node);
        }
    }

    private void bringOverlayHostToFront(NodeVisualAdapter node) {
        View overlayHost = node.getOverlayHostView();
        if (overlayHost == null || overlayHost.getParent() != this) return;

        LayoutParams lp = (LayoutParams) overlayHost.getLayoutParams();
        removeView(overlayHost);
        if (lp != null) {
            addView(overlayHost, lp);
        } else {
            addView(overlayHost);
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
        boolean canCull = prepareVisibleBounds(CULL_PADDING_DP);
        for (NodeVisualAdapter node : mNodeOrder) {
            syncOverlayHost(node, canCull);
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
        syncOverlayHost(node, prepareVisibleBounds(CULL_PADDING_DP));
    }

    private void syncOverlayHost(NodeVisualAdapter node, boolean canCull) {
        if (node == null) return;

        View overlayHost = node.getOverlayHostView();
        boolean visible = canCull && isNodeVisibleUi(node, mTmpVisibleBounds);
        boolean keepMounted = visible || node.isOverlayActive();
        if (overlayHost == null || !keepMounted) {
            if (overlayHost != null && overlayHost.getParent() == this) {
                removeView(overlayHost);
            }
            if (!keepMounted) {
                node.releaseOverlayViews();
            }
            return;
        }

        if (node.hasOverlayViews()) {
            node.ensureOverlayViews();
        }

        float scale = mViewport.getCamera().getScale();
        UINode uiNode = node instanceof UINode casted ? casted : null;
        int overlayWidthDp = uiNode != null ? uiNode.getOverlayWidthDp() : Math.round(node.getVisualWidthDp());
        int overlayHeightDp = uiNode != null ? uiNode.getOverlayHeightDp() : node.getTotalHeightDp();
        int widthPx = UIUtils.dp2pxInt(overlayWidthDp);
        int heightPx = UIUtils.dp2pxInt(overlayHeightDp);

        LayoutParams lp = (LayoutParams) overlayHost.getLayoutParams();
        if (lp == null) {
            lp = new LayoutParams(widthPx, heightPx);
        } else {
            lp.width = widthPx;
            lp.height = heightPx;
        }

        lp.leftMargin = Math.round(mViewport.getCamera().uiToScreenX(node.getUiX()));
        lp.topMargin = Math.round(mViewport.getCamera().uiToScreenY(node.getUiY()));
        if (overlayHost.getParent() != this) {
            addView(overlayHost, getOverlayHostInsertIndex(node), lp);
        } else {
            overlayHost.setLayoutParams(lp);
        }
        overlayHost.setPivotX(0);
        overlayHost.setPivotY(0);
        overlayHost.setScaleX(scale);
        overlayHost.setScaleY(scale);
        node.onOverlayScaleChanged(scale);
    }

    private int getOverlayHostInsertIndex(NodeVisualAdapter node) {
        int nodeIndex = mNodeOrder.indexOf(node);
        if (nodeIndex <= 0) return 0;

        int insertIndex = 0;
        for (int i = 0; i < nodeIndex; i++) {
            View host = mNodeOrder.get(i).getOverlayHostView();
            if (host != null && host.getParent() == this) {
                insertIndex++;
            }
        }
        return Math.min(insertIndex, getChildCount());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean canCull = prepareVisibleBounds(CULL_PADDING_DP);
        for (NodeVisualAdapter node : mNodeOrder) {
            if (!canCull || isNodeVisibleUi(node, mTmpVisibleBounds)) {
                View overlayHost = node.getOverlayHostView();
                if (overlayHost == null || overlayHost.getParent() != this) {
                    node.drawNode(canvas, mViewport.getCamera());
                }
            }
        }
    }

    private boolean prepareVisibleBounds(float paddingDp) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        mViewport.getCamera().getVisibleUiRect(mTmpVisibleBounds, getWidth(), getHeight(), paddingDp);
        return true;
    }

    private boolean isNodeVisibleUi(NodeVisualAdapter node, float paddingDp) {
        if (!prepareVisibleBounds(paddingDp)) {
            return true;
        }
        return isNodeVisibleUi(node, mTmpVisibleBounds);
    }

    private boolean isNodeVisibleUi(NodeVisualAdapter node, RectF visibleBounds) {
        node.getLogicalBounds(mTmpNodeBounds);
        return mTmpNodeBounds.intersects(visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom);
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

    public NodeVisualAdapter findInteractiveNodeAt(float uiX, float uiY) {
        for (int i = mNodeOrder.size() - 1; i >= 0; i--) {
            NodeVisualAdapter node = mNodeOrder.get(i);
            float localXpx = UIUtils.dp2px(uiX - node.getUiX());
            float localYpx = UIUtils.dp2px(uiY - node.getUiY());
            if (node.findInteractiveViewAt(localXpx, localYpx) != null) {
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

    public List<String> findNodeIdsInRect(float uiX, float uiY, float uiW, float uiH) {
        List<String> selectedNodeIds = new ArrayList<>();
        float selRight = uiX + uiW;
        float selBottom = uiY + uiH;

        List<NodeVisualAdapter> orderedNodes = new ArrayList<>(mNodeOrder);
        for (NodeVisualAdapter n : orderedNodes) {
            n.getLogicalBounds(mTmpNodeBounds);
            if (mTmpNodeBounds.intersects(uiX, uiY, selRight, selBottom)) {
                selectedNodeIds.add(n.getNodeId());
            }
        }
        return selectedNodeIds;
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
