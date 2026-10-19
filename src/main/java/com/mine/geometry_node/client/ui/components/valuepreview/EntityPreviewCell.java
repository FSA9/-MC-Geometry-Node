package com.mine.geometry_node.client.ui.components.valuepreview;

import com.mine.geometry_node.client.ui.components.display.UiDisplayText;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Read-only square preview cell. Pointer selection and ordinary scrolling belong to its row. */
public final class EntityPreviewCell extends ViewGroup {
    public static final int PREVIEW_SIZE_DP = 64;
    public static final int VERTICAL_PADDING_DP = 12;
    private final EntityTemplatePreviewView preview;
    private final UiDisplayText placeholder;
    private int previewSize;

    public EntityPreviewCell(Context context) {
        super(context);
        preview = new EntityTemplatePreviewView(context, EntityTemplatePreviewView.RotationMode.FIXED);
        placeholder = new UiDisplayText(context, "", 11, UiTheme.Text.MUTED);
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
        String key = switch (status) {
            case UNAVAILABLE -> "unavailable";
            case ERROR -> "error";
            default -> null;
        };
        placeholder.setText(key == null ? ""
                : Component.translatable("geometry_node.ui.entity_preview." + key).getString());
        placeholder.setVisibility(key == null ? GONE : VISIBLE);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(px(PREVIEW_SIZE_DP + 15), widthSpec);
        int desiredSize = Math.min(px(PREVIEW_SIZE_DP), Math.max(0, width - px(15)));
        int height = resolveSize(desiredSize + px(VERTICAL_PADDING_DP * 2), heightSpec);
        previewSize = Math.min(desiredSize, Math.max(0, height - px(VERTICAL_PADDING_DP * 2)));
        int square = MeasureSpec.makeMeasureSpec(previewSize, MeasureSpec.EXACTLY);
        preview.measure(square, square);
        placeholder.measure(square, square);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = Math.min(px(9), getWidth());
        int y = (getHeight() - previewSize) / 2;
        preview.layout(x, y, x + previewSize, y + previewSize);
        placeholder.layout(x, y, x + previewSize, y + previewSize);
    }

    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-2, -2); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
