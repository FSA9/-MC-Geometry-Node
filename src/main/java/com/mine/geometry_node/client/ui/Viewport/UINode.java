package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.PortRow;
import com.mine.geometry_node.core.node.nodes.UIHint;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.CheckBox;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

import java.util.EnumMap;
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
    // 1. 静态常量配置 (Constants)
    // ==========================================

    private static final int NODE_WIDTH = 160;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 20;

    private static final float PORT_RADIUS = 5.0f;
    private static final float CORNER_RADIUS = 6.0f;
    private static final float STROKE_WIDTH_NORMAL = 1.5f;
    private static final float STROKE_WIDTH_SELECTED = 2.5f;

    private static final int LABEL_MARGIN_PORT = 8;
    private static final int TEXT_SIZE_HEADER = 10;
    private static final int TEXT_SIZE_LABEL = 10;

    private static final int COLOR_BODY = 0xEE2A2A2A;
    private static final int COLOR_OUTLINE = 0xFF111111;
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_TEXT_LABEL = 0xFFDDDDDD;
    private static final int COLOR_TEXT_HEADER = 0xFFFFFFFF;

    // ==========================================
    // 2. 控件渲染策略注册表 (UI Hint Factory)
    // ==========================================

    /**
     * 策略接口：定义控件的创建与排版规则
     */
    private interface HintRenderer {
        View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext);
        void updateLayout(View view, PortRow row, float currentY, int nodeWidth);
    }

    private static final Map<UIHint, HintRenderer> HINT_RENDERERS = new EnumMap<>(UIHint.class);

    static {
        // --- CheckBox 渲染策略 ---
        HINT_RENDERERS.put(UIHint.CHECKBOX, new HintRenderer() {
            @Override
            public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
                String propKey = row.hintParams() != null ? (String) row.hintParams().get("property_key") : null;
                Object val = propKey != null ? nodeData.properties.get(propKey) : null;
                if (val == null && row.leftPort() != null) val = row.leftPort().defaultValue();

                CheckBox cb = new CheckBox(context);
                cb.setChecked(String.valueOf(val).equalsIgnoreCase("true"));
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (propKey != null && editorContext != null) {
                        Object oldVal = nodeData.properties.get(propKey);
                        if (!Boolean.valueOf(isChecked).equals(oldVal)) {
                            CmdChangeProperty cmd = new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, isChecked);
                            editorContext.getCommandManager().execute(cmd);
                        }
                    } else if (propKey != null) {
                        nodeData.properties.put(propKey, isChecked); // 兜底逻辑
                    }
                });
                return cb;
            }

            @Override
            public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
                LayoutParams lp = (LayoutParams) view.getLayoutParams();
                lp.width = LayoutParams.WRAP_CONTENT;
                lp.height = LayoutParams.WRAP_CONTENT;
                lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
                lp.leftMargin = LABEL_MARGIN_PORT;
                lp.topMargin = (int) currentY;
                view.setLayoutParams(lp);
            }
        });

        // --- Input 输入框渲染策略 ---
        HINT_RENDERERS.put(UIHint.INPUT, new HintRenderer() {
            @Override
            public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
                String propKey = row.hintParams() != null ? (String) row.hintParams().get("property_key") : null;
                Object val = propKey != null ? nodeData.properties.get(propKey) : null;
                if (val == null && row.leftPort() != null) val = row.leftPort().defaultValue();

                EditText et = new EditText(context);
                et.setText(val != null ? val.toString() : "");
                et.setTextColor(COLOR_TEXT_LABEL);
                et.setTextSize(TEXT_SIZE_LABEL);
                et.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus && propKey != null && editorContext != null) {
                        String currentText = et.getText().toString();
                        Object oldVal = nodeData.properties.get(propKey);
                        if (!currentText.equals(oldVal)) {
                            CmdChangeProperty cmd = new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, currentText);
                            editorContext.getCommandManager().execute(cmd);
                        }
                    }
                });
                return et;
            }

            @Override
            public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
                applyStandardHintLayout(view, row, currentY, nodeWidth);
            }
        });

        // --- Select 下拉框渲染策略 ---
        HINT_RENDERERS.put(UIHint.SELECT, new HintRenderer() {
            @Override
            public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
                String propKey = row.hintParams() != null ? (String) row.hintParams().get("property_key") : null;
                Object val = propKey != null ? nodeData.properties.get(propKey) : null;
                String[] options = row.hintParams() != null ? (String[]) row.hintParams().get("options") : new String[0];

                TextView selectBtn = new TextView(context);
                String currentText = val != null ? val.toString() : (options.length > 0 ? options[0] : "");
                selectBtn.setText(currentText + " ▼");
                selectBtn.setTextColor(COLOR_TEXT_LABEL);
                selectBtn.setTextSize(TEXT_SIZE_LABEL);
                selectBtn.setGravity(icyllis.modernui.view.Gravity.CENTER);

                selectBtn.setOnClickListener(v -> {
                    showSimpleDropdown(context, selectBtn, options, selectedVal -> {
                        selectBtn.setText(selectedVal + " ▼");
                        if (propKey != null && editorContext != null) {
                            Object oldVal = nodeData.properties.get(propKey);
                            if (!selectedVal.equals(oldVal)) {
                                CmdChangeProperty cmd = new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, selectedVal);
                                editorContext.getCommandManager().execute(cmd);
                            }
                        }
                    });
                });
                return selectBtn;
            }

            @Override
            public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
                applyStandardHintLayout(view, row, currentY, nodeWidth);
            }
        });
    }

    private static void showSimpleDropdown(Context context, View anchorView, String[] options, java.util.function.Consumer<String> onSelected) {
        icyllis.modernui.widget.LinearLayout listLayout = new icyllis.modernui.widget.LinearLayout(context);
        listLayout.setOrientation(icyllis.modernui.widget.LinearLayout.VERTICAL);
//        listLayout.setBackgroundColor(0xFF222222);

        for (String opt : options) {
            TextView tv = new TextView(context);
            tv.setText(opt);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(TEXT_SIZE_LABEL);
            tv.setPadding(10, 8, 10, 8);

            listLayout.addView(tv, new icyllis.modernui.widget.LinearLayout.LayoutParams(
                    icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        icyllis.modernui.widget.PopupWindow popup = new icyllis.modernui.widget.PopupWindow(listLayout,
                anchorView.getWidth(),
                icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);

        for (int i = 0; i < listLayout.getChildCount(); i++) {
            final String selectedOpt = options[i];
            listLayout.getChildAt(i).setOnClickListener(v -> {
                onSelected.accept(selectedOpt);
                popup.dismiss();
            });
        }
        popup.showAsDropDown(anchorView);
    }

    /**
     * 辅助方法：Input 和 Select 等长条形控件的通用排版计算
     */
    private static void applyStandardHintLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = (row.leftPort() != null) ? (nodeWidth * 0.45f) : LABEL_MARGIN_PORT;
        float endX = nodeWidth - ((row.rightPort() != null) ? 40 : LABEL_MARGIN_PORT);

        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        lp.width = (int) (endX - startX);
        lp.height = ROW_HEIGHT - 6;
        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
        lp.leftMargin = (int) startX;
        lp.topMargin = (int) currentY + 3;
        view.setLayoutParams(lp);
    }

    // ==========================================
    // 3. 核心数据与状态 (Data & State)
    // ==========================================
    private final NodeData mNodeData;
    private final NodeDef mNodeDef;
    private final EditorContext mEditorContext;
    private boolean mIsSelected = false;
    private int mTotalHeight;

    // ==========================================
    // 4. 渲染与排版缓存 (Cache)
    // ==========================================
    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final Map<String, Float> mInputPortY = new HashMap<>();
    private final Map<String, Float> mOutputPortY = new HashMap<>();
    private final int[] mTmpHintOnScreen = new int[2];

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
        // --- 1. 构建头部标题 ---
        TextView titleView = new TextView(context);
        titleView.setText(mNodeDef.displayName().getString());
        titleView.setTextColor(COLOR_TEXT_HEADER);
        titleView.setTextSize(TEXT_SIZE_HEADER);
        titleView.setGravity(icyllis.modernui.view.Gravity.CENTER);
        titleView.setClickable(false);
        titleView.setFocusable(false);
        titleView.setLongClickable(false);
        addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, HEADER_HEIGHT));

        // --- 2. 遍历行，构建端口标签与交互控件 ---
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);

            if (row.leftPort() != null) {
                TextView tv = createLabel(context, row.leftPort().displayName().getString(), icyllis.modernui.view.Gravity.LEFT);
                mPortLabels.put(row.leftPort().id(), tv);
                addView(tv, new LayoutParams(LayoutParams.WRAP_CONTENT, ROW_HEIGHT));
            }

            if (row.rightPort() != null) {
                TextView tv = createLabel(context, row.rightPort().displayName().getString(), icyllis.modernui.view.Gravity.RIGHT);
                mPortLabels.put(row.rightPort().id(), tv);
                addView(tv, new LayoutParams(LayoutParams.WRAP_CONTENT, ROW_HEIGHT));
            }

            // --- 3. 利用策略工厂构建交互控件 ---
            UIHint hint = row.uiHint();
            if (hint != null) {
                HintRenderer renderer = HINT_RENDERERS.get(hint);
                if (renderer != null) {
                    String propKey = row.hintParams() != null ? (String) row.hintParams().get("property_key") : null;
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
        tv.setTextColor(COLOR_TEXT_LABEL);
        tv.setTextSize(TEXT_SIZE_LABEL);
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

        float currentY = HEADER_HEIGHT;
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float portCenterY = currentY + ROW_HEIGHT / 2.0f;
            float portCenterYDp = portCenterY / UIConstants.mDensity;

            // --- 1. 排版左侧标签 ---
            if (row.leftPort() != null) {
                mInputPortY.put(row.leftPort().id(), portCenterYDp);
                TextView tv = mPortLabels.get(row.leftPort().id());
                if (tv != null) {
                    LayoutParams lp = (LayoutParams) tv.getLayoutParams();
                    int leftMargin = LABEL_MARGIN_PORT;
                    if (row.uiHint() == UIHint.CHECKBOX) leftMargin += 16; // 为 Checkbox 留出空间
                    lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
                    lp.leftMargin = leftMargin;
                    lp.topMargin = (int) currentY;
                    tv.setLayoutParams(lp);
                    tv.setTranslationX(0); tv.setTranslationY(0);
                }
            }

            // --- 2. 排版右侧标签 ---
            if (row.rightPort() != null) {
                mOutputPortY.put(row.rightPort().id(), portCenterYDp);
                TextView tv = mPortLabels.get(row.rightPort().id());
                if (tv != null) {
                    LayoutParams lp = (LayoutParams) tv.getLayoutParams();
                    lp.gravity = icyllis.modernui.view.Gravity.RIGHT | icyllis.modernui.view.Gravity.TOP;
                    lp.rightMargin = LABEL_MARGIN_PORT;
                    lp.topMargin = (int) currentY;
                    tv.setLayoutParams(lp);
                    tv.setTranslationX(0); tv.setTranslationY(0);
                }
            }

            // --- 3. 利用策略工厂计算并应用控件排版 ---
            UIHint hint = row.uiHint();
            View hintView = mHintViews.get(i);
            if (hint != null && hintView != null) {
                HintRenderer renderer = HINT_RENDERERS.get(hint);
                if (renderer != null) {
                    renderer.updateLayout(hintView, row, currentY, NODE_WIDTH);
                }
            }

            currentY += ROW_HEIGHT;
        }

        mTotalHeight = (int) currentY;
        setLayoutParams(new LayoutParams(NODE_WIDTH, mTotalHeight));
        invalidate();
    }

    // ==========================================
    // 模块 3: 核心绘制逻辑 (Draw)
    // ==========================================

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() > 0 ? getWidth() : NODE_WIDTH * UIConstants.mDensity;
        float h = getHeight() > 0 ? getHeight() : mTotalHeight;

        // --- 1. 绘制节点主体背景 ---
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_BODY);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, CORNER_RADIUS, (int) CORNER_RADIUS, mPaint);

        // --- 2. 绘制节点头部 (根据 Category 着色) ---
        canvas.save();
        canvas.clipRect(0, 0, w, HEADER_HEIGHT);
        mPaint.setColor(mNodeDef.category().getColor());
        canvas.drawRoundRect(0, 0, w, HEADER_HEIGHT + CORNER_RADIUS, CORNER_RADIUS, (int) CORNER_RADIUS, mPaint);
        canvas.restore();

        // --- 3. 绘制节点外框描边 (选中高亮) ---
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mIsSelected ? STROKE_WIDTH_SELECTED : STROKE_WIDTH_NORMAL);
        mPaint.setColor(mIsSelected ? COLOR_SELECTED : COLOR_OUTLINE);
        canvas.drawRoundRect(mTempRect, CORNER_RADIUS, (int) CORNER_RADIUS, mPaint);

        // --- 4. 绘制端口圆点与动态按钮 ---
        float currentY = HEADER_HEIGHT;
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float centerY = currentY + ROW_HEIGHT / 2.0f;

            if (row.leftPort() != null) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(row.leftPort().type().getColor());
                canvas.drawCircle(0, centerY, PORT_RADIUS, mPaint);
            }

            if (row.rightPort() != null) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(row.rightPort().type().getColor());
                canvas.drawCircle(w, centerY, PORT_RADIUS, mPaint);
            }

            if (isDynamicRow(row)) {
                boolean isLast = (i == mNodeDef.rows().size() - 1) || !isDynamicRow(mNodeDef.rows().get(i + 1));
                float rowBottom = currentY + ROW_HEIGHT;
                if (isLast) drawDynamicButton(canvas, 0, rowBottom, true);
                else drawDynamicButton(canvas, w, rowBottom, false);
            }

            currentY += ROW_HEIGHT;
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
        return row.hintParams() != null && Boolean.TRUE.equals(row.hintParams().get("is_dynamic"));
    }

    // ==========================================
    // 模块 4: 触摸拦截与命中测试 (Hit Test)
    // ==========================================

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float d = UIConstants.mDensity;
        float lx = ev.getX() / d;
        float ly = ev.getY() / d;
        float interceptRadius = .0f;

        if (hitTestPort(lx, ly, true, interceptRadius) != null ||
                hitTestPort(lx, ly, false, interceptRadius) != null) {
            return false; // 如果按到了端口，禁止把事件传递给节点本身的拖拽或内部控件
        }
        return super.dispatchTouchEvent(ev);
    }

    public View findInteractiveViewAtScreen(float screenX, float screenY, float currentScale) {
        for (View v : mHintViews.values()) {
            v.getLocationOnScreen(mTmpHintOnScreen);

            // 将内部未缩放的布局尺寸，乘以当前的画面缩放比例
            float w = v.getWidth() * currentScale;
            float h = v.getHeight() * currentScale;

            if (screenX >= mTmpHintOnScreen[0] && screenX < mTmpHintOnScreen[0] + w
                    && screenY >= mTmpHintOnScreen[1] && screenY < mTmpHintOnScreen[1] + h) {
                return v;
            }
        }
        return null;
    }

    public View findInteractiveViewAt(float localX, float localY) {
        for (View v : mHintViews.values()) {
            if (localX >= v.getLeft() && localX < v.getRight()
                    && localY >= v.getTop() && localY < v.getBottom()) {
                return v;
            }
        }
        return null;
    }

    public String hitTestPort(float localX, float localY, boolean checkInput, float touchRadius) {
        float wDp = getWidth() > 0 ? getWidth() / UIConstants.mDensity : NODE_WIDTH;
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
        float wDp = getWidth() > 0 ? getWidth() / d : NODE_WIDTH;
        float yPx = HEADER_HEIGHT;

        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            if (isDynamicRow(row)) {
                boolean isLast = (i == mNodeDef.rows().size() - 1) || !isDynamicRow(mNodeDef.rows().get(i + 1));
                float rowBottomDp = (yPx + ROW_HEIGHT) / d;
                float btnCenterXDp = isLast ? 0 : wDp;

                // 检测是否点击到了加减号按钮的包围盒 (8dp 容差)
                if (Math.abs(localX - btnCenterXDp) <= 8.0f && Math.abs(localY - rowBottomDp) <= 8.0f) {
                    String refId = row.leftPort() != null ? row.leftPort().id() :
                            (row.rightPort() != null ? row.rightPort().id() : "");
                    return new DynamicActionInfo(isLast, refId);
                }
            }
            yPx += ROW_HEIGHT;
        }
        return null;
    }

    // ==========================================
    // 模块 5: 属性访问器 (Getters & Setters)
    // ==========================================

    public void getPortPosition(String portId, boolean isInput, float[] outPos) {
        float wDp = getWidth() > 0 ? getWidth() / UIConstants.mDensity : NODE_WIDTH;
        outPos[0] = isInput ? 0 : wDp;
        Float y = isInput ? mInputPortY.get(portId) : mOutputPortY.get(portId);
        outPos[1] = (y != null) ? y : 0f;
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