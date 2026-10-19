package com.mine.geometry_node.client.ui.components.integration.valuepreview;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.minecraft.world.item.ItemStack;

/** Read-only item appearance in a table, using the same square and row visibility policy as entity cells. */
public final class ItemPreviewCell extends ViewGroup {
    private final ItemSlotView preview;
    private ItemStack displayedStack = ItemStack.EMPTY;
    private int previewSize;
    private final int preferredSizeDp;

    public ItemPreviewCell(Context context) {
        this(context, EntityPreviewCell.PREVIEW_SIZE_DP);
    }

    /** Preferred square size for inspector hosts; table cells keep their existing 64dp default. */
    public ItemPreviewCell(Context context, int preferredSizeDp) {
        super(context);
        if (preferredSizeDp <= 0) throw new IllegalArgumentException("Preview size must be positive");
        this.preferredSizeDp = preferredSizeDp;
        preview = new ItemSlotView(context);
        preview.setVisibility(INVISIBLE);
        addView(preview, new LayoutParams(-2, -2));
    }

    /** The data row must be completely visible vertically; ordinary scrolling belongs to its container. */
    public void setVisibilityRow(View row) { preview.setFullVisibilityAnchor(row); }

    /** Equal contents retain the native surface state; changed stacks are copied to isolate caller mutation. */
    public void setStack(ItemStack stack) {
        ItemStack next = stack == null ? ItemStack.EMPTY : stack;
        if (ItemStack.matches(displayedStack, next)) return;
        displayedStack = next.copy();
        preview.setDisplayStack(displayedStack);
        preview.setVisibility(displayedStack.isEmpty() ? INVISIBLE : VISIBLE);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(px(preferredSizeDp + 15), widthSpec);
        int desiredSize = Math.min(px(preferredSizeDp), Math.max(0, width - px(15)));
        int padding = px(EntityPreviewCell.VERTICAL_PADDING_DP * 2);
        int height = resolveSize(desiredSize + padding, heightSpec);
        previewSize = Math.min(desiredSize, Math.max(0, height - padding));
        int square = MeasureSpec.makeMeasureSpec(previewSize, MeasureSpec.EXACTLY);
        preview.measure(square, square);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = Math.min(px(9), getWidth());
        int y = (getHeight() - previewSize) / 2;
        preview.layout(x, y, x + previewSize, y + previewSize);
    }

    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-2, -2); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
