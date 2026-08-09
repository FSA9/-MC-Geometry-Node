package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.utils.ItemTooltipProxy;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.ItemStackTooltipOverlay;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.VanillaInventoryPicker;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.client.ui.viewport.preview.ViewportNativePreviewView;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.utils.ItemCodecUtils;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class UIItemSlot extends ViewportNativePreviewView implements ViewportScaledHint, ViewportTransformedHint, InteractiveHintTarget {
    private static final float ITEM_SIZE_GUI = 16f;
    private static final float ITEM_PADDING_DP = 4.0f;
    private static final float ITEM_MAX_SCALE = 1.25f;

    private final NodeData mNodeData;
    private final String mPortId;
    private final EditorContext mEditorContext;
    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();

    private volatile ItemStack mCachedStack = ItemStack.EMPTY;
    private String mLastJson = null;
    private Runnable mDisplayClickAction;
    private Consumer<String> mDisplayPasteAction;
    private boolean mEditable;
    private boolean mPressed;
    private boolean mOpenEditorOnClick = true;

    private static String sClipboardItemJson;

    public UIItemSlot(Context context) {
        this(context, null, "", null);
    }

    public UIItemSlot(Context context, NodeData nodeData, String portId, EditorContext editorContext) {
        super(context);
        this.mNodeData = nodeData;
        this.mPortId = portId;
        this.mEditorContext = editorContext;
        mEditable = nodeData != null && editorContext != null;

        setWillNotDraw(false);
        setClipChildren(false);
        updateFocusableState();
        setOnFocusChangeListener((v, hasFocus) -> invalidate());
        setOnHoverListener((v, event) -> {
            handleHover(event);
            return true;
        });
        updateCache();
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
        ItemStack stack = mCachedStack;
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

    @Override
    public void setViewportScale(float scale) {
        updateNativePreviewScale(scale);
    }

    @Override
    public void setViewportTransform(float scale, float windowLeftPx, float windowTopPx) {
        updateNativePreviewTransform(scale, windowLeftPx, windowTopPx, getNativePreviewOrder());
    }

    @Override
    public void setViewportTransform(float scale, float windowLeftPx, float windowTopPx, long previewOrder) {
        updateNativePreviewTransform(scale, windowLeftPx, windowTopPx, previewOrder);
    }

    public void setDisplayStack(ItemStack stack) {
        ItemStack previousStack = mCachedStack;
        mCachedStack = stack != null ? stack.copy() : ItemStack.EMPTY;
        mLastJson = null;
        ItemTooltipProxy.clearTooltipTask(previousStack);
        requestNativePreviewRender();
        invalidate();
    }

    public void setDisplayClickAction(Runnable action) {
        mDisplayClickAction = action;
        if (action != null) {
            mEditable = true;
            updateFocusableState();
        }
    }

    public void setDisplayPasteAction(Consumer<String> action) {
        mDisplayPasteAction = action;
        if (action != null) {
            mEditable = true;
            updateFocusableState();
        }
    }

    public void setOpenEditorOnClick(boolean openEditorOnClick) {
        mOpenEditorOnClick = openEditorOnClick;
    }

    public void openTemplateEditor() {
        openEditor();
    }

    private void updateFocusableState() {
        setFocusable(mEditable);
        setFocusableInTouchMode(mEditable);
        setClickable(mEditable);
    }

    private void updateCache() {
        if (mNodeData == null) return;
        Object rawVal = mNodeData.inputs.get(mPortId);
        String json = rawVal instanceof String ? (String) rawVal : "";
        if (!json.equals(mLastJson)) {
            ItemStack previousStack = mCachedStack;
            mLastJson = json;
            if (Minecraft.getInstance().level != null) {
                mCachedStack = ItemCodecUtils.fromJson(json, Minecraft.getInstance().level.registryAccess());
            } else {
                mCachedStack = ItemStack.EMPTY;
            }
            ItemTooltipProxy.clearTooltipTask(previousStack);
            requestNativePreviewRender();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateCache();

        float w = getWidth();
        float h = getHeight();
        float radius = UIUtils.dp2px(TemplatePreviewStyle.CORNER_RADIUS_DP);
        float stroke = UIUtils.dp2px(TemplatePreviewStyle.STROKE_WIDTH_DP);
        mPaint.setAntiAlias(true);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(TemplatePreviewStyle.COLOR_BACKGROUND);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(mEditable && isFocused()
                ? TemplatePreviewStyle.COLOR_BORDER_FOCUSED
                : TemplatePreviewStyle.COLOR_BORDER);
        mTempRect.set(stroke / 2.0f, stroke / 2.0f, w - stroke / 2.0f, h - stroke / 2.0f);
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
        updateCache();
        if (mCachedStack.isEmpty()) {
            ItemStackTooltipOverlay.hide();
            return PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
        }
        ItemStackTooltipOverlay.showForEvent(this, mCachedStack, event);
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
    }

    private void handleHover(MotionEvent event) {
        updateCache();
        int action = event.getAction();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            if (mCachedStack.isEmpty()) {
                ItemStackTooltipOverlay.hide();
                return;
            }

            ItemStackTooltipOverlay.showForEvent(this, mCachedStack, event);

        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            ItemTooltipProxy.clearTooltipTask(mCachedStack);
            ItemStackTooltipOverlay.hide();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ItemTooltipProxy.clearTooltipTask(mCachedStack);
        ItemStackTooltipOverlay.hide();
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

            openEditor();
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

    private void openEditor() {
        if (mDisplayClickAction != null) {
            mDisplayClickAction.run();
            return;
        }
        openPicker();
    }

    private void copyItem() {
        updateCache();
        Minecraft minecraft = Minecraft.getInstance();
        if (mCachedStack.isEmpty()) {
            sClipboardItemJson = "";
            return;
        }
        if (minecraft.level == null) return;
        sClipboardItemJson = ItemCodecUtils.toJson(mCachedStack, minecraft.level.registryAccess());
    }

    private void pasteItem() {
        if (sClipboardItemJson == null) return;

        if (mDisplayPasteAction != null) {
            mDisplayPasteAction.accept(sClipboardItemJson);
        } else if (mNodeData != null && mEditorContext != null) {
            UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId, sClipboardItemJson);
        }
        updateCache();
        invalidate();
    }

    private void openPicker() {
        if (mNodeData == null || mEditorContext == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemTooltipProxy.clearTooltipTask();
        ItemStackTooltipOverlay.hide();

        VanillaInventoryPicker.openItem(pickedStack -> {
            if (mc.level == null) {
                return;
            }
            String newJson = ItemCodecUtils.toJson(pickedStack, mc.level.registryAccess());
            UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId, newJson);
            updateCache();
            invalidate();
            ItemTooltipProxy.clearTooltipTask();
            ItemStackTooltipOverlay.hide();
        }, () -> {
            requestFocus();
        });
    }
}
