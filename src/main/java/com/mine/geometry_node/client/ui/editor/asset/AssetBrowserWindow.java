package com.mine.geometry_node.client.ui.editor.asset;


import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.ui.components.integration.asset.navigation.AssetNavigationPanel;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.feature.asset.remote.RemoteAssetClient;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.composite.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.config.AppConfig;
import com.mine.geometry_node.client.ui.workspace.state.EditorSidebarState;
import com.mine.geometry_node.client.ui.workspace.state.UiStateContext;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.workspace.persistence.EditorSessionState;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class AssetBrowserWindow extends FrameLayout implements AreaEditorWindow, AssetBrowserCoordinator {
    private final LinearLayout mMainLayout;
    private final AssetNavigationPanel mNavigationPanel;
    private final AssetFileBrowserPanel mBrowserPanel;
    private final AssetBrowserAreaBindings.Properties mPropertiesPanel;
    private final AssetBrowserAreaBindings mBindings;
    private final AssetBrowserAreaBindings.Transfers mTransfers;
    private final EditorSidebar mPropertiesSidebar;
    private final SidebarLayoutController mSidebarLayout;
    private final UiStateContext mUiStateContext;
    // Capability requests and the owner generation are confined to the UI thread.
    private long mRemoteGeneration;
    private boolean mRemoteActive = true;
    private final Set<Integer> mRemoteRequestIds = new HashSet<>();
    private final EditorSessionState.AssetBrowserState mSessionState;
    private final Runnable mSessionChanged;
    private AutoCloseable mSavedSubscription;
    private boolean mRestoringLocation;
    private String mPendingRemoteRestore;

    public AssetBrowserWindow(Context context, AssetBrowserAreaBindings bindings) {
        this(context, new EditorSessionState.AssetBrowserState(), null, UiStateContext.ephemeral(), bindings);
    }

    public AssetBrowserWindow(
            Context context,
            EditorSessionState.AssetBrowserState sessionState,
            Runnable sessionChanged,
            UiStateContext uiStateContext,
            AssetBrowserAreaBindings bindings) {
        super(context);
        mUiStateContext = java.util.Objects.requireNonNull(uiStateContext);
        mSessionState = sessionState == null
                ? new EditorSessionState.AssetBrowserState()
                : sessionState;
        mSessionChanged = sessionChanged;
        mBindings = java.util.Objects.requireNonNull(bindings);

        mMainLayout = new LinearLayout(context);
        mMainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mMainLayout.setBackground(createColorDrawable(UiThemes.EDITOR.surface().panel()));
        addView(mMainLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mNavigationPanel = new AssetNavigationPanel(context, this);
        float navigationWeight = sanitizeNavigationWeight(mSessionState.navigationWeight);
        mMainLayout.addView(mNavigationPanel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, navigationWeight));

        mMainLayout.addView(ThinResizableDivider.weighted(
                context, ThinResizableDivider.Orientation.HORIZONTAL, delta -> captureNavigationWeight()));

        if (!mUiStateContext.contains(EditorSidebarState.ASSETS, EditorSidebarState.SCOPE)) {
            AppConfig.AssetBrowserConfig browserConfig = ConfigManager.INSTANCE.getConfig().assetBrowser;
            mUiStateContext.write(EditorSidebarState.ASSETS, EditorSidebarState.SCOPE,
                    new EditorSidebarState(browserConfig.rightSidebarVisible,
                            browserConfig.rightSidebarWeight, browserConfig.rightSidebarTab));
        }
        EditorSidebarState sidebarState = mUiStateContext.read(
                EditorSidebarState.ASSETS, EditorSidebarState.SCOPE);
        float sidebarWeight = sidebarState.weight();

        LinearLayout browserWorkspace = new LinearLayout(context);
        browserWorkspace.setOrientation(LinearLayout.HORIZONTAL);
        mMainLayout.addView(browserWorkspace, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f - navigationWeight));

        mBrowserPanel = new AssetFileBrowserPanel(context, this, mBindings::documents);
        mTransfers = mBindings.transfers(this, mBrowserPanel);
        browserWorkspace.addView(mBrowserPanel, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0.8f - sidebarWeight));

        View propertiesDivider = ThinResizableDivider.weighted(
                context, ThinResizableDivider.Orientation.HORIZONTAL, delta -> persistSidebarState());
        browserWorkspace.addView(propertiesDivider);

        mPropertiesSidebar = new EditorSidebar(context);
        mPropertiesSidebar.installRegisteredPanels(new SidebarPanelContext(
                context,
                SidebarPanelScope.ASSET_BROWSER));
        mPropertiesPanel = mBindings.properties(mPropertiesSidebar);
        mPropertiesSidebar.restoreSelectedPanel(sidebarState.tab());
        browserWorkspace.addView(mPropertiesSidebar, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                sidebarWeight));

        mSidebarLayout = new SidebarLayoutController(
                browserWorkspace,
                mBrowserPanel,
                propertiesDivider,
                mPropertiesSidebar,
                sidebarWeight,
                (visible, weight) -> mUiStateContext.write(
                        EditorSidebarState.ASSETS, EditorSidebarState.SCOPE,
                        new EditorSidebarState(visible, weight, mPropertiesSidebar.getSelectedPanelId())));
        mPropertiesSidebar.setOnCollapseRequested(() -> mSidebarLayout.setVisible(false, true));
        mPropertiesSidebar.setOnSelectedPanelChanged(id -> persistSidebarState());
        mBrowserPanel.setSelectionChangedListener(entries ->
                mPropertiesPanel.bind(entries, mBrowserPanel::refreshFileList));
        mBrowserPanel.setLocationChangedListener(this::captureLocationState);
        mSidebarLayout.initialize(sidebarState.visible());

        restoreInitialLocation();
        requestRemoteCapabilities();
    }

    private void persistSidebarState() {
        mSidebarLayout.persistState();
    }

    /**
     * 跨区协调总线：将导航栏选中的目录分发给主文件浏览区
     */
    @Override
    public void dispatchNavigateTo(File directory) {
        if (mBrowserPanel != null) {
            mBrowserPanel.navigateTo(directory);
        }
    }

    @Override
    public void dispatchNavigateToFavorites() {
        if (mBrowserPanel != null) {
            mBrowserPanel.navigateToFavorites();
        }
    }

    @Override
    public void dispatchNavigateToRemoteRoot() {
        if (mBrowserPanel != null
                && mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE)) {
            mBrowserPanel.navigateToRemoteRoot();
        }
    }

    @Override
    public boolean canBrowseRemote() {
        return mBrowserPanel != null
                && mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE);
    }

    @Override public void showUploadDialog(List<File> files) { mTransfers.showUploadDialog(files); }

    @Override public void showDownloadDialog(List<AssetEntry> entries) { mTransfers.showDownloadDialog(entries); }

    @Override public void createRemoteGraph(String targetPath, Runnable onSuccess) {
        mTransfers.createRemoteGraph(targetPath, onSuccess);
    }

    /**
     * 跨区协调总线：当主文件浏览区通过 NavBar 的 "+" 添加快速路径后，刷新导航栏
     */
    @Override
    public void notifySidebarChanged() {
        if (mNavigationPanel != null) {
            mNavigationPanel.buildSidebar();
        }
        requestRemoteCapabilities();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        boolean reactivate = !mRemoteActive;
        mRemoteActive = true;
        if (reactivate) requestRemoteCapabilities();
        mSidebarLayout.onOwnerShown();
        mTransfers.activate();
        if (mSavedSubscription == null) {
            mSavedSubscription = mBindings.onDocumentSaved(path -> postRemote(mRemoteGeneration, () ->
                    mBrowserPanel.refreshIfDisplayingLocalFile(path)));
        }
        if (mBrowserPanel != null) {
            mBrowserPanel.activatePanel();
        }
        if (mBrowserPanel != null) {
            mBrowserPanel.refreshFileList();
        }
        if (mNavigationPanel != null) {
            mNavigationPanel.buildSidebar();
        }
    }

    @Override
    public void onHide() {
        cancelRemoteRequests();
        closeSavedSubscription();
        mSidebarLayout.onOwnerHidden();
        mSidebarLayout.persistState();
        if (mBrowserPanel != null) {
            mBrowserPanel.deactivatePanel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        closeSavedSubscription();
        mPropertiesPanel.commitPendingEdits();
        mTransfers.dispose();
        cancelRemoteRequests();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            KeyBinding saveBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_SAVE));
            if (saveBinding != null && UiInputBindings.matches(saveBinding, event)) {
                mPropertiesPanel.commitPendingEdits();
                return true;
            }

            if (findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
            if (mBrowserPanel != null && mBrowserPanel.handleShortcut(event)) return true;
            KeyBinding sidebarBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.VIEWPORT_TOGGLE_SIDEBAR));
            if (sidebarBinding != null && UiInputBindings.matches(sidebarBinding, event)) {
                mSidebarLayout.toggle();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private void requestRemoteCapabilities() {
        if (!mRemoteActive) return;
        long generation = mRemoteGeneration;
        int requestId = beginRemoteRequest();
        try {
            RemoteAssetClient.requestCapabilities(requestId, response ->
                    postRemoteRequest(requestId, generation, () -> {
                        if (mNavigationPanel != null) mNavigationPanel.buildSidebar();
                        if (isRemoteCurrent(generation)) restorePendingRemoteLocation();
                    }), () -> postRemoteRequest(requestId, generation, () -> { }));
        } catch (RuntimeException failure) {
            mRemoteRequestIds.remove(requestId);
            System.getLogger(AssetBrowserWindow.class.getName()).log(
                    System.Logger.Level.WARNING, "Could not request remote asset capabilities", failure);
        }
    }

    private void restoreInitialLocation() {
        mRestoringLocation = true;
        String location = mSessionState.location == null ? "LOCAL" : mSessionState.location;
        if ("FAVORITES".equals(location)) {
            dispatchNavigateToFavorites();
            mRestoringLocation = false;
            return;
        }
        if ("REMOTE".equals(location)) {
            mPendingRemoteRestore = mSessionState.remotePath == null ? "" : mSessionState.remotePath;
            dispatchNavigateTo(AssetBrowserPathPolicy.getLocalDraftsDir());
            return;
        }

        File restored = AssetBrowserPathPolicy.resolveConfigPath(mSessionState.localPath);
        dispatchNavigateTo(restored != null && restored.isDirectory()
                ? restored
                : AssetBrowserPathPolicy.getLocalDraftsDir());
        mRestoringLocation = false;
        captureLocationState();
    }

    private void restorePendingRemoteLocation() {
        if (mPendingRemoteRestore == null) {
            return;
        }
        String remotePath = mPendingRemoteRestore;
        mPendingRemoteRestore = null;
        if (mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE)) {
            mBrowserPanel.navigateToRemote(remotePath);
            mRestoringLocation = false;
            return;
        }
        mRestoringLocation = false;
        captureLocationState();
    }

    private void captureLocationState() {
        if (mRestoringLocation || mBrowserPanel == null) {
            return;
        }
        if (mBrowserPanel.isFavoritesMode()) {
            mSessionState.location = "FAVORITES";
        } else if (mBrowserPanel.getSourceKind() == AssetSourceKind.REMOTE) {
            mSessionState.location = "REMOTE";
            mSessionState.remotePath = mBrowserPanel.getRemoteDirectory();
        } else {
            mSessionState.location = "LOCAL";
            mSessionState.localPath = AssetBrowserPathPolicy.toConfigPath(mBrowserPanel.getCurrentDirectory());
        }
        if (mSessionChanged != null) {
            mSessionChanged.run();
        }
    }

    private void captureNavigationWeight() {
        if (mNavigationPanel.getLayoutParams() instanceof LinearLayout.LayoutParams params) {
            mSessionState.navigationWeight = sanitizeNavigationWeight(params.weight);
            if (mSessionChanged != null) {
                mSessionChanged.run();
            }
        }
    }

    private static float sanitizeNavigationWeight(float weight) {
        return Float.isFinite(weight) ? Math.max(0.05f, Math.min(0.45f, weight)) : 0.2f;
    }

    private int beginRemoteRequest() {
        int requestId = RemoteAssetClient.nextRequestId();
        mRemoteRequestIds.add(requestId);
        return requestId;
    }

    private boolean isRemoteCurrent(long generation) {
        return mRemoteActive && generation == mRemoteGeneration;
    }

    private void postRemote(long generation, Runnable action) {
        post(() -> {
            if (isRemoteCurrent(generation)) action.run();
        });
    }

    private void postRemoteRequest(int requestId, long generation, Runnable action) {
        postRemote(generation, () -> {
            if (mRemoteRequestIds.remove(requestId)) action.run();
        });
    }

    private void closeSavedSubscription() {
        if (mSavedSubscription == null) return;
        AutoCloseable subscription = mSavedSubscription;
        mSavedSubscription = null;
        try { subscription.close(); }
        catch (Exception failure) {
            System.getLogger(AssetBrowserWindow.class.getName()).log(
                    System.Logger.Level.WARNING, "Could not remove document save listener", failure);
        }
    }

    private void cancelRemoteRequests() {
        mRemoteActive = false;
        mRemoteGeneration++;
        List<Integer> requests = List.copyOf(mRemoteRequestIds);
        mRemoteRequestIds.clear();
        for (int requestId : requests) RemoteAssetClient.cancel(requestId);
        mTransfers.deactivate();
    }
}
