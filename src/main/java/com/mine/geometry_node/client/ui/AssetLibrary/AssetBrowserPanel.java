package com.mine.geometry_node.client.ui.AssetLibrary;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.Viewport.UINode;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.NodeDef;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class AssetBrowserPanel extends LinearLayout {

    private final LinearLayout mLeftSidebar;
    private final LinearLayout mRightContent;
    private final LinearLayout mFileListContainer;
    private final EditText mPathInput;

    private File mCurrentDirectory;

    public AssetBrowserPanel(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(UIConstants.MainUI.BG_TIMELINE));

        // ==========================================
        // 1. 左侧：快捷访问栏 (Drives & Shortcuts)
        // ==========================================
        mLeftSidebar = new LinearLayout(context);
        mLeftSidebar.setOrientation(LinearLayout.VERTICAL);
        mLeftSidebar.setBackground(createColorDrawable(0xFF1E1E1E));

        LayoutParams leftParams = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        leftParams.weight = 0.2f;
        addView(mLeftSidebar, leftParams);

        // ==========================================
        // 2. 动态分割线 (复用 UIConstants 配置)
        // ==========================================
        addView(createDraggableSplitter(context));

        // ==========================================
        // 3. 右侧：文件浏览区
        // ==========================================
        mRightContent = new LinearLayout(context);
        mRightContent.setOrientation(LinearLayout.VERTICAL);

        LayoutParams rightParams = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        rightParams.weight = 0.8f;
        addView(mRightContent, rightParams);

        // 3.1 右侧顶部：路径导航栏
        LinearLayout navBar = new LinearLayout(context);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackground(createColorDrawable(0xFF2A2A2A));
        mRightContent.addView(navBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40));

        TextView btnUp = new TextView(context);
        btnUp.setText("⬆ 向上");
        btnUp.setTextColor(0xFFDDDDDD);
        btnUp.setBackground(createColorDrawable(0xFF444444));
        btnUp.setPadding(16, 0, 16, 0);
        btnUp.setGravity(Gravity.CENTER);
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
                String inputPath = mPathInput.getText().toString().trim();
                File targetDir = new File(inputPath);

                if (targetDir.exists() && targetDir.isDirectory()) {
                    navigateTo(targetDir);
                    mPathInput.clearFocus();
                } else {
                    if (mCurrentDirectory != null) {
                        mPathInput.setText(mCurrentDirectory.getAbsolutePath());
                    }
                }
                return true;
            }
            return false;
        });

        navBar.addView(mPathInput, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 3.2 右侧主体：可滚动的文件列表
        ScrollView scrollView = new ScrollView(context);
        mFileListContainer = new LinearLayout(context);
        mFileListContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mFileListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mRightContent.addView(scrollView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ==========================================
        // 初始化加载
        // ==========================================
        buildSidebar(context);

        File defaultDir = getLocalDraftsFolder();
        if (!defaultDir.exists()) defaultDir.mkdirs();
        navigateTo(defaultDir);
    }

    // ==========================================
    // 界面交互逻辑
    // ==========================================

    /**
     * 创建基于常量的动态分割线
     */
    private View createDraggableSplitter(Context context) {
        FrameLayout container = new FrameLayout(context);

        int hitSize = UIConstants.MainUI.SPLITTER_HITBOX_SIZE;
        container.setLayoutParams(new LinearLayout.LayoutParams(hitSize, ViewGroup.LayoutParams.MATCH_PARENT));

        View visualLine = new View(context);
        visualLine.setBackground(createColorDrawable(UIConstants.MainUI.BG_SPLITTER));

        int visualSize = UIConstants.MainUI.SPLITTER_VISUAL_SIZE;
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(visualSize, ViewGroup.LayoutParams.MATCH_PARENT);
        lineParams.gravity = Gravity.CENTER;

        container.addView(visualLine, lineParams);

        // 拖拽控制逻辑
        final float[] lastX = {0};
        final boolean[] isDragging = {false};

        container.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging[0] = true;
                    lastX[0] = event.getRawX();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!isDragging[0]) return false;
                    float dx = event.getRawX() - lastX[0];
                    performSplitterResize(dx);
                    lastX[0] = event.getRawX();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging[0] = false;
                    return true;
            }
            return false;
        });

        return container;
    }

    /**
     * 处理左右侧 Weight 权重的动态调整
     */
    private void performSplitterResize(float dx) {
        LinearLayout.LayoutParams leftParams = (LinearLayout.LayoutParams) mLeftSidebar.getLayoutParams();
        LinearLayout.LayoutParams rightParams = (LinearLayout.LayoutParams) mRightContent.getLayoutParams();

        if (leftParams.weight > 0 && rightParams.weight > 0) {
            float totalWeight = leftParams.weight + rightParams.weight;
            float totalWidth = mLeftSidebar.getWidth() + mRightContent.getWidth();

            if (totalWidth <= 0) return;

            // 根据物理偏移量计算出占比权重的变化量
            float dWeight = (dx / totalWidth) * totalWeight;
            leftParams.weight += dWeight;
            rightParams.weight -= dWeight;

            // 约束最小权重，防止面板被彻底拉没 (复用 MainUI 的最小比例配置)
            float minW = UIConstants.MainUI.WEIGHT_MIN;
            if (leftParams.weight < minW) {
                rightParams.weight -= (minW - leftParams.weight);
                leftParams.weight = minW;
            }
            if (rightParams.weight < minW) {
                leftParams.weight -= (minW - rightParams.weight);
                rightParams.weight = minW;
            }

            mLeftSidebar.requestLayout();
            mRightContent.requestLayout();
        }
    }

    private void buildSidebar(Context context) {
        mLeftSidebar.removeAllViews();

        TextView title = new TextView(context);
        title.setText("快速访问");
        title.setTextColor(0xFF888888);
        title.setPadding(10, 10, 10, 10);
        mLeftSidebar.addView(title);

        TextView btnDrafts = createButton(context, "📂 本地草稿箱 (Local Drafts)", 0xFF2A2A2A);
        btnDrafts.setOnClickListener(v -> {
            File drafts = getLocalDraftsFolder();
            if (!drafts.exists()) drafts.mkdirs();
            navigateTo(drafts);
        });
        mLeftSidebar.addView(btnDrafts);

        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                TextView btnRoot = createButton(context, "💽 " + root.getAbsolutePath(), 0xFF2A2A2A);
                btnRoot.setOnClickListener(v -> navigateTo(root));
                mLeftSidebar.addView(btnRoot);
            }
        }
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

            String icon = file.isDirectory() ? "📁 " : "📄 ";
            int color = file.isDirectory() ? 0xFFDDAA00 : 0xFF88CCFF;

            TextView item = new TextView(context);
            item.setText(icon + file.getName());
            item.setTextColor(color);
            item.setPadding(10, 10, 10, 10);
            item.setTextSize(16);

            item.setBackground(createColorDrawable(0x00000000));
            item.setOnHoverListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    item.setBackground(createColorDrawable(0xFF333333));
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    item.setBackground(createColorDrawable(0x00000000));
                }
                return true;
            });

            final long[] lastClickTime = {0};
            item.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - lastClickTime[0] < 300) {
                    handleDoubleClick(file);
                }
                lastClickTime[0] = now;
            });

            mFileListContainer.addView(item, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void handleDoubleClick(File file) {
        if (file.isDirectory()) {
            navigateTo(file);
        } else {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".json")) {
                openGraphFile(file);
            }
        }
    }

    private void openGraphFile(File file) {
        try {
            String jsonContent = Files.readString(file.toPath()).trim();
            NodeGraph graph;

            if (jsonContent.isEmpty() || jsonContent.equals("{}")) {
                graph = new NodeGraph(file.getName());
            } else {
                graph = GraphJsonIO.fromJson(jsonContent);
            }

            String tabName = file.getName();
            GraphSession session = new GraphSession(getContext(), file.getAbsolutePath(), tabName, graph);

            if (graph.nodes != null) {
                for (NodeData data : graph.nodes.values()) {
                    NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(data);
                    if (def != null) {
                        UINode uiNode = new UINode(getContext(), data, def, session.editorContext);
                        uiNode.setTranslationX(data.getX());
                        uiNode.setTranslationY(data.getY());

                        session.nodeViews.put(data.id, uiNode);
                        session.nodeLayer.addView(uiNode);
                    }
                }
            }

            DocumentManager.INSTANCE.openSession(session);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TextView createButton(Context context, String text, int bgColor) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextColor(0xFFDDDDDD);
        btn.setBackground(createColorDrawable(bgColor));
        btn.setPadding(15, 10, 15, 10);
        btn.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 2);
        btn.setLayoutParams(lp);

        return btn;
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private File getLocalDraftsFolder() {
        File baseDir;
        try {
            if (net.minecraft.client.Minecraft.getInstance() != null && net.minecraft.client.Minecraft.getInstance().gameDirectory != null) {
                baseDir = net.minecraft.client.Minecraft.getInstance().gameDirectory;
            } else {
                baseDir = new File(System.getProperty("user.dir"));
            }
        } catch (Throwable t) {
            baseDir = new File(System.getProperty("user.dir"));
        }
        return baseDir.toPath().resolve("geometry_nodes").resolve("local_drafts").toFile();
    }
}