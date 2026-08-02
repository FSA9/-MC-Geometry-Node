package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

public class TerminalWindow extends LinearLayout implements AreaEditorWindow, TerminalTabBar.TabListener {
    private static final int MAX_SESSION_TABS = 32;

    private final TerminalTabBar mTabBar;
    private final FrameLayout mContainer;
    private final List<ConsoleView> mConsoleViews = new ArrayList<>();
    private int mCurrentIndex = -1;
    private final EditorSessionState.TerminalState mSessionState;
    private final Runnable mSessionChanged;
    private boolean mInitializing;

    public TerminalWindow(Context context) {
        this(context, new EditorSessionState.TerminalState(), null);
    }

    public TerminalWindow(
            Context context,
            EditorSessionState.TerminalState sessionState,
            Runnable sessionChanged) {
        super(context);
        mSessionState = sessionState == null ? new EditorSessionState.TerminalState() : sessionState;
        mSessionChanged = sessionChanged;
        mInitializing = true;
        setOrientation(VERTICAL);

        mTabBar = new TerminalTabBar(context);
        mTabBar.setListener(this);
        addView(mTabBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28f)));

        mContainer = new FrameLayout(context);
        addView(mContainer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int tabCount = Math.max(1, Math.min(MAX_SESSION_TABS, mSessionState.tabCount));
        for (int i = 0; i < tabCount; i++) {
            mConsoleViews.add(new ConsoleView(getContext()));
        }
        switchToTab(Math.max(0, Math.min(tabCount - 1, mSessionState.activeTab)));
        mInitializing = false;
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
        captureSessionState();
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
        if (mConsoleViews.size() >= MAX_SESSION_TABS) return;
        ConsoleView newView = new ConsoleView(getContext());
        mConsoleViews.add(newView);
        switchToTab(mConsoleViews.size() - 1);
    }

    @Override public View getView() { return this; }
    @Override public void onShow() { if (mCurrentIndex >= 0) mConsoleViews.get(mCurrentIndex).requestInputFocus(); }
    @Override public void onHide() { captureSessionState(); }

    private void captureSessionState() {
        if (mInitializing) return;
        mSessionState.tabCount = Math.max(1, mConsoleViews.size());
        mSessionState.activeTab = Math.max(0, mCurrentIndex);
        if (mSessionChanged != null) {
            mSessionChanged.run();
        }
    }
}
