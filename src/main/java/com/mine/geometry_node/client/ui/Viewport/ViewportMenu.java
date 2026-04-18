package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.core.node.NodeCategory;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;

import java.util.Stack;

/**
 * 视口右键菜单 (节点创建与全局操作)
 * <p>
 * 该类同时作为全屏透明遮罩 (Overlay)，点击空白处自动关闭菜单。
 */
public class ViewportMenu extends FrameLayout {

    // ==========================================
    // UI 尺寸与样式常量配置
    // ==========================================
    private static final int MENU_PADDING = 4;                     // 菜单主容器内边距
    private static final int LIST_SCROLL_HEIGHT = 300;             // 节点列表滚动区域的固定高度
    private static final int ITEM_PADDING_H = 12;                  // 菜单项左右内边距
    private static final int ITEM_MARGIN_V = 1;                    // 菜单项上下外边距
    private static final int ITEM_MARGIN_H = 2;                    // 菜单项左右外边距
    private static final int CORNER_RADIUS_SMALL = 4;              // 搜索框及 Hover 状态的圆角大小
    private static final int DIVIDER_HEIGHT = 1;                   // 分割线高度
    private static final int SEARCH_BOX_PADDING_H = 10;            // 搜索框左右内边距

    // 边界防溢出预估尺寸 (用于调整菜单弹出位置)
    private static final int BOUNDARY_WIDTH_CHECK = 230;           // 触发右侧溢出的预估宽度
    private static final int BOUNDARY_WIDTH_SAFE = 250;            // 溢出后向左偏移的安全宽度
    private static final int BOUNDARY_HEIGHT_CHECK = 350;          // 触发底部溢出的预估高度
    private static final int BOUNDARY_HEIGHT_SAFE = 360;           // 溢出后向上偏移的安全高度

    // 特殊文本颜色
    private static final int CLR_HINT_TEXT = 0xFF666666;           // 搜索框提示文本颜色
    private static final int CLR_SAVE_BTN = 0xFF44AAFF;            // 保存按钮高亮颜色
    private static final int CLR_BACK_BTN = 0xFF888888;            // 返回上一级按钮颜色

    // ==========================================
    // 核心 UI 组件与状态数据
    // ==========================================
    private LinearLayout mContentLayout;                           // 真正的菜单视觉面板
    private LinearLayout mListContainer;                           // 菜单列表项容器
    private EditText mSearchBox;                                   // 搜索输入框

    private Viewport mViewport;                                    // 绑定的视口引用
    private float mMenuX, mMenuY;                                  // 菜单触发的视口逻辑坐标 (用于在此处生成节点)

    private final Stack<NodeCategory> mHistory = new Stack<>();    // 文件夹导航历史栈
    private NodeCategory mCurrentFolder;                           // 当前所在的节点分类

    // ==========================================
    // 初始化
    // ==========================================

    public ViewportMenu(Context context) {
        super(context);
        initUI(context);
        navigateTo(NodeRegistry.INSTANCE.ROOT);
    }

    private void initUI(Context context) {
        // 1. 设置自身为全屏透明遮罩 (拦截外部点击事件)
        this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        this.setOnClickListener(v -> dismiss());

        // 2. 构建真正的菜单内容容器
        mContentLayout = new LinearLayout(context);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);
        mContentLayout.setBackground(createRectDrawable(
                UIConstants.ViewPort.NodeMenu.BG_COLOR,
                UIConstants.ViewPort.NodeMenu.BORDER_RADIUS));
        mContentLayout.setPadding(MENU_PADDING, MENU_PADDING, MENU_PADDING, MENU_PADDING);

        // 拦截点击事件，防止点击菜单本体时事件穿透到背景遮罩导致误关闭
        mContentLayout.setOnClickListener(v -> {});

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                UIConstants.ViewPort.NodeMenu.ITEM_WEIGHT, LayoutParams.WRAP_CONTENT);
        mContentLayout.setLayoutParams(lp);

        // 3. 构建搜索框
        mSearchBox = new EditText(context);
        mSearchBox.setHint(Component.translatable("menu.node.search").getString());

        float searchFontSize = UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX * (float) UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
        mSearchBox.setTextSize(0, searchFontSize);
        mSearchBox.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
        mSearchBox.setHintTextColor(CLR_HINT_TEXT);
        mSearchBox.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.SEARCH_BG_COLOR, CORNER_RADIUS_SMALL));
        mSearchBox.setPadding(SEARCH_BOX_PADDING_H, 0, SEARCH_BOX_PADDING_H, 0);

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) { performSearch(s.toString()); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX);
        searchLp.setMargins(MENU_PADDING, MENU_PADDING, MENU_PADDING, 6);
        mContentLayout.addView(mSearchBox, searchLp);

        // 4. 构建滚动列表区
        ScrollView sv = new ScrollView(context);
        mListContainer = new LinearLayout(context);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        sv.addView(mListContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        mContentLayout.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, LIST_SCROLL_HEIGHT));

        // 5. 将菜单容器添加到全屏遮罩中
        addView(mContentLayout);
    }

    // ==========================================
    // 生命周期与定位
    // ==========================================

    public void showAt(float x, float y, ViewGroup parent) {
        if (parent instanceof Viewport) {
            mViewport = (Viewport) parent;
        }

        // 保存用于生成节点的逻辑坐标
        mMenuX = x;
        mMenuY = y;

        // 控制内部菜单容器的位置
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
        lp.leftMargin = (int) x;
        lp.topMargin = (int) y;

        // 边界防溢出处理：确保菜单不会超出父容器屏幕外
        if (parent != null) {
            if (x + BOUNDARY_WIDTH_CHECK > parent.getWidth()) {
                lp.leftMargin = (int) (parent.getWidth() - BOUNDARY_WIDTH_SAFE);
            }
            if (y + BOUNDARY_HEIGHT_CHECK > parent.getHeight()) {
                lp.topMargin = (int) (parent.getHeight() - BOUNDARY_HEIGHT_SAFE);
            }
        }
        mContentLayout.setLayoutParams(lp);

        // 挂载到父容器
        if (this.getParent() != null) {
            ((ViewGroup) this.getParent()).removeView(this);
        }
        parent.addView(this);

        // 弹出时自动清空并聚焦搜索框
        mSearchBox.post(() -> {
            mSearchBox.setText("");
            mSearchBox.requestFocus();
        });
    }

    public void dismiss() {
        if (mViewport != null) {
            // 回调视口统一处理移除和焦点恢复
            mViewport.closeMenu();
        } else if (getParent() != null) {
            // 兜底逻辑：直接从父节点移除自己
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    // ==========================================
    // 菜单导航与渲染逻辑
    // ==========================================

    private void navigateTo(NodeCategory folder) {
        if (mCurrentFolder != null && folder != mCurrentFolder) {
            mHistory.push(mCurrentFolder);
        }
        mCurrentFolder = folder;
        renderCurrentFolder();
    }

    private void navigateBack() {
        if (!mHistory.isEmpty()) {
            mCurrentFolder = mHistory.pop();
            renderCurrentFolder();
        }
    }

    private void saveGraphAction() {
        DocumentManager.INSTANCE.saveSession(DocumentManager.INSTANCE.getActiveSession());
    }

    private void renderCurrentFolder() {
        mListContainer.removeAllViews();

        // --- A. 根目录特有全局操作 ---
        if (mCurrentFolder == NodeRegistry.INSTANCE.ROOT) {
            addClickItem("💾 Save", CLR_SAVE_BTN, v -> {
                saveGraphAction();
                // 延迟移除 View，避免在事件分发循环内发生视图层级改变导致死锁/定格
                post(this::dismiss);
            });

            // 绘制分割线
            icyllis.modernui.view.View divider = new icyllis.modernui.view.View(getContext());
            mListContainer.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, DIVIDER_HEIGHT));
        }

        // --- B. 返回按钮 (非根目录时显示) ---
        if (mCurrentFolder != NodeRegistry.INSTANCE.ROOT) {
            addClickItem("← " + Component.translatable("menu.node.back").getString(), CLR_BACK_BTN, v -> navigateBack());
        }

        // --- C. 渲染子文件夹 ---
        for (NodeCategory sub : mCurrentFolder.getSubCategories()) {
            String label = Component.translatable(sub.translationKey).getString() + "  ›";
            addClickItem(label, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> {
                mSearchBox.setText("");
                navigateTo(sub);
            });
        }

        // --- D. 渲染当前目录下的具体节点 ---
        for (BaseNode node : mCurrentFolder.getNodes()) {
            String label = node.getDefaultDefinition().displayName().getString();
            addClickItem(label, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> {
                if (mViewport != null) {
                    mViewport.addNode(mMenuX, mMenuY, node.getTypeId());
                }
                // 延迟移除 View，避免定格 Bug
                post(this::dismiss);
            });
        }
    }

    // ==========================================
    // 搜索逻辑
    // ==========================================

    private void performSearch(String query) {
        if (query.trim().isEmpty()) {
            renderCurrentFolder();
            return;
        }
        mListContainer.removeAllViews();
        String q = query.toLowerCase().trim();

        // 遍历注册表中所有节点，模糊匹配名称
        for (com.mine.geometry_node.core.node.nodes.NodeDef def : NodeRegistry.INSTANCE.getAllDefinitions()) {
            String name = def.displayName().getString();
            if (name.toLowerCase().contains(q)) {
                addClickItem(name, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> {
                    if (mViewport != null) {
                        mViewport.addNode(mMenuX, mMenuY, def.typeId());
                    }
                    post(this::dismiss);
                });
            }
        }
    }

    // ==========================================
    // 底部 UI 辅助构造方法
    // ==========================================

    /**
     * 添加一个标准的菜单点击交互项
     */
    private void addClickItem(String text, int color, icyllis.modernui.view.View.OnClickListener listener) {
        TextView tv = new TextView(getContext());
        tv.setText(text);

        float fontSize = UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT * (float) UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
        tv.setTextSize(0, fontSize);
        tv.setTextColor(color);
        tv.setPadding(ITEM_PADDING_H, 0, ITEM_PADDING_H, 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setOnClickListener(listener);

        // 悬浮高亮反馈
        tv.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                tv.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.HOVER_COLOR, CORNER_RADIUS_SMALL));
                tv.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_HOVER);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                tv.setBackground(null);
                tv.setTextColor(color);
            }
            return false;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT);
        lp.setMargins(ITEM_MARGIN_H, ITEM_MARGIN_V, ITEM_MARGIN_H, ITEM_MARGIN_V);
        mListContainer.addView(tv, lp);
    }

    /**
     * 创建纯色圆角矩形背景
     */
    private ShapeDrawable createRectDrawable(int color, int radius) {
        ShapeDrawable d = new ShapeDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
}