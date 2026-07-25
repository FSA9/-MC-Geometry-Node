package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetPathUtils;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.action.AssetLibraryActionId;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.action.AssetLibraryActionRegistry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.drag.AssetDragState;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.drag.AssetDragDropRegistry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.service.GraphAssetService;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.service.LocalAssetService;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.task.AssetTaskController;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedKeyManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphEntry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphListRequest;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

public class RightFileBrowserPanel extends LinearLayout implements AssetFileItemView.Listener, FileContentLayout.SelectionHost {

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
    private final AssetEntryLoader mEntryLoader = new AssetEntryLoader();
    private final GraphFavoriteStore mFavoriteStore = new GraphFavoriteStore();
    private final LocalAssetService mLocalAssetService = new LocalAssetService();
    private final GraphAssetService mGraphAssetService = new GraphAssetService();
    private final ScopedKeyManager<AssetLibraryActionId, RightFileBrowserPanel> mKeyManager;
    private final AssetTaskController mIoTasks;
    private final AssetBrowserActionController mActionController;

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
    private int mActiveRemoteListRequestId = 0;
    private int mActiveLocalLoadRequestId = 0;
    private Future<?> mActiveLocalLoad;
    private AssetEntryLoader.Result mCurrentEntryResult = AssetEntryLoader.Result.empty();
    private final boolean mEnableQuickAccessAdd;
    private final boolean mEnableLocalFileActions;
    private final boolean mEnableRemoteTransferActions;
    private final boolean mOpenLocalJsonOnDoubleClick;
    private final boolean mShowPickerContextActions;
    private Consumer<File> mLocalDirectoryChangedListener;
    private Consumer<String> mRemoteDirectoryChangedListener;
    private Runnable mPickCurrentDirectoryAction;
    private Consumer<AssetEntry> mPickFileAction;
    private Predicate<AssetEntry> mEntryFilter;

    private static final ExecutorService LOCAL_LOAD_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-AssetBrowser-Loader");
        thread.setDaemon(true);
        return thread;
    });

    private static final float NAV_BAR_HEIGHT = 40.0f;
    private static final float BTN_ADD_WIDTH = 40.0f;
    private static final float BTN_MENU_WIDTH = 34.0f;
    private static final float SEARCH_WIDTH = 170.0f;
    private static final float TAG_SEARCH_WIDTH = 150.0f;
    private static final float SEARCH_BUTTON_WIDTH = 34.0f;
    private static final float CLEAR_BUTTON_WIDTH = 34.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final float TEXT_SIZE_BTN_ADD = 14.0f;

    public RightFileBrowserPanel(Context context, AssetBrowserCoordinator coordinator) {
        this(context, coordinator, true, true, true, true, false);
    }

    public RightFileBrowserPanel(Context context) {
        this(context, null, false, false, true, false, true);
    }

    public static RightFileBrowserPanel picker(Context context, AssetBrowserCoordinator coordinator) {
        return new RightFileBrowserPanel(context, coordinator, false, false, false, false, true);
    }

    private RightFileBrowserPanel(
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
        addUpButton(navBar, context);

        mPathInput = createPathInput(context);
        navBar.addView(mPathInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        addQuickAccessButton(navBar, context);

        mSearchInput = createSearchInput(context, "搜索名称", value -> mPendingSearchQuery = value);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp2pxInt(SEARCH_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        searchLp.setMargins(dp2pxInt(6), dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(mSearchInput, searchLp);

        mTagSearchInput = createSearchInput(context, "搜索标签", value -> mPendingTagSearchQuery = value);
        LinearLayout.LayoutParams tagSearchLp = new LinearLayout.LayoutParams(dp2pxInt(TAG_SEARCH_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        tagSearchLp.setMargins(0, dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(mTagSearchInput, tagSearchLp);

        addSearchButton(navBar, context);
        addClearSearchButton(navBar, context);
        addOptionsButton(navBar, context);

        mBodyFrame = new FrameLayout(context);
        mScrollView = new ScrollView(context);
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
                mEntryLoader,
                mIoTasks,
                mEnableLocalFileActions,
                mEnableRemoteTransferActions,
                mOpenLocalJsonOnDoubleClick,
                mShowPickerContextActions
        );
    }

    private ScopedKeyManager<AssetLibraryActionId, RightFileBrowserPanel> createKeyManager() {
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
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setBackground(createColorDrawable(0xFF2A2A2A));
        return navBar;
    }

    private void addNavBar(LinearLayout navBar) {
        addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(NAV_BAR_HEIGHT)));
    }

    private void addUpButton(LinearLayout navBar, Context context) {
        TextView btnUp = createNavButton(context, "⬆ 向上");
        btnUp.setOnClickListener(v -> navigateUp());
        navBar.addView(btnUp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private EditText createPathInput(Context context) {
        EditText input = createNavInput(context);
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                if (mFavoritesMode) {
                    input.setText("我的收藏");
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
        TextView btnAdd = createNavButton(context, "+");
        btnAdd.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(TEXT_SIZE_BTN_ADD));
        btnAdd.setBackground(createColorDrawable(0xFF3A3A3A));
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
        navBar.addView(btnAdd, new LinearLayout.LayoutParams(dp2pxInt(BTN_ADD_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));
        btnAdd.setVisibility(mEnableQuickAccessAdd ? View.VISIBLE : View.GONE);
    }

    private EditText createSearchInput(Context context, String hint, Consumer<String> pendingQuerySetter) {
        EditText input = createSearchInput(context);
        input.setHint(hint);
        input.setHintTextColor(0xFF666666);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                pendingQuerySetter.accept(s.toString().trim());
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                applySearch();
                return true;
            }
            return false;
        });
        return input;
    }

    private void addSearchButton(LinearLayout navBar, Context context) {
        TextView btnSearch = createIconButton(context, "⌕");
        btnSearch.setOnClickListener(v -> applySearch());
        LinearLayout.LayoutParams searchButtonLp = new LinearLayout.LayoutParams(dp2pxInt(SEARCH_BUTTON_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        searchButtonLp.setMargins(0, dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(btnSearch, searchButtonLp);
    }

    private void addClearSearchButton(LinearLayout navBar, Context context) {
        TextView btnClearSearch = createIconButton(context, "×");
        btnClearSearch.setOnClickListener(v -> clearSearch());
        LinearLayout.LayoutParams clearButtonLp = new LinearLayout.LayoutParams(dp2pxInt(CLEAR_BUTTON_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        clearButtonLp.setMargins(0, dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(btnClearSearch, clearButtonLp);
    }

    private void addOptionsButton(LinearLayout navBar, Context context) {
        TextView btnOptions = createNavButton(context, "⋮");
        btnOptions.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(18));
        btnOptions.setPadding(0, 0, 0, 0);
        btnOptions.setBackground(createColorDrawable(0xFF3A3A3A));
        btnOptions.setOnClickListener(v -> showOptionsMenu(btnOptions));
        navBar.addView(btnOptions, new LinearLayout.LayoutParams(dp2pxInt(BTN_MENU_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onDetachedFromWindow() {
        mIoTasks.cancelAll();
        mKeyManager.dispose();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View focusedView = findFocus();
            if (focusedView instanceof EditText) return super.dispatchKeyEvent(event);
            if (mKeyManager.onKeyDown(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void executeAction(AssetLibraryActionId actionId) {
        if (actionId == null) return;
        switch (actionId) {
            case COPY -> mActionController.copySelectionToClipboard();
            case PASTE -> mActionController.pasteClipboard();
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
        refreshRemoteFileList(createIfMissing);
    }

    public void navigateToFavorites() {
        mFavoritesMode = true;
        mSourceKind = AssetSourceKind.LOCAL;
        mPathInput.setText("我的收藏");
        clearSelection();
        refreshFileList();
    }

    public void createRemoteDirectoryFromInput() {
        navigateToRemote(AssetPathUtils.remotePathFromInput(mPathInput.getText().toString()), true);
    }

    public void createRemoteFolderInCurrentDirectory() {
        String name = "新建文件夹";
        String target = mRemoteDirectory.isEmpty() ? name : mRemoteDirectory + "/" + name;
        navigateToRemote(target, true);
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

    public File getCurrentDirectory() {
        return mCurrentDirectory;
    }

    public AssetSourceKind getSourceKind() {
        return mSourceKind;
    }

    public String getRemoteDirectory() {
        return mRemoteDirectory;
    }

    public List<AssetEntry> getSelectedEntriesSnapshot() {
        return new ArrayList<>(getSelectedEntries());
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

    void refreshFileList(Runnable afterRender) {
        cancelLocalLoad();
        mFileContent.setEntries(List.of());
        mItemViews.clear();
        mVisibleEntries.clear();
        mCurrentEntryResult = AssetEntryLoader.Result.empty();

        if (mSourceKind == AssetSourceKind.REMOTE) {
            refreshRemoteFileList(false);
            return;
        }

        if (!mFavoritesMode && mCurrentDirectory == null) return;

        int requestId = ++mActiveLocalLoadRequestId;
        boolean requestedFavoritesMode = mFavoritesMode;
        File requestedDirectory = mCurrentDirectory;
        String requestedDirectoryKey = requestedDirectory == null ? "" : pathKey(requestedDirectory);
        AssetEntryLoader.Query query = new AssetEntryLoader.Query(mSearchQuery, mTagSearchQuery);
        boolean includeTags = mViewMode == AssetViewMode.LIST;
        List<String> favoritePaths = requestedFavoritesMode ? mFavoriteStore.pathsSnapshot() : List.of();

        mActiveLocalLoad = LOCAL_LOAD_EXECUTOR.submit(() -> {
            AssetEntryLoader.Result result;
            try {
                result = requestedFavoritesMode
                        ? mEntryLoader.loadFavorites(favoritePaths, query, includeTags)
                        : mEntryLoader.loadCurrentDirectory(requestedDirectory, query, includeTags);
            } catch (Exception e) {
                e.printStackTrace();
                result = AssetEntryLoader.Result.empty();
            }

            if (Thread.currentThread().isInterrupted()) return;
            AssetEntryLoader.Result finalResult = result;
            post(() -> {
                if (requestId != mActiveLocalLoadRequestId || mSourceKind != AssetSourceKind.LOCAL || mFavoritesMode != requestedFavoritesMode) return;
                if (!requestedFavoritesMode && (mCurrentDirectory == null || !requestedDirectoryKey.equals(pathKey(mCurrentDirectory)))) return;
                renderEntries(finalResult);
                if (afterRender != null) {
                    afterRender.run();
                }
            });
        });
    }

    private void refreshRemoteFileList(boolean createIfMissing) {
        cancelLocalLoad();
        renderEntries(AssetEntryLoader.Result.empty());
        int requestId = RemoteGraphClientState.nextRequestId();
        mActiveRemoteListRequestId = requestId;
        String requestedDirectory = AssetPathUtils.normalizeRemoteDirectory(mRemoteDirectory);
        RemoteGraphClientState.onList(requestId, response -> {
            post(() -> {
                if (mSourceKind != AssetSourceKind.REMOTE || requestId != mActiveRemoteListRequestId) return;
                if (!response.success()) {
                    renderEntries(AssetEntryLoader.Result.empty());
                    return;
                }
                String responseDirectory = AssetPathUtils.normalizeRemoteDirectory(response.directory());
                if (!responseDirectory.equals(requestedDirectory) || !responseDirectory.equals(mRemoteDirectory)) return;
                mRemoteDirectory = responseDirectory;
                mPathInput.setText(AssetPathUtils.formatRemotePath(mRemoteDirectory));
                if (mRemoteDirectoryChangedListener != null) {
                    mRemoteDirectoryChangedListener.accept(mRemoteDirectory);
                }
                List<AssetEntry> entries = new ArrayList<>();
                for (RemoteGraphEntry entry : response.entries()) {
                    if (!mTagSearchQuery.isEmpty()) {
                        continue;
                    }
                    if (!mSearchQuery.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(mSearchQuery.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    entries.add(AssetEntry.remote(entry.path(), entry.name(), entry.directory(), entry.size()));
                }
                renderEntries(AssetEntryLoader.Result.entriesOnly(entries));
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphListRequest(requestId, requestedDirectory, createIfMissing));
    }

    private void cancelLocalLoad() {
        mActiveLocalLoadRequestId++;
        if (mActiveLocalLoad != null) {
            mActiveLocalLoad.cancel(true);
            mActiveLocalLoad = null;
        }
    }

    private void renderEntries(AssetEntryLoader.Result result) {
        mCurrentEntryResult = result == null ? AssetEntryLoader.Result.empty() : result;
        mItemViews.clear();
        mVisibleEntries.clear();
        List<AssetEntry> entries = mCurrentEntryResult.entries();
        Set<String> visibleKeys = new LinkedHashSet<>();

        for (AssetEntry entry : entries) {
            if (mEntryFilter != null && !mEntryFilter.test(entry)) {
                continue;
            }
            mVisibleEntries.add(entry);
            String key = entry.key();
            visibleKeys.add(key);
        }

        mSelectedPaths.retainAll(visibleKeys);
        mFileContent.setViewMode(mViewMode);
        mScrollView.scrollTo(0, 0);
        mFileContent.setEntries(mVisibleEntries);
        updateVirtualViewport();
        mFileContent.requestLayout();
        mFileContent.invalidate();
    }

    private void setViewMode(AssetViewMode mode) {
        if (mViewMode == mode) return;
        mViewMode = mode;
        ConfigManager.INSTANCE.update(config -> config.assetBrowser.viewMode = mode.configValue);
        refreshFileList();
    }

    private void showOptionsMenu(View anchor) {
        int[] bodyLoc = new int[2];
        int[] anchorLoc = new int[2];
        mBodyFrame.getLocationOnScreen(bodyLoc);
        anchor.getLocationOnScreen(anchorLoc);

        float localX = anchorLoc[0] - bodyLoc[0];
        float localY = Math.max(0, anchorLoc[1] + anchor.getHeight() - bodyLoc[1]);

        FileContextMenu menu = new FileContextMenu(getContext());
        menu.addSubMenuItem("视图模式", subMenu -> {
            subMenu.addMenuItem(viewModeMenuText(AssetViewMode.LIST), () -> setViewMode(AssetViewMode.LIST));
            subMenu.addMenuItem(viewModeMenuText(AssetViewMode.ICON_SMALL), () -> setViewMode(AssetViewMode.ICON_SMALL));
            subMenu.addMenuItem(viewModeMenuText(AssetViewMode.ICON_MEDIUM), () -> setViewMode(AssetViewMode.ICON_MEDIUM));
            subMenu.addMenuItem(viewModeMenuText(AssetViewMode.ICON_LARGE), () -> setViewMode(AssetViewMode.ICON_LARGE));
        });
        menu.showAt(localX, localY, mBodyFrame);
    }

    private String viewModeMenuText(AssetViewMode mode) {
        return (mode == mViewMode ? "✓ " : "   ") + switch (mode) {
            case LIST -> "列表";
            case ICON_SMALL -> "小图标";
            case ICON_MEDIUM -> "中图标";
            case ICON_LARGE -> "大图标";
        };
    }

    @Override
    public void onItemPressed(AssetEntry entry, MotionEvent event) {
        requestFocus();
        AssetDragState.clear();
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
            AssetDragState.clear();
            return;
        }
        AssetDragState.start(new AssetDragState.Payload(selectedEntries));
    }

    @Override
    public void onItemReleased(AssetEntry entry, MotionEvent event, boolean moved) {
        if (moved && handleInternalDrop(event.getRawX(), event.getRawY())) {
            AssetDragState.clear();
            return;
        }
        if (moved && AssetDragDropRegistry.dispatchDrop(event.getRawX(), event.getRawY())) {
            AssetDragState.clear();
            return;
        }
        AssetDragState.clear();
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

    private void toggleSelection(AssetEntry entry) {
        String key = entry.key();
        if (!mSelectedPaths.remove(key)) {
            mSelectedPaths.add(key);
        }
        syncSelectionViews();
    }

    void selectOnly(AssetEntry entry) {
        mSelectedPaths.clear();
        mSelectedPaths.add(entry.key());
        syncSelectionViews();
    }

    @Override
    public void clearSelection() {
        mSelectedPaths.clear();
        syncSelectionViews();
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

    @Override
    public void onBoxSelection(RectF selectionRect, boolean additive, Set<String> baseSelection) {
        if (!additive) {
            mSelectedPaths.clear();
        } else {
            mSelectedPaths.clear();
            mSelectedPaths.addAll(baseSelection);
        }

        mFileContent.collectEntriesIntersecting(selectionRect, mSelectedPaths);
        syncSelectionViews();
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
        List<String> tags = mViewMode == AssetViewMode.LIST ? mCurrentEntryResult.tagsFor(entry) : List.of();
        AssetFileItemView item = new AssetFileItemView(getContext(), entry, mViewMode, displayName(entry), parentLabel, tags, isFavorite(entry), this);
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
            view.preloadSchematicThumbnail();
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

    @Override
    public void onFavoriteToggled(AssetEntry entry) {
        if (entry == null || entry.sourceKind() != AssetSourceKind.LOCAL || entry.localFile() == null || !isLocalGraphFile(entry.localFile())) {
            return;
        }

        mFavoriteStore.toggle(entry.localFile());
        refreshFileList();
    }

    private boolean isFavorite(AssetEntry entry) {
        if (entry == null || entry.sourceKind() != AssetSourceKind.LOCAL || entry.localFile() == null || !entry.isJsonFile()) {
            return false;
        }
        return mFavoriteStore.isFavorite(entry.localFile());
    }

    private boolean handleInternalDrop(float rawX, float rawY) {
        AssetDragState.Payload payload = AssetDragState.current();
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

    private boolean isLocalGraphFile(File file) {
        return AssetEntryLoader.isLocalGraphFile(file);
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

    boolean isFavoritesMode() {
        return mFavoritesMode;
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

    private TextView createNavButton(Context context, String text) {
        TextView btn = UIUtils.createLockedTextView(context, text, TEXT_SIZE_NAV, 0xFFDDDDDD);
        btn.setPadding(dp2pxInt(16), 0, dp2pxInt(16), 0);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(createColorDrawable(0xFF444444));
        return btn;
    }

    private TextView createIconButton(Context context, String text) {
        TextView btn = UIUtils.createLockedTextView(context, text, 16.0f, 0xFFE6E6E6);
        btn.setSingleLine(true);
        btn.setPadding(0, 0, 0, 0);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(createRectDrawable(0xFF343A42, 4));
        btn.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                btn.setBackground(createRectDrawable(0xFF46515E, 4));
                btn.setTextColor(0xFFFFFFFF);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                btn.setBackground(createRectDrawable(0xFF343A42, 4));
                btn.setTextColor(0xFFE6E6E6);
            }
            return false;
        });
        return btn;
    }

    private EditText createNavInput(Context context) {
        EditText input = new EditText(context);
        input.setTextColor(0xFFCCCCCC);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(TEXT_SIZE_NAV));
        input.setPadding(dp2pxInt(12), 0, dp2pxInt(12), 0);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackground(createColorDrawable(UIConstants.ViewPort.NodeMenu.SEARCH_BG_COLOR));
        input.setSingleLine(true);
        return input;
    }

    private EditText createSearchInput(Context context) {
        EditText input = createNavInput(context);
        input.setTextColor(0xFFE6E6E6);
        input.setPadding(dp2pxInt(12), 0, dp2pxInt(12), 0);
        input.setBackground(createRectDrawable(0xFF242424, 4));
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
        ShapeDrawable drawable = createColorDrawable(color);
        drawable.setCornerRadius(dp2px(radiusDp));
        return drawable;
    }
}
