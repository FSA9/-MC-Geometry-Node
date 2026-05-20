package com.mine.geometry_node.client.ui.viewport.layers;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.UINode;
import com.mine.geometry_node.client.ui.viewport.UIFrame; // 假设你的 UIFrame 在这个包下
import com.mine.geometry_node.client.ui.viewport.Viewport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrameLayer extends FrameLayout {
    private final Viewport mViewport;
    private final Map<String, UIFrame> mFrameViews = new HashMap<>();

    private final List<UIFrame> mSelectedFrames = new ArrayList<>();

    private static final float CONTENT_MARGIN = 20f;

    public FrameLayer(Context context, Viewport viewport) {
        super(context);
        this.mViewport = viewport;
        setClipChildren(false);
        setPivotX(0);
        setPivotY(0);
    }

    public Map<String, UIFrame> getFrameViews() {
        return mFrameViews;
    }

    public void clearFrameViews() {
        removeAllViews();
        mFrameViews.clear();
    }

    public void addFrameView(String frameId, UIFrame uiFrame) {
        addView(uiFrame);
        mFrameViews.put(frameId, uiFrame);
        requestLayout();
    }

    public void removeFrameView(String frameId) {
        UIFrame uiFrame = mFrameViews.remove(frameId);
        if (uiFrame != null) {
            removeView(uiFrame);
        }
    }

    public void updateFrameBounds(String frameId) {
        UIFrame uiFrame = mFrameViews.get(frameId);
        if (uiFrame != null) {
            uiFrame.updateBounds();
        }
    }

    public UIFrame findFrameAt(float uiX, float uiY) {
        // 倒序遍历（优先命中显示在最上层的子图框）
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof UIFrame frame) {
                if (frame.hitTest(uiX, uiY)) {
                    return frame;
                }
            }
        }
        return null;
    }

    public void clearSelection() {
        for (UIFrame frame : mSelectedFrames) {
            frame.setSelected(false);
        }
        mSelectedFrames.clear();
    }

    public void addToSelection(UIFrame frame) {
        if (frame != null && !mSelectedFrames.contains(frame)) {
            frame.setSelected(true);
            mSelectedFrames.add(frame);
        }
    }

    public List<UIFrame> getSelectedFrames() {
        return mSelectedFrames;
    }

    public UIFrame getSmallestContainingFrame(float uiX, float uiY) {
        UIFrame target = null;
        float minArea = Float.MAX_VALUE;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof UIFrame frame) {
                float x = frame.getFrameData().uiPos[0];
                float y = frame.getFrameData().uiPos[1];
                float w = frame.getFrameData().uiSize[0];
                float h = frame.getFrameData().uiSize[1];

                if (uiX >= x && uiX <= x + w && uiY >= y && uiY <= y + h) {
                    float area = w * h;
                    if (area < minArea) {
                        minArea = area;
                        target = frame;
                    }
                }
            }
        }
        return target;
    }

    public void moveFrameAndChildren(String frameId, float dx, float dy) {
        UIFrame frameView = mFrameViews.get(frameId);
        if (frameView == null) return;

        // 1. 物理平移图框自身
        frameView.offsetPosition(dx, dy);

        // 2. 联动平移属于该图框的所有直接子节点
        for (UINode node : mViewport.getNodeViews().values()) {
            if (frameId.equals(node.getNodeData().parentFrame)) {
                node.setTranslationX(node.getTranslationX() + dx);
                node.setTranslationY(node.getTranslationY() + dy);
                mViewport.updateConnectionsForNode(node.getNodeData().id); // 实时重绘导线
            }
        }

        // 3. 递归联动所有嵌套的子图框
        for (UIFrame childFrame : mFrameViews.values()) {
            if (frameId.equals(childFrame.getFrameData().parentFrame)) {
                moveFrameAndChildren(childFrame.getFrameData().id, dx, dy);
            }
        }
    }

    public void previewFrameBounds(String frameId) {
        UIFrame uiFrame = mFrameViews.get(frameId);
        if (uiFrame == null) return;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean hasChildren = false;

        // 1. 计算子节点边界
        for (UINode node : mViewport.getNodeViews().values()) {
            if (frameId.equals(node.getNodeData().parentFrame)) {
                hasChildren = true;
                float nx = node.getTranslationX();
                float ny = node.getTranslationY();
                float nw = node.getWidth() > 0 ? UIUtils.px2dp(node.getWidth()) : (node.getNodeData().uiSize != null ? node.getNodeData().uiSize[0] : 150f);
                float nh = node.getHeight() > 0 ? UIUtils.px2dp(node.getHeight()) : (node.getNodeData().uiSize != null ? node.getNodeData().uiSize[1] : 100f);

                minX = Math.min(minX, nx);
                minY = Math.min(minY, ny);
                maxX = Math.max(maxX, nx + nw);
                maxY = Math.max(maxY, ny + nh);
            }
        }

        // 2. 计算子图框边界
        for (UIFrame childFrame : mFrameViews.values()) {
            if (!frameId.equals(childFrame.getFrameData().id) &&
                    frameId.equals(childFrame.getFrameData().parentFrame)) {

                hasChildren = true;
                float fx = childFrame.getTranslationX();
                float fy = childFrame.getTranslationY();
                float fw = childFrame.getWidth() > 0 ? UIUtils.px2dp(childFrame.getWidth()) : childFrame.getFrameData().uiSize[0];
                float fh = childFrame.getHeight() > 0 ? UIUtils.px2dp(childFrame.getHeight()) : childFrame.getFrameData().uiSize[1];

                minX = Math.min(minX, fx);
                minY = Math.min(minY, fy);
                maxX = Math.max(maxX, fx + fw);
                maxY = Math.max(maxY, fy + fh);
            }
        }

        if (hasChildren) {
            float newX = minX - CONTENT_MARGIN;
            float newY = minY - CONTENT_MARGIN - UIFrame.FRAME_HEADER_H;

            float newW = (maxX - minX) + 2 * CONTENT_MARGIN;
            float newH = (maxY - minY) + 2 * CONTENT_MARGIN + UIFrame.FRAME_HEADER_H;

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(newW), UIUtils.dp2pxInt(newH));
            uiFrame.setLayoutParams(lp);

            uiFrame.setTranslationX(newX);
            uiFrame.setTranslationY(newY);

            if (uiFrame.getFrameData().parentFrame != null) {
                previewFrameBounds(uiFrame.getFrameData().parentFrame);
            }
        }
    }

    public void previewFrameMove(String frameId, float totalUiDx, float totalUiDy) {
        UIFrame uiFrame = mFrameViews.get(frameId);
        if (uiFrame != null) {
            float startX = uiFrame.getFrameData().uiPos[0];
            float startY = uiFrame.getFrameData().uiPos[1];
            uiFrame.setTranslationX(startX + totalUiDx);
            uiFrame.setTranslationY(startY + totalUiDy);
        }

        for (Map.Entry<String, UINode> entry : mViewport.getNodeViews().entrySet()) {
            UINode node = entry.getValue();
            if (frameId.equals(node.getNodeData().parentFrame)) {
                float startNx = node.getNodeData().getX();
                float startNy = node.getNodeData().getY();
                node.setTranslationX(startNx + totalUiDx);
                node.setTranslationY(startNy + totalUiDy);
                mViewport.updateConnectionsForNode(node.getNodeData().id);
            }
        }

        for (Map.Entry<String, UIFrame> entry : mFrameViews.entrySet()) {
            UIFrame childFrame = entry.getValue();
            if (frameId.equals(childFrame.getFrameData().parentFrame)) {
                previewFrameMove(childFrame.getFrameData().id, totalUiDx, totalUiDy);
            }
        }

        if (uiFrame != null && uiFrame.getFrameData().parentFrame != null) {
            previewFrameBounds(uiFrame.getFrameData().parentFrame);
        }
    }
}