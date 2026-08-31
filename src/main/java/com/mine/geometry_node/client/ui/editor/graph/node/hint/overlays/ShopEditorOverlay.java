package com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays;

import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.runtime.dialogue.ui.DialogueHudTheme;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.components.overlay.InventoryItemPickerOverlay;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.InputBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.dialogue.OpenShop;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShopEditorOverlay extends FrameLayout {
    private static final int COLOR_DIM = DialogueHudTheme.OVERLAY_DIM;
    private static final int COLOR_WINDOW = DialogueHudTheme.PANEL;
    private static final int COLOR_PANEL = DialogueHudTheme.SURFACE;
    private static final int COLOR_PANEL_ALT = DialogueHudTheme.BUTTON_PRESSED;
    private static final int COLOR_FIELD = DialogueHudTheme.withAlpha(DialogueHudTheme.PANEL, 0xFF);
    private static final int COLOR_BORDER = DialogueHudTheme.DIVIDER;
    private static final int COLOR_TEXT = DialogueHudTheme.TEXT_PRIMARY;
    private static final int COLOR_MUTED = DialogueHudTheme.TEXT_MUTED;
    private static final int COLOR_BUTTON = DialogueHudTheme.BUTTON;
    private static final int COLOR_PRIMARY = DialogueHudTheme.ACCENT;
    private static final int COLOR_DANGER = DialogueHudTheme.withAlpha(DialogueHudTheme.ERROR, 0xB8);
    private static final int COLOR_ACCENT = DialogueHudTheme.ACCENT;
    private static final int COLOR_FIELD_BORDER = DialogueHudTheme.withAlpha(DialogueHudTheme.TEXT_MUTED, 0x44);
    private static final int COLOR_SELECTED = DialogueHudTheme.withAlpha(DialogueHudTheme.ACCENT, 0x30);
    private static final int COLOR_DROP_HIGHLIGHT = DialogueHudTheme.withAlpha(DialogueHudTheme.ACCENT, 0x22);

    private static final int WINDOW_MARGIN_DP = 34;
    private static final int OFFER_MIN_HEIGHT_DP = 178;
    private static final int SLOT_SIZE_DP = 38;
    private static final int MAX_SLOTS_PER_ROW = 5;
    private static final int MENU_WIDTH_DP = 168;

    private static ShopEditorOverlay sOpenOverlay;

    private final EditorContext mEditorContext;
    private final NodeData mNodeData;
    private final String mPortId;
    private final LinearLayout mOfferList;
    private final List<OfferEditor> mOfferEditors = new ArrayList<>();
    private final float mTouchSlop;
    private InventoryItemPickerOverlay mInventoryPicker;
    private StackSlotMenu mSlotMenu;
    private ConditionDropdownMenu mConditionMenu;
    private QuantityDialog mQuantityDialog;
    private SlotGesture mSlotGesture;
    private DropTarget mDropTarget;

    private ShopEditorOverlay(Context context, EditorContext editorContext, NodeData nodeData, String portId) {
        super(context);
        this.mEditorContext = editorContext;
        this.mNodeData = nodeData;
        this.mPortId = portId;
        this.mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setBackground(rect(COLOR_DIM, 0.0f, 0, 0));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnClickListener(v -> dismiss());

        LinearLayout window = new LinearLayout(context);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setPadding(UIUtils.dp2pxInt(18), UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(18), UIUtils.dp2pxInt(14));
        window.setBackground(rect(COLOR_WINDOW, 3.0f, 1, COLOR_BORDER));
        window.setOnClickListener(v -> {
        });

        window.addView(createHeader(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(44)));
        window.addView(createDivider(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1)));

        ScrollView scrollView = new ScrollView(context);
        mOfferList = new LinearLayout(context);
        mOfferList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mOfferList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        window.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        window.addView(createDivider(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1)));
        window.addView(createActions(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(42)));

        FrameLayout.LayoutParams windowLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        windowLp.leftMargin = UIUtils.dp2pxInt(WINDOW_MARGIN_DP);
        windowLp.rightMargin = UIUtils.dp2pxInt(WINDOW_MARGIN_DP);
        windowLp.topMargin = UIUtils.dp2pxInt(WINDOW_MARGIN_DP);
        windowLp.bottomMargin = UIUtils.dp2pxInt(WINDOW_MARGIN_DP);
        addView(window, windowLp);

        loadExistingData();
        post(this::requestFocus);
    }

    public static void show(View anchor, EditorContext editorContext, NodeData nodeData, PortRow row) {
        if (anchor == null || editorContext == null || nodeData == null) {
            return;
        }
        ViewGroup host = findWindowHost(anchor);
        if (host == null) {
            return;
        }
        String portId = row != null && row.leftPort() != null
                ? row.leftPort().id()
                : StandardPorts.SHOP_DATA.getId();

        ShopEditorOverlay overlay = new ShopEditorOverlay(anchor.getContext(), editorContext, nodeData, portId);
        closeOpenOverlay();
        host.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sOpenOverlay = overlay;
    }

    public static boolean hasVisibleOverlay() {
        return sOpenOverlay != null
                && sOpenOverlay.getParent() != null
                && sOpenOverlay.getVisibility() == View.VISIBLE;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
            if (mQuantityDialog != null) {
                closeQuantityDialog();
                return true;
            }
            if (mSlotMenu != null) {
                closeSlotMenu();
                return true;
            }
            if (mConditionMenu != null) {
                closeConditionMenu();
                return true;
            }
            if (mInventoryPicker != null) {
                closeInventoryPicker();
                return true;
            }
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

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return true;
    }

    private View createHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View marker = new View(context);
        marker.setBackground(rect(COLOR_ACCENT, 1.0f, 0, 0));
        LinearLayout.LayoutParams markerLp = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(3),
                UIUtils.dp2pxInt(26)
        );
        markerLp.rightMargin = UIUtils.dp2pxInt(11);
        header.addView(marker, markerLp);

        TextView title = label(context, tr("geometry_node.shop.editor.title"), 15.0f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView add = button(context, tr("geometry_node.shop.editor.add_offer"), COLOR_PRIMARY, v -> addOffer(defaultOffer()));
        header.addView(add, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(84), UIUtils.dp2pxInt(30)));
        return header;
    }

    private View createActions(Context context) {
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, UIUtils.dp2pxInt(8), 0, 0);

        actions.addView(UiActionButton.create(context, tr("geometry_node.common.cancel"),
                UiActionButton.Role.SECONDARY, v -> dismiss()),
                new LinearLayout.LayoutParams(UIUtils.dp2pxInt(82), UIUtils.dp2pxInt(32)));
        TextView spacer = label(context, "", 1.0f, 0, Gravity.CENTER);
        actions.addView(spacer, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(10), 1));
        actions.addView(UiActionButton.create(context, tr("geometry_node.common.save"),
                UiActionButton.Role.PRIMARY, v -> commit()),
                new LinearLayout.LayoutParams(UIUtils.dp2pxInt(90), UIUtils.dp2pxInt(32)));
        return actions;
    }

    private static View createDivider(Context context) {
        View divider = new View(context);
        divider.setBackground(rect(COLOR_BORDER, 0.0f, 0, 0));
        return divider;
    }

    private void loadExistingData() {
        Object raw = mNodeData.inputs != null ? mNodeData.inputs.get(mPortId) : null;
        ShopState state = parseShopState(raw);
        List<OfferState> offers = new ArrayList<>(state.offers());
        if (offers.isEmpty()) {
            offers.add(defaultOffer());
        }
        for (OfferState offer : offers) {
            addOffer(offer);
        }
    }

    private void addOffer(OfferState state) {
        OfferEditor editor = new OfferEditor(getContext(), state);
        mOfferEditors.add(editor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UIUtils.dp2pxInt(10);
        mOfferList.addView(editor.root, lp);
        renumberOfferHeaders();
        editor.refreshConditionLabels();
    }

    private void removeOffer(OfferEditor editor) {
        if (mOfferEditors.size() <= 1) {
            editor.clear();
            return;
        }
        mOfferEditors.remove(editor);
        mOfferList.removeView(editor.root);
        renumberOfferHeaders();
    }

    private void renumberOfferHeaders() {
        for (int i = 0; i < mOfferEditors.size(); i++) {
            mOfferEditors.get(i).setIndex(i + 1);
        }
    }

    private void commit() {
        Map<String, Object> shopData = new LinkedHashMap<>();
        List<String> conditionPorts = conditionPorts();
        List<Object> offers = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (int i = 0; i < mOfferEditors.size(); i++) {
            Map<String, Object> offer = mOfferEditors.get(i).toMap(usedIds, i + 1, conditionPorts);
            if (offer != null) {
                offers.add(offer);
            }
        }
        shopData.put("offers", offers);
        UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId, shopData);
        dismiss();
    }

    private List<String> conditionPorts() {
        int count = resolveConditionInputCount();
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            result.add(StandardPorts.BOOL.getIdWithIndex(i));
        }
        return result;
    }

    private int resolveConditionInputCount() {
        Object raw = mNodeData.inputs != null ? mNodeData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()) : null;
        return Math.max(0, Math.min(intValue(raw, 0), OpenShop.MAX_CONDITION_INPUTS));
    }

    private static String validConditionId(String conditionId, List<String> conditionPorts) {
        if (conditionId == null || conditionId.isBlank() || conditionPorts == null) {
            return "";
        }
        for (String portId : conditionPorts) {
            if (portId.equals(conditionId)) {
                return conditionId;
            }
        }
        return "";
    }

    private void dismiss() {
        closeInventoryPicker();
        closeSlotMenu();
        closeConditionMenu();
        closeQuantityDialog();
        cancelSlotDrag();
        if (sOpenOverlay == this) {
            sOpenOverlay = null;
        }
        if (getParent() instanceof ViewGroup parent) {
            parent.removeView(this);
            parent.requestFocus();
        }
    }

    private static void closeOpenOverlay() {
        if (sOpenOverlay != null) {
            sOpenOverlay.dismiss();
        }
    }

    private void openInventoryPicker(java.util.function.Consumer<ItemStack> onPicked) {
        closeInventoryPicker();
        closeConditionMenu();
        mInventoryPicker = InventoryItemPickerOverlay.showIn(this, stack -> {
            if (onPicked != null) {
                onPicked.accept(stack.copy());
            }
        }, () -> {
            mInventoryPicker = null;
            requestFocus();
        });
    }

    private void closeInventoryPicker() {
        if (mInventoryPicker == null) {
            return;
        }
        mInventoryPicker.dismiss();
        mInventoryPicker = null;
        requestFocus();
    }

    private void showSlotMenu(StackEntryView entry, float rawX, float rawY) {
        if (entry == null) {
            return;
        }
        closeSlotMenu();
        closeConditionMenu();
        closeQuantityDialog();
        closeInventoryPicker();
        mSlotMenu = new StackSlotMenu(getContext(), entry);
        addView(mSlotMenu, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mSlotMenu.showAt(rawX, rawY);
    }

    private void closeSlotMenu() {
        if (mSlotMenu == null) {
            return;
        }
        mSlotMenu.dismiss();
        mSlotMenu = null;
        requestFocus();
    }

    private void showConditionMenu(ConditionSelector selector) {
        if (selector == null) {
            return;
        }
        closeConditionMenu();
        closeSlotMenu();
        closeQuantityDialog();
        closeInventoryPicker();
        mConditionMenu = new ConditionDropdownMenu(getContext(), selector);
        mConditionMenu.layoutBelow(selector.view);
        addView(mConditionMenu, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeConditionMenu() {
        if (mConditionMenu == null) {
            return;
        }
        mConditionMenu.dismiss();
        mConditionMenu = null;
        requestFocus();
    }

    private void showQuantityDialog(StackEntryView entry) {
        if (entry == null || entry.stack().isEmpty()) {
            return;
        }
        closeSlotMenu();
        closeConditionMenu();
        closeQuantityDialog();
        closeInventoryPicker();
        mQuantityDialog = new QuantityDialog(getContext(), entry);
        addView(mQuantityDialog, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mQuantityDialog.focusInput();
    }

    private void closeQuantityDialog() {
        if (mQuantityDialog == null) {
            return;
        }
        mQuantityDialog.dismiss();
        mQuantityDialog = null;
        requestFocus();
    }

    private String clearSlotShortcutText() {
        return ConfigManager.INSTANCE.get(BuiltinConfigEntries.SHOP_CLEAR_SLOT);
    }

    private boolean matchesClearSlotShortcut(MotionEvent event) {
        InputBinding binding = InputBinding.parse(clearSlotShortcutText());
        return binding != null && binding.matches(event);
    }

    private boolean beginSlotGesture(StackEntryView entry, MotionEvent event) {
        if (entry == null || event == null || isRightMouse(event)) {
            return false;
        }
        mSlotGesture = new SlotGesture(entry, event.getRawX(), event.getRawY());
        closeSlotMenu();
        closeConditionMenu();
        return true;
    }

    private boolean updateSlotGesture(MotionEvent event) {
        if (mSlotGesture == null || event == null) {
            return false;
        }
        float dx = event.getRawX() - mSlotGesture.startRawX();
        float dy = event.getRawY() - mSlotGesture.startRawY();
        if (!mSlotGesture.dragging() && Math.hypot(dx, dy) > mTouchSlop) {
            startSlotDrag(mSlotGesture);
        }
        if (mSlotGesture.dragging()) {
            highlightDropTarget(findDropTarget(event.getRawX(), event.getRawY(), mSlotGesture.entry()));
            return true;
        }
        return false;
    }

    private boolean finishSlotGesture(MotionEvent event) {
        if (mSlotGesture == null) {
            return false;
        }
        SlotGesture gesture = mSlotGesture;
        mSlotGesture = null;
        if (gesture.dragging()) {
            DropTarget target = findDropTarget(event.getRawX(), event.getRawY(), gesture.entry());
            finishSlotDrag(gesture.entry(), target);
            clearDropHighlight();
            gesture.entry().setAlpha(1.0f);
            mDropTarget = null;
            return true;
        }
        return false;
    }

    private void cancelSlotDrag() {
        if (mSlotGesture != null) {
            mSlotGesture.entry().setAlpha(1.0f);
            mSlotGesture = null;
        }
        clearDropHighlight();
        mDropTarget = null;
    }

    private void startSlotDrag(SlotGesture gesture) {
        if (gesture == null || gesture.entry().stackJson().isBlank()) {
            return;
        }
        gesture.setDragging(true);
        gesture.entry().setAlpha(0.45f);
    }

    private void finishSlotDrag(StackEntryView source, DropTarget target) {
        if (source == null || target == null || source.stackJson().isBlank()) {
            return;
        }
        StackListEditor sourceList = source.owner();
        StackListEditor targetList = target.list();
        if (sourceList == null || targetList == null) {
            return;
        }
        boolean sameOffer = sourceList.offerEditor() == targetList.offerEditor();
        if (sameOffer) {
            sourceList.moveEntryTo(source, targetList, target.index());
        } else {
            targetList.insertStack(source.stackJson(), target.index());
        }
    }

    private DropTarget findDropTarget(float rawX, float rawY, StackEntryView source) {
        DropTarget fallback = null;
        for (OfferEditor offerEditor : mOfferEditors) {
            DropTarget costTarget = offerEditor.costs.dropTargetAt(rawX, rawY, source);
            if (costTarget != null) {
                return costTarget;
            }
            if (fallback == null && offerEditor.costs.containsRawPoint(rawX, rawY)) {
                fallback = new DropTarget(offerEditor.costs, offerEditor.costs.size());
            }
            DropTarget rewardTarget = offerEditor.rewards.dropTargetAt(rawX, rawY, source);
            if (rewardTarget != null) {
                return rewardTarget;
            }
            if (fallback == null && offerEditor.rewards.containsRawPoint(rawX, rawY)) {
                fallback = new DropTarget(offerEditor.rewards, offerEditor.rewards.size());
            }
        }
        return fallback;
    }

    private void highlightDropTarget(DropTarget target) {
        if (mDropTarget != null && !mDropTarget.equals(target)) {
            mDropTarget.list().setDropHighlighted(false);
        }
        if (target != null) {
            target.list().setDropHighlighted(true);
        }
        mDropTarget = target;
    }

    private void clearDropHighlight() {
        if (mDropTarget != null) {
            mDropTarget.list().setDropHighlighted(false);
        }
    }

    private static boolean isRightMouse(MotionEvent event) {
        return (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                || event.getActionButton() == MotionEvent.BUTTON_SECONDARY;
    }

    private static boolean isLeftMouse(MotionEvent event) {
        return event.getActionButton() == MotionEvent.BUTTON_PRIMARY
                || (event.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0
                || event.getActionMasked() == MotionEvent.ACTION_UP;
    }

    private boolean isInsideRaw(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return rawX >= loc[0]
                && rawY >= loc[1]
                && rawX < loc[0] + view.getWidth()
                && rawY < loc[1] + view.getHeight();
    }

    private int rawToLocalX(View view, float rawX) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return Math.round(rawX - loc[0]);
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

    private static ShopState parseShopState(Object raw) {
        if (!(raw instanceof Map<?, ?> root)) {
            return new ShopState(List.of());
        }
        return new ShopState(parseOffers(root.get("offers")));
    }

    private static List<OfferState> parseOffers(Object offersObj) {
        List<OfferState> result = new ArrayList<>();
        if (!(offersObj instanceof List<?> offers)) {
            return result;
        }
        int index = 1;
        for (Object offerObj : offers) {
            if (!(offerObj instanceof Map<?, ?> offerMap)) {
                continue;
            }
            String id = stringValue(offerMap.get("id"), "trade_" + index);
            String title = stringValue(offerMap.get("title"), "");
            int maxUses = intValue(offerMap.get("max_uses"), 0);
            boolean consumeSellerItems = boolValue(offerMap.get("consume_seller_items"), false);
            boolean sellerReceivesPayment = boolValue(offerMap.get("seller_receives_payment"), false);
            String visibleCondition = stringValue(offerMap.get("visible_condition"), "");
            String enabledCondition = stringValue(offerMap.get("enabled_condition"), "");
            String disabledReason = stringValue(offerMap.get("disabled_reason"), "");
            result.add(new OfferState(
                    id,
                    title,
                    maxUses,
                    consumeSellerItems,
                    sellerReceivesPayment,
                    visibleCondition,
                    enabledCondition,
                    disabledReason,
                    parseStacks(offerMap.get("costs")),
                    parseStacks(offerMap.get("rewards"))
            ));
            index++;
        }
        return result;
    }

    private static List<String> parseStacks(Object raw) {
        List<String> stacks = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return stacks;
        }
        for (Object item : list) {
            String stack = "";
            if (item instanceof Map<?, ?> map) {
                stack = stringValue(map.get("stack"), "");
            } else if (item instanceof String string) {
                stack = string;
            }
            if (!stack.isBlank()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static OfferState defaultOffer() {
        return new OfferState("", "", 0, false, false, "", "", "", List.of(), List.of());
    }


    private static String normalizeOfferId(String raw, Set<String> usedIds, int index) {
        String base = raw == null ? "" : raw.trim();
        if (base.isEmpty()) {
            base = "trade_" + index;
        }
        base = base.replaceAll("\\s+", "_");
        String candidate = base;
        int suffix = 2;
        while (usedIds.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        usedIds.add(candidate);
        return candidate;
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String string) {
            return string;
        }
        return fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if ("true".equalsIgnoreCase(string) || "1".equals(string)) {
                return true;
            }
            if ("false".equalsIgnoreCase(string) || "0".equals(string)) {
                return false;
            }
        }
        return fallback;
    }

    private static EditText field(Context context, String value, int gravity) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setText(value == null ? "" : value);
        input.setTextColor(COLOR_TEXT);
        UIUtils.setLockedTextSize(input, 12.0f);
        input.setGravity(gravity);
        input.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        input.setBackground(rect(COLOR_FIELD, 2.0f, 1, COLOR_FIELD_BORDER));
        return input;
    }

    private static TextView label(Context context, String text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(context, text, sizeDp, color);
        view.setGravity(gravity);
        return view;
    }

    private static TextView button(Context context, String text, int color, View.OnClickListener listener) {
        TextView view = label(context, text, 12.5f, 0xFFFFFFFF, Gravity.CENTER);
        view.setBackground(rect(color, 2.0f, 0, 0));
        view.setOnClickListener(listener);
        int hoverColor = color == COLOR_PRIMARY
                ? DialogueHudTheme.ACCENT_HOVER
                : color == COLOR_DANGER ? DialogueHudTheme.ERROR : DialogueHudTheme.BUTTON_HOVER;
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                v.setBackground(rect(hoverColor, 2.0f, 0, 0));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                v.setBackground(rect(color, 2.0f, 0, 0));
            }
            return false;
        });
        return view;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
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

    private record ShopState(List<OfferState> offers) {
    }

    private record OfferState(
            String id,
            String title,
            int maxUses,
            boolean consumeSellerItems,
            boolean sellerReceivesPayment,
            String visibleCondition,
            String enabledCondition,
            String disabledReason,
            List<String> costs,
            List<String> rewards
    ) {
    }

    private record DropTarget(StackListEditor list, int index) {
    }

    private static final class SlotGesture {
        private final StackEntryView entry;
        private final float startRawX;
        private final float startRawY;
        private boolean dragging;

        private SlotGesture(StackEntryView entry, float startRawX, float startRawY) {
            this.entry = entry;
            this.startRawX = startRawX;
            this.startRawY = startRawY;
        }

        private StackEntryView entry() {
            return entry;
        }

        private float startRawX() {
            return startRawX;
        }

        private float startRawY() {
            return startRawY;
        }

        private boolean dragging() {
            return dragging;
        }

        private void setDragging(boolean dragging) {
            this.dragging = dragging;
        }
    }

    private final class OfferEditor {
        private final LinearLayout root;
        private final TextView header;
        private final EditText idInput;
        private final EditText titleInput;
        private final EditText maxUsesInput;
        private final ToggleControl infiniteUses;
        private final ToggleControl consumeSellerItems;
        private final ToggleControl sellerReceivesPayment;
        private final ConditionSelector visibleCondition;
        private final ConditionSelector enabledCondition;
        private final EditText disabledReasonInput;
        private final StackListEditor costs;
        private final StackListEditor rewards;

        private OfferEditor(Context context, OfferState state) {
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(12));
            root.setMinimumHeight(UIUtils.dp2pxInt(OFFER_MIN_HEIGHT_DP));
            root.setBackground(rect(COLOR_PANEL, 2.0f, 1, COLOR_BORDER));

            LinearLayout headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            header = label(context, tr("geometry_node.shop.editor.offer"), 13.5f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            headerRow.addView(header, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(30), 1.0f));
            TextView remove = button(context, tr("geometry_node.common.delete"), COLOR_DANGER, v -> removeOffer(this));
            headerRow.addView(remove, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(64), UIUtils.dp2pxInt(28)));
            root.addView(headerRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32)));

            LinearLayout fields = new LinearLayout(context);
            fields.setOrientation(LinearLayout.HORIZONTAL);
            fields.setGravity(Gravity.CENTER_VERTICAL);
            idInput = field(context, state.id(), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            titleInput = field(context, state.title(), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            maxUsesInput = field(context, String.valueOf(Math.max(0, state.maxUses())), Gravity.CENTER);
            addField(fields, context, tr("geometry_node.shop.editor.field.id"), idInput, 0, 1.1f);
            addField(fields, context, tr("geometry_node.shop.editor.field.title"), titleInput, UIUtils.dp2pxInt(8), 1.4f);
            addField(fields, context, tr("geometry_node.shop.editor.field.uses"), maxUsesInput, UIUtils.dp2pxInt(8), 0.55f);
            root.addView(fields, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

            LinearLayout optionsRow = new LinearLayout(context);
            optionsRow.setOrientation(LinearLayout.HORIZONTAL);
            optionsRow.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            optionsRow.setPadding(0, UIUtils.dp2pxInt(8), 0, 0);
            infiniteUses = new ToggleControl(context, tr("geometry_node.shop.editor.toggle.infinite_uses"), state.maxUses() <= 0, this::syncMaxUsesEnabled);
            consumeSellerItems = new ToggleControl(context, tr("geometry_node.shop.editor.toggle.consume_seller_items"), state.consumeSellerItems(), null);
            sellerReceivesPayment = new ToggleControl(context, tr("geometry_node.shop.editor.toggle.seller_receives_payment"), state.sellerReceivesPayment(), null);
            visibleCondition = new ConditionSelector(context, tr("geometry_node.shop.editor.condition.visible"), state.visibleCondition(), null);
            enabledCondition = new ConditionSelector(context, tr("geometry_node.shop.editor.condition.enabled"), state.enabledCondition(), this::syncDisabledReasonEnabled);
            disabledReasonInput = field(context, state.disabledReason(), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addToggle(optionsRow, infiniteUses, 0, 92);
            addToggle(optionsRow, consumeSellerItems, UIUtils.dp2pxInt(7), 102);
            addToggle(optionsRow, sellerReceivesPayment, UIUtils.dp2pxInt(7), 102);
            addSelector(optionsRow, visibleCondition, UIUtils.dp2pxInt(9), 112);
            addSelector(optionsRow, enabledCondition, UIUtils.dp2pxInt(7), 112);
            addField(optionsRow, context, tr("geometry_node.shop.editor.field.disabled_reason"), disabledReasonInput, UIUtils.dp2pxInt(7), 1.0f);
            root.addView(optionsRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(38)));
            syncMaxUsesEnabled();
            syncDisabledReasonEnabled();

            LinearLayout tradeRow = new LinearLayout(context);
            tradeRow.setOrientation(LinearLayout.HORIZONTAL);
            tradeRow.setPadding(0, UIUtils.dp2pxInt(10), 0, 0);
            costs = new StackListEditor(context, this, tr("geometry_node.shop.editor.costs"), state.costs());
            rewards = new StackListEditor(context, this, tr("geometry_node.shop.editor.rewards"), state.rewards());
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            leftLp.rightMargin = UIUtils.dp2pxInt(8);
            tradeRow.addView(costs.root, leftLp);
            tradeRow.addView(rewards.root, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            root.addView(tradeRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private void setIndex(int index) {
            header.setText(tr("geometry_node.shop.editor.offer_index", index));
            if (idInput.getText().toString().trim().isEmpty()) {
                idInput.setText("trade_" + index);
            }
        }

        private void clear() {
            idInput.setText("trade_1");
            titleInput.setText("");
            maxUsesInput.setText("0");
            infiniteUses.setChecked(true);
            consumeSellerItems.setChecked(false);
            sellerReceivesPayment.setChecked(false);
            visibleCondition.setConditionId("");
            enabledCondition.setConditionId("");
            disabledReasonInput.setText("");
            syncMaxUsesEnabled();
            syncDisabledReasonEnabled();
            costs.clear();
            rewards.clear();
        }

        private Map<String, Object> toMap(Set<String> usedIds, int index, List<String> conditionPorts) {
            int maxUses = infiniteUses.isChecked()
                    ? 0
                    : Math.max(1, intValue(maxUsesInput.getText().toString(), 1));
            Map<String, Object> offer = new LinkedHashMap<>();
            offer.put("id", normalizeOfferId(idInput.getText().toString(), usedIds, index));
            offer.put("title", titleInput.getText().toString().trim());
            offer.put("costs", costs.toList());
            offer.put("rewards", rewards.toList());
            offer.put("max_uses", maxUses);
            offer.put("consume_seller_items", consumeSellerItems.isChecked());
            offer.put("seller_receives_payment", sellerReceivesPayment.isChecked());
            offer.put("visible_condition", validConditionId(visibleCondition.conditionId(), conditionPorts));
            offer.put("enabled_condition", validConditionId(enabledCondition.conditionId(), conditionPorts));
            offer.put("disabled_reason", disabledReasonInput.getText().toString().trim());
            return offer;
        }

        private void refreshConditionLabels() {
            visibleCondition.refreshLabel();
            enabledCondition.refreshLabel();
            syncDisabledReasonEnabled();
        }

        private void syncMaxUsesEnabled() {
            boolean finite = !infiniteUses.isChecked();
            maxUsesInput.setEnabled(finite);
            maxUsesInput.setTextColor(finite ? COLOR_TEXT : COLOR_MUTED);
            if (!finite) {
                maxUsesInput.setText("0");
            } else if (intValue(maxUsesInput.getText().toString(), 0) <= 0) {
                maxUsesInput.setText("1");
            }
        }

        private void syncDisabledReasonEnabled() {
            boolean editable = !enabledCondition.conditionId().isBlank();
            disabledReasonInput.setEnabled(editable);
            disabledReasonInput.setTextColor(editable ? COLOR_TEXT : COLOR_MUTED);
        }

        private void addField(LinearLayout parent, Context context, String labelText, EditText input, int leftMargin, float weight) {
            LinearLayout group = new LinearLayout(context);
            group.setOrientation(LinearLayout.HORIZONTAL);
            group.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = label(context, labelText, 11.0f, COLOR_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            group.addView(label, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(34), ViewGroup.LayoutParams.MATCH_PARENT));
            group.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
            lp.leftMargin = leftMargin;
            parent.addView(group, lp);
        }

        private void addToggle(LinearLayout parent, ToggleControl toggle, int leftMargin, int widthDp) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(widthDp), UIUtils.dp2pxInt(26));
            lp.leftMargin = leftMargin;
            parent.addView(toggle.view, lp);
        }

        private void addSelector(LinearLayout parent, ConditionSelector selector, int leftMargin, int widthDp) {
            LinearLayout group = new LinearLayout(getContext());
            group.setOrientation(LinearLayout.HORIZONTAL);
            group.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = label(getContext(), selector.label(), 11.0f, COLOR_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            group.addView(label, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(44), ViewGroup.LayoutParams.MATCH_PARENT));
            group.addView(selector.view, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(widthDp), ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = leftMargin;
            parent.addView(group, lp);
        }
    }

    private final class ConditionSelector {
        private final TextView view;
        private final String label;
        private final Runnable onChanged;
        private String conditionId;

        private ConditionSelector(Context context, String label, String conditionId, Runnable onChanged) {
            this.view = ShopEditorOverlay.label(context, "", 11.5f, COLOR_TEXT, Gravity.CENTER);
            this.label = label;
            this.onChanged = onChanged;
            this.conditionId = conditionId == null ? "" : conditionId;
            this.view.setBackground(rect(COLOR_FIELD, 2.0f, 1, COLOR_FIELD_BORDER));
            this.view.setOnClickListener(v -> showConditionMenu(this));
            refreshLabel();
        }

        private String label() {
            return label;
        }

        private String conditionId() {
            return conditionId;
        }

        private void setConditionId(String conditionId) {
            this.conditionId = conditionId == null ? "" : conditionId;
            refreshLabel();
            if (onChanged != null) {
                onChanged.run();
            }
        }

        private void refreshLabel() {
            String validId = validConditionId(conditionId, conditionPorts());
            conditionId = validId;
            view.setText(validId.isBlank() ? tr("geometry_node.common.none") : validId);
            view.setTextColor(validId.isBlank() ? COLOR_MUTED : COLOR_TEXT);
        }
    }

    private final class ConditionDropdownMenu extends FrameLayout {
        private static final int COLOR_MENU_BG = DialogueHudTheme.PANEL;
        private static final int COLOR_MENU_BORDER = DialogueHudTheme.DIVIDER;
        private static final int COLOR_MENU_HOVER = DialogueHudTheme.BUTTON_HOVER;
        private static final int COLOR_MENU_TEXT = DialogueHudTheme.TEXT_PRIMARY;

        private final LinearLayout panel;

        private ConditionDropdownMenu(Context context, ConditionSelector selector) {
            super(context);
            setOnClickListener(v -> closeConditionMenu());

            panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(6));
            panel.setBackground(rect(COLOR_MENU_BG, 3.0f, 1, COLOR_MENU_BORDER));
            panel.setOnClickListener(v -> {
            });
            addView(panel);

            addOption(tr("geometry_node.common.none"), "", selector);
            for (String portId : conditionPorts()) {
                addOption(portId, portId, selector);
            }
        }

        private void layoutBelow(View anchor) {
            if (anchor == null) {
                return;
            }
            int[] anchorLoc = new int[2];
            int[] rootLoc = new int[2];
            anchor.getLocationOnScreen(anchorLoc);
            ShopEditorOverlay.this.getLocationOnScreen(rootLoc);
            layoutPanel(
                    anchorLoc[0] - rootLoc[0],
                    anchorLoc[1] - rootLoc[1] + anchor.getHeight(),
                    anchor.getWidth(),
                    ShopEditorOverlay.this.getWidth(),
                    ShopEditorOverlay.this.getHeight()
            );
        }

        private void dismiss() {
            if (getParent() instanceof ViewGroup parent) {
                parent.removeView(this);
            }
        }

        private void addOption(String labelText, String value, ConditionSelector selector) {
            TextView row = label(getContext(), labelText, 12.0f, COLOR_MENU_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
            row.setBackground(rect(value.equals(selector.conditionId()) ? COLOR_SELECTED : 0x00000000, 2.0f, 0, 0));
            row.setOnClickListener(v -> {
                selector.setConditionId(value);
                closeConditionMenu();
            });
            row.setOnHoverListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    row.setBackground(rect(COLOR_MENU_HOVER, 2.0f, 0, 0));
                    row.setTextColor(0xFFFFFFFF);
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    row.setBackground(rect(value.equals(selector.conditionId()) ? COLOR_SELECTED : 0x00000000, 2.0f, 0, 0));
                    row.setTextColor(COLOR_MENU_TEXT);
                }
                return false;
            });
            panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(26)));
        }

        private void layoutPanel(float x, float y, int anchorWidth, int hostWidth, int hostHeight) {
            int menuWidth = Math.max(UIUtils.dp2pxInt(MENU_WIDTH_DP), anchorWidth);
            int widthSpec = MeasureSpec.makeMeasureSpec(menuWidth, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            panel.measure(widthSpec, heightSpec);
            int menuHeight = panel.getMeasuredHeight();
            if (menuHeight <= 0) {
                menuHeight = UIUtils.dp2pxInt(42);
            }
            int edge = UIUtils.dp2pxInt(6);
            int targetX = Math.round(x);
            int targetY = Math.round(y);
            if (hostWidth > 0 && targetX + menuWidth + edge > hostWidth) {
                targetX = Math.max(edge, hostWidth - menuWidth - edge);
            }
            if (hostHeight > 0 && targetY + menuHeight + edge > hostHeight) {
                targetY = Math.max(edge, Math.round(y) - menuHeight - UIUtils.dp2pxInt(30));
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = Math.max(edge, targetX);
            lp.topMargin = Math.max(edge, targetY);
            panel.setLayoutParams(lp);
        }
    }

    private final class ToggleControl {
        private final TextView view;
        private final String label;
        private final Runnable onChanged;
        private boolean checked;

        private ToggleControl(Context context, String label, boolean checked, Runnable onChanged) {
            this.view = label(context, "", 11.5f, COLOR_TEXT, Gravity.CENTER);
            this.label = label;
            this.onChanged = onChanged;
            this.view.setOnClickListener(v -> {
                setChecked(!this.checked);
                if (this.onChanged != null) {
                    this.onChanged.run();
                }
            });
            setChecked(checked);
        }

        private boolean isChecked() {
            return checked;
        }

        private void setChecked(boolean checked) {
            this.checked = checked;
            view.setText(label);
            view.setTextColor(checked ? COLOR_TEXT : COLOR_MUTED);
            view.setBackground(rect(
                    checked ? DialogueHudTheme.ACCENT_PRESSED : COLOR_FIELD,
                    2.0f,
                    1,
                    checked ? COLOR_ACCENT : COLOR_FIELD_BORDER
            ));
        }
    }

    private final class StackListEditor {
        private final OfferEditor offerEditor;
        private final LinearLayout root;
        private final LinearLayout list;
        private final List<StackEntryView> entries = new ArrayList<>();

        private StackListEditor(Context context, OfferEditor offerEditor, String title, List<String> initialStacks) {
            this.offerEditor = offerEditor;
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(7), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8));
            root.setBackground(rect(COLOR_PANEL_ALT, 2.0f, 1, COLOR_FIELD_BORDER));

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView titleView = label(context, title, 12.0f, COLOR_ACCENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            header.addView(titleView, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(26), 1.0f));
            TextView add = button(context, "+", COLOR_BUTTON, v -> addStack(""));
            header.addView(add, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(24)));
            root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));

            list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (initialStacks != null) {
                for (String stack : initialStacks) {
                    addStack(stack);
                }
            }
        }

        private OfferEditor offerEditor() {
            return offerEditor;
        }

        private int size() {
            return entries.size();
        }

        private void addStack(String stackJson) {
            StackEntryView entry = new StackEntryView(getContext(), stackJson, this);
            entries.add(entry);
            rebuildSlots();
        }

        private void insertStack(String stackJson, int index) {
            StackEntryView entry = new StackEntryView(getContext(), stackJson, this);
            entries.add(clampIndex(index, entries.size()), entry);
            rebuildSlots();
        }

        private void removeStack(StackEntryView entry) {
            entries.remove(entry);
            rebuildSlots();
        }

        private void moveEntryTo(StackEntryView entry, StackListEditor target, int targetIndex) {
            if (entry == null || target == null || !entries.contains(entry)) {
                return;
            }
            int oldIndex = entries.indexOf(entry);
            entries.remove(entry);
            int insertIndex = clampIndex(targetIndex, target.entries.size());
            if (target == this && oldIndex >= 0 && oldIndex < targetIndex) {
                insertIndex = Math.max(0, insertIndex - 1);
            }
            entry.setOwner(target);
            target.entries.add(insertIndex, entry);
            rebuildSlots();
            if (target != this) {
                target.rebuildSlots();
            }
        }

        private void clear() {
            entries.clear();
            list.removeAllViews();
        }

        private List<Object> toList() {
            List<Object> result = new ArrayList<>();
            for (StackEntryView entry : entries) {
                String stack = entry.stackJson();
                if (stack == null || stack.isBlank()) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("stack", stack);
                result.add(item);
            }
            return result;
        }

        private void rebuildSlots() {
            list.removeAllViews();
            LinearLayout row = null;
            for (int i = 0; i < entries.size(); i++) {
                if (i % MAX_SLOTS_PER_ROW == 0) {
                    row = new LinearLayout(getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(SLOT_SIZE_DP + 6));
                    list.addView(row, rowLp);
                }
                StackEntryView entry = entries.get(i);
                if (entry.getParent() instanceof ViewGroup parent) {
                    parent.removeView(entry);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(SLOT_SIZE_DP), UIUtils.dp2pxInt(SLOT_SIZE_DP));
                lp.rightMargin = UIUtils.dp2pxInt(6);
                row.addView(entry, lp);
            }
        }

        private DropTarget dropTargetAt(float rawX, float rawY, StackEntryView source) {
            for (int i = 0; i < entries.size(); i++) {
                StackEntryView entry = entries.get(i);
                if (entry == source) {
                    continue;
                }
                if (!isInsideRaw(entry, rawX, rawY)) {
                    continue;
                }
                int localX = rawToLocalX(entry, rawX);
                int insertionIndex = localX < entry.getWidth() / 2 ? i : i + 1;
                return new DropTarget(this, insertionIndex);
            }
            if (containsRawPoint(rawX, rawY)) {
                return new DropTarget(this, entries.size());
            }
            return null;
        }

        private boolean containsRawPoint(float rawX, float rawY) {
            return isInsideRaw(root, rawX, rawY);
        }

        private void setDropHighlighted(boolean highlighted) {
            root.setBackground(rect(
                    highlighted ? COLOR_DROP_HIGHLIGHT : COLOR_PANEL_ALT,
                    2.0f,
                    1,
                    highlighted ? COLOR_ACCENT : COLOR_FIELD_BORDER
            ));
        }

        private int clampIndex(int index, int size) {
            return Math.max(0, Math.min(index, size));
        }
    }

    private final class StackEntryView extends FrameLayout {
        private final Paint paint = new Paint();
        private final RectF rect = new RectF();
        private final InventoryItemPickerOverlay.ItemStackView stackView;
        private StackListEditor owner;
        private String stackJson;
        private ItemStack stack = ItemStack.EMPTY;
        private boolean rightClickPending;

        private StackEntryView(Context context, String stackJson, StackListEditor owner) {
            super(context);
            this.owner = owner;
            setWillNotDraw(false);
            setClipChildren(false);

            stackView = new InventoryItemPickerOverlay.ItemStackView(context, ItemStack.EMPTY, null, false);
            addView(stackView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            setStackJson(stackJson);
        }

        private String stackJson() {
            return stackJson;
        }

        private StackListEditor owner() {
            return owner;
        }

        private void setOwner(StackListEditor owner) {
            this.owner = owner;
        }

        private ItemStack stack() {
            return stack;
        }

        private void setStackJson(String value) {
            stackJson = value == null ? "" : value;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && !stackJson.isBlank()) {
                stack = ItemCodecUtils.fromJson(stackJson, mc.level.registryAccess());
            } else {
                stack = ItemStack.EMPTY;
            }
            stackView.setStack(stack);
            invalidate();
        }

        private void setCount(int count) {
            if (stack.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            ItemStack adjusted = stack.copy();
            adjusted.setCount(Math.max(1, count));
            setStackJson(ItemCodecUtils.toJson(adjusted, mc.level.registryAccess()));
        }

        private void removeFromOwner() {
            if (owner != null) {
                owner.removeStack(this);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float stroke = UIUtils.dp2px(1.0f);
            float radius = UIUtils.dp2px(2.0f);
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
        public boolean dispatchTouchEvent(MotionEvent event) {
            return onTouchEvent(event);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            stackView.dispatchGenericMotionEvent(event);
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                rightClickPending = isRightMouse(event);
                if (!rightClickPending) {
                    beginSlotGesture(this, event);
                }
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                updateSlotGesture(event);
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                boolean dragged = finishSlotGesture(event);
                if (rightClickPending || isRightMouse(event)) {
                    showSlotMenu(this, event.getRawX(), event.getRawY());
                } else if (!dragged && matchesClearSlotShortcut(event)) {
                    removeFromOwner();
                } else if (!dragged) {
                    openPicker();
                } else {
                    requestFocus();
                }
                rightClickPending = false;
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                rightClickPending = false;
                cancelSlotDrag();
                return true;
            }
            return true;
        }

        private void openPicker() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            openInventoryPicker(pickedStack -> {
                if (mc.level != null) {
                    setStackJson(ItemCodecUtils.toJson(pickedStack, mc.level.registryAccess()));
                }
            });
        }
    }

    private final class StackSlotMenu extends FrameLayout {
        private static final int COLOR_MENU_BG = DialogueHudTheme.PANEL;
        private static final int COLOR_MENU_BORDER = DialogueHudTheme.DIVIDER;
        private static final int COLOR_MENU_HOVER = DialogueHudTheme.BUTTON_HOVER;
        private static final int COLOR_MENU_TEXT = DialogueHudTheme.TEXT_PRIMARY;
        private static final int COLOR_SHORTCUT = DialogueHudTheme.TEXT_MUTED;

        private final StackEntryView entry;
        private final LinearLayout panel;

        private StackSlotMenu(Context context, StackEntryView entry) {
            super(context);
            this.entry = entry;
            setOnClickListener(v -> closeSlotMenu());

            panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8));
            panel.setBackground(rect(COLOR_MENU_BG, 3.0f, 1, COLOR_MENU_BORDER));
            panel.setOnClickListener(v -> {
            });
            addView(panel);

            addMenuItem(tr("geometry_node.shop.editor.menu.adjust_count"), "", () -> showQuantityDialog(entry));
            addMenuItem(tr("geometry_node.common.clear"), clearSlotShortcutText(), entry::removeFromOwner);
        }

        private void showAt(float rawX, float rawY) {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            layoutPanel(rawX - loc[0], rawY - loc[1]);
        }

        private void dismiss() {
            if (getParent() instanceof ViewGroup parent) {
                parent.removeView(this);
            }
        }

        private void addMenuItem(String text, String shortcut, Runnable action) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
            row.setOnClickListener(v -> {
                closeSlotMenu();
                if (action != null) {
                    action.run();
                }
            });

            TextView label = label(getContext(), text, 12.0f, COLOR_MENU_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

            TextView shortcutView = label(getContext(), shortcut == null ? "" : shortcut, 10.0f, COLOR_SHORTCUT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.addView(shortcutView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

            row.setOnHoverListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    row.setBackground(rect(COLOR_MENU_HOVER, 2.0f, 0, 0));
                    label.setTextColor(0xFFFFFFFF);
                    shortcutView.setTextColor(0xFFFFFFFF);
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    row.setBackground(null);
                    label.setTextColor(COLOR_MENU_TEXT);
                    shortcutView.setTextColor(COLOR_SHORTCUT);
                }
                return false;
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(26));
            panel.addView(row, lp);
        }

        private void layoutPanel(float x, float y) {
            int menuWidth = UIUtils.dp2pxInt(MENU_WIDTH_DP);
            int widthSpec = MeasureSpec.makeMeasureSpec(menuWidth, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            panel.measure(widthSpec, heightSpec);
            int menuHeight = panel.getMeasuredHeight();
            if (menuHeight <= 0) {
                menuHeight = UIUtils.dp2pxInt(68);
            }
            int edge = UIUtils.dp2pxInt(6);
            int targetX = Math.round(x);
            int targetY = Math.round(y);
            if (getWidth() > 0 && targetX + menuWidth + edge > getWidth()) {
                targetX = Math.max(edge, getWidth() - menuWidth - edge);
            }
            if (getHeight() > 0 && targetY + menuHeight + edge > getHeight()) {
                targetY = Math.max(edge, targetY - menuHeight);
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = Math.max(edge, targetX);
            lp.topMargin = Math.max(edge, targetY);
            panel.setLayoutParams(lp);
        }
    }

    private final class QuantityDialog extends FrameLayout {
        private final StackEntryView entry;
        private final EditText input;

        private QuantityDialog(Context context, StackEntryView entry) {
            super(context);
            this.entry = entry;
            setBackground(rect(DialogueHudTheme.OVERLAY_DIM, 0.0f, 0, 0));
            setFocusable(true);
            setFocusableInTouchMode(true);
            setOnClickListener(v -> closeQuantityDialog());
            setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ESCAPE) {
                    closeQuantityDialog();
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                    apply();
                    return true;
                }
                return false;
            });

            LinearLayout panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(14));
            panel.setBackground(rect(COLOR_WINDOW, 3.0f, 1, COLOR_BORDER));
            panel.setOnClickListener(v -> {
            });

            TextView title = label(context, tr("geometry_node.shop.editor.quantity.title"), 14.0f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));

            input = field(context, String.valueOf(Math.max(1, entry.stack().getCount())), Gravity.CENTER);
            panel.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32)));

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            actions.setPadding(0, UIUtils.dp2pxInt(12), 0, 0);
            actions.addView(UiActionButton.create(context, tr("geometry_node.common.cancel"),
                    UiActionButton.Role.SECONDARY, v -> closeQuantityDialog()),
                    new LinearLayout.LayoutParams(UIUtils.dp2pxInt(72), UIUtils.dp2pxInt(28)));
            TextView spacer = label(context, "", 1.0f, 0, Gravity.CENTER);
            actions.addView(spacer, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(8), 1));
            actions.addView(UiActionButton.create(context, tr("geometry_node.common.confirm"),
                    UiActionButton.Role.PRIMARY, v -> apply()),
                    new LinearLayout.LayoutParams(UIUtils.dp2pxInt(72), UIUtils.dp2pxInt(28)));
            panel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(42)));

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(260), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            addView(panel, lp);
        }

        private void focusInput() {
            post(() -> {
                requestFocus();
                input.requestFocus();
                input.selectAll();
            });
        }

        private void apply() {
            int count = Math.max(1, intValue(input.getText().toString(), 1));
            entry.setCount(count);
            closeQuantityDialog();
        }

        private void dismiss() {
            if (getParent() instanceof ViewGroup parent) {
                parent.removeView(this);
            }
        }
    }
}
