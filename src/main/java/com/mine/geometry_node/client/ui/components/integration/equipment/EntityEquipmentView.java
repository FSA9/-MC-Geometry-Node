package com.mine.geometry_node.client.ui.components.integration.equipment;

import com.mine.geometry_node.client.ui.components.integration.overlay.ItemStackTooltipOverlay;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityTemplatePreviewView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ItemSlotView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.PreviewZoomController;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ValuePreviewStyle;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Reusable equipment editor backed exclusively by detached authoritative snapshot stacks.
 * Call {@link #returnCarried()} before leaving/applying; on false retain this tab and draft.
 * All methods and the changed callback belong to the UI thread. Preview sources can arrive later.
 * Host commits copies from equipment()/inventory()/dropped() through its own transaction.
 * Creative cloning defaults off; only enable it from an authoritative capability snapshot. */
public final class EntityEquipmentView extends FrameLayout {
    private static final String PREFIX = "geometry_node.ui.equipment.";
    private final UiTheme theme;
    private final EntityEquipmentDraft draft;
    private final Runnable changed;
    private Runnable operationFinished = () -> {};
    private final Map<EntityEquipmentDraft.Slot, EquipmentItemSlotView> slots = new LinkedHashMap<>();
    private final EquipmentEntityPreviewView preview;
    private final TextView previewStatus;
    private final LinearLayout panels;
    private final LinearLayout workbench;
    private final LinearLayout playerInventory;
    private final ItemSlotView cursor;
    private final TextView error;
    private final EquipmentInventoryInteraction interaction;
    private final Matrix pointerTransform = new Matrix();
    private final float[] pointerPosition = new float[2];
    private final Rect visibleBounds = new Rect();
    private Bindings bindings = Bindings.minecraft();
    private EntityEquipmentDraft.Slot hoveredSlot;
    private boolean pointerCaptured;
    private boolean inputBlocked;
    private boolean consumedEscape;
    private int pressedButton = -1;
    private Map<String, ItemStack> previewEquipment;
    private ItemStack cursorStack = ItemStack.EMPTY;
    private boolean pointerInside;
    private float pointerX;
    private float pointerY;

    /** Equipment keys are authoritative supported slots; include EMPTY for supported empty slots.
     * Missing keys stay disabled, and an empty map enables no entity equipment slots. */
    public EntityEquipmentView(Context context, UiTheme theme, Map<String, ItemStack> equipment,
                               List<ItemStack> inventory, Runnable changed) {
        super(context);
        this.theme = Objects.requireNonNull(theme, "theme");
        this.draft = new EntityEquipmentDraft(equipment, inventory);
        this.changed = Objects.requireNonNull(changed, "changed");
        this.interaction = new EquipmentInventoryInteraction(draft, this::completed, this::refresh);
        setBackground(background(theme.surface().panel(), theme.border().subtle(), false));

        UiScrollView scroll = new UiScrollView(context, theme.scroll());
        scroll.setFillViewport(true);
        scroll.setBackground(background(theme.surface().panel(), theme.border().subtle(), false));
        panels = column();
        scroll.addView(panels, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        workbench = column();
        workbench.setPadding(px(24), px(20), px(24), px(20));
        workbench.addView(text("title", 14, theme.text().strong()), wrap());

        LinearLayout outfit = new LinearLayout(context);
        outfit.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout armor = column();
        for (String name : List.of("head", "chest", "legs", "feet")) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(56), -2);
            params.bottomMargin = px(8);
            armor.addView(equipmentSlot(name), params);
        }
        outfit.addView(armor, new LinearLayout.LayoutParams(px(56), -2));
        LinearLayout previewColumn = column();
        previewColumn.setGravity(Gravity.CENTER);
        preview = new EquipmentEntityPreviewView(context, theme);
        preview.setFullVisibilityAnchor(preview);
        preview.zoom().setWheelMode(PreviewZoomController.WheelMode.DISABLED);
        FrameLayout previewFrame = new FrameLayout(context);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        previewStatus = UIUtils.createLockedTextView(context, "", 11, theme.text().muted());
        previewStatus.setTextColor(theme.text().muted());
        previewStatus.setGravity(Gravity.CENTER);
        previewStatus.setEnabled(false);
        previewFrame.addView(previewStatus, new FrameLayout.LayoutParams(-1, -1));
        preview.setOnPreviewStatusChanged(this::showPreviewStatus);
        previewColumn.addView(previewFrame, new LinearLayout.LayoutParams(-1, px(286)));
        TextView caption = text("preview", 10, theme.text().muted());
        caption.setGravity(Gravity.CENTER);
        previewColumn.addView(caption, new LinearLayout.LayoutParams(-1, px(24)));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(0, -2, 1);
        previewParams.leftMargin = px(16);
        outfit.addView(previewColumn, previewParams);
        LinearLayout.LayoutParams outfitParams = new LinearLayout.LayoutParams(-1, -2);
        outfitParams.topMargin = px(18);
        workbench.addView(outfit, outfitParams);

        LinearLayout hands = new LinearLayout(context);
        hands.setGravity(Gravity.CENTER);
        for (String name : List.of("mainhand", "offhand")) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(64), -2);
            params.leftMargin = px(10);
            params.rightMargin = px(10);
            hands.addView(equipmentSlot(name), params);
        }
        workbench.addView(hands, new LinearLayout.LayoutParams(-1, -2));

        playerInventory = column();
        playerInventory.setPadding(px(24), px(20), px(24), px(20));
        playerInventory.setBackground(background(theme.surface().sidebar(), theme.border().divider(), true));
        playerInventory.addView(text("player_inventory", 14, theme.text().strong()), wrap());
        addText(playerInventory, "main_inventory", 10, theme.text().muted(), 22);
        InventoryGrid main = new InventoryGrid(context, 9, 27);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, -2);
        gridParams.topMargin = px(8);
        playerInventory.addView(main, gridParams);
        addText(playerInventory, "hotbar", 10, theme.text().muted(), 16);
        divider(playerInventory, 8);
        InventoryGrid hotbar = new InventoryGrid(context, 0, 9);
        LinearLayout.LayoutParams hotbarParams = new LinearLayout.LayoutParams(-1, -2);
        hotbarParams.topMargin = px(10);
        playerInventory.addView(hotbar, hotbarParams);

        error = text("", 11, theme.status().error());
        error.setText("");
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(-1, -2);
        errorParams.topMargin = px(10);
        error.setMinHeight(px(22));
        playerInventory.addView(error, errorParams);
        panels.addView(workbench, new LinearLayout.LayoutParams(-1, -2));
        panels.addView(playerInventory, new LinearLayout.LayoutParams(-1, -2));

        // Last child inside this modal's content, not OverlayMount.tooltip (modal hosts may reject it).
        // Pass through all input so the cursor never obscures the destination slot's hit target.
        cursor = passiveItem(context);
        cursor.setVisibility(INVISIBLE);
        addView(cursor, new FrameLayout.LayoutParams(px(44), px(44), Gravity.TOP | Gravity.LEFT));
        refresh();
    }

    public EntityEquipmentView(Context context, UiTheme theme, EntityTemplateValue preview,
                               Map<String, ItemStack> equipment, List<ItemStack> inventory, Runnable changed) {
        this(context, theme, equipment, inventory, changed);
        setPreview(preview);
    }

    /** Sources use the shared preview's revision fencing and render-thread resolution. */
    public void setPreviewReference(UUID entityId, String dimension) {
        preview.setReferenceSource(entityId, dimension);
        showPreviewStatus(preview.getPreviewStatus());
    }
    public void setPreview(EntityTemplateValue template) {
        preview.setTemplateSource(template);
        showPreviewStatus(preview.getPreviewStatus());
    }
    public Map<String, ItemStack> equipment() { return draft.equipment(); }
    public List<ItemStack> inventory() { return draft.inventory(); }
    public List<ItemStack> dropped() { return draft.dropped(); }
    /** A held stack/active gesture is an unfinished operation, not a server inventory snapshot. */
    public boolean isCommitReady() { return !draft.hasCarried() && !interaction.active() && !pointerCaptured; }
    public void setOnOperationFinished(Runnable listener) { operationFinished = Objects.requireNonNull(listener); }
    public void setInputBlocked(boolean blocked) { inputBlocked = blocked; }
    public void acceptCommitted(Map<String, ItemStack> equipment, List<ItemStack> inventory, boolean creative) {
        if (!isCommitReady()) throw new IllegalStateException("Equipment operation is still active");
        draft.acceptCommitted(equipment, inventory, creative);
        refresh();
    }
    public void setCreative(boolean creative) { interaction.reset(); finishPointer(); draft.setCreative(creative); refresh(); }
    public void setBindings(Bindings bindings) { interaction.reset(); finishPointer(); this.bindings = Objects.requireNonNull(bindings); }

    public boolean returnCarried() {
        interaction.reset();
        finishPointer();
        boolean hadCarried = draft.hasCarried();
        if (!draft.returnCarried()) {
            error.setText(tr("return_full"));
            return false;
        }
        error.setText("");
        if (hadCarried) { refresh(); changed.run(); }
        return true;
    }

    private void completed(EntityEquipmentDraft.Result result) {
        error.setText(switch (result) {
            case INCOMPATIBLE -> tr("incompatible");
            case FULL -> tr("slot_full");
            case UNSUPPORTED -> tr("slot_unavailable");
            default -> "";
        });
        if (result == EntityEquipmentDraft.Result.CHANGED) {
            ItemStackTooltipOverlay.hideWithin(this);
            refresh();
            changed.run();
        }
    }

    private void refresh() {
        EntityEquipmentDraft.Distribution distribution = interaction.preview();
        Map<EntityEquipmentDraft.Slot, ItemStack> ghosts = distribution == null ? Map.of() : distribution.slots();
        ItemStack carried = distribution == null ? draft.carried() : distribution.carried();
        slots.forEach((slot, view) -> view.bind(distribution == null ? draft.stack(slot)
                : ghosts.getOrDefault(slot, draft.stack(slot)), carried));
        Map<String, ItemStack> equipment = draft.equipment();
        if (previewEquipment == null || !sameEquipment(previewEquipment, equipment)) {
            previewEquipment = equipment;
            preview.setEquipment(equipment);
        }
        if (!ItemStack.matches(cursorStack, carried)) {
            cursorStack = carried.copy();
            cursor.setDisplayStack(carried);
        }
        updateCursor();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (inputBlocked) return true;
        trackPointer(event);
        EntityEquipmentDraft.Slot slot = findSlot(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && slot != null || pointerCaptured)
            return handlePointer(slot, event);
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (inputBlocked) return true;
        trackPointer(event);
        int action = event.getActionMasked();
        if (pointerCaptured && action == MotionEvent.ACTION_BUTTON_RELEASE) {
            interaction.release(findSlot(event), mouseButton(event), event.isShiftPressed());
            finishPointer();
            return true;
        }
        hoveredSlot = findSlot(event);
        if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) hoveredSlot = null;
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean handlePointer(EntityEquipmentDraft.Slot slot, MotionEvent event) {
        if (inputBlocked) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                if (slot == null || !draft.supports(slot)) return false;
                int button = mouseButton(event);
                if (pointerCaptured) return true;
                KeyAction binding = bindings.mouse(event, button);
                slots.get(slot).requestFocus();
                hoveredSlot = slot;
                if (binding.action() != Action.NONE && binding.action() != Action.CLONE) {
                    interaction.cancel();
                    runBinding(slot, binding, event.isControlPressed());
                    pointerCaptured = true;
                } else pointerCaptured = interaction.press(slot, button, event.isShiftPressed(),
                        binding.action() == Action.CLONE, event.getEventTimeNano());
                if (pointerCaptured) {
                    pressedButton = button;
                    slots.get(slot).setPressed(true);
                    requestDisallowInterceptTouchEvent(true);
                    ItemStackTooltipOverlay.hideWithin(this);
                }
                return pointerCaptured;
            }
            case MotionEvent.ACTION_MOVE -> { interaction.move(slot); return pointerCaptured; }
            case MotionEvent.ACTION_UP -> {
                boolean captured = pointerCaptured;
                interaction.release(slot, event.getActionButton() == 0 ? pressedButton : mouseButton(event), event.isShiftPressed());
                finishPointer();
                return captured;
            }
            case MotionEvent.ACTION_CANCEL -> {
                boolean captured = pointerCaptured;
                interaction.reset();
                finishPointer();
                return captured;
            }
            default -> { return pointerCaptured; }
        }
    }

    private void finishPointer() {
        boolean wasCaptured = pointerCaptured;
        pointerCaptured = false;
        pressedButton = -1;
        slots.values().forEach(slot -> slot.setPressed(false));
        requestDisallowInterceptTouchEvent(false);
        if (wasCaptured && !draft.hasCarried()) operationFinished.run();
    }

    private EntityEquipmentDraft.Slot findSlot(MotionEvent event) {
        for (var entry : slots.entrySet()) {
            View view = entry.getValue();
            if (!view.isEnabled() || view.getVisibility() != VISIBLE
                    || !UIUtils.rootToLocal(view, event.getRawX(), event.getRawY(), pointerTransform, pointerPosition)) continue;
            if (pointerPosition[0] < 0 || pointerPosition[0] >= view.getWidth()
                    || pointerPosition[1] < 0 || pointerPosition[1] >= view.getHeight()) continue;
            if (view.isAttachedToWindow() && (!view.getGlobalVisibleRect(visibleBounds)
                    || !visibleBounds.contains((int) event.getRawX(), (int) event.getRawY()))) continue;
            return entry.getKey();
        }
        return null;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (inputBlocked) return true;
        View focus = getRootView().findFocus();
        if (focus instanceof icyllis.modernui.widget.EditText) return super.dispatchKeyEvent(event);
        EntityEquipmentDraft.Slot target = pointerInside ? hoveredSlot : null;
        if (target == null) {
            for (var entry : slots.entrySet()) if (entry.getValue().isFocused()) { target = entry.getKey(); break; }
        }
        return handleKey(target, event) || super.dispatchKeyEvent(event);
    }

    private boolean handleKey(EntityEquipmentDraft.Slot slot, KeyEvent event) {
        if (inputBlocked) return true;
        if (event.getKeyCode() == KeyEvent.KEY_ESCAPE && (interaction.active() || consumedEscape)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                consumedEscape = true; onEscapePressed();
            } else consumedEscape = false;
            return true;
        }
        if (slot == null || !draft.supports(slot)) return false;
        if (event.getKeyCode() == KeyEvent.KEY_ENTER || event.getKeyCode() == KeyEvent.KEY_SPACE) {
            if (event.getAction() == KeyEvent.ACTION_UP) { interaction.reset(); completed(draft.click(slot, false)); }
            return true;
        }
        KeyAction binding = bindings.key(event);
        if (binding.action() == Action.NONE) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            interaction.reset();
            finishPointer();
            runBinding(slot, binding, event.isControlPressed());
        }
        return true;
    }

    /** Modal hosts route Escape here before deciding to close the surrounding window. */
    public boolean onEscapePressed() {
        if (!interaction.active()) return false;
        interaction.reset();
        finishPointer();
        return true;
    }

    private void runBinding(EntityEquipmentDraft.Slot slot, KeyAction binding, boolean all) {
        if (draft.hasCarried() && (binding.action() == Action.HOTBAR || binding.action() == Action.SWAP_OFFHAND)) return;
        EntityEquipmentDraft.Result result = switch (binding.action()) {
            case CLONE -> draft.cloneStack(slot);
            case DROP -> draft.hasCarried() ? EntityEquipmentDraft.Result.UNCHANGED : draft.drop(slot, all);
            case SWAP_OFFHAND -> draft.swap(EntityEquipmentDraft.Slot.equipment("offhand"), slot);
            case HOTBAR -> draft.swap(EntityEquipmentDraft.Slot.inventory(binding.hotbarIndex()), slot);
            default -> EntityEquipmentDraft.Result.UNCHANGED;
        };
        completed(result);
    }

    private static int mouseButton(MotionEvent event) {
        int button = event.getActionButton();
        if (button == 0) button = event.getButtonState();
        if ((button & MotionEvent.BUTTON_PRIMARY) != 0) return 0;
        if ((button & MotionEvent.BUTTON_SECONDARY) != 0) return 1;
        if ((button & MotionEvent.BUTTON_TERTIARY) != 0) return 2;
        if ((button & MotionEvent.BUTTON_BACK) != 0) return 3;
        if ((button & MotionEvent.BUTTON_FORWARD) != 0) return 4;
        return -1;
    }

    private static boolean sameEquipment(Map<String, ItemStack> first, Map<String, ItemStack> second) {
        return first.keySet().equals(second.keySet())
                && first.entrySet().stream().allMatch(entry -> ItemStack.matches(entry.getValue(), second.get(entry.getKey())));
    }

    private void trackPointer(MotionEvent event) {
        pointerInside = event.getActionMasked() != MotionEvent.ACTION_HOVER_EXIT
                && event.getActionMasked() != MotionEvent.ACTION_CANCEL
                && UIUtils.rootToLocal(this, event.getRawX(), event.getRawY(), pointerTransform, pointerPosition);
        if (pointerInside) {
            pointerX = pointerPosition[0];
            pointerY = pointerPosition[1];
        }
        updateCursor();
    }

    private void updateCursor() {
        cursor.setVisibility(pointerInside && draft.hasCarried() ? VISIBLE : INVISIBLE);
        // Keep the UI widget inside this panel for visibility/clipping. Its native pixels follow
        // the render callback's mouse position, independently of delayed surface translations.
        float width = cursor.getWidth(), height = cursor.getHeight();
        // setX/Y also subtract the child's layout origin (padding/RTL), unlike translation alone.
        cursor.setX(Math.max(0, Math.min(pointerX - width / 2, getWidth() - width)));
        cursor.setY(Math.max(0, Math.min(pointerY - height / 2, getHeight() - height)));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        boolean sideBySide = MeasureSpec.getSize(widthSpec) >= px(780);
        int orientation = sideBySide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
        if (panels.getOrientation() != orientation) panels.setOrientation(orientation);
        int inventoryWidth = sideBySide ? Math.min(px(490), MeasureSpec.getSize(widthSpec) / 2) : -1;
        setPanelParams(workbench, sideBySide ? 0 : -1, sideBySide ? 1 : 0);
        setPanelParams(playerInventory, inventoryWidth, 0);
        super.onMeasure(widthSpec, heightSpec);
    }

    private static void setPanelParams(View panel, int width, float weight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) panel.getLayoutParams();
        if (params.width != width || params.weight != weight) {
            params.width = width;
            params.weight = weight;
            panel.setLayoutParams(params);
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateCursor();
    }

    @Override protected void onDetachedFromWindow() {
        interaction.reset();
        finishPointer();
        ItemStackTooltipOverlay.hideWithin(this);
        pointerInside = false;
        cursor.setVisibility(INVISIBLE);
        super.onDetachedFromWindow();
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (!visible && interaction != null) {
            interaction.reset(); finishPointer(); hoveredSlot = null; pointerInside = false;
            if (cursor != null) cursor.setVisibility(INVISIBLE);
        }
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) { interaction.reset(); finishPointer(); }
    }

    private EquipmentItemSlotView itemSlot(EntityEquipmentDraft.Slot slot) {
        EquipmentItemSlotView view = new EquipmentItemSlotView(getContext(), theme, slot,
                new EquipmentItemSlotView.Input() {
                    public boolean pointer(EntityEquipmentDraft.Slot target, MotionEvent event) {
                        return handlePointer(target, event);
                    }
                    public boolean key(EntityEquipmentDraft.Slot target, KeyEvent event) { return handleKey(target, event); }
                }, draft::hasCarried);
        view.setEnabled(draft.supports(slot));
        view.setAlpha(draft.supports(slot) ? 1 : .4f);
        view.setFullVisibilityAnchor(view);
        slots.put(slot, view);
        return view;
    }

    private void showPreviewStatus(EntityTemplatePreviewView.Status status) {
        String key = switch (status) {
            case LOADING -> "loading";
            case UNAVAILABLE -> "unavailable";
            case ERROR -> "error";
            default -> null;
        };
        previewStatus.setText(key == null ? "" : Component.translatable("geometry_node.ui.entity_preview." + key).getString());
        previewStatus.setVisibility(key == null ? GONE : VISIBLE);
    }

    private LinearLayout equipmentSlot(String name) {
        LinearLayout wrapper = column();
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.addView(itemSlot(EntityEquipmentDraft.Slot.equipment(name)), new LinearLayout.LayoutParams(px(48), px(48)));
        TextView label = text("slot." + name, 10, theme.text().muted());
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = px(4);
        wrapper.addView(label, labelParams);
        return wrapper;
    }

    private ItemSlotView passiveItem(Context context) {
        int transparent = UiTheme.withAlpha(theme.surface().panel(), 0);
        ItemSlotView view = new ItemSlotView(context,
                new ValuePreviewStyle(0, 0, transparent, transparent, transparent), theme) {
            @Override protected boolean followsNativePointer() { return true; }
            @Override public boolean dispatchTouchEvent(MotionEvent event) { return false; }
            @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { return false; }
        };
        view.setEnabled(false);
        return view;
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(getContext());
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }
    private TextView text(String key, int size, int color) {
        TextView result = UIUtils.createLockedTextView(getContext(), key.isEmpty() ? "" : tr(key), size, color);
        result.setTextColor(color);
        result.setSingleLine(false);
        return result;
    }
    private void addText(LinearLayout column, String key, int size, int color, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = px(margin);
        column.addView(text(key, size, color), params);
    }
    private void divider(LinearLayout column, int margin) {
        View line = new View(getContext());
        line.setBackground(background(theme.border().divider(), theme.border().divider(), false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, px(1));
        params.topMargin = px(margin);
        column.addView(line, params);
    }
    private static ShapeDrawable background(int fill, int border, boolean stroke) {
        ShapeDrawable result = new ShapeDrawable();
        result.setColor(fill);
        if (stroke) result.setStroke(px(1), border);
        return result;
    }
    private static LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private static String tr(String key, Object... arguments) { return Component.translatable(PREFIX + key, arguments).getString(); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }

    /** Hosts may replace bindings; the default follows Minecraft's current remapped inventory keys. */
    public interface Bindings {
        default KeyAction key(KeyEvent event) { return KeyAction.none(); }
        default KeyAction mouse(MotionEvent event, int button) { return KeyAction.none(); }

        static Bindings minecraft() {
            return new Bindings() {
                private Options options() {
                    Minecraft minecraft = Minecraft.getInstance();
                    return minecraft == null ? null : minecraft.options;
                }
                @Override public KeyAction key(KeyEvent event) {
                    Options options = options();
                    if (options == null) return KeyAction.none();
                    var nativeEvent = new net.minecraft.client.input.KeyEvent(event.getKeyCode(),
                            event.getScanCode(), event.getModifiers());
                    return resolve(options, mapping -> mapping.matches(nativeEvent));
                }
                @Override public KeyAction mouse(MotionEvent event, int button) {
                    Options options = options();
                    if (options == null || button < 0) return KeyAction.none();
                    var nativeEvent = new net.minecraft.client.input.MouseButtonEvent(event.getRawX(), event.getRawY(),
                            new MouseButtonInfo(button, event.getModifiers()));
                    return resolve(options, mapping -> mapping.matchesMouse(nativeEvent));
                }
                private KeyAction resolve(Options options, java.util.function.Predicate<KeyMapping> matches) {
                    if (matches.test(options.keyPickItem)) return KeyAction.of(Action.CLONE);
                    if (matches.test(options.keySwapOffhand)) return KeyAction.of(Action.SWAP_OFFHAND);
                    if (matches.test(options.keyDrop)) return KeyAction.of(Action.DROP);
                    for (int i = 0; i < options.keyHotbarSlots.length; i++)
                        if (matches.test(options.keyHotbarSlots[i])) return KeyAction.hotbar(i);
                    return KeyAction.none();
                }
            };
        }
    }

    public enum Action { NONE, CLONE, DROP, SWAP_OFFHAND, HOTBAR }
    public record KeyAction(Action action, int hotbarIndex) {
        public KeyAction {
            Objects.requireNonNull(action);
            if (action == Action.HOTBAR ? hotbarIndex < 0 || hotbarIndex > 8 : hotbarIndex != -1)
                throw new IllegalArgumentException("Invalid inventory binding");
        }
        public static KeyAction none() { return of(Action.NONE); }
        public static KeyAction of(Action action) { return new KeyAction(action, -1); }
        public static KeyAction hotbar(int index) { return new KeyAction(Action.HOTBAR, index); }
    }

    /** Three rows of nine main slots, or one row of nine native hotbar slots. */
    private final class InventoryGrid extends ViewGroup {
        private final int count;
        private int cell;
        InventoryGrid(Context context, int first, int count) {
            super(context);
            this.count = count;
            for (int i = 0; i < count; i++)
                addView(itemSlot(EntityEquipmentDraft.Slot.inventory(first + i)), new LayoutParams(-2, -2));
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = resolveSize(px(9 * 42 + 8 * 4), widthSpec);
            cell = Math.max(0, Math.min(px(42), (width - px(4) * 8) / 9));
            int exact = MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY);
            for (int i = 0; i < count; i++) getChildAt(i).measure(exact, exact);
            setMeasuredDimension(width, resolveSize(count / 9 * (cell + px(4)) - px(4), heightSpec));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < count; i++) {
                int x = i % 9 * (cell + px(4)), y = i / 9 * (cell + px(4));
                getChildAt(i).layout(x, y, x + cell, y + cell);
            }
        }
        @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-2, -2); }
    }
}
