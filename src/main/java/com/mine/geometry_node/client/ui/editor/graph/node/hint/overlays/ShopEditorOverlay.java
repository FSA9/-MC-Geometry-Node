package com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays;

import com.mine.geometry_node.client.ui.components.composite.overlay.UiOverlayHosts;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect;

import com.mine.geometry_node.client.ui.components.base.overlay.UiInputBarrier;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ShopEditorData.OfferDraft;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ShopEditorData.OfferState;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ShopEditorData.ShopState;
import static com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ShopEditorData.defaultOffer;
import static com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ShopEditorData.intValue;
import static com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ShopEditorData.parseShopState;
import static com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ShopEditorData.validConditionId;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.feature.graph.edit.EditorContext;
import com.mine.geometry_node.client.ui.components.integration.overlay.InventoryItemPickerOverlay;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ValuePreviewStyle;
import com.mine.geometry_node.client.ui.overlay.OverlayCloseReason;
import com.mine.geometry_node.client.ui.overlay.OverlayHandle;
import com.mine.geometry_node.client.ui.overlay.OverlayMount;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.input.binding.InputBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.dialogue.OpenShop;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
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
import java.util.List;

public final class ShopEditorOverlay extends UiInputBarrier {
    // This is a live shop-style preview: retain the warm HUD palette instead of editor surfaces.
    private static final int COLOR_DIM = UiThemes.HUD.surface().overlayDim();
    private static final int COLOR_WINDOW = UiThemes.HUD.surface().root();
    private static final int COLOR_PANEL = UiThemes.HUD.surface().panel();
    private static final int COLOR_PANEL_ALT = UiThemes.HUD.buttons().secondary().pressed();
    private static final int COLOR_FIELD = UiTheme.withAlpha(UiThemes.HUD.surface().root(), 0xFF);
    private static final int COLOR_BORDER = UiThemes.HUD.border().divider();
    private static final int COLOR_TEXT = UiThemes.HUD.text().normal();
    private static final int COLOR_MUTED = UiThemes.HUD.text().muted();
    private static final int COLOR_ACCENT = UiThemes.HUD.buttons().primary().normal();
    private static final int COLOR_FIELD_BORDER = UiTheme.withAlpha(UiThemes.HUD.text().muted(), 0x44);
    private static final int COLOR_DROP_HIGHLIGHT = UiTheme.withAlpha(UiThemes.HUD.buttons().primary().normal(), 0x22);
    // Shared item previews default to editor blue; the shop preview explicitly supplies its warm palette.
    private static final ValuePreviewStyle SLOT_PREVIEW_STYLE = new ValuePreviewStyle(
            2.0f, 1.0f, COLOR_FIELD, UiThemes.HUD.border().divider(), UiThemes.HUD.border().focus());

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
    private OverlayHandle mOverlayHandle;
    private boolean mClosed;

    private ShopEditorOverlay(Context context, EditorContext editorContext, NodeData nodeData, String portId) {
        super(context);
        this.mEditorContext = editorContext;
        this.mNodeData = nodeData;
        this.mPortId = portId;
        this.mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setBackground(rect(COLOR_DIM, 0.0f, 0, 0));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnClickListener(v -> dismiss(OverlayCloseReason.OUTSIDE_CLICK));

        LinearLayout window = new LinearLayout(context);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setPadding(UIUtils.dp2pxInt(18), UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(18), UIUtils.dp2pxInt(14));
        window.setBackground(rect(COLOR_WINDOW, 3.0f, 1, COLOR_BORDER));
        window.setOnClickListener(v -> {
        });

        window.addView(createHeader(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(44)));
        window.addView(createDivider(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1)));

        ScrollView scrollView = new UiScrollView(context, UiThemes.HUD.scroll());
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
        post(() -> {
            if (!mClosed) requestFocus();
        });
    }

    public static void show(View anchor, EditorContext editorContext, NodeData nodeData, PortRow row) {
        if (anchor == null || editorContext == null || nodeData == null) {
            return;
        }
        ViewGroup host = UiOverlayHosts.findWindowHost(anchor);
        if (host == null) {
            return;
        }
        String portId = row != null && row.leftPort() != null
                ? row.leftPort().id()
                : StandardPorts.SHOP_DATA.getId();

        ShopEditorOverlay overlay = new ShopEditorOverlay(anchor.getContext(), editorContext, nodeData, portId);
        closeOpenOverlay();
        overlay.mOverlayHandle = OverlayMount.modal(anchor, overlay, overlay::closeTopInternalOverlay, overlay::onClosed);
        if (overlay.mOverlayHandle == null) {
            host.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
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
            if (!closeTopInternalOverlay()) dismiss(OverlayCloseReason.ESCAPE);
            return true;
        }
        super.dispatchKeyEvent(event);
        return true;
    }

    private boolean closeTopInternalOverlay() {
        if (mQuantityDialog != null) {
            closeQuantityDialog();
        } else if (mSlotMenu != null) {
            closeSlotMenu();
        } else if (mConditionMenu != null) {
            closeConditionMenu();
        } else if (mInventoryPicker != null) {
            closeInventoryPicker();
        } else {
            return false;
        }
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

        TextView add = button(context, tr("geometry_node.shop.editor.add_offer"), UiActionButton.Role.PRIMARY, v -> addOffer(defaultOffer()));
        header.addView(add, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(84), UIUtils.dp2pxInt(30)));
        return header;
    }

    private View createActions(Context context) {
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, UIUtils.dp2pxInt(8), 0, 0);

        actions.addView(UiActionButton.createThemed(context, UiThemes.HUD, tr("geometry_node.common.cancel"),
                UiActionButton.Role.SECONDARY, v -> dismiss()),
                new LinearLayout.LayoutParams(UIUtils.dp2pxInt(82), UIUtils.dp2pxInt(32)));
        TextView spacer = label(context, "", 1.0f, 0, Gravity.CENTER);
        actions.addView(spacer, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(10), 1));
        actions.addView(UiActionButton.createThemed(context, UiThemes.HUD, tr("geometry_node.common.save"),
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
        if (mClosed) return;
        List<OfferDraft> offers = new ArrayList<>();
        for (OfferEditor editor : mOfferEditors) offers.add(editor.toDraft());
        UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId,
                ShopEditorData.encode(offers, conditionPorts()));
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

    private void dismiss() {
        dismiss(OverlayCloseReason.PROGRAMMATIC);
    }

    private void dismiss(OverlayCloseReason reason) {
        if (mClosed) return;
        if (mOverlayHandle != null) {
            mOverlayHandle.requestClose(reason);
            return;
        }
        onClosed(reason);
        if (getParent() instanceof ViewGroup parent) {
            parent.removeView(this);
            parent.requestFocus();
        }
    }

    private void onClosed(OverlayCloseReason reason) {
        if (mClosed) return;
        mClosed = true;
        mOverlayHandle = null;
        closeInventoryPicker();
        closeSlotMenu();
        closeConditionMenu();
        closeQuantityDialog();
        cancelSlotDrag();
        if (sOpenOverlay == this) {
            sOpenOverlay = null;
        }
    }

    private static void closeOpenOverlay() {
        if (sOpenOverlay != null) {
            sOpenOverlay.dismiss(OverlayCloseReason.REPLACED);
        }
    }

    private void openInventoryPicker(java.util.function.Consumer<ItemStack> onPicked) {
        closeInventoryPicker();
        closeConditionMenu();
        mInventoryPicker = InventoryItemPickerOverlay.showIn(this, UiThemes.HUD, stack -> {
            if (!mClosed && onPicked != null) {
                onPicked.accept(stack.copy());
            }
        }, () -> {
            mInventoryPicker = null;
            if (!mClosed) requestFocus();
        });
    }

    private void closeInventoryPicker() {
        if (mInventoryPicker == null) {
            return;
        }
        mInventoryPicker.dismiss();
        mInventoryPicker = null;
        if (!mClosed) requestFocus();
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
        if (!mClosed) requestFocus();
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
        if (!mClosed) requestFocus();
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
        if (!mClosed) requestFocus();
    }

    private String clearSlotShortcutText() {
        return ConfigManager.INSTANCE.get(BuiltinConfigEntries.SHOP_CLEAR_SLOT);
    }

    private boolean matchesClearSlotShortcut(MotionEvent event) {
        InputBinding binding = InputBinding.parse(clearSlotShortcutText());
        return binding != null && UiInputBindings.matches(binding, event);
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



    private static EditText field(Context context, String value, int gravity) {
        EditText input = new UiTextInput(context, new UiTextInput.Style(11, 8,
                UiMetrics.RADIUS_INPUT, UiMetrics.BORDER, COLOR_TEXT, COLOR_MUTED,
                COLOR_FIELD, COLOR_FIELD_BORDER, UiThemes.HUD.border().focus(), UiThemes.HUD.status().errorBorder(),
                UiThemes.HUD.selection().text(), UiMetrics.DISABLED_ALPHA));
        input.setSingleLine(true);
        input.setText(value == null ? "" : value);
        input.setTextColor(COLOR_TEXT);
        UIUtils.setLockedTextSize(input, 12.0f);
        input.setGravity(gravity);
        input.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        return input;
    }

    private static TextView label(Context context, String text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(context, text, sizeDp, color);
        view.setGravity(gravity);
        return view;
    }

    private static UiActionButton button(Context context, String text, UiActionButton.Role role,
                                         View.OnClickListener listener) {
        UiActionButton button = UiActionButton.createThemed(context, UiThemes.HUD, text, role,
                UiActionButton.Density.NORMAL,
                new UiActionButton.Style(2, 0, 0, UiMetrics.DISABLED_ALPHA, UiThemes.HUD.border().focus()),
                listener);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        UIUtils.setLockedTextSize(button, 12.5f);
        return button;
    }

    private static LinearLayout slotMenuRow(Context context, String text, String shortcut, Runnable action) {
        LinearLayout row = new LinearLayout(context) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        UiMenuItems.decorate(row, false, UiThemes.HUD);
        ((ShapeDrawable) row.getBackground()).setCornerRadius(UIUtils.dp2px(2));
        row.setOnClickListener(v -> action.run());

        TextView label = label(context, text, 12.0f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        label.setDuplicateParentStateEnabled(true);
        label.setTextColor(UiMenuItems.textColors(COLOR_TEXT, UiThemes.HUD));
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView shortcutView = label(context, shortcut == null ? "" : shortcut, 10.0f, COLOR_MUTED,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        shortcutView.setDuplicateParentStateEnabled(true);
        shortcutView.setTextColor(UiMenuItems.textColors(COLOR_MUTED, UiThemes.HUD));
        row.addView(shortcutView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
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
            TextView remove = button(context, tr("geometry_node.common.delete"), UiActionButton.Role.DANGER, v -> removeOffer(this));
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

        private OfferDraft toDraft() {
            return new OfferDraft(idInput.getText().toString(), titleInput.getText().toString(),
                    maxUsesInput.getText().toString(), infiniteUses.isChecked(),
                    consumeSellerItems.isChecked(), sellerReceivesPayment.isChecked(),
                    visibleCondition.conditionId(), enabledCondition.conditionId(),
                    disabledReasonInput.getText().toString(), costs.snapshot(), rewards.snapshot());
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
        private final UiSelectButton view;
        private final String label;
        private final Runnable onChanged;
        private String conditionId;

        private ConditionSelector(Context context, String label, String conditionId, Runnable onChanged) {
            this.view = new UiSelectButton(context, "", 11.5f, () -> 2, UiThemes.HUD);
            this.label = label;
            this.onChanged = onChanged;
            this.conditionId = conditionId == null ? "" : conditionId;
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
            String displayValue = validId.isBlank() ? tr("geometry_node.common.none") : validId;
            view.setValue(displayValue);
            view.setContentDescription(displayValue);
        }
    }

    private final class ConditionDropdownMenu extends UiInputBarrier {
        private static final int COLOR_MENU_BG = UiThemes.HUD.surface().root();
        private static final int COLOR_MENU_BORDER = UiThemes.HUD.border().divider();

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
            TextView row = UiMenuItems.row(getContext(), labelText, value.equals(selector.conditionId()), true, () -> {
                selector.setConditionId(value);
                closeConditionMenu();
            }, UiThemes.HUD);
            ((ShapeDrawable) row.getBackground()).setCornerRadius(UIUtils.dp2px(2));
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
        private final UiActionButton view;
        private final String label;
        private final Runnable onChanged;
        private boolean checked;

        private ToggleControl(Context context, String label, boolean checked, Runnable onChanged) {
            this.label = label;
            this.onChanged = onChanged;
            this.view = button(context, label, UiActionButton.Role.SECONDARY, v -> {
                setChecked(!this.checked);
                if (this.onChanged != null) {
                    this.onChanged.run();
                }
            });
            UIUtils.setLockedTextSize(view, 11.5f);
            setChecked(checked);
        }

        private boolean isChecked() {
            return checked;
        }

        private void setChecked(boolean checked) {
            this.checked = checked;
            view.setText(label);
            view.setSelected(checked);
            view.setPalette(UiActionButton.Palette.forRole(UiThemes.HUD,
                    checked ? UiActionButton.Role.PRIMARY : UiActionButton.Role.SECONDARY));
            view.setStyle(new UiActionButton.Style(2, 1,
                    checked ? UiThemes.HUD.selection().outline() : COLOR_FIELD_BORDER,
                    UiMetrics.DISABLED_ALPHA, UiThemes.HUD.border().focus()));
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
            TextView add = button(context, "+", UiActionButton.Role.SECONDARY, v -> addStack(""));
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
            int insertIndex = insertionIndexAfterRemoval(
                    target == this ? oldIndex : -1, targetIndex, target.entries.size());
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

        private List<String> snapshot() {
            List<String> result = new ArrayList<>();
            for (StackEntryView entry : entries) result.add(entry.stackJson());
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

    static int insertionIndexAfterRemoval(int oldIndex, int targetIndex, int remainingSize) {
        int adjusted = oldIndex >= 0 && oldIndex < targetIndex ? targetIndex - 1 : targetIndex;
        return Math.max(0, Math.min(adjusted, remainingSize));
    }

    private final class StackEntryView extends FrameLayout {
        private final InventoryItemPickerOverlay.ItemStackView stackView;
        private StackListEditor owner;
        private String stackJson;
        private ItemStack stack = ItemStack.EMPTY;
        private boolean rightClickPending;

        private StackEntryView(Context context, String stackJson, StackListEditor owner) {
            super(context);
            this.owner = owner;
            setClipChildren(false);

            stackView = new InventoryItemPickerOverlay.ItemStackView(
                    context, ItemStack.EMPTY, null, false, 1.0f, SLOT_PREVIEW_STYLE, UiThemes.HUD.buttons().primary().normal(), UiThemes.HUD);
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

    private final class StackSlotMenu extends UiInputBarrier {
        private static final int COLOR_MENU_BG = UiThemes.HUD.surface().root();
        private static final int COLOR_MENU_BORDER = UiThemes.HUD.border().divider();

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
            LinearLayout row = slotMenuRow(getContext(), text, shortcut, () -> {
                closeSlotMenu();
                if (action != null) {
                    action.run();
                }
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

    private final class QuantityDialog extends UiInputBarrier {
        private final StackEntryView entry;
        private final EditText input;

        private QuantityDialog(Context context, StackEntryView entry) {
            super(context);
            this.entry = entry;
            setBackground(rect(UiThemes.HUD.surface().overlayDim(), 0.0f, 0, 0));
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
            actions.addView(UiActionButton.createThemed(context, UiThemes.HUD, tr("geometry_node.common.cancel"),
                    UiActionButton.Role.SECONDARY, v -> closeQuantityDialog()),
                    new LinearLayout.LayoutParams(UIUtils.dp2pxInt(72), UIUtils.dp2pxInt(28)));
            TextView spacer = label(context, "", 1.0f, 0, Gravity.CENTER);
            actions.addView(spacer, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(8), 1));
            actions.addView(UiActionButton.createThemed(context, UiThemes.HUD, tr("geometry_node.common.confirm"),
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
