package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.Viewport.UIHints.HintRendererFactory;
import com.mine.geometry_node.client.ui.Viewport.UIHints.UIHintRenderer;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private static class RowLayoutMetrics {
        float topY;        // 该行顶部的 Y 坐标
        float height;      // 该行的高度
        RectF btnHitbox;   // 动态按钮的点击判定区 (逻辑坐标 dp)，为空则代表此行无按钮
        boolean isAddBtn;  // 如果有按钮，是加号还是减号
        String refPortId;  // 按钮关联的端口 ID
    }

    private final List<RowLayoutMetrics> mRowMetrics = new ArrayList<>();

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
        // 1. 清空所有旧缓存
        mInputPortY.clear();
        mOutputPortY.clear();
        mRowMetrics.clear();

        float currentY = UIConstants.Node.HEADER_HEIGHT;

        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);

            float rowHeight = calculateRowHeight(row);

            // 端口的 Y 坐标始终位于这一行的“首行垂直居中”位置
            float portCenterY = currentY + UIConstants.Node.ROW_HEIGHT / 2.0f;
            float portCenterYDp = portCenterY / UIConstants.mDensity;

            // 2. 初始化本行缓存指标
            RowLayoutMetrics metrics = new RowLayoutMetrics();
            metrics.topY = currentY;
            metrics.height = rowHeight;

            // --- 3. 左侧标签 ---
            if (row.leftPort() != null) {
                mInputPortY.put(row.leftPort().id(), portCenterYDp); // 保留！供外部连线使用

                TextView tv = mPortLabels.get(row.leftPort().id());
                if (tv != null) {
                    LayoutParams lp = (LayoutParams) tv.getLayoutParams();
                    int leftMargin = UIConstants.Node.LABEL_MARGIN_PORT - 5;

                    if (row.uiHint() == UIHint.CHECKBOX) {
                        int checkboxWidth = 16;
                        boolean isConnected = mNodeData.isInputConnected(row.leftPort().id());

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

            // --- 4. 右侧标签 ---
            if (row.rightPort() != null) {
                mOutputPortY.put(row.rightPort().id(), portCenterYDp); // 保留！

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

            // --- 5. UIHint 控件 ---
            UIHint hint = row.uiHint();
            View hintView = mHintViews.get(i);
            if (hint != null && hintView != null) {
                // 优化 1 成果：直接读状态
                boolean isConnected = false;
                if (row.leftPort() != null) {
                    isConnected = mNodeData.isInputConnected(row.leftPort().id());
                }

                hintView.setVisibility(isConnected ? View.GONE : View.VISIBLE);

                UIHintRenderer renderer = HintRendererFactory.getRenderer(hint);
                if (renderer != null) {
                    renderer.updateLayout(hintView, row, currentY, UIConstants.Node.NODE_WIDTH);
                }
            }

            // --- 6. 预计算动态按钮碰撞判定区 (Hitbox) ---
            if (isDynamicRow(row)) {
                boolean isLast = (i == mNodeDef.rows().size() - 1) || !isDynamicRow(mNodeDef.rows().get(i + 1));
                metrics.isAddBtn = isLast;
                metrics.refPortId = row.leftPort() != null ? row.leftPort().id() : (row.rightPort() != null ? row.rightPort().id() : "");

                float rowBottomDp = (currentY + rowHeight) / UIConstants.mDensity;
                float cxDp, cyDp;
                float wDp = UIConstants.Node.NODE_WIDTH;

                if (isLast) {
                    cxDp = wDp / 2.0f;
                    cyDp = rowBottomDp;
                } else {
                    cxDp = wDp - (16.0f / UIConstants.mDensity);
                    cyDp = rowBottomDp - (UIConstants.Node.ROW_HEIGHT / 2.0f / UIConstants.mDensity);
                }

                // 统一生成 12x12 dp 的碰撞矩形
                float tolerance = 6.0f;
                metrics.btnHitbox = new RectF(cxDp - tolerance, cyDp - tolerance, cxDp + tolerance, cyDp + tolerance);
            }

            // 7. 将这一行的数据存入缓存，并向下推移 Y 坐标
            mRowMetrics.add(metrics);
            currentY += rowHeight;
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
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            RowLayoutMetrics metrics = mRowMetrics.get(i);
            float centerY = metrics.topY + UIConstants.Node.ROW_HEIGHT / 2.0f;

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

            if (metrics.btnHitbox != null) {
                float cx = metrics.isAddBtn ? (w / 2.0f) : (w - 16.0f);
                float cy = metrics.topY + metrics.height;
                if (!metrics.isAddBtn) cy -= UIConstants.Node.ROW_HEIGHT / 2.0f;

                drawDynamicButton(canvas, cx, cy, metrics.isAddBtn);
            }
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
        float pxX = localX * UIConstants.mDensity;
        float pxY = localY * UIConstants.mDensity;

        // 2. 获取实际绘制时的物理像素宽度
        float w = getWidth() > 0 ? getWidth() : UIConstants.Node.NODE_WIDTH * UIConstants.mDensity;

        // 3. 定义像素级的点击宽容度 (相当于 15dp 的有效半径，手感很好)
        float tolerancePx = 15.0f * UIConstants.mDensity;

        for (RowLayoutMetrics metrics : mRowMetrics) {
            // 我们依然借用 btnHitbox != null 来作为“这一行存在动态按钮”的标志
            if (metrics.btnHitbox != null) {

                // 4. 【核心修复】直接照抄 onDraw() 里的视觉中心计算公式！确保 100% 视觉与判定重合
                float cx = metrics.isAddBtn ? (w / 2.0f) : (w - 16.0f);
                float cy = metrics.topY + metrics.height;
                if (!metrics.isAddBtn) {
                    cy -= UIConstants.Node.ROW_HEIGHT / 2.0f;
                }

                // 5. 在物理像素坐标系下进行精准范围比对
                if (Math.abs(pxX - cx) <= tolerancePx && Math.abs(pxY - cy) <= tolerancePx) {
                    return new DynamicActionInfo(metrics.isAddBtn, metrics.refPortId);
                }
            }
        }
        return null;
    }

    private float calculateRowHeight(PortRow row) {
        float height = UIConstants.Node.ROW_HEIGHT;

        // 如果没有任何 UI 控件，直接返回基础 1 行高度
        if (row.uiHint() == null) {
            return height;
        }

        // 检查是否连线，连线状态下控件会被隐藏，高度只占 1 行
        boolean isConnected = false;
        if (row.leftPort() != null) {
            isConnected = mNodeData.isInputConnected(row.leftPort().id());
        }

        // 核心：未连线时，通过 Renderer 动态获取所需额外高度，实现完全解耦
        if (!isConnected) {
            UIHintRenderer renderer = HintRendererFactory.getRenderer(row.uiHint());
            // 如果找不到 Renderer，默认不增加额外高度
            float extraRows = (renderer != null) ? renderer.getRequiredExtraRows(row) : 0.0f;

            boolean hasLabel = row.leftPort() != null || row.rightPort() != null;

            if (hasLabel) {
                // 有标签：基础 1 行 + 控件占用的额外行数
                height = UIConstants.Node.ROW_HEIGHT * (1.0f + extraRows);
            } else {
                // 无标签 (纯配置属性)：至少保证有 1 行。如果控件本身较大，取最大值
                height = UIConstants.Node.ROW_HEIGHT * Math.max(1.0f, extraRows);
            }
        }
        return height;
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