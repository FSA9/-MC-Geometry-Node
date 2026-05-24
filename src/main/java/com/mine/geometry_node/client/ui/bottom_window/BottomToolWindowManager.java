package com.mine.geometry_node.client.ui.bottom_window;

import com.mine.geometry_node.client.ui.bottom_window.console.TerminalConsolePanel;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserPanel;

import java.util.HashMap;
import java.util.Map;

public class BottomToolWindowManager extends LinearLayout {

    private final ToolWindowStripe mStripe;
    private final FrameLayout mContainer;
    private final Map<ToolWindowType, IToolWindow> mActivatedWindows = new HashMap<>();
    private ToolWindowType mCurrentType = null;

    private static final int COLOR_BG = 0xFF252526;
    private static final int COLOR_CONTAINER_BG = 0xFF1E1E1E;

    public BottomToolWindowManager(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(COLOR_BG));

        mStripe = new ToolWindowStripe(context);
        addView(mStripe);

        mContainer = new FrameLayout(context);
        mContainer.setBackground(createColorDrawable(COLOR_CONTAINER_BG));

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        addView(mContainer, containerParams);

        mStripe.setOnToolWindowSelectedListener(this::handleWindowSelection);

        post(() -> mStripe.selectTab(ToolWindowType.ASSET_BROWSER));
    }

    private void handleWindowSelection(ToolWindowType type) {
        if (mCurrentType == type) return;

        if (mCurrentType != null) {
            IToolWindow currentWindow = mActivatedWindows.get(mCurrentType);
            if (currentWindow != null) {
                currentWindow.onHide();
                currentWindow.getView().setVisibility(View.GONE);
            }
        }

        IToolWindow targetWindow = mActivatedWindows.get(type);
        if (targetWindow == null) {
            targetWindow = createToolWindowLazy(type);
            if (targetWindow != null) {
                mActivatedWindows.put(type, targetWindow);
                mContainer.addView(targetWindow.getView(), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }

        if (targetWindow != null) {
            targetWindow.getView().setVisibility(View.VISIBLE);
            targetWindow.onShow();
        }

        mCurrentType = type;
    }

    private IToolWindow createToolWindowLazy(ToolWindowType type) {
        Context context = getContext();
        switch (type) {
            case ASSET_BROWSER:
                return new AssetBrowserPanel(context);
            case TERMINAL:
                return new TerminalConsolePanel(context);
            case PERFORMANCE:
                return createPlaceholderWindow(context, "性能监视器 (Performance Panel)");
            default:
                return null;
        }
    }

    private IToolWindow createPlaceholderWindow(Context context, String text) {
        FrameLayout layout = new FrameLayout(context);
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFF666666);
        tv.setGravity(Gravity.CENTER);
        layout.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return new IToolWindow() {
            @Override public View getView() { return layout; }
            @Override public void onShow() {}
            @Override public void onHide() {}
        };
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }
}