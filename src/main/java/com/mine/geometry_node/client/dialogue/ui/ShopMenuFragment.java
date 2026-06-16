package com.mine.geometry_node.client.dialogue.ui;

import com.mine.geometry_node.client.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.dialogue.ModernDialogueText;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.InventoryItemPickerOverlay;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.ItemStackTooltipOverlay;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.OnBackPressedCallback;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client-side shop menu opened by the OpenShop dialogue node.
 */
public class ShopMenuFragment extends Fragment {
    private static final int COLOR_DIM = 0xA0000000;
    private static final int COLOR_WINDOW = 0xFF181C22;
    private static final int COLOR_ROW = 0xFF222832;
    private static final int COLOR_ROW_HOVER = 0xFF293240;
    private static final int COLOR_FIELD = 0xFF11151A;
    private static final int COLOR_BORDER = 0xFF384253;
    private static final int COLOR_TEXT = 0xFFE9EEF6;
    private static final int COLOR_MUTED = 0xFF97A2B2;
    private static final int COLOR_PRIMARY = 0xFF2F6FAE;
    private static final int COLOR_BUTTON = 0xFF303846;
    private static final int COLOR_DISABLED = 0xFF1D222A;
    private static final int COLOR_SUCCESS = 0xFF73C68A;
    private static final int COLOR_ERROR = 0xFFE07B7B;
    private static final int COLOR_ACCENT = 0xFFE0A84E;
    private static final int SLOT_SIZE_DP = 32;
    private static final int MAX_STACKS_INLINE = 5;

    private FrameLayout root;
    private LinearLayout window;
    private OnBackPressedCallback backPressedCallback;
    private boolean waitingForServer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = getContext();
        UIConstants.mDensity = context.getResources().getDisplayMetrics().density;

        root = new FrameLayout(context);
        root.setClipChildren(false);
        root.setBackground(rect(COLOR_DIM, 0.0f, 0, 0));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnClickListener(v -> {
        });
        root.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ESCAPE) {
                return ClientDialogueState.close();
            }
            return false;
        });

        window = new LinearLayout(context);
        window.setClipChildren(false);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setPadding(dp(14), dp(12), dp(14), dp(12));
        window.setBackground(rect(COLOR_WINDOW, 6.0f, 1, COLOR_BORDER));
        window.setOnClickListener(v -> {
        });

        FrameLayout.LayoutParams windowParams = new FrameLayout.LayoutParams(dp(720), ViewGroup.LayoutParams.WRAP_CONTENT);
        windowParams.gravity = Gravity.CENTER;
        root.addView(window, windowParams);

        refresh(ClientDialogueState.current());
        registerBackPressedCallback();
        root.post(root::requestFocus);
        return root;
    }

    @Override
    public void onDestroyView() {
        ItemStackTooltipOverlay.hide();
        if (backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }
        super.onDestroyView();
    }

    public void refresh(PacketOpenDialogue packet) {
        waitingForServer = false;
        ItemStackTooltipOverlay.hide();
        if (window == null) {
            return;
        }

        window.setAlpha(1.0f);
        window.removeAllViews();
        ShopState state = ShopState.from(packet);
        window.addView(createHeader(state), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setClipChildren(false);
        LinearLayout offerList = new LinearLayout(getContext());
        offerList.setClipChildren(false);
        offerList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(offerList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        window.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        if (state.offers().isEmpty()) {
            TextView empty = label(tr("geometry_node.shop.empty"), 13.0f, COLOR_MUTED, Gravity.CENTER);
            offerList.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));
        } else {
            for (int i = 0; i < state.offers().size(); i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
                lp.bottomMargin = dp(7);
                offerList.addView(createOfferRow(state.offers().get(i), i + 1), lp);
            }
        }

        window.addView(createFooter(packet, state), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
    }

    private View createHeader(ShopState state) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(state.title().isBlank() ? tr("geometry_node.shop.title.default") : state.title(), 17.0f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        titles.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        TextView subtitle = label(tr("geometry_node.shop.offer_count", state.offers().size()), 11.0f, COLOR_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        subtitle.setSingleLine(true);
        titles.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        return header;
    }

    private View createOfferRow(ShopOffer offer, int index) {
        LinearLayout row = new LinearLayout(getContext());
        row.setClipChildren(false);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(9), dp(7));
        row.setBackground(rect(COLOR_ROW, 5.0f, 1, COLOR_BORDER));
        bindHoverBackground(row);

        LinearLayout titleColumn = new LinearLayout(getContext());
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);
        String title = offer.title().isBlank() ? tr("geometry_node.shop.offer_index", index) : offer.title();
        TextView titleView = label(title, 12.5f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleColumn.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        TextView meta = label(offer.statusText(), 10.5f, offer.unavailable() ? COLOR_ERROR : COLOR_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        meta.setSingleLine(true);
        titleColumn.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        row.addView(titleColumn, new LinearLayout.LayoutParams(dp(128), ViewGroup.LayoutParams.MATCH_PARENT));

        row.addView(createStackStrip(offer.costs()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        TextView arrow = label(">", 15.0f, COLOR_ACCENT, Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(createStackStrip(offer.rewards()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView tradeButton = button(offer.buttonText(), offer.unavailable() ? COLOR_DISABLED : COLOR_PRIMARY, v -> tradeOffer(offer));
        tradeButton.setEnabled(!offer.soldOut());
        tradeButton.setTextColor(offer.unavailable() ? COLOR_MUTED : 0xFFFFFFFF);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(dp(76), dp(30));
        buttonLp.leftMargin = dp(10);
        row.addView(tradeButton, buttonLp);
        return row;
    }

    private View createStackStrip(List<ItemStack> stacks) {
        LinearLayout strip = new LinearLayout(getContext());
        strip.setClipChildren(false);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(6), 0, dp(4), 0);
        strip.setBackground(rect(COLOR_FIELD, 4.0f, 1, 0xFF2C3440));

        if (stacks == null || stacks.isEmpty()) {
            TextView empty = label(tr("geometry_node.common.none"), 11.0f, COLOR_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            strip.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return strip;
        }

        int shown = Math.min(stacks.size(), MAX_STACKS_INLINE);
        for (int i = 0; i < shown; i++) {
            InventoryItemPickerOverlay.ItemStackView slot = new InventoryItemPickerOverlay.ItemStackView(getContext(), stacks.get(i), null, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(SLOT_SIZE_DP), dp(SLOT_SIZE_DP));
            lp.rightMargin = dp(4);
            strip.addView(slot, lp);
        }
        if (stacks.size() > shown) {
            TextView more = label("+" + (stacks.size() - shown), 11.0f, COLOR_MUTED, Gravity.CENTER);
            strip.addView(more, new LinearLayout.LayoutParams(dp(30), dp(SLOT_SIZE_DP)));
        }
        return strip;
    }

    private View createFooter(PacketOpenDialogue packet, ShopState state) {
        LinearLayout footer = new LinearLayout(getContext());
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(12), 0, 0);

        String message = state.message();
        int messageColor = state.messageSuccess() ? COLOR_SUCCESS : COLOR_ERROR;
        TextView status = label(message, 11.5f, message.isBlank() ? COLOR_MUTED : messageColor, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        status.setSingleLine(true);
        footer.addView(status, new LinearLayout.LayoutParams(0, dp(30), 1.0f));

        TextView cancel = button(tr("geometry_node.common.cancel"), COLOR_BUTTON, v -> ClientDialogueState.close());
        footer.addView(cancel, new LinearLayout.LayoutParams(dp(78), dp(30)));

        TextView spacer = label("", 1.0f, 0, Gravity.CENTER);
        footer.addView(spacer, new LinearLayout.LayoutParams(dp(8), 1));

        PacketOpenDialogue.Choice choice = continueChoice(packet);
        String text = choice != null && choice.text() != null && !choice.text().isBlank()
                ? ModernDialogueText.plain(choice.text())
                : Component.translatable("geometry_node.dialogue.continue").getString();
        TextView continueButton = button(text, COLOR_PRIMARY, v -> chooseContinue(choice));
        continueButton.setEnabled(choice != null && choice.enabled());
        if (choice == null || !choice.enabled()) {
            continueButton.setTextColor(COLOR_MUTED);
            continueButton.setBackground(rect(COLOR_DISABLED, 4.0f, 1, 0xFF303846));
        }
        footer.addView(continueButton, new LinearLayout.LayoutParams(dp(92), dp(30)));
        return footer;
    }

    private void tradeOffer(ShopOffer offer) {
        if (waitingForServer || offer == null || offer.soldOut()) {
            return;
        }
        waitingForServer = ClientDialogueState.trade(offer.id());
        if (waitingForServer && window != null) {
            window.setAlpha(0.78f);
        }
    }

    private void chooseContinue(PacketOpenDialogue.Choice choice) {
        if (waitingForServer || choice == null || !choice.enabled()) {
            return;
        }
        waitingForServer = ClientDialogueState.choose(choice.choiceId());
        if (waitingForServer && window != null) {
            window.setAlpha(0.72f);
        }
    }

    private PacketOpenDialogue.Choice continueChoice(PacketOpenDialogue packet) {
        if (packet == null || packet.choices() == null || packet.choices().isEmpty()) {
            return null;
        }
        for (PacketOpenDialogue.Choice choice : packet.choices()) {
            if (choice != null && choice.metadata() != null && "continue".equals(String.valueOf(choice.metadata().get("role")))) {
                return choice;
            }
        }
        for (PacketOpenDialogue.Choice choice : packet.choices()) {
            if (choice != null && StandardPorts.FLOW_OUT.getId().equals(choice.choiceId())) {
                return choice;
            }
        }
        for (PacketOpenDialogue.Choice choice : packet.choices()) {
            if (choice != null && choice.defaultChoice()) {
                return choice;
            }
        }
        return packet.choices().get(0);
    }

    private void bindHoverBackground(View view) {
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                v.setBackground(rect(COLOR_ROW_HOVER, 5.0f, 1, 0xFF536175));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                v.setBackground(rect(COLOR_ROW, 5.0f, 1, COLOR_BORDER));
            }
            return false;
        });
    }

    private void registerBackPressedCallback() {
        if (backPressedCallback != null) {
            return;
        }
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ClientDialogueState.close();
            }
        };
        UIManager.getInstance().getOnBackPressedDispatcher().addCallback(backPressedCallback);
    }

    private TextView button(String text, int color, View.OnClickListener listener) {
        TextView view = label(text, 12.0f, 0xFFFFFFFF, Gravity.CENTER);
        view.setSingleLine(true);
        view.setBackground(rect(color, 4.0f, 1, 0x553C4658));
        view.setOnClickListener(listener);
        return view;
    }

    private TextView label(CharSequence text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(getContext(), "", sizeDp, color);
        view.setText(text == null ? "" : text);
        view.setGravity(gravity);
        return view;
    }

    private ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private int dp(float value) {
        return UIUtils.dp2pxInt(value);
    }

    private record ShopState(String title, String message, boolean messageSuccess, List<ShopOffer> offers) {
        private static ShopState from(PacketOpenDialogue packet) {
            if (packet == null) {
                return new ShopState("", "", true, List.of());
            }
            Map<String, Object> metadata = packet.metadata() == null ? Map.of() : packet.metadata();
            String title = stringValue(metadata.get("title"), packet.speaker());
            String messageKey = stringValue(metadata.get("last_trade_message_key"), "");
            String message = messageKey.isBlank()
                    ? stringValue(metadata.get("last_trade_message"), "")
                    : tr(messageKey);
            boolean success = boolValue(metadata.get("last_trade_success"), true);
            Map<?, ?> shopData = asMap(metadata.get("shop_data"));
            if (shopData == null) {
                shopData = metadata;
            }
            return new ShopState(title, message, success, parseOffers(shopData));
        }
    }

    private record ShopOffer(
            String id,
            String title,
            int maxUses,
            int uses,
            boolean consumeSellerItems,
            boolean sellerReceivesPayment,
            boolean enabled,
            String disabledReason,
            List<ItemStack> costs,
            List<ItemStack> rewards
    ) {
        private boolean unavailable() {
            return !enabled || soldOut();
        }

        private boolean soldOut() {
            return maxUses > 0 && uses >= maxUses;
        }

        private String buttonText() {
            if (soldOut()) {
                return tr("geometry_node.shop.sold_out");
            }
            return enabled ? tr("geometry_node.shop.trade") : tr("geometry_node.shop.unavailable");
        }

        private String statusText() {
            if (!enabled) {
                return disabledReason == null || disabledReason.isBlank() ? tr("geometry_node.shop.condition_not_met") : disabledReason;
            }
            List<String> parts = new ArrayList<>();
            parts.add(maxUses > 0
                    ? tr("geometry_node.shop.remaining_count", Math.max(0, maxUses - uses), maxUses)
                    : tr("geometry_node.shop.remaining_infinite"));
            if (consumeSellerItems) {
                parts.add(tr("geometry_node.shop.seller_inventory"));
            }
            if (sellerReceivesPayment) {
                parts.add(tr("geometry_node.shop.payment_to_seller"));
            }
            return String.join(" · ", parts);
        }
    }

    private static List<ShopOffer> parseOffers(Map<?, ?> shopData) {
        if (shopData == null) {
            return List.of();
        }
        Object offersObj = shopData.get("offers");
        if (!(offersObj instanceof List<?> offers)) {
            return List.of();
        }

        List<ShopOffer> result = new ArrayList<>();
        int index = 1;
        for (Object offerObj : offers) {
            Map<?, ?> offerMap = asMap(offerObj);
            if (offerMap == null) {
                continue;
            }
            String id = stringValue(offerMap.get("id"), "trade_" + index);
            String title = stringValue(offerMap.get("title"), "");
            int maxUses = intValue(offerMap.get("max_uses"), 0);
            int uses = Math.max(0, intValue(offerMap.get("uses"), 0));
            boolean consumeSellerItems = boolValue(offerMap.get("consume_seller_items"), false);
            boolean sellerReceivesPayment = boolValue(offerMap.get("seller_receives_payment"), false);
            boolean enabled = boolValue(offerMap.get("enabled"), true);
            String disabledReason = stringValue(offerMap.get("disabled_reason"), "");
            result.add(new ShopOffer(
                    id,
                    title,
                    maxUses,
                    uses,
                    consumeSellerItems,
                    sellerReceivesPayment,
                    enabled,
                    disabledReason,
                    parseStacks(offerMap.get("costs")),
                    parseStacks(offerMap.get("rewards"))
            ));
            index++;
        }
        return result;
    }

    private static List<ItemStack> parseStacks(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (Object item : list) {
            String stackJson = "";
            Map<?, ?> map = asMap(item);
            if (map != null) {
                stackJson = stringValue(map.get("stack"), "");
            } else if (item instanceof String string) {
                stackJson = string;
            }
            if (stackJson.isBlank()) {
                continue;
            }
            ItemStack stack = ItemCodecUtils.fromJson(stackJson, mc.level.registryAccess());
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String string) {
            return string;
        }
        return fallback == null ? "" : fallback;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
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
}
