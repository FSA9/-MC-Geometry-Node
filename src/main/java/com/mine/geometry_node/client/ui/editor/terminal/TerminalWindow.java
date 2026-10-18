package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.ui.components.scroll.UiHorizontalScrollView;

import com.mine.geometry_node.client.integration.terminal.TerminalRuntime;
import com.mine.geometry_node.client.integration.terminal.TerminalRuntimeFactory;
import com.mine.geometry_node.client.terminal.TerminalSession;
import com.mine.geometry_node.client.terminal.TerminalMode;
import com.mine.geometry_node.client.terminal.shell.PowerShellProfile;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.button.UiActionButton;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.persistence.EditorSessionState;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TerminalWindow extends LinearLayout implements AreaEditorWindow, TerminalTabBar.TabListener {
    private static final int MAX_SESSION_TABS = 32;
    private static final float TAB_SCROLL_MULTIPLIER = 80.0f;

    private final HorizontalScrollView mTabScrollView;
    private final TerminalTabBar mTabBar;
    private final LinearLayout mModeBar;
    private final UiActionButton mCommandMode;
    private final UiActionButton mShellMode;
    private final FrameLayout mContainer;
    private final List<TerminalTab> mTabs = new ArrayList<>();
    private int mCurrentIndex = -1;
    private int mNextTerminalNumber = 1;
    private final EditorSessionState.TerminalState mSessionState;
    private final Runnable mSessionChanged;
    private final TerminalRuntimeFactory mRuntimeFactory;
    private boolean mInitializing;
    private boolean mWindowActive;

    public TerminalWindow(
            Context context,
            EditorSessionState.TerminalState sessionState,
            Runnable sessionChanged,
            TerminalRuntimeFactory runtimeFactory) {
        super(context);
        mSessionState = sessionState == null ? new EditorSessionState.TerminalState() : sessionState;
        mSessionChanged = sessionChanged;
        mRuntimeFactory = java.util.Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        mInitializing = true;
        setOrientation(VERTICAL);

        mTabScrollView = new UiHorizontalScrollView(context);
        mTabScrollView.setFillViewport(true);
        mTabScrollView.setHorizontalScrollBarEnabled(false);
        mTabScrollView.setBackground(colorDrawable(UiTheme.Surface.PANEL));
        mTabScrollView.setOnGenericMotionListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_SCROLL) return false;
            float verticalScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (verticalScroll == 0f) return false;
            mTabScrollView.scrollBy((int) (-verticalScroll * TAB_SCROLL_MULTIPLIER), 0);
            return true;
        });

        mTabBar = new TerminalTabBar(context);
        mTabBar.setListener(this);
        mTabScrollView.addView(mTabBar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mTabScrollView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28f)));

        mModeBar = new LinearLayout(context);
        mModeBar.setOrientation(HORIZONTAL);
        mModeBar.setGravity(Gravity.CENTER_VERTICAL);
        mModeBar.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(3), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(3));
        mModeBar.setBackground(colorDrawable(UiTheme.Control.INPUT));
        mCommandMode = TerminalChrome.action(context, TerminalChrome.tr("mode.command"));
        mShellMode = TerminalChrome.action(context, TerminalChrome.tr("mode.shell"));
        mCommandMode.setOnClickListener(ignored -> switchMode(TerminalMode.COMMAND));
        mShellMode.setOnClickListener(ignored -> switchMode(TerminalMode.SHELL));
        mModeBar.addView(mCommandMode, new LayoutParams(UIUtils.dp2pxInt(92), ViewGroup.LayoutParams.MATCH_PARENT));
        mModeBar.addView(mShellMode, new LayoutParams(UIUtils.dp2pxInt(108), ViewGroup.LayoutParams.MATCH_PARENT));
        HorizontalScrollView modeScroller = new UiHorizontalScrollView(context);
        modeScroller.setFillViewport(true);
        modeScroller.setHorizontalScrollBarEnabled(false);
        modeScroller.addView(mModeBar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(modeScroller, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

        mContainer = new FrameLayout(context);
        addView(mContainer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        List<EditorSessionState.TerminalTabState> savedTabs = mSessionState.tabs;
        int savedTabCount = savedTabs == null || savedTabs.isEmpty() ? mSessionState.tabCount : savedTabs.size();
        int tabCount = Math.max(1, Math.min(MAX_SESSION_TABS, savedTabCount));
        for (int i = 0; i < tabCount; i++) {
            EditorSessionState.TerminalTabState saved = savedTabs != null && i < savedTabs.size()
                    ? savedTabs.get(i)
                    : null;
            mTabs.add(createTerminalTab(saved));
        }
        switchToTab(Math.max(0, Math.min(tabCount - 1, mSessionState.activeTab)));
        mInitializing = false;
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= mTabs.size()) return;
        for (TerminalTab tab : mTabs) tab.shellView.deactivate();
        mCurrentIndex = index;
        mContainer.removeAllViews();

        TerminalTab activeTab = mTabs.get(index);
        View activeView = activeTab.activeView();
        mContainer.addView(activeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 重新构建 TabBar UI
        List<String> titles = new ArrayList<>();
        for (TerminalTab tab : mTabs) {
            titles.add(tab.session.title());
        }
        mTabBar.rebuildTabs(titles, mCurrentIndex);
        mTabScrollView.post(this::scrollActiveTabIntoView);

        if (activeTab.session.mode() == TerminalMode.SHELL) {
            if (mWindowActive) activeTab.shellView.activate();
            activeTab.shellView.ensureStarted();
        }
        activeTab.requestInputFocus();
        updateModeBar(activeTab.session.mode());
        captureSessionState();
    }

    private void scrollActiveTabIntoView() {
        View activeTab = mTabBar.getTabView(mCurrentIndex);
        if (activeTab == null || mTabScrollView.getWidth() <= 0) return;

        int visibleLeft = mTabScrollView.getScrollX();
        int visibleRight = visibleLeft + mTabScrollView.getWidth();
        int targetLeft = activeTab.getLeft();
        int targetRight = activeTab.getRight();
        if (mCurrentIndex == mTabs.size() - 1) {
            View addButton = mTabBar.getAddButtonView();
            if (addButton != null) targetRight = addButton.getRight();
        }

        if (targetLeft < visibleLeft) {
            mTabScrollView.scrollTo(targetLeft, 0);
        } else if (targetRight > visibleRight) {
            mTabScrollView.scrollTo(targetRight - mTabScrollView.getWidth(), 0);
        }
    }

    @Override public void onTabSelected(int index) { switchToTab(index); }

    @Override
    public void onTabClosed(int index) {
        if (mTabs.size() <= 1 || index < 0 || index >= mTabs.size()) return; // 剩最后一个不让关
        mTabs.remove(index).close();
        if (mCurrentIndex > index) {
            mCurrentIndex--;
        } else if (mCurrentIndex >= mTabs.size()) {
            mCurrentIndex = mTabs.size() - 1;
        }
        switchToTab(mCurrentIndex);
    }

    @Override
    public void onTabMoved(int fromIndex, int toIndex) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0
                || fromIndex >= mTabs.size() || toIndex >= mTabs.size()) {
            return;
        }

        TerminalTab movedTab = mTabs.remove(fromIndex);
        mTabs.add(toIndex, movedTab);

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
        if (mTabs.size() >= MAX_SESSION_TABS) return;
        mTabs.add(createTerminalTab(null));
        switchToTab(mTabs.size() - 1);
    }

    @Override public View getView() { return this; }
    @Override
    public void onShow() {
        mWindowActive = true;
        if (mCurrentIndex >= 0) {
            TerminalTab activeTab = mTabs.get(mCurrentIndex);
            if (activeTab.session.mode() == TerminalMode.SHELL) activeTab.shellView.activate();
            activeTab.requestInputFocus();
        }
    }
    @Override
    public void onHide() {
        mWindowActive = false;
        captureSessionState();
        if (mCurrentIndex >= 0 && mCurrentIndex < mTabs.size()) {
            mTabs.get(mCurrentIndex).shellView.deactivate();
        }
    }

    @Override
    public void onDispose() {
        mWindowActive = false;
        mTabBar.setListener(null);
        captureSessionState();
        for (TerminalTab tab : mTabs) {
            tab.close();
        }
        mTabs.clear();
        mContainer.removeAllViews();
    }

    private TerminalTab createTerminalTab(EditorSessionState.TerminalTabState saved) {
        int number = mNextTerminalNumber++;
        UUID id = parseSessionId(saved == null ? null : saved.id);
        String title = saved == null || saved.title == null || saved.title.isBlank()
                ? "Terminal " + number
                : saved.title;
        TerminalMode mode = parseMode(saved == null ? null : saved.mode);
        String profileId = saved == null || saved.profileId == null || saved.profileId.isBlank()
                ? PowerShellProfile.ID : saved.profileId;
        TerminalRuntime runtime = mRuntimeFactory.create(id, title, mode, profileId);
        ShellTerminalView shellView = new ShellTerminalView(getContext(), runtime);
        return new TerminalTab(runtime, new ConsoleView(getContext()), shellView);
    }

    private void captureSessionState() {
        if (mInitializing) return;
        mSessionState.tabCount = Math.max(1, mTabs.size());
        mSessionState.activeTab = Math.max(0, mCurrentIndex);
        mSessionState.tabs = new ArrayList<>();
        for (TerminalTab tab : mTabs) {
            EditorSessionState.TerminalTabState saved = new EditorSessionState.TerminalTabState();
            saved.id = tab.session.id().toString();
            saved.title = tab.session.title();
            saved.mode = tab.session.mode().name();
            saved.profileId = tab.session.profileId();
            mSessionState.tabs.add(saved);
        }
        if (mSessionChanged != null) {
            mSessionChanged.run();
        }
    }

    private static UUID parseSessionId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return UUID.randomUUID();
        }
    }

    private static TerminalMode parseMode(String value) {
        try {
            TerminalMode mode = TerminalMode.valueOf(value);
            return mode == TerminalMode.SHELL ? mode : TerminalMode.COMMAND;
        } catch (RuntimeException ignored) {
            return TerminalMode.COMMAND;
        }
    }

    private void switchMode(TerminalMode mode) {
        if (mCurrentIndex < 0) return;
        TerminalTab tab = mTabs.get(mCurrentIndex);
        if (tab.session.mode() == mode) return;
        if (tab.session.state().hasActiveBackend() || tab.shellView.isStartPending()) {
            tab.shellView.reportError("请先停止当前终端会话，再切换模式");
            return;
        }
        tab.session.setMode(mode);
        if (mode == TerminalMode.SHELL) tab.session.setProfileId(PowerShellProfile.ID);
        switchToTab(mCurrentIndex);
    }

    private void updateModeBar(TerminalMode mode) {
        styleModeButton(mCommandMode, mode == TerminalMode.COMMAND);
        styleModeButton(mShellMode, mode == TerminalMode.SHELL);
    }

    private static void styleModeButton(UiActionButton view, boolean selected) {
        view.setSelected(selected);
        view.setPalette(TerminalChrome.palette(selected));
    }

    private static ShapeDrawable colorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static final class TerminalTab implements AutoCloseable {
        private final TerminalSession session;
        private final TerminalRuntime runtime;
        private final ConsoleView commandView;
        private final ShellTerminalView shellView;

        private TerminalTab(TerminalRuntime runtime, ConsoleView commandView, ShellTerminalView shellView) {
            this.runtime = runtime;
            this.session = runtime.session();
            this.commandView = commandView;
            this.shellView = shellView;
        }

        private View activeView() { return session.mode() == TerminalMode.COMMAND ? commandView : shellView; }

        private void requestInputFocus() {
            if (session.mode() != TerminalMode.COMMAND) shellView.requestInputFocus();
            else commandView.requestInputFocus();
        }

        @Override
        public void close() {
            shellView.dispose();
            runtime.close();
        }
    }

}
