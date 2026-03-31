package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.core.node.NodeCategory;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;

import java.util.Stack;

public class ViewportMenu {

    private PopupWindow mPopupWindow;
    private LinearLayout mContentLayout;
    private LinearLayout mListContainer;
    private EditText mSearchBox;

    private Viewport mViewport;
    private float mMenuX, mMenuY;

    private final Stack<NodeCategory> mHistory = new Stack<>();
    private NodeCategory mCurrentFolder;
    private final Context mContext;

    public ViewportMenu(Context context) {
        mContext = context;
        initUI();
        initPopupWindow();
        navigateTo(NodeRegistry.INSTANCE.ROOT);
    }

    private void initUI() {
        mContentLayout = new LinearLayout(mContext);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);
        mContentLayout.setBackground(createRectDrawable(
                UIConstants.ViewPort.NodeMenu.BG_COLOR,
                UIConstants.ViewPort.NodeMenu.BORDER_RADIUS));
        mContentLayout.setPadding(4, 4, 4, 4);

        // --- 1. 搜索框 ---
        mSearchBox = new EditText(mContext);
        mSearchBox.setHint(Component.translatable("menu.node.search").getString());
        float searchFontSize = UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX * (float)UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
        mSearchBox.setTextSize(0, searchFontSize);
        mSearchBox.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
        mSearchBox.setHintTextColor(0xFF666666);
        mSearchBox.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.SEARCH_BG_COLOR, 4));
        mSearchBox.setPadding(10, 0, 10, 0);

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) { performSearch(s.toString()); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX);
        searchLp.setMargins(4, 4, 4, 6);
        mContentLayout.addView(mSearchBox, searchLp);

        // --- 2. 滚动列表区 ---
        ScrollView sv = new ScrollView(mContext);
        mListContainer = new LinearLayout(mContext);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        sv.addView(mListContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        mContentLayout.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 300));
    }

    private void initPopupWindow() {
        mPopupWindow = new PopupWindow(mContentLayout, 200, ViewGroup.LayoutParams.WRAP_CONTENT);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setOutsideTouchable(true);

        ShapeDrawable transparentBg = new ShapeDrawable();
        transparentBg.setColor(0x00000000);
        mPopupWindow.setBackgroundDrawable(transparentBg);

        mPopupWindow.setOnDismissListener(() -> {
            if (mViewport != null) {
                mViewport.requestViewportFocus();
            }
        });
    }

    public void showAt(float x, float y, ViewGroup parent) {
        if (parent instanceof Viewport) mViewport = (Viewport) parent;

        // 保存用于生成节点的坐标
        mMenuX = x;
        mMenuY = y;

        // 坐标系转换 (局部坐标 -> 屏幕全局坐标)
        int[] location = new int[2];
        if (parent != null) {
            parent.getLocationOnScreen(location);
        }

        // 计算出在屏幕上的绝对坐标
        int popX = location[0] + (int) x;
        int popY = location[1] + (int) y;

        // 边界防溢出
        if (parent != null) {
            if (x + 200 > parent.getWidth()) {
                popX = location[0] + parent.getWidth() - 200;
            }
            if (y + 350 > parent.getHeight()) { // 假设菜单最大高度约 350
                popY = location[1] + parent.getHeight() - 350;
            }
        }

        // 使用全局坐标弹出
        mPopupWindow.showAtLocation(parent, Gravity.TOP | Gravity.LEFT, popX, popY);

        mSearchBox.setText("");
        mSearchBox.requestFocus();
    }

    public void dismiss() {
        if (mPopupWindow != null && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return mPopupWindow != null && mPopupWindow.isShowing();
    }

    // --- 核心导航&渲染逻辑 ---
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
        com.mine.geometry_node.core.node.NodeGraph graph = mViewport.getEditorContext().getGraph();
        String jsonOutput = com.mine.geometry_node.client.ui.persistence.GraphJsonIO.toJson(graph);
        System.out.println("[Menu] 手动保存成功:");
        System.out.println(jsonOutput);
    }

    private void renderCurrentFolder() {
        mListContainer.removeAllViews();

        if (mCurrentFolder == NodeRegistry.INSTANCE.ROOT) {
            addClickItem("💾 " + "保存项目 (Save JSON)", 0xFF44AAFF, v -> {
                if (mViewport != null) {
                    saveGraphAction();
                }
                dismiss();
            });

            View divider = new View(mContext);
            mListContainer.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }

        if (mCurrentFolder != NodeRegistry.INSTANCE.ROOT) {
            addClickItem("← " + Component.translatable("menu.node.back").getString(), 0xFF888888, v -> navigateBack());
        }
        for (NodeCategory sub : mCurrentFolder.getSubCategories()) {
            String label = Component.translatable(sub.translationKey).getString() + "  ›";
            addClickItem(label, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> {
                mSearchBox.setText("");
                navigateTo(sub);
            });
        }
        for (BaseNode node : mCurrentFolder.getNodes()) {
            String label = node.getDefaultDefinition().displayName().getString();
            addClickItem(label, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> {
                if (mViewport != null) {
                    mViewport.addNode(mMenuX, mMenuY, node.getTypeId());
                }
                dismiss();
            });
        }
    }

    // 搜索
    private void performSearch(String query) {
        if (query.trim().isEmpty()) {
            renderCurrentFolder();
            return;
        }
        mListContainer.removeAllViews();
        String q = query.toLowerCase().trim();

        for (com.mine.geometry_node.core.node.nodes.NodeDef def : NodeRegistry.INSTANCE.getAllDefinitions()) {
            String name = def.displayName().getString();
            if (name.toLowerCase().contains(q)) {
                addClickItem(name, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> {
                    if (mViewport != null) {
                        mViewport.addNode(mMenuX, mMenuY, def.typeId());
                    }
                    dismiss();
                });
            }
        }
    }

    // --- 底层 UI 组件 ---
    private void addClickItem(String text, int color, View.OnClickListener listener) {
        TextView tv = new TextView(mContext);
        tv.setText(text);
        float fontSize = UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT * (float)UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
        tv.setTextSize(0, fontSize);
        tv.setTextColor(color);
        tv.setPadding(12, 0, 12, 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setOnClickListener(listener);

        tv.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                tv.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.HOVER_COLOR, 4));
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
        lp.setMargins(2, 1, 2, 1);
        mListContainer.addView(tv, lp);
    }

    private ShapeDrawable createRectDrawable(int color, int radius) {
        ShapeDrawable d = new ShapeDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
}