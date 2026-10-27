package com.mine.geometry_node.client.ui.components.composite.menu;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.components.base.menu.UiPopupOverlay;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
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
public class UiSearchableMenu extends UiPopupOverlay {
    public record Style(int widthDp, int maxVisibleItems) {
        public static final Style DEFAULT = new Style(220, 6);
        public Style {
            if (widthDp <= 0 || maxVisibleItems <= 0) throw new IllegalArgumentException("Invalid menu dimensions");
        }
    }
    private final Style style;
    private static final int MENU_PADDING_DP = 8;
    private static final int ITEM_HEIGHT_DP = 24;

    private final UiTheme theme;

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
    private UiSearchInput mSearchBox;
    private ScrollView mScrollView;
    private final String mTitle;
    private final List<String> mOptions;
    private final List<String> mFilteredOptions;
    private final Map<String, String> mOptionLabels;
    private final Consumer<String> mOnSelect;

    private View mAnchor;
    private long presentation;

    private ViewGroup mContext;
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
        this(context, title, options, optionLabels, style, onSelect, UiThemes.EDITOR);
    }

    public UiSearchableMenu(Context context, String title, List<String> options,
                            Map<String, String> optionLabels, Style style, Consumer<String> onSelect, UiTheme theme) {
        super(context);
        this.theme = java.util.Objects.requireNonNull(theme, "theme");
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
        mContentLayout.setBackground(createRectDrawable(theme.surface().panel(), 6, 1, theme.border().strong()));
        mContentLayout.setPadding(
                UIUtils.dp2pxInt(MENU_PADDING_DP),
                UIUtils.dp2pxInt(MENU_PADDING_DP),
                UIUtils.dp2pxInt(MENU_PADDING_DP),
                UIUtils.dp2pxInt(MENU_PADDING_DP)
        );
        mContentLayout.setOnClickListener(v -> {});

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(style.widthDp()), LayoutParams.WRAP_CONTENT);
        mContentLayout.setLayoutParams(lp);

        mSearchBox = UiSearchMenuLayout.addHeader(mContentLayout, mTitle, true, theme);

        mScrollView = new UiScrollView(context, theme.scroll());
        mListContainer = new LinearLayout(context);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        mScrollView.addView(mListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mContentLayout.addView(mScrollView);
        addView(mContentLayout);

        mSearchBox.setOnQueryChanged(this::performSearch);
    }

    private void renderList() {
        mListContainer.removeAllViews();

        for (String item : mFilteredOptions) {
            TextView tv = UiMenuItems.row(getContext(), mOptionLabels.getOrDefault(item, item),
                    false, true, () -> {
                        if (!isOpen()) return;
                        long selectedPresentation = presentation;
                        mOnSelect.accept(item);
                        post(() -> { if (presentation == selectedPresentation) dismiss(); });
                    }, theme);

            int itemHeight = UIUtils.dp2pxInt(ITEM_HEIGHT_DP);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight);
            int marginV = UIUtils.dp2pxInt(1);
            lp.setMargins(0, marginV, 0, marginV);
            mListContainer.addView(tv, lp);
        }

        updateScrollHeight(mFilteredOptions.size());
    }

    private void updateScrollHeight(int itemCount) {
        int visibleItems = Math.min(Math.max(itemCount, 0), style.maxVisibleItems());
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
        if (getParent() != null || context.isInLayout()) return;
        this.mAnchor = anchor;
        this.mContext = context;

        renderList();
        if (!attachOverlay(anchor, context)) {
            mAnchor = null;
            mContext = null;
            return;
        }
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

    @Override protected void onDismissRequested() {
        presentation++;
        stopTracking();
        mAnchor = null;
        mContext = null;
    }

    @Override protected boolean containsPopup(float x, float y) {
        return x >= mContentLayout.getLeft() && x < mContentLayout.getRight()
                && y >= mContentLayout.getTop() && y < mContentLayout.getBottom();
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
