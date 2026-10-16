package com.mine.geometry_node.client.ui.components.input;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.AreaTransientUi;
import net.minecraft.network.chat.Component;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Search, selection and anchor geometry shared by graph and ordinary forms. */
public class UiSearchableMenu extends FrameLayout {
    private static final int MENU_WIDTH_DP = 220;
    private static final int MENU_PADDING_DP = 8;
    private static final int SEARCH_HEIGHT_DP = 28;
    private static final int TITLE_HEIGHT_DP = 22;
    private static final int SEARCH_RADIUS_DP = 5;
    private static final int ITEM_HEIGHT_DP = 24;
    private static final int ITEM_RADIUS_DP = 4;
    private static final int MAX_VISIBLE_ITEMS = 6;

    private static final int COLOR_PANEL_BG = UiTheme.Surface.PANEL;
    private static final int COLOR_PANEL_BORDER = UiTheme.Control.BORDER;
    private static final int COLOR_TITLE_BG = UiTheme.Control.INPUT;
    private static final int COLOR_TITLE_TEXT = UiTheme.INLINE.text();
    private static final int COLOR_SEARCH_BG = UiTheme.Control.INPUT;
    private static final int COLOR_SEARCH_BORDER = UiTheme.Control.INPUT_BORDER;
    private static final int COLOR_NODE_TEXT = UiTheme.Text.SECONDARY;
    private static final int COLOR_MUTED_TEXT = UiTheme.Text.DISABLED;
    private static final int COLOR_HOVER_BG = UiTheme.Control.SELECTED;

    private static ShapeDrawable createRectDrawable(int color, float radiusDp) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        return drawable;
    }

    private static ShapeDrawable createRectDrawable(int color, float radiusDp, float strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = createRectDrawable(color, radiusDp);
        drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        return drawable;
    }


    private LinearLayout mContentLayout;
    private LinearLayout mListContainer;
    private EditText mSearchBox;
    private ScrollView mScrollView;
    private final String mTitle;
    private final List<String> mOptions;
    private final List<String> mFilteredOptions;
    private final Map<String, String> mOptionLabels;
    private final Consumer<String> mOnSelect;

    private View mAnchor;

    private ViewGroup mContext;
    private float mAnchorScale = 1.0f;
    private boolean mIsTracking = false;
    private AreaLifecycle.Subscription areaBinding = AreaLifecycle.Subscription.NONE;
    private final Runnable focusSearch = () -> {
        if (getVisibility() != VISIBLE || getParent() == null || mAnchor == null || !anchorInHost()) return;
        mSearchBox.setText("");
        mSearchBox.requestFocus();
    };

    public UiSearchableMenu(Context context, String title, List<String> options,
                              Map<String, String> optionLabels, Consumer<String> onSelect) {
        super(context);
        this.mTitle = title;
        this.mOptions = List.copyOf(options);
        this.mFilteredOptions = new ArrayList<>(options);
        this.mOptionLabels = optionLabels != null ? Map.copyOf(optionLabels) : Map.of();
        this.mOnSelect = onSelect;
        initUI(context);
    }

    private void initUI(Context context) {
        this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        this.setOnClickListener(v -> dismiss());

        mContentLayout = new LinearLayout(context);
        mContentLayout.setOrientation(LinearLayout.VERTICAL);
        mContentLayout.setBackground(createRectDrawable(COLOR_PANEL_BG, 6, 1, COLOR_PANEL_BORDER));
        mContentLayout.setPadding(
                UIUtils.dp2pxInt(MENU_PADDING_DP),
                UIUtils.dp2pxInt(MENU_PADDING_DP),
                UIUtils.dp2pxInt(MENU_PADDING_DP),
                UIUtils.dp2pxInt(MENU_PADDING_DP)
        );
        mContentLayout.setOnClickListener(v -> {});

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(MENU_WIDTH_DP), LayoutParams.WRAP_CONTENT);
        mContentLayout.setLayoutParams(lp);

        if (mTitle != null && !mTitle.isBlank()) {
            TextView titleView = new TextView(context);
            titleView.setText(mTitle);
            titleView.setTextSize(0, UIUtils.dp2px(12));
            titleView.setTextColor(COLOR_TITLE_TEXT);
            titleView.setSingleLine(true);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            titleView.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
            titleView.setBackground(createRectDrawable(COLOR_TITLE_BG, ITEM_RADIUS_DP));

            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(TITLE_HEIGHT_DP)
            );
            titleLp.setMargins(0, 0, 0, UIUtils.dp2pxInt(6));
            mContentLayout.addView(titleView, titleLp);
        }

        mSearchBox = new UiSearchInput(context, new UiSearchInput.Style(12, 10, SEARCH_RADIUS_DP, 1,
                UiTheme.Text.STRONG, UiTheme.Text.HINT, COLOR_SEARCH_BG, COLOR_SEARCH_BORDER));
        mSearchBox.setHint(Component.translatable("geometry_node.asset_library.toolbar.search").getString());
        mSearchBox.setTextSize(0, UIUtils.dp2px(12));
        mSearchBox.setTextColor(UiTheme.Text.STRONG);
        mSearchBox.setHintTextColor(UiTheme.Text.HINT);
        mSearchBox.setSingleLine(true);
        mSearchBox.setGravity(Gravity.CENTER_VERTICAL);
        mSearchBox.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(SEARCH_HEIGHT_DP));
        searchLp.setMargins(0, 0, 0, UIUtils.dp2pxInt(8));
        mContentLayout.addView(mSearchBox, searchLp);

        mScrollView = new ScrollView(context);
        mListContainer = new LinearLayout(context);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        mScrollView.addView(mListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mContentLayout.addView(mScrollView);
        addView(mContentLayout);

        ((UiSearchInput) mSearchBox).setOnQueryChanged(this::performSearch);
    }

    private void renderList() {
        mListContainer.removeAllViews();
        if (mFilteredOptions.isEmpty()) {
            addEmptyItem(Component.translatable("geometry_node.data_library.no_results").getString());
            updateScrollHeight(1);
            return;
        }

        for (String item : mFilteredOptions) {
            TextView tv = new TextView(getContext());
            tv.setText(mOptionLabels.getOrDefault(item, item));

            tv.setTextSize(0, UIUtils.dp2px(12));
            tv.setTextColor(COLOR_NODE_TEXT);
            tv.setSingleLine(true);

            int padH = UIUtils.dp2pxInt(10);
            tv.setPadding(padH, 0, padH, 0);
            tv.setGravity(Gravity.CENTER_VERTICAL);

            tv.setOnClickListener(v -> { mOnSelect.accept(item); post(this::dismiss); });
            tv.setOnHoverListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    tv.setBackground(createRectDrawable(COLOR_HOVER_BG, ITEM_RADIUS_DP));
                    tv.setTextColor(UiTheme.Text.STRONG);
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    tv.setBackground(null);
                    tv.setTextColor(COLOR_NODE_TEXT);
                }
                return false;
            });

            int itemHeight = UIUtils.dp2pxInt(ITEM_HEIGHT_DP);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight);
            int marginV = UIUtils.dp2pxInt(1);
            lp.setMargins(0, marginV, 0, marginV);
            mListContainer.addView(tv, lp);
        }

        updateScrollHeight(mFilteredOptions.size());
    }

    private void addEmptyItem(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(0, UIUtils.dp2px(12));
        tv.setTextColor(COLOR_MUTED_TEXT);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(ITEM_HEIGHT_DP)
        );
        mListContainer.addView(tv, lp);
    }

    private void updateScrollHeight(int itemCount) {
        int visibleItems = Math.min(Math.max(itemCount, 1), MAX_VISIBLE_ITEMS);
        int targetHeightPx = UIUtils.dp2pxInt(visibleItems * (ITEM_HEIGHT_DP + 2));

        LinearLayout.LayoutParams svLp = (LinearLayout.LayoutParams) mScrollView.getLayoutParams();
        if (svLp.height != targetHeightPx) {
            svLp.height = targetHeightPx;
            mScrollView.setLayoutParams(svLp);
        }
    }

    private void performSearch(String query) {
        String q = query.toLowerCase(java.util.Locale.ROOT).trim();
        mFilteredOptions.clear();
        if (q.isEmpty()) {
            mFilteredOptions.addAll(mOptions);
        } else {
            for (String opt : mOptions) {
                String label = mOptionLabels.getOrDefault(opt, opt);
                if (opt.toLowerCase(java.util.Locale.ROOT).contains(q) || label.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    mFilteredOptions.add(opt);
                }
            }
        }
        renderList();
    }

    /** Shows the same searchable selector outside the graph viewport. */
    public void showAt(View anchor, ViewGroup context) {
        showAt(anchor, context, 1.0f);
    }

    public void showAt(View anchor, ViewGroup context, float anchorScale) {
        if (anchor == null || context == null) return;
        dismiss();
        setVisibility(VISIBLE);
        this.mAnchor = anchor;
        this.mContext = context;
        this.mAnchorScale = anchorScale;

        renderList();
        updatePosition();

        if (this.getParent() != null) ((ViewGroup) this.getParent()).removeView(this);

        context.addView(this);
        areaBinding = AreaTransientUi.bind(anchor, this, this::dismiss);
        mSearchBox.post(focusSearch);

        if (!mIsTracking) {
            mIsTracking = true;
            post(mTrackTask);
        }
    }

    private final Runnable mTrackTask = new Runnable() {
        @Override
        public void run() {
            if (getVisibility() != VISIBLE || getParent() == null || !anchorInHost()) {
                mIsTracking = false;
                dismiss();
                return;
            }
            updatePosition();
            postDelayed(this, 16);
        }
    };

    private void updatePosition() {
        if (mAnchor == null || mContext == null) return;

        ViewGroup parentView = mContext;

        int[] btnLoc = new int[2]; mAnchor.getLocationOnScreen(btnLoc);
        int[] vpLoc = new int[2]; parentView.getLocationOnScreen(vpLoc);

        float relX = btnLoc[0] - vpLoc[0];
        float relY = btnLoc[1] - vpLoc[1];

        int targetWidth = UIUtils.dp2pxInt(MENU_WIDTH_DP);
        float scaledHeight = mAnchor.getHeight() * mAnchorScale;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
        int oldWidth = lp.width;

        int targetX = (int) relX;
        int targetY = (int) (relY + scaledHeight);

        int widthSpec = MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        mContentLayout.measure(widthSpec, heightSpec);
        int contentHeight = mContentLayout.getMeasuredHeight();

        // 边缘溢出检测
        if (targetX + targetWidth > parentView.getWidth()) {
            targetX = Math.max(0, parentView.getWidth() - targetWidth);
        }
        if (targetY + contentHeight > parentView.getHeight()) {
            int popUpY = (int) (relY - contentHeight);
            if (popUpY > 0) {
                targetY = popUpY;
            } else {
                targetY = Math.max(0, parentView.getHeight() - contentHeight);
            }
        }

        if (lp.leftMargin != targetX || lp.topMargin != targetY || oldWidth != targetWidth
                || lp.gravity != (Gravity.TOP | Gravity.LEFT)) {
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.width = targetWidth;
            lp.leftMargin = targetX;
            lp.topMargin = targetY;
            mContentLayout.setLayoutParams(lp);
        }
    }

    public void dismiss() {
        areaBinding.close();
        areaBinding = AreaLifecycle.Subscription.NONE;
        stopTracking();
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        mAnchor = null;
        mContext = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTracking();
        super.onDetachedFromWindow();
    }

    private void stopTracking() {
        mIsTracking = false;
        removeCallbacks(mTrackTask);
        mSearchBox.removeCallbacks(focusSearch);
    }

    private boolean anchorInHost() {
        for (View current = mAnchor; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current == mContext) return true;
        }
        return false;
    }
}
