package com.mine.geometry_node.client.ui.bottom_window.asset_library;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeGraph;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
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

public class RightFileBrowserPanel extends LinearLayout {

    private enum ViewMode {
        LIST("LIST", 0, 40, 14, 14, 0),
        ICON_SMALL("ICON_SMALL", 84, 70, 22, 11, 13),
        ICON_MEDIUM("ICON_MEDIUM", 108, 92, 32, 12, 19),
        ICON_LARGE("ICON_LARGE", 144, 120, 46, 13, 28);

        final String configValue;
        final float itemWidthDp;
        final float itemHeightDp;
        final float iconTextSizeDp;
        final float nameTextSizeDp;
        final int iconNameLimit;

        ViewMode(String configValue, float itemWidthDp, float itemHeightDp, float iconTextSizeDp, float nameTextSizeDp, int iconNameLimit) {
            this.configValue = configValue;
            this.itemWidthDp = itemWidthDp;
            this.itemHeightDp = itemHeightDp;
            this.iconTextSizeDp = iconTextSizeDp;
            this.nameTextSizeDp = nameTextSizeDp;
            this.iconNameLimit = iconNameLimit;
        }

        static ViewMode fromConfig(String value) {
            for (ViewMode mode : values()) {
                if (mode.configValue.equals(value)) return mode;
            }
            return LIST;
        }
    }

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
    private ViewMode mViewMode;
    private String mSearchQuery = "";
    private String mLastClickedPath = null;
    private long mLastClickTime = 0L;

    private static final float NAV_BAR_HEIGHT = 40.0f;
    private static final float BTN_ADD_WIDTH = 40.0f;
    private static final float BTN_MENU_WIDTH = 34.0f;
    private static final float SEARCH_WIDTH = 220.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final float TEXT_SIZE_LIST_ITEM = 14.0f;
    private static final float TEXT_SIZE_LIST_SUBTITLE = 11.0f;
    private static final float TEXT_SIZE_BTN_ADD = 14.0f;
    private static final int COLOR_ITEM_SELECTED = 0xFF445566;
    private static final int COLOR_ITEM_TRANSPARENT = 0x00000000;
    private static final int COLOR_FOLDER = 0xFFDDAA00;
    private static final int COLOR_FILE = 0xFF88CCFF;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_SUBTEXT = 0xFF888888;

    public RightFileBrowserPanel(Context context, AssetBrowserPanel coordinator) {
        super(context);
        mCoordinator = coordinator;
        mViewMode = ViewMode.fromConfig(ConfigManager.INSTANCE.getConfig().assetBrowser.viewMode);
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
        mFileContent = new FileContentLayout(context);
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
            AssetFileItemView item = new AssetFileItemView(context, file);
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

    private void setViewMode(ViewMode mode) {
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
        menu.addTitle("视图模式");
        menu.addMenuItem(viewModeMenuText(ViewMode.LIST), () -> setViewMode(ViewMode.LIST));
        menu.addMenuItem(viewModeMenuText(ViewMode.ICON_SMALL), () -> setViewMode(ViewMode.ICON_SMALL));
        menu.addMenuItem(viewModeMenuText(ViewMode.ICON_MEDIUM), () -> setViewMode(ViewMode.ICON_MEDIUM));
        menu.addMenuItem(viewModeMenuText(ViewMode.ICON_LARGE), () -> setViewMode(ViewMode.ICON_LARGE));
        menu.showAt(localX, localY, mBodyFrame);
    }

    private String viewModeMenuText(ViewMode mode) {
        return (mode == mViewMode ? "✓ " : "   ") + switch (mode) {
            case LIST -> "列表";
            case ICON_SMALL -> "小图标";
            case ICON_MEDIUM -> "中图标";
            case ICON_LARGE -> "大图标";
        };
    }

    private void handleItemPressed(File file, MotionEvent event) {
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

    private void handleItemReleased(File file, MotionEvent event, boolean moved) {
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

    private void clearSelection() {
        mSelectedPaths.clear();
        syncSelectionViews();
    }

    private void syncSelectionViews() {
        for (Map.Entry<String, AssetFileItemView> entry : mItemViews.entrySet()) {
            entry.getValue().setSelected(mSelectedPaths.contains(entry.getKey()));
        }
        mFileContent.invalidate();
    }

    private void applyBoxSelection(RectF selectionRect, boolean additive, Set<String> baseSelection) {
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
        if (mViewMode == ViewMode.LIST) return itemName(file);
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

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private ShapeDrawable createRectDrawable(int color, float radiusDp) {
        ShapeDrawable drawable = createColorDrawable(color);
        drawable.setCornerRadius(dp2px(radiusDp));
        return drawable;
    }

    private final class AssetFileItemView extends LinearLayout {
        private final File mFile;
        private final TextView mIconView;
        private final TextView mNameView;
        private final TextView mSubtitleView;
        private boolean mIsSelected;
        private float mDownX;
        private float mDownY;
        private boolean mMoved;
        private final float mTouchSlop;

        AssetFileItemView(Context context, File file) {
            super(context);
            mFile = file;
            mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp2pxInt(6), dp2pxInt(4), dp2pxInt(6), dp2pxInt(4));
            setBackground(createRectDrawable(COLOR_ITEM_TRANSPARENT, 4));

            mIconView = UIUtils.createLockedTextView(context, file.isDirectory() ? "📁" : "📄", mViewMode.iconTextSizeDp, file.isDirectory() ? COLOR_FOLDER : COLOR_FILE);
            mIconView.setGravity(Gravity.CENTER);
            mNameView = UIUtils.createLockedTextView(context, displayName(file), mViewMode.nameTextSizeDp, COLOR_TEXT);
            mNameView.setGravity(mViewMode == ViewMode.LIST ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            mSubtitleView = UIUtils.createLockedTextView(context, relativeParentLabel(file), TEXT_SIZE_LIST_SUBTITLE, COLOR_SUBTEXT);
            mSubtitleView.setGravity(mViewMode == ViewMode.LIST ? Gravity.CENTER_VERTICAL : Gravity.CENTER);

            buildLayoutForMode();
            setOnTouchListener(this::onItemTouch);
        }

        File getFile() {
            return mFile;
        }

        TextView getNameView() {
            return mNameView;
        }

        @Override
        public void setSelected(boolean selected) {
            if (mIsSelected == selected) return;
            mIsSelected = selected;
            super.setSelected(selected);
            setBackground(createRectDrawable(selected ? COLOR_ITEM_SELECTED : COLOR_ITEM_TRANSPARENT, 4));
        }

        private void buildLayoutForMode() {
            removeAllViews();
            mIconView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(mViewMode.iconTextSizeDp));
            mNameView.setText(displayName(mFile));
            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(mViewMode.nameTextSizeDp));

            String parentLabel = mSearchQuery.isEmpty() ? "" : relativeParentLabel(mFile);
            mSubtitleView.setText(parentLabel);
            mSubtitleView.setVisibility(parentLabel.isEmpty() ? View.GONE : View.VISIBLE);

            if (mViewMode == ViewMode.LIST) {
                setOrientation(LinearLayout.HORIZONTAL);
                setGravity(Gravity.CENTER_VERTICAL);
                mIconView.setText((mFile.isDirectory() ? "📁 " : "📄 "));
                addView(mIconView, new LinearLayout.LayoutParams(dp2pxInt(34), ViewGroup.LayoutParams.MATCH_PARENT));

                LinearLayout textColumn = new LinearLayout(getContext());
                textColumn.setOrientation(LinearLayout.VERTICAL);
                textColumn.setGravity(Gravity.CENTER_VERTICAL);
                textColumn.addView(mNameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
                textColumn.addView(mSubtitleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
                addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            } else {
                setOrientation(LinearLayout.VERTICAL);
                setGravity(Gravity.CENTER);
                mIconView.setText(mFile.isDirectory() ? "📁" : "📄");
                addView(mIconView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(mViewMode.iconTextSizeDp + 10)));
                addView(mNameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                addView(mSubtitleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        private boolean onItemTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = event.getX();
                    mDownY = event.getY();
                    mMoved = false;
                    handleItemPressed(mFile, event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - mDownX) > mTouchSlop || Math.abs(event.getY() - mDownY) > mTouchSlop) {
                        mMoved = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    handleItemReleased(mFile, event, mMoved);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    mMoved = false;
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class FileContentLayout extends ViewGroup {
        private final Paint mSelectionFillPaint = new Paint();
        private final Paint mSelectionBorderPaint = new Paint();
        private final RectF mSelectionRect = new RectF();
        private final Set<String> mSelectionBase = new LinkedHashSet<>();
        private ViewMode mMode = ViewMode.LIST;
        private boolean mSelecting = false;
        private boolean mSelectionAdditive = false;
        private float mSelectionStartX;
        private float mSelectionStartY;
        private float mDownX;
        private float mDownY;
        private int mMinimumContentHeight = 0;
        private final float mTouchSlop;

        FileContentLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            mSelectionFillPaint.setColor(UIConstants.ViewPort.Selection.CLR_FILL);
            mSelectionFillPaint.setStyle(Paint.Style.FILL);
            mSelectionBorderPaint.setColor(UIConstants.ViewPort.Selection.CLR_BORDER);
            mSelectionBorderPaint.setStyle(Paint.Style.STROKE);
            mSelectionBorderPaint.setStrokeWidth(dp2px(UIConstants.ViewPort.Selection.STROKE_WIDTH));
            setBackground(createColorDrawable(0xFF1E1E1E));
        }

        void setViewMode(ViewMode mode) {
            mMode = mode;
        }

        void setMinimumContentHeight(int height) {
            if (mMinimumContentHeight == height) return;
            mMinimumContentHeight = height;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height;

            if (mMode == ViewMode.LIST) {
                int rowHeight = dp2pxInt(mMode.itemHeightDp);
                int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
                int childHeightSpec = MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY);
                for (int i = 0; i < getChildCount(); i++) {
                    getChildAt(i).measure(childWidthSpec, childHeightSpec);
                }
                height = rowHeight * getChildCount();
            } else {
                int padding = dp2pxInt(8);
                int gap = dp2pxInt(8);
                int itemWidth = dp2pxInt(mMode.itemWidthDp);
                int itemHeight = dp2pxInt(mMode.itemHeightDp);
                int available = Math.max(itemWidth, width - padding * 2);
                int columns = Math.max(1, (available + gap) / (itemWidth + gap));
                int childWidthSpec = MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY);
                int childHeightSpec = MeasureSpec.makeMeasureSpec(itemHeight, MeasureSpec.EXACTLY);
                for (int i = 0; i < getChildCount(); i++) {
                    getChildAt(i).measure(childWidthSpec, childHeightSpec);
                }
                int rows = getChildCount() == 0 ? 0 : (getChildCount() + columns - 1) / columns;
                height = rows == 0 ? 0 : padding * 2 + rows * itemHeight + (rows - 1) * gap;
            }

            height = Math.max(height, mMinimumContentHeight);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (mMode == ViewMode.LIST) {
                int y = 0;
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    int h = child.getMeasuredHeight();
                    child.layout(0, y, r - l, y + h);
                    y += h;
                }
                return;
            }

            int padding = dp2pxInt(8);
            int gap = dp2pxInt(8);
            int itemWidth = dp2pxInt(mMode.itemWidthDp);
            int itemHeight = dp2pxInt(mMode.itemHeightDp);
            int available = Math.max(itemWidth, getWidth() - padding * 2);
            int columns = Math.max(1, (available + gap) / (itemWidth + gap));
            for (int i = 0; i < getChildCount(); i++) {
                int col = i % columns;
                int row = i / columns;
                int x = padding + col * (itemWidth + gap);
                int y = padding + row * (itemHeight + gap);
                getChildAt(i).layout(x, y, x + itemWidth, y + itemHeight);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (isRightMouse(event)) {
                        RightFileBrowserPanel.this.showContextMenuAtRaw(event.getRawX(), event.getRawY(), null);
                        return true;
                    }
                    mDownX = event.getX();
                    mDownY = event.getY();
                    mSelectionStartX = mDownX;
                    mSelectionStartY = mDownY;
                    mSelectionAdditive = event.isCtrlPressed();
                    mSelectionBase.clear();
                    mSelectionBase.addAll(mSelectedPaths);
                    mSelecting = false;
                    mSelectionRect.setEmpty();
                    if (!mSelectionAdditive) {
                        clearSelection();
                    }
                    mScrollView.requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateSelectionRect(event.getX(), event.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mSelecting) {
                        updateSelectionRect(event.getX(), event.getY());
                    }
                    mSelecting = false;
                    mSelectionRect.setEmpty();
                    mScrollView.requestDisallowInterceptTouchEvent(false);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private void updateSelectionRect(float currentX, float currentY) {
            if (!mSelecting) {
                if (Math.abs(currentX - mDownX) <= mTouchSlop && Math.abs(currentY - mDownY) <= mTouchSlop) {
                    return;
                }
                mSelecting = true;
            }

            float left = Math.min(mSelectionStartX, currentX);
            float top = Math.min(mSelectionStartY, currentY);
            float right = Math.max(mSelectionStartX, currentX);
            float bottom = Math.max(mSelectionStartY, currentY);
            mSelectionRect.set(left, top, right, bottom);
            applyBoxSelection(mSelectionRect, mSelectionAdditive, mSelectionBase);
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (mSelecting && mSelectionRect.right > mSelectionRect.left && mSelectionRect.bottom > mSelectionRect.top) {
                canvas.drawRect(mSelectionRect.left, mSelectionRect.top, mSelectionRect.right, mSelectionRect.bottom, mSelectionFillPaint);
                canvas.drawRect(mSelectionRect.left, mSelectionRect.top, mSelectionRect.right, mSelectionRect.bottom, mSelectionBorderPaint);
            }
        }
    }
}
