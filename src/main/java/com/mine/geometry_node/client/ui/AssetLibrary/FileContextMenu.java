package com.mine.geometry_node.client.ui.AssetLibrary;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import com.mine.geometry_node.client.ui.utils.UIUtils;

public class FileContextMenu extends FrameLayout {
    private final LinearLayout mContentLayout;

    public FileContextMenu(Context context) {
        super(context);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 点击外部透明区域关闭菜单
        setOnClickListener(v -> dismiss());

        mContentLayout = new LinearLayout(context);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);

        // 【关键修复】：拦截菜单本身的点击，防止事件穿透触发 dismiss()
        mContentLayout.setOnClickListener(v -> {});

        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(0xFF2D2D2D);
        bg.setCornerRadius(4);
        mContentLayout.setBackground(bg);
        mContentLayout.setPadding(4, 4, 4, 4);

        addView(mContentLayout);
    }

    public void addMenuItem(String text, Runnable action) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(0xFFCCCCCC);
        tv.setPadding(UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(6));

        tv.setOnClickListener(v -> {
            action.run();
            dismiss();
        });

        tv.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                ShapeDrawable hoverBg = new ShapeDrawable(); hoverBg.setColor(0xFF44AAFF);
                tv.setBackground(hoverBg);
                tv.setTextColor(0xFFFFFFFF);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                tv.setBackground(null);
                tv.setTextColor(0xFFCCCCCC);
            }
            return false;
        });

        mContentLayout.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void addDivider() {
        View divider = new View(getContext());
        ShapeDrawable line = new ShapeDrawable(); line.setColor(0xFF111111);
        divider.setBackground(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1));
        lp.setMargins(0, 4, 0, 4);
        mContentLayout.addView(divider, lp);
    }

    public void showAt(float x, float y, ViewGroup parent) {
        // 1. 强制进行预测量 (Measure)，获取菜单真实的宽高
        int widthSpec = MeasureSpec.makeMeasureSpec(UIUtils.dp2pxInt(160), MeasureSpec.EXACTLY); // 强制宽度 160dp，解决宽度乱跑问题
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED); // 高度由内容决定
        mContentLayout.measure(widthSpec, heightSpec);

        int menuWidth = mContentLayout.getMeasuredWidth();
        int menuHeight = mContentLayout.getMeasuredHeight();

        // 容错处理（防止部分框架第一帧测不出来）
        if (menuWidth == 0) menuWidth = UIUtils.dp2pxInt(160);
        if (menuHeight == 0) menuHeight = UIUtils.dp2pxInt(200);

        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();

        // 2. 边界碰撞检测算法 (类似于 Windows 菜单避让)
        float finalX = x;
        float finalY = y;

        // 如果右侧放不下了，往左翻折
        if (finalX + menuWidth > parentWidth && parentWidth > 0) {
            finalX = Math.max(0, parentWidth - menuWidth);
        }

        // 如果底部放不下了，往上翻折
        if (finalY + menuHeight > parentHeight && parentHeight > 0) {
            // 将菜单显示在鼠标的上方
            finalY = Math.max(0, y - menuHeight);
        }

        // 3. 应用修正后的坐标和尺寸
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.setMargins((int) finalX, (int) finalY, 0, 0);

        mContentLayout.setLayoutParams(lp);
        parent.addView(this);
    }

    public void dismiss() {
        if (getParent() != null) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }
}