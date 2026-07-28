package com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays;

import com.mine.geometry_node.client.ui.utils.ItemTooltipProxy;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class InventoryItemPickerOverlay extends FrameLayout {
    private static final int COLOR_DIM = 0x99000000;
    private static final int COLOR_WINDOW = 0xFF20242C;
    private static final int COLOR_FIELD = 0xFF12151B;
    private static final int COLOR_BORDER = 0xFF3D4654;
    private static final int COLOR_TEXT = 0xFFE8EDF6;
    private static final int COLOR_ACCENT = 0xFFE0A84E;

    private static final int COLUMNS = 9;
    private static final int ROWS = 4;
    private static final int SLOT_SIZE_DP = 30;

    private static InventoryItemPickerOverlay sOpenOverlay;

    private final Consumer<ItemStack> onPicked;
    private final Runnable onDismissed;
    private final List<ItemStackView> itemSlots = new ArrayList<>();
    private ItemStackView hoveredSlot;

    private InventoryItemPickerOverlay(Context context, Consumer<ItemStack> onPicked, Runnable onDismissed) {
        super(context);
        this.onPicked = onPicked;
        this.onDismissed = onDismissed;

        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackground(rect(COLOR_DIM, 0.0f, 0, 0));
        setOnClickListener(v -> dismiss());

        LinearLayout window = new LinearLayout(context);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setPadding(dp(12), dp(10), dp(12), dp(12));
        window.setBackground(rect(COLOR_WINDOW, 5.0f, 1, COLOR_BORDER));
        window.setOnClickListener(v -> {
        });
        window.setOnHoverListener((v, event) -> {
            updateTooltipForPointer(event);
            return false;
        });

        TextView title = UIUtils.createLockedTextView(context, "选择物品", 13.0f, COLOR_TEXT);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        window.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(4), 0, 0);
        grid.setOnHoverListener((v, event) -> {
            updateTooltipForPointer(event);
            return false;
        });
        buildInventoryGrid(context, grid);
        window.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        addView(window, lp);

        post(this::requestFocus);
    }

    public static InventoryItemPickerOverlay showIn(ViewGroup parent, Consumer<ItemStack> onPicked, Runnable onDismissed) {
        if (parent == null) {
            return null;
        }
        closeOpenOverlay();
        InventoryItemPickerOverlay overlay = new InventoryItemPickerOverlay(parent.getContext(), onPicked, onDismissed);
        parent.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sOpenOverlay = overlay;
        overlay.requestFocus();
        return overlay;
    }

    public static InventoryItemPickerOverlay showFor(View anchor, Consumer<ItemStack> onPicked, Runnable onDismissed) {
        if (anchor == null) {
            return null;
        }
        ViewGroup host = findWindowHost(anchor);
        return showIn(host, onPicked, onDismissed);
    }

    public static boolean hasVisibleOverlay() {
        return sOpenOverlay != null
                && sOpenOverlay.getParent() != null
                && sOpenOverlay.getVisibility() == View.VISIBLE;
    }

    private void buildInventoryGrid(Context context, LinearLayout grid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inventory = mc.player.getInventory();
        for (int rowIndex = 0; rowIndex < ROWS; rowIndex++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setOnHoverListener((v, event) -> {
                updateTooltipForPointer(event);
                return false;
            });
            grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(SLOT_SIZE_DP + 4)));

            for (int col = 0; col < COLUMNS; col++) {
                int inventoryIndex = rowIndex == ROWS - 1
                        ? col
                        : 9 + rowIndex * COLUMNS + col;
                ItemStack stack = inventory.getItem(inventoryIndex).copy();
                ItemStackView slot = new ItemStackView(context, stack, picked -> {
                    if (onPicked != null) {
                        onPicked.accept(picked.copy());
                    }
                    dismiss();
                }, true);
                itemSlots.add(slot);
                LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(dp(SLOT_SIZE_DP), dp(SLOT_SIZE_DP));
                slotLp.rightMargin = dp(4);
                row.addView(slot, slotLp);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
            dismiss();
            return true;
        }
        super.dispatchKeyEvent(event);
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        super.dispatchTouchEvent(event);
        updateTooltipForPointer(event);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        super.dispatchGenericMotionEvent(event);
        updateTooltipForPointer(event);
        return true;
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        ItemStackView slot = updateTooltipForPointerLocation(event);
        if (slot != null && !slot.getStack().isEmpty()) {
            return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
        }
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
    }

    public void dismiss() {
        ItemTooltipProxy.clearTooltipTask();
        ItemStackTooltipOverlay.hide();
        if (sOpenOverlay == this) {
            sOpenOverlay = null;
        }
        if (getParent() instanceof ViewGroup parent) {
            parent.removeView(this);
        }
        if (onDismissed != null) {
            onDismissed.run();
        }
    }

    private void updateTooltipForPointer(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_EXIT || action == MotionEvent.ACTION_CANCEL) {
            clearHoveredSlot();
            return;
        }
        if (action != MotionEvent.ACTION_HOVER_ENTER
                && action != MotionEvent.ACTION_HOVER_MOVE
                && action != MotionEvent.ACTION_MOVE
                && action != MotionEvent.ACTION_DOWN) {
            return;
        }

        updateTooltipForRawPoint(event.getRawX(), event.getRawY());
    }

    private ItemStackView updateTooltipForPointerLocation(MotionEvent event) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float rawX = loc[0] + event.getX();
        float rawY = loc[1] + event.getY();
        ItemStackView slot = updateTooltipForRawPoint(rawX, rawY);
        if (slot != null) {
            return slot;
        }
        return updateTooltipForRawPoint(event.getRawX(), event.getRawY());
    }

    private ItemStackView updateTooltipForRawPoint(float rawX, float rawY) {
        ItemStackView slot = findSlotAtRaw(rawX, rawY);
        if (slot == null || slot.getStack().isEmpty()) {
            clearHoveredSlot();
            return null;
        }

        hoveredSlot = slot;
        ItemStackTooltipOverlay.show(this, slot.getStack(), rawX, rawY);
        return slot;
    }

    private ItemStackView findSlotAtRaw(float rawX, float rawY) {
        int[] loc = new int[2];
        for (ItemStackView slot : itemSlots) {
            if (slot.getVisibility() != View.VISIBLE || slot.getWidth() <= 0 || slot.getHeight() <= 0) {
                continue;
            }
            slot.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX < loc[0] + slot.getWidth()
                    && rawY >= loc[1] && rawY < loc[1] + slot.getHeight()) {
                return slot;
            }
        }
        return null;
    }

    private void clearHoveredSlot() {
        if (hoveredSlot != null) {
            hoveredSlot = null;
        }
        ItemStackTooltipOverlay.hide();
        ItemTooltipProxy.clearTooltipTask();
    }

    private static void closeOpenOverlay() {
        if (sOpenOverlay != null) {
            sOpenOverlay.dismiss();
        }
    }

    private static ViewGroup findWindowHost(View anchor) {
        View current = anchor;
        ViewGroup best = anchor instanceof ViewGroup viewGroup ? viewGroup : null;
        while (current != null) {
            if (current instanceof FrameLayout frameLayout) {
                best = frameLayout;
            }
            if (!(current.getParent() instanceof View parentView)) {
                break;
            }
            current = parentView;
        }
        return best != null ? best : anchor.getParent() instanceof ViewGroup parent ? parent : null;
    }

    public static final class ItemStackView extends FrameLayout {
        private static final float ITEM_SIZE_GUI = 16.0f;
        private static final float ITEM_PADDING_DP = 4.0f;

        private final Paint paint = new Paint();
        private final RectF rect = new RectF();
        private final Consumer<ItemStack> onPicked;
        private final boolean pickOnClick;
        private final float visualScale;
        private MinecraftSurfaceView surfaceView;
        private ItemStack stack;

        public ItemStackView(Context context, ItemStack stack, Consumer<ItemStack> onPicked, boolean pickOnClick) {
            this(context, stack, onPicked, pickOnClick, 1.0f);
        }

        public ItemStackView(Context context,
                             ItemStack stack,
                             Consumer<ItemStack> onPicked,
                             boolean pickOnClick,
                             float visualScale) {
            super(context);
            this.stack = stack == null ? ItemStack.EMPTY : stack;
            this.onPicked = onPicked;
            this.pickOnClick = pickOnClick;
            this.visualScale = Math.max(0.1f, visualScale);
            setWillNotDraw(false);
            setClipChildren(false);
            setFocusable(false);
            setClickable(true);
            setOnHoverListener((v, event) -> {
                handleHover(event);
                return true;
            });

            surfaceView = new MinecraftSurfaceView(context);
            surfaceView.setEnabled(false);
            surfaceView.setClickable(false);
            surfaceView.setFocusable(false);
            surfaceView.setRenderer(new MinecraftSurfaceView.Renderer() {
                @Override
                public void onSurfaceChanged(int width, int height) {
                }

                @Override
                public void onDraw(@Nonnull GuiGraphicsExtractor gr, int mouseX, int mouseY, float deltaTick, double guiScale, float alpha) {
                    if (ItemStackView.this.stack.isEmpty()) {
                        return;
                    }
                    drawStack(gr, guiScale);
                }
            });
            addView(surfaceView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        public void setStack(ItemStack stack) {
            ItemTooltipProxy.clearTooltipTask();
            ItemStackTooltipOverlay.hide();
            this.stack = stack == null ? ItemStack.EMPTY : stack;
            if (surfaceView != null) {
                surfaceView.invalidate();
            }
            invalidate();
        }

        public ItemStack getStack() {
            return stack;
        }

        private void drawStack(GuiGraphicsExtractor gr, double guiScale) {
            gr.pose().pushMatrix();
            float safeGuiScale = guiScale > 0.0 ? (float) guiScale : 1.0f;
            float slotGuiW = getWidth() / safeGuiScale;
            float slotGuiH = getHeight() / safeGuiScale;
            float padding = UIUtils.dp2px(ITEM_PADDING_DP * visualScale) / safeGuiScale;
            float contentSize = Math.max(1.0f, Math.min(slotGuiW, slotGuiH) - padding * 2.0f);
            float itemScale = contentSize / ITEM_SIZE_GUI;
            float drawX = (slotGuiW - ITEM_SIZE_GUI * itemScale) / 2.0f;
            float drawY = (slotGuiH - ITEM_SIZE_GUI * itemScale) / 2.0f;
            gr.pose().translate(drawX, drawY);
            gr.pose().scale(itemScale, itemScale);
            gr.item(stack, 0, 0);
            gr.itemDecorations(Minecraft.getInstance().font, stack, 0, 0);
            gr.pose().popMatrix();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float stroke = UIUtils.dp2px(visualScale);
            float radius = UIUtils.dp2px(3.0f * visualScale);
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_FIELD);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, radius, radius, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(stack.isEmpty() ? 0xFF4E5664 : COLOR_ACCENT);
            rect.set(stroke / 2.0f, stroke / 2.0f, w - stroke / 2.0f, h - stroke / 2.0f);
            canvas.drawRoundRect(rect, radius, radius, radius, radius, paint);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            handleHover(event);
            super.dispatchGenericMotionEvent(event);
            return true;
        }

        @Override
        public boolean onHoverEvent(MotionEvent event) {
            handleHover(event);
            return true;
        }

        @Override
        public PointerIcon onResolvePointerIcon(MotionEvent event) {
            return stack.isEmpty()
                    ? PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT)
                    : PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
        }

        private void handleHover(MotionEvent event) {
            int action = event.getAction();
            if ((action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) && !stack.isEmpty()) {
                ItemStackTooltipOverlay.showForEvent(this, stack, event);
            } else if (action == MotionEvent.ACTION_HOVER_EXIT || action == MotionEvent.ACTION_CANCEL) {
                ItemTooltipProxy.clearTooltipTask();
                ItemStackTooltipOverlay.hide();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            ItemTooltipProxy.clearTooltipTask();
            ItemStackTooltipOverlay.hide();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return onTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && pickOnClick) {
                if (onPicked != null) {
                    onPicked.accept(stack.copy());
                }
                return true;
            }
            return true;
        }
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private static int dp(int value) {
        return UIUtils.dp2pxInt(value);
    }
}
