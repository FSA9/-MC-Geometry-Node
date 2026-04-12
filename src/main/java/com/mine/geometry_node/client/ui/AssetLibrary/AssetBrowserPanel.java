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
import icyllis.modernui.view.ViewGroup;
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
    private final TextView mPathText;

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

        // 分割线
        icyllis.modernui.view.View splitter = new icyllis.modernui.view.View(context);
        splitter.setBackground(createColorDrawable(0xFF111111));
        addView(splitter, new LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT));

        // ==========================================
        // 2. 右侧：文件浏览区
        // ==========================================
        mRightContent = new LinearLayout(context);
        mRightContent.setOrientation(LinearLayout.VERTICAL);

        LayoutParams rightParams = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        rightParams.weight = 0.8f;
        addView(mRightContent, rightParams);

        // 2.1 右侧顶部：路径导航栏
        LinearLayout navBar = new LinearLayout(context);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackground(createColorDrawable(0xFF2A2A2A));
        mRightContent.addView(navBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30));

        TextView btnUp = createButton(context, "[向上 返回]", 0xFF444444);
        btnUp.setOnClickListener(v -> navigateUp());
        navBar.addView(btnUp, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mPathText = new TextView(context);
        mPathText.setTextColor(0xFFAAAAAA);
        mPathText.setGravity(Gravity.CENTER_VERTICAL);
        mPathText.setPadding(10, 0, 0, 0);
        navBar.addView(mPathText, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2.2 右侧主体：可滚动的文件列表
        ScrollView scrollView = new ScrollView(context);
        mFileListContainer = new LinearLayout(context);
        mFileListContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mFileListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mRightContent.addView(scrollView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ==========================================
        // 3. 初始化加载
        // ==========================================
        buildSidebar(context);

        // 默认跳转到 local_drafts 目录
        File defaultDir = getLocalDraftsFolder();
        if (!defaultDir.exists()) defaultDir.mkdirs();
        navigateTo(defaultDir);
    }

    // ==========================================
    // 逻辑方法
    // ==========================================

    private void buildSidebar(Context context) {
        mLeftSidebar.removeAllViews();

        // 标题
        TextView title = new TextView(context);
        title.setText("快速访问");
        title.setTextColor(0xFF888888);
        title.setPadding(10, 10, 10, 10);
        mLeftSidebar.addView(title);

        // 默认草稿目录按钮
        TextView btnDrafts = createButton(context, "📂 本地草稿箱 (Local Drafts)", 0xFF2A2A2A);
        btnDrafts.setOnClickListener(v -> {
            File drafts = getLocalDraftsFolder();
            if (!drafts.exists()) drafts.mkdirs();
            navigateTo(drafts);
        });
        mLeftSidebar.addView(btnDrafts);

        // 系统磁盘根目录 (C:, D: 等)
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
        mPathText.setText(directory.getAbsolutePath());
        refreshFileList();
    }

    private void refreshFileList() {
        mFileListContainer.removeAllViews();
        if (mCurrentDirectory == null) return;

        File[] files = mCurrentDirectory.listFiles();
        if (files == null) return;

        // 排序：文件夹在前，文件在后；然后按字母排序
        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        Context context = getContext();
        for (File file : files) {
            // 我们只显示文件夹和 .json 文件
            if (!file.isDirectory() && !file.getName().toLowerCase().endsWith(".json")) continue;

            String icon = file.isDirectory() ? "📁 " : "📄 ";
            int color = file.isDirectory() ? 0xFFDDAA00 : 0xFF88CCFF; // 文件夹黄色，JSON 蓝色

            TextView item = new TextView(context);
            item.setText(icon + file.getName());
            item.setTextColor(color);
            item.setPadding(10, 10, 10, 10);
            item.setTextSize(16);

            // 鼠标悬停变色 (简易实现)
            item.setBackground(createColorDrawable(0x00000000));
            item.setOnHoverListener((v, event) -> {
                if (event.getAction() == icyllis.modernui.view.MotionEvent.ACTION_HOVER_ENTER) {
                    item.setBackground(createColorDrawable(0xFF333333));
                } else if (event.getAction() == icyllis.modernui.view.MotionEvent.ACTION_HOVER_EXIT) {
                    item.setBackground(createColorDrawable(0x00000000));
                }
                return true;
            });

            // 监听双击事件
            final long[] lastClickTime = {0};
            item.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - lastClickTime[0] < 300) { // 300ms 内连点视为双击
                    handleDoubleClick(file);
                }
                lastClickTime[0] = now;
            });

            mFileListContainer.addView(item, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void handleDoubleClick(File file) {
        System.out.println("[AssetBrowser] 双击检测: " + file.getName() + " (文件夹: " + file.isDirectory() + ")");

        if (file.isDirectory()) {
            navigateTo(file);
        } else {
            // 修复：使用 toLowerCase() 处理后缀，防止 .JSON (大写) 无法匹配
            String name = file.getName().toLowerCase();
            if (name.endsWith(".json")) {
                System.out.println("[AssetBrowser] 识别为图纸文件，开始执行加载流程...");
                openGraphFile(file);
            } else {
                System.out.println("[AssetBrowser] 识别为普通文件，忽略操作。");
            }
        }
    }

    // ==========================================
    // 核心：读取 JSON 并载入编辑器
    // ==========================================
    private void openGraphFile(File file) {
        try {
            // 1. 读取文本并去除首尾空白
            String jsonContent = Files.readString(file.toPath()).trim();
            NodeGraph graph;

            // 2. 智能反序列化：如果是空文件或空对象，直接初始化一张白纸
            if (jsonContent.isEmpty() || jsonContent.equals("{}")) {
                System.out.println("[AssetBrowser] 检测到空文件: " + file.getName() + "，已自动初始化为空白图纸。");
                graph = new NodeGraph(file.getName());
            } else {
                graph = GraphJsonIO.fromJson(jsonContent);
                System.out.println("[AssetBrowser] 成功解析图纸: " + graph.graphName + "，包含节点数: " + graph.nodes.size());
            }

            // 3. 创建全新的 Session
            String tabName = file.getName();
            GraphSession session = new GraphSession(getContext(), file.getAbsolutePath(), tabName, graph);

            // 4. 重建 UI 节点 (如果是空图纸，这里的循环会自动跳过，安全无痛)
            if (graph.nodes != null) {
                for (NodeData data : graph.nodes.values()) {
                    NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(data);
                    if (def != null) {
                        // 创建 UINode
                        UINode uiNode = new UINode(getContext(), data, def, session.editorContext);
                        // 恢复位置
                        uiNode.setTranslationX(data.getX());
                        uiNode.setTranslationY(data.getY());

                        // 存入 Session 的专属画布
                        session.nodeViews.put(data.id, uiNode);
                        session.nodeLayer.addView(uiNode);
                    } else {
                        System.err.println("[AssetBrowser] 警告：找不到节点定义: " + data.type);
                    }
                }
            }

            // 5. 提交给 DocumentManager，系统会自动渲染并画出所有连线！
            DocumentManager.INSTANCE.openSession(session);
            System.out.println("[AssetBrowser] 图纸已提交至 DocumentManager 并激活。");

        } catch (Exception e) {
            System.err.println("[AssetBrowser] 无法打开文件 " + file.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==========================================
    // 辅助 UI 工具
    // ==========================================
    private TextView createButton(Context context, String text, int bgColor) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextColor(0xFFDDDDDD);
        btn.setBackground(createColorDrawable(bgColor));
        btn.setPadding(15, 10, 15, 10);
        btn.setGravity(Gravity.CENTER_VERTICAL);

        // 增加一点边距
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

    /**
     * 安全地获取本地草稿箱目录 (兼容游戏内环境与独立 UI 测试环境)
     */
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
            System.err.println("[AssetBrowserPanel::getLocalDraftsFolder]Error: Failed to get folder!");
        }
        return baseDir.toPath().resolve("geometry_nodes").resolve("local_drafts").toFile();
    }
}