package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdAddNode;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.Viewport.Interaction.InteractionContext;
import com.mine.geometry_node.client.ui.Viewport.Interaction.InteractionManager;
import com.mine.geometry_node.client.ui.Viewport.Interaction.KeyManager;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.NodeDef;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 视口容器 (画布核心引擎)
 * <p>
 * 架构职责：
 * 1. 坐标系映射：屏幕物理坐标 (Screen Pixels) <-> UI 逻辑坐标 (Logical DP)。
 * 2. 节点承载：管理 UINode 生命周期与渲染层级。
 * 3. 视口变换：处理画布的平移 (Pan) 和缩放 (Zoom)。
 * 4. 事件中转：拦截并分发触摸/按键事件到底层交互组件或节点内的 UI 控件。
 */
public class Viewport extends FrameLayout implements InteractionContext, EditorContext.EditorListener {

    private static final int TOOL_TYPE_MOUSE = 1;

    // ==========================================
    // 1. 核心组件 (Core Components)
    // ==========================================
    private final FrameLayout mNodeLayer;                  // 节点真实的物理容器，承接缩放/平移
    private final InteractionManager mInteractionManager;  // 交互管理器：框选、拖拽、连线
    private final KeyManager mKeyManager;                  // 快捷键管理器
    private final EditorContext mEditorContext;            // 编辑器上下文：桥接数据与UI
    private ViewportMenu mActiveMenu;

    // ==========================================
    // 2. 视口状态 (Viewport State)
    // ==========================================
    private float mViewportX = 0;                          // 视口物理偏移 X 坐标 (Screen Pixels)
    private float mViewportY = 0;                          // 视口物理偏移 Y 坐标 (Screen Pixels)
    private float mCurrentScale = 1.0f;                    // 当前缩放比例 (1.0f 为基准)
    private boolean mFirstLayout = true;                   // 首次布局标记

    // ==========================================
    // 3. 数据与节点映射 (Data & Node Mapping)
    // ==========================================
    private final List<UINode> mSelectedNodes = new ArrayList<>();
    private final Map<String, UINode> mNodeViews = new HashMap<>(); // NodeID -> UINode
//    private final List<Connection> mConnections = new ArrayList<>();

    // ==========================================
    // 4. 事件与交互拦截缓存 (Event Cache)
    // ==========================================
    private View mCapturedHintView;                        // 当前捕获交互事件的内部控件 (如输入框)
    private boolean mHintCaptureUsesLogical;               // 该控件是否使用逻辑坐标判定

    // ==========================================
    // 5. 渲染与临时复用对象 (避免高频 GC)
    // ==========================================
    private final icyllis.modernui.graphics.RectF mTmpNodeBounds = new icyllis.modernui.graphics.RectF(); // 复用包围盒
    private final Paint mGridPaint = new Paint();
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mConnectionPaint = new Paint();

    private final int[] mTmpTargetLoc = new int[2];
    private final float[] mTmpEventScreen = new float[2];
    private final float[] mTempOutPos = new float[2];
    private final float[] mTempInPos  = new float[2];

    // ==========================================
    // 模块 1: 初始化与生命周期 (Initialization)
    // ==========================================

    public Viewport(Context context, EditorContext editorContext) {
        super(context);
        this.mEditorContext = editorContext;
        this.mEditorContext.addListener(this);

        // 初始化组件层级
        mNodeLayer = new FrameLayout(context);
        initViewportProps();
        initPaints();
        addView(mNodeLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 初始化管理器
        mInteractionManager = new InteractionManager(this);
        mKeyManager = new KeyManager(this);

        // 允许接收焦点与按键
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    private void initViewportProps() {
        setWillNotDraw(false);
        setClipChildren(false);

        mNodeLayer.setPivotX(0);
        mNodeLayer.setPivotY(0);
        mNodeLayer.setClipChildren(false);
    }

    private void initPaints() {
        mBackgroundPaint.setColor(UIConstants.ViewPort.BG_COLOR);

        mGridPaint.setAntiAlias(true);
        mGridPaint.setStyle(Paint.Style.STROKE);

        mConnectionPaint.setAntiAlias(true);
        mConnectionPaint.setStyle(Paint.Style.STROKE);
        mConnectionPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_CONNECTION);
        mConnectionPaint.setColor(0xFFE0E0E0);
    }

    // ==========================================
    // 模块 2: 数据驱动监听 (EditorContext.EditorListener)
    // ==========================================

    @Override
    public void onNodeAdded(NodeData nodeData) {
        NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(nodeData);
        if (def == null) return;

        UINode uiNode = new UINode(getContext(), nodeData, def, mEditorContext);
        uiNode.setTranslationX(nodeData.getX());
        uiNode.setTranslationY(nodeData.getY());

        mNodeLayer.addView(uiNode);
        mNodeViews.put(nodeData.id, uiNode);
    }

    @Override
    public void onNodeRemoved(String nodeId) {
        UINode uiNode = mNodeViews.remove(nodeId);
        if (uiNode != null) {
            mNodeLayer.removeView(uiNode);
            mSelectedNodes.remove(uiNode);
        }

        invalidate();
    }

    @Override
    public void onNodeStructureChanged(NodeData nodeData) {
        if (nodeData == null || nodeData.id == null) return;

        UINode old = mNodeViews.remove(nodeData.id);
        boolean wasSelected = old != null && mSelectedNodes.contains(old);
        if (old != null) {
            mNodeLayer.removeView(old);
            mSelectedNodes.remove(old);
        }

        onNodeAdded(nodeData);
        if (wasSelected) {
            UINode rebuilt = mNodeViews.get(nodeData.id);
            if (rebuilt != null) {
                mSelectedNodes.add(rebuilt);
                rebuilt.setSelected(true);
            }
        }

        invalidate();
    }

    @Override
    public void onGraphConnectionsRebuildRequested() {
        invalidate();
    }

    @Override
    public void onExecutionConnectionAdded(String outNodeId, String outPortId, String inNodeId) {
        invalidate();
    }

    @Override
    public void onExecutionConnectionRemoved(String outNodeId, String outPortId, String inNodeId) {
        invalidate();
    }

    @Override
    public void onSelectionChanged(List<String> selectedNodeIds) {
        for (UINode node : mNodeViews.values()) {
            node.setSelected(false);
        }
        mSelectedNodes.clear();

        for (String id : selectedNodeIds) {
            UINode uiNode = mNodeViews.get(id);
            if (uiNode != null) {
                uiNode.setSelected(true);
                mSelectedNodes.add(uiNode);
            }
        }
        invalidate();
    }

    @Override
    public void onNodeMoved(String nodeId, float x, float y) {
        UINode uiNode = mNodeViews.get(nodeId);
        if (uiNode != null) {
            uiNode.setTranslationX(x);
            uiNode.setTranslationY(y);
            invalidate();
        }
    }

    @Override
    public void onConnectionAdded(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        UINode outNode = mNodeViews.get(outNodeId);
        UINode inNode = mNodeViews.get(inNodeId);
        if (outNode != null) outNode.updateNodeLayout();
        if (inNode != null) inNode.updateNodeLayout();
        invalidate();
    }
    @Override
    public void onConnectionRemoved(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        UINode outNode = mNodeViews.get(outNodeId);
        UINode inNode = mNodeViews.get(inNodeId);
        if (outNode != null) outNode.updateNodeLayout();
        if (inNode != null) inNode.updateNodeLayout();
        invalidate();
    }

    // ==========================================
    // 模块 3: 视口变换与坐标系映射 (Transform & Coordinates)
    // ==========================================

    @Override
    public void updateTransform() {
        mNodeLayer.setTranslationX(mViewportX / UIConstants.mDensity);
        mNodeLayer.setTranslationY(mViewportY / UIConstants.mDensity);
        mNodeLayer.setScaleX(mCurrentScale);
        mNodeLayer.setScaleY(mCurrentScale);
        invalidate();
    }

    @Override
    public void performZoom(boolean zoomIn, float pivotScreenX, float pivotScreenY) {
        float oldScale = mCurrentScale;
        float factor = zoomIn ? UIConstants.ViewPort.ZOOM_SENSITIVITY : -UIConstants.ViewPort.ZOOM_SENSITIVITY;

        mCurrentScale = Math.max(UIConstants.ViewPort.ZOOM_MIN,
                Math.min(UIConstants.ViewPort.ZOOM_MAX, oldScale + factor));

        if (mCurrentScale == oldScale) return;

        float ratio = mCurrentScale / oldScale;
        mViewportX = pivotScreenX - (pivotScreenX - mViewportX) * ratio;
        mViewportY = pivotScreenY - (pivotScreenY - mViewportY) * ratio;

        updateTransform();
    }

    @Override
    public float screenToUIX(float screenX) {
        return ((screenX - mViewportX) / mCurrentScale) / UIConstants.mDensity;
    }

    @Override
    public float screenToUIY(float screenY) {
        return ((screenY - mViewportY) / mCurrentScale) / UIConstants.mDensity;
    }

    @Override
    public float uiToScreenX(float uiX) {
        return (uiX * UIConstants.mDensity) * mCurrentScale + mViewportX;
    }

    @Override
    public float uiToScreenY(float uiY) {
        return (uiY * UIConstants.mDensity) * mCurrentScale + mViewportY;
    }

    @Override public float getViewportX() { return mViewportX; }
    @Override public float getViewportY() { return mViewportY; }
    @Override public void setViewportX(float x) { mViewportX = x; }
    @Override public void setViewportY(float y) { mViewportY = y; }
    @Override public float getCurrentScale() { return mCurrentScale; }

    // ==========================================
    // 模块 4: 核心渲染逻辑 (Render Logic)
    // ==========================================

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mFirstLayout && w > 0 && h > 0) {
            mViewportX = w / 2f;
            mViewportY = h / 2f;
            updateTransform();
            mFirstLayout = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), mBackgroundPaint);
        drawInfiniteGrid(canvas);
        super.onDraw(canvas);
    }

    private void drawInfiniteGrid(Canvas canvas) {
        float scaledGrid = UIConstants.GRID_SIZE * mCurrentScale;
        if (scaledGrid < 5f) return; // 性能优化：网格过小时不绘制

        float w = getWidth();
        float h = getHeight();

        float startX = mViewportX % scaledGrid;
        if (startX > 0) startX -= scaledGrid;

        float startY = mViewportY % scaledGrid;
        if (startY > 0) startY -= scaledGrid;

        // 常规网格线
        mGridPaint.setColor(UIConstants.ViewPort.COLOR_GRID_LINE);
        mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_NORMAL);
        for (float x = startX; x < w; x += scaledGrid) {
            canvas.drawLine(x, 0, x, h, mGridPaint);
        }
        for (float y = startY; y < h; y += scaledGrid) {
            canvas.drawLine(0, y, w, y, mGridPaint);
        }

        // 坐标中心线
        mGridPaint.setColor(UIConstants.ViewPort.COLOR_GRID_AXIS);
        mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_AXIS);
        if (mViewportX >= 0 && mViewportX <= w) {
            canvas.drawLine(mViewportX, 0, mViewportX, h, mGridPaint);
        }
        if (mViewportY >= 0 && mViewportY <= h) {
            canvas.drawLine(0, mViewportY, w, mViewportY, mGridPaint);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawAllConnections(canvas);
        super.dispatchDraw(canvas);
        mInteractionManager.drawOverlay(canvas);
    }

    // 在 Viewport.java 中替换旧方法
    private void drawAllConnections(Canvas canvas) {
        float scaledLineWidth = UIConstants.ViewPort.LINE_WIDTH_CONNECTION * mCurrentScale;
        mConnectionPaint.setStrokeWidth(scaledLineWidth);

        // 直接从核心数据层获取节点
        com.mine.geometry_node.core.node.NodeGraph graph = mEditorContext.getGraph();
        if (graph == null) return;

        for (NodeData outData : graph.nodes.values()) {
            UINode outUi = mNodeViews.get(outData.id);
            if (outUi == null) continue;

            // 1. 绘制普通数据连线
            if (outData.outputs != null) {
                for (Map.Entry<String, List<com.mine.geometry_node.core.node.Connection>> entry : outData.outputs.entrySet()) {
                    String outPortId = entry.getKey();
                    if (entry.getValue() == null) continue;

                    for (com.mine.geometry_node.core.node.Connection link : entry.getValue()) {
                        UINode inUi = mNodeViews.get(link.targetNodeId());
                        if (inUi != null) {
                            drawLine(canvas, outUi, outPortId, inUi, link.targetPortName());
                        }
                    }
                }
            }

            // 2. 绘制执行流连线
            if (outData.execution != null) {
                for (Map.Entry<String, String> entry : outData.execution.entrySet()) {
                    String execOutPortId = entry.getKey();
                    String targetNodeId = entry.getValue();

                    UINode inUi = mNodeViews.get(targetNodeId);
                    if (inUi != null) {
                        String targetExecPortId = findFirstExecInputPort(inUi);
                        if (targetExecPortId != null) {
                            drawLine(canvas, outUi, execOutPortId, inUi, targetExecPortId);
                        }
                    }
                }
            }
        }
    }

    // 辅助绘制方法，复用了你原本定义的 mTempOutPos 和 mTempInPos，零 GC！
    private void drawLine(Canvas canvas, UINode outUi, String outPortId, UINode inUi, String inPortId) {
        outUi.getPortPosition(outPortId, false, mTempOutPos);
        float outUiX = outUi.getTranslationX() + mTempOutPos[0];
        float outUiY = outUi.getTranslationY() + mTempOutPos[1];

        inUi.getPortPosition(inPortId, true, mTempInPos);
        float inUiX = inUi.getTranslationX() + mTempInPos[0];
        float inUiY = inUi.getTranslationY() + mTempInPos[1];

        canvas.drawLine(
                uiToScreenX(outUiX), uiToScreenY(outUiY),
                uiToScreenX(inUiX), uiToScreenY(inUiY),
                mConnectionPaint
        );
    }

    // ==========================================
    // 模块 5: 节点检索与基础交互 APIs (Hit Test & Base Interaction)
    // ==========================================

    private interface NodeVisitor<T> {
        T visit(UINode node);
    }

    /**
     * 按照 Z-Order (从顶层到底层) 遍历节点，直到找到第一个命中的目标
     */
    private <T> T findHitInZOrder(NodeVisitor<T> visitor) {
        for (int i = mNodeLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mNodeLayer.getChildAt(i);
            if (child instanceof UINode node) {
                T result = visitor.visit(node);
                if (result != null) return result; // 一旦命中，立即阻断并返回
            }
        }
        return null;
    }

    @Override
    public UINode findNodeAt(float uiX, float uiY) {
        return findHitInZOrder(node -> {
            node.getLogicalBounds(mTmpNodeBounds);
            mTmpNodeBounds.inset(-12.0f, -12.0f);  // 扩充容差，以包含悬浮在边缘的加减号按钮和端口外沿
            return mTmpNodeBounds.contains(uiX, uiY) ? node : null;
        });
    }

    @Override
    public Viewport.PortInfo findPortAt(float uiX, float uiY) {
        // 使用基于模数动态计算出的交互判定半径
        float dynamicMargin = UIConstants.Node.PORT_HITBOX_RADIUS;
        return findHitInZOrder(node -> {
            node.getLogicalBounds(mTmpNodeBounds);
            mTmpNodeBounds.inset(-dynamicMargin, -dynamicMargin);

            if (mTmpNodeBounds.contains(uiX, uiY)) {
                float localX = uiX - node.getTranslationX();
                float localY = uiY - node.getTranslationY();

                String inPortId = node.hitTestPort(localX, localY, true, dynamicMargin);
                if (inPortId != null) return new PortInfo(node, inPortId, true);

                String outPortId = node.hitTestPort(localX, localY, false, dynamicMargin);
                if (outPortId != null) return new PortInfo(node, outPortId, false);
            }
            return null;
        });
    }

    @Override
    public void updateBoxSelection(float uiX, float uiY, float uiW, float uiH) {
        clearSelection();
        float selRight = uiX + uiW;
        float selBottom = uiY + uiH;

        for (int i = 0; i < mNodeLayer.getChildCount(); i++) {
            if (mNodeLayer.getChildAt(i) instanceof UINode n) {
                n.getLogicalBounds(mTmpNodeBounds);
                if (mTmpNodeBounds.intersects(uiX, uiY, selRight, selBottom)) {
                    addToSelection(n);
                }
            }
        }
    }

    @Override
    public void moveSelectedNodes(float uiDx, float uiDy) {
        for (UINode node : mSelectedNodes) {
            node.setTranslationX(node.getTranslationX() + uiDx);
            node.setTranslationY(node.getTranslationY() + uiDy);
        }
    }

    @Override public List<UINode> getSelectedNodes() { return mSelectedNodes; }

    @Override
    public void clearSelection() {
        for (UINode node : mSelectedNodes) {
            node.setSelected(false);
        }
        mSelectedNodes.clear();
    }

    @Override
    public void addToSelection(UINode node) {
        if (!mSelectedNodes.contains(node)) {
            mSelectedNodes.add(node);
            node.setSelected(true);
        }
    }

    @Override
    public boolean hasConnection(UINode outN, String outId, UINode inN, String inId) {
        List<com.mine.geometry_node.core.node.Connection> links = outN.getNodeData().getConnections(outId);
        if (links == null) return false;

        for (com.mine.geometry_node.core.node.Connection link : links) {
            if (link.targetNodeId().equals(inN.getNodeData().id) && link.targetPortName().equals(inId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void showMenu(float screenX, float screenY) {
        closeMenu();

        mActiveMenu = new ViewportMenu(getContext());
        mActiveMenu.showAt(screenX, screenY, this);
    }

    public void closeMenu() {
        if (mActiveMenu != null && mActiveMenu.isShowing()) {
            mActiveMenu.dismiss();
            mActiveMenu = null;
        }
    }

    @Override
    public void addNodeToScene(UINode node) {
        mNodeLayer.addView(node);
    }

    public void addNode(float screenX, float screenY, String typeId) {
        float uiX = screenToUIX(screenX);
        float uiY = screenToUIY(screenY);
        String mockId = UUID.randomUUID().toString();
        NodeData data = new NodeData(mockId, typeId, uiX, uiY);

        CmdAddNode cmd = new CmdAddNode(mEditorContext.getGraphController(), data);
        mEditorContext.getCommandManager().execute(cmd);
    }

    @Override public Context getUIContext() { return getContext(); }
    @Override public EditorContext getEditorContext() { return mEditorContext; }
    @Override public void requestViewportFocus() { requestFocus(); }

    private String findFirstExecInputPort(UINode node) {
        for (com.mine.geometry_node.core.node.nodes.PortRow row : node.getNodeDef().rows()) {
            if (row.leftPort() != null && row.leftPort().type() == com.mine.geometry_node.core.node.nodes.PortType.EXECUTION) {
                return row.leftPort().id();
            }
        }
        return "flow_in"; // 兜底返回默认名
    }

    // ==========================================
    // 模块 6: 内部控件事件分发 (Event Dispatch & UI Hints)
    // ==========================================

    private record HintHitResult(View view, boolean isLogical) {}

    private interface EventDispatcher {
        boolean dispatch(View target, MotionEvent ev);
    }

    /**
     * 将屏幕事件缓存转换为物理坐标点
     */
    private void eventToScreen(MotionEvent ev) {
        mTmpEventScreen[0] = ev.getRawX();
        mTmpEventScreen[1] = ev.getRawY();
    }

    /**
     * 统一的控件命中测试：一次遍历，双重校验，严格遵守 Z-Order
     */
    private HintHitResult findInteractiveHint(MotionEvent ev) {
        float uiX = screenToUIX(ev.getX());
        float uiY = screenToUIY(ev.getY());
        eventToScreen(ev);

        return findHitInZOrder(node -> {
            // 1. 优先尝试：逻辑坐标系碰撞
            node.getLogicalBounds(mTmpNodeBounds);
            if (mTmpNodeBounds.contains(uiX, uiY)) {
                float localX = (uiX - node.getTranslationX()) * UIConstants.mDensity;
                float localY = (uiY - node.getTranslationY()) * UIConstants.mDensity;
                View v = node.findInteractiveViewAt(localX, localY);
                if (v != null) return new HintHitResult(v, true);
            }

            // 2. 降级尝试：屏幕物理坐标系碰撞 (针对不受缩放影响的特殊弹出框等)
            View vScreen = node.findInteractiveViewAtScreen(mTmpEventScreen[0], mTmpEventScreen[1], mCurrentScale);
            if (vScreen != null) return new HintHitResult(vScreen, false);

            return null;
        });
    }

    /**
     * 统一的事件坐标变换与分发引擎
     * @param isLogical 是否使用逻辑坐标系计算偏移
     * @param skipEventToScreen 是否跳过屏幕坐标换算 (仅 isLogical 为 false 时有效)
     */
    private boolean dispatchTransformedEvent(MotionEvent ev, View target, boolean isLogical, boolean skipEventToScreen, EventDispatcher dispatcher) {
        float ox = ev.getX();
        float oy = ev.getY();
        float lx, ly;

        if (isLogical) {
            if (!(target.getParent() instanceof UINode node)) return false;
            float uiX = screenToUIX(ox);
            float uiY = screenToUIY(oy);
            lx = (uiX - node.getTranslationX()) * UIConstants.mDensity - target.getLeft();
            ly = (uiY - node.getTranslationY()) * UIConstants.mDensity - target.getTop();
        } else {
            if (!skipEventToScreen) eventToScreen(ev);
            target.getLocationOnScreen(mTmpTargetLoc);

            // 【核心修复】计算出屏幕物理差值后，必须除以缩放系数，将其还原为控件的真实本地坐标比例
            float physicalLx = mTmpEventScreen[0] - mTmpTargetLoc[0];
            float physicalLy = mTmpEventScreen[1] - mTmpTargetLoc[1];
            lx = physicalLx / mCurrentScale;
            ly = physicalLy / mCurrentScale;
        }

        // 替换坐标 -> 派发事件 -> 还原坐标
        ev.setLocation(lx, ly);
        boolean handled = dispatcher.dispatch(target, ev);
        ev.setLocation(ox, oy);

        return handled;
    }

    @Override
    public boolean dispatchKeyEvent(icyllis.modernui.view.KeyEvent event) {
        // 【核心修复】全局拦截：只要按下了 Ctrl 键，Viewport 优先尝试处理
        if (event.isCtrlPressed()) {
            System.out.println("isCtrlPressed");
            // 我们只在按下 (ACTION_DOWN) 时触发保存/撤销操作
            if (event.getAction() == icyllis.modernui.view.KeyEvent.ACTION_DOWN) {
                // 将事件交给 KeyManager 判定。如果是 S, Z, Y，KeyManager 会返回 true
                if (mKeyManager.onKeyDown(event)) {
                    return true; // 关键：返回 true 代表事件已消费，EditText 将完全不知道发生了什么
                }
            }
        }

        // 如果不是快捷键，或者没有按下 Ctrl，按照原有的正常逻辑分发
        // 这样 EditText 依然可以正常输入文字、使用 Ctrl+C / Ctrl+V 等自带功能
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            closeMenu();
        }

        // 1. 已捕获焦点的快速通道
        if (mCapturedHintView != null) {
            boolean r = dispatchTransformedEvent(ev, mCapturedHintView, mHintCaptureUsesLogical, !mHintCaptureUsesLogical, View::dispatchTouchEvent);

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mCapturedHintView = null;
                mHintCaptureUsesLogical = false;
            }
            return r;
        }

        // 2. 单指操作的控件碰撞与分发
        if (ev.getPointerCount() == 1) {
            boolean isMouseHoverMove = (action == MotionEvent.ACTION_MOVE && ev.getButtonState() == 0 && ev.getToolType(0) == TOOL_TYPE_MOUSE);
            boolean isActionDown = (action == MotionEvent.ACTION_DOWN);

            if (isMouseHoverMove || isActionDown) {
                HintHitResult hitResult = findInteractiveHint(ev);

                if (hitResult != null) {
                    boolean handled = dispatchTransformedEvent(ev, hitResult.view(), hitResult.isLogical(), !hitResult.isLogical(), View::dispatchTouchEvent);
                    if (handled) {
                        if (isActionDown) {
                            mCapturedHintView = hitResult.view();
                            mHintCaptureUsesLogical = hitResult.isLogical();
                        }
                        return true;
                    }
                }
            }
        }

        // 3. 原生画布与连线交互兜底
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_MOVE
                || action == MotionEvent.ACTION_HOVER_ENTER
                || action == MotionEvent.ACTION_HOVER_EXIT) {

            HintHitResult hitResult = findInteractiveHint(ev);
            if (hitResult != null) {
                if (dispatchTransformedEvent(ev, hitResult.view(), hitResult.isLogical(), !hitResult.isLogical(), View::dispatchGenericMotionEvent)) {
                    return true;
                }
            }
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        HintHitResult hitResult = findInteractiveHint(event);

        if (hitResult != null) {
            View hit = hitResult.view();
            if (hit instanceof EditText) {
                return PointerIcon.getSystemIcon(PointerIcon.TYPE_TEXT);
            }
            return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
        }
        return super.onResolvePointerIcon(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mInteractionManager.onGenericMotionEvent(event) || super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mInteractionManager.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, icyllis.modernui.view.KeyEvent event) {
        return mKeyManager.onKeyDown(event) || super.onKeyDown(keyCode, event);
    }

    // ==========================================
    // 模块 7: 内部数据结构 (Inner Classes)
    // ==========================================

    /**
     * 端口交互的封装上下文信息
     */
    public static class PortInfo {
        public UINode node;
        public String portId;
        public boolean isInput;

        public PortInfo(UINode n, String id, boolean in) {
            this.node = n;
            this.portId = id;
            this.isInput = in;
        }
    }
}