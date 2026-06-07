package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.*;
import com.mine.geometry_node.client.ui.viewport.menu.FrameMenu;
import com.mine.geometry_node.client.ui.viewport.menu.GroupNodeMenu;
import com.mine.geometry_node.client.ui.viewport.menu.PortMenu;
import com.mine.geometry_node.client.ui.viewport.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InteractionManager {
    // --- 新增：意图监听器接口 ---
    public interface InteractionListener {
        void onMoveElements(List<String> nodeIds, List<String> frameIds, float dx, float dy);
        void onChangeParent(List<String> elementIds, boolean isNode, String newParentId);
        void onConnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId);
        void onDisconnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId);
        void onMoveElementsTo(Map<String, float[]> nodePositions, Map<String, float[]> framePositions);
        void onNodeDoubleClicked(String nodeId);
        boolean isCyclicFrame(String draggedFrameId, String potentialParentId);
    }

    private static final long DOUBLE_CLICK_TIMEOUT_MS = 300L;

    private static final int MODE_NONE           = 0;
    private static final int MODE_PANNING        = 1;
    private static final int MODE_DRAGGING_NODES = 2;
    private static final int MODE_SELECTING      = 3;
    private static final int MODE_CONNECTING     = 4;
    private static final int MODE_DRAGGING_FRAME = 5;
    private static final int MODE_CUTTING        = 6;

    private FrameVisualAdapter mDraggedFrame = null;
    private final InteractionContext mContext;
    private InteractionListener mListener;
    private int mCurrentMode = MODE_NONE;

    private float mDownScreenX, mDownScreenY;
    private float mLastScreenX, mLastScreenY;
    private boolean mHasMovedSignificantly = false;
    private long mLastClickTimeMs;
    private String mLastClickedNodeId;
    private float mDragStartUiX, mDragStartUiY;
    private float mDragAnchorStartUiX, mDragAnchorStartUiY;
    private float mAppliedDragUiDx, mAppliedDragUiDy;
    private float mSelectionStartUiX, mSelectionStartUiY;
    private final RectF mSelectionRectUi = new RectF();
    private Viewport.PortInfo mDraftStartPort = null;
    private float mDraftCurrentUiX, mDraftCurrentUiY;

    private final Paint mSelectionFillPaint = new Paint();
    private final Paint mSelectionBorderPaint = new Paint();
    private final Paint mDraftLinePaint = new Paint();
    private final float[] mTempPos = new float[2];

    // 轨迹记录
    private final List<Float> mCutPath = new ArrayList<>();
    // 刀锋画笔
    private final Paint mCutLinePaint = new Paint();

    public InteractionManager(InteractionContext context) {
        this.mContext = context;
        initPaints();
    }

    public void setListener(InteractionListener listener) {
        this.mListener = listener;
    }

    private void initPaints() {
        mSelectionFillPaint.setColor(UIConstants.ViewPort.Selection.CLR_FILL);
        mSelectionFillPaint.setStyle(Paint.Style.FILL);
        mSelectionBorderPaint.setColor(UIConstants.ViewPort.Selection.CLR_BORDER);
        mSelectionBorderPaint.setStyle(Paint.Style.STROKE);
        mSelectionBorderPaint.setStrokeWidth(UIUtils.dp2px(UIConstants.ViewPort.Selection.STROKE_WIDTH));
        mDraftLinePaint.setAntiAlias(true);
        mDraftLinePaint.setStyle(Paint.Style.STROKE);
        mDraftLinePaint.setColor(UIConstants.ViewPort.Connection.CLR_DRAFT_LINE);

        // 刀锋连线样式
        mCutLinePaint.setAntiAlias(true);
        mCutLinePaint.setStyle(Paint.Style.STROKE);
        mCutLinePaint.setColor(0xFFFF4444);
        mCutLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mCutLinePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        if (!mContext.isReady()) return false;
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            mContext.getCamera().zoom(scrollY > 0, event.getX(), event.getY());
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!mContext.isReady()) return false;
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: handleActionDown(event, x, y); return true;
            case MotionEvent.ACTION_MOVE: handleActionMove(x, y); return true;
            case MotionEvent.ACTION_UP: handleActionUp(event, x, y); return true;
            default: return false;
        }
    }

    private void handleActionDown(MotionEvent event, float screenX, float screenY) {
        mLastScreenX = screenX; mLastScreenY = screenY;
        mDownScreenX = screenX; mDownScreenY = screenY;
        mHasMovedSignificantly = false;

        ViewportCamera camera = mContext.getCamera();
        float uiX = camera.screenToUIX(screenX);
        float uiY = camera.screenToUIY(screenY);

        if (event.isCtrlPressed() && !isRightMouse(event) && !isMiddleMouse(event)) {
            mCurrentMode = MODE_CUTTING;
            mCutPath.clear();
            mCutPath.add(uiX);
            mCutPath.add(uiY);
            return;
        }

        if (isMiddleMouse(event)) {
            resetDoubleClickTracking();
            mCurrentMode = MODE_PANNING;
            return;
        }
        if (isRightMouse(event)) {
            resetDoubleClickTracking();
            return;
        }

        Viewport.PortInfo port = mContext.findPortAt(uiX, uiY);
        if (port != null) { enterConnectingMode(port, uiX, uiY); return; }

        NodeVisualAdapter target = mContext.findNodeAt(uiX, uiY);
        if (target != null) {
            float localXpx = UIUtils.dp2px(uiX - target.getUiX());
            float localYpx = UIUtils.dp2px(uiY - target.getUiY());
            if (target.findInteractiveViewAt(localXpx, localYpx) != null) return;
            enterDraggingMode(target, uiX, uiY);
            return;
        }

        FrameVisualAdapter targetFrame = mContext.findFrameAt(uiX, uiY);
        if (targetFrame != null) {
            mCurrentMode = MODE_DRAGGING_FRAME;
            mDragStartUiX = uiX; mDragStartUiY = uiY;
            mDragAnchorStartUiX = targetFrame.getUiX();
            mDragAnchorStartUiY = targetFrame.getUiY();
            mAppliedDragUiDx = 0.0f;
            mAppliedDragUiDy = 0.0f;
            mDraggedFrame = targetFrame;
            mContext.clearSelection();
            mContext.addToSelection(targetFrame);
            return;
        }
        enterSelectingMode(uiX, uiY);
    }

    private void handleActionMove(float screenX, float screenY) {
        float dx = screenX - mLastScreenX;
        float dy = screenY - mLastScreenY;
        float totalDx = screenX - mDownScreenX;
        float totalDy = screenY - mDownScreenY;
        float touchSlopPx = UIUtils.dp2px(UIConstants.ViewPort.Interaction.TOUCH_SLOP);

        if (Math.abs(totalDx) > touchSlopPx || Math.abs(totalDy) > touchSlopPx) mHasMovedSignificantly = true;

        ViewportCamera camera = mContext.getCamera();
        float uiX = camera.screenToUIX(screenX);
        float uiY = camera.screenToUIY(screenY);

        switch (mCurrentMode) {
            case MODE_PANNING: mContext.getCamera().pan(dx, dy); break;
            case MODE_DRAGGING_NODES: updateNodeDragPreview(uiX, uiY); break;
            case MODE_SELECTING: updateBoxSelection(uiX, uiY); break;
            case MODE_CONNECTING: mDraftCurrentUiX = uiX; mDraftCurrentUiY = uiY; mContext.invalidate(); break;
            case MODE_DRAGGING_FRAME:
                if (mDraggedFrame != null) {
                    updateFrameDragPreview(uiX, uiY);
                }
                break;
            case MODE_CUTTING:
                if (mCutPath.size() >= 2) {
                    float lastPathX = mCutPath.get(mCutPath.size() - 2);
                    float lastPathY = mCutPath.get(mCutPath.size() - 1);

                    float distSq = (uiX - lastPathX) * (uiX - lastPathX) + (uiY - lastPathY) * (uiY - lastPathY);
                    if (distSq > UIUtils.dp2px(2) * UIUtils.dp2px(2)) {
                        mCutPath.add(uiX);
                        mCutPath.add(uiY);

                        mContext.cutIntersectingConnections(lastPathX, lastPathY, uiX, uiY, mListener);
                    }
                }
                mContext.invalidate();
                break;
        }
        mLastScreenX = screenX; mLastScreenY = screenY;
    }

    private void handleActionUp(MotionEvent event, float screenX, float screenY) {
        ViewportCamera camera = mContext.getCamera();
        float uiX = camera.screenToUIX(screenX);
        float uiY = camera.screenToUIY(screenY);

        if (isRightMouse(event) && !mHasMovedSignificantly) {
            NodeVisualAdapter targetNode = mContext.findNodeAt(uiX, uiY);
            if (targetNode != null) {
                float localX = uiX - targetNode.getUiX();
                float localY = uiY - targetNode.getUiY();
                String clickedLabelPortId = targetNode.hitTestLabel(UIUtils.dp2px(localX), UIUtils.dp2px(localY));
                if (clickedLabelPortId != null) {
                    PortMenu.show(mContext, targetNode, clickedLabelPortId, screenX, screenY);
                    mCurrentMode = MODE_NONE;
                    return;
                }
                if (targetNode.getNodeData().isGroupNode() && localY >= 0 && localY <= UIConstants.Node.HEADER_HEIGHT) {
                    mContext.clearSelection();
                    mContext.addToSelection(targetNode);
                    GroupNodeMenu.show(mContext, targetNode, screenX, screenY);
                    mCurrentMode = MODE_NONE;
                    mContext.invalidate();
                    return;
                }
            }
            if (targetNode == null) {
                FrameVisualAdapter targetFrame = mContext.findFrameAt(uiX, uiY);
                if (targetFrame != null) {
                    mContext.clearSelection();
                    mContext.addToSelection(targetFrame);
                    FrameMenu.show(mContext, targetFrame, screenX, screenY);
                    mCurrentMode = MODE_NONE;
                    mContext.invalidate();
                    return;
                }
            }
            mContext.showMenu(screenX, screenY);
        }

        switch (mCurrentMode) {
            case MODE_DRAGGING_NODES: finalizeNodeDragging(uiX, uiY); break;
            case MODE_CONNECTING: finalizeConnection(uiX, uiY); break;
            case MODE_SELECTING: mSelectionRectUi.setEmpty(); break;
            case MODE_DRAGGING_FRAME: finalizeFrameDragging(uiX, uiY); break;
            case MODE_CUTTING: mCutPath.clear(); break;
        }

        mCurrentMode = MODE_NONE;
        mContext.invalidate();
    }

    private void enterConnectingMode(Viewport.PortInfo port, float uiX, float uiY) {
        mCurrentMode = MODE_CONNECTING;
        mDraftStartPort = port; mDraftCurrentUiX = uiX; mDraftCurrentUiY = uiY;
    }

    private void enterDraggingMode(NodeVisualAdapter target, float uiX, float uiY) {
        mCurrentMode = MODE_DRAGGING_NODES;
        mDragStartUiX = uiX; mDragStartUiY = uiY;
        mDragAnchorStartUiX = target.getUiX();
        mDragAnchorStartUiY = target.getUiY();
        mAppliedDragUiDx = 0.0f;
        mAppliedDragUiDy = 0.0f;
        if (!target.isSelected()) {
            mContext.clearSelection();
        }
        mContext.addToSelection(target);
    }

    private void updateNodeDragPreview(float currentUiX, float currentUiY) {
        if (mHasMovedSignificantly) {
            resetDoubleClickTracking();
        }
        float rawTotalUiDx = currentUiX - mDragStartUiX;
        float rawTotalUiDy = currentUiY - mDragStartUiY;
        float snappedTotalUiDx = getSnappedDragDx(rawTotalUiDx);
        float snappedTotalUiDy = getSnappedDragDy(rawTotalUiDy);
        float deltaUiDx = snappedTotalUiDx - mAppliedDragUiDx;
        float deltaUiDy = snappedTotalUiDy - mAppliedDragUiDy;

        if (deltaUiDx != 0.0f || deltaUiDy != 0.0f) {
            mContext.moveSelectedNodes(deltaUiDx, deltaUiDy);
            mAppliedDragUiDx = snappedTotalUiDx;
            mAppliedDragUiDy = snappedTotalUiDy;
        }
    }

    private void enterSelectingMode(float uiX, float uiY) {
        mCurrentMode = MODE_SELECTING;
        mContext.clearSelection();
        mSelectionStartUiX = uiX; mSelectionStartUiY = uiY;
        mSelectionRectUi.set(uiX, uiY, uiX, uiY);
    }

    private void updateBoxSelection(float currentUiX, float currentUiY) {
        float x = Math.min(mSelectionStartUiX, currentUiX);
        float y = Math.min(mSelectionStartUiY, currentUiY);
        float w = Math.abs(currentUiX - mSelectionStartUiX);
        float h = Math.abs(currentUiY - mSelectionStartUiY);
        mSelectionRectUi.set(x, y, x + w, y + h);
        mContext.updateBoxSelection(x, y, w, h);
        mContext.invalidate();
    }

    private void finalizeNodeDragging(float endUiX, float endUiY) {
        float rawTotalUiDx = endUiX - mDragStartUiX;
        float rawTotalUiDy = endUiY - mDragStartUiY;
        float totalUiDx = getSnappedDragDx(rawTotalUiDx);
        float totalUiDy = getSnappedDragDy(rawTotalUiDy);

        float previewDeltaUiDx = totalUiDx - mAppliedDragUiDx;
        float previewDeltaUiDy = totalUiDy - mAppliedDragUiDy;
        if (previewDeltaUiDx != 0.0f || previewDeltaUiDy != 0.0f) {
            mContext.moveSelectedNodes(previewDeltaUiDx, previewDeltaUiDy);
            mAppliedDragUiDx = totalUiDx;
            mAppliedDragUiDy = totalUiDy;
        }

        if (Math.abs(totalUiDx) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE || Math.abs(totalUiDy) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE) {
            List<String> selectedIds = new ArrayList<>();
            for (NodeVisualAdapter node : mContext.getSelectedNodeVisuals()) selectedIds.add(node.getNodeId());

            if (mListener != null) mListener.onMoveElements(selectedIds, new ArrayList<>(), totalUiDx, totalUiDy);

            FrameVisualAdapter targetFrame = mContext.getSmallestContainingFrame(endUiX, endUiY);
            String targetFrameId = (targetFrame != null) ? targetFrame.getFrameId() : null;

            List<String> nodesToChange = new ArrayList<>();
            for (NodeVisualAdapter node : mContext.getSelectedNodeVisuals()) {
                String currentParent = node.getNodeData().parentFrame;
                if ((currentParent == null && targetFrameId != null) || (currentParent != null && !currentParent.equals(targetFrameId))) {
                    nodesToChange.add(node.getNodeId());
                }
            }
            if (!nodesToChange.isEmpty() && mListener != null) mListener.onChangeParent(nodesToChange, true, targetFrameId);
        } else {
            NodeVisualAdapter clickedNode = mContext.findNodeAt(endUiX, endUiY);
            if (clickedNode != null) {
                handleNodeClick(clickedNode);
            }
            for (NodeVisualAdapter node : mContext.getSelectedNodeVisuals()) {
                node.setPreviewPosition(node.getNodeData().getX(), node.getNodeData().getY());
                mContext.updateConnectionsForNode(node.getNodeId());
            }
            mContext.invalidate();
        }
    }

    private void handleNodeClick(NodeVisualAdapter node) {
        long now = System.currentTimeMillis();
        String nodeId = node.getNodeId();
        if (nodeId != null
                && nodeId.equals(mLastClickedNodeId)
                && now - mLastClickTimeMs <= DOUBLE_CLICK_TIMEOUT_MS) {
            mLastClickedNodeId = null;
            mLastClickTimeMs = 0L;
            if (mListener != null) {
                mListener.onNodeDoubleClicked(nodeId);
            }
            return;
        }

        mLastClickedNodeId = nodeId;
        mLastClickTimeMs = now;
    }

    private void resetDoubleClickTracking() {
        mLastClickedNodeId = null;
        mLastClickTimeMs = 0L;
    }

    private void updateFrameDragPreview(float currentUiX, float currentUiY) {
        float rawTotalUiDx = currentUiX - mDragStartUiX;
        float rawTotalUiDy = currentUiY - mDragStartUiY;

        if (rawTotalUiDx != mAppliedDragUiDx || rawTotalUiDy != mAppliedDragUiDy) {
            mContext.previewFrameMove(mDraggedFrame.getFrameData().id, rawTotalUiDx, rawTotalUiDy);
            mAppliedDragUiDx = rawTotalUiDx;
            mAppliedDragUiDy = rawTotalUiDy;
        }
    }

    private float getSnappedDragDx(float rawTotalUiDx) {
        return getSnappedDragDelta(mDragAnchorStartUiX, rawTotalUiDx);
    }

    private float getSnappedDragDy(float rawTotalUiDy) {
        return getSnappedDragDelta(mDragAnchorStartUiY, rawTotalUiDy);
    }

    private float getSnappedDragDelta(float anchorStartUi, float rawTotalUiDelta) {
        if (!mContext.isSnapToGridEnabled()) return rawTotalUiDelta;

        float gridSize = mContext.getSnapGridSize();
        if (gridSize <= 0.0f) return rawTotalUiDelta;

        float snappedAnchorUi = Math.round((anchorStartUi + rawTotalUiDelta) / gridSize) * gridSize;
        return snappedAnchorUi - anchorStartUi;
    }

    private void finalizeFrameDragging(float endUiX, float endUiY) {
        if (mDraggedFrame == null) return;
        float rawTotalUiDx = endUiX - mDragStartUiX;
        float rawTotalUiDy = endUiY - mDragStartUiY;
        String draggedFrameId = mDraggedFrame.getFrameData().id;

        if (rawTotalUiDx != mAppliedDragUiDx || rawTotalUiDy != mAppliedDragUiDy) {
            mContext.previewFrameMove(draggedFrameId, rawTotalUiDx, rawTotalUiDy);
            mAppliedDragUiDx = rawTotalUiDx;
            mAppliedDragUiDy = rawTotalUiDy;
        }

        if (Math.abs(rawTotalUiDx) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE || Math.abs(rawTotalUiDy) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE) {
            if (mListener != null) {
                if (mContext.isSnapToGridEnabled()) {
                    MoveTargets targets = collectFrameMoveTargets(draggedFrameId, rawTotalUiDx, rawTotalUiDy);
                    mListener.onMoveElementsTo(targets.nodePositions, targets.framePositions);
                } else {
                    mListener.onMoveElements(new ArrayList<>(), List.of(draggedFrameId), rawTotalUiDx, rawTotalUiDy);
                }
            }

            FrameVisualAdapter largerFrame = getSmallestContainingFrameForFrame(draggedFrameId, endUiX, endUiY);
            String newParentId = (largerFrame != null) ? largerFrame.getFrameId() : null;
            String currentParent = mDraggedFrame.getFrameData().parentFrame;

            if ((currentParent == null && newParentId != null) || (currentParent != null && !currentParent.equals(newParentId))) {
                if (mListener != null) mListener.onChangeParent(List.of(draggedFrameId), false, newParentId);
            }
        } else {
            mContext.updateFrameBounds(draggedFrameId);
        }
        mDraggedFrame = null;
    }

    private MoveTargets collectFrameMoveTargets(String frameId, float rawTotalUiDx, float rawTotalUiDy) {
        MoveTargets targets = new MoveTargets();
        collectFrameMoveTargets(frameId, rawTotalUiDx, rawTotalUiDy, targets);
        return targets;
    }

    private boolean collectFrameMoveTargets(String frameId, float rawTotalUiDx, float rawTotalUiDy, MoveTargets targets) {
        boolean hasChildren = false;
        float gridSize = mContext.getSnapGridSize();

        for (NodeVisualAdapter node : mContext.getAllNodeVisuals()) {
            if (frameId.equals(node.getParentFrameId())) {
                hasChildren = true;
                targets.nodePositions.put(node.getNodeId(), new float[]{
                        snapCoordinate(node.getNodeData().getX() + rawTotalUiDx, gridSize),
                        snapCoordinate(node.getNodeData().getY() + rawTotalUiDy, gridSize)
                });
            }
        }

        for (FrameVisualAdapter frame : mContext.getAllFrameVisuals()) {
            if (frameId.equals(frame.getParentFrameId())) {
                hasChildren = true;
                collectFrameMoveTargets(frame.getFrameId(), rawTotalUiDx, rawTotalUiDy, targets);
            }
        }

        if (!hasChildren) {
            FrameVisualAdapter frame = findFrameVisual(frameId);
            if (frame != null) {
                targets.framePositions.put(frameId, new float[]{
                        snapCoordinate(frame.getFrameData().uiPos[0] + rawTotalUiDx, gridSize),
                        snapCoordinate(frame.getFrameData().uiPos[1] + rawTotalUiDy, gridSize)
                });
            }
        }

        return hasChildren;
    }

    private FrameVisualAdapter findFrameVisual(String frameId) {
        for (FrameVisualAdapter frame : mContext.getAllFrameVisuals()) {
            if (frameId.equals(frame.getFrameId())) return frame;
        }
        return null;
    }

    private float snapCoordinate(float value, float gridSize) {
        if (gridSize <= 0.0f) return value;
        return Math.round(value / gridSize) * gridSize;
    }

    private static final class MoveTargets {
        final Map<String, float[]> nodePositions = new HashMap<>();
        final Map<String, float[]> framePositions = new HashMap<>();
    }

    private FrameVisualAdapter getSmallestContainingFrameForFrame(String draggedFrameId, float uiX, float uiY) {
        FrameVisualAdapter target = null;
        float minArea = Float.MAX_VALUE;

        for (FrameVisualAdapter frame : mContext.getAllFrameVisuals()) {
            String fid = frame.getFrameId();
            if (fid.equals(draggedFrameId) || (mListener != null && mListener.isCyclicFrame(draggedFrameId, fid))) {
                continue;
            }
            float[] pos = frame.getFrameData().uiPos;
            float[] size = frame.getFrameData().uiSize;
            if (uiX >= pos[0] && uiX <= pos[0] + size[0] && uiY >= pos[1] && uiY <= pos[1] + size[1]) {
                float area = size[0] * size[1];
                if (area < minArea) { minArea = area; target = frame; }
            }
        }
        return target;
    }

    private void finalizeConnection(float endUiX, float endUiY) {
        Viewport.PortInfo endPort = mContext.findPortAt(endUiX, endUiY);
        if (isValidConnection(mDraftStartPort, endPort)) {
            Viewport.PortInfo input = mDraftStartPort.isInput ? mDraftStartPort : endPort;
            Viewport.PortInfo output = mDraftStartPort.isInput ? endPort : mDraftStartPort;
            if (!mContext.hasConnection(output.node, output.portId, input.node, input.portId)) {
                if (mListener != null) mListener.onConnectPorts(output.node.getNodeId(), output.portId, input.node.getNodeId(), input.portId);
            }
        }
        mDraftStartPort = null;
    }

    public void drawOverlay(Canvas canvas) {
        if (mCurrentMode == MODE_CONNECTING && mDraftStartPort != null) drawDraftLine(canvas);
        if (mCurrentMode == MODE_SELECTING) {
            ViewportCamera camera = mContext.getCamera();
            float l = camera.uiToScreenX(mSelectionRectUi.left);
            float t = camera.uiToScreenY(mSelectionRectUi.top);
            float r = camera.uiToScreenX(mSelectionRectUi.right);
            float b = camera.uiToScreenY(mSelectionRectUi.bottom);
            canvas.drawRect(l, t, r, b, mSelectionFillPaint);
            canvas.drawRect(l, t, r, b, mSelectionBorderPaint);
        }
        if (mCurrentMode == MODE_CUTTING && mCutPath.size() >= 4) {
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
    }

    private void drawDraftLine(Canvas canvas) {
        ViewportCamera camera = mContext.getCamera();
        mDraftLinePaint.setStrokeWidth(UIUtils.dp2px(UIConstants.ViewPort.Connection.LINE_WIDTH_DRAFT) * camera.getScale());
        mDraftStartPort.node.getPortPosition(mDraftStartPort.portId, mDraftStartPort.isInput, mTempPos);
        canvas.drawLine(camera.uiToScreenX(mDraftStartPort.node.getUiX() + mTempPos[0]),
                camera.uiToScreenY(mDraftStartPort.node.getUiY() + mTempPos[1]),
                camera.uiToScreenX(mDraftCurrentUiX), camera.uiToScreenY(mDraftCurrentUiY), mDraftLinePaint);
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
            if (portInfo.isInput) { if (row.leftPort() != null && row.leftPort().id().equals(portInfo.portId)) return row.leftPort().type(); }
            else { if (row.rightPort() != null && row.rightPort().id().equals(portInfo.portId)) return row.rightPort().type(); }
        }
        return null;
    }

    private boolean isRightMouse(MotionEvent e) { return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0 || e.getActionButton() == MotionEvent.BUTTON_SECONDARY; }
    private boolean isMiddleMouse(MotionEvent e) { return (e.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0 || e.getActionButton() == MotionEvent.BUTTON_TERTIARY; }
}
