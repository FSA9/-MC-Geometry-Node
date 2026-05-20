package com.mine.geometry_node.client.ui.bottom_window.asset_library;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeGraph;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

public class RightFileBrowserPanel extends LinearLayout {

    private final AssetBrowserPanel mCoordinator;
    private final EditText mPathInput;
    private final LinearLayout mFileListContainer;
    private File mCurrentDirectory;

    private File mSelectedFile = null;
    private View mSelectedView = null;
    private File mClipboardFile = null;
    private boolean mIsCutOperation = false;

    private static final float NAV_BAR_HEIGHT = 40.0f;
    private static final float BTN_ADD_WIDTH = 40.0f;
    private static final float ROW_HEIGHT = 40.0f;
    private static final float TEXT_SIZE_NAV = 14.0f;
    private static final float TEXT_SIZE_LIST_ITEM = 14.0f;
    private static final float TEXT_SIZE_BTN_ADD = 14.0f;

    public RightFileBrowserPanel(Context context, AssetBrowserPanel coordinator) {
        super(context);
        mCoordinator = coordinator;
        setOrientation(LinearLayout.VERTICAL);

        // 导航栏 (NavBar)
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
                    // 通知中介者更新左侧栏布局
                    mCoordinator.notifySidebarChanged();
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
        addView(scrollView, new LinearLayout.LayoutParams(
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
    }

    public void navigateTo(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;
        mCurrentDirectory = directory;
        mPathInput.setText(directory.getAbsolutePath());
        refreshFileList();
    }

    private void navigateUp() {
        if (mCurrentDirectory != null && mCurrentDirectory.getParentFile() != null) {
            navigateTo(mCurrentDirectory.getParentFile());
        }
    }

    private boolean isRightMouse(MotionEvent e) {
        return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                || e.getActionButton() == MotionEvent.BUTTON_SECONDARY;
    }

    public void refreshFileList() {
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

            LinearLayout itemRow = new LinearLayout(context);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int color = file.isDirectory() ? 0xFFDDAA00 : 0xFF88CCFF;
            TextView itemText = UIUtils.createLockedTextView(context, (file.isDirectory() ? "📁 " : "📄 ") + file.getName(), TEXT_SIZE_LIST_ITEM, color);
            itemText.setPadding(dp2pxInt(12), dp2pxInt(10), dp2pxInt(12), dp2pxInt(10));
            itemRow.addView(itemText, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            itemRow.setBackground(createColorDrawable(0));

            itemRow.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (mSelectedView != null && mSelectedView != itemRow) {
                        mSelectedView.setBackground(createColorDrawable(0));
                    }
                    mSelectedFile = file;
                    mSelectedView = itemRow;
                    itemRow.setBackground(createColorDrawable(0xFF445566));

                    if (isRightMouse(event)) {
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

    private void startInlineEdit(File targetFile, LinearLayout parentRow, TextView originalTextView, boolean isNewFolder, boolean isNewFile) {
        originalTextView.setVisibility(View.GONE);

        EditText editInput = new EditText(getContext());
        editInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(TEXT_SIZE_LIST_ITEM));
        editInput.setTextColor(0xFFFFFFFF);
        editInput.setBackground(createColorDrawable(0xFF444444));
        editInput.setSingleLine(true);
        editInput.setPadding(dp2pxInt(12), dp2pxInt(4), dp2pxInt(12), dp2pxInt(4));

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
            boolean isCommitted = false;

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
                mClipboardFile = null;
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
}