package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.UIUtils; // 引用工具类
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
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;

import java.util.Stack;

public class ViewportMenu extends FrameLayout {

    private LinearLayout mContentLayout;
    private LinearLayout mListContainer;
    private EditText mSearchBox;

    private Viewport mViewport;
    private float mMenuX, mMenuY; // 视口逻辑坐标

    private final Stack<NodeCategory> mHistory = new Stack<>();
    private NodeCategory mCurrentFolder;

    public ViewportMenu(Context context) {
        super(context);
        initUI(context);
        navigateTo(NodeRegistry.INSTANCE.ROOT);
    }

    private void initUI(Context context) {
        this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        this.setOnClickListener(v -> dismiss());

        mContentLayout = new LinearLayout(context);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);
        mContentLayout.setBackground(createRectDrawable(
                UIConstants.ViewPort.NodeMenu.BG_COLOR,
                UIConstants.ViewPort.NodeMenu.BORDER_RADIUS));

        int menuPadding = UIUtils.dp2pxInt(4); // 4dp
        mContentLayout.setPadding(menuPadding, menuPadding, menuPadding, menuPadding);
        mContentLayout.setOnClickListener(v -> {});

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(200), LayoutParams.WRAP_CONTENT); // 假设宽度 200dp
        mContentLayout.setLayoutParams(lp);

        // 搜索框
        mSearchBox = new EditText(context);
        mSearchBox.setHint(Component.translatable("menu.node.search").getString());

        // 字体计算：高度 * 比例，保持逻辑一致
        float searchFontSize = UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX * (float) UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
        mSearchBox.setTextSize(0, searchFontSize);
        mSearchBox.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
        mSearchBox.setHintTextColor(0xFF666666);
        mSearchBox.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.SEARCH_BG_COLOR, 4));
        mSearchBox.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) { performSearch(s.toString()); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX));
        searchLp.setMargins(menuPadding, menuPadding, menuPadding, UIUtils.dp2pxInt(6));
        mContentLayout.addView(mSearchBox, searchLp);

        // 列表
        ScrollView sv = new ScrollView(context);
        mListContainer = new LinearLayout(context);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        sv.addView(mListContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        mContentLayout.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(300))); // 300dp 滚动区

        addView(mContentLayout);
    }

    public void showAt(float x, float y, ViewGroup parent) {
        if (parent instanceof Viewport vp) {
            mViewport = vp;
        }

        mMenuX = x;
        mMenuY = y;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
        lp.leftMargin = (int) x;
        lp.topMargin = (int) y;

        // 边界防溢出 (使用工具类转换临界值)
        if (parent != null) {
            int checkW = UIUtils.dp2pxInt(230);
            int safeW = UIUtils.dp2pxInt(250);
            int checkH = UIUtils.dp2pxInt(350);
            int safeH = UIUtils.dp2pxInt(360);

            if (x + checkW > parent.getWidth()) {
                lp.leftMargin = (int) (parent.getWidth() - safeW);
            }
            if (y + checkH > parent.getHeight()) {
                lp.topMargin = (int) (parent.getHeight() - safeH);
            }
        }
        mContentLayout.setLayoutParams(lp);

        if (this.getParent() != null) {
            ((ViewGroup) this.getParent()).removeView(this);
        }
        parent.addView(this);

        mSearchBox.post(() -> {
            mSearchBox.setText("");
            mSearchBox.requestFocus();
        });
    }

    public void dismiss() {
        if (mViewport != null) {
            mViewport.closeMenu();
        } else if (getParent() != null) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

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

    private void renderCurrentFolder() {
        mListContainer.removeAllViews();

        if (mCurrentFolder == NodeRegistry.INSTANCE.ROOT) {
            addClickItem("💾 Save", 0xFF44AAFF, v -> {
                DocumentManager.INSTANCE.saveSession(DocumentManager.INSTANCE.getActiveSession());
                post(this::dismiss);
            });
            View divider = new View(getContext());
            mListContainer.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1)));
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
                post(this::dismiss);
            });
        }
    }

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
                    post(this::dismiss);
                });
            }
        }
    }

    private void addClickItem(String text, int color, View.OnClickListener listener) {
        TextView tv = new TextView(getContext());
        tv.setText(text);

        float fontSize = UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT * (float) UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
        tv.setTextSize(0, fontSize);
        tv.setTextColor(color);
        tv.setPadding(UIUtils.dp2pxInt(12), 0, UIUtils.dp2pxInt(12), 0);
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
                UIUtils.dp2pxInt(UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT));
        int marginV = UIUtils.dp2pxInt(1);
        int marginH = UIUtils.dp2pxInt(2);
        lp.setMargins(marginH, marginV, marginH, marginV);
        mListContainer.addView(tv, lp);
    }

    private ShapeDrawable createRectDrawable(int color, int radius) {
        ShapeDrawable d = new ShapeDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
}