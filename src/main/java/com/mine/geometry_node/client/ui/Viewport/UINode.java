package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.Viewport.UIHints.HintRendererFactory;
import com.mine.geometry_node.client.ui.Viewport.UIHints.UIHintRenderer;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.UIHint;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点 UI 容器 (单个可视化的节点)
 * <p>
 * 架构职责：
 * 1. 负责单个节点内部元素的构建 (标题、端口标签、交互控件)。
 * 2. 负责节点内部的排版布局 (updateNodeLayout)。
 * 3. 负责节点自身的背景、描边、端口圆点及动态按钮的绘制 (onDraw)。
 * 4. 提供节点内部元素的命中测试 (Hit Test) 接口供 Viewport 调用。
 */
public class UINode extends FrameLayout {
    // ==========================================
    // 核心数据与状态
    // ==========================================
    private final NodeData mNodeData;
    private final NodeDef mNodeDef;
    private final EditorContext mEditorContext;
    private boolean mIsSelected = false;
    private int mTotalHeight;

    // ==========================================
    // 渲染与排版缓存
    // ==========================================
    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final Map<String, Float> mInputPortY = new HashMap<>();
    private final Map<String, Float> mOutputPortY = new HashMap<>();

    private final Map<String, TextView> mPortLabels = new HashMap<>();
    private final Map<Integer, View> mHintViews = new HashMap<>();

    public record DynamicActionInfo(boolean isAdd, String referencePortId) {}


    // ==========================================
    // 模块 1: 初始化与构建 (Initialization)
    // ==========================================

    public UINode(Context context, NodeData nodeData, NodeDef nodeDef, EditorContext editorContext) {
        super(context);
        this.mNodeData = nodeData;
        this.mNodeDef = nodeDef;
        this.mEditorContext = editorContext;

        setWillNotDraw(false);
        setClipChildren(false);
        mPaint.setAntiAlias(true);

        buildUIElements(context);
        updateNodeLayout();
    }

    private void buildUIElements(Context context) {
        // --- 构建头部标题 ---
        TextView titleView = new TextView(context);
        titleView.setText(mNodeDef.displayName().getString());
        titleView.setTextColor(UIConstants.CLR_WHITE);
        titleView.setTextSize(UIConstants.Node.TEXT_SIZE_HEADER);
        titleView.setGravity(icyllis.modernui.view.Gravity.CENTER);
        titleView.setClickable(false);
        titleView.setFocusable(false);
        titleView.setLongClickable(false);
        addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, UIConstants.Node.HEADER_HEIGHT));

        // --- 遍历行，构建端口标签与交互控件 ---
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);

            if (row.leftPort() != null) {
                TextView tv = createLabel(context, row.leftPort().displayName().getString(), icyllis.modernui.view.Gravity.LEFT);
                mPortLabels.put(row.leftPort().id(), tv);
                addView(tv, new LayoutParams(LayoutParams.WRAP_CONTENT, UIConstants.Node.ROW_HEIGHT));
            }

            if (row.rightPort() != null) {
                TextView tv = createLabel(context, row.rightPort().displayName().getString(), icyllis.modernui.view.Gravity.RIGHT);
                mPortLabels.put(row.rightPort().id(), tv);
                addView(tv, new LayoutParams(LayoutParams.WRAP_CONTENT, UIConstants.Node.ROW_HEIGHT));
            }

            // --- 利用策略工厂构建交互控件 ---
            UIHint hint = row.uiHint();
            if (hint != null) {
                UIHintRenderer renderer = HintRendererFactory.getRenderer(hint);
                if (renderer != null) {
                    String propKey = row.hintParams() != null ? (String) row.hintParams().get("properties") : null;
                    Object val = propKey != null ? mNodeData.properties.get(propKey) : null;
                    if (val == null && row.leftPort() != null) val = row.leftPort().defaultValue();

                    View hintView = renderer.createView(context, mNodeData, row, mEditorContext);
                    if (hintView != null) {
                        mHintViews.put(i, hintView);
                        addView(hintView, new LayoutParams(0, 0)); // 尺寸将在 updateNodeLayout 中重新分配
                    }
                }
            }
        }
    }

    private TextView createLabel(Context context, String text, int gravity) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(UIConstants.CLR_GRAY_LABEL);
        tv.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        tv.setGravity(gravity | icyllis.modernui.view.Gravity.CENTER_VERTICAL);
        tv.setClickable(false);
        tv.setFocusable(false);
        tv.setLongClickable(false);
        return tv;
    }

    // ==========================================
    // 模块 2: 测量与排版 (Layout & Measure)
    // ==========================================

    public void updateNodeLayout() {
        mInputPortY.clear();
        mOutputPortY.clear();

        float currentY = UIConstants.Node.HEADER_HEIGHT;
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float portCenterY = currentY + UIConstants.Node.ROW_HEIGHT / 2.0f;
            float portCenterYDp = portCenterY / UIConstants.mDensity;

            // --- 排版左侧标签 ---
            if (row.leftPort() != null) {
                mInputPortY.put(row.leftPort().id(), portCenterYDp);
                TextView tv = mPortLabels.get(row.leftPort().id());
                if (tv != null) {
                    LayoutParams lp = (LayoutParams) tv.getLayoutParams();
                    int leftMargin = UIConstants.Node.LABEL_MARGIN_PORT - 5;

                    if (row.uiHint() == UIHint.CHECKBOX) {
                        int checkboxWidth = 16;

                        // 【连线判定】：判断此 CheckBox 对应的端口是否已连线
                        boolean isConnected = mEditorContext.getGraphController().isInputPortConnected(mNodeData.id, row.leftPort().id());

                        if (isConnected) {
                            leftMargin = UIConstants.Node.LABEL_MARGIN_PORT;
                        } else {
                            View cbView = mHintViews.get(i);
                            if (cbView != null) {
                                cbView.measure(
                                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                                );
                                if (cbView.getMeasuredWidth() > 0) {
                                    checkboxWidth = cbView.getMeasuredWidth();
                                }
                            }
                            leftMargin = UIConstants.Node.LABEL_MARGIN_PORT + checkboxWidth + 3;
                        }
                    }

                    lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
                    lp.leftMargin = leftMargin;
                    lp.topMargin = (int) currentY;
                    tv.setLayoutParams(lp);
                    tv.setTranslationX(0); tv.setTranslationY(0);
                }
            }

            // --- 排版右侧标签 ---
            if (row.rightPort() != null) {
                mOutputPortY.put(row.rightPort().id(), portCenterYDp);
                TextView tv = mPortLabels.get(row.rightPort().id());
                if (tv != null) {
                    LayoutParams lp = (LayoutParams) tv.getLayoutParams();
                    lp.gravity = icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.TOP;
                    lp.rightMargin = UIConstants.Node.LABEL_MARGIN_PORT;
                    lp.topMargin = (int) currentY;
                    tv.setLayoutParams(lp);
                    tv.setTranslationX(0); tv.setTranslationY(0);
                }
            }

            // --- 利用策略工厂计算并应用控件排版 ---
            UIHint hint = row.uiHint();
            View hintView = mHintViews.get(i);

            // 默认占据一行高度
            float rowHeightAdded = UIConstants.Node.ROW_HEIGHT;

            if (hint != null && hintView != null) {
                // 【判定连线状态】
                boolean isConnected = false;
                if (row.leftPort() != null) {
                    isConnected = mEditorContext.getGraphController().isInputPortConnected(mNodeData.id, row.leftPort().id());
                }

                hintView.setVisibility(isConnected ? View.GONE : View.VISIBLE);

                UIHintRenderer renderer = HintRendererFactory.getRenderer(hint);
                if (renderer != null) {
                    renderer.updateLayout(hintView, row, currentY, UIConstants.Node.NODE_WIDTH);

                    // 【核心修改：如果是向量且未连线，高度需要增加额外的 3 行！】
                    // 假设你的枚举叫 UIHint.VECTOR，或者根据 row.leftPort().type() == PortType.XYZ 判断
                    if (!isConnected && row.leftPort() != null && row.leftPort().type() == com.mine.geometry_node.core.node.port.PortType.XYZ) {
                        rowHeightAdded += (UIConstants.Node.ROW_HEIGHT * 3);
                    }
                }
            }

            // 动态累加高度
            currentY += rowHeightAdded;
        }

        mTotalHeight = (int) currentY;
        setLayoutParams(new LayoutParams(UIConstants.Node.NODE_WIDTH, mTotalHeight));
        invalidate();
    }

    // ==========================================
    // 模块 3: 核心绘制逻辑 (Draw)
    // ==========================================

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() > 0 ? getWidth() : UIConstants.Node.NODE_WIDTH * UIConstants.mDensity;
        float h = getHeight() > 0 ? getHeight() : mTotalHeight;

        // --- 1. 绘制节点主体背景 ---
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(UIConstants.CLR_BG_NODE_BODY);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, UIConstants.Node.CORNER_RADIUS, (int) UIConstants.Node.CORNER_RADIUS, mPaint);

        // --- 2. 绘制节点头部 ---
        canvas.save();
        canvas.clipRect(0, 0, w, UIConstants.Node.HEADER_HEIGHT);
        mPaint.setColor(mNodeDef.category().getColor());
        canvas.drawRoundRect(0, 0, w, UIConstants.Node.HEADER_HEIGHT + UIConstants.Node.CORNER_RADIUS,
                UIConstants.Node.CORNER_RADIUS, (int) UIConstants.Node.CORNER_RADIUS, mPaint);
        canvas.restore();

        // --- 3. 绘制节点外框描边 (选中高亮) ---
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mIsSelected ? UIConstants.Node.STROKE_WIDTH_SELECTED : UIConstants.Node.STROKE_WIDTH_NORMAL);
        mPaint.setColor(mIsSelected ? UIConstants.CLR_WHITE : UIConstants.CLR_NODE_OUTLINE);
        canvas.drawRoundRect(mTempRect, UIConstants.Node.CORNER_RADIUS, (int) UIConstants.Node.CORNER_RADIUS, mPaint);

        // --- 4. 绘制端口圆点与动态按钮 ---
        float currentY = UIConstants.Node.HEADER_HEIGHT;
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float centerY = currentY + UIConstants.Node.ROW_HEIGHT / 2.0f;

            if (row.leftPort() != null) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(row.leftPort().type().getColor());
                canvas.drawCircle(0, centerY, UIConstants.Node.PORT_VISUAL_RADIUS, mPaint);
            }

            if (row.rightPort() != null) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(row.rightPort().type().getColor());
                canvas.drawCircle(w, centerY, UIConstants.Node.PORT_VISUAL_RADIUS, mPaint);
            }

            if (isDynamicRow(row)) {
                boolean isLast = (i == mNodeDef.rows().size() - 1) || !isDynamicRow(mNodeDef.rows().get(i + 1));
                float rowBottom = currentY + UIConstants.Node.ROW_HEIGHT;

                if (isLast) {
                    // 加号：画在底部正中间
                    drawDynamicButton(canvas, w / 2.0f, rowBottom, true);
                } else {
                    // 减号：向内收缩 16px，略微偏上，避开右侧的输出端口
                    drawDynamicButton(canvas, w - 16.0f, rowBottom - UIConstants.Node.ROW_HEIGHT / 2.0f, false);
                }
            }

            currentY += UIConstants.Node.ROW_HEIGHT;
        }

        super.onDraw(canvas);
    }

    private void drawDynamicButton(Canvas canvas, float cx, float cy, boolean isAdd) {
        float halfSize = 5.0f;

        mPaint.setColor(0xFF444444);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, mPaint);

        mPaint.setColor(0xFFFFFFFF);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.0f);
        canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, mPaint);

        if (isAdd) {
            canvas.drawLine(cx - 3, cy, cx + 3, cy, mPaint);
            canvas.drawLine(cx, cy - 3, cx, cy + 3, mPaint);
        } else {
            canvas.drawLine(cx - 3, cy, cx + 3, cy, mPaint);
        }
    }

    private boolean isDynamicRow(PortRow row) {
        return row.hintParams() != null && Boolean.TRUE.equals(row.hintParams().get(PortMetaKeys.IS_DYNAMIC));
    }

    // ==========================================
    // 模块 4: 触摸拦截与命中测试 (Hit Test)
    // ==========================================

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float d = UIConstants.mDensity;
        float lx = ev.getX() / d;
        float ly = ev.getY() / d;

        float interceptRadius = UIConstants.Node.PORT_HITBOX_RADIUS;

        if (hitTestPort(lx, ly, true, interceptRadius) != null ||
                hitTestPort(lx, ly, false, interceptRadius) != null) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    public View findInteractiveViewAt(float localX, float localY) {
        if (localX < 0 || localX > this.getWidth() || localY < 0 || localY > this.getHeight()) {
            return null;
        }

        for (View v : mHintViews.values()) {
            if (v.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (localX >= v.getLeft() && localX < v.getRight()
                    && localY >= v.getTop() && localY < v.getBottom()) {
                return v;
            }
        }
        return null;
    }

    public String hitTestPort(float localX, float localY, boolean checkInput, float touchRadius) {
        float wDp = getWidth() > 0 ? getWidth() / UIConstants.mDensity : UIConstants.Node.NODE_WIDTH;
        float targetX = checkInput ? 0 : wDp;
        float dx = localX - targetX;

        Map<String, Float> map = checkInput ? mInputPortY : mOutputPortY;
        float thresholdSq = touchRadius * touchRadius;
        String best = null;
        float bestDistSq = Float.MAX_VALUE;

        for (Map.Entry<String, Float> entry : map.entrySet()) {
            float dy = localY - entry.getValue();
            float distSq = dx * dx + dy * dy;
            if (distSq <= thresholdSq && distSq < bestDistSq) {
                bestDistSq = distSq;
                best = entry.getKey();
            }
        }
        return best;
    }

    public DynamicActionInfo hitTestDynamicButton(float localX, float localY) {
        float d = UIConstants.mDensity;
        float wDp = getWidth() > 0 ? getWidth() / d : UIConstants.Node.NODE_WIDTH;
        float yPx = UIConstants.Node.HEADER_HEIGHT;

        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            if (isDynamicRow(row)) {
                boolean isLast = (i == mNodeDef.rows().size() - 1) || !isDynamicRow(mNodeDef.rows().get(i + 1));
                float rowBottomDp = (yPx + UIConstants.Node.ROW_HEIGHT) / d;

                float btnCenterXDp;
                float btnCenterYDp;

                if (isLast) {
                    btnCenterXDp = wDp / 2.0f;
                    btnCenterYDp = rowBottomDp;
                } else {
                    btnCenterXDp = wDp - (16.0f / d);
                    btnCenterYDp = rowBottomDp - (UIConstants.Node.ROW_HEIGHT / 2.0f / d);
                }

                // 适度放大点击容差至 10.0f
                if (Math.abs(localX - btnCenterXDp) <= 6.0f && Math.abs(localY - btnCenterYDp) <= 6.0f) {
                    String refId = row.leftPort() != null ? row.leftPort().id() :
                            (row.rightPort() != null ? row.rightPort().id() : "");
                    return new DynamicActionInfo(isLast, refId);
                }
            }
            yPx += UIConstants.Node.ROW_HEIGHT;
        }
        return null;
    }

    // ==========================================
    // 模块 5: 属性访问器 (Getters & Setters)
    // ==========================================

    public void getPortPosition(String portId, boolean isInput, float[] outPos) {
        float wDp = getWidth() > 0 ? getWidth() / UIConstants.mDensity : UIConstants.Node.NODE_WIDTH;
        outPos[0] = isInput ? 0 : wDp;
        Float y = isInput ? mInputPortY.get(portId) : mOutputPortY.get(portId);

        if (y != null) {
            outPos[1] = y;
        } else {
            outPos[1] = UIConstants.Node.HEADER_HEIGHT + UIConstants.Node.ROW_HEIGHT / 2.0f;
        }
    }

    public NodeData getNodeData() { return mNodeData; }
    public NodeDef getNodeDef() { return mNodeDef; }

    public void setSelected(boolean selected) {
        if (mIsSelected != selected) {
            mIsSelected = selected;
            invalidate();
        }
    }
    public boolean isSelected() { return mIsSelected; }

    /**
     * 获取节点在 Viewport 逻辑坐标系下的包围盒
     */
    public void getLogicalBounds(RectF outRect) {
        float left = getTranslationX();
        float top = getTranslationY();
        float right = left + getWidth() / UIConstants.mDensity;
        float bottom = top + getHeight() / UIConstants.mDensity;
        outRect.set(left, top, right, bottom);
    }
}