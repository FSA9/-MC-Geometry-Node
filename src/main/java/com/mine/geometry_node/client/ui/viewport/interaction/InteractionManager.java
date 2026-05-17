// --- START OF FILE InteractionManager.java ---
package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.UICommand.commands.*;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.*;
import com.mine.geometry_node.client.ui.viewport.menu.PortMenu;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

public class InteractionManager {
    private static final int MODE_NONE           = 0;
    private static final int MODE_PANNING        = 1;
    private static final int MODE_DRAGGING_NODES = 2;
    private static final int MODE_SELECTING      = 3;
    private static final int MODE_CONNECTING     = 4;
    private static final int MODE_DRAGGING_FRAME = 5;

    private UIFrame mDraggedFrame = null;

    private final InteractionContext mContext;
    private int mCurrentMode = MODE_NONE;

    private float mDownScreenX, mDownScreenY;
    private float mLastScreenX, mLastScreenY;
    private boolean mHasMovedSignificantly = false;

    private float mDragStartUiX, mDragStartUiY;
    private float mSelectionStartUiX, mSelectionStartUiY;
    private final RectF mSelectionRectUi = new RectF();

    private Viewport.PortInfo mDraftStartPort = null;
    private float mDraftCurrentUiX, mDraftCurrentUiY;

    private final Paint mSelectionFillPaint = new Paint();
    private final Paint mSelectionBorderPaint = new Paint();
    private final Paint mDraftLinePaint = new Paint();
    private final float[] mTempPos = new float[2];

    public InteractionManager(InteractionContext context) {
        this.mContext = context;
        initPaints();
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
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mContext.getEditorContext() == null) return false;

        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            // 委托给 Camera 处理缩放
            mContext.getCamera().zoom(scrollY > 0, event.getX(), event.getY());
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (mContext.getEditorContext() == null) return false;

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
        mLastScreenX = screenX;
        mLastScreenY = screenY;
        mDownScreenX = screenX;
        mDownScreenY = screenY;
        mHasMovedSignificantly = false;

        ViewportCamera camera = mContext.getCamera();
        float uiX = camera.screenToUIX(screenX);
        float uiY = camera.screenToUIY(screenY);

        if (isMiddleMouse(event)) { mCurrentMode = MODE_PANNING; return; }
        if (isRightMouse(event)) return;

        Viewport.PortInfo port = mContext.findPortAt(uiX, uiY);
        if (port != null) { enterConnectingMode(port, uiX, uiY); return; }

        UINode target = mContext.findNodeAt(uiX, uiY);
        if (target != null) {
            float localXpx = UIUtils.dp2px(uiX - target.getTranslationX());
            float localYpx = UIUtils.dp2px(uiY - target.getTranslationY());
            if (target.findInteractiveViewAt(localXpx, localYpx) != null) {
                return;
            }
            enterDraggingMode(target, uiX, uiY);
            return;
        }

        UIFrame targetFrame = mContext.findFrameAt(uiX, uiY);
        if (targetFrame != null) {
            mCurrentMode = MODE_DRAGGING_FRAME;
            mDragStartUiX = uiX;
            mDragStartUiY = uiY;
            mDraggedFrame = targetFrame;
            mContext.clearSelection();
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

        if (Math.abs(totalDx) > touchSlopPx || Math.abs(totalDy) > touchSlopPx) {
            mHasMovedSignificantly = true;
        }

        ViewportCamera camera = mContext.getCamera();
        float uiX = camera.screenToUIX(screenX);
        float uiY = camera.screenToUIY(screenY);
        float lastUiX = camera.screenToUIX(mLastScreenX);
        float lastUiY = camera.screenToUIY(mLastScreenY);

        float uiDx = uiX - lastUiX;
        float uiDy = uiY - lastUiY;

        switch (mCurrentMode) {
            case MODE_PANNING: mContext.getCamera().pan(dx, dy); break;
            case MODE_DRAGGING_NODES: updateNodeDragging(uiDx, uiDy); break;
            case MODE_SELECTING: updateBoxSelection(uiX, uiY); break;
            case MODE_CONNECTING: updateDraftLine(uiX, uiY); break;
            case MODE_DRAGGING_FRAME:
                // 这里传的是 totalUiDx，也就是 current - mDragStartUiX
                float totalUiDx = uiX - mDragStartUiX;
                float totalUiDy = uiY - mDragStartUiY;
                if (mDraggedFrame != null) {
                    ((Viewport) mContext).previewFrameMove(mDraggedFrame.getFrameData().id, totalUiDx, totalUiDy);
                }
                break;
        }

        mLastScreenX = screenX;
        mLastScreenY = screenY;
    }

    private void handleActionUp(MotionEvent event, float screenX, float screenY) {
        ViewportCamera camera = mContext.getCamera();
        float uiX = camera.screenToUIX(screenX);
        float uiY = camera.screenToUIY(screenY);

        if (isRightMouse(event) && !mHasMovedSignificantly) {
            UINode targetNode = mContext.findNodeAt(uiX, uiY);
            if (targetNode != null) {
                float localXpx = UIUtils.dp2px(uiX - targetNode.getTranslationX());
                float localYpx = UIUtils.dp2px(uiY - targetNode.getTranslationY());

                String clickedLabelPortId = targetNode.hitTestLabel(localXpx, localYpx);
                if (clickedLabelPortId != null) {
                    PortMenu.show(mContext, targetNode, clickedLabelPortId, screenX, screenY);
                    mCurrentMode = MODE_NONE;
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
        }

        mCurrentMode = MODE_NONE;
        mContext.invalidate();
    }

    private void enterConnectingMode(Viewport.PortInfo port, float uiX, float uiY) {
        mCurrentMode = MODE_CONNECTING;
        mDraftStartPort = port;
        mDraftCurrentUiX = uiX;
        mDraftCurrentUiY = uiY;
    }

    private void enterDraggingMode(UINode target, float uiX, float uiY) {
        mCurrentMode = MODE_DRAGGING_NODES;
        mDragStartUiX = uiX;
        mDragStartUiY = uiY;
        if (!target.isSelected()) {
            mContext.clearSelection();
            mContext.addToSelection(target);
        }
    }

    private void enterSelectingMode(float uiX, float uiY) {
        mCurrentMode = MODE_SELECTING;
        mContext.clearSelection();
        mSelectionStartUiX = uiX;
        mSelectionStartUiY = uiY;
        mSelectionRectUi.set(uiX, uiY, uiX, uiY);
    }

    private void updateNodeDragging(float uiDx, float uiDy) {
        mContext.moveSelectedNodes(uiDx, uiDy);
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

    private void updateDraftLine(float uiX, float uiY) {
        mDraftCurrentUiX = uiX;
        mDraftCurrentUiY = uiY;
        mContext.invalidate();
    }

    private void finalizeNodeDragging(float endUiX, float endUiY) {
        float totalUiDx = endUiX - mDragStartUiX;
        float totalUiDy = endUiY - mDragStartUiY;

        if (Math.abs(totalUiDx) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE ||
                Math.abs(totalUiDy) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE) {

            List<String> selectedIds = new ArrayList<>();
            for (UINode node : mContext.getSelectedNodes()) selectedIds.add(node.getNodeData().id);

            // 1. 执行移动命令 (替换原来的 CmdMoveNode)
            CmdMoveElements cmdMove = new CmdMoveElements(mContext.getEditorContext().getGraphController(), selectedIds, new ArrayList<>(), totalUiDx, totalUiDy);
            mContext.getEditorContext().getCommandManager().execute(cmdMove);

            // 2. 自动并入判定 (Auto-Parenting)
            UIFrame targetFrame = mContext.getSmallestContainingFrame(endUiX, endUiY);
            String targetFrameId = (targetFrame != null) ? targetFrame.getFrameData().id : null;

            // 找出真正需要变更父级的节点（避免无意义的命令入栈）
            List<String> nodesToChange = new ArrayList<>();
            for (UINode node : mContext.getSelectedNodes()) {
                String currentParent = node.getNodeData().parentFrame;
                if ((currentParent == null && targetFrameId != null) ||
                        (currentParent != null && !currentParent.equals(targetFrameId))) {
                    nodesToChange.add(node.getNodeData().id);
                }
            }

            // 3. 执行父级变更命令
            if (!nodesToChange.isEmpty()) {
                CmdChangeParent cmdParent = new CmdChangeParent(mContext.getEditorContext().getGraphController(), nodesToChange, true, targetFrameId);
                mContext.getEditorContext().getCommandManager().execute(cmdParent);
            }

        } else {
            // (原有的原路弹回逻辑保持不变)
            for (UINode node : mContext.getSelectedNodes()) {
                node.setTranslationX(node.getNodeData().getX());
                node.setTranslationY(node.getNodeData().getY());
                if (mContext instanceof Viewport) {
                    ((Viewport) mContext).updateConnectionsForNode(node.getNodeData().id);
                }
            }
            mContext.invalidate();
        }
    }

    /**
     * 图框拖拽结束结算：实现大框嵌套自动吸附
     */
    private void finalizeFrameDragging(float endUiX, float endUiY) {
        if (mDraggedFrame == null) return;

        float totalUiDx = endUiX - mDragStartUiX;
        float totalUiDy = endUiY - mDragStartUiY;

        if (Math.abs(totalUiDx) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE ||
                Math.abs(totalUiDy) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE) {

            String draggedFrameId = mDraggedFrame.getFrameData().id;

            // 1. 提交数据层移动命令（使所有下属元素坐标正式同步，计入历史栈）
            CmdMoveElements cmdMove = new CmdMoveElements(
                    mContext.getEditorContext().getGraphController(),
                    new ArrayList<>(),
                    List.of(draggedFrameId),
                    totalUiDx,
                    totalUiDy
            );
            mContext.getEditorContext().getCommandManager().execute(cmdMove);

            // 2. 自动并入更大的图框判定 (Auto-Parenting for Frame)
            UIFrame largerFrame = getSmallestContainingFrameForFrame(draggedFrameId, endUiX, endUiY);
            String newParentId = (largerFrame != null) ? largerFrame.getFrameData().id : null;

            String currentParent = mDraggedFrame.getFrameData().parentFrame;
            if ((currentParent == null && newParentId != null) ||
                    (currentParent != null && !currentParent.equals(newParentId))) {

                // 3. 提交父级变更命令 (对图框做变更，isNode 传入 false)
                CmdChangeParent cmdParent = new CmdChangeParent(
                        mContext.getEditorContext().getGraphController(),
                        List.of(draggedFrameId),
                        false,
                        newParentId
                );
                mContext.getEditorContext().getCommandManager().execute(cmdParent);
            }
        } else {
            // 没达到拖拽阈值，按原始坐标弹回刷新
            if (mContext instanceof Viewport) {
                ((Viewport) mContext).updateFrameBounds(mDraggedFrame.getFrameData().id);
            }
        }

        mDraggedFrame = null;
    }

    /**
     * 辅助方法：寻找最适合嵌套当前图框的外部大图框（排除自身及子图框）
     */
    private UIFrame getSmallestContainingFrameForFrame(String draggedFrameId, float uiX, float uiY) {
        UIFrame target = null;
        float minArea = Float.MAX_VALUE;

        if (mContext instanceof Viewport vp) {
            GraphController controller = vp.getEditorContext().getGraphController();
            for (UIFrame frame : vp.getFrameViews().values()) {
                String fid = frame.getFrameData().id;

                // 排除自身，且排除会导致循环引用的子孙图框
                if (fid.equals(draggedFrameId) || isCyclicFrameReference(controller, draggedFrameId, fid)) {
                    continue;
                }

                float x = frame.getFrameData().uiPos[0];
                float y = frame.getFrameData().uiPos[1];
                float w = frame.getFrameData().uiSize[0];
                float h = frame.getFrameData().uiSize[1];

                // 判定放手时的鼠标坐标是否在外部大框内
                if (uiX >= x && uiX <= x + w && uiY >= y && uiY <= y + h) {
                    float area = w * h;
                    if (area < minArea) {
                        minArea = area;
                        target = frame; // 锁定制导深度最深、面积最小的直接外壳
                    }
                }
            }
        }
        return target;
    }

    /**
     * 核心安全校验：防止嵌套引发拓扑环
     */
    private boolean isCyclicFrameReference(GraphController controller, String draggedFrameId, String potentialParentId) {
        if (potentialParentId == null) return false;
        if (draggedFrameId.equals(potentialParentId)) return true;

        // 沿着潜在父框的亲属链一路上溯，如果在其祖先里发现了当前拖拽框的ID，说明在开历史倒车，会形成死循环
        com.mine.geometry_node.core.node.FrameData current = controller.getContext().getGraph().getFrame(potentialParentId);
        while (current != null) {
            if (draggedFrameId.equals(current.parentFrame)) {
                return true;
            }
            current = controller.getContext().getGraph().getFrame(current.parentFrame);
        }
        return false;
    }

    private void finalizeConnection(float endUiX, float endUiY) {
        Viewport.PortInfo endPort = mContext.findPortAt(endUiX, endUiY);
        if (isValidConnection(mDraftStartPort, endPort)) {
            Viewport.PortInfo input = mDraftStartPort.isInput ? mDraftStartPort : endPort;
            Viewport.PortInfo output = mDraftStartPort.isInput ? endPort : mDraftStartPort;
            if (!mContext.hasConnection(output.node, output.portId, input.node, input.portId)) {
                CmdConnect cmd = new CmdConnect(mContext.getEditorContext().getGraphController(), mContext.getEditorContext().getGraph(), output.node.getNodeData().id, output.portId, input.node.getNodeData().id, input.portId);
                mContext.getEditorContext().getCommandManager().execute(cmd);
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
    }

    private void drawDraftLine(Canvas canvas) {
        ViewportCamera camera = mContext.getCamera();
        float currentScale = camera.getScale();
        float scaledLineWidth = UIUtils.dp2px(UIConstants.ViewPort.Connection.LINE_WIDTH_DRAFT) * currentScale;
        mDraftLinePaint.setStrokeWidth(scaledLineWidth);

        mDraftStartPort.node.getPortPosition(mDraftStartPort.portId, mDraftStartPort.isInput, mTempPos);
        float startUiX = mDraftStartPort.node.getTranslationX() + mTempPos[0];
        float startUiY = mDraftStartPort.node.getTranslationY() + mTempPos[1];

        float startScreenX = camera.uiToScreenX(startUiX);
        float startScreenY = camera.uiToScreenY(startUiY);
        float endScreenX = camera.uiToScreenX(mDraftCurrentUiX);
        float endScreenY = camera.uiToScreenY(mDraftCurrentUiY);

        canvas.drawLine(startScreenX, startScreenY, endScreenX, endScreenY, mDraftLinePaint);
    }

    private boolean isValidConnection(Viewport.PortInfo s, Viewport.PortInfo e) {
        if (s == null || e == null || s.node == e.node || s.isInput == e.isInput) return false;
        Viewport.PortInfo outPortInfo = s.isInput ? e : s;
        Viewport.PortInfo inPortInfo   = s.isInput ? s : e;
        return PortType.isCompatible(getPortType(outPortInfo), getPortType(inPortInfo));
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
// --- END OF FILE InteractionManager.java ---