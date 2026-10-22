package com.mine.geometry_node.client.ui.components.integration.equipment;

import com.mine.geometry_node.client.ui.components.integration.valuepreview.ItemSlotView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ValuePreviewStyle;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/** Native item appearance with transactional slot input; never opens an item editor or picker. */
final class EquipmentItemSlotView extends ItemSlotView {
    private final UiTheme theme;
    interface Input {
        boolean pointer(EntityEquipmentDraft.Slot slot, MotionEvent event);
        boolean key(EntityEquipmentDraft.Slot slot, KeyEvent event);
    }
    private final Input input;
    private final BooleanSupplier carrying;
    private final Paint paint = new Paint();
    private final EntityEquipmentDraft.Slot slot;
    private int targetColor;

    EquipmentItemSlotView(Context context, UiTheme theme, EntityEquipmentDraft.Slot slot,
                          Input input, BooleanSupplier carrying) {
        super(context, ValuePreviewStyle.forTheme(theme).withColors(theme.control().input(),
                theme.border().strong(), theme.border().focus()), theme);
        this.theme = theme;
        this.slot = slot;
        this.input = input;
        this.carrying = carrying;
        setEditable(true);
        setOpenEditorOnClick(false);
    }

    void bind(ItemStack stack, ItemStack cursor) {
        if (!ItemStack.matches(getDisplayStack(), stack)) setDisplayStack(stack);
        targetColor = cursor.isEmpty() ? theme.border().strong()
                : EntityEquipmentDraft.accepts(slot, cursor) ? theme.status().success() : theme.status().error();
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        return input.pointer(slot, event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // A slot belongs to the inventory input context, not ItemSlotView's picker/clipboard context.
        return isEnabled() && isFocused() && input.key(slot, event);
    }

    @Override protected void onStackHover(MotionEvent event, ItemStack stack) {
        if (!carrying.getAsBoolean()) super.onStackHover(event, stack);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = UIUtils.dp2px(1);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(inset);
        paint.setColor(!isEnabled() ? theme.text().disabled()
                : isFocused() ? theme.border().focus() : isHovered() ? theme.selection().outline() : targetColor);
        canvas.drawRect(inset, inset, getWidth() - inset, getHeight() - inset, paint);
    }
}
