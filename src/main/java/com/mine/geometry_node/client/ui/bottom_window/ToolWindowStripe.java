package com.mine.geometry_node.client.ui.bottom_window;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import com.mine.geometry_node.client.ui.utils.UIUtils;

import java.util.HashMap;
import java.util.Map;

public class ToolWindowStripe extends LinearLayout {

    // 🎛️ UI 尺寸与布局参数
    private static final float STRIPE_WIDTH = 32.0f;  // 侧边栏总宽度
    private static final float ITEM_HEIGHT = 32.0f;   // 每个方块的高度
    private static final float ICON_SIZE = 16.0f;     // 字体图标的大小

    // 🎨 IDE 经典配色方案
    private static final int COLOR_BG = 0xFF252526;           // 条带底色
    private static final int COLOR_ICON_NORMAL = 0xFF858585;  // 默认灰色
    private static final int COLOR_ICON_HOVER = 0xFFCCCCCC;   // 悬浮亮灰
    private static final int COLOR_ICON_SELECTED = 0xFFFFFFFF;// 选中纯白
    private static final int COLOR_BG_SELECTED = 0xFF37373D;  // 选中时的背景高亮

    public interface OnToolWindowSelectedListener {
        void onSelected(ToolWindowType type);
    }

    private OnToolWindowSelectedListener mListener;
    private ToolWindowType mCurrentSelectedType = null;
    private final Map<ToolWindowType, TextView> mTabViews = new HashMap<>();

    public ToolWindowStripe(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(createColorDrawable(COLOR_BG));

        setLayoutParams(new ViewGroup.LayoutParams(
                UIUtils.dp2pxInt(STRIPE_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        for (ToolWindowType type : ToolWindowType.values()) {
            TextView tabBtn = createTabButton(context, type);
            mTabViews.put(type, tabBtn);
            addView(tabBtn, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(ITEM_HEIGHT)));
        }
    }

    public void setOnToolWindowSelectedListener(OnToolWindowSelectedListener listener) {
        mListener = listener;
    }

    public void selectTab(ToolWindowType type) {
        if (mCurrentSelectedType == type) {
            return;
        }

        mCurrentSelectedType = type;
        updateTabsUI();

        if (mListener != null) {
            mListener.onSelected(type);
        }
    }

    private TextView createTabButton(Context context, ToolWindowType type) {
        TextView btn = new TextView(context);

        btn.setText(type.getIconChar());
        btn.setTextSize(TypedValue.COMPLEX_UNIT_PX, UIUtils.dp2px(ICON_SIZE));
        btn.setGravity(Gravity.CENTER);
        btn.setTextColor(COLOR_ICON_NORMAL);

        btn.setOnHoverListener((v, event) -> {
            if (mCurrentSelectedType != type) {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    btn.setTextColor(COLOR_ICON_HOVER);
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    btn.setTextColor(COLOR_ICON_NORMAL);
                }
            }
            return true;
        });

        btn.setOnClickListener(v -> selectTab(type));

        return btn;
    }

    private void updateTabsUI() {
        for (Map.Entry<ToolWindowType, TextView> entry : mTabViews.entrySet()) {
            ToolWindowType type = entry.getKey();
            TextView view = entry.getValue();

            if (type == mCurrentSelectedType) {
                view.setTextColor(COLOR_ICON_SELECTED);
                view.setBackground(createColorDrawable(COLOR_BG_SELECTED));
            } else {
                view.setTextColor(COLOR_ICON_NORMAL);
                view.setBackground(null);
            }
        }
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }
}