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

    private float mLastTouchX;
    private float mLastTouchY;
    private boolean mIsDragging = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = getContext();

        // 密度初始化
        if ("true".equals(System.getProperty("gn.standalone"))) {
            UIConstants.mDensity = 2.0f;
        } else {
            UIConstants.mDensity = context.getResources().getDisplayMetrics().density;
        }

        LinearLayout rootLayout = createRootLayout(context);
        setupHeader(context, rootLayout);
        setupMiddleSection(context, rootLayout);
        setupBottomSection(context, rootLayout);

        return rootLayout;
    }

    private LinearLayout createRootLayout(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));
        return root;
    }

    private void setupHeader(Context context, LinearLayout root) {
        RelativeLayout header = createPanel(context, "Header / Menu", UIConstants.MainUI.BG_HEADER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(UIConstants.MainUI.HEIGHT_HEADER) // 使用工具类
        );
        root.addView(header, params);
    }

    private void setupMiddleSection(Context context, LinearLayout root) {
        LinearLayout middleContainer = new LinearLayout(context);
        middleContainer.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        middleParams.weight = 1.0f;

        View leftPanel = createPanel(context, "Outliner", UIConstants.MainUI.BG_OUTLINER);
        middleContainer.addView(leftPanel, createWeightParams(UIConstants.MainUI.WEIGHT_LEFT));

        middleContainer.addView(createDraggableSplitter(context, true, null));

        com.mine.geometry_node.client.ui.Viewport.ViewportPanel centerPanel = new com.mine.geometry_node.client.ui.Viewport.ViewportPanel(context);
        middleContainer.addView(centerPanel, createWeightParams(UIConstants.MainUI.WEIGHT_CENTER));

        middleContainer.addView(createDraggableSplitter(context, true, null));

        View rightPanel = createPanel(context, "Properties", UIConstants.MainUI.BG_PROPERTIES);
        middleContainer.addView(rightPanel, createWeightParams(UIConstants.MainUI.WEIGHT_RIGHT));

        root.addView(middleContainer, middleParams);
    }

    private void setupBottomSection(Context context, LinearLayout root) {
        com.mine.geometry_node.client.ui.AssetLibrary.AssetBrowserPanel bottomPanel = new com.mine.geometry_node.client.ui.AssetLibrary.AssetBrowserPanel(context);

        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(UIConstants.MainUI.HEIGHT_BOTTOM_DEFAULT)
        );

        root.addView(createDraggableSplitter(context, false, bottomPanel));
        root.addView(bottomPanel, bottomParams);
    }

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

    private View createDraggableSplitter(Context context, boolean isVertical, View targetView) {
        FrameLayout container = new FrameLayout(context);
        int hitSize = UIUtils.dp2pxInt(UIConstants.MainUI.SPLITTER_HITBOX_SIZE);

        if (isVertical) {
            container.setLayoutParams(new LinearLayout.LayoutParams(hitSize, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            container.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hitSize));
        }

        View visualLine = new View(context);
        visualLine.setBackground(createColorDrawable(UIConstants.MainUI.BG_SPLITTER));
        int visualSize = UIUtils.dp2pxInt(UIConstants.MainUI.SPLITTER_VISUAL_SIZE);

        FrameLayout.LayoutParams lineParams = isVertical
                ? new FrameLayout.LayoutParams(visualSize, ViewGroup.LayoutParams.MATCH_PARENT)
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, visualSize);
        lineParams.gravity = Gravity.CENTER;

        container.addView(visualLine, lineParams);
        container.setOnTouchListener((v, event) -> handleSplitterTouch(v, event, isVertical, targetView));

        return container;
    }

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

    private void performHorizontalResize(View targetView, float dy) {
        if (targetView == null) return;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) targetView.getLayoutParams();
        params.height += (int) dy;

        int minHeight = UIUtils.dp2pxInt(UIConstants.MainUI.HEIGHT_BOTTOM_MIN);
        if (params.height < minHeight) params.height = minHeight;

        targetView.setLayoutParams(params);
    }

    private LinearLayout.LayoutParams createWeightParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        params.weight = weight;
        return params;
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("gn.standalone", "true");
        Configurator.setRootLevel(Level.DEBUG);

        com.mine.geometry_node.client.ui.persistence.ConfigManager.INSTANCE.initOrLoad();
        NodeRegistry.INSTANCE.init();

        try (ModernUI app = new ModernUI()) {
            app.run(new MainUI());
        }
        AudioManager.getInstance().close();
        System.gc();
    }
}