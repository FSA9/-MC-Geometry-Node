package com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShopEditorOverlay extends FrameLayout {
    private static final int COLOR_DIM = 0x99000000;
    private static final int COLOR_WINDOW = 0xFF20242C;
    private static final int COLOR_PANEL = 0xFF272C35;
    private static final int COLOR_PANEL_ALT = 0xFF1B1F26;
    private static final int COLOR_FIELD = 0xFF12151B;
    private static final int COLOR_BORDER = 0xFF3D4654;
    private static final int COLOR_TEXT = 0xFFE8EDF6;
    private static final int COLOR_MUTED = 0xFF9AA5B5;
    private static final int COLOR_BUTTON = 0xFF384150;
    private static final int COLOR_PRIMARY = 0xFF3D6EA8;
    private static final int COLOR_DANGER = 0xFF7C3F46;
    private static final int COLOR_ACCENT = 0xFFE0A84E;

    private static final int WINDOW_MARGIN_DP = 34;
    private static final int OFFER_MIN_HEIGHT_DP = 178;
    private static final int SLOT_SIZE_DP = 38;
    private static final int MAX_SLOTS_PER_ROW = 6;

    private static ShopEditorOverlay sOpenOverlay;

    private final EditorContext mEditorContext;
    private final NodeData mNodeData;
    private final String mPortId;
    private final LinearLayout mOfferList;
    private final List<OfferEditor> mOfferEditors = new ArrayList<>();
    private InventoryItemPickerOverlay mInventoryPicker;

    private ShopEditorOverlay(Context context, EditorContext editorContext, NodeData nodeData, String portId) {
        super(context);
        this.mEditorContext = editorContext;
        this.mNodeData = nodeData;
        this.mPortId = portId;

        setBackground(rect(COLOR_DIM, 0.0f, 0, 0));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnClickListener(v -> dismiss());

        LinearLayout window = new LinearLayout(context);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setPadding(UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(14));
        window.setBackground(rect(COLOR_WINDOW, 6.0f, 1, COLOR_BORDER));
        window.setOnClickListener(v -> {
        });

        window.addView(createHeader(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(38)));

        ScrollView scrollView = new ScrollView(context);
        mOfferList = new LinearLayout(context);
        mOfferList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mOfferList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        window.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        window.addView(createActions(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(40)));

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

        TextView title = label(context, "编辑商店", 15.0f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView add = button(context, "+ 交易", COLOR_PRIMARY, v -> addOffer(defaultOffer()));
        header.addView(add, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(84), UIUtils.dp2pxInt(30)));
        return header;
    }

    private View createActions(Context context) {
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, UIUtils.dp2pxInt(10), 0, 0);

        actions.addView(button(context, "取消", COLOR_BUTTON, v -> dismiss()), new LinearLayout.LayoutParams(UIUtils.dp2pxInt(82), UIUtils.dp2pxInt(30)));
        TextView spacer = label(context, "", 1.0f, 0, Gravity.CENTER);
        actions.addView(spacer, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(10), 1));
        actions.addView(button(context, "保存", COLOR_PRIMARY, v -> commit()), new LinearLayout.LayoutParams(UIUtils.dp2pxInt(90), UIUtils.dp2pxInt(30)));
        return actions;
    }

    private void loadExistingData() {
        Object raw = mNodeData.inputs != null ? mNodeData.inputs.get(mPortId) : null;
        List<OfferState> offers = parseOffers(raw);
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
        List<Object> offers = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (int i = 0; i < mOfferEditors.size(); i++) {
            Map<String, Object> offer = mOfferEditors.get(i).toMap(usedIds, i + 1);
            if (offer != null) {
                offers.add(offer);
            }
        }
        shopData.put("offers", offers);
        UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId, shopData);
        dismiss();
    }

    private void dismiss() {
        closeInventoryPicker();
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

    private static List<OfferState> parseOffers(Object raw) {
        List<OfferState> result = new ArrayList<>();
        if (!(raw instanceof Map<?, ?> root)) {
            return result;
        }
        Object offersObj = root.get("offers");
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
            result.add(new OfferState(id, title, maxUses, parseStacks(offerMap.get("costs")), parseStacks(offerMap.get("rewards"))));
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
        return new OfferState("", "", 0, List.of(), List.of());
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

    private static EditText field(Context context, String value, int gravity) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setText(value == null ? "" : value);
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(12.0f);
        input.setGravity(gravity);
        input.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        input.setBackground(rect(COLOR_FIELD, 3.0f, 1, 0xFF323A46));
        return input;
    }

    private static TextView label(Context context, String text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(context, text, sizeDp, color);
        view.setGravity(gravity);
        return view;
    }

    private static TextView button(Context context, String text, int color, View.OnClickListener listener) {
        TextView view = label(context, text, 12.5f, 0xFFFFFFFF, Gravity.CENTER);
        view.setBackground(rect(color, 4.0f, 1, 0x553C4658));
        view.setOnClickListener(listener);
        return view;
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

    private record OfferState(String id, String title, int maxUses, List<String> costs, List<String> rewards) {
    }

    private final class OfferEditor {
        private final LinearLayout root;
        private final TextView header;
        private final EditText idInput;
        private final EditText titleInput;
        private final EditText maxUsesInput;
        private final StackListEditor costs;
        private final StackListEditor rewards;

        private OfferEditor(Context context, OfferState state) {
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(12));
            root.setMinimumHeight(UIUtils.dp2pxInt(OFFER_MIN_HEIGHT_DP));
            root.setBackground(rect(COLOR_PANEL, 5.0f, 1, COLOR_BORDER));

            LinearLayout headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            header = label(context, "交易", 13.5f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            headerRow.addView(header, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(30), 1.0f));
            TextView remove = button(context, "删除", COLOR_DANGER, v -> removeOffer(this));
            headerRow.addView(remove, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(64), UIUtils.dp2pxInt(28)));
            root.addView(headerRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32)));

            LinearLayout fields = new LinearLayout(context);
            fields.setOrientation(LinearLayout.HORIZONTAL);
            fields.setGravity(Gravity.CENTER_VERTICAL);
            idInput = field(context, state.id(), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            titleInput = field(context, state.title(), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            maxUsesInput = field(context, String.valueOf(Math.max(0, state.maxUses())), Gravity.CENTER);
            addField(fields, context, "ID", idInput, 0, 1.1f);
            addField(fields, context, "标题", titleInput, UIUtils.dp2pxInt(8), 1.4f);
            addField(fields, context, "次数", maxUsesInput, UIUtils.dp2pxInt(8), 0.55f);
            root.addView(fields, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

            LinearLayout tradeRow = new LinearLayout(context);
            tradeRow.setOrientation(LinearLayout.HORIZONTAL);
            tradeRow.setPadding(0, UIUtils.dp2pxInt(10), 0, 0);
            costs = new StackListEditor(context, "买入", state.costs());
            rewards = new StackListEditor(context, "卖出", state.rewards());
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            leftLp.rightMargin = UIUtils.dp2pxInt(8);
            tradeRow.addView(costs.root, leftLp);
            tradeRow.addView(rewards.root, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            root.addView(tradeRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private void setIndex(int index) {
            header.setText("交易 " + index);
            if (idInput.getText().toString().trim().isEmpty()) {
                idInput.setText("trade_" + index);
            }
        }

        private void clear() {
            idInput.setText("trade_1");
            titleInput.setText("");
            maxUsesInput.setText("0");
            costs.clear();
            rewards.clear();
        }

        private Map<String, Object> toMap(Set<String> usedIds, int index) {
            Map<String, Object> offer = new LinkedHashMap<>();
            offer.put("id", normalizeOfferId(idInput.getText().toString(), usedIds, index));
            offer.put("title", titleInput.getText().toString().trim());
            offer.put("costs", costs.toList());
            offer.put("rewards", rewards.toList());
            offer.put("max_uses", Math.max(0, intValue(maxUsesInput.getText().toString(), 0)));
            return offer;
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
    }

    private final class StackListEditor {
        private final LinearLayout root;
        private final LinearLayout list;
        private final List<StackEntryView> entries = new ArrayList<>();

        private StackListEditor(Context context, String title, List<String> initialStacks) {
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(7), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8));
            root.setBackground(rect(COLOR_PANEL_ALT, 4.0f, 1, 0xFF303846));

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

        private void addStack(String stackJson) {
            StackEntryView entry = new StackEntryView(getContext(), stackJson, this::removeStack);
            entries.add(entry);
            rebuildSlots();
        }

        private void removeStack(StackEntryView entry) {
            entries.remove(entry);
            rebuildSlots();
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
    }

    private final class StackEntryView extends FrameLayout {
        private final Paint paint = new Paint();
        private final RectF rect = new RectF();
        private final StackRemoveHandler removeHandler;
        private final InventoryItemPickerOverlay.ItemStackView stackView;
        private String stackJson;
        private ItemStack stack = ItemStack.EMPTY;
        private boolean rightClickPending;

        private StackEntryView(Context context, String stackJson, StackRemoveHandler removeHandler) {
            super(context);
            this.removeHandler = removeHandler;
            setWillNotDraw(false);
            setClipChildren(false);

            stackView = new InventoryItemPickerOverlay.ItemStackView(context, ItemStack.EMPTY, null, false);
            addView(stackView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            setStackJson(stackJson);
        }

        private String stackJson() {
            return stackJson;
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

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float stroke = UIUtils.dp2px(1.0f);
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_FIELD);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, UIUtils.dp2px(4.0f), UIUtils.dp2px(4.0f), UIUtils.dp2px(4.0f), UIUtils.dp2px(4.0f), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(stack.isEmpty() ? 0xFF4E5664 : COLOR_ACCENT);
            rect.set(stroke / 2.0f, stroke / 2.0f, w - stroke / 2.0f, h - stroke / 2.0f);
            canvas.drawRoundRect(rect, UIUtils.dp2px(4.0f), UIUtils.dp2px(4.0f), UIUtils.dp2px(4.0f), UIUtils.dp2px(4.0f), paint);
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
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                if (rightClickPending || isRightMouse(event)) {
                    removeHandler.remove(this);
                } else {
                    openPicker();
                }
                rightClickPending = false;
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                rightClickPending = false;
                return true;
            }
            return true;
        }

        private boolean isRightMouse(MotionEvent event) {
            return (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                    || event.getActionButton() == MotionEvent.BUTTON_SECONDARY;
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

    private interface StackRemoveHandler {
        void remove(StackEntryView entry);
    }
}
