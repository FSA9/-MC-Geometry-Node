package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetPathUtils;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.action.AssetLibraryActionId;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.action.AssetLibraryActionRegistry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.ConfirmDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.GraphTagDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.drag.AssetDragState;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.drag.AssetDragDropRegistry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedKeyManager;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphEntry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphFileOperationRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphListRequest;
import com.mine.geometry_node.core.node.NodeGraph;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
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
    private final ScopedKeyManager<AssetLibraryActionId, RightFileBrowserPanel> mKeyManager;

    private File mCurrentDirectory;
    private String mRemoteDirectory = "";
    private AssetSourceKind mSourceKind = AssetSourceKind.LOCAL;
    private boolean mFavoritesMode = false;
    private List<File> mClipboardFiles = new ArrayList<>();
    private boolean mIsCutOperation = false;
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

    private static List<String> sRemoteClipboardPaths = new ArrayList<>();
    private static boolean sRemoteCutOperation = false;
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
    private static final float TEXT_SIZE_LIST_ITEM = 14.0f;
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
        mViewMode = AssetViewMode.fromConfig(ConfigManager.INSTANCE.getConfig().assetBrowser.viewMode);
        setOrientation(LinearLayout.VERTICAL);
        setFocusable(true);
        setFocusableInTouchMode(true);
        mKeyManager = new ScopedKeyManager<>(
                this,
                KeyScope.ASSET_LIBRARY,
                AssetLibraryActionRegistry::all,
                this::executeAction
        );

        LinearLayout navBar = new LinearLayout(context);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setBackground(createColorDrawable(0xFF2A2A2A));
        addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(NAV_BAR_HEIGHT)));

        TextView btnUp = createNavButton(context, "⬆ 向上");
        btnUp.setOnClickListener(v -> navigateUp());
        navBar.addView(btnUp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mPathInput = createNavInput(context);
        mPathInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                if (mFavoritesMode) {
                    mPathInput.setText("我的收藏");
                    mPathInput.clearFocus();
                    return true;
                }
                String path = mPathInput.getText().toString().trim();
                if (mSourceKind == AssetSourceKind.REMOTE || path.startsWith("remote:/")) {
                    navigateToRemote(AssetPathUtils.remotePathFromInput(path));
                } else if (mCurrentDirectory != null) {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        navigateTo(dir);
                    } else {
                        mPathInput.setText(mCurrentDirectory.getAbsolutePath());
                    }
                }
                mPathInput.clearFocus();
                return true;
            }
            return false;
        });
        navBar.addView(mPathInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

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

        mSearchInput = createSearchInput(context);
        mSearchInput.setHint("搜索名称");
        mSearchInput.setHintTextColor(0xFF666666);
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                mPendingSearchQuery = s.toString().trim();
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        mSearchInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                applySearch();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp2pxInt(SEARCH_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        searchLp.setMargins(dp2pxInt(6), dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(mSearchInput, searchLp);

        mTagSearchInput = createSearchInput(context);
        mTagSearchInput.setHint("搜索标签");
        mTagSearchInput.setHintTextColor(0xFF666666);
        mTagSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                mPendingTagSearchQuery = s.toString().trim();
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        mTagSearchInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                applySearch();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams tagSearchLp = new LinearLayout.LayoutParams(dp2pxInt(TAG_SEARCH_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        tagSearchLp.setMargins(0, dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(mTagSearchInput, tagSearchLp);

        TextView btnSearch = createIconButton(context, "⌕");
        btnSearch.setOnClickListener(v -> applySearch());
        LinearLayout.LayoutParams searchButtonLp = new LinearLayout.LayoutParams(dp2pxInt(SEARCH_BUTTON_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        searchButtonLp.setMargins(0, dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(btnSearch, searchButtonLp);

        TextView btnClearSearch = createIconButton(context, "×");
        btnClearSearch.setOnClickListener(v -> clearSearch());
        LinearLayout.LayoutParams clearButtonLp = new LinearLayout.LayoutParams(dp2pxInt(CLEAR_BUTTON_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        clearButtonLp.setMargins(0, dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(btnClearSearch, clearButtonLp);

        TextView btnOptions = createNavButton(context, "⋮");
        btnOptions.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(18));
        btnOptions.setPadding(0, 0, 0, 0);
        btnOptions.setBackground(createColorDrawable(0xFF3A3A3A));
        btnOptions.setOnClickListener(v -> showOptionsMenu(btnOptions));
        navBar.addView(btnOptions, new LinearLayout.LayoutParams(dp2pxInt(BTN_MENU_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        mBodyFrame = new FrameLayout(context);
        mScrollView = new ScrollView(context);
        mFileContent = new FileContentLayout(context, this);
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
            case COPY -> copySelectionToClipboard();
            case PASTE -> pasteClipboard();
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
            triggerNewItem(true);
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

    private void refreshFileList(Runnable afterRender) {
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
        int requestId = RemoteGraphClientState.nextRequestId();
        mActiveRemoteListRequestId = requestId;
        String requestedDirectory = mRemoteDirectory;
        RemoteGraphClientState.onList(requestId, response -> {
            post(() -> {
                if (mSourceKind != AssetSourceKind.REMOTE || requestId != mActiveRemoteListRequestId) return;
                if (!response.success()) {
                    renderEntries(AssetEntryLoader.Result.empty());
                    return;
                }
                mRemoteDirectory = response.directory();
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
            showContextMenuAtRaw(event.getRawX(), event.getRawY(), entry);
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
            handleDoubleClick(entry);
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

    private void selectOnly(AssetEntry entry) {
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
        showContextMenuAtRaw(rawX, rawY, null);
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

    private List<AssetEntry> getSelectedEntries() {
        List<AssetEntry> result = new ArrayList<>();
        for (AssetEntry entry : mVisibleEntries) {
            if (mSelectedPaths.contains(entry.key())) {
                result.add(entry);
            }
        }
        return result;
    }

    private List<File> getSelectedLocalFiles() {
        List<File> result = new ArrayList<>();
        for (AssetEntry entry : getSelectedEntries()) {
            if (entry.sourceKind() == AssetSourceKind.LOCAL && entry.localFile() != null) {
                result.add(entry.localFile());
            }
        }
        return result;
    }

    public boolean canCopySelection() {
        List<AssetEntry> entries = getSelectedEntries();
        if (entries.isEmpty()) return false;
        if (mSourceKind == AssetSourceKind.LOCAL) {
            return mEnableLocalFileActions && !getSelectedLocalFiles().isEmpty();
        }
        return mEnableRemoteTransferActions && RemoteGraphClientState.canManage()
                && entries.stream().anyMatch(entry -> entry.sourceKind() == AssetSourceKind.REMOTE);
    }

    public boolean canPasteClipboard() {
        if (mSourceKind == AssetSourceKind.LOCAL) {
            return mEnableLocalFileActions && !mFavoritesMode && !mClipboardFiles.isEmpty();
        }
        return mEnableRemoteTransferActions && RemoteGraphClientState.canManage() && !sRemoteClipboardPaths.isEmpty();
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
            return moveLocalEntries(payload.entries(), target);
        }
        if (target.sourceKind() == AssetSourceKind.REMOTE) {
            return moveRemoteEntries(payload.entries(), target);
        }
        return false;
    }

    private AssetEntry findDirectoryEntryAt(float rawX, float rawY) {
        AssetEntry candidate = mFileContent.entryAtRaw(rawX, rawY);
        return candidate != null && candidate.isDirectory() ? candidate : null;
    }

    private boolean moveLocalEntries(List<AssetEntry> entries, AssetEntry targetDirectoryEntry) {
        if (targetDirectoryEntry.sourceKind() != AssetSourceKind.LOCAL || targetDirectoryEntry.localFile() == null) return false;
        File targetDirectory = targetDirectoryEntry.localFile();
        if (!targetDirectory.isDirectory()) return false;

        boolean movedAny = false;
        try {
            for (AssetEntry entry : entries) {
                if (entry.sourceKind() != AssetSourceKind.LOCAL || entry.localFile() == null) continue;
                File source = entry.localFile();
                if (!source.exists() || source.equals(targetDirectory) || isDescendantOrSelf(targetDirectory, source)) continue;
                if (source.getParentFile() != null && source.getParentFile().equals(targetDirectory)) continue;

                File dest = AssetFileOperations.resolveAvailableDestination(targetDirectory, source.getName(), source.isDirectory());
                AssetFileOperations.moveRecursively(source, dest);
                mFavoriteStore.updatePath(source, dest);
                movedAny = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (movedAny) {
            clearSelection();
            refreshFileList();
        }
        return movedAny;
    }

    private boolean moveRemoteEntries(List<AssetEntry> entries, AssetEntry targetDirectoryEntry) {
        if (targetDirectoryEntry.sourceKind() != AssetSourceKind.REMOTE || !targetDirectoryEntry.isDirectory()) return false;
        if (!mEnableRemoteTransferActions || !RemoteGraphClientState.canManage()) return false;

        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE && !entry.path().equals(targetDirectoryEntry.path())) {
                paths.add(entry.path());
            }
        }
        if (paths.isEmpty()) return false;

        sendRemoteFileOperation(PacketRemoteGraphFileOperationRequest.Operation.MOVE, paths, targetDirectoryEntry.path());
        return true;
    }

    private void addLocalContextActions(FileContextMenu menu, List<File> filesSnapshot) {
        if (filesSnapshot.isEmpty()) return;
        String suffix = filesSnapshot.size() > 1 ? " (" + filesSnapshot.size() + ")" : "";
        List<File> uploadableGraphs = getUploadableGraphFiles(filesSnapshot);
        if (mEnableRemoteTransferActions && mCoordinator != null && RemoteGraphClientState.canUpload() && !uploadableGraphs.isEmpty()) {
            String uploadSuffix = uploadableGraphs.size() > 1 ? " (" + uploadableGraphs.size() + ")" : "";
            menu.addMenuItem("上传到服务器" + uploadSuffix, () -> mCoordinator.showUploadDialog(uploadableGraphs));
            menu.addDivider();
        }
        if (!mEnableLocalFileActions) return;
        if (filesSnapshot.size() == 1 && isLocalGraphFile(filesSnapshot.get(0))) {
            menu.addMenuItem("编辑标签", () -> showGraphTagDialog(filesSnapshot.get(0)));
            menu.addDivider();
        }
        menu.addMenuItem("复制" + suffix, shortcutText(AssetLibraryActionId.COPY), this::copySelectionToClipboard);
        menu.addMenuItem("剪切" + suffix, () -> {
            mClipboardFiles = new ArrayList<>(filesSnapshot);
            mIsCutOperation = true;
        });
        menu.addMenuItem("删除" + suffix, () -> {
            for (File file : filesSnapshot) {
                try {
                    mFavoriteStore.removePath(file);
                    AssetFileOperations.deleteRecursively(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            clearSelection();
            refreshFileList();
        });
        if (filesSnapshot.size() == 1) {
            menu.addDivider();
            menu.addMenuItem("重命名", () -> startInlineEdit(filesSnapshot.get(0)));
        }
        menu.addDivider();
    }

    private boolean isLocalGraphFile(File file) {
        return AssetEntryLoader.isLocalGraphFile(file);
    }

    private List<File> getUploadableGraphFiles(List<File> files) {
        List<File> result = new ArrayList<>();
        for (File file : files) {
            collectUploadableGraphFiles(file, result);
        }
        return result;
    }

    private void collectUploadableGraphFiles(File file, List<File> out) {
        if (file == null || !file.exists()) return;
        if (file.isFile()) {
            if (isLocalGraphFile(file)) {
                out.add(file);
            }
            return;
        }
        File[] children = file.listFiles();
        if (children == null) return;
        for (File child : children) {
            collectUploadableGraphFiles(child, out);
        }
    }

    private void showGraphTagDialog(File file) {
        GraphTagDialog dialog = new GraphTagDialog(getContext(), file, tags -> {
            syncOpenGraphTags(file, tags);
            refreshFileList();
        });
        dialog.showIn(this);
    }

    private void syncOpenGraphTags(File file, List<String> tags) {
        String targetKey = pathKey(file);
        for (GraphSession session : DocumentManager.INSTANCE.getSessions()) {
            if (session == null || session.editorContext == null || session.editorContext.getGraph() == null) continue;
            File sessionFile = new File(session.fileId);
            if (!targetKey.equals(pathKey(sessionFile))) continue;
            session.editorContext.getGraph().tags = new ArrayList<>(tags);
        }
    }

    private void addRemoteContextActions(FileContextMenu menu, List<AssetEntry> entriesSnapshot) {
        if (entriesSnapshot.isEmpty() || !mEnableRemoteTransferActions) return;
        String suffix = entriesSnapshot.size() > 1 ? " (" + entriesSnapshot.size() + ")" : "";
        if (mCoordinator != null && RemoteGraphClientState.canDownload()) {
            menu.addMenuItem("下载到本地" + suffix, () -> mCoordinator.showDownloadDialog(entriesSnapshot));
            menu.addDivider();
        }
        if (RemoteGraphClientState.canManage()) {
            menu.addMenuItem("复制" + suffix, shortcutText(AssetLibraryActionId.COPY), this::copySelectionToClipboard);
            menu.addMenuItem("剪切" + suffix, () -> setRemoteClipboard(entriesSnapshot, true));
            menu.addMenuItem("删除" + suffix, () -> deleteRemoteEntries(entriesSnapshot));
            menu.addDivider();
        }
    }

    private void showContextMenu(float localX, float localY, AssetEntry targetEntry) {
        FileContextMenu menu = new FileContextMenu(getContext());

        if (targetEntry != null && !mSelectedPaths.contains(targetEntry.key())) {
            selectOnly(targetEntry);
        }

        List<AssetEntry> actionEntries = targetEntry == null ? Collections.emptyList() : getSelectedEntries();
        if (!actionEntries.isEmpty()) {
            if (mSourceKind == AssetSourceKind.LOCAL) {
                addLocalContextActions(menu, getSelectedLocalFiles());
            } else {
                addRemoteContextActions(menu, new ArrayList<>(actionEntries));
            }
        }

        if (mEnableLocalFileActions && !mFavoritesMode && mSourceKind == AssetSourceKind.LOCAL && !mClipboardFiles.isEmpty()) {
            menu.addMenuItem("粘贴", shortcutText(AssetLibraryActionId.PASTE), this::pasteClipboard);
            menu.addDivider();
        }
        if (mEnableRemoteTransferActions && mSourceKind == AssetSourceKind.REMOTE
                && RemoteGraphClientState.canManage() && !sRemoteClipboardPaths.isEmpty()) {
            menu.addMenuItem(sRemoteCutOperation ? "移动到此处" : "粘贴", shortcutText(AssetLibraryActionId.PASTE), this::pasteClipboard);
            menu.addDivider();
        }
        if (mEnableLocalFileActions && !mFavoritesMode && mSourceKind == AssetSourceKind.LOCAL) {
            menu.addMenuItem("新建文件夹", () -> triggerNewItem(true));
            menu.addMenuItem("新建文件", () -> triggerNewItem(false));
        }
        if (mShowPickerContextActions && mPickFileAction != null && targetEntry != null && !targetEntry.isDirectory()) {
            menu.addMenuItem("选择文件", () -> mPickFileAction.accept(targetEntry));
        }
        if (mShowPickerContextActions && mPickCurrentDirectoryAction != null) {
            menu.addMenuItem("选择当前文件夹", () -> {
                mPickCurrentDirectoryAction.run();
            });
        }
        if (mShowPickerContextActions) {
            menu.addMenuItem("刷新", this::refreshFileList);
        }

        if (menu.hasItems()) {
            menu.showAt(localX, localY, mBodyFrame);
        }
    }

    private void showContextMenuAtRaw(float rawX, float rawY, AssetEntry targetEntry) {
        int[] loc = new int[2];
        mBodyFrame.getLocationOnScreen(loc);
        showContextMenu(rawX - loc[0], rawY - loc[1], targetEntry);
    }

    private void startInlineEdit(File targetFile) {
        String key = pathKey(targetFile);
        AssetFileItemView itemView = mItemViews.get(key);
        if (itemView == null) {
            int top = mFileContent.entryTop(key);
            if (top < 0) return;
            mScrollView.scrollTo(0, Math.max(0, top - dp2pxInt(8)));
            updateVirtualViewport();
            mFileContent.forceRefreshMountedItems();
            itemView = mItemViews.get(key);
        }
        if (itemView == null) return;

        TextView originalTextView = itemView.getNameView();
        ViewGroup parent = (ViewGroup) originalTextView.getParent();
        int index = parent.indexOfChild(originalTextView);
        originalTextView.setVisibility(View.GONE);

        EditText editInput = new EditText(getContext());
        editInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(TEXT_SIZE_LIST_ITEM));
        editInput.setTextColor(0xFFFFFFFF);
        editInput.setBackground(createColorDrawable(0xFF444444));
        editInput.setSingleLine(true);
        editInput.setPadding(dp2pxInt(6), 0, dp2pxInt(6), 0);
        editInput.setText(targetFile.getName());

        parent.addView(editInput, index, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        editInput.requestFocus();
        String name = editInput.getText().toString();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex > 0 && !targetFile.isDirectory()) {
            editInput.setSelection(0, dotIndex);
        } else {
            editInput.setSelection(name.length());
        }

        Runnable commitAction = new Runnable() {
            boolean isCommitted = false;

            @Override
            public void run() {
                if (isCommitted) return;
                isCommitted = true;

                String newName = editInput.getText().toString().trim();
                if (!newName.isEmpty() && !newName.equals(targetFile.getName())) {
                    File dest = new File(targetFile.getParentFile(), newName);
                    if (!dest.exists()) {
                        if (targetFile.renameTo(dest)) {
                            mFavoriteStore.updatePath(targetFile, dest);
                        }
                    }
                }
                refreshFileList();
            }
        };

        editInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                commitAction.run();
                return true;
            }
            return false;
        });

        editInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitAction.run();
        });
    }

    private void performPaste() {
        if (mFavoritesMode || mClipboardFiles.isEmpty() || mCurrentDirectory == null) return;
        try {
            for (File source : new ArrayList<>(mClipboardFiles)) {
                if (!source.exists()) continue;
                File dest = AssetFileOperations.resolveAvailableDestination(mCurrentDirectory, source.getName(), source.isDirectory());
                if (source.isDirectory() && isDescendantOrSelf(dest, source)) {
                    System.err.println("[AssetBrowser] Skip copying directory into itself: " + source);
                    continue;
                }
                if (mIsCutOperation) {
                    AssetFileOperations.moveRecursively(source, dest);
                    mFavoriteStore.updatePath(source, dest);
                } else {
                    AssetFileOperations.copyRecursively(source, dest);
                }
            }
            if (mIsCutOperation) {
                mClipboardFiles.clear();
            }
            clearSelection();
            refreshFileList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copySelectionToClipboard() {
        if (mSourceKind == AssetSourceKind.LOCAL) {
            List<File> files = getSelectedLocalFiles();
            if (files.isEmpty()) return;
            mClipboardFiles = new ArrayList<>(files);
            mIsCutOperation = false;
            return;
        }

        if (!mEnableRemoteTransferActions || !RemoteGraphClientState.canManage()) return;
        List<AssetEntry> entries = getSelectedEntries();
        if (entries.isEmpty()) return;
        setRemoteClipboard(entries, false);
    }

    private void pasteClipboard() {
        if (mSourceKind == AssetSourceKind.LOCAL) {
            performPaste();
        } else {
            pasteRemoteEntries();
        }
    }

    private void setRemoteClipboard(List<AssetEntry> entries, boolean cutOperation) {
        sRemoteClipboardPaths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE) {
                sRemoteClipboardPaths.add(entry.path());
            }
        }
        sRemoteCutOperation = cutOperation;
    }

    private String shortcutText(AssetLibraryActionId actionId) {
        return AssetLibraryActionRegistry.shortcutText(actionId, ConfigManager.INSTANCE.getConfig());
    }

    private void deleteRemoteEntries(List<AssetEntry> entries) {
        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE) {
                paths.add(entry.path());
            }
        }
        if (paths.isEmpty()) return;
        String message = paths.size() == 1
                ? "确定删除云端项目: " + entries.get(0).name()
                : "确定删除选中的 " + paths.size() + " 个云端项目？";
        ConfirmDialog dialog = new ConfirmDialog(
                getContext(),
                "删除云端文件",
                message,
                "删除",
                () -> sendRemoteFileOperation(PacketRemoteGraphFileOperationRequest.Operation.DELETE, paths, "")
        );
        dialog.showIn(this);
    }

    private void pasteRemoteEntries() {
        if (sRemoteClipboardPaths.isEmpty()) return;
        PacketRemoteGraphFileOperationRequest.Operation operation = sRemoteCutOperation
                ? PacketRemoteGraphFileOperationRequest.Operation.MOVE
                : PacketRemoteGraphFileOperationRequest.Operation.COPY;
        sendRemoteFileOperation(operation, new ArrayList<>(sRemoteClipboardPaths), mRemoteDirectory);
    }

    private void sendRemoteFileOperation(PacketRemoteGraphFileOperationRequest.Operation operation, List<String> paths, String targetDirectory) {
        if (paths.isEmpty()) return;
        int requestId = RemoteGraphClientState.nextRequestId();
        String title = switch (operation) {
            case DELETE -> "删除云端文件";
            case COPY -> "复制云端文件";
            case MOVE -> "移动云端文件";
        };
        com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.TransferProgressDialog progress =
                new com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.TransferProgressDialog(getContext(), title);
        progress.showIn(this);
        RemoteGraphClientState.onFileOperation(requestId, response -> {
            post(() -> {
                if (!response.success()) {
                    progress.fail(response.message());
                    System.err.println("[AssetBrowser] Remote file operation failed: " + response.message());
                    return;
                }
                progress.update(response.message(), 1, 1);
                if (operation == PacketRemoteGraphFileOperationRequest.Operation.MOVE) {
                    sRemoteClipboardPaths = new ArrayList<>();
                    sRemoteCutOperation = false;
                }
                clearSelection();
                refreshFileList();
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphFileOperationRequest(requestId, operation, targetDirectory, paths));
    }

    private void triggerNewItem(boolean isFolder) {
        if (mFavoritesMode) return;
        clearSearch();
        if (mCurrentDirectory == null) return;
        try {
            File newFile = AssetFileOperations.resolveAvailableDestination(mCurrentDirectory, isFolder ? "新建文件夹" : "新建文件.json", isFolder);
            if (isFolder) {
                newFile.mkdirs();
            } else {
                newFile.createNewFile();
            }
            AssetEntry newEntry = mEntryLoader.toLocalEntry(newFile, mCurrentDirectory, false);
            refreshFileList(() -> {
                selectOnly(newEntry);
                startInlineEdit(newFile);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearSearch() {
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

    private void handleDoubleClick(AssetEntry entry) {
        if (mPickFileAction != null && !entry.isDirectory()) {
            mPickFileAction.accept(entry);
            return;
        }

        if (entry.sourceKind() == AssetSourceKind.REMOTE) {
            if (entry.isDirectory()) {
                navigateToRemote(entry.path());
            }
            return;
        }

        File file = entry.localFile();
        if (file == null) return;
        if (file.isDirectory()) {
            navigateTo(file);
        } else if (mOpenLocalJsonOnDoubleClick && file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
            openGraphFile(file);
        }
    }

    private void openGraphFile(File file) {
        try {
            String content = Files.readString(file.toPath()).trim();
            NodeGraph graph = (content.isEmpty() || content.equals("{}"))
                    ? new NodeGraph()
                    : GraphJsonIO.fromJson(content);

            GraphSession session = new GraphSession(file.getAbsolutePath(), file.getName(), graph);
            DocumentManager.INSTANCE.openSession(session);
        } catch (Exception e) { e.printStackTrace(); }
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

    private void updateVirtualViewport() {
        if (mFileContent == null || mScrollView == null) return;
        mFileContent.updateViewport(mScrollView.getScrollY(), mScrollView.getHeight());
    }

    private boolean isRightMouse(MotionEvent e) {
        return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                || e.getActionButton() == MotionEvent.BUTTON_SECONDARY;
    }

    private boolean isDescendantOrSelf(File candidate, File root) {
        try {
            String candidatePath = candidate.getCanonicalPath();
            String rootPath = root.getCanonicalPath();
            return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    private String pathKey(File file) {
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
