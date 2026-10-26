package com.mine.geometry_node.client.ui.editor.graph.menu;

import com.mine.geometry_node.client.ui.editor.graph.GraphUiStyle;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.components.base.overlay.UiInputBarrier;
import com.mine.geometry_node.client.feature.graph.query.NodeSearchService;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.config.AppConfig;
import com.mine.geometry_node.client.config.ConfigChangeListener;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionId;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionRegistry;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionRequest;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionSink;
import com.mine.geometry_node.client.ui.editor.graph.interaction.InteractionContext;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.node.NodeCategory;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;
import java.util.Stack;
import java.util.ArrayList;
import java.util.List;

public class ViewportMenu extends UiInputBarrier {
    private static final int MENU_WIDTH_DP = 220;
    private static final int MENU_PADDING_DP = 8;
    private static final int MENU_EDGE_MARGIN_DP = 6;
    private static final int SEARCH_HEIGHT_DP = 28;
    private static final int SEARCH_BOTTOM_MARGIN_DP = 8;
    private static final int ITEM_HEIGHT_DP = 24;
    private static final int ITEM_RADIUS_DP = 4;
    private static final int MAX_LIST_HEIGHT_DP = 320;

    private static final int COLOR_PANEL_BG = UiThemes.EDITOR.surface().popup();
    private static final int COLOR_PANEL_BORDER = UiThemes.EDITOR.border().strong();
    private static final int COLOR_DIVIDER = UiThemes.EDITOR.border().divider();
    private static final int COLOR_SECTION_TEXT = UiThemes.EDITOR.text().muted();
    private static final int COLOR_ACTION_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_CATEGORY_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_NODE_TEXT = UiThemes.EDITOR.text().secondary();
    private static final int COLOR_MUTED_TEXT = UiThemes.EDITOR.text().muted();
    private static final int COLOR_SHORTCUT_TEXT = UiThemes.EDITOR.text().hint();

    private LinearLayout mContentLayout;
    private LinearLayout mListContainer;
    private ScrollView mScrollView;
    private EditText mSearchBox;

    private InteractionContext mContext;
    private ViewportActionSink mActionSink;
    private float mMenuX, mMenuY;
    private AppConfig mConfig;
    private final ConfigChangeListener mConfigChangeListener = this::applyConfig;

    private final Stack<NodeCategory> mHistory = new Stack<>();
    private NodeCategory mCurrentFolder;

    public ViewportMenu(Context context) {
        super(context);
        mConfig = ConfigManager.INSTANCE.getConfig();
        ConfigManager.INSTANCE.addChangeListener(mConfigChangeListener);
        initUI(context);
        navigateTo(NodeRegistry.INSTANCE.ROOT);
    }

    @Override
    protected void onDetachedFromWindow() {
        ConfigManager.INSTANCE.removeChangeListener(mConfigChangeListener);
        super.onDetachedFromWindow();
    }

    private void initUI(Context context) {
        this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        this.setOnClickListener(v -> dismiss());

        mContentLayout = new LinearLayout(context);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);
        mContentLayout.setBackground(createRectDrawable(COLOR_PANEL_BG, GraphUiStyle.MENU_RADIUS_DP, 1, COLOR_PANEL_BORDER));

        int menuPadding = UIUtils.dp2pxInt(MENU_PADDING_DP);
        mContentLayout.setPadding(menuPadding, menuPadding, menuPadding, menuPadding);
        mContentLayout.setOnClickListener(v -> {});

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(MENU_WIDTH_DP), LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        mContentLayout.setLayoutParams(lp);

        mSearchBox = new UiTextInput(context, new UiTextInput.Style(11, 8,
                UiMetrics.RADIUS_INPUT, UiMetrics.BORDER, UiThemes.EDITOR.text().normal(), UiThemes.EDITOR.text().hint(),
                UiThemes.EDITOR.control().input(), UiThemes.EDITOR.control().inputBorder()));
        mSearchBox.setHint(Component.translatable("menu.node.search").getString());

        mSearchBox.setTextSize(0, UIUtils.dp2px(12));
        mSearchBox.setTextColor(UiThemes.EDITOR.text().strong());
        mSearchBox.setHintTextColor(UiThemes.EDITOR.text().hint());
        mSearchBox.setSingleLine(true);
        mSearchBox.setGravity(Gravity.CENTER_VERTICAL);
        mSearchBox.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) { performSearch(s.toString()); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(SEARCH_HEIGHT_DP));
        searchLp.setMargins(0, 0, 0, UIUtils.dp2pxInt(SEARCH_BOTTOM_MARGIN_DP));
        mContentLayout.addView(mSearchBox, searchLp);

        mScrollView = new UiScrollView(context);
        mListContainer = new LinearLayout(context);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        mScrollView.addView(mListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mContentLayout.addView(mScrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mContentLayout);
    }

    public void showAt(float x, float y, InteractionContext context, ViewportActionSink actionSink) {
        this.mContext = context;
        this.mActionSink = actionSink;
        mMenuX = x;
        mMenuY = y;
        renderCurrentFolder();

        ViewGroup parent = (ViewGroup) context;
        if (parent != null) layoutPanel(parent, x, y);

        mSearchBox.post(() -> {
            if (getParent() == null || getVisibility() != VISIBLE) return;
            mSearchBox.setText("");
            mSearchBox.requestFocus();
        });
    }

    public void dismiss() {
        if (mContext != null) {
            mContext.closeMenu();
        }
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
            addSectionLabel("操作");
            addActionItem(ViewportActionId.SAVE, v -> {
                performAction(ViewportActionId.SAVE, ViewportActionRequest.EMPTY);
                post(this::dismiss);
            });
            addActionItem(ViewportActionId.EXPORT_IMAGE, v -> {
                performAction(ViewportActionId.EXPORT_IMAGE, ViewportActionRequest.EMPTY);
                post(this::dismiss);
            });

            boolean behaviorTree = mActionSink != null && GraphTypeRegistry.BEHAVIOR_TREE.id()
                    .equals(mActionSink.graphTypeId());
            if (mContext != null && mContext.isInsideGroupScope()) {
                addActionItem(ViewportActionId.EXIT_GROUP, v -> {
                    performAction(ViewportActionId.EXIT_GROUP, ViewportActionRequest.EMPTY);
                    post(this::dismiss);
                });
            } else {
                addActionItem(ViewportActionId.GROUP_INTO_FRAME, v -> {
                    performAction(ViewportActionId.GROUP_INTO_FRAME, ViewportActionRequest.EMPTY);
                    post(this::dismiss);
                });
            }

            if (!behaviorTree) {
                addActionItem(ViewportActionId.GROUP_INTO_NODE_GROUP, v -> {
                    performAction(ViewportActionId.GROUP_INTO_NODE_GROUP, ViewportActionRequest.EMPTY);
                    post(this::dismiss);
                });
            }

            addDivider();
            addSectionLabel("节点");
        }

        if (mCurrentFolder != NodeRegistry.INSTANCE.ROOT) {
            addClickItem("← " + Component.translatable("menu.node.back").getString(), COLOR_MUTED_TEXT, v -> navigateBack());
            addDivider();
        }

        for (NodeCategory sub : mCurrentFolder.getSubCategories()) {
            String label = Component.translatable(sub.translationKey).getString() + "    ›";
            addClickItem(label, COLOR_CATEGORY_TEXT, v -> { mSearchBox.setText(""); navigateTo(sub); });
        }

        for (BaseNode node : mCurrentFolder.getNodes()) {
            String label = node.getDefaultDefinition().displayName().getString();
            addClickItem(label, COLOR_NODE_TEXT, v -> {
                performAction(ViewportActionId.ADD_NODE, ViewportActionRequest.builder()
                        .screen(mMenuX, mMenuY)
                        .typeId(node.getTypeId())
                        .build());
                post(this::dismiss);
            });
        }

        updateScrollHeight();
        relayoutIfAttached();
    }

    private void performSearch(String query) {
        if (query.trim().isEmpty()) { renderCurrentFolder(); return; }
        mListContainer.removeAllViews();
        List<NodeDef> visibleDefinitions = new ArrayList<>();
        for (NodeDef definition : NodeRegistry.INSTANCE.getAllDefinitions()) {
            if (definition != null) visibleDefinitions.add(definition);
        }
        NodeSearchService.Page results = NodeSearchService.search(
                visibleDefinitions, query, 0, visibleDefinitions.size());
        for (NodeSearchService.Match match : results.items()) {
            NodeDef def = match.definition();
            addClickItem(match.displayName(), COLOR_NODE_TEXT, v -> {
                performAction(ViewportActionId.ADD_NODE, ViewportActionRequest.builder()
                        .screen(mMenuX, mMenuY)
                        .typeId(def.typeId())
                        .build());
                post(this::dismiss);
            });
        }
        if (results.total() == 0) addEmptyItem("未找到匹配节点");

        updateScrollHeight();
        relayoutIfAttached();
    }

    private void addClickItem(String text, int color, View.OnClickListener listener) {
        TextView tv = UiMenuItems.row(getContext(), text, false, true, () -> {});
        tv.setTextColor(UiMenuItems.textColors(color));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(ITEM_HEIGHT_DP));
        int marginV = UIUtils.dp2pxInt(1);
        lp.setMargins(0, marginV, 0, marginV);
        mListContainer.addView(tv, lp);
    }

    private void addShortcutItem(String text, String shortcut, int color, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(getContext()) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        UiMenuItems.decorate(row, 0, ITEM_RADIUS_DP, UiThemes.EDITOR);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        row.setOnClickListener(listener);

        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(0, UIUtils.dp2px(12));
        label.setTextColor(UiMenuItems.textColors(color));
        label.setDuplicateParentStateEnabled(true);
        label.setSingleLine(true);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView shortcutView = new TextView(getContext());
        shortcutView.setText(shortcut != null ? shortcut : "");
        shortcutView.setTextSize(0, UIUtils.dp2px(10));
        shortcutView.setTextColor(UiMenuItems.textColors(COLOR_SHORTCUT_TEXT));
        shortcutView.setDuplicateParentStateEnabled(true);
        shortcutView.setSingleLine(true);
        shortcutView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(shortcutView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(ITEM_HEIGHT_DP));
        int marginV = UIUtils.dp2pxInt(1);
        lp.setMargins(0, marginV, 0, marginV);
        mListContainer.addView(row, lp);
    }

    private void addActionItem(ViewportActionId actionId, View.OnClickListener listener) {
        String shortcut = ViewportActionRegistry.shortcutText(actionId, mConfig);
        String label = ViewportActionRegistry.label(actionId);
        if (shortcut == null || shortcut.isBlank()) {
            addClickItem(label, COLOR_ACTION_TEXT, listener);
        } else {
            addShortcutItem(label, shortcut, COLOR_ACTION_TEXT, listener);
        }
    }

    private void addSectionLabel(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(0, UIUtils.dp2px(10));
        tv.setTextColor(COLOR_SECTION_TEXT);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(18));
        lp.setMargins(0, UIUtils.dp2pxInt(2), 0, 0);
        mListContainer.addView(tv, lp);
    }

    private void addEmptyItem(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(0, UIUtils.dp2px(12));
        tv.setTextColor(COLOR_MUTED_TEXT);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        mListContainer.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(ITEM_HEIGHT_DP)));
    }

    private void addDivider() {
        View divider = new View(getContext());
        divider.setBackground(createRectDrawable(COLOR_DIVIDER, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1));
        lp.setMargins(0, UIUtils.dp2pxInt(6), 0, UIUtils.dp2pxInt(6));
        mListContainer.addView(divider, lp);
    }

    private void updateScrollHeight() {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mScrollView.getLayoutParams();
        lp.height = calculateScrollHeight(getAvailableScrollHeightPx(null, 0));
        mScrollView.setLayoutParams(lp);
    }

    private void relayoutIfAttached() {
        if (mContext instanceof ViewGroup parent) {
            layoutPanel(parent, mMenuX, mMenuY);
        }
    }

    private void layoutPanel(ViewGroup parent, float x, float y) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
        int menuWidth = UIUtils.dp2pxInt(MENU_WIDTH_DP);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.width = menuWidth;

        LinearLayout.LayoutParams scrollLp = (LinearLayout.LayoutParams) mScrollView.getLayoutParams();
        scrollLp.height = calculateScrollHeight(getAvailableScrollHeightPx(parent, y));
        mScrollView.setLayoutParams(scrollLp);

        int widthSpec = MeasureSpec.makeMeasureSpec(menuWidth, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        mContentLayout.measure(widthSpec, heightSpec);

        int actualH = mContentLayout.getMeasuredHeight();
        if (actualH == 0) {
            actualH = UIUtils.dp2pxInt(SEARCH_HEIGHT_DP + SEARCH_BOTTOM_MARGIN_DP + MAX_LIST_HEIGHT_DP + MENU_PADDING_DP * 2);
        }

        int edge = UIUtils.dp2pxInt(MENU_EDGE_MARGIN_DP);
        int parentW = parent.getWidth();
        int parentH = parent.getHeight();

        int targetX = (int) x;
        int targetY = (int) y;

        if (parentW > 0 && targetX + menuWidth + edge > parentW) {
            targetX = Math.max(edge, parentW - menuWidth - edge);
        }
        if (parentH > 0 && targetY + actualH + edge > parentH) {
            int aboveY = (int) y - actualH;
            targetY = aboveY >= edge ? aboveY : Math.max(edge, parentH - actualH - edge);
        }

        lp.leftMargin = Math.max(edge, targetX);
        lp.topMargin = Math.max(edge, targetY);
        mContentLayout.setLayoutParams(lp);
    }

    private int calculateScrollHeight(int maxAvailableHeightPx) {
        int listHeight = measureListHeight();
        if (listHeight <= 0) {
            int fallbackItems = Math.max(1, mListContainer.getChildCount());
            listHeight = UIUtils.dp2pxInt(fallbackItems * (ITEM_HEIGHT_DP + 2));
        }

        int maxListHeight = UIUtils.dp2pxInt(MAX_LIST_HEIGHT_DP);
        int targetHeight = Math.min(listHeight, maxListHeight);
        if (maxAvailableHeightPx > 0) {
            targetHeight = Math.min(targetHeight, maxAvailableHeightPx);
        }
        return Math.max(UIUtils.dp2pxInt(ITEM_HEIGHT_DP), targetHeight);
    }

    private int measureListHeight() {
        int listWidth = UIUtils.dp2pxInt(MENU_WIDTH_DP - MENU_PADDING_DP * 2);
        int widthSpec = MeasureSpec.makeMeasureSpec(Math.max(0, listWidth), MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        mListContainer.measure(widthSpec, heightSpec);
        return mListContainer.getMeasuredHeight();
    }

    private int getAvailableScrollHeightPx(ViewGroup parent, float anchorY) {
        if (parent == null || parent.getHeight() <= 0) {
            return UIUtils.dp2pxInt(MAX_LIST_HEIGHT_DP);
        }

        int edge = UIUtils.dp2pxInt(MENU_EDGE_MARGIN_DP);
        int nonListHeight = UIUtils.dp2pxInt(MENU_PADDING_DP * 2 + SEARCH_HEIGHT_DP + SEARCH_BOTTOM_MARGIN_DP);
        int belowHeight = parent.getHeight() - (int) anchorY - edge - nonListHeight;
        int aboveHeight = (int) anchorY - edge - nonListHeight;
        int availableHeight = Math.max(belowHeight, aboveHeight);
        return Math.max(UIUtils.dp2pxInt(ITEM_HEIGHT_DP), availableHeight);
    }

    private ShapeDrawable createRectDrawable(int color, int radius) {
        return createRectDrawable(color, radius, 0, 0);
    }

    private ShapeDrawable createRectDrawable(int color, int radius, int strokeWidthDp, int strokeColor) {
        ShapeDrawable d = new ShapeDrawable();
        d.setColor(color);
        d.setCornerRadius(UIUtils.dp2px(radius));
        if (strokeWidthDp > 0) {
            d.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return d;
    }

    private void applyConfig(AppConfig config) {
        mConfig = config;
        if (mListContainer == null || mCurrentFolder == null) return;
        String query = mSearchBox != null ? mSearchBox.getText().toString() : "";
        if (query == null || query.trim().isEmpty()) {
            renderCurrentFolder();
        } else {
            performSearch(query);
        }
    }

    private void performAction(ViewportActionId id, ViewportActionRequest request) {
        if (mActionSink != null) {
            mActionSink.performAction(id, request);
        }
    }
}
