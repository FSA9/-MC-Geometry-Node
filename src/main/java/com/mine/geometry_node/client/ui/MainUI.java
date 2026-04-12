package com.mine.geometry_node.client.ui;

import com.mine.geometry_node.client.ui.Viewport.Viewport;
import com.mine.geometry_node.core.node.NodeRegistry;
import icyllis.modernui.ModernUI;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RelativeLayout;
import icyllis.modernui.widget.TextView;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class MainUI extends Fragment {

    // --- 状态变量 ---
    private float mLastTouchX;
    private float mLastTouchY;
    private boolean mIsDragging = false;

    // ==========================================
    // 生命周期与核心初始化
    // ==========================================

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = getContext();

        // 1. 创建根布局 (垂直排列)
        LinearLayout rootLayout = createRootLayout(context);

        // 2. 组装：顶部标题栏
        setupHeader(context, rootLayout);

        // 3. 组装：中间主体区域 (左右分栏)
        setupMiddleSection(context, rootLayout);

        // 4. 组装：底部区域 (状态栏/资产栏)
        setupBottomSection(context, rootLayout);

        return rootLayout;
    }

    // ==========================================
    // 顶级 UI 组装逻辑
    // ==========================================

    /**
     * 初始化根容器
     */
    private LinearLayout createRootLayout(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));
        return root;
    }

    /**
     * 构建并添加顶部 Header
     */
    private void setupHeader(Context context, LinearLayout root) {
        RelativeLayout header = createPanel(context, "Header / Menu", UIConstants.MainUI.BG_HEADER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(UIConstants.MainUI.HEIGHT_HEADER)
        );
        root.addView(header, params);
    }

    /**
     * 构建并添加中间区域 (包含 Outliner, Viewport, Properties)
     */
    private void setupMiddleSection(Context context, LinearLayout root) {
        LinearLayout middleContainer = new LinearLayout(context);
        middleContainer.setOrientation(LinearLayout.HORIZONTAL);

        // 占据剩余全部垂直高度
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        middleParams.weight = 1.0f;

        // A. 左侧大纲视图 (Outliner)
        View leftPanel = createPanel(context, "Outliner", UIConstants.MainUI.BG_OUTLINER);
        middleContainer.addView(leftPanel, createWeightParams(UIConstants.MainUI.WEIGHT_LEFT));

        // 分割线 1
        middleContainer.addView(createDraggableSplitter(context, true, null));

        // B. 中央 3D 视口 (Viewport)
        com.mine.geometry_node.client.ui.Viewport.ViewportPanel centerPanel = new com.mine.geometry_node.client.ui.Viewport.ViewportPanel(context);
        middleContainer.addView(centerPanel, createWeightParams(UIConstants.MainUI.WEIGHT_CENTER));

        // 分割线 2
        middleContainer.addView(createDraggableSplitter(context, true, null));

        // C. 右侧属性面板 (Properties)
        View rightPanel = createPanel(context, "Properties", UIConstants.MainUI.BG_PROPERTIES);
        middleContainer.addView(rightPanel, createWeightParams(UIConstants.MainUI.WEIGHT_RIGHT));

        root.addView(middleContainer, middleParams);
    }

    /**
     * 构建并添加底部区域 (包含水平分割线)
     */
    private void setupBottomSection(Context context, LinearLayout root) {
        com.mine.geometry_node.client.ui.AssetLibrary.AssetBrowserPanel bottomPanel = new com.mine.geometry_node.client.ui.AssetLibrary.AssetBrowserPanel(context);

        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(UIConstants.MainUI.HEIGHT_BOTTOM_DEFAULT)
        );

        root.addView(createDraggableSplitter(context, false, bottomPanel));
        root.addView(bottomPanel, bottomParams);
    }

    // ==========================================
    // UI 组件工厂
    // ==========================================

    /**
     * 创建一个带文字居中的基础面板
     */
    private RelativeLayout createPanel(Context context, String title, int colorHex) {
        RelativeLayout panel = new RelativeLayout(context);
        panel.setBackground(createColorDrawable(colorHex));

        TextView textView = new TextView(context);
        textView.setText(title);
        textView.setTextSize(UIConstants.MainUI.TEXT_SIZE);
        textView.setTextColor(UIConstants.MainUI.TEXT_COLOR);

        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        panel.addView(textView, textParams);
        return panel;
    }

    /**
     * 创建视口包装容器
     */
    private FrameLayout createViewportWrapper(Context context) {
        Viewport viewport = new Viewport(context);
        FrameLayout container = new FrameLayout(context);
        container.addView(viewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return container;
    }

    /**
     * 创建可拖拽的分割线组件
     * @param isVertical 是否为垂直线（用于左右调整宽度）
     * @param targetView 受控的目标 View（主要用于水平分割线控制高度）
     */
    private View createDraggableSplitter(Context context, boolean isVertical, View targetView) {
        FrameLayout container = new FrameLayout(context);
        int hitSize = dp(UIConstants.MainUI.SPLITTER_HITBOX_SIZE);

        // 设置容器（热区）大小
        if (isVertical) {
            container.setLayoutParams(new LinearLayout.LayoutParams(hitSize, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            container.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hitSize));
        }

        // 创建视觉线条
        View visualLine = new View(context);
        visualLine.setBackground(createColorDrawable(UIConstants.MainUI.BG_SPLITTER));
        int visualSize = dp(UIConstants.MainUI.SPLITTER_VISUAL_SIZE);

        FrameLayout.LayoutParams lineParams = isVertical
                ? new FrameLayout.LayoutParams(visualSize, ViewGroup.LayoutParams.MATCH_PARENT)
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, visualSize);
        lineParams.gravity = Gravity.CENTER;

        container.addView(visualLine, lineParams);
        container.setOnTouchListener((v, event) -> handleSplitterTouch(v, event, isVertical, targetView));

        return container;
    }

    // ==========================================
    // 交互逻辑与尺寸计算
    // ==========================================

    /**
     * 处理分割线触摸事件调度
     */
    private boolean handleSplitterTouch(View view, MotionEvent event, boolean isVertical, View targetView) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsDragging = true;
                mLastTouchX = event.getRawX();
                mLastTouchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!mIsDragging) return false;

                float rawX = event.getRawX();
                float rawY = event.getRawY();
                float dx = rawX - mLastTouchX;
                float dy = rawY - mLastTouchY;

                if (isVertical) {
                    performVerticalResize(view, dx);
                } else {
                    performHorizontalResize(targetView, -dy);
                }

                mLastTouchX = rawX;
                mLastTouchY = rawY;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                return true;
        }
        return false;
    }

    /**
     * 执行垂直方向的分割线逻辑 (调整左右 Weight)
     */
    private void performVerticalResize(View splitter, float dx) {
        ViewGroup parent = (ViewGroup) splitter.getParent();
        if (!(parent instanceof LinearLayout)) return;

        int index = parent.indexOfChild(splitter);

        if (index > 0 && index < parent.getChildCount() - 1) {
            View leftView = parent.getChildAt(index - 1);
            View rightView = parent.getChildAt(index + 1);

            LinearLayout.LayoutParams leftParams = (LinearLayout.LayoutParams) leftView.getLayoutParams();
            LinearLayout.LayoutParams rightParams = (LinearLayout.LayoutParams) rightView.getLayoutParams();

            if (leftParams.weight > 0 && rightParams.weight > 0) {
                float totalWeight = leftParams.weight + rightParams.weight;
                float totalWidth = leftView.getWidth() + rightView.getWidth();

                if (totalWidth <= 0) return;

                float dWeight = (dx / totalWidth) * totalWeight;
                leftParams.weight += dWeight;
                rightParams.weight -= dWeight;

                // 约束最小权重
                float minW = UIConstants.MainUI.WEIGHT_MIN;
                if (leftParams.weight < minW) {
                    rightParams.weight -= (minW - leftParams.weight);
                    leftParams.weight = minW;
                }
                if (rightParams.weight < minW) {
                    leftParams.weight -= (minW - rightParams.weight);
                    rightParams.weight = minW;
                }

                leftView.requestLayout();
                rightView.requestLayout();
            }
        }
    }

    /**
     * 执行水平方向的分割线逻辑 (调整目标 View 高度)
     */
    private void performHorizontalResize(View targetView, float dy) {
        if (targetView == null) return;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) targetView.getLayoutParams();
        params.height += (int) dy;

        int minHeight = dp(UIConstants.MainUI.HEIGHT_BOTTOM_MIN);
        if (params.height < minHeight) params.height = minHeight;

        targetView.setLayoutParams(params);
    }

    // ==========================================
    // 辅助工具方法
    // ==========================================

    /**
     * 快速创建带权重的水平布局参数
     */
    private LinearLayout.LayoutParams createWeightParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        params.weight = weight;
        return params;
    }

    /**
     * 创建纯色背景 Drawable
     */
    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
    }

    /**
     * 像素转换 (占位，可根据需要实现 DP 转 PX)
     */
    private int dp(int value) {
        return value;
    }

    /**
     * 程序入口
     */
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        Configurator.setRootLevel(Level.DEBUG);

        NodeRegistry.INSTANCE.init();

        try (ModernUI app = new ModernUI()) {
            app.run(new MainUI());
        }
        AudioManager.getInstance().close();
        System.gc();
    }
}