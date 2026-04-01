package com.mine.geometry_node.client.ui.Viewport.Interaction;

import com.mine.geometry_node.client.ui.UICommand.commands.CmdConnect;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdMoveNode;
import com.mine.geometry_node.client.ui.Viewport.UINode;
import com.mine.geometry_node.client.ui.Viewport.Viewport;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PropertyKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
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
 * 3. 内部纯粹：所有判定、移动、框选逻辑均在 UI 坐标系下进行，与屏幕物理密度、当前缩放率彻底解耦。
 * </p>
 *
 */
public class InteractionManager {


    // 交互模式常量 (状态机枚举)


    private static final int MODE_NONE           = 0; // 空闲状态
    private static final int MODE_PANNING        = 1; // 画布平移 (操作 Viewport 物理偏移)
    private static final int MODE_DRAGGING_NODES = 2; // 节点拖拽 (操作 UINode 的 UI 逻辑坐标)
    private static final int MODE_SELECTING      = 3; // 框选模式 (操作 UI 逻辑坐标构建矩形)
    private static final int MODE_CONNECTING     = 4; // 连线模式 (操作 UI 逻辑坐标绘制草稿)

    /** 移动判定阈值 (物理像素)，超过此值才判定为拖拽而非点击 */
    private static final float TOUCH_SLOP = 5.0f;


    // 核心依赖与状态数据


    private final InteractionContext mContext;
    private int mCurrentMode = MODE_NONE;

    // --- 触控基础状态 ---
    /** 上一次触摸点的屏幕物理坐标 (用于计算增量) */
    private float mLastScreenX, mLastScreenY;
    /** 是否发生了显著移动 (用于区分单纯的 Click 和 Drag) */
    private boolean mHasMovedSignificantly = false;

    // --- 节点拖拽状态 (UI 坐标系) ---
    /** 拖拽起始点的 UI 逻辑坐标 (用于计算总偏移量，提交给 Command) */
    private float mDragStartUiX, mDragStartUiY;

    // --- 框选状态 (UI 坐标系) ---
    /** 框选起始点的 UI 逻辑坐标 */
    private float mSelectionStartUiX, mSelectionStartUiY;
    /** 存储 UI 坐标的框选矩形 */
    private final RectF mSelectionRectUi = new RectF();

    // --- 连线草稿状态 (UI 坐标系) ---
    /** 连线起始端口信息 */
    private Viewport.PortInfo mDraftStartPort = null;
    /** 连线终点的当前游标 UI 逻辑坐标 */
    private float mDraftCurrentUiX, mDraftCurrentUiY;


    // 渲染资源与复用对象


    private final Paint mSelectionFillPaint = new Paint();
    private final Paint mSelectionBorderPaint = new Paint();
    private final Paint mDraftLinePaint = new Paint();

    /** 临时变量，用于查询端口坐标，避免渲染与交互中产生 GC */
    private final float[] mTempPos = new float[2];

    public InteractionManager(InteractionContext context) {
        this.mContext = context;
        initPaints();
    }

    private void initPaints() {
        mSelectionFillPaint.setColor(0x3344AAFF);
        mSelectionFillPaint.setStyle(Paint.Style.FILL);

        mSelectionBorderPaint.setColor(0xFF44AAFF);
        mSelectionBorderPaint.setStyle(Paint.Style.STROKE);
        mSelectionBorderPaint.setStrokeWidth(1.0f);

        mDraftLinePaint.setAntiAlias(true);
        mDraftLinePaint.setStyle(Paint.Style.STROKE);
        mDraftLinePaint.setStrokeWidth(3.0f);
        mDraftLinePaint.setColor(0xFFE0E0E0);
    }


    // 1. 事件分发入口


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


    // 2. 触控阶段处理 (Touch Phases)


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

        // 优先级 1：点击端口 -> 进入连线模式
        Viewport.PortInfo port = mContext.findPortAt(uiX, uiY);
        if (port != null) {
            enterConnectingMode(port, uiX, uiY);
            return;
        }

        // 优先级 2：点击节点内部元素或节点本身
        UINode target = mContext.findNodeAt(uiX, uiY);
        if (target != null) {
            // 2.1 拦截动态按钮 (+/-) 点击
            float localX = uiX - target.getTranslationX();
            float localY = uiY - target.getTranslationY();
            UINode.DynamicActionInfo btnInfo = target.hitTestDynamicButton(localX, localY);
            if (btnInfo != null) {
                NodeData nodeData = target.getNodeData();
                NodeDef nodeDef = target.getNodeDef();

                // 2. 读取当前分支数量
                int currentCount = 1;
                String propertyKey = PropertyKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id();
                if (nodeData.properties.containsKey(propertyKey)) {
                    Object countObj = nodeData.properties.get(propertyKey);
                    if (countObj instanceof Number num) {
                        currentCount = num.intValue();
                    } else if (countObj instanceof String str) {
                        try { currentCount = Integer.parseInt(str); } catch (Exception ignored) {}
                    }
                }

                // 3. 读取最大限制
                int maxCount = nodeDef.getMetaOrDefault(SchemaKeys.MAX_DYNAMIC_OUTPUT, 10);

                // 4. 根据点击类型执行对应的 Command
                if (btnInfo.isAdd()) {
                    if (currentCount < maxCount) {
                        com.mine.geometry_node.client.ui.UICommand.commands.CmdAddBranch cmd =
                                new com.mine.geometry_node.client.ui.UICommand.commands.CmdAddBranch(
                                        mContext.getEditorContext().getGraphController(),
                                        nodeData.id, propertyKey, currentCount);
                        mContext.getEditorContext().getCommandManager().execute(cmd);
                    } else {
                        System.out.println("[Interaction] 动态端口数量已达上限: " + maxCount);
                    }
                } else {
                    if (currentCount > 1) {
                        com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveBranch cmd =
                                new com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveBranch(
                                        mContext.getEditorContext().getGraphController(),
                                        mContext.getEditorContext().getGraph(),
                                        nodeData.id, propertyKey, currentCount);
                        mContext.getEditorContext().getCommandManager().execute(cmd);
                    } else {
                        System.out.println("[Interaction] 无法删除，必须保留至少一个分支");
                    }
                }

                return; // 消费事件，阻止进入拖拽模式
            }

            // 2.2 进入拖拽模式
            enterDraggingMode(target, uiX, uiY);
            return;
        }

        // 优先级 3：点击空白处 -> 进入框选模式
        enterSelectingMode(uiX, uiY);
    }

    private void handleActionMove(float screenX, float screenY) {
        // 1. 判断是否产生有效拖拽 (滤除点击手抖)
        float dx = screenX - mLastScreenX;
        float dy = screenY - mLastScreenY;
        if (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP) {
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
                updateViewportPan(dx, dy); // 平移视口操作物理像素
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

        // 处理右键菜单 (仅在没有拖拽的情况下触发)
        if (isRightMouse(event) && !mHasMovedSignificantly) {
            mContext.showMenu(screenX, screenY); // 菜单基于屏幕生成
        }

        // 根据当前状态机结算行为
        switch (mCurrentMode) {
            case MODE_DRAGGING_NODES:
                finalizeNodeDragging(uiX, uiY);
                break;
            case MODE_CONNECTING:
                finalizeConnection(uiX, uiY);
                break;
            case MODE_SELECTING:
                mSelectionRectUi.setEmpty(); // 清理框选框
                break;
        }

        // 重置状态机
        mCurrentMode = MODE_NONE;
        mContext.invalidate();
    }


    // 3. 状态进入逻辑 (Enter Modes)


    private void enterConnectingMode(Viewport.PortInfo port, float uiX, float uiY) {
        mCurrentMode = MODE_CONNECTING;
        mDraftStartPort = port;
        mDraftCurrentUiX = uiX;
        mDraftCurrentUiY = uiY;
    }

    private void enterDraggingMode(UINode target, float uiX, float uiY) {
        mCurrentMode = MODE_DRAGGING_NODES;
        // 记录起始点，用于 Up 时计算总位移提交命令
        mDragStartUiX = uiX;
        mDragStartUiY = uiY;

        // 选中逻辑：如果点击的节点未选中，则单选它；如果已选中，则保持多选状态以便群体拖拽
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


    // 4. 状态更新逻辑 (Update Modes)


    private void updateViewportPan(float screenDx, float screenDy) {
        mContext.setViewportX(mContext.getViewportX() + screenDx);
        mContext.setViewportY(mContext.getViewportY() + screenDy);
        mContext.updateTransform();
    }

    private void updateNodeDragging(float uiDx, float uiDy) {
        // UI 增量直接加给节点的 TranslationX/Y
        mContext.moveSelectedNodes(uiDx, uiDy);
    }

    private void updateBoxSelection(float currentUiX, float currentUiY) {
        // 1. 标准化左上角与宽高 (支持反向拉框)
        float x = Math.min(mSelectionStartUiX, currentUiX);
        float y = Math.min(mSelectionStartUiY, currentUiY);
        float w = Math.abs(currentUiX - mSelectionStartUiX);
        float h = Math.abs(currentUiY - mSelectionStartUiY);

        // 2. 更新逻辑矩形
        mSelectionRectUi.set(x, y, x + w, y + h);

        // 3. 执行 UI 坐标系下的碰撞检测
        mContext.updateBoxSelection(x, y, w, h);
        mContext.invalidate();
    }

    private void updateDraftLine(float uiX, float uiY) {
        mDraftCurrentUiX = uiX;
        mDraftCurrentUiY = uiY;
        mContext.invalidate();
    }


    // 5. 状态结算逻辑 (Finalize Modes)


    /**
     * 结算节点拖拽：判断是否发生有效位移，并提交撤销/重做命令
     */
    private void finalizeNodeDragging(float endUiX, float endUiY) {
        if (!mHasMovedSignificantly) return;

        float totalUiDx = endUiX - mDragStartUiX;
        float totalUiDy = endUiY - mDragStartUiY;

        // 容差过滤，防止微小浮点误差生成无用 Command
        if (Math.abs(totalUiDx) > 0.1f || Math.abs(totalUiDy) > 0.1f) {
            List<String> selectedIds = new ArrayList<>();
            for (UINode node : mContext.getSelectedNodes()) {
                selectedIds.add(node.getNodeData().id);
            }

            // Command 接收逻辑坐标系增量
            CmdMoveNode cmd =
                    new CmdMoveNode(
                            mContext.getEditorContext().getGraphController(), selectedIds, totalUiDx, totalUiDy);

            mContext.getEditorContext().getCommandManager().execute(cmd);
        }
    }

    /**
     * 结算连线：验证目标端口并生成连线命令
     */
    private void finalizeConnection(float endUiX, float endUiY) {
        Viewport.PortInfo endPort = mContext.findPortAt(endUiX, endUiY);

        if (isValidConnection(mDraftStartPort, endPort)) {
            Viewport.PortInfo input = mDraftStartPort.isInput ? mDraftStartPort : endPort;
            Viewport.PortInfo output = mDraftStartPort.isInput ? endPort : mDraftStartPort;

            // 防止重复连线
            if (!mContext.hasConnection(output.node, output.portId, input.node, input.portId)) {
                String outNodeId = output.node.getNodeData().id;
                String inNodeId = input.node.getNodeData().id;

                CmdConnect cmd =
                        new CmdConnect(
                                mContext.getEditorContext().getGraphController(), outNodeId, output.portId, inNodeId, input.portId);
                mContext.getEditorContext().getCommandManager().execute(cmd);
            }
        }

        mDraftStartPort = null; // 清理状态
    }


    // 6. 顶层叠加渲染 (Overlay)


    public void drawOverlay(Canvas canvas) {
        // 绘制正在拖拽的连线
        if (mCurrentMode == MODE_CONNECTING && mDraftStartPort != null) {
            drawDraftLine(canvas);
        }

        // 绘制框选虚线框/半透明框
        if (mCurrentMode == MODE_SELECTING) {
            // 绘制在屏幕空间：需将逻辑坐标升维映射回屏幕物理坐标
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
        float scaledLineWidth = 3.0f * currentScale; // 3.0f 是你在 initPaints 中设置的基础宽度
        mDraftLinePaint.setStrokeWidth(scaledLineWidth);

        // 1. 获取起始端口的 UI 逻辑坐标
        mDraftStartPort.node.getPortPosition(mDraftStartPort.portId, mDraftStartPort.isInput, mTempPos);
        float startUiX = mDraftStartPort.node.getTranslationX() + mTempPos[0];
        float startUiY = mDraftStartPort.node.getTranslationY() + mTempPos[1];

        // 2. 将两端的 UI 逻辑坐标转换为屏幕物理坐标
        float startScreenX = mContext.uiToScreenX(startUiX);
        float startScreenY = mContext.uiToScreenY(startUiY);
        float endScreenX = mContext.uiToScreenX(mDraftCurrentUiX);
        float endScreenY = mContext.uiToScreenY(mDraftCurrentUiY);

        canvas.drawLine(startScreenX, startScreenY, endScreenX, endScreenY, mDraftLinePaint);
    }


    // 7. 辅助判断工具


    private boolean isValidConnection(Viewport.PortInfo s, Viewport.PortInfo e) {
        // 1. 基础物理校验：非空、不能连接自身节点、必须一进一出
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
                // 输入
                if (row.leftPort() != null && row.leftPort().id().equals(portInfo.portId)) {
                    return row.leftPort().type();
                }
            } else {
                // 输出
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