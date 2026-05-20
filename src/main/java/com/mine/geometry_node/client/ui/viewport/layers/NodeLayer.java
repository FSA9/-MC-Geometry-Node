package com.mine.geometry_node.client.ui.viewport.layers;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.graphics.RectF;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.UINode;
import com.mine.geometry_node.client.ui.viewport.Viewport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class NodeLayer extends FrameLayout {
    private final Viewport mViewport;
    private final Map<String, UINode> mNodeViews = new HashMap<>();
    private final List<UINode> mSelectedNodes = new ArrayList<>();

    // 复用的临时对象，避免在遍历和碰撞检测时频繁创建
    private final RectF mTmpNodeBounds = new RectF();

    public NodeLayer(Context context, Viewport viewport) {
        super(context);
        this.mViewport = viewport;
        setClipChildren(false);
        setPivotX(0);
        setPivotY(0);
    }

    public Map<String, UINode> getNodeViews() { return mNodeViews; }
    public List<UINode> getSelectedNodes() { return mSelectedNodes; }

    public void clearNodeViews() {
        removeAllViews();
        mNodeViews.clear();
        mSelectedNodes.clear();
    }

    public void addNodeView(String nodeId, UINode uiNode) {
        addView(uiNode);
        mNodeViews.put(nodeId, uiNode);
    }

    public void removeNodeView(String nodeId) {
        UINode uiNode = mNodeViews.remove(nodeId);
        if (uiNode != null) {
            removeView(uiNode);
            mSelectedNodes.remove(uiNode);
        }
    }

    public UINode getNodeView(String nodeId) { return mNodeViews.get(nodeId); }

    public boolean isNodeSelected(String nodeId) {
        UINode uiNode = mNodeViews.get(nodeId);
        return uiNode != null && mSelectedNodes.contains(uiNode);
    }

    public void updateSelectionState(List<String> selectedNodeIds) {
        for (UINode node : mNodeViews.values()) { node.setSelected(false); }
        mSelectedNodes.clear();

        for (String id : selectedNodeIds) {
            UINode uiNode = mNodeViews.get(id);
            if (uiNode != null) {
                uiNode.setSelected(true);
                mSelectedNodes.add(uiNode);
            }
        }
    }

    public void clearSelection() {
        for (UINode node : mSelectedNodes) { node.setSelected(false); }
        mSelectedNodes.clear();
    }

    public void addToSelection(UINode node) {
        if (!mSelectedNodes.contains(node)) {
            mSelectedNodes.add(node);
            node.setSelected(true);
        }
    }

    public void updateNodePosition(String nodeId, float x, float y) {
        UINode uiNode = mNodeViews.get(nodeId);
        if (uiNode != null) {
            uiNode.setTranslationX(x);
            uiNode.setTranslationY(y);
        }
    }

    public void notifyNodeLayoutUpdate(String nodeId) {
        UINode uiNode = mNodeViews.get(nodeId);
        if (uiNode != null) uiNode.updateNodeLayout();
    }

    // --- 碰撞检测逻辑 ---

    public UINode findNodeAt(float uiX, float uiY) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof UINode node) {
                node.getLogicalBounds(mTmpNodeBounds);
                mTmpNodeBounds.inset(-UIConstants.Node.PORT_VISUAL_RADIUS, -UIConstants.Node.PORT_VISUAL_RADIUS);
                if (mTmpNodeBounds.contains(uiX, uiY)) {
                    return node;
                }
            }
        }
        return null;
    }

    public Viewport.PortInfo findPortAt(float uiX, float uiY) {
        float dynamicMargin = UIConstants.Node.PORT_HITBOX_RADIUS;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof UINode node) {
                node.getLogicalBounds(mTmpNodeBounds);
                mTmpNodeBounds.inset(-dynamicMargin, -dynamicMargin);
                if (mTmpNodeBounds.contains(uiX, uiY)) {
                    float localX = uiX - node.getTranslationX();
                    float localY = uiY - node.getTranslationY();

                    String inPortId = node.hitTestPort(localX, localY, true, dynamicMargin);
                    if (inPortId != null) return new Viewport.PortInfo(node, inPortId, true);

                    String outPortId = node.hitTestPort(localX, localY, false, dynamicMargin);
                    if (outPortId != null) return new Viewport.PortInfo(node, outPortId, false);
                }
                // 阻断穿透
                node.getLogicalBounds(mTmpNodeBounds);
                if (mTmpNodeBounds.contains(uiX, uiY)) return null;
            }
        }
        return null;
    }

    public void updateBoxSelection(float uiX, float uiY, float uiW, float uiH) {
        clearSelection();
        float selRight = uiX + uiW;
        float selBottom = uiY + uiH;

        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof UINode n) {
                n.getLogicalBounds(mTmpNodeBounds);
                if (mTmpNodeBounds.intersects(uiX, uiY, selRight, selBottom)) addToSelection(n);
            }
        }
    }

    public void moveSelectedNodes(float uiDx, float uiDy) {
        Set<String> affectedFrames = new HashSet<>();

        for (UINode node : mSelectedNodes) {
            node.setTranslationX(node.getTranslationX() + uiDx);
            node.setTranslationY(node.getTranslationY() + uiDy);
            mViewport.updateConnectionsForNode(node.getNodeData().id);

            if (node.getNodeData().parentFrame != null) {
                affectedFrames.add(node.getNodeData().parentFrame);
            }
        }

        for (String frameId : affectedFrames) {
            mViewport.previewFrameBounds(frameId);
        }
    }
}