// --- START OF FILE Viewport.java ---
package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionManager;
import com.mine.geometry_node.client.ui.viewport.interaction.KeyManager;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.viewport.menu.ViewportMenu;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Viewport extends FrameLayout implements InteractionContext {

    private static final int TOOL_TYPE_MOUSE = 1;

    private float mLastMouseScreenX = 0;
    private float mLastMouseScreenY = 0;

    // 1. 核心组件
    private FrameLayout mNodeLayer;
    private final ViewportCamera mCamera; // 新增：独立的摄像机模块
    private final InteractionManager mInteractionManager;
    private final KeyManager mKeyManager;
    private GraphSession mCurrentSession;
    private final ViewportController mController;
    private ViewportMenu mActiveMenu;

    private boolean mFirstLayout = true;

    // 2. 视图映射缓存
    private final List<UINode> mSelectedNodes = new ArrayList<>();
    private final Map<String, UINode> mNodeViews = new HashMap<>();

    // 3. 事件缓存
    private View mCapturedHintView;
    private boolean mHintCaptureUsesLogical;

    // 4. 渲染对象复用
    private final icyllis.modernui.graphics.RectF mTmpNodeBounds = new icyllis.modernui.graphics.RectF();
    private final Paint mGridPaint = new Paint();
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mConnectionPaint = new Paint();

    private final int[] mTmpTargetLoc = new int[2];
    private final float[] mTmpEventScreen = new float[2];
    private final float[] mTempOutPos = new float[2];
    private final float[] mTempInPos  = new float[2];

    private TextView mEmptyHint;

    private final List<VisualConnection> mVisualConnections = new ArrayList<>();

    private static class VisualConnection {
        final UINode outNode;
        final String outPortId;
        final UINode inNode;
        final String inPortId;
        final boolean isExecution;

        // 缓存逻辑坐标，避免绘制时重新计算
        float startUiX, startUiY;
        float endUiX, endUiY;

        VisualConnection(UINode outNode, String outPortId, UINode inNode, String inPortId, boolean isExecution) {
            this.outNode = outNode;
            this.outPortId = outPortId;
            this.inNode = inNode;
            this.inPortId = inPortId;
            this.isExecution = isExecution;
        }

        // 重新计算此连线的逻辑坐标
        void updateUiCoordinates(float[] tempOutPos, float[] tempInPos) {
            outNode.getPortPosition(outPortId, false, tempOutPos);
            startUiX = outNode.getTranslationX() + tempOutPos[0];
            startUiY = outNode.getTranslationY() + tempOutPos[1];

            inNode.getPortPosition(inPortId, true, tempInPos);
            endUiX = inNode.getTranslationX() + tempInPos[0];
            endUiY = inNode.getTranslationY() + tempInPos[1];
        }
    }

    public Viewport(Context context) {
        super(context);
        initViewportProps();
        initPaints();

        // 绑定摄像机，并传入更新回调
        mCamera = new ViewportCamera(this::updateTransform);
        mInteractionManager = new InteractionManager(this);
        mKeyManager = new KeyManager(this);
        mController = new ViewportController(this, null);

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public ViewportCamera getCamera() {
        return mCamera;
    }

    private void initViewportProps() {
        setWillNotDraw(false);
        setClipChildren(false);

        mEmptyHint = new TextView(getContext());
        mEmptyHint.setText("当前没有打开的蓝图\n请在资产浏览器中双击 JSON 文件开始编辑");
        mEmptyHint.setTextSize(18);
        mEmptyHint.setTextColor(0xFF888888);
        mEmptyHint.setGravity(Gravity.CENTER);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        addView(mEmptyHint, lp);
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

    public void bindSession(GraphSession session) {
        saveCurrentSessionState();

        if (mNodeLayer != null) {
            removeView(mNodeLayer);
        }
        mNodeViews.clear();
        mSelectedNodes.clear();

        this.mCurrentSession = session;

        if (session != null) {
            mEmptyHint.setVisibility(View.GONE);
            mCamera.setPosition(session.viewportX, session.viewportY);
            mCamera.setScale(session.currentScale);

            mNodeLayer = new FrameLayout(getContext());
            mNodeLayer.setClipChildren(false);
            mNodeLayer.setPivotX(0);
            mNodeLayer.setPivotY(0);
            addView(mNodeLayer, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            mController.setEditorContext(session.editorContext);
            rebuildNodesFromData(session);
            updateTransform();
        } else {
            mEmptyHint.setVisibility(View.VISIBLE);
            mController.setEditorContext(null);
        }
        requestLayout();
        invalidate();
    }

    private void rebuildNodesFromData(GraphSession session) {
        com.mine.geometry_node.core.node.NodeGraph graph = session.editorContext.getGraph();
        if (graph == null || graph.nodes == null) return;

        for (NodeData data : graph.nodes.values()) {
            mController.onNodeAdded(data); // 内部会调用 addNodeView
        }
        updateSelectionState(session.selectedNodeIds);
        rebuildVisualConnections();
    }

    private void saveCurrentSessionState() {
        if (mCurrentSession != null) {
            mCurrentSession.viewportX = mCamera.getX();
            mCurrentSession.viewportY = mCamera.getY();
            mCurrentSession.currentScale = mCamera.getScale();
            mCurrentSession.selectedNodeIds.clear();
            for (UINode node : mSelectedNodes) {
                mCurrentSession.selectedNodeIds.add(node.getNodeData().id);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        saveCurrentSessionState();
        super.onDetachedFromWindow();
    }

    // --- UI 操控 APIs ---

    public void addNodeView(String nodeId, UINode uiNode) {
        if (mNodeLayer != null) {
            mNodeLayer.addView(uiNode);
            mNodeViews.put(nodeId, uiNode);
            invalidate();
        }
    }

    public void removeNodeView(String nodeId) {
        UINode uiNode = mNodeViews.remove(nodeId);
        if (uiNode != null) {
            mNodeLayer.removeView(uiNode);
            mSelectedNodes.remove(uiNode);
            invalidate();
        }
    }

    public UINode getNodeView(String nodeId) { return mNodeViews.get(nodeId); }

    public boolean isNodeSelected(String nodeId) {
        UINode uiNode = mNodeViews.get(nodeId);
        return uiNode != null && mSelectedNodes.contains(uiNode);
    }

    public void updateSelectionState(List<String> selectedNodeIds) {
        for (UINode node : mNodeViews.values()) { node.setSelected(false); }
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
        if (uiNode != null) uiNode.updateNodeLayout();
    }

    // --- 视口变换与绘制同步 ---

    private void updateTransform() {
        if (mNodeLayer != null) {
            mNodeLayer.setTranslationX(0);
            mNodeLayer.setTranslationY(0);

            LayoutParams lp = (LayoutParams) mNodeLayer.getLayoutParams();
            if (lp == null) lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            lp.leftMargin = (int) mCamera.getX();
            lp.topMargin = (int) mCamera.getY();
            mNodeLayer.setLayoutParams(lp);

            mNodeLayer.setScaleX(mCamera.getScale());
            mNodeLayer.setScaleY(mCamera.getScale());
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mFirstLayout && w > 0 && h > 0) {
            mCamera.setPosition(w / 2f, h / 2f);
            mFirstLayout = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), mBackgroundPaint);
        if (mCurrentSession == null) return;
        drawInfiniteGrid(canvas);
        super.onDraw(canvas);
    }

    private void drawInfiniteGrid(Canvas canvas) {
        float scale = mCamera.getScale();
        float cx = mCamera.getX();
        float cy = mCamera.getY();
        float scaledGrid = UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().viewport.gridSize) * scale;
        if (scaledGrid < 5f) return;

        float w = getWidth();
        float h = getHeight();

        float startX = cx % scaledGrid;
        if (startX > 0) startX -= scaledGrid;
        float startY = cy % scaledGrid;
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
        if (cx >= 0 && cx <= w) canvas.drawLine(cx, 0, cx, h, mGridPaint);
        if (cy >= 0 && cy <= h) canvas.drawLine(0, cy, w, cy, mGridPaint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawAllConnections(canvas);
        super.dispatchDraw(canvas);
        mInteractionManager.drawOverlay(canvas);
    }

    private void drawAllConnections(Canvas canvas) {
        if (mCurrentSession == null || mCurrentSession.editorContext == null) return;

        float scaledLineWidth = UIConstants.ViewPort.LINE_WIDTH_CONNECTION * mCamera.getScale();
        mConnectionPaint.setStrokeWidth(scaledLineWidth);

        for (int i = 0; i < mVisualConnections.size(); i++) {
            VisualConnection vc = mVisualConnections.get(i);
            canvas.drawLine(
                    mCamera.uiToScreenX(vc.startUiX), mCamera.uiToScreenY(vc.startUiY),
                    mCamera.uiToScreenX(vc.endUiX), mCamera.uiToScreenY(vc.endUiY),
                    mConnectionPaint
            );
        }
    }

    /**
     * 全量重建连线缓存。
     * 只有在加载图、节点结构大变动、连线增删时调用。
     */
    public void rebuildVisualConnections() {
        mVisualConnections.clear();
        if (mCurrentSession == null || mCurrentSession.editorContext == null) return;

        com.mine.geometry_node.core.node.NodeGraph graph = mCurrentSession.editorContext.getGraph();
        if (graph == null) return;

        for (NodeData outData : graph.nodes.values()) {
            UINode outUi = mNodeViews.get(outData.id);
            if (outUi == null) continue;

            // 处理数据连接
            if (outData.outputs != null) {
                for (Map.Entry<String, List<com.mine.geometry_node.core.node.Connection>> entry : outData.outputs.entrySet()) {
                    String outPortId = entry.getKey();
                    if (entry.getValue() == null) continue;
                    for (com.mine.geometry_node.core.node.Connection link : entry.getValue()) {
                        UINode inUi = mNodeViews.get(link.targetNodeId());
                        if (inUi != null) {
                            VisualConnection vc = new VisualConnection(outUi, outPortId, inUi, link.targetPortName(), false);
                            vc.updateUiCoordinates(mTempOutPos, mTempInPos);
                            mVisualConnections.add(vc);
                        }
                    }
                }
            }

            // 【核心修改】处理执行连接 (Execution)
            if (outData.execOutputs != null) {
                for (Map.Entry<String, com.mine.geometry_node.core.node.Connection> entry : outData.execOutputs.entrySet()) {
                    String execOutPortId = entry.getKey();
                    com.mine.geometry_node.core.node.Connection link = entry.getValue();

                    UINode inUi = mNodeViews.get(link.targetNodeId());
                    if (inUi != null) {
                        // 不再硬编码去找，而是直接使用 link 记录的目标端口！
                        VisualConnection vc = new VisualConnection(outUi, execOutPortId, inUi, link.targetPortName(), true);
                        vc.updateUiCoordinates(mTempOutPos, mTempInPos);
                        mVisualConnections.add(vc);
                    }
                }
            }
        }
        invalidate();
    }

    public void updateConnectionsForNode(String nodeId) {
        boolean needsInvalidate = false;
        for (int i = 0; i < mVisualConnections.size(); i++) {
            VisualConnection vc = mVisualConnections.get(i);
            // 如果该连线的起点或终点是当前移动的节点，则重新计算坐标
            if (vc.outNode.getNodeData().id.equals(nodeId) || vc.inNode.getNodeData().id.equals(nodeId)) {
                vc.updateUiCoordinates(mTempOutPos, mTempInPos);
                needsInvalidate = true;
            }
        }
        if (needsInvalidate) {
            invalidate();
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
                mCamera.uiToScreenX(outUiX), mCamera.uiToScreenY(outUiY),
                mCamera.uiToScreenX(inUiX), mCamera.uiToScreenY(inUiY),
                mConnectionPaint
        );
    }

    // --- 节点检索与交互 APIs ---

    @Override
    public UINode findNodeAt(float uiX, float uiY) {
        if (mNodeLayer == null) return null;
        for (int i = mNodeLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mNodeLayer.getChildAt(i);
            if (child instanceof UINode node) {
                node.getLogicalBounds(mTmpNodeBounds);
                mTmpNodeBounds.inset(-UIConstants.Node.PORT_VISUAL_RADIUS, -UIConstants.Node.PORT_VISUAL_RADIUS);
                if (mTmpNodeBounds.contains(uiX, uiY)) return node;
            }
        }
        return null;
    }

    @Override
    public PortInfo findPortAt(float uiX, float uiY) {
        if (mNodeLayer == null) return null;
        float dynamicMargin = UIConstants.Node.PORT_HITBOX_RADIUS;

        for (int i = mNodeLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mNodeLayer.getChildAt(i);
            if (child instanceof UINode node) {
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
                node.getLogicalBounds(mTmpNodeBounds);
                if (mTmpNodeBounds.contains(uiX, uiY)) return null;
            }
        }
        return null;
    }

    @Override
    public void updateBoxSelection(float uiX, float uiY, float uiW, float uiH) {
        clearSelection();
        if (mNodeLayer == null) return;
        float selRight = uiX + uiW;
        float selBottom = uiY + uiH;

        for (int i = 0; i < mNodeLayer.getChildCount(); i++) {
            if (mNodeLayer.getChildAt(i) instanceof UINode n) {
                n.getLogicalBounds(mTmpNodeBounds);
                if (mTmpNodeBounds.intersects(uiX, uiY, selRight, selBottom)) addToSelection(n);
            }
        }
    }

    @Override
    public void moveSelectedNodes(float uiDx, float uiDy) {
        for (UINode node : mSelectedNodes) {
            node.setTranslationX(node.getTranslationX() + uiDx);
            node.setTranslationY(node.getTranslationY() + uiDy);
            updateConnectionsForNode(node.getNodeData().id);
        }
    }

    @Override public List<UINode> getSelectedNodes() { return mSelectedNodes; }

    @Override
    public void clearSelection() {
        for (UINode node : mSelectedNodes) { node.setSelected(false); }
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
            if (link.targetNodeId().equals(inN.getNodeData().id) && link.targetPortName().equals(inId)) return true;
        }
        return false;
    }

    @Override
    public void showMenu(float screenX, float screenY) {
        closeMenu();
        mActiveMenu = new ViewportMenu(getContext());
        mActiveMenu.showAt(screenX, screenY, this);
    }

    @Override
    public void requestAddNode(float screenX, float screenY, String typeId) {
        if (mController != null) {
            mController.executeAddNode(screenX, screenY, typeId);
        }
    }

    @Override
    public void closeMenu() {
        if (mActiveMenu != null) {
            if (mActiveMenu.getParent() == this) removeView(mActiveMenu);
            mActiveMenu = null;
            requestViewportFocus();
        }
    }

    @Override public void addNodeToScene(UINode node) { if (mNodeLayer != null) mNodeLayer.addView(node); }
    @Override public Context getUIContext() { return getContext(); }
    @Override public EditorContext getEditorContext() { return mCurrentSession != null ? mCurrentSession.editorContext : null; }
    @Override public void requestViewportFocus() { requestFocus(); }

    // 实现 KeyManager 要求的接口
    @Override public float getLastMouseUiX() { return mCamera.screenToUIX(mLastMouseScreenX); }
    @Override public float getLastMouseUiY() { return mCamera.screenToUIY(mLastMouseScreenY); }

    // --- 事件分发 ---

    private record HintHitResult(View view, boolean isLogical, UINode node) {}

    private void eventToScreen(MotionEvent ev) {
        mTmpEventScreen[0] = ev.getRawX();
        mTmpEventScreen[1] = ev.getRawY();
    }

    private HintHitResult findInteractiveHint(MotionEvent ev) {
        float uiX = mCamera.screenToUIX(ev.getX());
        float uiY = mCamera.screenToUIY(ev.getY());

        UINode topNode = findNodeAt(uiX, uiY);
        if (topNode != null) {
            float localXpx = UIUtils.dp2px(uiX - topNode.getTranslationX());
            float localYpx = UIUtils.dp2px(uiY - topNode.getTranslationY());

            View interactiveView = topNode.findInteractiveViewAt(localXpx, localYpx);
            if (interactiveView != null) {
                eventToScreen(ev);
                return new HintHitResult(interactiveView, true, topNode);
            }
        }
        return null;
    }

    private boolean dispatchTransformedEvent(MotionEvent ev, View target, boolean isLogical, boolean skipEventToScreen, OnTouchListener dispatcher) {
        float ox = ev.getX();
        float oy = ev.getY();
        float lx, ly;

        if (isLogical) {
            if (!(target.getParent() instanceof UINode node)) return false;
            float uiX = mCamera.screenToUIX(ox);
            float uiY = mCamera.screenToUIY(oy);
            lx = UIUtils.dp2px(uiX - node.getTranslationX()) - target.getLeft();
            ly = UIUtils.dp2px(uiY - node.getTranslationY()) - target.getTop();
        } else {
            if (!skipEventToScreen) eventToScreen(ev);
            target.getLocationOnScreen(mTmpTargetLoc);
            lx = (mTmpEventScreen[0] - mTmpTargetLoc[0]) / mCamera.getScale();
            ly = (mTmpEventScreen[1] - mTmpTargetLoc[1]) / mCamera.getScale();
        }

        ev.setLocation(lx, ly);
        boolean handled = target.dispatchTouchEvent(ev);
        ev.setLocation(ox, oy);
        return handled;
    }

    @Override
    public boolean dispatchKeyEvent(icyllis.modernui.view.KeyEvent event) {
        if (event.isCtrlPressed() && event.getAction() == icyllis.modernui.view.KeyEvent.ACTION_DOWN) {
            View focusedView = findFocus();
            if (focusedView instanceof EditText) return super.dispatchKeyEvent(event);
            if (mKeyManager.onKeyDown(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isHitOverlay(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child == mNodeLayer || child == mEmptyHint || child.getVisibility() != View.VISIBLE) continue;
            if (x >= child.getLeft() && x <= child.getRight() && y >= child.getTop() && y <= child.getBottom()) return true;
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mLastMouseScreenX = ev.getX();
        mLastMouseScreenY = ev.getY();
        int action = ev.getActionMasked();

        if (mCapturedHintView != null) {
            boolean r = dispatchTransformedEvent(ev, mCapturedHintView, mHintCaptureUsesLogical, !mHintCaptureUsesLogical, null);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mCapturedHintView = null;
                mHintCaptureUsesLogical = false;
            }
            return r;
        }

        if (isHitOverlay(ev.getX(), ev.getY())) return super.dispatchTouchEvent(ev);

        if (ev.getPointerCount() == 1) {
            boolean isMouseHoverMove = (action == MotionEvent.ACTION_MOVE && ev.getButtonState() == 0 && ev.getToolType(0) == TOOL_TYPE_MOUSE);
            boolean isActionDown = (action == MotionEvent.ACTION_DOWN);

            if (isMouseHoverMove || isActionDown) {
                HintHitResult hitResult = findInteractiveHint(ev);
                if (hitResult != null) {
                    boolean handled = dispatchTransformedEvent(ev, hitResult.view(), hitResult.isLogical(), !hitResult.isLogical(), null);
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
                } else if (isActionDown) {
                    requestViewportFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        mLastMouseScreenX = ev.getX();
        mLastMouseScreenY = ev.getY();
        if (isHitOverlay(ev.getX(), ev.getY())) return super.dispatchGenericMotionEvent(ev);

        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_EXIT) {
            HintHitResult hitResult = findInteractiveHint(ev);
            if (hitResult != null) {
                if (dispatchTransformedEvent(ev, hitResult.view(), hitResult.isLogical(), !hitResult.isLogical(), null)) return true;
            }
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        HintHitResult hitResult = findInteractiveHint(event);
        if (hitResult != null) {
            return (hitResult.view() instanceof EditText) ? PointerIcon.getSystemIcon(PointerIcon.TYPE_TEXT) : PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
        }
        return super.onResolvePointerIcon(event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) { return mInteractionManager.onGenericMotionEvent(event) || super.onGenericMotionEvent(event); }
    @Override public boolean onTouchEvent(MotionEvent event) { return mInteractionManager.onTouchEvent(event) || super.onTouchEvent(event); }
    @Override public boolean onKeyDown(int keyCode, icyllis.modernui.view.KeyEvent event) { return mKeyManager.onKeyDown(event) || super.onKeyDown(keyCode, event); }

    public static class PortInfo {
        public UINode node;
        public String portId;
        public boolean isInput;
        public PortInfo(UINode n, String id, boolean in) { this.node = n; this.portId = id; this.isInput = in; }
    }
}
// --- END OF FILE Viewport.java ---