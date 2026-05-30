package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserPanel;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
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
    private final List<File> mVisibleFiles = new ArrayList<>();

    private File mCurrentDirectory;
    private List<File> mClipboardFiles = new ArrayList<>();
    private boolean mIsCutOperation = false;
    private AssetViewMode mViewMode;
    private String mSearchQuery = "";
    private String mLastClickedPath = null;
    private long mLastClickTime = 0L;

    private static final float NAV_BAR_HEIGHT = 40.0f;
    private static final float BTN_ADD_WIDTH = 40.0f;
    private static final float BTN_MENU_WIDTH = 34.0f;
    private static final float SEARCH_WIDTH = 220.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final float TEXT_SIZE_LIST_ITEM = 14.0f;
    private static final float TEXT_SIZE_BTN_ADD = 14.0f;

    public RightFileBrowserPanel(Context context, AssetBrowserPanel coordinator) {
        super(context);
        mCoordinator = coordinator;
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
                File dir = new File(mPathInput.getText().toString().trim());
                if (dir.exists() && dir.isDirectory()) {
                    navigateTo(dir);
                } else if (mCurrentDirectory != null) {
                    mPathInput.setText(mCurrentDirectory.getAbsolutePath());
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
        mCurrentDirectory = directory;
        mPathInput.setText(directory.getAbsolutePath());
        clearSelection();
        refreshFileList();
    }

    private void navigateUp() {
        if (mCurrentDirectory != null && mCurrentDirectory.getParentFile() != null) {
            navigateTo(mCurrentDirectory.getParentFile());
        }
    }

    public void refreshFileList() {
        mFileContent.removeAllViews();
        mItemViews.clear();
        mVisibleFiles.clear();
        if (mCurrentDirectory == null) return;

        List<File> files = loadVisibleFiles();
        mVisibleFiles.addAll(files);
        Set<String> visibleKeys = new LinkedHashSet<>();

        Context context = getContext();
        for (File file : files) {
            String key = pathKey(file);
            visibleKeys.add(key);
            String parentLabel = mSearchQuery.isEmpty() ? "" : relativeParentLabel(file);
            AssetFileItemView item = new AssetFileItemView(context, file, mViewMode, displayName(file), parentLabel, this);
            item.setSelected(mSelectedPaths.contains(key));
            mItemViews.put(key, item);
            mFileContent.addView(item);
        }

        mSelectedPaths.retainAll(visibleKeys);
        mFileContent.setViewMode(mViewMode);
        mFileContent.requestLayout();
        mFileContent.invalidate();
    }

    private List<File> loadVisibleFiles() {
        if (mSearchQuery.isEmpty()) {
            File[] files = mCurrentDirectory.listFiles();
            if (files == null) return Collections.emptyList();
            List<File> result = new ArrayList<>();
            for (File file : files) {
                if (isDisplayable(file)) result.add(file);
            }
            sortFiles(result);
            return result;
        }

        List<File> result = new ArrayList<>();
        collectSearchMatches(mCurrentDirectory, mSearchQuery.toLowerCase(Locale.ROOT), result);
        sortFiles(result);
        return result;
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
    public void onItemPressed(File file, MotionEvent event) {
        if (isRightMouse(event)) {
            if (!mSelectedPaths.contains(pathKey(file))) {
                selectOnly(file);
            }
            showContextMenuAtRaw(event.getRawX(), event.getRawY(), file);
            return;
        }

        if (event.isCtrlPressed()) {
            toggleSelection(file);
        } else {
            selectOnly(file);
        }
    }

    @Override
    public void onItemReleased(File file, MotionEvent event, boolean moved) {
        if (moved || isRightMouse(event)) return;

        String key = pathKey(file);
        long now = System.currentTimeMillis();
        if (key.equals(mLastClickedPath) && now - mLastClickTime < 300) {
            mLastClickedPath = null;
            mLastClickTime = 0L;
            handleDoubleClick(file);
        } else {
            mLastClickedPath = key;
            mLastClickTime = now;
        }
    }

    private void toggleSelection(File file) {
        String key = pathKey(file);
        if (!mSelectedPaths.remove(key)) {
            mSelectedPaths.add(key);
        }
        syncSelectionViews();
    }

    private void selectOnly(File file) {
        mSelectedPaths.clear();
        mSelectedPaths.add(pathKey(file));
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
                mSelectedPaths.add(pathKey(item.getFile()));
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

    private List<File> getSelectedFiles() {
        List<File> result = new ArrayList<>();
        for (File file : mVisibleFiles) {
            if (mSelectedPaths.contains(pathKey(file))) {
                result.add(file);
            }
        }
        return result;
    }

    private void showContextMenu(float localX, float localY, File targetFile) {
        FileContextMenu menu = new FileContextMenu(getContext());

        if (targetFile != null && !mSelectedPaths.contains(pathKey(targetFile))) {
            selectOnly(targetFile);
        }

        List<File> actionFiles = targetFile == null ? Collections.emptyList() : getSelectedFiles();
        if (!actionFiles.isEmpty()) {
            List<File> filesSnapshot = new ArrayList<>(actionFiles);
            String suffix = filesSnapshot.size() > 1 ? " (" + filesSnapshot.size() + ")" : "";
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

        if (!mClipboardFiles.isEmpty()) {
            menu.addMenuItem("粘贴", this::performPaste);
            menu.addDivider();
        }
        menu.addMenuItem("新建文件夹", () -> triggerNewItem(true));
        menu.addMenuItem("新建文件", () -> triggerNewItem(false));

        menu.showAt(localX, localY, mBodyFrame);
    }

    private void showContextMenuAtRaw(float rawX, float rawY, File targetFile) {
        int[] loc = new int[2];
        mBodyFrame.getLocationOnScreen(loc);
        showContextMenu(rawX - loc[0], rawY - loc[1], targetFile);
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
            selectOnly(newFile);
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

    private void handleDoubleClick(File file) {
        if (file.isDirectory()) {
            navigateTo(file);
        } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
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

    private String relativeParentLabel(File file) {
        String relative = relativeLabel(file);
        int idx = Math.max(relative.lastIndexOf('/'), relative.lastIndexOf('\\'));
        return idx > 0 ? relative.substring(0, idx) : "";
    }

    private String itemName(File file) {
        return file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
    }

    private String displayName(File file) {
        if (mViewMode == AssetViewMode.LIST) return itemName(file);
        return shortenMiddle(itemName(file), mViewMode.iconNameLimit);
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
