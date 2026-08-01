package com.mine.geometry_node.client.ui.viewport.frame;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FrameLayer extends FrameLayout {
    private final Viewport mViewport;
    private final Map<String, FrameVisualAdapter> mFrameVisuals = new HashMap<>();
    private final List<FrameVisualAdapter> mFrameOrder = new ArrayList<>();

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
    }

    public void addFrameVisual(String frameId, FrameVisualAdapter frame) {
        FrameVisualAdapter oldFrame = mFrameVisuals.put(frameId, frame);
        if (oldFrame != null) {
            mFrameOrder.remove(oldFrame);
        }
        mFrameOrder.add(frame);
        ensureFrameHierarchyOrder();
        invalidate();
    }

    public void removeFrameVisual(String frameId) {
        FrameVisualAdapter frame = mFrameVisuals.remove(frameId);
        if (frame != null) {
            mFrameOrder.remove(frame);
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
        ensureFrameHierarchyOrder();
        // 倒序遍历（优先命中显示在最上层的子图框）
        for (int i = mFrameOrder.size() - 1; i >= 0; i--) {
            FrameVisualAdapter frame = mFrameOrder.get(i);
            if (frame.hitTest(uiX, uiY)) {
                return frame;
            }
        }
        return null;
    }

    public void applySelection(List<String> selectedFrameIds) {
        Set<String> selectedFrameIdSet = selectedFrameIds != null ? new HashSet<>(selectedFrameIds) : new HashSet<>();
        for (FrameVisualAdapter frame : mFrameVisuals.values()) {
            frame.setSelected(selectedFrameIdSet.contains(frame.getFrameId()));
        }
        for (FrameVisualAdapter frame : getFrameVisuals(selectedFrameIds)) {
            bringFrameToFront(frame);
        }
        invalidate();
    }

    private void bringFrameToFront(FrameVisualAdapter frame) {
        String rootId = frame.getFrameId();
        if (rootId == null) return;

        List<FrameVisualAdapter> subtree = new ArrayList<>();
        for (FrameVisualAdapter candidate : mFrameOrder) {
            if (candidate == frame || isDescendantOf(candidate, rootId)) {
                subtree.add(candidate);
            }
        }

        if (!subtree.isEmpty()) {
            mFrameOrder.removeAll(subtree);
            mFrameOrder.addAll(subtree);
            ensureFrameHierarchyOrder();
        }
    }

    public List<FrameVisualAdapter> getFrameVisuals(List<String> frameIds) {
        List<FrameVisualAdapter> frames = new ArrayList<>();
        if (frameIds == null) return frames;
        for (String frameId : frameIds) {
            FrameVisualAdapter frame = mFrameVisuals.get(frameId);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
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

    public List<String> findFrameIdsInRect(float uiX, float uiY, float uiW, float uiH) {
        List<String> selectedFrameIds = new ArrayList<>();
        float selRight = uiX + uiW;
        float selBottom = uiY + uiH;

        for (FrameVisualAdapter frame : mFrameOrder) {
            frame.getLogicalBounds(mTmpFrameBounds);
            if (mTmpFrameBounds.intersects(uiX, uiY, selRight, selBottom)) {
                selectedFrameIds.add(frame.getFrameId());
            }
        }
        return selectedFrameIds;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void drawFrames(Canvas canvas) {
        ensureFrameHierarchyOrder();
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

    public void drawFramesForExport(Canvas canvas, ViewportCamera camera) {
        ensureFrameHierarchyOrder();
        for (FrameVisualAdapter frame : mFrameOrder) {
            boolean selected = frame.isSelected();
            frame.setSelected(false);
            try {
                frame.drawFrame(canvas, camera);
            } finally {
                frame.setSelected(selected);
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
        previewFrameMoveInternal(frameId, totalUiDx, totalUiDy, mViewport.isSnapToGridEnabled(), mViewport.getSnapGridSize());
    }

    private boolean previewFrameMoveInternal(String frameId, float totalUiDx, float totalUiDy, boolean snapToGrid, float gridSize) {
        FrameVisualAdapter frame = mFrameVisuals.get(frameId);
        boolean hasChildren = false;

        for (Map.Entry<String, ? extends NodeVisualAdapter> entry : mViewport.getNodeVisuals().entrySet()) {
            NodeVisualAdapter node = entry.getValue();
            if (frameId.equals(node.getParentFrameId())) {
                hasChildren = true;
                float startNx = node.getNodeData().getX();
                float startNy = node.getNodeData().getY();
                node.setPreviewPosition(
                        snapIfNeeded(startNx + totalUiDx, snapToGrid, gridSize),
                        snapIfNeeded(startNy + totalUiDy, snapToGrid, gridSize)
                );
                mViewport.notifyNodeVisualMoved(node);
                mViewport.updateConnectionsForNode(node.getNodeId());
            }
        }

        for (Map.Entry<String, FrameVisualAdapter> entry : mFrameVisuals.entrySet()) {
            FrameVisualAdapter childFrame = entry.getValue();
            if (frameId.equals(childFrame.getParentFrameId())) {
                hasChildren = true;
                previewFrameMoveInternal(childFrame.getFrameId(), totalUiDx, totalUiDy, snapToGrid, gridSize);
            }
        }

        if (frame != null) {
            if (hasChildren) {
                previewFrameBounds(frameId);
            } else {
                float startX = frame.getFrameData().uiPos[0];
                float startY = frame.getFrameData().uiPos[1];
                frame.setPreviewPosition(
                        snapIfNeeded(startX + totalUiDx, snapToGrid, gridSize),
                        snapIfNeeded(startY + totalUiDy, snapToGrid, gridSize)
                );
                invalidate();

                if (frame.getParentFrameId() != null) {
                    previewFrameBounds(frame.getParentFrameId());
                }
            }
        }

        return hasChildren;
    }

    private float snapIfNeeded(float value, boolean snapToGrid, float gridSize) {
        if (!snapToGrid || gridSize <= 0.0f) return value;
        return Math.round(value / gridSize) * gridSize;
    }

    private void ensureFrameHierarchyOrder() {
        if (mFrameOrder.size() < 2) return;

        List<FrameVisualAdapter> ordered = new ArrayList<>(mFrameOrder.size());
        Set<String> emitted = new HashSet<>();
        for (FrameVisualAdapter frame : mFrameOrder) {
            appendAfterParents(frame, ordered, emitted, new HashSet<>());
        }

        if (ordered.size() == mFrameOrder.size()) {
            mFrameOrder.clear();
            mFrameOrder.addAll(ordered);
        }
    }

    private void appendAfterParents(
            FrameVisualAdapter frame,
            List<FrameVisualAdapter> ordered,
            Set<String> emitted,
            Set<String> visiting
    ) {
        if (frame == null) return;
        String frameId = frame.getFrameId();
        if (frameId == null || emitted.contains(frameId)) return;
        if (!visiting.add(frameId)) return;

        String parentFrameId = frame.getParentFrameId();
        FrameVisualAdapter parentFrame = parentFrameId != null ? mFrameVisuals.get(parentFrameId) : null;
        if (parentFrame != null && parentFrame != frame) {
            appendAfterParents(parentFrame, ordered, emitted, visiting);
        }

        visiting.remove(frameId);
        if (emitted.add(frameId)) {
            ordered.add(frame);
        }
    }

    private boolean isDescendantOf(FrameVisualAdapter frame, String ancestorFrameId) {
        if (frame == null || ancestorFrameId == null) return false;

        Set<String> visited = new HashSet<>();
        String parentFrameId = frame.getParentFrameId();
        while (parentFrameId != null && visited.add(parentFrameId)) {
            if (ancestorFrameId.equals(parentFrameId)) {
                return true;
            }
            FrameVisualAdapter parentFrame = mFrameVisuals.get(parentFrameId);
            parentFrameId = parentFrame != null ? parentFrame.getParentFrameId() : null;
        }
        return false;
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
