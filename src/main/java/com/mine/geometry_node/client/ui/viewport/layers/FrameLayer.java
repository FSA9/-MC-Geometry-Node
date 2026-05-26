package com.mine.geometry_node.client.ui.viewport.layers;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;
import com.mine.geometry_node.client.ui.viewport.FrameBoundsCalculator;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.visual.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.visual.NodeVisualAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrameLayer extends FrameLayout {
    private final Viewport mViewport;
    private final Map<String, FrameVisualAdapter> mFrameVisuals = new HashMap<>();
    private final List<FrameVisualAdapter> mFrameOrder = new ArrayList<>();

    private final List<FrameVisualAdapter> mSelectedFrames = new ArrayList<>();
    private final RectF mTmpFrameBounds = new RectF();
    private final RectF mTmpVisibleBounds = new RectF();

    private static final float CULL_PADDING_DP = 64f;
    private final FrameBoundsCalculator.Result mTmpFrameBoundsResult = new FrameBoundsCalculator.Result();

    public FrameLayer(Context context, Viewport viewport) {
        super(context);
        this.mViewport = viewport;
        setClipChildren(false);
        setWillNotDraw(false);
        setPivotX(0);
        setPivotY(0);
    }

    public Map<String, FrameVisualAdapter> getFrameVisuals() {
        return mFrameVisuals;
    }

    public void clearFrameVisuals() {
        removeAllViews();
        mFrameVisuals.clear();
        mFrameOrder.clear();
        mSelectedFrames.clear();
    }

    public void addFrameVisual(String frameId, FrameVisualAdapter frame) {
        mFrameVisuals.put(frameId, frame);
        mFrameOrder.add(frame);
        invalidate();
    }

    public void removeFrameVisual(String frameId) {
        FrameVisualAdapter frame = mFrameVisuals.remove(frameId);
        if (frame != null) {
            mFrameOrder.remove(frame);
            mSelectedFrames.remove(frame);
            invalidate();
        }
    }

    public void updateFrameBounds(String frameId) {
        FrameVisualAdapter frame = mFrameVisuals.get(frameId);
        if (frame != null) {
            frame.updateBounds();
            invalidate();
        }
    }

    public void updateFrameVisual(String frameId) {
        FrameVisualAdapter frame = mFrameVisuals.get(frameId);
        if (frame != null) {
            frame.updateTitle();
            invalidate();
        }
    }

    public FrameVisualAdapter findFrameAt(float uiX, float uiY) {
        // 倒序遍历（优先命中显示在最上层的子图框）
        for (int i = mFrameOrder.size() - 1; i >= 0; i--) {
            FrameVisualAdapter frame = mFrameOrder.get(i);
            if (frame.hitTest(uiX, uiY)) {
                return frame;
            }
        }
        return null;
    }

    public void clearSelection() {
        for (FrameVisualAdapter frame : mSelectedFrames) {
            frame.setSelected(false);
        }
        mSelectedFrames.clear();
        invalidate();
    }

    public void addToSelection(FrameVisualAdapter frame) {
        if (frame != null && !mSelectedFrames.contains(frame)) {
            frame.setSelected(true);
            mSelectedFrames.add(frame);
            invalidate();
        }
    }

    public List<FrameVisualAdapter> getSelectedFrameVisuals() {
        return mSelectedFrames;
    }

    public FrameVisualAdapter getSmallestContainingFrame(float uiX, float uiY) {
        FrameVisualAdapter target = null;
        float minArea = Float.MAX_VALUE;

        for (FrameVisualAdapter frame : mFrameOrder) {
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
        return target;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean canCull = getWidth() > 0 && getHeight() > 0;
        if (canCull) {
            mViewport.getCamera().getVisibleUiRect(mTmpVisibleBounds, getWidth(), getHeight(), CULL_PADDING_DP);
        }

        for (FrameVisualAdapter frame : mFrameOrder) {
            frame.getLogicalBounds(mTmpFrameBounds);
            if (!canCull || mTmpFrameBounds.intersects(mTmpVisibleBounds.left, mTmpVisibleBounds.top, mTmpVisibleBounds.right, mTmpVisibleBounds.bottom)) {
                frame.drawFrame(canvas, mViewport.getCamera());
            }
        }
    }

    public void previewFrameBounds(String frameId) {
        FrameVisualAdapter frame = mFrameVisuals.get(frameId);
        if (frame == null) return;

        FrameBoundsCalculator.Result bounds = FrameBoundsCalculator.computePreviewBounds(
                frameId,
                mViewport.getNodeVisuals().values(),
                mFrameVisuals.values(),
                mTmpFrameBoundsResult
        );

        if (bounds.hasChildren()) {
            frame.setPreviewBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            invalidate();

            if (frame.getParentFrameId() != null) {
                previewFrameBounds(frame.getParentFrameId());
            }
        }
    }

    public void previewFrameMove(String frameId, float totalUiDx, float totalUiDy) {
        FrameVisualAdapter frame = mFrameVisuals.get(frameId);
        if (frame != null) {
            float startX = frame.getFrameData().uiPos[0];
            float startY = frame.getFrameData().uiPos[1];
            frame.setPreviewPosition(startX + totalUiDx, startY + totalUiDy);
            invalidate();
        }

        for (Map.Entry<String, ? extends NodeVisualAdapter> entry : mViewport.getNodeVisuals().entrySet()) {
            NodeVisualAdapter node = entry.getValue();
            if (frameId.equals(node.getParentFrameId())) {
                float startNx = node.getNodeData().getX();
                float startNy = node.getNodeData().getY();
                node.setPreviewPosition(startNx + totalUiDx, startNy + totalUiDy);
                mViewport.notifyNodeVisualMoved(node);
                mViewport.updateConnectionsForNode(node.getNodeId());
            }
        }

        for (Map.Entry<String, FrameVisualAdapter> entry : mFrameVisuals.entrySet()) {
            FrameVisualAdapter childFrame = entry.getValue();
            if (frameId.equals(childFrame.getParentFrameId())) {
                previewFrameMove(childFrame.getFrameId(), totalUiDx, totalUiDy);
            }
        }

        if (frame != null && frame.getParentFrameId() != null) {
            previewFrameBounds(frame.getParentFrameId());
        }
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
