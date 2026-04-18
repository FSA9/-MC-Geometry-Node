package com.mine.geometry_node.client.ui.Viewport.Interaction;

import com.mine.geometry_node.client.ui.UICommand.commands.*;
import com.mine.geometry_node.client.ui.UIConstants;
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

/**
 * 交互管理器 (画布交互状态机)
 * <p>
 * 核心架构原则：
 * 1. 边界防御：只在入口处接收屏幕物理坐标 (Screen Coords)。
 * 2. 立即降维：接收后立即转换为 UI 逻辑坐标 (UI Coords = Screen / Scale / Density)。
 * 3. 内部纯粹：所有判定、移动、框选逻辑均在 UI 逻辑坐标系下进行，与物理屏幕密度解耦。
 */
public class InteractionManager {

    // ==========================================
    // 状态机枚举定义
    // ==========================================
    private static final int MODE_NONE           = 0; // 空闲状态
    private static final int MODE_PANNING        = 1; // 画布平移 (操作 Viewport 物理偏移)
    private static final int MODE_DRAGGING_NODES = 2; // 节点拖拽 (操作 UINode 的 UI 逻辑坐标)
    private static final int MODE_SELECTING      = 3; // 框选模式 (操作 UI 逻辑坐标构建矩形)
    private static final int MODE_CONNECTING     = 4; // 连线模式 (操作 UI 逻辑坐标绘制草稿)

    // ==========================================
    // 核心依赖与状态数据
    // ==========================================
    private final InteractionContext mContext;
    private int mCurrentMode = MODE_NONE;

    // --- 触控基础状态 ---
    private float mLastScreenX, mLastScreenY;
    private boolean mHasMovedSignificantly = false;

    // --- 节点拖拽状态 (UI 坐标系) ---
    private float mDragStartUiX, mDragStartUiY;

    // --- 框选状态 (UI 坐标系) ---
    private float mSelectionStartUiX, mSelectionStartUiY;
    private final RectF mSelectionRectUi = new RectF();

    // --- 连线草稿状态 (UI 坐标系) ---
    private Viewport.PortInfo mDraftStartPort = null;
    private float mDraftCurrentUiX, mDraftCurrentUiY;

    // ==========================================
    // 渲染资源与复用对象
    // ==========================================
    private final Paint mSelectionFillPaint = new Paint();
    private final Paint mSelectionBorderPaint = new Paint();
    private final Paint mDraftLinePaint = new Paint();

    /** 临时变量，避免查询端口坐标时频繁触发 GC */
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
        mSelectionBorderPaint.setStrokeWidth(UIConstants.ViewPort.Selection.STROKE_WIDTH);

        mDraftLinePaint.setAntiAlias(true);
        mDraftLinePaint.setStyle(Paint.Style.STROKE);
        mDraftLinePaint.setStrokeWidth(UIConstants.ViewPort.Connection.LINE_WIDTH_DRAFT);
        mDraftLinePaint.setColor(UIConstants.ViewPort.Connection.CLR_DRAFT_LINE);
    }

    // ==========================================
    // 1. 事件分发入口
    // ==========================================

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
            case MotionEvent.ACTION_DOWN:
                handleActionDown(event, x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleActionMove(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                handleActionUp(event, x, y);
                return true;
            default:
                return false;
        }
    }

    // ==========================================
    // 2. 触控阶段处理 (Touch Phases)
    // ==========================================

    private void handleActionDown(MotionEvent event, float screenX, float screenY) {
        mLastScreenX = screenX;
        mLastScreenY = screenY;
        mHasMovedSignificantly = false;

        // 【关键防御】入口处立即降维至 UI 逻辑坐标
        float uiX = mContext.screenToUIX(screenX);
        float uiY = mContext.screenToUIY(screenY);

        if (isMiddleMouse(event)) {
            mCurrentMode = MODE_PANNING;
            return;
        }

        if (isRightMouse(event)) {
            // 右键交互通常在 UP 时触发菜单，DOWN 阶段暂不处理
            return;
        }

        // --- 以下为左键交互判定 (全基于 UI 坐标) ---

        // 提前获取目标节点，因为后续高优先级拦截都需要基于它
        UINode target = mContext.findNodeAt(uiX, uiY);

        // ================= 优先级 1：先拦截节点上的动态按钮 (+/-) 点击 =================
        if (target != null) {
            float localX = uiX - target.getTranslationX();
            float localY = uiY - target.getTranslationY();
            UINode.DynamicActionInfo btnInfo = target.hitTestDynamicButton(localX, localY);

            if (btnInfo != null) {
                NodeData nodeData = target.getNodeData();
                NodeDef nodeDef = target.getNodeDef();

                boolean isInputDynamic = nodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();

                String propertyKey = isInputDynamic ?
                        PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id() :
                        PropertyKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id();

                int maxCount = isInputDynamic ?
                        nodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_INPUT, 10) :
                        nodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_OUTPUT, 10);

                int currentCount = 1;
                if (nodeData.properties.containsKey(propertyKey)) {
                    Object countObj = nodeData.properties.get(propertyKey);
                    if (countObj instanceof Number num) {
                        currentCount = num.intValue();
                    } else if (countObj instanceof String str) {
                        try { currentCount = Integer.parseInt(str); } catch (Exception ignored) {}
                    }
                }

                if (btnInfo.isAdd()) {
                    if (currentCount < maxCount) {
                        com.mine.geometry_node.client.ui.UICommand.commands.CmdAddBranch cmd =
                                new com.mine.geometry_node.client.ui.UICommand.commands.CmdAddBranch(
                                        mContext.getEditorContext().getGraphController(),
                                        nodeData.id, propertyKey, currentCount);
                        mContext.getEditorContext().getCommandManager().execute(cmd);
                    }
                } else {
                    if (currentCount > 1) {
                        String refId = btnInfo.referencePortId();

                        CmdRemoveBranch cmd =
                                new CmdRemoveBranch(
                                        mContext.getEditorContext().getGraphController(),
                                        mContext.getEditorContext().getGraph(),
                                        nodeData.id, propertyKey, currentCount, refId); // <-- 把 refId 传进命令里
                        mContext.getEditorContext().getCommandManager().execute(cmd);
                    }
                }

                return; // 成功消费按钮事件，阻止进入后续连线模式
            }
        }

        // ================= 优先级 2：点击端口 -> 进入连线模式 =================
        Viewport.PortInfo port = mContext.findPortAt(uiX, uiY);
        if (port != null) {
            enterConnectingMode(port, uiX, uiY);
            return;
        }

        // ================= 优先级 3：点击节点主体 -> 进入拖拽模式 =================
        if (target != null) {
            enterDraggingMode(target, uiX, uiY);
            return;
        }

        // ================= 优先级 4：点击空白处 -> 进入框选模式 =================
        enterSelectingMode(uiX, uiY);
    }

    private void handleActionMove(float screenX, float screenY) {
        // 1. 判断是否产生有效拖拽 (滤除点击手抖)
        float dx = screenX - mLastScreenX;
        float dy = screenY - mLastScreenY;
        if (Math.abs(dx) > UIConstants.ViewPort.Interaction.TOUCH_SLOP ||
                Math.abs(dy) > UIConstants.ViewPort.Interaction.TOUCH_SLOP) {
            mHasMovedSignificantly = true;
        }

        // 2. 计算 UI 逻辑位移增量
        float uiX = mContext.screenToUIX(screenX);
        float uiY = mContext.screenToUIY(screenY);
        float lastUiX = mContext.screenToUIX(mLastScreenX);
        float lastUiY = mContext.screenToUIY(mLastScreenY);

        float uiDx = uiX - lastUiX;
        float uiDy = uiY - lastUiY;

        // 3. 根据当前状态机分发更新操作
        switch (mCurrentMode) {
            case MODE_PANNING:
                updateViewportPan(dx, dy);
                break;
            case MODE_DRAGGING_NODES:
                updateNodeDragging(uiDx, uiDy);
                break;
            case MODE_SELECTING:
                updateBoxSelection(uiX, uiY);
                break;
            case MODE_CONNECTING:
                updateDraftLine(uiX, uiY);
                break;
        }

        // 4. 保存当前帧坐标
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
            case MODE_DRAGGING_NODES:
                finalizeNodeDragging(uiX, uiY);
                break;
            case MODE_CONNECTING:
                finalizeConnection(uiX, uiY);
                break;
            case MODE_SELECTING:
                mSelectionRectUi.setEmpty();
                break;
        }

        mCurrentMode = MODE_NONE;
        mContext.invalidate();
    }

    // ==========================================
    // 3. 状态进入逻辑 (Enter Modes)
    // ==========================================

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

    // ==========================================
    // 4. 状态更新逻辑 (Update Modes)
    // ==========================================

    private void updateViewportPan(float screenDx, float screenDy) {
        mContext.setViewportX(mContext.getViewportX() + screenDx);
        mContext.setViewportY(mContext.getViewportY() + screenDy);
        mContext.updateTransform();
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

    // ==========================================
    // 5. 状态结算逻辑 (Finalize Modes)
    // ==========================================

    private void finalizeNodeDragging(float endUiX, float endUiY) {
        if (!mHasMovedSignificantly) return;

        float totalUiDx = endUiX - mDragStartUiX;
        float totalUiDy = endUiY - mDragStartUiY;

        // 容差过滤，防止微小浮点误差生成无用 Command
        if (Math.abs(totalUiDx) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE ||
                Math.abs(totalUiDy) > UIConstants.ViewPort.Interaction.MIN_DRAG_DISTANCE) {

            List<String> selectedIds = new ArrayList<>();
            for (UINode node : mContext.getSelectedNodes()) {
                selectedIds.add(node.getNodeData().id);
            }

            CmdMoveNode cmd = new CmdMoveNode(
                    mContext.getEditorContext().getGraphController(), selectedIds, totalUiDx, totalUiDy);

            mContext.getEditorContext().getCommandManager().execute(cmd);
        }
    }

    private void finalizeConnection(float endUiX, float endUiY) {
        Viewport.PortInfo endPort = mContext.findPortAt(endUiX, endUiY);

        if (isValidConnection(mDraftStartPort, endPort)) {
            Viewport.PortInfo input = mDraftStartPort.isInput ? mDraftStartPort : endPort;
            Viewport.PortInfo output = mDraftStartPort.isInput ? endPort : mDraftStartPort;

            if (!mContext.hasConnection(output.node, output.portId, input.node, input.portId)) {
                String outNodeId = output.node.getNodeData().id;
                String inNodeId = input.node.getNodeData().id;

                CmdConnect cmd = new CmdConnect(
                        mContext.getEditorContext().getGraphController(),
                        mContext.getEditorContext().getGraph(),
                        outNodeId, output.portId, inNodeId, input.portId);
                mContext.getEditorContext().getCommandManager().execute(cmd);
            }
        }
        mDraftStartPort = null;
    }

    // ==========================================
    // 6. 顶层叠加渲染 (Overlay)
    // ==========================================

    public void drawOverlay(Canvas canvas) {
        if (mCurrentMode == MODE_CONNECTING && mDraftStartPort != null) {
            drawDraftLine(canvas);
        }

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
        float scaledLineWidth = UIConstants.ViewPort.Connection.LINE_WIDTH_DRAFT * currentScale;
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

    // ==========================================
    // 7. 辅助判断工具
    // ==========================================

    private boolean isValidConnection(Viewport.PortInfo s, Viewport.PortInfo e) {
        if (s == null || e == null || s.node == e.node || s.isInput == e.isInput) {
            return false;
        }

        Viewport.PortInfo outPortInfo = s.isInput ? e : s;
        Viewport.PortInfo inPortInfo   = s.isInput ? s : e;

        PortType outType = getPortType(outPortInfo);
        PortType inType  = getPortType(inPortInfo);

        return PortType.isCompatible(outType, inType);
    }

    private PortType getPortType(Viewport.PortInfo portInfo) {
        if (portInfo == null || portInfo.node == null) return null;

        for (PortRow row : portInfo.node.getNodeDef().rows()) {
            if (portInfo.isInput) {
                if (row.leftPort() != null && row.leftPort().id().equals(portInfo.portId)) {
                    return row.leftPort().type();
                }
            } else {
                if (row.rightPort() != null && row.rightPort().id().equals(portInfo.portId)) {
                    return row.rightPort().type();
                }
            }
        }
        return null;
    }

    private boolean isRightMouse(MotionEvent e) {
        return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0 ||
                e.getActionButton() == MotionEvent.BUTTON_SECONDARY;
    }

    private boolean isMiddleMouse(MotionEvent e) {
        return (e.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0 ||
                e.getActionButton() == MotionEvent.BUTTON_TERTIARY;
    }
}