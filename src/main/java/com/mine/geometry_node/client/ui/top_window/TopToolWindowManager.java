package com.mine.geometry_node.client.ui.top_window;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.top_window.graph_editor.GraphEditorWindow;
import com.mine.geometry_node.client.ui.window.IToolWindow;
import com.mine.geometry_node.client.ui.window.ToolWindowStripe;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;

public class TopToolWindowManager extends LinearLayout {
    private final ToolWindowStripe<TopToolWindowType> mStripe;
    private final FrameLayout mContainer;
    private final Map<TopToolWindowType, IToolWindow> mActivatedWindows = new HashMap<>();
    private TopToolWindowType mCurrentType = null;

    public TopToolWindowManager(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(UIConstants.CLR_BG_DARK_3));

        mStripe = new ToolWindowStripe<>(context, TopToolWindowType.values());
        addView(mStripe);

        mContainer = new FrameLayout(context);
        mContainer.setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));
        addView(mContainer, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f));

        mStripe.setOnToolWindowSelectedListener(this::handleWindowSelection);
        post(() -> mStripe.selectTab(TopToolWindowType.GRAPH_EDITOR));
    }

    private void handleWindowSelection(TopToolWindowType type) {
        if (mCurrentType == type) {
            return;
        }

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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }

        if (targetWindow != null) {
            targetWindow.getView().setVisibility(View.VISIBLE);
            targetWindow.onShow();
        }

        mCurrentType = type;
    }

    private IToolWindow createToolWindowLazy(TopToolWindowType type) {
        if (type == TopToolWindowType.GRAPH_EDITOR) {
            return new GraphEditorWindow(getContext());
        }
        return null;
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }
}
