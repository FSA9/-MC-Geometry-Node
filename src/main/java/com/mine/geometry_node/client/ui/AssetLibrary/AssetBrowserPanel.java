package com.mine.geometry_node.client.ui.AssetLibrary;

import com.mine.geometry_node.client.ui.utils.PanelSplitter;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.persistence.PathUtils;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.node.NodeGraph;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class AssetBrowserPanel extends LinearLayout {

    private final QuickAccessListLayout mLeftSidebar;
    private final LinearLayout mRightContent; // 声明 final
    private final LinearLayout mFileListContainer;
    private final EditText mPathInput;
    private File mCurrentDirectory;
    private final float mTouchSlop;

    public AssetBrowserPanel(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(UIConstants.MainUI.BG_TIMELINE));
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // ==========================================
        // 1. 优先实例化所有核心容器
        // ==========================================
        mLeftSidebar = new QuickAccessListLayout(context);
        mLeftSidebar.setOrientation(LinearLayout.VERTICAL);
        mLeftSidebar.setBackground(createColorDrawable(0xFF1E1E1E));

        mRightContent = new LinearLayout(context);
        mRightContent.setOrientation(LinearLayout.VERTICAL);

        // ==========================================
        // 2. 按视觉从左到右的顺序 addView
        // ==========================================

        // A. 添加左侧
        addView(mLeftSidebar, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.2f));

        // B. 使用公共组件添加中间分割线
        addView(PanelSplitter.create(context, true, null));

        // C. 添加右侧内容区
        addView(mRightContent, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.8f));

        // ==========================================
        // 3. 构建右侧内部的 UI (导航栏 + 文件列表)
        // ==========================================
        LinearLayout navBar = new LinearLayout(context);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setBackground(createColorDrawable(0xFF2A2A2A));
        mRightContent.addView(navBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40));

        TextView btnUp = createNavButton(context, "⬆ 向上");
        btnUp.setOnClickListener(v -> navigateUp());
        navBar.addView(btnUp, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mPathInput = new EditText(context);
        mPathInput.setTextColor(0xFFCCCCCC);
        mPathInput.setTextSize(14);
        mPathInput.setPadding(12, 0, 12, 0);
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
        navBar.addView(mPathInput, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView btnAdd = createNavButton(context, "＋");
        btnAdd.setTextSize(16);
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
        navBar.addView(btnAdd, new LayoutParams(40, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scrollView = new ScrollView(context);
        mFileListContainer = new LinearLayout(context);
        mFileListContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mFileListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mRightContent.addView(scrollView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 触发初始化
        buildSidebar(context);
        navigateTo(PathUtils.getLocalDraftsDir());
    }

    private void buildSidebar(Context context) {
        mLeftSidebar.removeAllViews();

        TextView title = new TextView(context);
        title.setText("快速访问");
        title.setTextColor(0xFF888888);
        title.setPadding(10, 10, 10, 10);
        mLeftSidebar.addView(title);

        for (String pathStr : ConfigManager.INSTANCE.getConfig().assetBrowser.quickAccessPaths) {
            mLeftSidebar.addView(createQuickAccessRow(context, pathStr));
        }
    }

    private LinearLayout createQuickAccessRow(Context context, String pathStr) {
        File file = new File(pathStr);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(createColorDrawable(0xFF2A2A2A));

        LayoutParams rowParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 34);
        rowParams.setMargins(0, 0, 0, 2);
        row.setLayoutParams(rowParams);

        TextView dragHandle = new TextView(context);
        dragHandle.setText(" ⋮⋮ ");
        dragHandle.setTextColor(0xFF666666);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setTextSize(12);
        row.addView(dragHandle, new LayoutParams(24, ViewGroup.LayoutParams.MATCH_PARENT));

        final float[] startY = {0};
        final boolean[] isDragging = {false};

        dragHandle.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    startY[0] = event.getRawY();
                    isDragging[0] = false;
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
                    break;
            }
            return true;
        });

        String displayName = file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
        TextView btnPath = new TextView(context);
        btnPath.setText("📂 " + displayName);
        btnPath.setPadding(6, 0, 15, 0);
        btnPath.setGravity(Gravity.CENTER_VERTICAL);
        btnPath.setTextColor(0xFFDDDDDD);
        btnPath.setOnClickListener(v -> navigateTo(file));
        row.addView(btnPath, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView btnDel = new TextView(context);
        btnDel.setText("－");
        btnDel.setTextSize(14);
        btnDel.setTextColor(0xFFCC4444);
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

        row.addView(btnDel, new LayoutParams(34, ViewGroup.LayoutParams.MATCH_PARENT));

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

            TextView item = new TextView(context);
            item.setText((file.isDirectory() ? "📁 " : "📄 ") + file.getName());
            item.setTextColor(file.isDirectory() ? 0xFFDDAA00 : 0xFF88CCFF);
            item.setPadding(12, 10, 12, 10);
            item.setTextSize(15);

            item.setBackground(createColorDrawable(0));
            item.setOnHoverListener((v, event) -> {
                item.setBackground(createColorDrawable(event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? 0xFF333333 : 0));
                return true;
            });

            final long[] lastClickTime = {0};
            item.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - lastClickTime[0] < 300) handleDoubleClick(file);
                lastClickTime[0] = now;
            });

            mFileListContainer.addView(item, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
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

            // 现在是 3 个参数了，非常干净
            GraphSession session = new GraphSession(file.getAbsolutePath(), file.getName(), graph);

            // 剩下的事情全交给 DocumentManager 和 Viewport 处理
            DocumentManager.INSTANCE.openSession(session);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private TextView createNavButton(Context context, String text) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextColor(0xFFDDDDDD);
        btn.setPadding(16, 0, 16, 0);
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
            mIndicatorPaint.setColor(0xFF00AAFF); // IDE 经典蓝
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
                int drawY = Math.max(2, Math.min(getHeight() - 2, mIndicatorY));
                canvas.drawRect(0, drawY - 2, getWidth(), drawY + 2, mIndicatorPaint);
            }
        }
    }
}