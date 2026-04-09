package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.Viewport.Interaction.InteractionContext;
import com.mine.geometry_node.client.ui.Viewport.Interaction.InteractionManager;
import com.mine.geometry_node.client.ui.Viewport.Interaction.KeyManager;
import com.mine.geometry_node.core.node.NodeData;

import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
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

/**
 * 视口容器 (画布核心引擎 - 纯 View 层)
 * <p>
 * 架构职责：
 * 1. 坐标系映射：屏幕物理坐标 <-> UI 逻辑坐标。
 * 2. 节点承载：渲染层级管理与连线绘制。
 * 3. 视口变换：处理画布的平移与缩放。
 * 4. 事件中转：处理 Touch 事件并分发给对应的管理器或 UI 控件。
 */
public class Viewport extends FrameLayout implements InteractionContext {

    private static final int TOOL_TYPE_MOUSE = 1;

    // ==========================================
    // 1. 核心组件
    // ==========================================
    private final FrameLayout mNodeLayer;
    private final InteractionManager mInteractionManager;
    private final KeyManager mKeyManager;
    private final EditorContext mEditorContext;
    private final ViewportController mController;          // 控制器引用
    private ViewportMenu mActiveMenu;

    // ==========================================
    // 2. 视口状态
    // ==========================================
    private float mViewportX = 0;
    private float mViewportY = 0;
    private float mCurrentScale = 1.0f;
    private boolean mFirstLayout = true;

    // ==========================================
    // 3. 视图映射缓存
    // ==========================================
    private final List<UINode> mSelectedNodes = new ArrayList<>();
    private final Map<String, UINode> mNodeViews = new HashMap<>();

    // ==========================================
    // 4. 事件缓存
    // ==========================================
    private View mCapturedHintView;
    private boolean mHintCaptureUsesLogical;

    // ==========================================
    // 5. 渲染对象复用
    // ==========================================
    private final icyllis.modernui.graphics.RectF mTmpNodeBounds = new icyllis.modernui.graphics.RectF();
    private final Paint mGridPaint = new Paint();
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mConnectionPaint = new Paint();

    private final int[] mTmpTargetLoc = new int[2];
    private final float[] mTmpEventScreen = new float[2];
    private final float[] mTempOutPos = new float[2];
    private final float[] mTempInPos  = new float[2];

    public Viewport(Context context, EditorContext editorContext) {
        super(context);
        this.mEditorContext = editorContext;

        mNodeLayer = new FrameLayout(context);
        initViewportProps();
        initPaints();
        addView(mNodeLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mInteractionManager = new InteractionManager(this);
        mKeyManager = new KeyManager(this);

        // 绑定 Controller
        mController = new ViewportController(this, editorContext);

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
    // UI 操控 APIs (供 ViewportController 调用)
    // ==========================================

    public void addNodeView(String nodeId, UINode uiNode) {
        mNodeLayer.addView(uiNode);
        mNodeViews.put(nodeId, uiNode);
        invalidate();
    }

    public void removeNodeView(String nodeId) {
        UINode uiNode = mNodeViews.remove(nodeId);
        if (uiNode != null) {
            mNodeLayer.removeView(uiNode);
            mSelectedNodes.remove(uiNode);
            invalidate();
        }
    }

    public UINode getNodeView(String nodeId) {
        return mNodeViews.get(nodeId);
    }

    public boolean isNodeSelected(String nodeId) {
        UINode uiNode = mNodeViews.get(nodeId);
        return uiNode != null && mSelectedNodes.contains(uiNode);
    }

    public void updateSelectionState(List<String> selectedNodeIds) {
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

    public void updateNodePosition(String nodeId, float x, float y) {
        UINode uiNode = mNodeViews.get(nodeId);
        if (uiNode != null) {
            uiNode.setTranslationX(x);
            uiNode.setTranslationY(y);
            invalidate();
        }
    }

    public void notifyNodeLayoutUpdate(String nodeId) {
        UINode uiNode = mNodeViews.get(nodeId);
        if (uiNode != null) {
            uiNode.updateNodeLayout();
        }
    }

    public void addNode(float screenX, float screenY, String typeId) {
        // 代理给 Controller 处理数据逻辑
        mController.executeAddNode(screenX, screenY, typeId);
    }

    // ==========================================
    // 视口变换与坐标系映射
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
    // 渲染逻辑
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
        if (scaledGrid < 5f) return;

        float w = getWidth();
        float h = getHeight();

        float startX = mViewportX % scaledGrid;
        if (startX > 0) startX -= scaledGrid;

        float startY = mViewportY % scaledGrid;
        if (startY > 0) startY -= scaledGrid;

        mGridPaint.setColor(UIConstants.ViewPort.COLOR_GRID_LINE);
        mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_NORMAL);
        for (float x = startX; x < w; x += scaledGrid) {
            canvas.drawLine(x, 0, x, h, mGridPaint);
        }
        for (float y = startY; y < h; y += scaledGrid) {
            canvas.drawLine(0, y, w, y, mGridPaint);
        }

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

    private void drawAllConnections(Canvas canvas) {
        float scaledLineWidth = UIConstants.ViewPort.LINE_WIDTH_CONNECTION * mCurrentScale;
        mConnectionPaint.setStrokeWidth(scaledLineWidth);

        com.mine.geometry_node.core.node.NodeGraph graph = mEditorContext.getGraph();
        if (graph == null) return;

        for (NodeData outData : graph.nodes.values()) {
            UINode outUi = mNodeViews.get(outData.id);
            if (outUi == null) continue;

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
    // 节点检索与交互 APIs
    // ==========================================

    private interface NodeVisitor<T> {
        T visit(UINode node);
    }

    private <T> T findHitInZOrder(NodeVisitor<T> visitor) {
        for (int i = mNodeLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mNodeLayer.getChildAt(i);
            if (child instanceof UINode node) {
                T result = visitor.visit(node);
                if (result != null) return result;
            }
        }
        return null;
    }

    @Override
    public UINode findNodeAt(float uiX, float uiY) {
        return findHitInZOrder(node -> {
            node.getLogicalBounds(mTmpNodeBounds);
            mTmpNodeBounds.inset(-UIConstants.Node.PORT_VISUAL_RADIUS, -UIConstants.Node.PORT_VISUAL_RADIUS);
            return mTmpNodeBounds.contains(uiX, uiY) ? node : null;
        });
    }

    @Override
    public Viewport.PortInfo findPortAt(float uiX, float uiY) {
        float dynamicMargin = UIConstants.Node.PORT_HITBOX_RADIUS;

        // 手动倒序遍历（从顶到底判断 Z 轴）
        for (int i = mNodeLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mNodeLayer.getChildAt(i);
            if (child instanceof UINode node) {
                node.getLogicalBounds(mTmpNodeBounds);

                // 1. 先扩展包围盒，检查鼠标是否在包含端口的【宽泛判定区】内
                mTmpNodeBounds.inset(-dynamicMargin, -dynamicMargin);
                if (mTmpNodeBounds.contains(uiX, uiY)) {
                    float localX = uiX - node.getTranslationX();
                    float localY = uiY - node.getTranslationY();

                    // 尝试精确命中端口
                    String inPortId = node.hitTestPort(localX, localY, true, dynamicMargin);
                    if (inPortId != null) return new PortInfo(node, inPortId, true);

                    String outPortId = node.hitTestPort(localX, localY, false, dynamicMargin);
                    if (outPortId != null) return new PortInfo(node, outPortId, false);
                }

                // 2. 如果没命中该节点的端口，但鼠标落在这个节点的【主体】内，必须阻断往下层寻找！
                node.getLogicalBounds(mTmpNodeBounds); // 恢复真实节点主体包围盒
                if (mTmpNodeBounds.contains(uiX, uiY)) {
                    return null; // 被当前节点主体死死遮挡，阻断穿透
                }
            }
        }
        return null;
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
        if (mActiveMenu != null) {
            // 安全移除 View
            if (mActiveMenu.getParent() == this) {
                removeView(mActiveMenu);
            }
            mActiveMenu = null;
            requestViewportFocus(); // 重新获取焦点给画布
        }
    }

    @Override
    public void addNodeToScene(UINode node) {
        mNodeLayer.addView(node);
    }

    @Override public Context getUIContext() { return getContext(); }
    @Override public EditorContext getEditorContext() { return mEditorContext; }
    @Override public void requestViewportFocus() { requestFocus(); }

    private String findFirstExecInputPort(UINode node) {
        for (PortRow row : node.getNodeDef().rows()) {
            if (row.leftPort() != null && row.leftPort().type() == PortType.EXECUTION) {
                return row.leftPort().id();
            }
        }
        return "flow_in";
    }

    // ==========================================
    // 事件分发
    // ==========================================

    private record HintHitResult(View view, boolean isLogical, UINode node) {}

    private interface EventDispatcher {
        boolean dispatch(View target, MotionEvent ev);
    }

    private void eventToScreen(MotionEvent ev) {
        mTmpEventScreen[0] = ev.getRawX();
        mTmpEventScreen[1] = ev.getRawY();
    }

    private HintHitResult findInteractiveHint(MotionEvent ev) {
        float uiX = screenToUIX(ev.getX());
        float uiY = screenToUIY(ev.getY());

        // 1. 先获取当前位置最顶层的节点（遵循真实的 Z 轴遮挡关系）
        UINode topNode = findNodeAt(uiX, uiY);

        if (topNode != null) {
            // 2. 如果点到了节点，我们【只在这个最顶层节点】内部寻找交互控件
            float localX = (uiX - topNode.getTranslationX()) * UIConstants.mDensity;
            float localY = (uiY - topNode.getTranslationY()) * UIConstants.mDensity;

            View interactiveView = topNode.findInteractiveViewAt(localX, localY);
            if (interactiveView != null) {
                eventToScreen(ev);
                return new HintHitResult(interactiveView, true, topNode);
            }
            // 【关键防御】：如果顶层节点覆盖了这里，但它没有输入框，也不能继续往下层找！直接阻断。
        }

        return null;
    }

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

            float physicalLx = mTmpEventScreen[0] - mTmpTargetLoc[0];
            float physicalLy = mTmpEventScreen[1] - mTmpTargetLoc[1];
            lx = physicalLx / mCurrentScale;
            ly = physicalLy / mCurrentScale;
        }

        ev.setLocation(lx, ly);
        boolean handled = dispatcher.dispatch(target, ev);
        ev.setLocation(ox, oy);

        return handled;
    }

    @Override
    public boolean dispatchKeyEvent(icyllis.modernui.view.KeyEvent event) {
        if (event.isCtrlPressed()) {
            if (event.getAction() == icyllis.modernui.view.KeyEvent.ACTION_DOWN) {
                if (mKeyManager.onKeyDown(event)) {
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        if (mCapturedHintView != null) {
            boolean r = dispatchTransformedEvent(ev, mCapturedHintView, mHintCaptureUsesLogical, !mHintCaptureUsesLogical, View::dispatchTouchEvent);

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mCapturedHintView = null;
                mHintCaptureUsesLogical = false;
            }
            return r;
        }

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

                            if (!mSelectedNodes.contains(hitResult.node())) {
                                clearSelection();
                                addToSelection(hitResult.node());
                                invalidate();
                            }
                        }
                        return true;
                    }
                }else if (isActionDown) {
                    requestViewportFocus();
                }
            }
        }

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