package com.mine.geometry_node.client.ui.editor.asset.browser;

import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;

import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.composite.menu.UiSearchableMenu;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.icon.VectorIconView;
import com.mine.geometry_node.client.ui.theme.UiTheme;

import com.mine.geometry_node.client.ui.editor.asset.AssetPathUtils;
import com.mine.geometry_node.client.ui.editor.asset.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.editor.asset.action.AssetLibraryActionId;
import com.mine.geometry_node.client.ui.editor.asset.action.AssetLibraryActionRegistry;
import com.mine.geometry_node.client.ui.editor.asset.drag.AssetDragPayload;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragDropRegistry;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragOperation;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragState;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragService;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetBrowseRequest;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetListing;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetLocation;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetQuery;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetRepository;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetRepositoryRegistry;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetRequest;
import com.mine.geometry_node.client.ui.editor.asset.service.GraphAssetService;
import com.mine.geometry_node.client.ui.editor.asset.service.LocalAssetService;
import com.mine.geometry_node.client.ui.editor.asset.task.AssetTaskController;
import com.mine.geometry_node.client.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedKeyManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

public class AssetFileBrowserPanel extends LinearLayout implements AssetFileItemView.Listener, FileContentLayout.SelectionHost {

    private final AssetBrowserCoordinator mCoordinator;
    private final EditText mPathInput;
    private final EditText mSearchInput;
    private final EditText mTagSearchInput;
    private final FrameLayout mBodyFrame;
    private final ScrollView mScrollView;
    private final FileContentLayout mFileContent;
    private final Map<String, AssetFileItemView> mItemViews = new HashMap<>();
    private final Set<String> mSelectedPaths = new LinkedHashSet<>();
    private final List<AssetEntry> mVisibleEntries = new ArrayList<>();
    private final AssetFavoriteStore mFavoriteStore = new AssetFavoriteStore();
    private final LocalAssetService mLocalAssetService = new LocalAssetService();
    private final GraphAssetService mGraphAssetService = new GraphAssetService();
    private final ScopedKeyManager<AssetLibraryActionId, AssetFileBrowserPanel> mKeyManager;
    private final AssetTaskController mIoTasks;
    private final AssetBrowserActionController mActionController;
    private UiContextMenu mActiveMenu;
    private UiSearchableMenu mViewModeMenu;

    private File mCurrentDirectory;
    private String mRemoteDirectory = "";
    private AssetSourceKind mSourceKind = AssetSourceKind.LOCAL;
    private boolean mFavoritesMode = false;
    private AssetViewMode mViewMode;
    private String mSearchQuery = "";
    private String mTagSearchQuery = "";
    private String mPendingSearchQuery = "";
    private String mPendingTagSearchQuery = "";
    private String mLastClickedPath = null;
    private long mLastClickTime = 0L;
    private long mBrowseGeneration;
    private AssetRequest mActiveBrowseRequest = AssetRequest.NONE;
    private AssetListing mCurrentListing = AssetListing.empty(null);
    private final boolean mEnableQuickAccessAdd;
    private final boolean mEnableLocalFileActions;
    private final boolean mEnableRemoteTransferActions;
    private final boolean mOpenLocalJsonOnDoubleClick;
    private final boolean mShowPickerContextActions;
    private Consumer<File> mLocalDirectoryChangedListener;
    private Consumer<String> mRemoteDirectoryChangedListener;
    private Runnable mLocationChangedListener;
    private Consumer<List<AssetEntry>> mSelectionChangedListener;
    private Runnable mPickCurrentDirectoryAction;
    private Consumer<AssetEntry> mPickFileAction;
    private Predicate<AssetEntry> mEntryFilter;

    private static final float NAV_BAR_HEIGHT = 76.0f;
    private static final float NAV_ROW_HEIGHT = 32.0f;
    private static final float NAV_BUTTON_SIZE = 30.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final int COLOR_TOOLBAR_BG = UiTheme.Surface.SIDEBAR;

    public AssetFileBrowserPanel(Context context, AssetBrowserCoordinator coordinator) {
        this(context, coordinator, true, true, true, true, false);
    }

    public AssetFileBrowserPanel(Context context) {
        this(context, null, false, false, true, false, true);
    }

    public static AssetFileBrowserPanel picker(Context context, AssetBrowserCoordinator coordinator) {
        return new AssetFileBrowserPanel(context, coordinator, false, false, false, false, true);
    }

    private AssetFileBrowserPanel(
            Context context,
            AssetBrowserCoordinator coordinator,
            boolean enableQuickAccessAdd,
            boolean enableLocalFileActions,
            boolean enableRemoteTransferActions,
            boolean openLocalJsonOnDoubleClick,
            boolean showPickerContextActions
    ) {
        super(context);
        mCoordinator = coordinator;
        mEnableQuickAccessAdd = enableQuickAccessAdd;
        mEnableLocalFileActions = enableLocalFileActions;
        mEnableRemoteTransferActions = enableRemoteTransferActions;
        mOpenLocalJsonOnDoubleClick = openLocalJsonOnDoubleClick;
        mShowPickerContextActions = showPickerContextActions;
        mIoTasks = new AssetTaskController(this);
        mActionController = createActionController();
        mViewMode = AssetViewMode.fromConfig(ConfigManager.INSTANCE.getConfig().assetBrowser.viewMode);
        mKeyManager = createKeyManager();

        configurePanelRoot();

        LinearLayout navBar = createNavBar(context);
        addNavBar(navBar);

        LinearLayout pathRow = createNavRow(context);
        navBar.addView(pathRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(NAV_ROW_HEIGHT)));
        addUpButton(pathRow, context);

        mPathInput = createPathInput(context);
        LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        pathLp.setMargins(dp2pxInt(4), 0, dp2pxInt(4), 0);
        pathRow.addView(mPathInput, pathLp);
        addQuickAccessButton(pathRow, context);
        addOptionsButton(pathRow, context);

        LinearLayout searchRow = createNavRow(context);
        LinearLayout.LayoutParams searchRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(NAV_ROW_HEIGHT));
        searchRowLp.setMargins(0, dp2pxInt(4), 0, 0);
        navBar.addView(searchRow, searchRowLp);

        mSearchInput = createSearchInput(context, tr("geometry_node.asset_library.toolbar.search_name"),
                value -> mPendingSearchQuery = value);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f);
        searchRow.addView(mSearchInput, searchLp);

        mTagSearchInput = createSearchInput(context, tr("geometry_node.asset_library.toolbar.search_tag"),
                value -> mPendingTagSearchQuery = value);
        LinearLayout.LayoutParams tagSearchLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.85f);
        tagSearchLp.setMargins(dp2pxInt(4), 0, dp2pxInt(4), 0);
        searchRow.addView(mTagSearchInput, tagSearchLp);

        addSearchButton(searchRow, context);
        addClearSearchButton(searchRow, context);

        mBodyFrame = new FrameLayout(context);
        mScrollView = new UiScrollView(context);
        mFileContent = new FileContentLayout(context, this);
        configureBody();
    }

    private AssetBrowserActionController createActionController() {
        return new AssetBrowserActionController(
                this,
                mCoordinator,
                mLocalAssetService,
                mGraphAssetService,
                mFavoriteStore,
                mIoTasks,
                mEnableLocalFileActions,
                mEnableRemoteTransferActions,
                mOpenLocalJsonOnDoubleClick,
                mShowPickerContextActions
        );
    }

    private ScopedKeyManager<AssetLibraryActionId, AssetFileBrowserPanel> createKeyManager() {
        return new ScopedKeyManager<>(
                this,
                KeyScope.ASSET_LIBRARY,
                AssetLibraryActionRegistry::all,
                this::executeAction
        );
    }

    private void configurePanelRoot() {
        setOrientation(LinearLayout.VERTICAL);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    private LinearLayout createNavBar(Context context) {
        LinearLayout navBar = new LinearLayout(context);
        navBar.setOrientation(LinearLayout.VERTICAL);
        navBar.setPadding(dp2pxInt(6), dp2pxInt(4), dp2pxInt(6), dp2pxInt(4));
        navBar.setBackground(createColorDrawable(COLOR_TOOLBAR_BG));
        return navBar;
    }

    private LinearLayout createNavRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void addNavBar(LinearLayout navBar) {
        addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(NAV_BAR_HEIGHT)));
    }

    private void addUpButton(LinearLayout navBar, Context context) {
        UiIconButton btnUp = createIconButton(context,
                new VectorIconView(context, VectorIconView.Kind.CHEVRON_UP, UiTheme.Text.NORMAL),
                tr("geometry_node.asset_library.toolbar.up"));
        btnUp.setOnClickListener(v -> navigateUp());
        navBar.addView(btnUp, new LinearLayout.LayoutParams(
                dp2pxInt(NAV_BUTTON_SIZE), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private EditText createPathInput(Context context) {
        EditText input = createNavInput(context);
        input.setHint(tr("geometry_node.asset_library.toolbar.path"));
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                if (mFavoritesMode) {
                    input.setText(tr("geometry_node.asset_library.toolbar.favorites"));
                    input.clearFocus();
                    return true;
                }
                String path = input.getText().toString().trim();
                if (mSourceKind == AssetSourceKind.REMOTE || AssetPathUtils.isRemotePathInput(path)) {
                    navigateToRemote(AssetPathUtils.remotePathFromInput(path));
                } else if (mCurrentDirectory != null) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        navigateTo(dir);
                    } else {
                        input.setText(mCurrentDirectory.getAbsolutePath());
                    }
                }
                input.clearFocus();
                return true;
            }
            return false;
        });
        return input;
    }

    private void addQuickAccessButton(LinearLayout navBar, Context context) {
        UiIconButton btnAdd = createIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.PLUS, UiTheme.Text.NORMAL),
                tr("geometry_node.asset_library.toolbar.add_quick_access"));
        btnAdd.setOnClickListener(v -> {
            if (!mEnableQuickAccessAdd || mCoordinator == null) return;
            String path = mPathInput.getText().toString().trim();
            File directory = AssetBrowserPathPolicy.resolveConfigPath(path);
            if (AssetBrowserPathPolicy.canPersistQuickAccessPath(path)) {
                String configPath = AssetBrowserPathPolicy.toConfigPath(directory);
                if (!ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths.contains(configPath)) {
                    ConfigManager.INSTANCE.update(config -> config.assetBrowser.quickAccessPaths.add(configPath));
                    mCoordinator.notifySidebarChanged();
                }
            }
        });
        navBar.addView(btnAdd, new LinearLayout.LayoutParams(dp2pxInt(NAV_BUTTON_SIZE), ViewGroup.LayoutParams.MATCH_PARENT));
        btnAdd.setVisibility(mEnableQuickAccessAdd ? View.VISIBLE : View.GONE);
    }

    private EditText createSearchInput(Context context, String hint, Consumer<String> pendingQuerySetter) {
        UiSearchInput input = new UiSearchInput(context);
        input.setHint(hint);
        input.setOnQueryChanged(value -> pendingQuerySetter.accept(value.trim()));
        return input;
    }

    private void addSearchButton(LinearLayout navBar, Context context) {
        UiIconButton btnSearch = createIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.SEARCH, UiTheme.Text.NORMAL),
                tr("geometry_node.asset_library.toolbar.search"));
        UiSearchSubmit.bind(btnSearch, this::applySearch, mSearchInput, mTagSearchInput);
        LinearLayout.LayoutParams searchButtonLp = new LinearLayout.LayoutParams(dp2pxInt(NAV_BUTTON_SIZE), ViewGroup.LayoutParams.MATCH_PARENT);
        navBar.addView(btnSearch, searchButtonLp);
    }

    private void addClearSearchButton(LinearLayout navBar, Context context) {
        UiIconButton btnClearSearch = createIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.CLOSE, UiTheme.Text.NORMAL),
                tr("geometry_node.asset_library.toolbar.clear_search"));
        btnClearSearch.setOnClickListener(v -> clearSearch());
        LinearLayout.LayoutParams clearButtonLp = new LinearLayout.LayoutParams(dp2pxInt(NAV_BUTTON_SIZE), ViewGroup.LayoutParams.MATCH_PARENT);
        clearButtonLp.setMargins(dp2pxInt(4), 0, 0, 0);
        navBar.addView(btnClearSearch, clearButtonLp);
    }

    private void addOptionsButton(LinearLayout navBar, Context context) {
        TextView optionsIcon = UIUtils.createLockedTextView(context, "⋮", 18, UiTheme.Text.NORMAL);
        optionsIcon.setGravity(Gravity.CENTER);
        UiIconButton btnOptions = createIconButton(context, optionsIcon,
                tr("geometry_node.asset_library.toolbar.more_options"));
        btnOptions.setOnClickListener(v -> showOptionsMenu(btnOptions));
        LinearLayout.LayoutParams optionsLp = new LinearLayout.LayoutParams(dp2pxInt(NAV_BUTTON_SIZE), ViewGroup.LayoutParams.MATCH_PARENT);
        optionsLp.setMargins(dp2pxInt(4), 0, 0, 0);
        navBar.addView(btnOptions, optionsLp);
    }

    private void configureBody() {
        mFileContent.setViewMode(mViewMode);
        mScrollView.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> updateVirtualViewport());
        mScrollView.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateVirtualViewport());
        mScrollView.addView(mFileContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mBodyFrame.addView(mScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mBodyFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        activatePanel();
    }

    @Override
    protected void onDetachedFromWindow() {
        WorkspaceDragService.INSTANCE.cancelIfSource(this);
        mIoTasks.cancelAll();
        mActionController.clearClipboard();
        deactivatePanel();
        super.onDetachedFromWindow();
    }

    public void activatePanel() {
        mActionController.activateRemoteRequests();
        mKeyManager.attach();
    }

    public void deactivatePanel() {
        dismissTransientUi();
        mKeyManager.dispose();
        cancelBrowseRequest();
        mActionController.cancelRemoteRequests();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            dismissTransientUi();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleShortcut(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    public boolean handleShortcut(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        View focusedView = findFocus();
        if (focusedView instanceof EditText) return false;
        return mKeyManager.onKeyDown(event);
    }

    private void executeAction(AssetLibraryActionId actionId) {
        if (actionId == null) return;
        switch (actionId) {
            case COPY -> mActionController.copySelectionToClipboard();
            case PASTE -> mActionController.pasteClipboard();
            case CUT -> mActionController.cutSelectionToClipboard();
            case DELETE -> mActionController.deleteSelection();
            case RENAME -> mActionController.renameSelection();
        }
    }

    private void applySearch() {
        mSearchQuery = mPendingSearchQuery;
        mTagSearchQuery = mPendingTagSearchQuery;
        refreshFileList();
        mSearchInput.clearFocus();
        mTagSearchInput.clearFocus();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mFileContent != null) {
            mFileContent.setMinimumContentHeight(Math.max(0, h - dp2pxInt(NAV_BAR_HEIGHT)));
            updateVirtualViewport();
        }
    }

    public void navigateTo(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;
        mFavoritesMode = false;
        mSourceKind = AssetSourceKind.LOCAL;
        mCurrentDirectory = directory;
        mPathInput.setText(directory.getAbsolutePath());
        clearSelection();
        refreshFileList();
        if (mLocalDirectoryChangedListener != null) {
            mLocalDirectoryChangedListener.accept(directory);
        }
        notifyLocationChanged();
    }

    public void navigateToRemoteRoot() {
        navigateToRemote("");
    }

    public void navigateToRemote(String directory) {
        navigateToRemote(directory, false);
    }

    public void navigateToRemote(String directory, boolean createIfMissing) {
        mFavoritesMode = false;
        mSourceKind = AssetSourceKind.REMOTE;
        mRemoteDirectory = AssetPathUtils.normalizeRemoteDirectory(directory);
        mPathInput.setText(AssetPathUtils.formatRemotePath(mRemoteDirectory));
        clearSelection();
        refreshFileList(createIfMissing, null);
        notifyLocationChanged();
    }

    public void navigateToFavorites() {
        mFavoritesMode = true;
        mSourceKind = AssetSourceKind.LOCAL;
        mPathInput.setText("我的收藏");
        clearSelection();
        refreshFileList();
        notifyLocationChanged();
    }

    public void createRemoteDirectoryFromInput() {
        mActionController.createRemoteDirectory(
                AssetPathUtils.remotePathFromInput(mPathInput.getText().toString()), true);
    }

    public void createRemoteFolderInCurrentDirectory() {
        mActionController.triggerNewItem(true);
    }

    public void createLocalFolderInCurrentDirectory() {
        if (!mFavoritesMode && mSourceKind == AssetSourceKind.LOCAL) {
            mActionController.triggerNewItem(true);
        }
    }

    public void setLocalDirectoryChangedListener(Consumer<File> listener) {
        mLocalDirectoryChangedListener = listener;
    }

    public void setRemoteDirectoryChangedListener(Consumer<String> listener) {
        mRemoteDirectoryChangedListener = listener;
    }

    public void setLocationChangedListener(Runnable listener) {
        mLocationChangedListener = listener;
    }

    public File getCurrentDirectory() {
        return mCurrentDirectory;
    }

    public AssetSourceKind getSourceKind() {
        return mSourceKind;
    }

    public String getRemoteDirectory() {
        return mRemoteDirectory;
    }

    public boolean isFavoritesMode() {
        return mFavoritesMode;
    }

    private void notifyLocationChanged() {
        if (mLocationChangedListener != null) {
            mLocationChangedListener.run();
        }
    }

    public List<AssetEntry> getSelectedEntriesSnapshot() {
        return new ArrayList<>(getSelectedEntries());
    }

    public void setSelectionChangedListener(Consumer<List<AssetEntry>> listener) {
        mSelectionChangedListener = listener;
        notifySelectionChanged();
    }

    public void setPickCurrentDirectoryAction(Runnable action) {
        mPickCurrentDirectoryAction = action;
    }

    public void setPickFileAction(Consumer<AssetEntry> action) {
        mPickFileAction = action;
    }

    public void setEntryFilter(Predicate<AssetEntry> filter) {
        mEntryFilter = filter;
    }

    private void navigateUp() {
        if (mFavoritesMode) {
            return;
        }
        if (mSourceKind == AssetSourceKind.REMOTE) {
            if (!mRemoteDirectory.isEmpty()) {
                int idx = mRemoteDirectory.lastIndexOf('/');
                navigateToRemote(idx > 0 ? mRemoteDirectory.substring(0, idx) : "");
            }
            return;
        }
        if (mCurrentDirectory != null && mCurrentDirectory.getParentFile() != null) {
            navigateTo(mCurrentDirectory.getParentFile());
        }
    }

    public void refreshFileList() {
        refreshFileList(null);
    }

    public void refreshIfDisplayingLocalFile(Path file) {
        if (mSourceKind != AssetSourceKind.LOCAL || file == null) return;
        Path normalized = file.toAbsolutePath().normalize();
        for (AssetEntry entry : mCurrentListing.entries()) {
            File localFile = entry.localFile();
            if (localFile != null && localFile.toPath().toAbsolutePath().normalize().equals(normalized)) {
                refreshFileList();
                return;
            }
        }
    }

    public void refreshFileList(Runnable afterRender) {
        refreshFileList(false, afterRender);
    }

    private void refreshFileList(boolean createIfMissing, Runnable afterRender) {
        cancelBrowseRequest();
        mFileContent.setEntries(List.of());
        mItemViews.clear();
        mVisibleEntries.clear();
        mCurrentListing = AssetListing.empty(currentLocation(createIfMissing));

        if (mSourceKind == AssetSourceKind.LOCAL && !mFavoritesMode && mCurrentDirectory == null) return;
        AssetLocation location = currentLocation(createIfMissing);
        AssetRepository repository = AssetRepositoryRegistry.INSTANCE.get(location.sourceKind());
        if (repository == null
                || !repository.supports(AssetRepositoryOperation.BROWSE)
                || (createIfMissing && !repository.supports(AssetRepositoryOperation.CREATE))) {
            renderEntries(AssetListing.failure(location));
            return;
        }
        long generation = ++mBrowseGeneration;
        AssetBrowseRequest request = new AssetBrowseRequest(location,
                new AssetQuery(mSearchQuery, mTagSearchQuery, mViewMode == AssetViewMode.LIST));
        mActiveBrowseRequest = repository.browse(request, result -> post(() -> {
            if (generation != mBrowseGeneration || result == null || !matchesCurrentLocation(result.location())) return;
            mActiveBrowseRequest = AssetRequest.NONE;
            if (result.location() instanceof AssetLocation.Remote remote && result.success()) {
                mRemoteDirectory = remote.directory();
                mPathInput.setText(AssetPathUtils.formatRemotePath(mRemoteDirectory));
                if (mRemoteDirectoryChangedListener != null) mRemoteDirectoryChangedListener.accept(mRemoteDirectory);
            }
            renderEntries(result);
            if (afterRender != null) afterRender.run();
        }));
    }

    private AssetLocation currentLocation(boolean createIfMissing) {
        if (mSourceKind == AssetSourceKind.REMOTE) {
            return new AssetLocation.Remote(mRemoteDirectory, createIfMissing);
        }
        return new AssetLocation.Local(mCurrentDirectory, mFavoritesMode,
                mFavoritesMode ? mFavoriteStore.pathsSnapshot() : List.of());
    }

    private boolean matchesCurrentLocation(AssetLocation location) {
        if (location == null || location.sourceKind() != mSourceKind) return false;
        if (location instanceof AssetLocation.Remote remote) {
            return remote.directory().equals(AssetPathUtils.normalizeRemoteDirectory(mRemoteDirectory));
        }
        if (!(location instanceof AssetLocation.Local local) || local.favorites() != mFavoritesMode) return false;
        return mFavoritesMode || (mCurrentDirectory != null && local.directory() != null
                && pathKey(mCurrentDirectory).equals(pathKey(local.directory())));
    }

    private void cancelBrowseRequest() {
        mBrowseGeneration++;
        mActiveBrowseRequest.cancel();
        mActiveBrowseRequest = AssetRequest.NONE;
    }

    private void renderEntries(AssetListing result) {
        mCurrentListing = result == null ? AssetListing.empty(currentLocation(false)) : result;
        mItemViews.clear();
        mVisibleEntries.clear();
        List<AssetEntry> entries = mCurrentListing.entries();
        Set<String> visibleKeys = new LinkedHashSet<>();

        for (AssetEntry entry : entries) {
            if (mEntryFilter != null && !mEntryFilter.test(entry)) {
                continue;
            }
            mVisibleEntries.add(entry);
            String key = entry.key();
            visibleKeys.add(key);
        }

        boolean selectionChanged = mSelectedPaths.retainAll(visibleKeys);
        mFileContent.setViewMode(mViewMode);
        mScrollView.scrollTo(0, 0);
        mFileContent.setEntries(mVisibleEntries);
        updateVirtualViewport();
        mFileContent.requestLayout();
        mFileContent.invalidate();
        if (selectionChanged || !mSelectedPaths.isEmpty()) notifySelectionChanged();
    }

    private void setViewMode(AssetViewMode mode) {
        if (mViewMode == mode) return;
        mViewMode = mode;
        ConfigManager.INSTANCE.update(config -> config.assetBrowser.viewMode = mode.configValue);
        refreshFileList();
    }

    private void showOptionsMenu(View anchor) {
        dismissActiveMenu();
        // Mount over the whole containing area so the toolbar anchor remains in the host.
        View current = this;
        FrameLayout host = null;
        while (current.getParent() instanceof ViewGroup parent) {
            if (parent instanceof FrameLayout frame) { host = frame; break; }
            current = parent;
        }
        if (host == null) return;
        List<String> options = new ArrayList<>();
        Map<String, String> labels = new HashMap<>();
        for (AssetViewMode mode : AssetViewMode.values()) {
            options.add(mode.configValue);
            labels.put(mode.configValue, viewModeMenuText(mode));
        }
        mViewModeMenu = new UiSearchableMenu(getContext(),
                tr("geometry_node.settings.entry.assetBrowser.viewMode.label"), options, labels, value -> {
            dismissActiveMenu();
            setViewMode(AssetViewMode.fromConfig(value));
        });
        mViewModeMenu.showAt(anchor, host);
    }

    void showMenu(UiContextMenu menu, float localX, float localY) {
        dismissActiveMenu();
        if (menu == null || !menu.hasItems()) return;

        mActiveMenu = menu;
        menu.setOnDismissListener(() -> {
            if (mActiveMenu == menu) mActiveMenu = null;
        });
        menu.showAt(localX, localY, mBodyFrame);
    }

    private void dismissTransientUi() {
        dismissActiveMenu();
    }

    private void dismissActiveMenu() {
        UiSearchableMenu viewMenu = mViewModeMenu;
        mViewModeMenu = null;
        if (viewMenu != null) viewMenu.dismiss();
        UiContextMenu menu = mActiveMenu;
        mActiveMenu = null;
        if (menu != null) menu.dismiss();
    }

    private String viewModeMenuText(AssetViewMode mode) {
        return (mode == mViewMode ? "✓ " : "") + tr("geometry_node.settings.choice.asset_view."
                + mode.configValue.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void onItemPressed(AssetEntry entry, MotionEvent event) {
        requestFocus();
        WorkspaceDragState.clear();
        if (isRightMouse(event)) {
            if (!mSelectedPaths.contains(entry.key())) {
                selectOnly(entry);
            }
            mActionController.showContextMenuAtRaw(event.getRawX(), event.getRawY(), entry);
            return;
        }

        if (event.isCtrlPressed()) {
            toggleSelection(entry);
        } else if (!mSelectedPaths.contains(entry.key())) {
            selectOnly(entry);
        } else {
            syncSelectionViews();
        }
    }

    @Override
    public void onItemDragStarted(AssetEntry entry, MotionEvent event) {
        List<AssetEntry> selectedEntries = getSelectedEntries();
        if (selectedEntries.isEmpty() || !mSelectedPaths.contains(entry.key())) {
            WorkspaceDragState.clear();
            return;
        }
        WorkspaceDragService.INSTANCE.begin(new AssetDragPayload(selectedEntries), WorkspaceDragOperation.IMPORT, this);
    }

    @Override
    public void onItemReleased(AssetEntry entry, MotionEvent event, boolean moved) {
        if (moved && handleInternalDrop(event.getRawX(), event.getRawY())) {
            WorkspaceDragState.clear();
            return;
        }
        if (moved && WorkspaceDragDropRegistry.dispatchDrop(event.getRawX(), event.getRawY())) {
            WorkspaceDragState.clear();
            return;
        }
        WorkspaceDragState.clear();
        if (moved || isRightMouse(event)) return;

        String key = entry.key();
        long now = System.currentTimeMillis();
        if (key.equals(mLastClickedPath) && now - mLastClickTime < 300) {
            mLastClickedPath = null;
            mLastClickTime = 0L;
            mActionController.handleDoubleClick(entry);
        } else {
            mLastClickedPath = key;
            mLastClickTime = now;
        }
    }

    @Override
    public void onItemCancelled(AssetEntry entry, MotionEvent event) {
        WorkspaceDragService.INSTANCE.cancelIfSource(this);
        WorkspaceDragState.clear();
    }

    private void toggleSelection(AssetEntry entry) {
        String key = entry.key();
        if (!mSelectedPaths.remove(key)) {
            mSelectedPaths.add(key);
        }
        syncSelectionViews();
        notifySelectionChanged();
    }

    void selectOnly(AssetEntry entry) {
        if (mSelectedPaths.size() == 1 && mSelectedPaths.contains(entry.key())) {
            syncSelectionViews();
            return;
        }
        mSelectedPaths.clear();
        mSelectedPaths.add(entry.key());
        syncSelectionViews();
        notifySelectionChanged();
    }

    @Override
    public void clearSelection() {
        if (mSelectedPaths.isEmpty()) return;
        mSelectedPaths.clear();
        syncSelectionViews();
        notifySelectionChanged();
    }

    @Override
    public void requestContentFocus() {
        requestFocus();
    }

    private void syncSelectionViews() {
        for (Map.Entry<String, AssetFileItemView> entry : mItemViews.entrySet()) {
            entry.getValue().setSelected(mSelectedPaths.contains(entry.getKey()));
        }
        mFileContent.invalidate();
    }

    void notifySelectionChanged() {
        if (mSelectionChangedListener != null) {
            mSelectionChangedListener.accept(getSelectedEntriesSnapshot());
        }
    }

    @Override
    public void onBoxSelection(RectF selectionRect, boolean additive, Set<String> baseSelection) {
        Set<String> previousSelection = new LinkedHashSet<>(mSelectedPaths);
        if (!additive) {
            mSelectedPaths.clear();
        } else {
            mSelectedPaths.clear();
            mSelectedPaths.addAll(baseSelection);
        }

        mFileContent.collectEntriesIntersecting(selectionRect, mSelectedPaths);
        syncSelectionViews();
        if (!previousSelection.equals(mSelectedPaths)) notifySelectionChanged();
    }

    @Override
    public void onContentRightClick(float rawX, float rawY) {
        requestFocus();
        mActionController.showContextMenuAtRaw(rawX, rawY, null);
    }

    @Override
    public Set<String> selectedPathsSnapshot() {
        return new LinkedHashSet<>(mSelectedPaths);
    }

    @Override
    public void disallowScrollIntercept(boolean disallow) {
        mScrollView.requestDisallowInterceptTouchEvent(disallow);
    }

    @Override
    public AssetFileItemView createItemView(AssetEntry entry) {
        String parentLabel = (mFavoritesMode || !mSearchQuery.isEmpty() || !mTagSearchQuery.isEmpty()) ? parentLabel(entry) : "";
        List<String> tags = mViewMode == AssetViewMode.LIST ? mCurrentListing.tagsFor(entry) : List.of();
        String graphTypeId = mCurrentListing.graphTypeIdFor(entry);
        AssetFileItemView item = new AssetFileItemView(getContext(), entry, mViewMode, displayName(entry), parentLabel,
                tags, graphTypeId, isFavorite(entry), this);
        item.setSelected(mSelectedPaths.contains(entry.key()));
        return item;
    }

    @Override
    public void onMountedItemViewsChanged(Map<String, AssetFileItemView> mountedItems) {
        mItemViews.clear();
        if (mountedItems != null) {
            mItemViews.putAll(mountedItems);
        }
        for (AssetFileItemView view : mItemViews.values()) {
            view.preloadThumbnail();
        }
        syncSelectionViews();
    }

    List<AssetEntry> getSelectedEntries() {
        List<AssetEntry> result = new ArrayList<>();
        for (AssetEntry entry : mVisibleEntries) {
            if (mSelectedPaths.contains(entry.key())) {
                result.add(entry);
            }
        }
        return result;
    }

    List<AssetEntry> visibleEntriesSnapshot() {
        return List.copyOf(mVisibleEntries);
    }

    List<File> getSelectedLocalFiles() {
        List<File> result = new ArrayList<>();
        for (AssetEntry entry : getSelectedEntries()) {
            if (entry.sourceKind() == AssetSourceKind.LOCAL && entry.localFile() != null) {
                result.add(entry.localFile());
            }
        }
        return result;
    }

    public boolean canCopySelection() {
        return mActionController.canCopySelection();
    }

    public boolean canPasteClipboard() {
        return mActionController.canPasteClipboard();
    }

    public boolean canCutSelection() {
        return mActionController.canCutSelection();
    }

    public boolean canDeleteSelection() {
        return mActionController.canDeleteSelection();
    }

    public boolean canRenameSelection() {
        return mActionController.canRenameSelection();
    }

    @Override
    public void onFavoriteToggled(AssetEntry entry) {
        if (entry == null || entry.sourceKind() != AssetSourceKind.LOCAL || entry.localFile() == null
                || !entry.supports(AssetTypeAction.FAVORITE)) {
            return;
        }

        mFavoriteStore.toggle(entry.localFile());
        refreshFileList();
    }

    private boolean isFavorite(AssetEntry entry) {
        if (entry == null || entry.sourceKind() != AssetSourceKind.LOCAL || entry.localFile() == null
                || !entry.supports(AssetTypeAction.FAVORITE)) {
            return false;
        }
        return mFavoriteStore.isFavorite(entry.localFile());
    }

    private boolean handleInternalDrop(float rawX, float rawY) {
        WorkspaceDragState.Session session = WorkspaceDragState.current();
        AssetDragPayload payload = session != null && session.payload() instanceof AssetDragPayload asset
                ? asset : null;
        if (payload == null || payload.entries().isEmpty()) return false;

        AssetEntry target = findDirectoryEntryAt(rawX, rawY);
        if (target == null) return false;

        if (target.sourceKind() == AssetSourceKind.LOCAL) {
            return mActionController.moveLocalEntries(payload.entries(), target);
        }
        if (target.sourceKind() == AssetSourceKind.REMOTE) {
            return mActionController.moveRemoteEntries(payload.entries(), target);
        }
        return false;
    }

    private AssetEntry findDirectoryEntryAt(float rawX, float rawY) {
        AssetEntry candidate = mFileContent.entryAtRaw(rawX, rawY);
        return candidate != null && candidate.isDirectory() ? candidate : null;
    }

    public boolean repositorySupports(AssetSourceKind source, AssetRepositoryOperation operation) {
        AssetRepository repository = AssetRepositoryRegistry.INSTANCE.get(source);
        return repository != null && repository.supports(operation);
    }

    void clearSearch() {
        boolean hadSearch = !mSearchQuery.isEmpty()
                || !mTagSearchQuery.isEmpty()
                || !mPendingSearchQuery.isEmpty()
                || !mPendingTagSearchQuery.isEmpty();
        mSearchInput.setText("");
        mTagSearchInput.setText("");
        mPendingSearchQuery = "";
        mPendingTagSearchQuery = "";
        mSearchQuery = "";
        mTagSearchQuery = "";
        if (hadSearch) {
            refreshFileList();
        }
    }

    Consumer<AssetEntry> pickFileAction() {
        return mPickFileAction;
    }

    Runnable pickCurrentDirectoryAction() {
        return mPickCurrentDirectoryAction;
    }

    FrameLayout bodyFrame() {
        return mBodyFrame;
    }

    ScrollView scrollView() {
        return mScrollView;
    }

    FileContentLayout fileContent() {
        return mFileContent;
    }

    Map<String, AssetFileItemView> itemViews() {
        return mItemViews;
    }

    private UiIconButton createIconButton(Context context, View icon, String description) {
        UiIconButton.Style style = new UiIconButton.Style(UiTheme.Metrics.RADIUS_INPUT, 0,
                UiTheme.Control.ICON, UiTheme.Control.ICON_HOVER, UiTheme.Control.ICON_PRESSED,
                UiTheme.Surface.PANEL, 0, 16.0f, UiTheme.Metrics.DISABLED_ALPHA);
        return new UiIconButton(context, icon, style).tooltip(description);
    }

    private static String tr(String translationKey) {
        return Component.translatable(translationKey).getString();
    }

    private EditText createNavInput(Context context) {
        UiTextInput input = new UiTextInput(context, new UiTextInput.Style(
                TEXT_SIZE_NAV, 10, UiTheme.Metrics.RADIUS_INPUT, UiTheme.Metrics.BORDER,
                UiTheme.Text.SECONDARY, UiTheme.Text.HINT, UiTheme.Control.INPUT, UiTheme.Control.INPUT_BORDER));
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setSingleLine(true);
        return input;
    }

    private String parentLabel(AssetEntry entry) {
        String relative = entry.path();
        int idx = Math.max(relative.lastIndexOf('/'), relative.lastIndexOf('\\'));
        return idx > 0 ? relative.substring(0, idx) : "";
    }

    private String displayName(AssetEntry entry) {
        if (mViewMode == AssetViewMode.LIST) return entry.name();
        return shortenMiddle(entry.name(), mViewMode.iconNameLimit);
    }

    private String shortenMiddle(String text, int maxChars) {
        if (maxChars <= 0 || text.length() <= maxChars) return text;
        int keepStart = Math.max(1, (maxChars - 1) / 2);
        int keepEnd = Math.max(1, maxChars - 1 - keepStart);
        return text.substring(0, keepStart) + "…" + text.substring(text.length() - keepEnd);
    }

    void updateVirtualViewport() {
        if (mFileContent == null || mScrollView == null) return;
        mFileContent.updateViewport(mScrollView.getScrollY(), mScrollView.getHeight());
    }

    private boolean isRightMouse(MotionEvent e) {
        return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                || e.getActionButton() == MotionEvent.BUTTON_SECONDARY;
    }

    String pathKey(File file) {
        return mFavoriteStore.pathKey(file);
    }

    static ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    static ShapeDrawable createRectDrawable(int color, float radiusDp) {
        return createRectDrawable(color, radiusDp, 0, 0);
    }

    static ShapeDrawable createRectDrawable(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = createColorDrawable(color);
        drawable.setCornerRadius(dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }
}
