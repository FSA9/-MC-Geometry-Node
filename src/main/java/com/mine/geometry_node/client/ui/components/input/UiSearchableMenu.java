package com.mine.geometry_node.client.ui.components.input;

import com.mine.geometry_node.client.ui.components.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.menu.UiMenuFocus;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.AreaTransientUi;
import net.minecraft.network.chat.Component;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Search, selection and anchor geometry shared by graph and ordinary forms. */
public class UiSearchableMenu extends FrameLayout {
    public record Style(int widthDp, int maxVisibleItems) {
        public static final Style DEFAULT = new Style(220, 6);
        public Style {
            if (widthDp <= 0 || maxVisibleItems <= 0) throw new IllegalArgumentException("Invalid menu dimensions");
        }
    }
    private final Style style;
    private static final int MENU_PADDING_DP = 8;
    private static final int SEARCH_HEIGHT_DP = 28;
    private static final int TITLE_HEIGHT_DP = 22;
    private static final int SEARCH_RADIUS_DP = 5;
    private static final int ITEM_HEIGHT_DP = 24;
    private static final int ITEM_RADIUS_DP = 4;

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
    private final UiMenuFocus focus = new UiMenuFocus();
    private int preferredScrollHeight;
    private final Matrix anchorMatrix = new Matrix();
    private final Matrix hostMatrix = new Matrix();
    private final float[] anchorPoint = new float[2];
    private ViewTreeObserver positionObserver;
    private final ViewTreeObserver.OnPreDrawListener trackPosition = () -> {
        if (getVisibility() != VISIBLE || getParent() == null || !anchorInHost()) {
            dismiss();
        } else {
            updatePosition();
        }
        return true;
    };
    private AreaLifecycle.Subscription areaBinding = AreaLifecycle.Subscription.NONE;
    private final Runnable focusSearch = () -> {
        if (getVisibility() != VISIBLE || getParent() == null || mAnchor == null || !anchorInHost()) return;
        mSearchBox.setText("");
        mSearchBox.requestFocus();
    };

    public UiSearchableMenu(Context context, String title, List<String> options,
                              Map<String, String> optionLabels, Consumer<String> onSelect) {
        this(context, title, options, optionLabels, Style.DEFAULT, onSelect);
    }

    public UiSearchableMenu(Context context, String title, List<String> options,
                            Map<String, String> optionLabels, Style style, Consumer<String> onSelect) {
        super(context);
        this.style = java.util.Objects.requireNonNull(style);
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

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(style.widthDp()), LayoutParams.WRAP_CONTENT);
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

        mScrollView = new UiScrollView(context);
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
            // Keep one drawable: replacing null/background on every hover requests layout.
            ShapeDrawable hoverBackground = createRectDrawable(0x00000000, ITEM_RADIUS_DP);
            tv.setBackground(hoverBackground);
            tv.setOnHoverListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    hoverBackground.setColor(COLOR_HOVER_BG);
                    tv.setTextColor(UiTheme.Text.STRONG);
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    hoverBackground.setColor(0x00000000);
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
        int visibleItems = Math.min(Math.max(itemCount, 1), style.maxVisibleItems());
        int targetHeightPx = visibleItems * (UIUtils.dp2pxInt(ITEM_HEIGHT_DP) + 2 * UIUtils.dp2pxInt(1));
        if (preferredScrollHeight != targetHeightPx) {
            preferredScrollHeight = targetHeightPx;
            requestLayout();
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
        if (anchor == null || context == null) return;
        dismiss();
        setVisibility(VISIBLE);
        this.mAnchor = anchor;
        this.mContext = context;

        renderList();
        if (this.getParent() != null) ((ViewGroup) this.getParent()).removeView(this);

        focus.capture(anchor);
        context.addView(this);
        areaBinding = AreaTransientUi.bind(anchor, this, this::dismiss);
        mSearchBox.post(focusSearch);

        startTracking();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAnchor != null) startTracking();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = UIUtils.dp2pxInt(style.widthDp());
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = Math.min(width, Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
                    - getPaddingLeft() - getPaddingRight()));
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
        lp.width = width;
        // Title/search rows have fixed heights. Reserve their actual rounded dimensions
        // and margins, then give the list only the remaining host height.
        int chromeHeight = mContentLayout.getPaddingTop() + mContentLayout.getPaddingBottom();
        for (int i = 0; i < mContentLayout.getChildCount(); i++) {
            View child = mContentLayout.getChildAt(i);
            if (child == mScrollView || child.getVisibility() == GONE) continue;
            LinearLayout.LayoutParams childLp = (LinearLayout.LayoutParams) child.getLayoutParams();
            chromeHeight += childLp.height + childLp.topMargin + childLp.bottomMargin;
        }
        int availableHeight = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? chromeHeight + preferredScrollHeight
                : Math.max(0, MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom());
        LinearLayout.LayoutParams scrollLp = (LinearLayout.LayoutParams) mScrollView.getLayoutParams();
        scrollLp.height = Math.min(preferredScrollHeight, Math.max(0, availableHeight - chromeHeight));
        lp.height = Math.min(availableHeight, chromeHeight + scrollLp.height);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updatePosition();
    }

    private void startTracking() {
        if (positionObserver != null && positionObserver.isAlive()) return;
        positionObserver = getViewTreeObserver();
        positionObserver.addOnPreDrawListener(trackPosition);
    }

    private void updatePosition() {
        if (mAnchor == null || mContext == null || getWidth() <= 0) return;
        // Both corners are transformed into this overlay's local pixels. Ancestor zoom,
        // translation and scrolling are part of the matrix, not a separately sampled scale.
        anchorMatrix.setIdentity();
        mAnchor.transformMatrixToGlobal(anchorMatrix);
        anchorPoint[0] = 0;
        anchorPoint[1] = 0;
        anchorMatrix.mapPoint(anchorPoint);
        if (!UIUtils.rootToLocal(this, anchorPoint[0], anchorPoint[1], hostMatrix, anchorPoint)) return;
        float anchorLeft = anchorPoint[0], anchorTop = anchorPoint[1];
        anchorPoint[0] = 0;
        anchorPoint[1] = mAnchor.getHeight();
        anchorMatrix.mapPoint(anchorPoint);
        if (!UIUtils.rootToLocal(this, anchorPoint[0], anchorPoint[1], hostMatrix, anchorPoint)) return;
        int width = mContentLayout.getWidth(), height = mContentLayout.getHeight();
        int x = Math.round(Math.clamp(anchorLeft, 0, Math.max(0, getWidth() - width)));
        int y = Math.round(anchorPoint[1]);
        if (y + height > getHeight()) {
            y = Math.round(anchorTop - height);
        }
        y = Math.clamp(y, 0, Math.max(0, getHeight() - height));
        // Keep layout margins at zero so placement cannot change the available height.
        // Move the actual frame after layout / before drawing, without a second layout pass.
        mContentLayout.offsetLeftAndRight(x - mContentLayout.getLeft());
        mContentLayout.offsetTopAndBottom(y - mContentLayout.getTop());
    }

    public void dismiss() {
        areaBinding.close();
        areaBinding = AreaLifecycle.Subscription.NONE;
        stopTracking();
        focus.restoreAndClear(this);
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
        if (positionObserver != null) {
            ViewTreeObserver observer = positionObserver.isAlive() ? positionObserver : getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(trackPosition);
            positionObserver = null;
        }
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
