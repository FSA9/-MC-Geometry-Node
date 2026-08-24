package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.agent.mcp.McpPowerShellRun;
import com.mine.geometry_node.client.ai.command.CommandCatalog;
import com.mine.geometry_node.client.terminal.TerminalSession;
import com.mine.geometry_node.client.terminal.TerminalMode;
import com.mine.geometry_node.client.terminal.TerminalSize;
import com.mine.geometry_node.client.terminal.shell.PowerShellProfile;
import com.mine.geometry_node.client.terminal.shell.ShellTerminalCoordinator;
import com.mine.geometry_node.client.terminal.pty.pty4j.Pty4jProcessFactory;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
import com.mine.geometry_node.client.ui.editor.terminal.command.BoundGraphQueryTarget;
import com.mine.geometry_node.client.ui.editor.terminal.command.MinecraftClientMcpGateway;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
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

    private final TerminalTabBar mTabBar;
    private final LinearLayout mModeBar;
    private final TextView mCommandMode;
    private final TextView mShellMode;
    private final FrameLayout mContainer;
    private final List<TerminalTab> mTabs = new ArrayList<>();
    private int mCurrentIndex = -1;
    private int mNextTerminalNumber = 1;
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

        mModeBar = new LinearLayout(context);
        mModeBar.setOrientation(HORIZONTAL);
        mModeBar.setGravity(Gravity.CENTER_VERTICAL);
        mModeBar.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(3), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(3));
        mModeBar.setBackground(colorDrawable(0xFF202020));
        mCommandMode = modeButton(context, "Command");
        mShellMode = modeButton(context, "PowerShell");
        mCommandMode.setOnClickListener(ignored -> switchMode(TerminalMode.COMMAND));
        mShellMode.setOnClickListener(ignored -> switchMode(TerminalMode.SHELL));
        mModeBar.addView(mCommandMode, new LayoutParams(UIUtils.dp2pxInt(92), ViewGroup.LayoutParams.MATCH_PARENT));
        mModeBar.addView(mShellMode, new LayoutParams(UIUtils.dp2pxInt(108), ViewGroup.LayoutParams.MATCH_PARENT));
        HorizontalScrollView modeScroller = new HorizontalScrollView(context);
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

        activeTab.requestInputFocus();
        if (activeTab.session.mode() == TerminalMode.SHELL) activeTab.shellView.ensureStarted();
        updateModeBar(activeTab.session.mode());
        captureSessionState();
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
    @Override public void onShow() { if (mCurrentIndex >= 0) mTabs.get(mCurrentIndex).requestInputFocus(); }
    @Override public void onHide() { captureSessionState(); }

    @Override
    public void onDispose() {
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
        ShellTerminalCoordinator coordinator = new ShellTerminalCoordinator(
                new TerminalSize(80, 24), new Pty4jProcessFactory());
        TerminalSession session = new TerminalSession(id, title, mode, profileId, coordinator);
        coordinator.bind(session);
        ShellTerminalView[] viewHolder = new ShellTerminalView[1];
        ShellTerminalView shellView = new ShellTerminalView(getContext(), coordinator,
                (size, generation) -> startMcpPowerShell(coordinator, viewHolder[0], size, generation));
        viewHolder[0] = shellView;
        return new TerminalTab(session, new ConsoleView(getContext()), shellView);
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

    private static void startMcpPowerShell(ShellTerminalCoordinator coordinator,
                                           ShellTerminalView view, TerminalSize size, long generation) {
        GraphSession graphSession = DocumentManager.INSTANCE.getActiveSession();
        if (graphSession == null) {
            view.reportError("请先打开一个蓝图，再启动 PowerShell");
            return;
        }
        var boundGraph = graphSession.editorContext.getCurrentGraph();
        var target = new BoundGraphQueryTarget(graphSession, boundGraph);
        var gateway = new MinecraftClientMcpGateway(CommandCatalog.registry(), target);
        Thread.ofVirtual().name("geometry-node-mcp-powershell-start").start(() -> {
            McpPowerShellRun run = null;
            try {
                run = McpPowerShellRun.start(CommandCatalog.registry(), gateway,
                        view::onTrustedToolEvent, size);
                if (!view.acceptsShellStart(generation)) {
                    run.close();
                    return;
                }
                coordinator.startManaged(run.launchSpec(), run);
                if (!view.acceptsShellStart(generation)) coordinator.stop();
            } catch (Exception failure) {
                if (run != null) run.close();
                if (!view.acceptsShellStart(generation)) return;
                String message = failure.getMessage();
                view.reportError(message == null || message.isBlank()
                        ? failure.getClass().getSimpleName() : message);
            }
        });
    }

    private static TextView modeButton(Context context, String text) {
        TextView view = UIUtils.createLockedTextView(context, text, 12f, 0xFFB8B8B8);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private static void styleModeButton(TextView view, boolean selected) {
        view.setTextColor(selected ? 0xFFFFFFFF : 0xFF999999);
        view.setBackground(colorDrawable(selected ? 0xFF3A3A3A : 0x00000000));
    }

    private static ShapeDrawable colorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static final class TerminalTab implements AutoCloseable {
        private final TerminalSession session;
        private final ConsoleView commandView;
        private final ShellTerminalView shellView;

        private TerminalTab(TerminalSession session, ConsoleView commandView, ShellTerminalView shellView) {
            this.session = session;
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
            session.close();
        }
    }

}
