package com.mine.geometry_node.client.ui.editor.game;

import com.mine.geometry_node.client.runtime.game.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.EditViewportFlightInput;
import com.mine.geometry_node.client.runtime.game.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.GameViewTiming;
import com.mine.geometry_node.client.runtime.game.SharedGameFrame;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.common.ResizableDivider;
import com.mine.geometry_node.client.ui.components.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.SurfaceRegistrationAware;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

/** Workspace visibility owns subscriptions; each window retains its own edit camera. */
public final class GameViewportWindow extends LinearLayout implements AreaEditorWindow, SurfaceRegistrationAware {
    private final EditViewportCamera editCamera = new EditViewportCamera();
    private final GameViewportView viewport;
    private final TextView gameButton;
    private final TextView editButton;
    private final TextView flyButton;
    private final TextView projectionButton;
    private final TextView resetButton;
    private final TextView status;
    private final TextView waiting;
    private final SidebarLayoutController sidebarLayout;
    private final GameViewportSidebarPanel sidebarPanel;
    private UiSurfaceRegistry.Registration registration;
    private AreaLifecycle areaLifecycle;
    private AreaLifecycle.Subscription areaSubscription = AreaLifecycle.Subscription.NONE;
    private final ViewportModeState modes = new ViewportModeState(editCamera);
    private boolean shown;
    private boolean registered;
    private boolean disposed;
    private String statusKey;
    private String statusShortcut = "";
    private boolean toolOrthographic;
    private long nextSidebarRefresh;
    private long nextHintRefresh;
    private final Runnable refresh = this::refresh;
    private final Runnable refreshVisibility = this::updateVisibility;

    public GameViewportWindow(Context context) {
        super(context);
        long constructionStart = GameViewTiming.start();
        setOrientation(VERTICAL);
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(background(UiTheme.Surface.HEADER));
        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackground(background(UiTheme.Surface.HEADER));
        gameButton = action(context, "mode.game", () -> setMode(ViewportMode.GAME));
        editButton = action(context, "mode.edit", () -> setMode(ViewportMode.EDIT));
        flyButton = action(context, "mode.fly", this::selectFlightMode);
        projectionButton = action(context, "projection.perspective", this::toggleProjection);
        resetButton = action(context, "reset_camera", this::resetCamera);
        for (TextView button : new TextView[]{gameButton, editButton, flyButton, projectionButton, resetButton}) {
            tools.addView(button, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        HorizontalScrollView toolScroller = new HorizontalScrollView(context);
        toolScroller.setHorizontalScrollBarEnabled(false);
        toolScroller.setFillViewport(true);
        toolScroller.addView(tools, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header.addView(toolScroller, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = UIUtils.createLockedTextView(context, "", 10.0f, UiTheme.Text.MUTED);
        status.setSingleLine(true);
        status.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        status.setBackground(background(UiTheme.Surface.HEADER));
        header.addView(status, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));

        FrameLayout content = new FrameLayout(context);
        viewport = new GameViewportView(context, this, editCamera, this::markInteracted,
                this::updateTools, this::scheduleVisibilityRefresh);
        content.addView(viewport, new FrameLayout.LayoutParams(-1, -1));
        waiting = UIUtils.createLockedTextView(context, tr("waiting"), 12.0f, UiTheme.Text.MUTED);
        waiting.setPadding(UIUtils.dp2pxInt(12), 0, UIUtils.dp2pxInt(12), 0);
        waiting.setGravity(Gravity.CENTER);
        content.addView(waiting, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout workspace = new LinearLayout(context);
        workspace.setOrientation(HORIZONTAL);
        workspace.addView(content, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.75f));
        View divider = ResizableDivider.weighted(context, ResizableDivider.Orientation.HORIZONTAL);
        workspace.addView(divider);
        EditorSidebar sidebar = new EditorSidebar(context);
        sidebar.installRegisteredPanels(new SidebarPanelContext(context, SidebarPanelScope.GAME_VIEWPORT, this));
        sidebarPanel = sidebar.requirePanel(GameViewportSidebarPanel.PANEL_ID, GameViewportSidebarPanel.class);
        workspace.addView(sidebar, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.25f));
        sidebarLayout = new SidebarLayoutController(workspace, content, divider, sidebar, 0.25f, null);
        sidebar.setOnCollapseRequested(() -> sidebarLayout.setVisible(false, true));
        sidebarLayout.initialize(false);
        addView(workspace, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        updateTools();
        updateStatus(false);
        GameViewTiming.finish("window construction", constructionStart, true);
    }

    public ViewportMode mode() { return modes.current(); }
    void selectFlightMode() { setMode(ViewportMode.FLY); }
    @Override public View getView() { return this; }
    @Override public void bindSurfaceRegistration(UiSurfaceRegistry.Registration registration) { this.registration = registration; }
    @Override public void onShow() { shown = true; sidebarLayout.onOwnerShown(); updateVisibility(); }
    @Override public void onHide() { shown = false; sidebarLayout.onOwnerHidden(); updateVisibility(); }
    @Override public void onDispose() { disposed = true; onHide(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        areaSubscription.close();
        areaLifecycle = AreaLifecycle.find(this);
        if (areaLifecycle != null) areaSubscription = areaLifecycle.subscribe(this::onAreaEvent);
        updateVisibility();
    }
    @Override protected void onDetachedFromWindow() {
        areaSubscription.close();
        areaSubscription = AreaLifecycle.Subscription.NONE;
        areaLifecycle = null;
        deactivate();
        super.onDetachedFromWindow();
    }

    private void onAreaEvent(AreaLifecycle.Event event) {
        switch (event) {
            case ACTIVATED -> updatePreferred();
            case DEACTIVATED -> {
                viewport.releaseInteraction();
                updatePreferred();
            }
            case CONTENT_CHANGED, HIDDEN, DETACHED, DISPOSED -> deactivate();
        }
    }

    private void updatePreferred() {
        if (!Boolean.getBoolean("gn.standalone")) {
            EditViewportRuntime.setPreferred(this, registered && areaLifecycle != null && areaLifecycle.isActive());
        }
    }
    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (viewport != null) updateVisibility();
    }

    private void setMode(ViewportMode next) {
        if (mode() == next || disposed) return;
        deactivate();
        modes.select(next);
        markInteracted();
        updateTools();
        updateStatus(false);
        updateVisibility();
        viewport.requestFocus();
    }

    boolean isOrthographic() { return editCamera.isOrthographic(); }

    void resetCamera() {
        if (!mode().usesEditorCamera()) return;
        markInteracted();
        if (!Boolean.getBoolean("gn.standalone")) EditViewportRuntime.resetToPlayer(this);
    }

    void toggleProjection() {
        if (mode() != ViewportMode.EDIT) return;
        editCamera.toggleOrthographic();
        markInteracted();
        updateTools();
        viewport.invalidate();
    }

    private void markInteracted() {
        if (registration != null) registration.markInteracted();
        if (!Boolean.getBoolean("gn.standalone") && mode().usesEditorCamera()) EditViewportRuntime.touch(this);
    }

    private TextView action(Context context, String key, Runnable action) {
        TextView button = UIUtils.createLockedTextView(context, tr(key), 11.0f, UiTheme.Text.MUTED);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(9), 0);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(ignored -> action.run());
        return button;
    }

    private void updateTools() {
        toolOrthographic = editCamera.isOrthographic();
        boolean editing = mode() == ViewportMode.EDIT;
        gameButton.setTextColor(mode() == ViewportMode.GAME ? UiTheme.Control.FOCUS : UiTheme.Text.MUTED);
        flyButton.setTextColor(mode() == ViewportMode.FLY ? UiTheme.Control.FOCUS : UiTheme.Text.MUTED);
        editButton.setTextColor(editing ? UiTheme.Control.FOCUS : UiTheme.Text.MUTED);
        projectionButton.setVisibility(editing ? View.VISIBLE : View.GONE);
        resetButton.setVisibility(mode().usesEditorCamera() ? View.VISIBLE : View.GONE);
        projectionButton.setText(tr(editCamera.isOrthographic() ? "projection.orthographic" : "projection.perspective"));
        if (sidebarPanel != null) sidebarPanel.refresh();
    }

    private void updateVisibility() {
        boolean visible = shown && !disposed && isAttachedToWindow() && isShown()
                && viewport.getWidth() > 0 && viewport.getHeight() > 0;
        if (!visible) {
            deactivate();
            return;
        }
        if (registered) return;
        registered = true;
        if (!Boolean.getBoolean("gn.standalone")) {
            if (mode() == ViewportMode.GAME) {
                SharedGameFrame.setVisible(this, true);
                GameViewportInput.setVisible(this, true);
            } else {
                EditViewportRuntime.setVisible(this, editCamera, true);
            }
        }
        viewport.setActive(true);
        updatePreferred();
        refresh();
    }

    private void scheduleVisibilityRefresh() {
        // A layout callback must not alter children or registrations mid-traversal.
        removeCallbacks(refreshVisibility);
        post(refreshVisibility);
    }

    private void deactivate() {
        removeCallbacks(refresh);
        removeCallbacks(refreshVisibility);
        if (viewport != null) viewport.setActive(false);
        if (!registered) return;
        registered = false;
        updatePreferred();
        if (!Boolean.getBoolean("gn.standalone")) {
            GameViewportInput.setVisible(this, false);
            SharedGameFrame.setVisible(this, false);
            EditViewportRuntime.setVisible(this, editCamera, false);
        }
    }

    private void refresh() {
        if (!registered) return;
        // Includes the first frame and parent-only transforms, not just local layout changes.
        viewport.publishPixelDemand();
        if (toolOrthographic != editCamera.isOrthographic()) updateTools();
        long now = System.nanoTime();
        if (sidebarLayout.isVisible() && now >= nextSidebarRefresh) {
            sidebarPanel.refresh();
            nextSidebarRefresh = now + 250_000_000L;
        }
        boolean available = viewport.isFrameAvailable();
        waiting.setVisibility(available ? View.GONE : View.VISIBLE);
        updateStatus(available);
        viewport.invalidate();
        postDelayed(refresh, 16);
    }

    private void updateStatus(boolean available) {
        String key;
        if (Boolean.getBoolean("gn.standalone")) key = "waiting";
        else if (mode().usesEditorCamera()) {
            key = EditViewportRuntime.isLimited(this) ? "edit_limited"
                    : EditViewportFlightInput.isCaptured(this) ? "flying_basic" : available
                    ? (mode() == ViewportMode.FLY ? "fly_ready_basic"
                            : editCamera.isOrthographic() ? "editing_orthographic" : "editing_basic") : "edit_waiting";
        } else {
            boolean basic = EditViewportRuntime.hasVisibleViews();
            key = !available ? "waiting" : GameViewportInput.isCaptured(this)
                    ? (basic ? "controlling_basic" : "controlling") : (basic ? "viewing_basic" : "viewing");
        }
        // Camera images and transform demand refresh every frame. Binding descriptions
        // and localized help need only follow settings changes at the sidebar cadence;
        // capture, availability and mode transitions still update immediately.
        long now = System.nanoTime();
        if (key.equals(statusKey) && now < nextHintRefresh) return;
        nextHintRefresh = now + 250_000_000L;
        String shortcut = ("editing_basic".equals(key) || "editing_orthographic".equals(key))
                ? GameViewportShortcuts.display(BuiltinConfigEntries.GAME_VIEW_FLY)
                : "flying_basic".equals(key) ? GameViewportShortcuts.flightHint() : "";
        if (key.equals(statusKey) && shortcut.equals(statusShortcut)) return;
        statusKey = key;
        statusShortcut = shortcut;
        String text = "flying_basic".equals(key) ? shortcut + " · " + tr("basic_shading") : shortcut.isEmpty() ? tr(key)
                : Component.translatable("geometry_node.game_view." + key, shortcut).getString();
        status.setText(text);
        status.setTooltipText(text);
        waiting.setText(text);
        status.setTextColor((key.startsWith("controlling") || "flying_basic".equals(key)) ? UiTheme.Control.FOCUS : UiTheme.Text.MUTED);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && !(findFocus() instanceof EditText)) {
            // A toolbar/sidebar button may own focus after switching modes. Commands
            // belong to this window, not only to its image child.
            if (viewport.dispatchShortcut(event)) return true;
            KeyBinding binding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GAME_VIEW_SIDEBAR));
            if (binding != null && binding.matches(event)) {
                if (event.getRepeatCount() == 0) sidebarLayout.toggle();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static ShapeDrawable background(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable("geometry_node.game_view." + key).getString();
    }
}
