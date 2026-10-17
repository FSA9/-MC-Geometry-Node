package com.mine.geometry_node.client.ui.components.menu.grid;

import com.mine.geometry_node.client.ui.components.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Searchable cards with square previews. Preview factories must return a fresh, passive view. */
public final class UiGridMenu<T> {
    public record Style(int cardWidthDp, int columns, int gapDp, int captionHeightDp, int maxHeightDp) {
        public static final Style DEFAULT = new Style(80, 3, 6, 24, 360);
        public Style {
            if (cardWidthDp <= 0 || columns <= 0 || gapDp < 0 || captionHeightDp <= 0 || maxHeightDp < 0)
                throw new IllegalArgumentException("Invalid grid menu dimensions");
        }
        public int contentWidthPx() {
            return columns * UIUtils.dp2pxInt(cardWidthDp) + (columns - 1) * UIUtils.dp2pxInt(gapDp);
        }
    }
    /** Null/blank captions omit the caption region entirely; search text is independent. */
    public record Entry<T>(T value, String caption, String searchText,
                           Function<Context, View> preview, boolean enabled) {
        public Entry { Objects.requireNonNull(preview); }
    }

    private final UiPopover popup;

    public UiGridMenu(Context context, String title, List<Entry<T>> entries, T selected,
                      Consumer<T> onSelect) {
        this(context, title, entries, selected, Style.DEFAULT, onSelect);
    }

    public UiGridMenu(Context context, String title, List<Entry<T>> entries, T selected,
                      Style style, Consumer<T> onSelect) {
        List<Entry<T>> source = List.copyOf(entries);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        if (title != null && !title.isBlank()) {
            content.addView(label(context, title), new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(24)));
        }
        UiSearchInput search = new UiSearchInput(context);
        search.setHint(Component.translatable("geometry_node.asset_library.toolbar.search").getString());
        content.addView(search, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(28)));
        CardGrid grid = new CardGrid(context, style);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, -2);
        gridParams.topMargin = UIUtils.dp2pxInt(style.gapDp());
        content.addView(grid, gridParams);
        TextView empty = label(context, Component.translatable("geometry_node.data_library.no_results").getString());
        content.addView(empty, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(28)));
        popup = UiPopover.forContentWidth(context, content, style.contentWidthPx(), style.maxHeightDp());
        Consumer<String> filter = query -> {
            grid.removeAllViews();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            for (Entry<T> entry : source) {
                String text = Objects.toString(entry.searchText(), "") + " " + Objects.toString(entry.caption(), "");
                if (!text.toLowerCase(Locale.ROOT).contains(needle)) continue;
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                boolean active = Objects.equals(selected, entry.value());
                int base = active ? UiTheme.Control.SELECTED : UiTheme.Control.INPUT;
                ShapeDrawable background = new ShapeDrawable();
                background.setColor(base);
                card.setBackground(background);
                card.setEnabled(entry.enabled());
                card.setAlpha(entry.enabled() ? 1f : 0.45f);
                card.setOnHoverListener((v, event) -> {
                    background.setColor(entry.enabled() && event.getActionMasked() != MotionEvent.ACTION_HOVER_EXIT
                            ? UiTheme.Control.ICON_HOVER : base);
                    return false;
                });
                FrameLayout preview = new FrameLayout(context);
                preview.addView(entry.preview().apply(context), new FrameLayout.LayoutParams(-1, -1));
                card.addView(preview, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(style.cardWidthDp())));
                if (entry.caption() != null && !entry.caption().isBlank()) {
                    TextView caption = label(context, entry.caption());
                    caption.setSingleLine(true);
                    caption.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(caption, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(style.captionHeightDp())));
                }
                card.setOnClickListener(v -> { popup.dismiss(); onSelect.accept(entry.value()); });
                grid.addView(card, new ViewGroup.LayoutParams(-2, -2));
            }
            empty.setVisibility(grid.getChildCount() == 0 ? View.VISIBLE : View.GONE);
            popup.refreshSize();
        };
        search.setOnQueryChanged(filter);
        filter.accept("");
    }

    public void showBelow(View anchor, FrameLayout host) { popup.showBelow(anchor, host); }
    public void showAtRoot(View owner, FrameLayout host, float x, float y) { popup.showAtRoot(owner, host, x, y); }
    public void dismiss() { popup.dismiss(); }
    public boolean isOpen() { return popup.isOpen(); }

    private static TextView label(Context context, String text) {
        TextView view = UIUtils.createLockedTextView(context, text, 11, UiTheme.Text.NORMAL);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    /** Fixed-width cards wrap into fewer columns when the host cannot fit the preferred width. */
    private static final class CardGrid extends ViewGroup {
        private final Style style;
        private int columns = 1;
        private int rowHeight;
        private int cardWidth;
        CardGrid(Context context, Style style) { super(context); this.style = style; }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int gap = UIUtils.dp2pxInt(style.gapDp());
            cardWidth = Math.min(UIUtils.dp2pxInt(style.cardWidthDp()), width);
            columns = Math.max(1, Math.min(style.columns(), (width + gap) / Math.max(1, cardWidth + gap)));
            rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                LinearLayout card = (LinearLayout) getChildAt(i);
                card.getChildAt(0).getLayoutParams().height = cardWidth;
                card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowHeight = Math.max(rowHeight, card.getMeasuredHeight());
            }
            int rows = (getChildCount() + columns - 1) / columns;
            setMeasuredDimension(width, rows * rowHeight + Math.max(0, rows - 1) * gap);
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int gap = UIUtils.dp2pxInt(style.gapDp());
            int gridWidth = columns * cardWidth + (columns - 1) * gap;
            int inset = Math.max(0, (r - l - gridWidth) / 2);
            for (int i = 0; i < getChildCount(); i++) {
                View card = getChildAt(i);
                int x = inset + (i % columns) * (cardWidth + gap), y = (i / columns) * (rowHeight + gap);
                card.layout(x, y, x + cardWidth, y + card.getMeasuredHeight());
            }
        }
    }
}
