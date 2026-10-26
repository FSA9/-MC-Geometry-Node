package com.mine.geometry_node.client.ui.components.integration.entity.appearance;

import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityTemplatePreviewView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ValuePreviewStyle;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

/** One native surface, fitted against both dimensions; captions never make this pane scroll. */
final class AppearancePreviewPane extends FrameLayout {
    final EntityTemplatePreviewView preview;
    final UiDisplayText status;
    final UiSelectButton variant;
    final UiIconButton random;
    private final UiDisplayText name, id, variantLabel;
    private int square, caption, gap, contentWidth, variantArea;
    private int variantTopGap, variantLabelHeight, variantControlGap, variantControlHeight, randomGap;

    AppearancePreviewPane(Context context, UiTheme theme) {
        super(context);
        setClipChildren(true);
        preview = new EntityTemplatePreviewView(context, EntityTemplatePreviewView.RotationMode.HORIZONTAL,
                ValuePreviewStyle.forTheme(theme));
        addView(preview);
        status = label(context, theme.text().muted(), 12);
        status.setSingleLine(false);
        addView(status);
        name = label(context, theme.text().normal(), 14);
        addView(name);
        id = label(context, theme.text().muted(), 12);
        addView(id);
        variantLabel = label(context, theme.text().secondary(), 12);
        variantLabel.setText(EntityAppearancePicker.tr("variant"));
        variantLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        addView(variantLabel);
        variant = new UiSelectButton(context, "", 12, () -> 2, theme);
        variant.setContentDescription(EntityAppearancePicker.tr("variant"));
        addView(variant);
        random = UiIconButton.createThemed(context, theme, SvgIconView.Icon.SHUFFLE,
                EntityAppearancePicker.tr("random_variant"));
        addView(random);
        showVariant(null, false);
        setContentDescription(EntityAppearancePicker.tr("preview"));
    }

    private static UiDisplayText label(Context context, int color, float size) {
        UiDisplayText text = new UiDisplayText(context, "", size, color);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    void show(AppearanceSelection.Option option) {
        name.setText(option == null ? "" : option.name());
        id.setText(option == null ? "" : option.id());
        name.setTooltipText(name.getText());
        id.setTooltipText(id.getText());
    }

    /** Presentation only; the picker owns registry options, menus and draft changes. */
    void showVariant(String variantId, boolean available) {
        variant.setValue(variantId);
        variant.setTooltipText(variantId);
        int visibility = available ? View.VISIBLE : View.INVISIBLE;
        variantLabel.setVisibility(visibility);
        variant.setVisibility(visibility);
        random.setVisibility(visibility);
        variant.setEnabled(available);
        random.setEnabled(available);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
        int horizontalInset = Math.min(UIUtils.dp2pxInt(22), width / 2);
        int verticalInset = Math.min(UIUtils.dp2pxInt(22), height / 2);
        contentWidth = Math.min(UIUtils.dp2pxInt(320), Math.max(0, width - horizontalInset * 2));
        int innerHeight = Math.max(0, height - verticalInset * 2);
        // Reserve the same 78dp for every candidate, including those without variants.
        // Under a constrained host, shrink the reserved row rather than extending its hit bounds.
        variantArea = Math.min(UIUtils.dp2pxInt(78), innerHeight);
        int remainingHeight = innerHeight - variantArea;
        caption = Math.min(UIUtils.dp2pxInt(22), remainingHeight / 2);
        gap = Math.min(UIUtils.dp2pxInt(14), remainingHeight - caption * 2);
        square = Math.max(0, Math.min(UIUtils.dp2pxInt(320),
                Math.min(contentWidth, remainingHeight - caption * 2 - gap)));
        int exactSquare = MeasureSpec.makeMeasureSpec(square, MeasureSpec.EXACTLY);
        preview.measure(exactSquare, exactSquare);
        status.measure(exactSquare, exactSquare);
        int captionWidth = MeasureSpec.makeMeasureSpec(square, MeasureSpec.EXACTLY);
        int captionHeight = MeasureSpec.makeMeasureSpec(caption, MeasureSpec.EXACTLY);
        name.measure(captionWidth, captionHeight);
        id.measure(captionWidth, captionHeight);
        variantTopGap = Math.min(UIUtils.dp2pxInt(18), variantArea);
        variantLabelHeight = Math.min(UIUtils.dp2pxInt(20), variantArea - variantTopGap);
        variantControlGap = Math.min(UIUtils.dp2pxInt(8), variantArea - variantTopGap - variantLabelHeight);
        variantControlHeight = variantArea - variantTopGap - variantLabelHeight - variantControlGap;
        variantLabel.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(variantLabelHeight, MeasureSpec.EXACTLY));
        int randomSize = Math.min(contentWidth, variantControlHeight);
        randomGap = Math.min(UIUtils.dp2pxInt(8), contentWidth - randomSize);
        variant.measure(MeasureSpec.makeMeasureSpec(contentWidth - randomSize - randomGap, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(variantControlHeight, MeasureSpec.EXACTLY));
        random.measure(MeasureSpec.makeMeasureSpec(randomSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(randomSize, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = (getWidth() - square) / 2;
        int y = Math.max(0, (getHeight() - square - gap - caption * 2 - variantArea) / 2);
        preview.layout(x, y, x + square, y + square);
        status.layout(x, y, x + square, y + square);
        int captionX = (getWidth() - name.getMeasuredWidth()) / 2;
        y += square + gap;
        name.layout(captionX, y, captionX + name.getMeasuredWidth(), y + caption);
        id.layout(captionX, y + caption, captionX + id.getMeasuredWidth(), y + caption * 2);
        int controlX = (getWidth() - contentWidth) / 2;
        y += caption * 2 + variantTopGap;
        variantLabel.layout(controlX, y, controlX + contentWidth, y + variantLabelHeight);
        y += variantLabelHeight + variantControlGap;
        variant.layout(controlX, y, controlX + variant.getMeasuredWidth(), y + variantControlHeight);
        int randomX = controlX + variant.getMeasuredWidth() + randomGap;
        random.layout(randomX, y, randomX + random.getMeasuredWidth(), y + random.getMeasuredHeight());
    }
}
