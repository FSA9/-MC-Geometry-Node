package com.mine.geometry_node.client.ui.components.integration.asset.browser;

import com.mine.geometry_node.client.feature.asset.favorites.AssetFavoriteStore;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.composite.menu.UiSearchableMenu;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableViewport;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.feature.asset.navigation.AssetQuickAccessStore;
import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;
import com.mine.geometry_node.client.feature.asset.path.AssetPathUtils;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.components.integration.asset.action.AssetLibraryActionId;
import com.mine.geometry_node.client.ui.components.integration.asset.action.AssetLibraryActionRegistry;
import com.mine.geometry_node.client.ui.components.integration.asset.drag.AssetDragPayload;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragDropRegistry;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragOperation;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragState;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragService;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.feature.asset.repository.AssetBrowseRequest;
import com.mine.geometry_node.client.feature.asset.repository.AssetListing;
import com.mine.geometry_node.client.feature.asset.repository.AssetLocation;
import com.mine.geometry_node.client.feature.asset.repository.AssetQuery;
import com.mine.geometry_node.client.feature.asset.repository.AssetRepository;
import com.mine.geometry_node.client.feature.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.feature.asset.repository.AssetRepositoryRegistry;
import com.mine.geometry_node.client.feature.asset.repository.AssetRequest;
import com.mine.geometry_node.client.feature.asset.file.LocalAssetService;
import com.mine.geometry_node.client.ui.components.integration.asset.task.AssetTaskController;
import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
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
import java.util.function.Function;
import java.util.function.Predicate;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

public class AssetFileBrowserPanel extends LinearLayout implements AssetFileItemView.Listener, FileContentLayout.SelectionHost {

    private final AssetBrowserCoordinator mCoordinator;
    private final EditText mPathInput;
    private final EditText mSearchInput;
    private final FrameLayout mBodyFrame;
    private final ScrollView mScrollView;
    private final FileContentLayout mFileContent;
    private final AssetListTable mListTable;
    private final LinearLayout mTableLayout;
    private final UiTableViewport mTableViewport;
    private final Map<String, AssetFileItemView> mItemViews = new HashMap<>();
    private final Set<String> mSelectedPaths = new LinkedHashSet<>();
    private final List<AssetEntry> mVisibleEntries = new ArrayList<>();
    private final AssetFavoriteStore mFavoriteStore = new AssetFavoriteStore();
    private final LocalAssetService mLocalAssetService = new LocalAssetService();
    private final ScopedKeyManager<AssetLibraryActionId, AssetFileBrowserPanel> mKeyManager;
    private final AssetTaskController mIoTasks;
    private final AssetBrowserActionController mActionController;
    private UiContextMenu mActiveMenu;
    private UiSearchableMenu mViewModeMenu;
    private AssetBrowserStatusBar mStatusBar;
    private final AssetQuickAccessStore mQuickAccessStore = new AssetQuickAccessStore();

    private File mCurrentDirectory;
    private String mRemoteDirectory = "";
    private AssetSourceKind mSourceKind = AssetSourceKind.LOCAL;
    private boolean mFavoritesMode = false;
    private AssetViewMode mViewMode;
    private String mSearchQuery = "";
    private String mPendingSearchQuery = "";
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
    private Set<AssetSourceKind> mAllowedSources = Set.of(AssetSourceKind.LOCAL, AssetSourceKind.REMOTE);
    private boolean mAllowCreateFolders = true;
    private boolean mAllowCreateFiles = true;
    private boolean mVerifiedCurrentDirectory;
    private boolean mLoading;

    private static final float NAV_BAR_HEIGHT = 76.0f;
    private static final float NAV_ROW_HEIGHT = 32.0f;
    private static final float NAV_BUTTON_SIZE = 30.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final int COLOR_TOOLBAR_BG = UiThemes.EDITOR.surface().sidebar();

    public AssetFileBrowserPanel(Context context, AssetBrowserCoordinator coordinator) {
        this(context, coordinator, ignored -> AssetBrowserDocumentActions.NONE);
    }

    public AssetFileBrowserPanel(Context context, AssetBrowserCoordinator coordinator,
                                 Function<AssetTaskController, AssetBrowserDocumentActions> documentActions) {
        this(context, coordinator, true, true, true, true, false, documentActions);
    }

    public AssetFileBrowserPanel(Context context) {
        this(context, null, false, false, true, false, true, ignored -> AssetBrowserDocumentActions.NONE);
    }

    public static AssetFileBrowserPanel picker(Context context, AssetBrowserCoordinator coordinator) {
        AssetFileBrowserPanel panel = new AssetFileBrowserPanel(context, coordinator, true, false, false, false, true,
                ignored -> AssetBrowserDocumentActions.NONE);
        panel.setCreationAllowed(false, false);
        return panel;
    }

    private AssetFileBrowserPanel(
            Context context,
            AssetBrowserCoordinator coordinator,
            boolean enableQuickAccessAdd,
            boolean enableLocalFileActions,
            boolean enableRemoteTransferActions,
            boolean openLocalJsonOnDoubleClick,
            boolean showPickerContextActions,
            Function<AssetTaskController, AssetBrowserDocumentActions> documentActions
    ) {
        super(context);
        mCoordinator = coordinator;
        mEnableQuickAccessAdd = enableQuickAccessAdd;
        mEnableLocalFileActions = enableLocalFileActions;
        mEnableRemoteTransferActions = enableRemoteTransferActions;
        mOpenLocalJsonOnDoubleClick = openLocalJsonOnDoubleClick;
        mShowPickerContextActions = showPickerContextActions;
        mIoTasks = new AssetTaskController(this);
        AssetBrowserDocumentActions documents = java.util.Objects.requireNonNull(documentActions.apply(mIoTasks));
        mAllowCreateFiles = documents != AssetBrowserDocumentActions.NONE;
        mActionController = createActionController(documents);
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

        addSearchButton(searchRow, context);
        addClearSearchButton(searchRow, context);

        mBodyFrame = new FrameLayout(context);
        mScrollView = new UiScrollView(context);
        mFileContent = new FileContentLayout(context, this);
        mListTable = new AssetListTable(context, this, () -> renderEntries(mCurrentListing), checked -> {
            mSelectedPaths.clear();
            if (checked) for (AssetEntry entry : mVisibleEntries) mSelectedPaths.add(entry.key());
            syncSelectionViews();
            notifySelectionChanged();
        });
        mTableLayout = new LinearLayout(context);
        mTableLayout.setOrientation(LinearLayout.VERTICAL);
        mTableLayout.addView(mListTable.header, new LinearLayout.LayoutParams(-1, dp2pxInt(UiTableStyle.HEADER_HEIGHT)));
        mTableViewport = new UiTableViewport(context, mListTable.columns, mTableLayout);
        configureBody();
        mStatusBar = new AssetBrowserStatusBar(context, this::setViewMode);
        addView(mStatusBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(30)));
        updateStatus();
    }

    private AssetBrowserActionController createActionController(AssetBrowserDocumentActions documents) {
        return new AssetBrowserActionController(
                this,
                mCoordinator,
                mLocalAssetService,
                documents,
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
                new SvgIconView(context, SvgIconView.Icon.ARROW_UP, UiThemes.EDITOR.text().normal()),
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
                try {
                    File local = new File(path);
                    if (AssetPathUtils.isRemotePathInput(path)) {
                        navigateToRemote(AssetPathUtils.remotePathFromInput(path));
                    } else if (local.isAbsolute() && local.isDirectory()) {
                        navigateTo(local);
                    } else if (mSourceKind == AssetSourceKind.REMOTE) {
                        navigateToRemote(AssetPathUtils.remotePathFromInput(path));
                    } else if (local.isDirectory()) {
                        navigateTo(local);
                    }
                } catch (IllegalArgumentException invalidAddress) {
                    // Invalid input never replaces the last verified navigation state.
                }
                input.setText(mSourceKind == AssetSourceKind.REMOTE
                        ? AssetPathUtils.formatRemotePath(mRemoteDirectory)
                        : mCurrentDirectory != null ? mCurrentDirectory.getAbsolutePath() : "");
                input.clearFocus();
                return true;
            }
            return false;
        });
        return input;
    }

    private void addQuickAccessButton(LinearLayout navBar, Context context) {
        UiIconButton btnAdd = createIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.PLUS, UiThemes.EDITOR.text().normal()),
                tr("geometry_node.asset_library.toolbar.add_quick_access"));
        btnAdd.setOnClickListener(v -> {
            if (!mEnableQuickAccessAdd || mCoordinator == null) return;
            if (hasVerifiedCurrentDirectory() && mQuickAccessStore.add(locationSnapshot())) {
                mCoordinator.notifySidebarChanged();
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

    private FrameLayout menuHost() {
        View current = this;
        while (current.getParent() instanceof ViewGroup parent) {
            if (parent instanceof FrameLayout frame) return frame;
            current = parent;
        }
        return null;
    }

    private void addSearchButton(LinearLayout navBar, Context context) {
        UiIconButton btnSearch = createIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.SEARCH, UiThemes.EDITOR.text().normal()),
                tr("geometry_node.asset_library.toolbar.search"));
        UiSearchSubmit.bind(btnSearch, this::applySearch, mSearchInput);
        LinearLayout.LayoutParams searchButtonLp = new LinearLayout.LayoutParams(dp2pxInt(NAV_BUTTON_SIZE), ViewGroup.LayoutParams.MATCH_PARENT);
        navBar.addView(btnSearch, searchButtonLp);
    }

    private void addClearSearchButton(LinearLayout navBar, Context context) {
        UiIconButton btnClearSearch = createIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.CLOSE, UiThemes.EDITOR.text().normal()),
                tr("geometry_node.asset_library.toolbar.clear_search"));
        btnClearSearch.setOnClickListener(v -> clearSearch());
        LinearLayout.LayoutParams clearButtonLp = new LinearLayout.LayoutParams(dp2pxInt(NAV_BUTTON_SIZE), ViewGroup.LayoutParams.MATCH_PARENT);
        clearButtonLp.setMargins(dp2pxInt(4), 0, 0, 0);
        navBar.addView(btnClearSearch, clearButtonLp);
    }

    private void addOptionsButton(LinearLayout navBar, Context context) {
        SvgIconView optionsIcon = new SvgIconView(context, SvgIconView.Icon.MORE_VERTICAL,
                UiThemes.EDITOR.text().normal());
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
        mBodyFrame.addView(mTableViewport, new FrameLayout.LayoutParams(-1, -1));
        updateBodyMode();
        addView(mBodyFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
    }

    private void updateBodyMode() {
        boolean list = mViewMode == AssetViewMode.LIST;
        ViewGroup target = list ? mTableLayout : mBodyFrame;
        if (mScrollView.getParent() != target) {
            if (mScrollView.getParent() instanceof ViewGroup previous) previous.removeView(mScrollView);
            if (list) mTableLayout.addView(mScrollView, new LinearLayout.LayoutParams(-1, 0, 1));
            else mBodyFrame.addView(mScrollView, new FrameLayout.LayoutParams(-1, -1));
        }
        mTableViewport.setVisibility(list ? View.VISIBLE : View.GONE);
        if (!list) mListTable.dismiss();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        activatePanel();
    }

    @Override
    protected void onDetachedFromWindow() {
        WorkspaceDragService.INSTANCE.cancelIfSource(this);
        mActionController.clearClipboard();
        deactivatePanel();
        super.onDetachedFromWindow();
    }

    public void activatePanel() {
        mIoTasks.activate();
        mActionController.activateRemoteRequests();
        mKeyManager.attach();
    }

    public void deactivatePanel() {
        mVerifiedCurrentDirectory = false;
        mLoading = false;
        mIoTasks.deactivate();
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
            case SELECT_ALL -> {
                mSelectedPaths.clear();
                for (AssetEntry entry : mVisibleEntries) mSelectedPaths.add(entry.key());
                syncSelectionViews();
                notifySelectionChanged();
            }
        }
    }

    private void applySearch() {
        mSearchQuery = mPendingSearchQuery;
        refreshFileList();
        mSearchInput.clearFocus();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mFileContent != null) {
            mFileContent.setMinimumContentHeight(mScrollView.getHeight());
            updateVirtualViewport();
        }
    }

    public void navigateTo(File directory) {
        if (!mAllowedSources.contains(AssetSourceKind.LOCAL)) return;
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
        if (!mAllowedSources.contains(AssetSourceKind.REMOTE)) return;
        mFavoritesMode = false;
        mSourceKind = AssetSourceKind.REMOTE;
        mRemoteDirectory = AssetPathUtils.normalizeRemoteDirectory(directory);
        mPathInput.setText(AssetPathUtils.formatRemotePath(mRemoteDirectory));
        clearSelection();
        refreshFileList(createIfMissing, null);
        notifyLocationChanged();
    }

    public void navigateToFavorites() {
        if (!mAllowedSources.contains(AssetSourceKind.LOCAL)) return;
        mFavoritesMode = true;
        mSourceKind = AssetSourceKind.LOCAL;
        mPathInput.setText(tr("geometry_node.asset_library.toolbar.favorites"));
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
        renderEntries(mCurrentListing);
    }

    /** Applies at the navigation boundary, including path input, favorites and keyboard actions. */
    public void setAllowedSources(Set<AssetSourceKind> sources) {
        Set<AssetSourceKind> allowed = Set.copyOf(sources);
        if (allowed.isEmpty()) throw new IllegalArgumentException("At least one asset source is required");
        mAllowedSources = allowed;
        if (!allowed.contains(mSourceKind)) {
            mVerifiedCurrentDirectory = false;
            cancelBrowseRequest();
            mCurrentDirectory = null;
            mFavoritesMode = false;
            mPathInput.setText("");
            clearSelection();
            renderEntries(AssetListing.empty(null));
        }
    }

    public void setCreationAllowed(boolean folders, boolean files) {
        mAllowCreateFolders = folders;
        mAllowCreateFiles = files;
    }

    boolean creationAllowed(boolean folder) {
        return folder ? mAllowCreateFolders : mAllowCreateFiles;
    }

    /** False while browsing, after a failed listing, or when no concrete directory is selected. */
    public boolean hasVerifiedCurrentDirectory() {
        return mVerifiedCurrentDirectory && !mFavoritesMode && mAllowedSources.contains(mSourceKind);
    }

    private void navigateUp() {
        if (mFavoritesMode) {
            return;
        }
        if (mSourceKind == AssetSourceKind.REMOTE) {
            if (!AssetRepositoryPath.parse(mRemoteDirectory, true).isRoot()) {
                navigateToRemote(AssetPathUtils.parentRemoteDirectory(mRemoteDirectory));
            }
            return;
        }
        if (mCurrentDirectory != null && mCurrentDirectory.getParentFile() != null
                && !com.mine.geometry_node.client.platform.ClientPaths.sameFile(mCurrentDirectory, AssetBrowserPathPolicy.getCommonDir())
                && !com.mine.geometry_node.client.platform.ClientPaths.sameFile(mCurrentDirectory, AssetBrowserPathPolicy.getPacksDir())) {
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
        mVerifiedCurrentDirectory = false;
        mLoading = true;
        mFileContent.setEntries(List.of());
        mItemViews.clear();
        mVisibleEntries.clear();
        mCurrentListing = AssetListing.empty(currentLocation(createIfMissing));
        updateStatus();

        if (!mAllowedSources.contains(mSourceKind)
                || mSourceKind == AssetSourceKind.LOCAL && !mFavoritesMode && mCurrentDirectory == null) {
            mLoading = false;
            updateStatus();
            return;
        }
        AssetLocation location = currentLocation(createIfMissing);
        AssetRepository repository = AssetRepositoryRegistry.INSTANCE.get(location.sourceKind());
        if (repository == null
                || !repository.supports(AssetRepositoryOperation.BROWSE)
                || (createIfMissing && !repository.supports(AssetRepositoryOperation.CREATE))) {
            mLoading = false;
            renderEntries(AssetListing.failure(location));
            return;
        }
        long generation = ++mBrowseGeneration;
        AssetBrowseRequest request = new AssetBrowseRequest(location,
                new AssetQuery(mSearchQuery, "", mViewMode == AssetViewMode.LIST));
        mActiveBrowseRequest = repository.browse(request, result -> post(() -> {
            if (generation != mBrowseGeneration || result == null || !matchesCurrentLocation(result.location())) return;
            mActiveBrowseRequest = AssetRequest.NONE;
            mLoading = false;
            mVerifiedCurrentDirectory = result.success() && !mFavoritesMode;
            if (result.location() instanceof AssetLocation.Remote remote && result.success()) {
                mRemoteDirectory = remote.directory();
                mPathInput.setText(AssetPathUtils.formatRemotePath(mRemoteDirectory));
                if (mRemoteDirectoryChangedListener != null) mRemoteDirectoryChangedListener.accept(mRemoteDirectory);
            }
            renderEntries(result);
            if (afterRender != null) afterRender.run();
        }));
    }

    public AssetLocation locationSnapshot() {
        return currentLocation(false);
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
        mListTable.setEntries(entries);
        Set<String> favoriteKeys = new java.util.HashSet<>(mFavoriteStore.pathsSnapshot());
        Predicate<AssetEntry> favorite = entry -> entry.sourceKind() == AssetSourceKind.LOCAL
                && entry.supports(AssetTypeAction.FAVORITE) && favoriteKeys.contains(entry.key());
        Set<String> visibleKeys = new LinkedHashSet<>();

        for (AssetEntry entry : entries) {
            if (!mAllowedSources.contains(entry.sourceKind())) continue;
            if (mViewMode == AssetViewMode.LIST && !mListTable.query().matches(entry, mCurrentListing, favorite.test(entry))) continue;
            if (mEntryFilter != null && !mEntryFilter.test(entry)) {
                continue;
            }
            mVisibleEntries.add(entry);
            String key = entry.key();
            visibleKeys.add(key);
        }

        mVisibleEntries.sort(mListTable.query().comparator(mCurrentListing, favorite));
        boolean selectionChanged = mSelectedPaths.retainAll(visibleKeys);
        updateBodyMode();
        mFileContent.setViewMode(mViewMode);
        mScrollView.scrollTo(0, 0);
        mFileContent.setEntries(mVisibleEntries);
        updateVirtualViewport();
        mFileContent.requestLayout();
        mFileContent.invalidate();
        updateStatus();
        if (selectionChanged || !mSelectedPaths.isEmpty()) notifySelectionChanged();
    }

    private void setViewMode(AssetViewMode mode) {
        if (mViewMode == mode) return;
        mViewMode = mode;
        updateBodyMode();
        updateStatus();
        ConfigManager.INSTANCE.update(config -> config.assetBrowser.viewMode = mode.configValue);
        refreshFileList();
    }

    private void showOptionsMenu(View anchor) {
        dismissActiveMenu();
        FrameLayout host = menuHost();
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
        if (mListTable != null) mListTable.dismiss();
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

    @Override
    public void onItemSelectionToggled(AssetEntry entry) { toggleSelection(entry); }

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
        updateStatus();
        for (Map.Entry<String, AssetFileItemView> entry : mItemViews.entrySet()) {
            entry.getValue().setSelected(mSelectedPaths.contains(entry.getKey()));
        }
        mFileContent.invalidate();
    }

    private void updateStatus() {
        if (mListTable != null) mListTable.updateSelection(mSelectedPaths.size(), mVisibleEntries.size());
        if (mStatusBar != null) mStatusBar.update(mVisibleEntries.size(), mSelectedPaths.size(), mViewMode,
                mLoading, mCurrentListing.success());
    }

    void notifySelectionChanged() {
        updateStatus();
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
        String parentLabel = (mFavoritesMode || !mSearchQuery.isEmpty()) ? parentLabel(entry) : "";
        List<String> tags = mViewMode == AssetViewMode.LIST ? mCurrentListing.tagsFor(entry) : List.of();
        String graphTypeId = mCurrentListing.graphTypeIdFor(entry);
        AssetFileItemView item = new AssetFileItemView(getContext(), entry, mViewMode, displayName(entry), parentLabel,
                tags, graphTypeId, isFavorite(entry), this, mListTable.columns);
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
        if (!mAllowedSources.contains(source)) return false;
        AssetRepository repository = AssetRepositoryRegistry.INSTANCE.get(source);
        return repository != null && repository.supports(operation);
    }

    void clearSearch() {
        boolean hadSearch = !mSearchQuery.isEmpty() || !mPendingSearchQuery.isEmpty() || mListTable.query().active();
        mSearchInput.setText("");
        mPendingSearchQuery = "";
        mSearchQuery = "";
        mListTable.clear();
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
        return UiIconButton.createThemed(context, UiThemes.EDITOR, icon).tooltip(description);
    }

    private static String tr(String translationKey) {
        return Component.translatable(translationKey).getString();
    }

    private EditText createNavInput(Context context) {
        UiTextInput input = new UiTextInput(context, new UiTextInput.Style(
                TEXT_SIZE_NAV, 10, UiMetrics.RADIUS_INPUT, UiMetrics.BORDER,
                UiThemes.EDITOR.text().secondary(), UiThemes.EDITOR.text().hint(), UiThemes.EDITOR.control().input(), UiThemes.EDITOR.control().inputBorder()));
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
        mFileContent.setMinimumContentHeight(mScrollView.getHeight());
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
