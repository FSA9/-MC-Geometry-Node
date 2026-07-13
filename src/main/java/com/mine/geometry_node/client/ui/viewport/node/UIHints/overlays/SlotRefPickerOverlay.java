package com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays;

import com.mine.geometry_node.client.ui.utils.ItemTooltipProxy;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.SlotRef;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public final class SlotRefPickerOverlay extends FrameLayout {
    private static final int COLOR_DIM = 0x99000000;
    private static final int COLOR_WINDOW = 0xFF20242C;
    private static final int COLOR_FIELD = 0xFF12151B;
    private static final int COLOR_BORDER = 0xFF3D4654;
    private static final int COLOR_TEXT = 0xFFE8EDF6;
    private static final int COLOR_MUTED = 0xFF9EA8B8;
    private static final int COLOR_HOVER = 0xFF303947;
    private static final int COLOR_ACCENT = 0xFFE0A84E;

    private static final int SLOT_SIZE_DP = 30;
    private static final int SMALL_SLOT_SIZE_DP = 28;
    private static final int GAP_DP = 4;

    private static SlotRefPickerOverlay sOpenOverlay;

    private final Consumer<SlotRef> onPicked;
    private final Runnable onDismissed;

    private SlotRefPickerOverlay(Context context, Consumer<SlotRef> onPicked, Runnable onDismissed) {
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

        TextView title = text(context, "选择槽位", 13.0f, COLOR_TEXT);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        window.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildPlayerInventory(context, content);
        buildEquipment(context, content);
        buildIndexedGrid(context, content, "容器槽位", SlotRef.CONTAINER, 54);
        buildIndexedGrid(context, content, "实体物品能力", SlotRef.ENTITY_ITEM_HANDLER, 54);

        window.addView(scrollView, new LinearLayout.LayoutParams(dp(338), dp(420)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        addView(window, lp);
        post(this::requestFocus);
    }

    public static SlotRefPickerOverlay showFor(View anchor, Consumer<SlotRef> onPicked, Runnable onDismissed) {
        if (anchor == null) {
            return null;
        }
        ViewGroup host = findWindowHost(anchor);
        if (host == null) {
            return null;
        }
        closeOpenOverlay();
        SlotRefPickerOverlay overlay = new SlotRefPickerOverlay(anchor.getContext(), onPicked, onDismissed);
        host.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sOpenOverlay = overlay;
        overlay.requestFocus();
        return overlay;
    }

    private void buildPlayerInventory(Context context, LinearLayout content) {
        addSectionHeader(context, content, "玩家背包");

        Minecraft mc = Minecraft.getInstance();
        Inventory inventory = mc.player != null ? mc.player.getInventory() : null;

        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = gridRow(context);
            content.addView(row, rowLayout());
            for (int col = 0; col < 9; col++) {
                int mainIndex = rowIndex * 9 + col;
                int inventoryIndex = 9 + mainIndex;
                ItemStack stack = inventory != null ? inventory.getItem(inventoryIndex).copy() : ItemStack.EMPTY;
                addItemCell(context, row, stack, new SlotRef(SlotRef.PLAYER_INVENTORY, "main." + mainIndex));
            }
        }

        addSmallGap(context, content, 4);

        LinearLayout hotbar = gridRow(context);
        content.addView(hotbar, rowLayout());
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory != null ? inventory.getItem(i).copy() : ItemStack.EMPTY;
            addItemCell(context, hotbar, stack, new SlotRef(SlotRef.PLAYER_INVENTORY, "hotbar." + i));
        }
    }

    private void buildEquipment(Context context, LinearLayout content) {
        addSectionHeader(context, content, "装备栏");

        Minecraft mc = Minecraft.getInstance();
        LinearLayout row = gridRow(context);
        content.addView(row, rowLayout());

        addItemCell(context, row, mc.player != null ? mc.player.getMainHandItem().copy() : ItemStack.EMPTY,
                new SlotRef(SlotRef.EQUIPMENT, "mainhand"));
        addItemCell(context, row, mc.player != null ? mc.player.getOffhandItem().copy() : ItemStack.EMPTY,
                new SlotRef(SlotRef.EQUIPMENT, "offhand"));
        addItemCell(context, row, equipmentStack(EquipmentSlot.HEAD), new SlotRef(SlotRef.EQUIPMENT, "head"));
        addItemCell(context, row, equipmentStack(EquipmentSlot.CHEST), new SlotRef(SlotRef.EQUIPMENT, "chest"));
        addItemCell(context, row, equipmentStack(EquipmentSlot.LEGS), new SlotRef(SlotRef.EQUIPMENT, "legs"));
        addItemCell(context, row, equipmentStack(EquipmentSlot.FEET), new SlotRef(SlotRef.EQUIPMENT, "feet"));
    }

    private ItemStack equipmentStack(EquipmentSlot slot) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getItemBySlot(slot).copy() : ItemStack.EMPTY;
    }

    private void buildIndexedGrid(Context context, LinearLayout content, String title, String space, int count) {
        addSectionHeader(context, content, title);
        for (int rowIndex = 0; rowIndex < Math.ceil(count / 9.0); rowIndex++) {
            LinearLayout row = gridRow(context);
            content.addView(row, rowLayout());
            for (int col = 0; col < 9; col++) {
                int index = rowIndex * 9 + col;
                if (index >= count) {
                    addSpacerCell(context, row);
                    continue;
                }
                addIndexCell(context, row, index, new SlotRef(space, String.valueOf(index)));
            }
        }
    }

    private void addItemCell(Context context, LinearLayout row, ItemStack stack, SlotRef slotRef) {
        InventoryItemPickerOverlay.ItemStackView cell = new InventoryItemPickerOverlay.ItemStackView(context, stack, ignored -> pick(slotRef), true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(SLOT_SIZE_DP), dp(SLOT_SIZE_DP));
        lp.rightMargin = dp(GAP_DP);
        row.addView(cell, lp);
    }

    private void addIndexCell(Context context, LinearLayout row, int index, SlotRef slotRef) {
        TextView cell = text(context, String.valueOf(index + 1), 10.0f, COLOR_TEXT);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(rect(COLOR_FIELD, 3.0f, 1, COLOR_BORDER));
        cell.setOnHoverListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
                v.setBackground(rect(COLOR_HOVER, 3.0f, 1, COLOR_ACCENT));
            } else if (action == MotionEvent.ACTION_HOVER_EXIT || action == MotionEvent.ACTION_CANCEL) {
                v.setBackground(rect(COLOR_FIELD, 3.0f, 1, COLOR_BORDER));
            }
            return false;
        });
        cell.setOnClickListener(v -> pick(slotRef));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(SMALL_SLOT_SIZE_DP), dp(SMALL_SLOT_SIZE_DP));
        lp.rightMargin = dp(GAP_DP);
        row.addView(cell, lp);
    }

    private void addSpacerCell(Context context, LinearLayout row) {
        View spacer = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(SMALL_SLOT_SIZE_DP), dp(SMALL_SLOT_SIZE_DP));
        lp.rightMargin = dp(GAP_DP);
        row.addView(spacer, lp);
    }

    private void addSectionHeader(Context context, LinearLayout content, String title) {
        TextView header = text(context, title, 11.0f, COLOR_MUTED);
        header.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(8), 0, dp(2));
        content.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));
    }

    private void addSmallGap(Context context, LinearLayout content, int heightDp) {
        View spacer = new View(context);
        content.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    private LinearLayout gridRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams rowLayout() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(SLOT_SIZE_DP + GAP_DP));
        lp.bottomMargin = dp(2);
        return lp;
    }

    private void pick(SlotRef slotRef) {
        if (onPicked != null) {
            onPicked.accept(slotRef);
        }
        dismiss();
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
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        super.dispatchGenericMotionEvent(event);
        return true;
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

    private static TextView text(Context context, String value, float size, int color) {
        TextView textView = UIUtils.createLockedTextView(context, value, size, color);
        textView.setSingleLine(true);
        return textView;
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
