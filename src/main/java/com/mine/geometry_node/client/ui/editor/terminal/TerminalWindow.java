package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.area.AreaEditorWindow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

public class TerminalWindow extends LinearLayout implements AreaEditorWindow, TerminalTabBar.TabListener {

    private final TerminalTabBar mTabBar;
    private final FrameLayout mContainer;
    private final List<ConsoleView> mConsoleViews = new ArrayList<>();
    private int mCurrentIndex = -1;

    public TerminalWindow(Context context) {
        super(context);
        setOrientation(VERTICAL);

        mTabBar = new TerminalTabBar(context);
        mTabBar.setListener(this);
        addView(mTabBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28f)));

        mContainer = new FrameLayout(context);
        addView(mContainer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 默认开一个终端
        onTabCreated();
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= mConsoleViews.size()) return;
        mCurrentIndex = index;
        mContainer.removeAllViews();

        ConsoleView activeView = mConsoleViews.get(index);
        mContainer.addView(activeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 重新构建 TabBar UI
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < mConsoleViews.size(); i++) titles.add("Terminal " + (i + 1));
        mTabBar.rebuildTabs(titles, mCurrentIndex);

        activeView.requestInputFocus();
    }

    @Override public void onTabSelected(int index) { switchToTab(index); }

    @Override
    public void onTabClosed(int index) {
        if (mConsoleViews.size() <= 1) return; // 剩最后一个不让关
        mConsoleViews.remove(index);
        if (mCurrentIndex >= mConsoleViews.size()) {
            mCurrentIndex = mConsoleViews.size() - 1;
        }
        switchToTab(mCurrentIndex);
    }

    @Override
    public void onTabMoved(int fromIndex, int toIndex) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0
                || fromIndex >= mConsoleViews.size() || toIndex >= mConsoleViews.size()) {
            return;
        }

        ConsoleView movedView = mConsoleViews.remove(fromIndex);
        mConsoleViews.add(toIndex, movedView);

        if (mCurrentIndex == fromIndex) {
            mCurrentIndex = toIndex;
        } else if (mCurrentIndex > fromIndex && mCurrentIndex <= toIndex) {
            mCurrentIndex--;
        } else if (mCurrentIndex < fromIndex && mCurrentIndex >= toIndex) {
            mCurrentIndex++;
        }

        switchToTab(mCurrentIndex);
    }

    @Override
    public void onTabCreated() {
        ConsoleView newView = new ConsoleView(getContext());
        mConsoleViews.add(newView);
        switchToTab(mConsoleViews.size() - 1);
    }

    @Override public View getView() { return this; }
    @Override public void onShow() { if (mCurrentIndex >= 0) mConsoleViews.get(mCurrentIndex).requestInputFocus(); }
    @Override public void onHide() {}
}
