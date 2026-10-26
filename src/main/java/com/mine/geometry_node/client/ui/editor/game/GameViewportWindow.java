package com.mine.geometry_node.client.ui.editor.game;


import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapSettings;
import com.mine.geometry_node.client.ui.components.base.scroll.UiHorizontalScrollView;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.ui.editor.game.input.GameViewportShortcuts;
import com.mine.geometry_node.client.ui.editor.game.sidebar.GameViewportSidebarPanel;
import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.input.EditViewportFlightInput;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.diagnostic.GameViewTiming;
import com.mine.geometry_node.client.runtime.game.render.SharedGameFrame;
import com.mine.geometry_node.client.ui.components.composite.menu.UiRadialMenu;
import com.mine.geometry_node.client.ui.components.composite.menu.RadialMenuLayout.Slot;
import com.mine.geometry_node.client.ui.components.composite.menu.HeldMenuKey;
import java.util.List;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.composite.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.SurfaceRegistrationAware;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.composite.button.UiSplitButton;
import net.minecraft.network.chat.Component;

/** Workspace visibility owns subscriptions; each window retains its own edit camera. */
public final class GameViewportWindow extends LinearLayout implements AreaEditorWindow, com.mine.geometry_node.client.ui.workspace.surface.HistorySurface, SurfaceRegistrationAware, EditViewportFlightInput.ViewMenuOwner {
    private final com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization visualization = new com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization();
    private final com.mine.geometry_node.client.ui.editor.game.scene.ViewportSceneMenus sceneMenus;
    private final com.mine.geometry_node.client.ui.editor.game.sidebar.ScenePropertiesPanel propertiesPanel;
    private final EditorSidebar sidebar;
    private final UiActionButton visibilityButton;
    private final UiActionButton snapButton;
    private final UiSplitButton snapControl;
    private final UiActionButton gridButton;
    private ViewportSnapSettings moveSnap=ViewportSnapSettings.defaults();
    private final UiActionButton snapOptionsButton;
    private final EditViewportCamera editCamera = new EditViewportCamera();
    private final GameViewportView viewport;
    private final UiRadialMenu viewMenu;
    private final HeldMenuKey menuKey = new HeldMenuKey();
    private final UiActionButton modeButton;
    private final UiActionButton projectionButton;
    private final UiActionButton resetButton;
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
    private boolean dismissingMenuGesture;
    private volatile long viewMenuGeneration;
    private String statusKey;
    private final TextView transformStatus;
    private boolean toolOrthographic;
    private long nextSidebarRefresh;
    private long creationRequest;
    private final Runnable refresh = this::refresh;
    private final Runnable refreshVisibility = this::updateVisibility;

    public GameViewportWindow(Context context) {
        super(context);
        long constructionStart = GameViewTiming.start();
        setOrientation(VERTICAL);
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(background(UiThemes.EDITOR.surface().header()));
        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackground(background(UiThemes.EDITOR.surface().header()));
        modeButton = action(context, "mode.game", this::openModeMenu);
        projectionButton = action(context, "projection.perspective", this::toggleProjection);
        resetButton = action(context, "reset_camera", this::resetCamera);
        for (TextView button : new TextView[]{modeButton, projectionButton, resetButton}) {
            tools.addView(button, toolbarButtonLayout());
        }
        HorizontalScrollView toolScroller = new UiHorizontalScrollView(context);
        toolScroller.setHorizontalScrollBarEnabled(false);
        toolScroller.setFillViewport(true);
        toolScroller.addView(tools, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header.addView(toolScroller, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        transformStatus=UIUtils.createLockedTextView(context,"",11,UiThemes.EDITOR.text().secondary());
        transformStatus.setSingleLine(true);
        header.addView(transformStatus, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        snapControl = new UiSplitButton(context, tr("scene.snap"), toolbarPalette(false),
                this::toggleMoveSnap, this::openSnapSettings);
        snapButton = snapControl.primaryButton();
        snapOptionsButton = snapControl.secondaryButton();
        header.addView(snapControl, toolbarButtonLayout());
        gridButton = action(context, "scene.grid", () -> {
            visualization.setGrid(!visualization.grid());
            visualizationChanged();
            updateTools();
        });
        header.addView(gridButton, toolbarButtonLayout());
        visibilityButton = action(context, "scene.visibility", this::openVisualization);
        header.addView(visibilityButton, toolbarButtonLayout());
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));

        FrameLayout content = new FrameLayout(context);
        viewport = new GameViewportView(context, this, editCamera, this::markInteracted,
                this::scheduleVisibilityRefresh);
        content.addView(viewport, new FrameLayout.LayoutParams(-1, -1));
        sceneMenus = new com.mine.geometry_node.client.ui.editor.game.scene.ViewportSceneMenus(this,content,visualization,this::visualizationChanged);
        waiting = UIUtils.createLockedTextView(context, tr("waiting"), 12.0f, UiThemes.EDITOR.text().muted());
        waiting.setPadding(UIUtils.dp2pxInt(12), 0, UIUtils.dp2pxInt(12), 0);
        waiting.setGravity(Gravity.CENTER);
        content.addView(waiting, new FrameLayout.LayoutParams(-1, -1));
        viewMenu = new UiRadialMenu(context, tr("view_menu.title"), List.of(
                viewItem(Slot.TOP, "top", EditViewportCamera.Axis.POSITIVE_Y),
                viewItem(Slot.BOTTOM, "bottom", EditViewportCamera.Axis.NEGATIVE_Y),
                viewItem(Slot.TOP_LEFT, "front", EditViewportCamera.Axis.NEGATIVE_Z),
                viewItem(Slot.TOP_RIGHT, "back", EditViewportCamera.Axis.POSITIVE_Z),
                viewItem(Slot.LEFT, "left", EditViewportCamera.Axis.NEGATIVE_X),
                viewItem(Slot.RIGHT, "right", EditViewportCamera.Axis.POSITIVE_X)));
        content.addView(viewMenu, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout workspace = new LinearLayout(context);
        workspace.setOrientation(HORIZONTAL);
        workspace.addView(content, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.75f));
        View divider = ThinResizableDivider.weighted(context, ThinResizableDivider.Orientation.HORIZONTAL);
        workspace.addView(divider);
        sidebar = new EditorSidebar(context);
        sidebar.installRegisteredPanels(new SidebarPanelContext(context, SidebarPanelScope.GAME_VIEWPORT, this));
        sidebarPanel = sidebar.requirePanel(GameViewportSidebarPanel.PANEL_ID, GameViewportSidebarPanel.class);
        propertiesPanel = sidebar.requirePanel(com.mine.geometry_node.client.ui.editor.game.sidebar.ScenePropertiesPanel.PANEL_ID, com.mine.geometry_node.client.ui.editor.game.sidebar.ScenePropertiesPanel.class);
        workspace.addView(sidebar, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.25f));
        sidebarLayout = new SidebarLayoutController(workspace, content, divider, sidebar, 0.25f, null);
        sidebar.setOnCollapseRequested(() -> sidebarLayout.setVisible(false, true));
        sidebarLayout.initialize(false);
        addView(workspace, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        updateTools();
        updateStatus(false);
        GameViewTiming.finish("window construction", constructionStart, true);
    }

    private UiRadialMenu.Item viewItem(Slot slot, String name, EditViewportCamera.Axis axis) {
        return new UiRadialMenu.Item(slot, tr("view_menu." + name), null, () -> viewport.alignView(axis));
    }

    private void openViewMenu() {
        if (!viewport.canNavigate()) return;
        closeSceneMenus();
        viewport.prepareViewMenu();
        viewport.showViewMenu(viewMenu);
    }

    void closeViewMenu() {
        viewMenuGeneration++;
        if (viewMenu != null) viewMenu.dismiss();
        menuKey.reset();
    }

    private void confirmViewMenu() {
        menuKey.reset();
        if (registered && !disposed && viewport.canNavigate()
                && (areaLifecycle == null || areaLifecycle.isActive())) viewMenu.confirmHoveredAndDismiss();
        else viewMenu.dismiss();
    }

    @Override public void onViewMenuCancelled() {
        long generation = viewMenuGeneration;
        post(() -> {
            if (generation == viewMenuGeneration && !disposed) closeViewMenu();
        });
    }

    /** Flight input calls on the client thread; preserve press/release order on the UI queue. */
    @Override public void onViewMenuKey(int key, boolean pressed) {
        long generation = viewMenuGeneration;
        post(() -> {
            if (generation != viewMenuGeneration || disposed) return;
            if (pressed) {
                if (!registered || disposed || mode() != ViewportMode.FLY || !viewport.canNavigate()
                        || areaLifecycle != null && !areaLifecycle.isActive()) return;
                menuKey.begin(key);
                openViewMenu();
            } else confirmViewMenu();
        });
    }

    public com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization visualization() { return visualization; }
    private void visualizationChanged() {
        viewport.prepareViewMenu();
        if (registered && mode().usesEditorCamera()) com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization.publish(this,visualization.snapshot());
        viewport.invalidate();
    }
    void closeSceneMenus() { creationRequest++; if(sceneMenus != null) sceneMenus.dismiss(); }
    private void openModeMenu() {
        closeSceneMenus();
        closeViewMenu();
        viewport.prepareViewMenu();
        sceneMenus.modes(modeButton, mode(), this::setMode);
    }

    private void openVisualization() {
        if (!mode().usesEditorCamera()) return;
        closeSceneMenus();
        closeViewMenu(); viewport.prepareViewMenu(); sceneMenus.visualization(visibilityButton);
    }
    void openCreateMenu(float rootX,float rootY,
                        com.mine.geometry_node.client.runtime.game.scene.ViewportCreationPlacement.Request placement) {
        if(mode()!=ViewportMode.EDIT || !viewport.canNavigate() || placement == null)return;
        closeSceneMenus();
        closeViewMenu(); viewport.prepareViewMenu();
        if(!Float.isFinite(rootX)||!Float.isFinite(rootY)){
            icyllis.modernui.graphics.Matrix matrix=new icyllis.modernui.graphics.Matrix();viewport.transformMatrixToGlobal(matrix);
            float[] point={viewport.getWidth()*.5f,viewport.getHeight()*.5f};matrix.mapPoint(point);rootX=point[0];rootY=point[1];
        }
        long request = ++creationRequest;
        float menuX = rootX, menuY = rootY;
        com.mine.geometry_node.client.runtime.game.scene.ViewportCreationPlacement.resolve(placement, position -> post(() -> {
            if (request != creationRequest || disposed || !registered || mode() != ViewportMode.EDIT
                    || !viewport.canNavigate() || !placement.current(com.mine.geometry_node.client.feature.scene.ClientSceneService.INSTANCE.snapshot())) return;
            sceneMenus.createAt(menuX,menuY,position,()->{
                sidebarLayout.setVisible(true,true);
                sidebar.selectPanel(com.mine.geometry_node.client.ui.editor.game.sidebar.ScenePropertiesPanel.PANEL_ID);
                viewport.requestFocus();
            });
        }));
    }

    public ViewportMode mode() { return modes.current(); }
    void selectFlightMode() { setMode(ViewportMode.FLY); }
    @Override public View getView() { return this; }
    @Override public void bindSurfaceRegistration(UiSurfaceRegistry.Registration registration) { this.registration = registration; }
    @Override public void onShow() {
        if (disposed || shown) return;
        shown = true; sidebarLayout.onOwnerShown(); updateVisibility();
    }
    @Override public void onHide() { shown = false; sidebarLayout.onOwnerHidden(); updateVisibility(); }
    @Override public void onDispose() {
        if (disposed) return;
        onHide();
        disposed = true;
        areaSubscription.close();
        areaSubscription = AreaLifecycle.Subscription.NONE;
        areaLifecycle = null;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (disposed) return;
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

    public boolean isOrthographic() { return editCamera.isOrthographic(); }

    /** UI-thread navigation entry point used by database results; never teleports the player. */
    public boolean focusWorldObjects(String dimension, net.minecraft.world.phys.AABB bounds,
                                     java.util.Set<java.util.UUID> objectIds) {
        if (disposed || !shown || bounds == null) return false;
        var service = com.mine.geometry_node.client.feature.scene.ClientSceneService.INSTANCE;
        var snapshot = service.snapshot();
        if (!snapshot.dimension().equals(dimension)) return false;
        closeViewMenu(); closeSceneMenus();
        viewport.releaseInteraction();
        setMode(ViewportMode.EDIT);
        java.util.Set<java.util.UUID> available = new java.util.LinkedHashSet<>();
        for (java.util.UUID id : objectIds) if (snapshot.object(id) != null) available.add(id);
        if (!available.isEmpty()) service.select(available, available.iterator().next());
        double aspect = (double) Math.max(1, viewport.getWidth()) / Math.max(1, viewport.getHeight());
        if (Boolean.getBoolean("gn.standalone")) editCamera.focus(bounds, aspect, 1.2);
        else EditViewportRuntime.focusWorldObjects(this, dimension, bounds);
        markInteracted(); viewport.requestFocus(); viewport.invalidate();
        return true;
    }

    public void resetCamera() {
        if (!mode().usesEditorCamera()) return;
        viewport.releaseInteraction();
        markInteracted();
        if (!Boolean.getBoolean("gn.standalone")) EditViewportRuntime.resetToPlayer(this);
    }

    public void toggleProjection() {
        if (mode() != ViewportMode.EDIT) return;
        viewport.releaseInteraction();
        editCamera.toggleOrthographic();
        markInteracted();
        updateTools();
        viewport.invalidate();
    }

    private void markInteracted() {
        if (registration != null) registration.markInteracted();
        if (!Boolean.getBoolean("gn.standalone") && mode().usesEditorCamera()) EditViewportRuntime.touch(this);
    }

    private LayoutParams toolbarButtonLayout() {
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(3), UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(3));
        return params;
    }

    private UiActionButton action(Context context, String key, Runnable action) {
        UiActionButton button = UiActionButton.withPalette(context, tr(key), toolbarPalette(false),
                UiActionButton.Density.COMPACT,
                new UiActionButton.Style(3, 1, UiThemes.EDITOR.control().iconBorder(), UiMetrics.DISABLED_ALPHA));
        UIUtils.setLockedTextSize(button, 11);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(9), 0);
        button.setOnClickListener(ignored -> action.run());
        return button;
    }

    private static UiActionButton.Palette toolbarPalette(boolean selected) {
        return UiActionButton.Palette.toggle(selected);
    }

    void toggleMoveSnap() {
        if(mode()!=ViewportMode.EDIT)return;
        moveSnap=moveSnap.withEnabled(!moveSnap.enabled());viewport.setMoveSnap(moveSnap);updateTools();
    }

    private void openSnapSettings(){
        closeSceneMenus();
        if(mode()!=ViewportMode.EDIT)return;
        closeViewMenu();viewport.prepareViewMenu();
        sceneMenus.snapping(snapOptionsButton,()->moveSnap,settings->{moveSnap=settings;viewport.setMoveSnap(settings);updateTools();});
    }

    private void updateTools() {
        toolOrthographic = editCamera.isOrthographic();
        boolean editing = mode() == ViewportMode.EDIT;
        modeButton.setText(tr("mode." + mode().name().toLowerCase(java.util.Locale.ROOT)) + " ▾");
        gridButton.setVisibility(mode().usesEditorCamera() ? View.VISIBLE : View.GONE);
        gridButton.setSelected(visualization.grid());
        snapControl.setVisibility(editing ? View.VISIBLE : View.GONE);
        snapButton.setSelected(moveSnap.enabled());
        snapOptionsButton.setTooltipText(tr("snap.options"));
        snapButton.setTooltipText(tr("scene.snap")+" ("+GameViewportShortcuts.display(BuiltinConfigEntries.GAME_VIEW_SNAP)+")");
        projectionButton.setVisibility(editing ? View.VISIBLE : View.GONE);
        resetButton.setVisibility(mode().usesEditorCamera() ? View.VISIBLE : View.GONE);
        visibilityButton.setVisibility(mode().usesEditorCamera() ? View.VISIBLE : View.GONE);
        projectionButton.setText(tr("projection.orthographic"));
        projectionButton.setSelected(editCamera.isOrthographic());
        for (UiActionButton button : new UiActionButton[]{modeButton, projectionButton, snapButton, gridButton})
            button.setPalette(toolbarPalette(button.isSelected()));
        if (sidebarPanel != null) sidebarPanel.refresh();
        if (propertiesPanel != null) propertiesPanel.refresh();
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
                com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization.publish(this,visualization.snapshot());
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
        closeSceneMenus();
        com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization.remove(this);
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

    @Override
    public com.mine.geometry_node.client.history.HistoryTarget historyTarget() {
        return mode() == ViewportMode.EDIT ? com.mine.geometry_node.client.feature.scene.ClientSceneService.INSTANCE : null;
    }

    private void refresh() {
        if (!registered) return;
        // Includes the first frame and parent-only transforms, not just local layout changes.
        viewport.publishPixelDemand();
        String transformLabel=viewport.transformLabel();
        if(!transformLabel.contentEquals(transformStatus.getText()))transformStatus.setText(transformLabel);
        if (viewMenu.isOpen() && !viewport.canNavigate()) closeViewMenu();
        if (toolOrthographic != editCamera.isOrthographic()) updateTools();
        long now = System.nanoTime();
        if (sidebarLayout.isVisible() && now >= nextSidebarRefresh) {
            sidebarPanel.refresh();
            propertiesPanel.refresh();
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
        if (key.equals(statusKey)) return;
        statusKey = key;
        waiting.setText(tr(key));
    }

    @Override
    public boolean dispatchTouchEvent(icyllis.modernui.view.MotionEvent event) {
        int action = event.getActionMasked();
        if (action == icyllis.modernui.view.MotionEvent.ACTION_DOWN) {
            boolean dismissedProperties = propertiesPanel.dismissIfOutside(event.getRawX(), event.getRawY());
            boolean dismissedScene = sceneMenus.dismissIfOutside(event.getRawX(), event.getRawY());
            dismissingMenuGesture = dismissedProperties || dismissedScene;
            propertiesPanel.commitIfOutside(event.getRawX(),event.getRawY());
        }
        if (dismissingMenuGesture) {
            if (action == icyllis.modernui.view.MotionEvent.ACTION_UP
                    || action == icyllis.modernui.view.MotionEvent.ACTION_CANCEL) dismissingMenuGesture = false;
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (propertiesPanel.isMenuOpen()) {
            if (sceneMenus.isOpen()) closeSceneMenus();
            KeyBinding cancel = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GAME_VIEW_CANCEL));
            if (event.getAction() == KeyEvent.ACTION_DOWN && cancel != null && UiInputBindings.matches(cancel, event)) {
                propertiesPanel.dismissMenu();
                viewport.requestFocus();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        KeyBinding menuBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GAME_VIEW_MENU));
        if (menuKey.dispatch(event, menuBinding, !(findFocus() instanceof EditText) && viewport.canNavigate()
                && !EditViewportFlightInput.isCaptured(), this::openViewMenu, this::confirmViewMenu, viewMenu::dismiss)) return true;
        if (sceneMenus.isOpen() && event.getAction()==KeyEvent.ACTION_DOWN && !(findFocus() instanceof EditText)) {
            KeyBinding snap=KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GAME_VIEW_SNAP));
            if(snap!=null&&UiInputBindings.matches(snap,event)&&viewport.dispatchShortcut(event))return true;
        }
        if (sceneMenus.isOpen()) {
            KeyBinding cancel=KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GAME_VIEW_CANCEL));
            if(event.getAction()==KeyEvent.ACTION_DOWN&&cancel!=null&&UiInputBindings.matches(cancel,event)) {
                closeSceneMenus();viewport.requestFocus();return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && !(findFocus() instanceof EditText)) {
            // A toolbar/sidebar button may own focus after switching modes. Commands
            // belong to this window, not only to its image child.
            if (viewport.dispatchShortcut(event)) return true;
            KeyBinding binding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GAME_VIEW_SIDEBAR));
            if (binding != null && UiInputBindings.matches(binding, event)) {
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
