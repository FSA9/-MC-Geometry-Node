package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetPathUtils;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserPanel;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.ConfirmDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.execution.storage.RemoteGraphFileService;
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

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

public class RightFileBrowserPanel extends LinearLayout implements AssetFileItemView.Listener, FileContentLayout.SelectionHost {

    private final AssetBrowserPanel mCoordinator;
    private final EditText mPathInput;
    private final EditText mSearchInput;
    private final FrameLayout mBodyFrame;
    private final ScrollView mScrollView;
    private final FileContentLayout mFileContent;
    private final Map<String, AssetFileItemView> mItemViews = new HashMap<>();
    private final Set<String> mSelectedPaths = new LinkedHashSet<>();
    private final List<AssetEntry> mVisibleEntries = new ArrayList<>();

    private File mCurrentDirectory;
    private String mRemoteDirectory = "";
    private AssetSourceKind mSourceKind = AssetSourceKind.LOCAL;
    private List<File> mClipboardFiles = new ArrayList<>();
    private boolean mIsCutOperation = false;
    private AssetViewMode mViewMode;
    private String mSearchQuery = "";
    private String mLastClickedPath = null;
    private long mLastClickTime = 0L;
    private int mActiveRemoteListRequestId = 0;
    private final boolean mEnableQuickAccessAdd;
    private final boolean mEnableLocalFileActions;
    private final boolean mEnableRemoteTransferActions;
    private final boolean mOpenLocalJsonOnDoubleClick;
    private final boolean mShowPickerContextActions;
    private Consumer<File> mLocalDirectoryChangedListener;
    private Consumer<String> mRemoteDirectoryChangedListener;
    private Runnable mPickCurrentDirectoryAction;

    private static List<String> sRemoteClipboardPaths = new ArrayList<>();

    private static final float NAV_BAR_HEIGHT = 40.0f;
    private static final float BTN_ADD_WIDTH = 40.0f;
    private static final float BTN_MENU_WIDTH = 34.0f;
    private static final float SEARCH_WIDTH = 220.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final float TEXT_SIZE_LIST_ITEM = 14.0f;
    private static final float TEXT_SIZE_BTN_ADD = 14.0f;

    public RightFileBrowserPanel(Context context, AssetBrowserPanel coordinator) {
        this(context, coordinator, true, true, true, true, false);
    }

    public RightFileBrowserPanel(Context context) {
        this(context, null, false, false, true, false, true);
    }

    private RightFileBrowserPanel(
            Context context,
            AssetBrowserPanel coordinator,
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
            if (!path.isEmpty() && new File(path).isDirectory()) {
                if (!ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths.contains(path)) {
                    ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths.add(path);
                    ConfigManager.INSTANCE.save();
                    mCoordinator.notifySidebarChanged();
                }
            }
        });
        navBar.addView(btnAdd, new LinearLayout.LayoutParams(dp2pxInt(BTN_ADD_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));
        btnAdd.setVisibility(mEnableQuickAccessAdd ? View.VISIBLE : View.GONE);

        mSearchInput = createSearchInput(context);
        mSearchInput.setHint("搜索资产");
        mSearchInput.setHintTextColor(0xFF666666);
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                mSearchQuery = s.toString().trim();
                refreshFileList();
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp2pxInt(SEARCH_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT);
        searchLp.setMargins(dp2pxInt(6), dp2pxInt(5), dp2pxInt(6), dp2pxInt(5));
        navBar.addView(mSearchInput, searchLp);

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
        mScrollView.addView(mFileContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mBodyFrame.addView(mScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mBodyFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mFileContent != null) {
            mFileContent.setMinimumContentHeight(Math.max(0, h - dp2pxInt(NAV_BAR_HEIGHT)));
        }
    }

    public void navigateTo(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;
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
        mSourceKind = AssetSourceKind.REMOTE;
        mRemoteDirectory = AssetPathUtils.normalizeRemoteDirectory(directory);
        mPathInput.setText(AssetPathUtils.formatRemotePath(mRemoteDirectory));
        clearSelection();
        refreshRemoteFileList(createIfMissing);
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
        if (mSourceKind == AssetSourceKind.LOCAL) {
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

    public String getRemoteDirectory() {
        return mRemoteDirectory;
    }

    public List<AssetEntry> getSelectedEntriesSnapshot() {
        return new ArrayList<>(getSelectedEntries());
    }

    public void setPickCurrentDirectoryAction(Runnable action) {
        mPickCurrentDirectoryAction = action;
    }

    private void navigateUp() {
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
        mFileContent.removeAllViews();
        mItemViews.clear();
        mVisibleEntries.clear();

        if (mSourceKind == AssetSourceKind.REMOTE) {
            refreshRemoteFileList(false);
            return;
        }

        if (mCurrentDirectory == null) return;

        List<AssetEntry> entries = loadVisibleEntries();
        renderEntries(entries);
    }

    private void refreshRemoteFileList(boolean createIfMissing) {
        int requestId = RemoteGraphClientState.nextRequestId();
        mActiveRemoteListRequestId = requestId;
        String requestedDirectory = mRemoteDirectory;
        RemoteGraphClientState.onList(requestId, response -> {
            post(() -> {
                if (mSourceKind != AssetSourceKind.REMOTE || requestId != mActiveRemoteListRequestId) return;
                if (!response.success()) {
                    renderEntries(Collections.emptyList());
                    return;
                }
                mRemoteDirectory = response.directory();
                mPathInput.setText(AssetPathUtils.formatRemotePath(mRemoteDirectory));
                if (mRemoteDirectoryChangedListener != null) {
                    mRemoteDirectoryChangedListener.accept(mRemoteDirectory);
                }
                List<AssetEntry> entries = new ArrayList<>();
                for (RemoteGraphFileService.Entry entry : response.entries()) {
                    if (!mSearchQuery.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(mSearchQuery.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    entries.add(AssetEntry.remote(entry.path(), entry.name(), entry.directory(), entry.size()));
                }
                renderEntries(entries);
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphListRequest(requestId, requestedDirectory, createIfMissing));
    }

    private void renderEntries(List<AssetEntry> entries) {
        mFileContent.removeAllViews();
        mItemViews.clear();
        mVisibleEntries.clear();
        mVisibleEntries.addAll(entries);
        Set<String> visibleKeys = new LinkedHashSet<>();

        Context context = getContext();
        for (AssetEntry entry : entries) {
            String key = entry.key();
            visibleKeys.add(key);
            String parentLabel = mSearchQuery.isEmpty() ? "" : parentLabel(entry);
            AssetFileItemView item = new AssetFileItemView(context, entry, mViewMode, displayName(entry), parentLabel, this);
            item.setSelected(mSelectedPaths.contains(key));
            mItemViews.put(key, item);
            mFileContent.addView(item);
        }

        mSelectedPaths.retainAll(visibleKeys);
        mFileContent.setViewMode(mViewMode);
        mFileContent.requestLayout();
        mFileContent.invalidate();
    }

    private List<AssetEntry> loadVisibleEntries() {
        if (mSearchQuery.isEmpty()) {
            File[] files = mCurrentDirectory.listFiles();
            if (files == null) return Collections.emptyList();
            List<File> fileResult = new ArrayList<>();
            for (File file : files) {
                if (isDisplayable(file)) fileResult.add(file);
            }
            sortFiles(fileResult);
            return toLocalEntries(fileResult);
        }

        List<File> fileResult = new ArrayList<>();
        collectSearchMatches(mCurrentDirectory, mSearchQuery.toLowerCase(Locale.ROOT), fileResult);
        sortFiles(fileResult);
        return toLocalEntries(fileResult);
    }

    private void collectSearchMatches(File directory, String query, List<File> out) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            try {
                if (Files.isSymbolicLink(file.toPath())) continue;
            } catch (Exception ignored) {
                continue;
            }

            boolean displayable = isDisplayable(file);
            if (displayable && file.getName().toLowerCase(Locale.ROOT).contains(query)) {
                out.add(file);
            }
            if (file.isDirectory()) {
                collectSearchMatches(file, query, out);
            }
        }
    }

    private boolean isDisplayable(File file) {
        return file.isDirectory() || file.getName().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private void sortFiles(List<File> files) {
        files.sort((f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return relativeLabel(f1).compareToIgnoreCase(relativeLabel(f2));
        });
    }

    private List<AssetEntry> toLocalEntries(List<File> files) {
        List<AssetEntry> entries = new ArrayList<>(files.size());
        for (File file : files) {
            entries.add(AssetEntry.local(file, pathKey(file), relativeLabel(file)));
        }
        return entries;
    }

    private void setViewMode(AssetViewMode mode) {
        if (mViewMode == mode) return;
        mViewMode = mode;
        ConfigManager.INSTANCE.getConfig().assetBrowser.viewMode = mode.configValue;
        ConfigManager.INSTANCE.save();
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
        if (isRightMouse(event)) {
            if (!mSelectedPaths.contains(entry.key())) {
                selectOnly(entry);
            }
            showContextMenuAtRaw(event.getRawX(), event.getRawY(), entry);
            return;
        }

        if (event.isCtrlPressed()) {
            toggleSelection(entry);
        } else {
            selectOnly(entry);
        }
    }

    @Override
    public void onItemReleased(AssetEntry entry, MotionEvent event, boolean moved) {
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

        RectF itemRect = new RectF();
        for (AssetFileItemView item : mItemViews.values()) {
            itemRect.set(item.getLeft(), item.getTop(), item.getRight(), item.getBottom());
            if (itemRect.intersects(selectionRect.left, selectionRect.top, selectionRect.right, selectionRect.bottom)) {
                mSelectedPaths.add(item.getEntry().key());
            }
        }
        syncSelectionViews();
    }

    @Override
    public void onContentRightClick(float rawX, float rawY) {
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

    private void addLocalContextActions(FileContextMenu menu, List<File> filesSnapshot) {
        if (filesSnapshot.isEmpty()) return;
        String suffix = filesSnapshot.size() > 1 ? " (" + filesSnapshot.size() + ")" : "";
        if (mEnableRemoteTransferActions && mCoordinator != null && RemoteGraphClientState.canUpload()) {
            menu.addMenuItem("上传到服务器" + suffix, () -> mCoordinator.showUploadDialog(filesSnapshot));
            menu.addDivider();
        }
        if (!mEnableLocalFileActions) return;
        menu.addMenuItem("复制" + suffix, () -> {
            mClipboardFiles = new ArrayList<>(filesSnapshot);
            mIsCutOperation = false;
        });
        menu.addMenuItem("剪切" + suffix, () -> {
            mClipboardFiles = new ArrayList<>(filesSnapshot);
            mIsCutOperation = true;
        });
        menu.addMenuItem("删除" + suffix, () -> {
            for (File file : filesSnapshot) {
                try {
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

    private void addRemoteContextActions(FileContextMenu menu, List<AssetEntry> entriesSnapshot) {
        if (entriesSnapshot.isEmpty() || !mEnableRemoteTransferActions) return;
        String suffix = entriesSnapshot.size() > 1 ? " (" + entriesSnapshot.size() + ")" : "";
        if (mCoordinator != null && RemoteGraphClientState.canDownload()) {
            menu.addMenuItem("下载到本地" + suffix, () -> mCoordinator.showDownloadDialog(entriesSnapshot));
            menu.addDivider();
        }
        if (RemoteGraphClientState.canManage()) {
            menu.addMenuItem("复制" + suffix, () -> copyRemoteEntries(entriesSnapshot));
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

        if (mEnableLocalFileActions && mSourceKind == AssetSourceKind.LOCAL && !mClipboardFiles.isEmpty()) {
            menu.addMenuItem("粘贴", this::performPaste);
            menu.addDivider();
        }
        if (mEnableRemoteTransferActions && mSourceKind == AssetSourceKind.REMOTE
                && RemoteGraphClientState.canManage() && !sRemoteClipboardPaths.isEmpty()) {
            menu.addMenuItem("粘贴", this::pasteRemoteEntries);
            menu.addDivider();
        }
        if (mEnableLocalFileActions && mSourceKind == AssetSourceKind.LOCAL) {
            menu.addMenuItem("新建文件夹", () -> triggerNewItem(true));
            menu.addMenuItem("新建文件", () -> triggerNewItem(false));
        }
        if (mShowPickerContextActions) {
            menu.addMenuItem("选择当前文件夹", () -> {
                if (mPickCurrentDirectoryAction != null) {
                    mPickCurrentDirectoryAction.run();
                }
            });
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
        AssetFileItemView itemView = mItemViews.get(pathKey(targetFile));
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
                        targetFile.renameTo(dest);
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
        if (mClipboardFiles.isEmpty() || mCurrentDirectory == null) return;
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

    private void copyRemoteEntries(List<AssetEntry> entries) {
        sRemoteClipboardPaths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE) {
                sRemoteClipboardPaths.add(entry.path());
            }
        }
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
        sendRemoteFileOperation(PacketRemoteGraphFileOperationRequest.Operation.COPY, new ArrayList<>(sRemoteClipboardPaths), mRemoteDirectory);
    }

    private void sendRemoteFileOperation(PacketRemoteGraphFileOperationRequest.Operation operation, List<String> paths, String targetDirectory) {
        if (paths.isEmpty()) return;
        int requestId = RemoteGraphClientState.nextRequestId();
        String title = operation == PacketRemoteGraphFileOperationRequest.Operation.DELETE ? "删除云端文件" : "复制云端文件";
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
                clearSelection();
                refreshFileList();
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphFileOperationRequest(requestId, operation, targetDirectory, paths));
    }

    private void triggerNewItem(boolean isFolder) {
        clearSearch();
        if (mCurrentDirectory == null) return;
        try {
            File newFile = AssetFileOperations.resolveAvailableDestination(mCurrentDirectory, isFolder ? "新建文件夹" : "新建文件.json", isFolder);
            if (isFolder) {
                newFile.mkdirs();
            } else {
                newFile.createNewFile();
            }
            refreshFileList();
            selectOnly(AssetEntry.local(newFile, pathKey(newFile), relativeLabel(newFile)));
            startInlineEdit(newFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearSearch() {
        if (!mSearchQuery.isEmpty()) {
            mSearchInput.setText("");
        }
    }

    private void handleDoubleClick(AssetEntry entry) {
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
                    ? new NodeGraph(file.getName())
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

    private String relativeLabel(File file) {
        if (mCurrentDirectory == null) return file.getName();
        try {
            return mCurrentDirectory.toPath().relativize(file.toPath()).toString();
        } catch (Exception ignored) {
            return file.getName();
        }
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
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
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
