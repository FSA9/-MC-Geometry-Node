package com.mine.geometry_node.client.ui.components.integration.valuepreview;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import java.util.UUID;

/** Read-only square preview cell. Pointer selection and ordinary scrolling belong to its row. */
public final class EntityPreviewCell extends ViewGroup {
    public enum Layout { TABLE, SQUARE }
    public static final int PREVIEW_SIZE_DP = 64;
    public static final int VERTICAL_PADDING_DP = 12;
    private final EntityTemplatePreviewView preview;
    private final UiDisplayText placeholder;
    private final int preferredSizeDp;
    private final Layout layout;
    private int previewSize;

    public EntityPreviewCell(Context context) {
        this(context, PREVIEW_SIZE_DP);
    }

    /** Preferred square size for non-table hosts; ordinary table cells keep their 64dp default. */
    public EntityPreviewCell(Context context, int preferredSizeDp) {
        this(context, preferredSizeDp, UiThemes.EDITOR);
    }

    public EntityPreviewCell(Context context, int preferredSizeDp, UiTheme theme) {
        this(context, preferredSizeDp, theme, Layout.TABLE);
    }

    /** Standalone squares fill their bounds; table cells retain row padding and alignment. */
    public EntityPreviewCell(Context context, int preferredSizeDp, UiTheme theme, Layout layout) {
        super(context);
        this.layout = java.util.Objects.requireNonNull(layout, "layout");
        if (preferredSizeDp <= 0) throw new IllegalArgumentException("Preview size must be positive");
        this.preferredSizeDp = preferredSizeDp;
        preview = new EntityTemplatePreviewView(context, EntityTemplatePreviewView.RotationMode.FIXED,
                ValuePreviewStyle.forTheme(theme));
        placeholder = new UiDisplayText(context, "", 11, theme.text().muted());
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(px(3), 0, px(3), 0);
        addView(preview, new LayoutParams(-2, -2));
        addView(placeholder, new LayoutParams(-2, -2));
        preview.setOnPreviewStatusChanged(this::showStatus);
    }

    /** The data row (not its scroll container) must be completely visible vertically. */
    public void setVisibilityRow(View row) { preview.setFullVisibilityAnchor(row); }

    public void setTemplate(EntityTemplateValue value) {
        preview.setDisplayTemplate(value);
        showStatus(preview.getPreviewStatus());
    }

    public void setReference(UUID entityId, String dimension) {
        preview.setDisplayReference(entityId, dimension);
        showStatus(preview.getPreviewStatus());
    }

    private void showStatus(EntityTemplatePreviewView.Status status) {
        String text = status.placeholderText();
        placeholder.setText(text);
        placeholder.setVisibility(text.isEmpty() ? GONE : VISIBLE);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (layout == Layout.SQUARE) {
            int side = resolveSize(px(preferredSizeDp), widthSpec);
            if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
                side = Math.min(side, MeasureSpec.getSize(heightSpec));
            previewSize = side;
            int square = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
            preview.measure(square, square);
            placeholder.measure(square, square);
            setMeasuredDimension(side, side);
            return;
        }
        int width = resolveSize(px(preferredSizeDp + 15), widthSpec);
        int desiredSize = Math.min(px(preferredSizeDp), Math.max(0, width - px(15)));
        int height = resolveSize(desiredSize + px(VERTICAL_PADDING_DP * 2), heightSpec);
        previewSize = Math.min(desiredSize, Math.max(0, height - px(VERTICAL_PADDING_DP * 2)));
        int square = MeasureSpec.makeMeasureSpec(previewSize, MeasureSpec.EXACTLY);
        preview.measure(square, square);
        placeholder.measure(square, square);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = layout == Layout.SQUARE ? 0 : Math.min(px(9), getWidth());
        int y = (getHeight() - previewSize) / 2;
        preview.layout(x, y, x + previewSize, y + previewSize);
        placeholder.layout(x, y, x + previewSize, y + previewSize);
    }

    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-2, -2); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
