package com.mine.geometry_node.client.ui.viewport.menu;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
import com.mine.geometry_node.core.node.NodeCategory;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;

import java.util.Stack;

public class ViewportMenu extends FrameLayout {

    private LinearLayout mContentLayout;
    private LinearLayout mListContainer;
    private EditText mSearchBox;

    private InteractionContext mContext;
    private float mMenuX, mMenuY;

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
        mContentLayout.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.BG_COLOR, UIConstants.ViewPort.NodeMenu.BORDER_RADIUS));

        int menuPadding = UIUtils.dp2pxInt(4);
        mContentLayout.setPadding(menuPadding, menuPadding, menuPadding, menuPadding);
        mContentLayout.setOnClickListener(v -> {});

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(100), LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        mContentLayout.setLayoutParams(lp);

        mSearchBox = new EditText(context);
        mSearchBox.setHint(Component.translatable("menu.node.search").getString());

        float searchFontSizeDp = UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX * (float) UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
        mSearchBox.setTextSize(0, UIUtils.dp2px(searchFontSizeDp));
        mSearchBox.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
        mSearchBox.setHintTextColor(0xFF666666);
        mSearchBox.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.SEARCH_BG_COLOR, 4));
        mSearchBox.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) { performSearch(s.toString()); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX));
        searchLp.setMargins(menuPadding, menuPadding, menuPadding, UIUtils.dp2pxInt(6));
        mContentLayout.addView(mSearchBox, searchLp);

        ScrollView sv = new ScrollView(context);
        mListContainer = new LinearLayout(context);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        sv.addView(mListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mContentLayout.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mContentLayout);
    }

    public void showAt(float x, float y, InteractionContext context) {
        this.mContext = context;
        mMenuX = x;
        mMenuY = y;

        ViewGroup parent = (ViewGroup) context;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = (int) x;
        lp.topMargin = (int) y;

        if (parent != null) {
            int widthSpec = MeasureSpec.makeMeasureSpec(UIUtils.dp2pxInt(200), MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            mContentLayout.measure(widthSpec, heightSpec);

            int actualW = mContentLayout.getMeasuredWidth();
            int actualH = mContentLayout.getMeasuredHeight();

            if (actualW == 0) actualW = UIUtils.dp2pxInt(200);
            if (actualH == 0) actualH = UIUtils.dp2pxInt(300);

            if (x + actualW > parent.getWidth()) lp.leftMargin = Math.max(0, parent.getWidth() - actualW);
            if (y + actualH > parent.getHeight()) lp.topMargin = Math.max(0, parent.getHeight() - actualH);
        }
        mContentLayout.setLayoutParams(lp);

        if (this.getParent() != null) ((ViewGroup) this.getParent()).removeView(this);
        parent.addView(this);

        mSearchBox.post(() -> {
            mSearchBox.setText("");
            mSearchBox.requestFocus();
        });
    }

    public void dismiss() {
        if (mContext != null) mContext.closeMenu();
        else if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
    }

    private void navigateTo(NodeCategory folder) {
        if (mCurrentFolder != null && folder != mCurrentFolder) mHistory.push(mCurrentFolder);
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
                if (mContext != null) mContext.requestSave();
                post(this::dismiss);
            });

            addClickItem("📦 添加图框", 0xFF44AAFF, v -> {
                if (mContext != null) {
                    float uiX = mContext.getCamera().screenToUIX(mMenuX);
                    float uiY = mContext.getCamera().screenToUIY(mMenuY);
                    mContext.requestAddFrame(uiX, uiY);
                }
                post(this::dismiss);
            });

            addClickItem("🖇 并入图框", 0xFF44AAFF, v -> {
                if (mContext != null) {
                    mContext.requestGroupIntoFrame();
                    mContext.clearSelection();
                }
                post(this::dismiss);
            });

            View divider = new View(getContext());
            mListContainer.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1)));
        }

        if (mCurrentFolder != NodeRegistry.INSTANCE.ROOT) {
            addClickItem("← " + Component.translatable("menu.node.back").getString(), 0xFF888888, v -> navigateBack());
        }

        for (NodeCategory sub : mCurrentFolder.getSubCategories()) {
            String label = Component.translatable(sub.translationKey).getString() + "  ›";
            addClickItem(label, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> { mSearchBox.setText(""); navigateTo(sub); });
        }

        for (BaseNode node : mCurrentFolder.getNodes()) {
            String label = node.getDefaultDefinition().displayName().getString();
            addClickItem(label, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> {
                if (mContext != null) mContext.requestAddNode(mMenuX, mMenuY, node.getTypeId());
                post(this::dismiss);
            });
        }
    }

    private void performSearch(String query) {
        if (query.trim().isEmpty()) { renderCurrentFolder(); return; }
        mListContainer.removeAllViews();
        String q = query.toLowerCase().trim();

        for (com.mine.geometry_node.core.node.nodes.NodeDef def : NodeRegistry.INSTANCE.getAllDefinitions()) {
            String name = def.displayName().getString();
            if (name.toLowerCase().contains(q)) {
                addClickItem(name, UIConstants.ViewPort.NodeMenu.TEXT_COLOR, v -> {
                    if (mContext != null) mContext.requestAddNode(mMenuX, mMenuY, def.typeId());
                    post(this::dismiss);
                });
            }
        }
    }

    private void addClickItem(String text, int color, View.OnClickListener listener) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        float itemFontSizeDp = UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT * (float) UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
        tv.setTextSize(0, UIUtils.dp2px(itemFontSizeDp));
        tv.setTextColor(color);
        tv.setPadding(UIUtils.dp2pxInt(3), 0, UIUtils.dp2pxInt(3), 0);
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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT));
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