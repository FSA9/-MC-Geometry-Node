package com.mine.geometry_node.client.ui.components.valuepreview;

import com.mine.geometry_node.client.ui.components.nativepreview.ViewportNativePreviewView;
import com.mine.geometry_node.client.ui.components.overlay.ItemStackTooltipOverlay;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.utils.ItemTooltipProxy;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** Reusable item-stack preview without graph-editor state or commands. */
public class ItemSlotView extends ViewportNativePreviewView {
    private static final float ITEM_SIZE_GUI = 16f;
    private static final float ITEM_PADDING_DP = 4.0f;
    private static final float ITEM_MAX_SCALE = 1.25f;

    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();

    private volatile ItemStack mDisplayStack = ItemStack.EMPTY;
    private Runnable mDisplayClickAction;
    private Consumer<String> mDisplayPasteAction;
    private boolean mEditable;
    private boolean mPressed;
    private boolean mOpenEditorOnClick = true;

    private static String sClipboardItemJson;

    public ItemSlotView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        updateFocusableState();
        setOnFocusChangeListener((view, focused) -> invalidate());
        setOnHoverListener((view, event) -> {
            handleHover(event);
            return true;
        });
    }

    @Override
    protected void renderNativePreviewContent(
            GuiGraphicsExtractor graphics,
            float deltaTick,
            float guiScale,
            float alpha,
            float windowLeftPx,
            float windowTopPx,
            float widthPx,
            float heightPx,
            float viewportScale
    ) {
        refreshDisplayValue();
        ItemStack stack = mDisplayStack;
        if (stack.isEmpty()) return;

        graphics.pose().pushMatrix();
        try {
            float targetGuiX = windowLeftPx / guiScale;
            float targetGuiY = windowTopPx / guiScale;
            graphics.pose().translateLocal(targetGuiX - graphics.pose().m20(), targetGuiY - graphics.pose().m21());

            float slotGuiW = widthPx / guiScale;
            float slotGuiH = heightPx / guiScale;
            float padding = UIUtils.dp2px(ITEM_PADDING_DP) * viewportScale / guiScale;
            float contentSize = Math.max(1.0f, Math.min(slotGuiW, slotGuiH) - padding * 2.0f);
            float itemScale = Math.min(ITEM_MAX_SCALE * viewportScale, contentSize / ITEM_SIZE_GUI);
            float drawX = (slotGuiW - ITEM_SIZE_GUI * itemScale) / 2.0f;
            float drawY = (slotGuiH - ITEM_SIZE_GUI * itemScale) / 2.0f;

            graphics.pose().translate(drawX, drawY);
            graphics.pose().scale(itemScale, itemScale);
            graphics.item(stack, 0, 0);
            graphics.itemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public final void setViewportScale(float scale) {
        updateNativePreviewScale(scale);
    }

    public final void setViewportTransform(float scale, float windowLeftPx, float windowTopPx) {
        updateNativePreviewTransform(scale, windowLeftPx, windowTopPx, getNativePreviewOrder());
    }

    public final void setViewportTransform(float scale, float windowLeftPx, float windowTopPx, long previewOrder) {
        updateNativePreviewTransform(scale, windowLeftPx, windowTopPx, previewOrder);
    }

    public void setDisplayStack(ItemStack stack) {
        ItemStack previousStack = mDisplayStack;
        mDisplayStack = stack != null ? stack.copy() : ItemStack.EMPTY;
        ItemTooltipProxy.clearTooltipTask(previousStack);
        requestNativePreviewRender();
        invalidate();
    }

    public void setDisplayClickAction(Runnable action) {
        mDisplayClickAction = action;
        if (action != null) setEditable(true);
    }

    public void setDisplayPasteAction(Consumer<String> action) {
        mDisplayPasteAction = action;
        if (action != null) setEditable(true);
    }

    public void setOpenEditorOnClick(boolean openEditorOnClick) {
        mOpenEditorOnClick = openEditorOnClick;
    }

    public void openTemplateEditor() {
        if (mDisplayClickAction != null) {
            mDisplayClickAction.run();
        } else {
            onOpenEditorRequested();
        }
    }

    protected final void setEditable(boolean editable) {
        mEditable = editable;
        updateFocusableState();
    }

    protected final ItemStack getDisplayStack() {
        return mDisplayStack;
    }

    protected final boolean dispatchDisplayPaste(String json) {
        if (mDisplayPasteAction == null) return false;
        mDisplayPasteAction.accept(json);
        return true;
    }

    protected void refreshDisplayValue() {
    }

    protected void onOpenEditorRequested() {
    }

    protected void onStackHover(MotionEvent event, ItemStack stack) {
        ItemStackTooltipOverlay.showForEvent(this, stack, event);
    }

    protected void onStackHoverExit(ItemStack stack) {
        ItemTooltipProxy.clearTooltipTask(stack);
        ItemStackTooltipOverlay.hide();
    }

    private void updateFocusableState() {
        setFocusable(mEditable);
        setFocusableInTouchMode(mEditable);
        setClickable(mEditable);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        refreshDisplayValue();

        float width = getWidth();
        float height = getHeight();
        float radius = UIUtils.dp2px(ValuePreviewStyle.CORNER_RADIUS_DP);
        float stroke = UIUtils.dp2px(ValuePreviewStyle.STROKE_WIDTH_DP);
        mPaint.setAntiAlias(true);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(ValuePreviewStyle.COLOR_BACKGROUND);
        mTempRect.set(0, 0, width, height);
        canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(mEditable && isFocused()
                ? ValuePreviewStyle.COLOR_BORDER_FOCUSED
                : ValuePreviewStyle.COLOR_BORDER);
        mTempRect.set(stroke / 2.0f, stroke / 2.0f, width - stroke / 2.0f, height - stroke / 2.0f);
        canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        handleHover(event);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        super.dispatchGenericMotionEvent(event);
        handleHover(event);
        return true;
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        refreshDisplayValue();
        ItemStack stack = mDisplayStack;
        if (stack.isEmpty()) {
            onStackHoverExit(stack);
            return PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
        }
        onStackHover(event, stack);
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
    }

    private void handleHover(MotionEvent event) {
        refreshDisplayValue();
        ItemStack stack = mDisplayStack;
        int action = event.getAction();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            if (stack.isEmpty()) {
                onStackHoverExit(stack);
                return;
            }
            onStackHover(event, stack);
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            onStackHoverExit(stack);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        onStackHoverExit(mDisplayStack);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!mEditable) return false;
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEditable) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mPressed = true;
            setPressed(true);
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean wasPressed = mPressed;
            mPressed = false;
            setPressed(false);
            if (!wasPressed) return true;
            if (!mOpenEditorOnClick || !isFocused()) {
                requestFocus();
                invalidate();
                return true;
            }
            openTemplateEditor();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            mPressed = false;
            setPressed(false);
            return true;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mEditable && isFocused()) {
            KeyBinding copy = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_COPY));
            if (copy != null && copy.matches(event)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) copyItem();
                return true;
            }
            KeyBinding paste = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_PASTE));
            if (paste != null && paste.matches(event)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) pasteItem();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void copyItem() {
        refreshDisplayValue();
        Minecraft minecraft = Minecraft.getInstance();
        if (mDisplayStack.isEmpty()) {
            sClipboardItemJson = "";
            return;
        }
        if (minecraft.level == null) return;
        sClipboardItemJson = ItemCodecUtils.toJson(mDisplayStack, minecraft.level.registryAccess());
    }

    private void pasteItem() {
        if (sClipboardItemJson == null) return;
        if (dispatchDisplayPaste(sClipboardItemJson)) {
            refreshDisplayValue();
            invalidate();
            return;
        }
        onPasteRequested(sClipboardItemJson);
        refreshDisplayValue();
        invalidate();
    }

    protected void onPasteRequested(String json) {
    }
}
