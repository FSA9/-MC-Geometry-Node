package com.mine.geometry_node.client.ui.Viewport.Interaction;

import com.mine.geometry_node.client.ui.UICommand.commands.*;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils; // 引入工具类
import com.mine.geometry_node.client.ui.Viewport.UINode;
import com.mine.geometry_node.client.ui.Viewport.Viewport;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.*;
import com.mine.geometry_node.core.node.nodes.*;
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

    private final InteractionContext mContext;
    private int mCurrentMode = MODE_NONE;

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
        // 线宽转换为物理像素
        mSelectionBorderPaint.setStrokeWidth(UIUtils.dp2px(UIConstants.ViewPort.Selection.STROKE_WIDTH));

        mDraftLinePaint.setAntiAlias(true);
        mDraftLinePaint.setStyle(Paint.Style.STROKE);
        mDraftLinePaint.setColor(UIConstants.ViewPort.Connection.CLR_DRAFT_LINE);
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            mContext.performZoom(scrollY > 0, event.getX(), event.getY());
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) {
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
        mHasMovedSignificantly = false;

        float uiX = mContext.screenToUIX(screenX);
        float uiY = mContext.screenToUIY(screenY);

        if (isMiddleMouse(event)) { mCurrentMode = MODE_PANNING; return; }
        if (isRightMouse(event)) return;

        UINode target = mContext.findNodeAt(uiX, uiY);

        if (target != null) {
            float localX = uiX - target.getTranslationX();
            float localY = uiY - target.getTranslationY();
            UINode.DynamicActionInfo btnInfo = target.hitTestDynamicButton(localX, localY);

            if (btnInfo != null) {
                NodeData nodeData = target.getNodeData();
                NodeDef nodeDef = target.getNodeDef();

                boolean isInputDynamic = nodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
                String propertyKey = isInputDynamic ? PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id() : PropertyKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id();
                int maxCount = isInputDynamic ? nodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_INPUT, 10) : nodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_OUTPUT, 10);

                int currentCount = 1;
                if (nodeData.properties.containsKey(propertyKey)) {
                    Object countObj = nodeData.properties.get(propertyKey);
                    if (countObj instanceof Number num) currentCount = num.intValue();
                    else if (countObj instanceof String str) { try { currentCount = Integer.parseInt(str); } catch (Exception ignored) {} }
                }

                if (btnInfo.isAdd()) {
                    if (currentCount < maxCount) {
                        CmdAddBranch cmd = new CmdAddBranch(mContext.getEditorContext().getGraphController(), nodeData.id, propertyKey, currentCount);
                        mContext.getEditorContext().getCommandManager().execute(cmd);
                    }
                } else {
                    if (currentCount > 1) {
                        CmdRemoveBranch cmd = new CmdRemoveBranch(mContext.getEditorContext().getGraphController(), mContext.getEditorContext().getGraph(), nodeData.id, propertyKey, currentCount, btnInfo.referencePortId());
                        mContext.getEditorContext().getCommandManager().execute(cmd);
                    }
                }
                return;
            }
        }

        Viewport.PortInfo port = mContext.findPortAt(uiX, uiY);
        if (port != null) { enterConnectingMode(port, uiX, uiY); return; }

        if (target != null) { enterDraggingMode(target, uiX, uiY); return; }

        enterSelectingMode(uiX, uiY);
    }

    private void handleActionMove(float screenX, float screenY) {
        float dx = screenX - mLastScreenX;
        float dy = screenY - mLastScreenY;

        // 防抖判定：转换 TOUCH_SLOP 为物理像素
        float touchSlopPx = UIUtils.dp2px(UIConstants.ViewPort.Interaction.TOUCH_SLOP);
        if (Math.abs(dx) > touchSlopPx || Math.abs(dy) > touchSlopPx) {
            mHasMovedSignificantly = true;
        }

        float uiX = mContext.screenToUIX(screenX);
        float uiY = mContext.screenToUIY(screenY);
        float lastUiX = mContext.screenToUIX(mLastScreenX);
        float lastUiY = mContext.screenToUIY(mLastScreenY);

        float uiDx = uiX - lastUiX;
        float uiDy = uiY - lastUiY;

        switch (mCurrentMode) {
            case MODE_PANNING: updateViewportPan(dx, dy); break;
            case MODE_DRAGGING_NODES: updateNodeDragging(uiDx, uiDy); break;
            case MODE_SELECTING: updateBoxSelection(uiX, uiY); break;
            case MODE_CONNECTING: updateDraftLine(uiX, uiY); break;
        }

        mLastScreenX = screenX;
        mLastScreenY = screenY;
    }

    private void handleActionUp(MotionEvent event, float screenX, float screenY) {
        float uiX = mContext.screenToUIX(screenX);
        float uiY = mContext.screenToUIY(screenY);

        if (isRightMouse(event) && !mHasMovedSignificantly) {
            mContext.showMenu(screenX, screenY);
        }

        switch (mCurrentMode) {
            case MODE_DRAGGING_NODES: finalizeNodeDragging(uiX, uiY); break;
            case MODE_CONNECTING: finalizeConnection(uiX, uiY); break;
            case MODE_SELECTING: mSelectionRectUi.setEmpty(); break;
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

    private void updateViewportPan(float screenDx, float screenDy) {
        mContext.setViewportX(mContext.getViewportX() + screenDx);
        mContext.setViewportY(mContext.getViewportY() + screenDy);
        mContext.updateTransform();
    }

    private void updateNodeDragging(float uiDx, float uiDy) { mContext.moveSelectedNodes(uiDx, uiDy); }

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
        if (!mHasMovedSignificantly) return;
        float totalUiDx = endUiX - mDragStartUiX;
        float totalUiDy = endUiY - mDragStartUiY;

        if (Math.abs(totalUiDx) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE || Math.abs(totalUiDy) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE) {
            List<String> selectedIds = new ArrayList<>();
            for (UINode node : mContext.getSelectedNodes()) selectedIds.add(node.getNodeData().id);
            CmdMoveNode cmd = new CmdMoveNode(mContext.getEditorContext().getGraphController(), selectedIds, totalUiDx, totalUiDy);
            mContext.getEditorContext().getCommandManager().execute(cmd);
        }
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
            float l = mContext.uiToScreenX(mSelectionRectUi.left);
            float t = mContext.uiToScreenY(mSelectionRectUi.top);
            float r = mContext.uiToScreenX(mSelectionRectUi.right);
            float b = mContext.uiToScreenY(mSelectionRectUi.bottom);
            canvas.drawRect(l, t, r, b, mSelectionFillPaint);
            canvas.drawRect(l, t, r, b, mSelectionBorderPaint);
        }
    }

    private void drawDraftLine(Canvas canvas) {
        float currentScale = mContext.getCurrentScale();
        // 线宽转换为物理像素
        float scaledLineWidth = UIUtils.dp2px(UIConstants.ViewPort.Connection.LINE_WIDTH_DRAFT) * currentScale;
        mDraftLinePaint.setStrokeWidth(scaledLineWidth);

        mDraftStartPort.node.getPortPosition(mDraftStartPort.portId, mDraftStartPort.isInput, mTempPos);
        float startUiX = mDraftStartPort.node.getTranslationX() + mTempPos[0];
        float startUiY = mDraftStartPort.node.getTranslationY() + mTempPos[1];

        float startScreenX = mContext.uiToScreenX(startUiX);
        float startScreenY = mContext.uiToScreenY(startUiY);
        float endScreenX = mContext.uiToScreenX(mDraftCurrentUiX);
        float endScreenY = mContext.uiToScreenY(mDraftCurrentUiY);

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