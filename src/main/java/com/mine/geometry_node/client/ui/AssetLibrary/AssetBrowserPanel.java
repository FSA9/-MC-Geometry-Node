package com.mine.geometry_node.client.ui.AssetLibrary;

import com.mine.geometry_node.client.ui.utils.PanelSplitter;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.persistence.PathUtils;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.node.NodeGraph;
import com.mine.geometry_node.client.ui.utils.UIUtils;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

public class AssetBrowserPanel extends FrameLayout {

    private final LinearLayout mMainLayout;

    // ==========================================
    // 局部 UI 尺寸常量
    // ==========================================
    private static final float NAV_BAR_HEIGHT = 40.0f;
    private static final float BTN_ADD_WIDTH = 40.0f;
    private static final float ROW_HEIGHT = 40.0f;
    private static final float DRAG_HANDLE_WIDTH = 24.0f;

    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final float TEXT_SIZE_TITLE = 14.0f;
    private static final float TEXT_SIZE_PATH = 14.0f;
    private static final float TEXT_SIZE_HANDLE = 12.0f;
    private static final float TEXT_SIZE_LIST_ITEM = 14.0f;
    private static final float TEXT_SIZE_BTN_ADD = 14.0f;

    private final QuickAccessListLayout mLeftSidebar;
    private final ScrollView mLeftScrollView;
    private final LinearLayout mRightContent;
    private final LinearLayout mFileListContainer;
    private final EditText mPathInput;
    private File mCurrentDirectory;
    private final float mTouchSlop;

    private File mSelectedFile = null;
    private View mSelectedView = null; // 用于控制高亮UI
    private File mClipboardFile = null;
    private boolean mIsCutOperation = false;

    public AssetBrowserPanel(Context context) {
        super(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // 初始化主容器（替代原有的 LinearLayout 行为）
        mMainLayout = new LinearLayout(context);
        mMainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mMainLayout.setBackground(createColorDrawable(UIConstants.MainUI.BG_TIMELINE));
        // 将主容器铺满整个 FrameLayout
        addView(mMainLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 左侧侧边栏初始化
        mLeftSidebar = new QuickAccessListLayout(context);
        mLeftSidebar.setOrientation(LinearLayout.VERTICAL);
        mLeftSidebar.setBackground(createColorDrawable(0xFF1E1E1E));

        mLeftScrollView = new ScrollView(context);
        mLeftScrollView.addView(mLeftSidebar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 将组件添加到 mMainLayout 而不是 this
        mMainLayout.addView(mLeftScrollView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.2f));
        mMainLayout.addView(PanelSplitter.create(context, true));

        // 右侧内容区初始化
        mRightContent = new LinearLayout(context);
        mRightContent.setOrientation(LinearLayout.VERTICAL);
        mMainLayout.addView(mRightContent, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.8f));

        // 导航栏 (NavBar)
        LinearLayout navBar = new LinearLayout(context);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setBackground(createColorDrawable(0xFF2A2A2A));
        mRightContent.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(NAV_BAR_HEIGHT)));

        TextView btnUp = createNavButton(context, "⬆ 向上");
        btnUp.setOnClickListener(v -> navigateUp());
        navBar.addView(btnUp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mPathInput = new EditText(context);
        mPathInput.setTextColor(0xFFCCCCCC);
        mPathInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(TEXT_SIZE_NAV));
        mPathInput.setPadding(dp2pxInt(12), 0, dp2pxInt(12), 0);
        mPathInput.setGravity(Gravity.CENTER_VERTICAL);
        mPathInput.setBackground(null);
        mPathInput.setSingleLine(true);
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
                    buildSidebar(context);
                }
            }
        });
        navBar.addView(btnAdd, new LinearLayout.LayoutParams(dp2pxInt(BTN_ADD_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        // 文件列表滚动区
        ScrollView scrollView = new ScrollView(context);
        mFileListContainer = new LinearLayout(context);
        mFileListContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mFileListContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mRightContent.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        OnTouchListener bgContextListener = (v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && isRightMouse(event)) {
                int[] loc = new int[2];
                this.getLocationOnScreen(loc);
                showContextMenu(event.getRawX() - loc[0], event.getRawY() - loc[1], null, null, null);
                return true;
            }
            return false;
        };

        scrollView.setOnTouchListener(bgContextListener);
        mFileListContainer.setOnTouchListener(bgContextListener);

        buildSidebar(context);
        navigateTo(PathUtils.getLocalDraftsDir());
    }

    private void startInlineEdit(File targetFile, LinearLayout parentRow, TextView originalTextView, boolean isNewFolder, boolean isNewFile) {
        originalTextView.setVisibility(View.GONE); // 隐藏原文字

        EditText editInput = new EditText(getContext());
        editInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(TEXT_SIZE_LIST_ITEM));
        editInput.setTextColor(0xFFFFFFFF);
        editInput.setBackground(createColorDrawable(0xFF444444));
        editInput.setSingleLine(true);
        editInput.setPadding(dp2pxInt(12), dp2pxInt(4), dp2pxInt(12), dp2pxInt(4));

        // 默认文本
        if (!isNewFolder && !isNewFile) {
            editInput.setText(targetFile.getName());
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        parentRow.addView(editInput, parentRow.indexOfChild(originalTextView), lp);

        editInput.requestFocus();
        String name = editInput.getText().toString();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex > 0 && !targetFile.isDirectory()) {
            editInput.setSelection(0, dotIndex);
        } else {
            editInput.setSelection(name.length());
        }

        Runnable commitAction = new Runnable() {
            boolean isCommitted = false; // 状态锁

            @Override
            public void run() {
                if (isCommitted) return;
                isCommitted = true;

                String newName = editInput.getText().toString().trim();
                if (!newName.isEmpty() && (targetFile == null || !newName.equals(targetFile.getName()))) {
                    try {
                        if (isNewFolder) {
                            new File(mCurrentDirectory, newName).mkdir();
                        } else if (isNewFile) {
                            new File(mCurrentDirectory, newName + (newName.endsWith(".json") ? "" : ".json")).createNewFile();
                        } else if (targetFile != null) {
                            targetFile.renameTo(new File(targetFile.getParentFile(), newName));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                refreshFileList(); // 刷新列表，销毁临时输入框
            }
        };

        // 监听回车键
        editInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                commitAction.run();
                return true;
            }
            return false;
        });

        // 失去焦点时自动确认
        editInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitAction.run();
        });
    }

    private void buildSidebar(Context context) {
        mLeftSidebar.removeAllViews();

        TextView title = UIUtils.createLockedTextView(context, "快速访问", TEXT_SIZE_TITLE, 0xFF888888);
        title.setPadding(dp2pxInt(10), dp2pxInt(10), dp2pxInt(10), dp2pxInt(10));
        mLeftSidebar.addView(title);

        for (String pathStr : ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths) {
            mLeftSidebar.addView(createQuickAccessRow(context, pathStr));
        }
    }

    private boolean isRightMouse(MotionEvent e) {
        return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                || e.getActionButton() == MotionEvent.BUTTON_SECONDARY;
    }

    private LinearLayout createQuickAccessRow(Context context, String pathStr) {
        File file = new File(pathStr);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(createColorDrawable(0xFF2A2A2A));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(ROW_HEIGHT));
        rowParams.setMargins(0, 0, 0, dp2pxInt(2));
        row.setLayoutParams(rowParams);

        TextView dragHandle = UIUtils.createLockedTextView(context, " ⋮⋮ ", TEXT_SIZE_HANDLE, 0xFF666666);
        dragHandle.setGravity(Gravity.CENTER);
        row.addView(dragHandle, new LinearLayout.LayoutParams(dp2pxInt(DRAG_HANDLE_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        final float[] startY = {0};
        final boolean[] isDragging = {false};

        // 拖拽逻辑
        dragHandle.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    startY[0] = event.getRawY();
                    isDragging[0] = false;
                    mLeftScrollView.requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy = event.getRawY() - startY[0];
                    if (!isDragging[0] && Math.abs(dy) > mTouchSlop) {
                        isDragging[0] = true;
                        row.setAlpha(0.4f);
                    }
                    if (isDragging[0]) {
                        int[] loc = new int[2];
                        mLeftSidebar.getLocationOnScreen(loc);
                        float dropY = event.getRawY() - loc[1];

                        int indicatorY = mLeftSidebar.getHeight();
                        if (mLeftSidebar.getChildCount() > 1) {
                            View lastChild = mLeftSidebar.getChildAt(mLeftSidebar.getChildCount() - 1);
                            indicatorY = lastChild.getBottom();
                        }

                        for (int i = 1; i < mLeftSidebar.getChildCount(); i++) {
                            View child = mLeftSidebar.getChildAt(i);
                            float centerY = child.getTop() + child.getHeight() / 2f;
                            if (dropY < centerY) {
                                indicatorY = child.getTop();
                                break;
                            }
                        }
                        mLeftSidebar.updateIndicator(indicatorY);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging[0]) {
                        int[] loc = new int[2];
                        mLeftSidebar.getLocationOnScreen(loc);
                        float dropY = event.getRawY() - loc[1];

                        int targetIdx = mLeftSidebar.getChildCount() - 1;
                        for (int i = 1; i < mLeftSidebar.getChildCount(); i++) {
                            View child = mLeftSidebar.getChildAt(i);
                            if (dropY < child.getTop() + child.getHeight() / 2f) {
                                targetIdx = i - 1;
                                break;
                            }
                        }

                        List<String> list = ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths;
                        int currentIndex = list.indexOf(pathStr);

                        if (targetIdx > currentIndex) {
                            targetIdx--;
                        }

                        if (targetIdx != currentIndex && targetIdx >= 0) {
                            String item = list.remove(currentIndex);
                            list.add(targetIdx, item);
                            ConfigManager.INSTANCE.save();
                            row.post(() -> buildSidebar(context));
                        }
                    }
                    row.setAlpha(1.0f);
                    mLeftSidebar.hideIndicator();
                    mLeftScrollView.requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        });

        String displayName = file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
        TextView btnPath = UIUtils.createLockedTextView(context, "📂 " + displayName, TEXT_SIZE_PATH, 0xFFDDDDDD);
        btnPath.setPadding(dp2pxInt(6), 0, dp2pxInt(15), 0);
        btnPath.setGravity(Gravity.CENTER_VERTICAL);
        btnPath.setOnClickListener(v -> navigateTo(file));

        // 修改：明确使用 LinearLayout.LayoutParams，并去掉错误的 (int) 转换，保留 1.0f 权重
        row.addView(btnPath, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView btnDel = UIUtils.createLockedTextView(context, "－", TEXT_SIZE_NAV, 0xFFCC4444);
        btnDel.setGravity(Gravity.CENTER);
        btnDel.setBackground(createColorDrawable(0xFF3A3A3A));

        btnDel.setOnHoverListener((v, event) -> {
            btnDel.setBackground(createColorDrawable(event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? 0xFF882222 : 0xFF3A3A3A));
            return true;
        });

        btnDel.setOnClickListener(v -> {
            ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths.remove(pathStr);
            ConfigManager.INSTANCE.save();
            buildSidebar(context);
        });

        // 修改：明确使用 LinearLayout.LayoutParams
        row.addView(btnDel, new LinearLayout.LayoutParams(dp2pxInt(ROW_HEIGHT), ViewGroup.LayoutParams.MATCH_PARENT));

        return row;
    }

    private void navigateUp() {
        if (mCurrentDirectory != null && mCurrentDirectory.getParentFile() != null) {
            navigateTo(mCurrentDirectory.getParentFile());
        }
    }

    private void navigateTo(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;
        mCurrentDirectory = directory;
        mPathInput.setText(directory.getAbsolutePath());
        refreshFileList();
    }

    private void refreshFileList() {
        mFileListContainer.removeAllViews();
        if (mCurrentDirectory == null) return;

        File[] files = mCurrentDirectory.listFiles();
        if (files == null) return;

        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        Context context = getContext();
        for (File file : files) {
            if (!file.isDirectory() && !file.getName().toLowerCase().endsWith(".json")) continue;

            // 使用 LinearLayout 包裹，方便我们后续替换里面的 TextView
            LinearLayout itemRow = new LinearLayout(context);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int color = file.isDirectory() ? 0xFFDDAA00 : 0xFF88CCFF;
            TextView itemText = UIUtils.createLockedTextView(context, (file.isDirectory() ? "📁 " : "📄 ") + file.getName(), TEXT_SIZE_LIST_ITEM, color);
            itemText.setPadding(dp2pxInt(12), dp2pxInt(10), dp2pxInt(12), dp2pxInt(10));
            itemRow.addView(itemText, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            itemRow.setBackground(createColorDrawable(0));

            // 交互逻辑
            itemRow.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    // 1. 选中高亮状态
                    if (mSelectedView != null && mSelectedView != itemRow) {
                        mSelectedView.setBackground(createColorDrawable(0)); // 恢复上一个
                    }
                    mSelectedFile = file;
                    mSelectedView = itemRow;
                    itemRow.setBackground(createColorDrawable(0xFF445566)); // 选中色

                    boolean isRightClick = (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0 ||
                            event.getActionButton() == MotionEvent.BUTTON_SECONDARY;

                    if (isRightClick) {
                        int[] loc = new int[2];
                        this.getLocationOnScreen(loc);
                        float localX = event.getRawX() - loc[0];
                        float localY = event.getRawY() - loc[1];

                        showContextMenu(localX, localY, file, itemRow, itemText);
                        return true;
                    }
                }
                return false;
            });

            // 双击逻辑
            final long[] lastClickTime = {0};
            itemRow.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - lastClickTime[0] < 300) handleDoubleClick(file);
                lastClickTime[0] = now;
            });

            mFileListContainer.addView(itemRow);
        }
    }

    private void showContextMenu(float localX, float localY, File targetFile, LinearLayout itemRow, TextView itemText) {
        FileContextMenu menu = new FileContextMenu(getContext());

        if (targetFile != null) {
            menu.addMenuItem("复制", () -> { mClipboardFile = targetFile; mIsCutOperation = false; });
            menu.addMenuItem("剪切", () -> { mClipboardFile = targetFile; mIsCutOperation = true; });
            menu.addMenuItem("删除", () -> {
                deleteRecursively(targetFile);
                refreshFileList();
            });
            menu.addDivider();
            menu.addMenuItem("重命名", () -> startInlineEdit(targetFile, itemRow, itemText, false, false));
            menu.addDivider();
        }

        if (mClipboardFile != null && mClipboardFile.exists()) {
            menu.addMenuItem("粘贴", this::performPaste);
            menu.addDivider();
        }
        menu.addMenuItem("新建文件夹", () -> triggerNewItem(true));
        menu.addMenuItem("新建文件", () -> triggerNewItem(false));

        menu.showAt(localX, localY, this);
    }

    private void performPaste() {
        if (mClipboardFile == null || !mClipboardFile.exists() || mCurrentDirectory == null) return;
        try {
            File dest = new File(mCurrentDirectory, mClipboardFile.getName());

            int counter = 1;
            while (dest.exists()) {
                String name = mClipboardFile.getName();
                int dotIdx = name.lastIndexOf('.');
                if (dotIdx > 0 && !mClipboardFile.isDirectory()) {
                    dest = new File(mCurrentDirectory, name.substring(0, dotIdx) + "_" + counter + name.substring(dotIdx));
                } else {
                    dest = new File(mCurrentDirectory, name + "_" + counter);
                }
                counter++;
            }

            if (mIsCutOperation) {
                mClipboardFile.renameTo(dest);
                mClipboardFile = null; // 剪切只能用一次
            } else {
                Files.copy(mClipboardFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            refreshFileList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private void triggerNewItem(boolean isFolder) {
        LinearLayout dummyRow = new LinearLayout(getContext());
        dummyRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView dummyText = new TextView(getContext());
        dummyRow.addView(dummyText);
        mFileListContainer.addView(dummyRow, 0);

        startInlineEdit(new File(""), dummyRow, dummyText, isFolder, !isFolder);
    }

    private void handleDoubleClick(File file) {
        if (file.isDirectory()) {
            navigateTo(file);
        } else if (file.getName().toLowerCase().endsWith(".json")) {
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

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static class QuickAccessListLayout extends LinearLayout {
        private int mIndicatorY = -1;
        private final Paint mIndicatorPaint;

        public QuickAccessListLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            mIndicatorPaint = new Paint();
            mIndicatorPaint.setColor(0xFF00AAFF);
        }

        public void updateIndicator(int y) {
            if (mIndicatorY != y) {
                mIndicatorY = y;
                invalidate();
            }
        }

        public void hideIndicator() {
            if (mIndicatorY != -1) {
                mIndicatorY = -1;
                invalidate();
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (mIndicatorY >= 0) {
                int indicatorHalfHeight = dp2pxInt(2);
                int drawY = Math.max(indicatorHalfHeight, Math.min(getHeight() - indicatorHalfHeight, mIndicatorY));
                canvas.drawRect(0, drawY - indicatorHalfHeight, getWidth(), drawY + indicatorHalfHeight, mIndicatorPaint);
            }
        }
    }
}