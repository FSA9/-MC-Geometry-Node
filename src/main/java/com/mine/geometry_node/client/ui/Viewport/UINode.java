// --- START OF FILE UINode.java ---
package com.mine.geometry_node.client.ui.Viewport;

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

import java.util.HashMap;
import java.util.Map;

public class UINode extends FrameLayout {

    // --- 布局尺寸常量 ---
    private static final int NODE_WIDTH = 160;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 20;

    // --- 绘制参数常量 ---
    private static final float PORT_RADIUS = 5.0f;
    private static final float PORT_TOUCH_RADIUS = 16.0f;
    private static final float CORNER_RADIUS = 6.0f;
    private static final float STROKE_WIDTH_NORMAL = 1.5f;
    private static final float STROKE_WIDTH_SELECTED = 2.5f;

    // --- 间距与文本常量 ---
    private static final int LABEL_MARGIN_PORT = 14;
    private static final int TEXT_SIZE_HEADER = 12;
    private static final int TEXT_SIZE_LABEL = 11;

    // --- 颜色配置 ---
    private static final int COLOR_BODY = 0xEE2A2A2A;
    private static final int COLOR_OUTLINE = 0xFF111111;
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_TEXT_LABEL = 0xFFDDDDDD;
    private static final int COLOR_TEXT_HEADER = 0xFFFFFFFF;

    private final NodeData mNodeData;
    private final NodeDef mNodeDef;
    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();

    private boolean mIsSelected = false;
    private int mTotalHeight;

    // 绝对坐标缓存池
    private final Map<String, Float> mInputPortY = new HashMap<>();
    private final Map<String, Float> mOutputPortY = new HashMap<>();

    // 文本视图缓存
    private TextView mTitleView;
    private final Map<String, TextView> mPortLabels = new HashMap<>();

    // 【重要修改】现在存放的是通用的 View，因为里面会有 CheckBox, EditText 等不同控件
    private final Map<Integer, View> mHintViews = new HashMap<>();

    private final int[] mTmpHintOnScreen = new int[2];

    public record DynamicActionInfo(boolean isAdd, String referencePortId) {}

    public UINode(Context context, NodeData nodeData, NodeDef nodeDef) {
        super(context);
        this.mNodeData = nodeData;
        this.mNodeDef = nodeDef;

        setWillNotDraw(false);
        setClipChildren(false);
        mPaint.setAntiAlias(true);

        buildUIElements(context);
        updateNodeLayout();
    }

    private void buildUIElements(Context context) {
        // 1. 标题
        mTitleView = new TextView(context);
        mTitleView.setText(mNodeDef.displayName().getString());
        mTitleView.setTextColor(COLOR_TEXT_HEADER);
        mTitleView.setTextSize(TEXT_SIZE_HEADER);
        mTitleView.setGravity(icyllis.modernui.view.Gravity.CENTER);
        mTitleView.setClickable(false);
        mTitleView.setFocusable(false);
        mTitleView.setLongClickable(false);
        addView(mTitleView, new LayoutParams(LayoutParams.MATCH_PARENT, HEADER_HEIGHT));

        // 2. 遍历行，构建标签与真实交互控件
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);

            // 左端口标签
            if (row.leftPort() != null) {
                TextView tv = createLabel(context, row.leftPort().displayName().getString(), icyllis.modernui.view.Gravity.LEFT);
                mPortLabels.put(row.leftPort().id(), tv);
                addView(tv, new LayoutParams(LayoutParams.WRAP_CONTENT, ROW_HEIGHT));
            }
            // 右端口标签
            if (row.rightPort() != null) {
                TextView tv = createLabel(context, row.rightPort().displayName().getString(), icyllis.modernui.view.Gravity.RIGHT);
                mPortLabels.put(row.rightPort().id(), tv);
                addView(tv, new LayoutParams(LayoutParams.WRAP_CONTENT, ROW_HEIGHT));
            }

            // 【重要修改】为 SELECT, INPUT, CHECKBOX 创建真实的交互组件
            UIHint hint = row.uiHint();
            if (hint != null) {
                String propKey = row.hintParams() != null ? (String) row.hintParams().get("property_key") : null;
                Object val = propKey != null ? mNodeData.properties.get(propKey) : null;
                if (val == null && row.leftPort() != null) val = row.leftPort().defaultValue();
                final String finalPropKey = propKey; // Lambda需要 effectively final

                if (hint == UIHint.CHECKBOX) {
                    CheckBox cb = new CheckBox(context);
                    cb.setChecked(String.valueOf(val).equalsIgnoreCase("true"));
                    // 绑定真实交互：勾选改变时，直接写入数据节点
                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (finalPropKey != null) {
                            mNodeData.properties.put(finalPropKey, isChecked);
                        }
                    });
                    mHintViews.put(i, cb);
                    addView(cb, new LayoutParams(0, 0));
                }
                else if (hint == UIHint.INPUT) {
                    EditText et = new EditText(context);
                    et.setText(val != null ? val.toString() : "");
                    et.setTextColor(COLOR_TEXT_LABEL);
                    et.setTextSize(TEXT_SIZE_LABEL);
//                    et.setBackgroundColor(0xFF1E1E1E); // 模拟原有的暗色底
                    // 绑定真实交互：失去焦点时保存数据
                    et.setOnFocusChangeListener((v, hasFocus) -> {
                        if (!hasFocus && finalPropKey != null) {
                            mNodeData.properties.put(finalPropKey, et.getText().toString());
                        }
                    });
                    mHintViews.put(i, et);
                    addView(et, new LayoutParams(0, 0));
                }
                else if (hint == UIHint.SELECT) {
                    TextView selectBtn = new TextView(context);
                    selectBtn.setText(val != null ? val.toString() + " ▼" : " ▼"); // 加个小箭头示意
                    selectBtn.setTextColor(COLOR_TEXT_LABEL);
                    selectBtn.setTextSize(TEXT_SIZE_LABEL);
                    selectBtn.setGravity(icyllis.modernui.view.Gravity.CENTER);
//                    selectBtn.setBackgroundColor(0xFF1E1E1E);
                    // 绑定真实交互：点击弹出菜单（这里留出 Todo 接口，你可以接你的 PopupWindow）
                    selectBtn.setOnClickListener(v -> {
                        // TODO: 弹出下拉菜单列表，选完后更新 text 并写入 mNodeData.properties
                    });
                    mHintViews.put(i, selectBtn);
                    addView(selectBtn, new LayoutParams(0, 0));
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

    public void updateNodeLayout() {
        mInputPortY.clear();
        mOutputPortY.clear();

        float currentY = HEADER_HEIGHT;
        for (int i = 0; i < mNodeDef.rows().size(); i++) {
            PortRow row = mNodeDef.rows().get(i);
            float portCenterY = currentY + ROW_HEIGHT / 2.0f;
            float portCenterYDp = portCenterY / UIConstants.mDensity;

            // 排版左侧标签
            if (row.leftPort() != null) {
                mInputPortY.put(row.leftPort().id(), portCenterYDp);
                TextView tv = mPortLabels.get(row.leftPort().id());
                if (tv != null) {
                    LayoutParams lp = (LayoutParams) tv.getLayoutParams();
                    int leftMargin = LABEL_MARGIN_PORT;
                    if (row.uiHint() == UIHint.CHECKBOX) leftMargin += 16;
                    lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
                    lp.leftMargin = leftMargin;
                    lp.topMargin = (int) currentY;
                    tv.setLayoutParams(lp);
                    tv.setTranslationX(0); tv.setTranslationY(0);
                }
            }

            // 排版右侧标签
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

            // 【重要修改】排版真实的交互控件
            UIHint hint = row.uiHint();
            View hintView = mHintViews.get(i);
            if (hintView != null) {
                if (hint == UIHint.CHECKBOX) {
                    LayoutParams lp = (LayoutParams) hintView.getLayoutParams();
                    lp.width = LayoutParams.WRAP_CONTENT;
                    lp.height = LayoutParams.WRAP_CONTENT;
                    lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
                    lp.leftMargin = LABEL_MARGIN_PORT;
                    lp.topMargin = (int) currentY; // CheckBox 自带一定高度，不用额外加偏移
                    hintView.setLayoutParams(lp);
                }
                else if (hint == UIHint.SELECT || hint == UIHint.INPUT) {
                    float startX = (row.leftPort() != null) ? (NODE_WIDTH * 0.45f) : LABEL_MARGIN_PORT;
                    float endX = NODE_WIDTH - ((row.rightPort() != null) ? 40 : LABEL_MARGIN_PORT);

                    LayoutParams lp = (LayoutParams) hintView.getLayoutParams();
                    lp.width = (int) (endX - startX);
                    lp.height = ROW_HEIGHT - 6;
                    lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
                    lp.leftMargin = (int) startX;
                    lp.topMargin = (int) currentY + 3;
                    hintView.setLayoutParams(lp);
                }
            }

            currentY += ROW_HEIGHT;
        }

        mTotalHeight = (int) currentY;
        setLayoutParams(new LayoutParams(NODE_WIDTH, mTotalHeight));
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float d = UIConstants.mDensity;
        float lx = ev.getX() / d;
        float ly = ev.getY() / d;
        if (hitTestPort(lx, ly, true) != null || hitTestPort(lx, ly, false) != null) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() > 0 ? getWidth() : NODE_WIDTH * UIConstants.mDensity;
        float h = getHeight() > 0 ? getHeight() : mTotalHeight;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_BODY);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, CORNER_RADIUS, (int) CORNER_RADIUS, mPaint);

        canvas.save();
        canvas.clipRect(0, 0, w, HEADER_HEIGHT);
        mPaint.setColor(mNodeDef.category().getColor());
        canvas.drawRoundRect(0, 0, w, HEADER_HEIGHT + CORNER_RADIUS, CORNER_RADIUS, (int) CORNER_RADIUS, mPaint);
        canvas.restore();

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mIsSelected ? STROKE_WIDTH_SELECTED : STROKE_WIDTH_NORMAL);
        mPaint.setColor(mIsSelected ? COLOR_SELECTED : COLOR_OUTLINE);
        canvas.drawRoundRect(mTempRect, CORNER_RADIUS, (int) CORNER_RADIUS, mPaint);

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

    public void getPortPosition(String portId, boolean isInput, float[] outPos) {
        float wDp = getWidth() > 0 ? getWidth() / UIConstants.mDensity : NODE_WIDTH;
        outPos[0] = isInput ? 0 : wDp;
        Float y = isInput ? mInputPortY.get(portId) : mOutputPortY.get(portId);
        outPos[1] = (y != null) ? y : 0f;
    }

    public View findInteractiveViewAtScreen(float screenX, float screenY) {
        for (int row = mNodeDef.rows().size() - 1; row >= 0; row--) {
            View v = mHintViews.get(row);
            if (v == null) continue;
            v.getLocationOnScreen(mTmpHintOnScreen);
            int w = v.getWidth();
            int h = v.getHeight();
            if (screenX >= mTmpHintOnScreen[0] && screenX < mTmpHintOnScreen[0] + w
                    && screenY >= mTmpHintOnScreen[1] && screenY < mTmpHintOnScreen[1] + h) {
                return v;
            }
        }
        return null;
    }

    public View findInteractiveViewAt(float localX, float localY) {
        for (int row = mNodeDef.rows().size() - 1; row >= 0; row--) {
            View v = mHintViews.get(row);
            if (v == null) continue;
            int l = v.getLeft();
            int t = v.getTop();
            int r = v.getRight();
            int b = v.getBottom();
            if (localX >= l && localX < r && localY >= t && localY < b) {
                return v;
            }
        }
        return null;
    }

    public String hitTestPort(float localX, float localY, boolean checkInput) {
        float wDp = getWidth() > 0 ? getWidth() / UIConstants.mDensity : NODE_WIDTH;
        float targetX = checkInput ? 0 : wDp;
        float dx = localX - targetX;
        Map<String, Float> map = checkInput ? mInputPortY : mOutputPortY;
        float thresholdSq = PORT_TOUCH_RADIUS * PORT_TOUCH_RADIUS;
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

    public NodeData getNodeData() { return mNodeData; }
    public NodeDef getNodeDef() { return mNodeDef; }

    public void setSelected(boolean selected) {
        if (mIsSelected != selected) {
            mIsSelected = selected;
            invalidate();
        }
    }
    public boolean isSelected() { return mIsSelected; }
}
// --- END OF FILE ---