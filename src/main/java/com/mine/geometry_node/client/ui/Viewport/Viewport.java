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
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 视口容器 (画布核心引擎)
 * <p>
 * 架构职责：
 * 1. 坐标系映射：负责将屏幕物理坐标 (Screen Pixels) 转换为 UI 逻辑坐标 (Logical DP)。
 * 2. 节点承载：管理所有 UINode 的生命周期与渲染层级，UINode 内部的 TranslationX/Y 仅存储逻辑坐标。
 * 3. 视口变换：处理画布的平移 (Pan) 和缩放 (Zoom)，底层渲染框架自动处理 DP 到物理像素的映射。
 * 4. 事件中转：将触摸和键盘事件分发给 InteractionManager 和 KeyManager。
 * </p>
 */
public class Viewport extends FrameLayout implements InteractionContext, EditorContext.EditorListener {

    private static final int TOOL_TYPE_MOUSE = 1;

    // 核心组件与数据依赖


    /** 节点真实的物理容器，所有的平移和缩放变换最终作用于此容器 */
    private final FrameLayout mNodeLayer;

    /** 交互管理器：处理框选、拖拽节点、连线等复杂交互逻辑 */
    private final InteractionManager mInteractionManager;

    /** 快捷键管理器：处理键盘输入 */
    private final KeyManager mKeyManager;

    /** 编辑器上下文：桥接底层图数据与 UI 表现 */
    private final EditorContext mEditorContext;


    // 视口状态与数据映射


    /** 视口物理偏移 X 坐标 (Screen Pixels) */
    private float mViewportX = 0;

    /** 视口物理偏移 Y 坐标 (Screen Pixels) */
    private float mViewportY = 0;

    /** 视口当前的缩放比例 (1.0f 为基准) */
    private float mCurrentScale = 1.0f;

    /** 标记是否是首次布局，用于初始化视口中心点 */
    private boolean mFirstLayout = true;

    /** 当前被选中的节点集合 */
    private final List<UINode> mSelectedNodes = new ArrayList<>();

    /** 节点 ID 到 UINode 实例的映射表，实现 O(1) 查找 */
    private final Map<String, UINode> mNodeViews = new HashMap<>();

    /** 当前视口中所有的连接线集合 */
    private final List<Connection> mConnections = new ArrayList<>();

    private View mCapturedHintView;

    private boolean mHintCaptureUsesLogical;

    private final int[] mTmpTargetLoc = new int[2];

    private final float[] mTmpEventScreen = new float[2];


    // 渲染画笔与复用对象 (防止 onDraw 触发频繁 GC)


    private final Paint mGridPaint = new Paint();
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mConnectionPaint = new Paint();

    /** 临时数组，用于计算输出端口坐标，避免在渲染循环中创建对象 */
    private final float[] mTempOutPos = new float[2];

    /** 临时数组，用于计算输入端口坐标，避免在渲染循环中创建对象 */
    private final float[] mTempInPos  = new float[2];


    // 初始化与生命周期


    /**
     * 构造函数：初始化视口环境与核心组件
     */
    public Viewport(Context context, EditorContext editorContext) {
        super(context);
        this.mEditorContext = editorContext;
        this.mEditorContext.addListener(this);

        // 初始化节点层
        mNodeLayer = new FrameLayout(context);
        initViewportProps();
        initPaints();
        addView(mNodeLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 初始化交互管理器
        mInteractionManager = new InteractionManager(this);
        mKeyManager = new KeyManager(this);

        // 确保 Viewport 可以接收焦点与按键事件
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /**
     * 初始化视图容器的层级属性
     */
    private void initViewportProps() {
        setWillNotDraw(false);    // 允许执行 onDraw 绘制背景和网格
        setClipChildren(false);   // 允许子视图绘制超出边界（方便节点拖拽时过度）

        mNodeLayer.setPivotX(0);  // 将缩放中心设置在左上角，配合 viewport 偏移计算
        mNodeLayer.setPivotY(0);
        mNodeLayer.setClipChildren(false);
    }

    /**
     * 初始化背景、网格、连线的渲染画笔
     */
    private void initPaints() {
        mBackgroundPaint.setColor(UIConstants.ViewPort.BG_COLOR);

        mGridPaint.setAntiAlias(true);
        mGridPaint.setStyle(Paint.Style.STROKE);

        mConnectionPaint.setAntiAlias(true);
        mConnectionPaint.setStyle(Paint.Style.STROKE);
        mConnectionPaint.setStrokeWidth(3.0f);
        mConnectionPaint.setColor(0xFFE0E0E0);
    }


    // 1. 数据驱动监听器 (EditorContext.EditorListener)


    @Override
    public void onNodeAdded(NodeData nodeData) {
        NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(nodeData);
        if (def == null) {
            return;
        }

        UINode uiNode = new UINode(getContext(), nodeData, def);

        // NodeData 中存储的是逻辑坐标 (DP)，直接赋值给 View 的 Translation
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
        mConnections.removeIf(c ->
                c.outputNode.getNodeData().id.equals(nodeId) || c.inputNode.getNodeData().id.equals(nodeId));
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
        rebuildConnectionsFromGraph();
        invalidate();
    }

    @Override
    public void onGraphConnectionsRebuildRequested() {
        rebuildConnectionsFromGraph();
        invalidate();
    }

    private void rebuildConnectionsFromGraph() {
        mConnections.clear();
        NodeGraph g = mEditorContext.getGraph();
        for (NodeData out : g.nodes.values()) {
            UINode outUi = mNodeViews.get(out.id);
            if (outUi == null) continue;
            for (java.util.Map.Entry<String, java.util.List<com.mine.geometry_node.core.node.Connection>> e : out.outputs.entrySet()) {
                if (e.getValue() == null) continue;
                for (com.mine.geometry_node.core.node.Connection link : e.getValue()) {
                    UINode inUi = mNodeViews.get(link.targetNodeId());
                    if (inUi != null) {
                        mConnections.add(new Connection(outUi, e.getKey(), inUi, link.targetPortName()));
                    }
                }
            }
        }
    }

    @Override
    public void onSelectionChanged(List<String> selectedNodeIds) {
        // 重置所有节点状态
        for (UINode node : mNodeViews.values()) {
            node.setSelected(false);
        }
        mSelectedNodes.clear();

        // 更新选中状态
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
            // 接收并设置逻辑坐标 (DP)
            uiNode.setTranslationX(x);
            uiNode.setTranslationY(y);
            invalidate();
        }
    }

    @Override
    public void onConnectionAdded(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        UINode outNode = mNodeViews.get(outNodeId);
        UINode inNode = mNodeViews.get(inNodeId);
        if (outNode == null || inNode == null) return;

        mConnections.add(new Connection(outNode, outPortId, inNode, inPortId));
        invalidate();
    }

    @Override
    public void onConnectionRemoved(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        mConnections.removeIf(c ->
                c.outputNode.getNodeData().id.equals(outNodeId)
                        && c.outputPortID.equals(outPortId)
                        && c.inputNode.getNodeData().id.equals(inNodeId)
                        && c.inputPortID.equals(inPortId)
        );
        invalidate();
    }


    // 2. 核心渲染逻辑 (背景、网格、连线层)


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 首次布局时，将视口原点对齐到画布中心
        if (mFirstLayout && w > 0 && h > 0) {
            mViewportX = w / 2f;
            mViewportY = h / 2f;
            updateTransform();
            mFirstLayout = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 1. 绘制底层背景颜色
        canvas.drawRect(0, 0, getWidth(), getHeight(), mBackgroundPaint);

        // 2. 绘制无限网格 (基于物理像素和缩放进行动态计算)
        drawInfiniteGrid(canvas);

        super.onDraw(canvas);
    }

    /**
     * 绘制动态网格系统
     */
    private void drawInfiniteGrid(Canvas canvas) {
        // 计算缩放后的实际网格间距 (逻辑间距 * 缩放比例 * 屏幕密度)
        float scaledGrid = UIConstants.ViewPort.GRID_SIZE * mCurrentScale * UIConstants.mDensity;

        // 当网格过密时停止绘制，防止性能下降和视觉杂乱
        if (scaledGrid < 5f) return;

        float w = getWidth();
        float h = getHeight();

        // 计算网格起始偏移 (利用取模运算将网格对齐到视口偏移量)
        float startX = mViewportX % scaledGrid;
        if (startX > 0) startX -= scaledGrid;

        float startY = mViewportY % scaledGrid;
        if (startY > 0) startY -= scaledGrid;

        // --- 绘制常规网格线 ---
        mGridPaint.setColor(UIConstants.ViewPort.COLOR_GRID_LINE);
        mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_NORMAL);

        for (float x = startX; x < w; x += scaledGrid) {
            canvas.drawLine(x, 0, x, h, mGridPaint);
        }
        for (float y = startY; y < h; y += scaledGrid) {
            canvas.drawLine(0, y, w, y, mGridPaint);
        }

        // --- 绘制坐标轴中心线 ---
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
        // 1. 绘制节点之间的连线 (必须在子 View 节点之前绘制，使其处于下层)
        drawAllConnections(canvas);

        // 2. 绘制子 View (即 mNodeLayer 及其包含的 UINode)
        super.dispatchDraw(canvas);

        // 3. 绘制交互覆盖层 Overlay (如框选虚线框、正在拖拽的连线草稿)
        mInteractionManager.drawOverlay(canvas);
    }

    /**
     * 遍历并绘制所有已建立的节点连线
     */
    private void drawAllConnections(Canvas canvas) {
        for (Connection c : mConnections) {
            // 获取输出端逻辑坐标 (使用 String outputPortId)
            c.outputNode.getPortPosition(c.outputPortID, false, mTempOutPos);
            float outUiX = c.outputNode.getTranslationX() + mTempOutPos[0];
            float outUiY = c.outputNode.getTranslationY() + mTempOutPos[1];

            // 获取输入端逻辑坐标 (使用 String inputPortId)
            c.inputNode.getPortPosition(c.inputPortID, true, mTempInPos);
            float inUiX = c.inputNode.getTranslationX() + mTempInPos[0];
            float inUiY = c.inputNode.getTranslationY() + mTempInPos[1];

            // 将 UI 逻辑坐标转换为屏幕物理坐标后进行绘制
            canvas.drawLine(
                    uiToScreenX(outUiX), uiToScreenY(outUiY),
                    uiToScreenX(inUiX), uiToScreenY(inUiY),
                    mConnectionPaint
            );
        }
    }


    // 3. 核心坐标变换机制 (实现 InteractionContext)


    @Override
    public float screenToUIX(float screenX) {
        // 降维：(物理坐标 - 视口偏移) -> 还原缩放 -> 还原密度，得到 DP
        return ((screenX - mViewportX) / mCurrentScale) / UIConstants.mDensity;
    }

    @Override
    public float screenToUIY(float screenY) {
        return ((screenY - mViewportY) / mCurrentScale) / UIConstants.mDensity;
    }

    @Override
    public float uiToScreenX(float uiX) {
        // 升维：DP -> 乘密度应用屏幕 -> 乘缩放应用视口 -> 叠加物理偏移
        return (uiX * UIConstants.mDensity) * mCurrentScale + mViewportX;
    }

    @Override
    public float uiToScreenY(float uiY) {
        return (uiY * UIConstants.mDensity) * mCurrentScale + mViewportY;
    }


    // 4. 视口控制与变换 (平移与缩放)


    /**
     * 将当前视口的偏移和缩放状态应用到 NodeLayer 上
     */
    @Override
    public void updateTransform() {
        // mNodeLayer 的 setTranslation 接收的是逻辑坐标，需除以密度进行换算
        mNodeLayer.setTranslationX(mViewportX / UIConstants.mDensity);
        mNodeLayer.setTranslationY(mViewportY / UIConstants.mDensity);

        mNodeLayer.setScaleX(mCurrentScale);
        mNodeLayer.setScaleY(mCurrentScale);

        invalidate();
    }

    /**
     * 执行缩放操作 (以指定屏幕坐标为锚点)
     */
    @Override
    public void performZoom(boolean zoomIn, float pivotScreenX, float pivotScreenY) {
        float oldScale = mCurrentScale;
        float factor = zoomIn ? UIConstants.ViewPort.ZOOM_SENSITIVITY : -UIConstants.ViewPort.ZOOM_SENSITIVITY;

        // 限制缩放范围
        mCurrentScale = Math.max(UIConstants.ViewPort.ZOOM_MIN,
                Math.min(UIConstants.ViewPort.ZOOM_MAX, oldScale + factor));

        if (mCurrentScale == oldScale) return;

        // 计算缩放比率以重新定位视口原点 (实现以鼠标点为中心的缩放)
        float ratio = mCurrentScale / oldScale;
        mViewportX = pivotScreenX - (pivotScreenX - mViewportX) * ratio;
        mViewportY = pivotScreenY - (pivotScreenY - mViewportY) * ratio;

        updateTransform();
    }

    @Override public float getViewportX() { return mViewportX; }
    @Override public float getViewportY() { return mViewportY; }
    @Override public void setViewportX(float x) { mViewportX = x; }
    @Override public void setViewportY(float y) { mViewportY = y; }
    @Override public float getCurrentScale() { return mCurrentScale; }


    // 5. 命中测试与交互 (全部基于 UI 逻辑坐标系计算)


    @Override
    public UINode findNodeAt(float uiX, float uiY) {
        // 倒序遍历处理 Z-Order (确保总是优先命中上层节点)
        for (int i = mNodeLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mNodeLayer.getChildAt(i);

            if (child instanceof UINode node) {
                float left = node.getTranslationX();
                float top = node.getTranslationY();
                float right = left + node.getWidth() / UIConstants.mDensity;
                float bottom = top + node.getHeight() / UIConstants.mDensity;

                // 逻辑坐标系下的 AABB 碰撞检测
                if (uiX >= left && uiX <= right && uiY >= top && uiY <= bottom) {
                    return node;
                }
            }
        }
        return null;
    }

    @Override
    public Viewport.PortInfo findPortAt(float uiX, float uiY) {
        UINode node = findNodeAt(uiX, uiY);
        if (node == null) return null;

        // 转换至节点内部的局部 UI 坐标
        float localX = uiX - node.getTranslationX();
        float localY = uiY - node.getTranslationY();

        String inPortId = node.hitTestPort(localX, localY, true);
        if (inPortId != null) return new PortInfo(node, inPortId, true);

        String outPortId = node.hitTestPort(localX, localY, false);
        if (outPortId != null) return new PortInfo(node, outPortId, false);

        return null;
    }

    @Override
    public void moveSelectedNodes(float uiDx, float uiDy) {
        for (UINode node : mSelectedNodes) {
            // 位移直接叠加逻辑坐标
            node.setTranslationX(node.getTranslationX() + uiDx);
            node.setTranslationY(node.getTranslationY() + uiDy);
        }
    }

    @Override
    public void updateBoxSelection(float uiX, float uiY, float uiW, float uiH) {
        clearSelection();

        // 预计算框选区域右、下边界，避免在循环体内重复计算
        float selRight = uiX + uiW;
        float selBottom = uiY + uiH;

        for (int i = 0; i < mNodeLayer.getChildCount(); i++) {
            if (mNodeLayer.getChildAt(i) instanceof UINode n) {
                float nodeLeft = n.getTranslationX();
                float nodeTop = n.getTranslationY();
                float nodeRight = nodeLeft + n.getWidth() / UIConstants.mDensity;
                float nodeBottom = nodeTop + n.getHeight() / UIConstants.mDensity;

                // 2D 矩形相交检测算法 (AABB)
                boolean isIntersecting = uiX < nodeRight &&
                        selRight > nodeLeft &&
                        uiY < nodeBottom &&
                        selBottom > nodeTop;

                if (isIntersecting) {
                    addToSelection(n);
                }
            }
        }
    }


    // 6. 状态管理与 API 接口实现


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

    @Override public void addConnection(Connection c) { mConnections.add(c); }

    @Override
    public boolean hasConnection(UINode outN, String outId, UINode inN, String inId) {
        for (Connection c : mConnections) {
            if (c.isSame(outN, outId, inN, inId)) return true;
        }
        return false;
    }

    @Override
    public void showMenu(float screenX, float screenY) {
        new ViewportMenu(getContext()).showAt(screenX, screenY, this);
    }

    @Override
    public void addNodeToScene(UINode node) {
        mNodeLayer.addView(node);
    }

    /**
     * 通过外部调用（如右键菜单）添加指定类型节点
     */
    public void addNode(float screenX, float screenY, String typeId) {
        float uiX = screenToUIX(screenX);
        float uiY = screenToUIY(screenY);

        String mockId = UUID.randomUUID().toString();
        NodeData data = new NodeData(mockId, typeId, uiX, uiY);

        CmdAddNode cmd =
                new CmdAddNode(mEditorContext.getGraphController(), data);
        mEditorContext.getCommandManager().execute(cmd);
    }

    @Override public icyllis.modernui.core.Context getUIContext() { return getContext(); }
    @Override public EditorContext getEditorContext() { return mEditorContext; }

    @Override
    public void requestViewportFocus() {
        requestFocus();
    }


    // 7. 事件分发拦截

    private void eventToScreen(MotionEvent ev) {
        mTmpEventScreen[0] = ev.getRawX();
        mTmpEventScreen[1] = ev.getRawY();
    }

    private View findHintByScreen(float screenX, float screenY) {
        for (int i = mNodeLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mNodeLayer.getChildAt(i);
            if (!(child instanceof UINode node)) continue;
            View v = node.findInteractiveViewAtScreen(screenX, screenY);
            if (v != null) return v;
        }
        return null;
    }

    private View findInteractiveHintAtLogical(float uiX, float uiY) {
        for (int i = mNodeLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mNodeLayer.getChildAt(i);
            if (!(child instanceof UINode node)) continue;
            float left = node.getTranslationX();
            float top = node.getTranslationY();
            float right = left + node.getWidth() / UIConstants.mDensity;
            float bottom = top + node.getHeight() / UIConstants.mDensity;
            if (uiX < left || uiX > right || uiY < top || uiY > bottom) continue;
            float localX = (uiX - left) * UIConstants.mDensity;
            float localY = (uiY - top) * UIConstants.mDensity;
            View v = node.findInteractiveViewAt(localX, localY);
            if (v != null) return v;
        }
        return null;
    }

    private boolean dispatchTransformedToView(MotionEvent ev, View target, boolean skipEventToScreen) {
        if (!skipEventToScreen) {
            eventToScreen(ev);
        }
        target.getLocationOnScreen(mTmpTargetLoc);
        float lx = mTmpEventScreen[0] - mTmpTargetLoc[0];
        float ly = mTmpEventScreen[1] - mTmpTargetLoc[1];
        float ox = ev.getX();
        float oy = ev.getY();
        ev.setLocation(lx, ly);
        boolean handled = target.dispatchTouchEvent(ev);
        ev.setLocation(ox, oy);
        return handled;
    }

    private boolean dispatchTransformedToViewLogical(MotionEvent ev, View target) {
        float uiX = screenToUIX(ev.getX());
        float uiY = screenToUIY(ev.getY());
        if (!(target.getParent() instanceof UINode node)) return false;
        float lx = (uiX - node.getTranslationX()) * UIConstants.mDensity - target.getLeft();
        float ly = (uiY - node.getTranslationY()) * UIConstants.mDensity - target.getTop();
        float ox = ev.getX();
        float oy = ev.getY();
        ev.setLocation(lx, ly);
        boolean handled = target.dispatchTouchEvent(ev);
        ev.setLocation(ox, oy);
        return handled;
    }

    private boolean dispatchTransformedGenericToView(MotionEvent ev, View target, boolean skipEventToScreen) {
        if (!skipEventToScreen) {
            eventToScreen(ev);
        }
        target.getLocationOnScreen(mTmpTargetLoc);
        float lx = mTmpEventScreen[0] - mTmpTargetLoc[0];
        float ly = mTmpEventScreen[1] - mTmpTargetLoc[1];
        float ox = ev.getX();
        float oy = ev.getY();
        ev.setLocation(lx, ly);
        boolean handled = target.dispatchGenericMotionEvent(ev);
        ev.setLocation(ox, oy);
        return handled;
    }

    private boolean dispatchTransformedGenericToViewLogical(MotionEvent ev, View target) {
        float uiX = screenToUIX(ev.getX());
        float uiY = screenToUIY(ev.getY());
        if (!(target.getParent() instanceof UINode node)) return false;
        float lx = (uiX - node.getTranslationX()) * UIConstants.mDensity - target.getLeft();
        float ly = (uiY - node.getTranslationY()) * UIConstants.mDensity - target.getTop();
        float ox = ev.getX();
        float oy = ev.getY();
        ev.setLocation(lx, ly);
        boolean handled = target.dispatchGenericMotionEvent(ev);
        ev.setLocation(ox, oy);
        return handled;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_MOVE
                || action == MotionEvent.ACTION_HOVER_ENTER
                || action == MotionEvent.ACTION_HOVER_EXIT) {
            float uiX = screenToUIX(ev.getX());
            float uiY = screenToUIY(ev.getY());
            View hit = findInteractiveHintAtLogical(uiX, uiY);
            if (hit != null) {
                if (dispatchTransformedGenericToViewLogical(ev, hit)) {
                    return true;
                }
            }
            eventToScreen(ev);
            hit = findHintByScreen(mTmpEventScreen[0], mTmpEventScreen[1]);
            if (hit != null) {
                if (dispatchTransformedGenericToView(ev, hit, true)) {
                    return true;
                }
            }
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (mCapturedHintView != null) {
            boolean r = mHintCaptureUsesLogical
                    ? dispatchTransformedToViewLogical(ev, mCapturedHintView)
                    : dispatchTransformedToView(ev, mCapturedHintView, false);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mCapturedHintView = null;
                mHintCaptureUsesLogical = false;
            }
            return r;
        }
        if (ev.getPointerCount() == 1) {
            if (action == MotionEvent.ACTION_MOVE && ev.getButtonState() == 0
                    && ev.getToolType(0) == TOOL_TYPE_MOUSE) {
                float uiX = screenToUIX(ev.getX());
                float uiY = screenToUIY(ev.getY());
                View hit = findInteractiveHintAtLogical(uiX, uiY);
                if (hit != null) {
                    if (dispatchTransformedToViewLogical(ev, hit)) {
                        return true;
                    }
                }
                eventToScreen(ev);
                hit = findHintByScreen(mTmpEventScreen[0], mTmpEventScreen[1]);
                if (hit != null) {
                    if (dispatchTransformedToView(ev, hit, true)) {
                        return true;
                    }
                }
            }
            if (action == MotionEvent.ACTION_DOWN) {
                float uiX = screenToUIX(ev.getX());
                float uiY = screenToUIY(ev.getY());
                View hit = findInteractiveHintAtLogical(uiX, uiY);
                if (hit != null) {
                    if (dispatchTransformedToViewLogical(ev, hit)) {
                        mCapturedHintView = hit;
                        mHintCaptureUsesLogical = true;
                        return true;
                    }
                }
                eventToScreen(ev);
                hit = findHintByScreen(mTmpEventScreen[0], mTmpEventScreen[1]);
                if (hit != null) {
                    if (dispatchTransformedToView(ev, hit, true)) {
                        mCapturedHintView = hit;
                        mHintCaptureUsesLogical = false;
                        return true;
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        float uiX = screenToUIX(event.getX());
        float uiY = screenToUIY(event.getY());
        View hit = findInteractiveHintAtLogical(uiX, uiY);
        if (hit == null) {
            eventToScreen(event);
            hit = findHintByScreen(mTmpEventScreen[0], mTmpEventScreen[1]);
        }
        if (hit != null) {
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


    // 内部类 (纯数据结构)


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

    /**
     * 节点间连线的数据模型映射
     */
    public static class Connection {
        public UINode inputNode;
        public UINode outputNode;
        public String inputPortID;
        public String outputPortID;

        public Connection(UINode outN, String outID, UINode inN, String inID) {
            this.outputNode = outN;
            this.outputPortID = outID;
            this.inputNode = inN;
            this.inputPortID = inID;
        }

        public boolean isSame(UINode outN, String outID, UINode inN, String inID) {
            return outputNode == outN && java.util.Objects.equals(outputPortID, outID)
                    && inputNode == inN && java.util.Objects.equals(inputPortID, inID);
        }
    }
}