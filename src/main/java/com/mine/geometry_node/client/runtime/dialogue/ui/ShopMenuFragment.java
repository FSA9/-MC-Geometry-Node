package com.mine.geometry_node.client.runtime.dialogue.ui;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ValuePreviewStyle;
import com.mine.geometry_node.client.runtime.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.runtime.dialogue.ModernDialogueText;
import com.mine.geometry_node.client.ui.components.base.icon.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.integration.overlay.InventoryItemPickerOverlay;
import com.mine.geometry_node.client.ui.components.integration.overlay.ItemStackTooltipOverlay;
import com.mine.geometry_node.core.engine.system.dialogue.model.shop.ShopPagePayload;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.OnBackPressedCallback;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.util.ColorStateList;
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

/**
 * Client-side shop menu opened by the OpenShop dialogue node.
 */
public class ShopMenuFragment extends Fragment {
    private static final int COLOR_DIM = UiThemes.HUD.surface().overlayDim();
    private static final int COLOR_WINDOW = UiThemes.HUD.surface().root();
    private static final int COLOR_ROW = UiThemes.HUD.surface().panel();
    private static final int COLOR_ROW_HOVER = UiThemes.HUD.surface().hover();
    private static final int COLOR_DIVIDER = UiThemes.HUD.border().divider();
    private static final int COLOR_TEXT = UiThemes.HUD.text().normal();
    private static final int COLOR_MUTED = UiThemes.HUD.text().muted();
    private static final int COLOR_ACCENT = UiThemes.HUD.buttons().primary().normal();
    private static final int COLOR_ACCENT_HOVER = UiThemes.HUD.buttons().primary().hover();
    private static final int COLOR_ACCENT_PRESSED = UiThemes.HUD.buttons().primary().pressed();
    private static final int COLOR_BUTTON = UiThemes.HUD.buttons().secondary().normal();
    private static final int COLOR_BUTTON_HOVER = UiThemes.HUD.buttons().secondary().hover();
    private static final int COLOR_BUTTON_PRESSED = UiThemes.HUD.buttons().secondary().pressed();
    private static final int COLOR_DISABLED = UiThemes.HUD.control().disabledBackground();
    private static final int COLOR_SUCCESS = UiThemes.HUD.status().success();
    private static final int COLOR_ERROR = UiThemes.HUD.status().error();
    private static final int COLOR_LIMIT = UiThemes.HUD.status().warning();
    private static final ValuePreviewStyle PREVIEW_STYLE = new ValuePreviewStyle(
            3.0f, 1.0f, UiThemes.HUD.surface().panel(), UiThemes.HUD.border().divider(), UiThemes.HUD.border().focus());
    private static final float DISPLAY_SCALE = 2.0f;
    private static final int WINDOW_WIDTH_DP = 696;
    private static final int TITLE_COLUMN_WIDTH_DP = 120;
    private static final int MIN_OFFER_ROW_HEIGHT_DP = 68;
    private static final int OFFER_ROW_VERTICAL_PADDING_DP = 14;
    private static final int SLOT_SIZE_DP = 34;
    private static final int SLOT_MARGIN_DP = 3;
    private static final int SLOT_ROW_HEIGHT_DP = SLOT_SIZE_DP + SLOT_MARGIN_DP;
    private static final int STACKS_PER_ROW = 5;

    private FrameLayout root;
    private LinearLayout window;
    private OnBackPressedCallback backPressedCallback;
    private boolean waitingForServer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = getContext();
        UIUtils.syncFixedDensity();

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
        window.setPadding(dp(18), dp(14), dp(18), dp(14));
        window.setBackground(rect(COLOR_WINDOW, 3.0f, 1, COLOR_DIVIDER));
        window.setOnClickListener(v -> {
        });

        FrameLayout.LayoutParams windowParams = new FrameLayout.LayoutParams(
                dp(WINDOW_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        windowParams.gravity = Gravity.CENTER;
        root.addView(window, windowParams);

        refresh(ClientDialogueState.current());
        registerBackPressedCallback();
        root.post(root::requestFocus);
        return root;
    }

    @Override
    public void onDestroyView() {
        ItemStackTooltipOverlay.hideWithin(root);
        if (backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }
        super.onDestroyView();
    }

    public void refresh(PacketOpenDialogue packet) {
        waitingForServer = false;
        ItemStackTooltipOverlay.hideWithin(window);
        if (window == null) {
            return;
        }

        window.setAlpha(1.0f);
        window.removeAllViews();
        ShopState state = ShopState.from(packet);
        window.addView(createHeader(state), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        window.addView(createDivider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView scrollView = new UiScrollView(getContext(), UiThemes.HUD.scroll());
        scrollView.setClipChildren(false);
        LinearLayout offerList = new LinearLayout(getContext());
        offerList.setClipChildren(false);
        offerList.setOrientation(LinearLayout.VERTICAL);
        offerList.setPadding(0, dp(8), 0, dp(4));
        scrollView.addView(offerList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        window.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(292)));

        if (state.offers().isEmpty()) {
            TextView empty = label(tr("geometry_node.shop.empty"), 13.0f, COLOR_MUTED, Gravity.CENTER);
            offerList.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));
        } else {
            for (int i = 0; i < state.offers().size(); i++) {
                ShopOffer offer = state.offers().get(i);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(offerRowHeightDp(offer))
                );
                lp.bottomMargin = dp(4);
                offerList.addView(createOfferRow(offer, i + 1), lp);
            }
        }

        window.addView(createDivider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        window.addView(createFooter(packet, state), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
    }

    private View createHeader(ShopState state) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View marker = new View(getContext());
        marker.setBackground(rect(COLOR_ACCENT, 1.0f, 0, 0));
        LinearLayout.LayoutParams markerLp = new LinearLayout.LayoutParams(dp(3), dp(26));
        markerLp.rightMargin = dp(11);
        header.addView(marker, markerLp);

        String titleText = state.title().isBlank() ? tr("geometry_node.shop.title.default") : state.title();
        TextView title = label(titleText, 17.0f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView count = label(
                tr("geometry_node.shop.offer_count", state.offers().size()),
                11.0f,
                COLOR_MUTED,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL
        );
        count.setSingleLine(true);
        header.addView(count, new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.MATCH_PARENT));

        View close = closeButton();
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        closeLp.leftMargin = dp(10);
        header.addView(close, closeLp);
        return header;
    }

    private View createOfferRow(ShopOffer offer, int index) {
        LinearLayout row = new LinearLayout(getContext());
        row.setClipChildren(false);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(7), dp(10), dp(7));
        row.setBackground(rect(COLOR_ROW, 2.0f, 0, 0));
        bindHoverBackground(row);

        LinearLayout titleColumn = new LinearLayout(getContext());
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);
        String title = offer.title().isBlank() ? tr("geometry_node.shop.offer_index", index) : offer.title();
        TextView titleView = label(title, 13.5f, COLOR_TEXT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleColumn.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        TextView meta = label(
                offer.remainingText(),
                11.0f,
                offer.soldOut() ? COLOR_ERROR : COLOR_LIMIT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL
        );
        meta.setSingleLine(true);
        titleColumn.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
        row.addView(titleColumn, new LinearLayout.LayoutParams(dp(TITLE_COLUMN_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));

        row.addView(createStackGrid(offer.costs()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        TextView arrow = label("\u2192", 16.0f, COLOR_MUTED, Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(createStackGrid(offer.rewards()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView tradeButton = button(
                offer.buttonText(),
                COLOR_ACCENT,
                COLOR_ACCENT_HOVER,
                COLOR_ACCENT_PRESSED,
                UiThemes.HUD.buttons().primary().text(),
                v -> tradeOffer(offer)
        );
        tradeButton.setEnabled(!offer.unavailable());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(dp(82), dp(32));
        buttonLp.leftMargin = dp(12);
        row.addView(tradeButton, buttonLp);
        return row;
    }

    private View createStackGrid(List<ItemStack> stacks) {
        LinearLayout grid = new LinearLayout(getContext());
        grid.setClipChildren(false);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        grid.setPadding(dp(4), 0, dp(2), 0);

        if (stacks == null || stacks.isEmpty()) {
            TextView empty = label(tr("geometry_node.common.none"), 11.0f, COLOR_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            grid.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return grid;
        }

        for (int rowStart = 0; rowStart < stacks.size(); rowStart += STACKS_PER_ROW) {
            LinearLayout slotRow = new LinearLayout(getContext());
            slotRow.setOrientation(LinearLayout.HORIZONTAL);
            slotRow.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            grid.addView(slotRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(SLOT_ROW_HEIGHT_DP)
            ));

            int rowEnd = Math.min(stacks.size(), rowStart + STACKS_PER_ROW);
            for (int i = rowStart; i < rowEnd; i++) {
                InventoryItemPickerOverlay.ItemStackView slot = new InventoryItemPickerOverlay.ItemStackView(
                        getContext(),
                        stacks.get(i),
                        null,
                        false,
                        DISPLAY_SCALE,
                        PREVIEW_STYLE,
                        UiThemes.HUD.buttons().primary().normal(),
                        UiThemes.HUD
                );
                LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(dp(SLOT_SIZE_DP), dp(SLOT_SIZE_DP));
                if (i + 1 < rowEnd) {
                    slotLp.rightMargin = dp(SLOT_MARGIN_DP);
                }
                slotRow.addView(slot, slotLp);
            }
        }
        return grid;
    }

    private static int offerRowHeightDp(ShopOffer offer) {
        int stackRows = Math.max(stackRowCount(offer.costs()), stackRowCount(offer.rewards()));
        int contentHeightDp = stackRows * SLOT_ROW_HEIGHT_DP;
        return Math.max(MIN_OFFER_ROW_HEIGHT_DP, OFFER_ROW_VERTICAL_PADDING_DP + contentHeightDp);
    }

    private static int stackRowCount(List<ItemStack> stacks) {
        int stackCount = stacks == null ? 0 : stacks.size();
        return Math.max(1, (stackCount + STACKS_PER_ROW - 1) / STACKS_PER_ROW);
    }

    private View createFooter(PacketOpenDialogue packet, ShopState state) {
        LinearLayout footer = new LinearLayout(getContext());
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(8), 0, 0);

        String message = state.message();
        int messageColor = state.messageSuccess() ? COLOR_SUCCESS : COLOR_ERROR;
        TextView status = label(
                message,
                11.5f,
                message.isBlank() ? COLOR_MUTED : messageColor,
                Gravity.LEFT | Gravity.CENTER_VERTICAL
        );
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        footer.addView(status, new LinearLayout.LayoutParams(0, dp(32), 1.0f));

        PacketOpenDialogue.Choice choice = continueChoice(packet);
        String choiceText = choice == null ? "" : ModernDialogueText.plain(choice.text());
        String text = choiceText.isBlank()
                ? Component.translatable("geometry_node.dialogue.continue").getString()
                : choiceText;
        TextView continueButton = button(
                text,
                COLOR_BUTTON,
                COLOR_BUTTON_HOVER,
                COLOR_BUTTON_PRESSED,
                COLOR_TEXT,
                v -> chooseContinue(choice)
        );
        continueButton.setEnabled(choice != null && choice.enabled());
        LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(dp(96), dp(32));
        continueLp.leftMargin = dp(12);
        footer.addView(continueButton, continueLp);
        return footer;
    }

    private void tradeOffer(ShopOffer offer) {
        if (waitingForServer || offer == null || offer.unavailable()) {
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
            if (choice != null && choice.defaultChoice()) {
                return choice;
            }
        }
        return packet.choices().get(0);
    }

    private void bindHoverBackground(View view) {
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                v.setBackground(rect(COLOR_ROW_HOVER, 2.0f, 0, 0));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                v.setBackground(rect(COLOR_ROW, 2.0f, 0, 0));
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

    private TextView button(String text,
                            int color,
                            int hoverColor,
                            int pressedColor,
                            int textColor,
                            View.OnClickListener listener) {
        TextView view = label(text, 12.0f, textColor, Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextColor(new ColorStateList(new int[][]{
                {-R.attr.state_enabled}, {}
        }, new int[]{COLOR_MUTED, textColor}));
        view.setOnClickListener(listener);
        bindButtonFeedback(view, color, hoverColor, pressedColor);
        return view;
    }

    private void bindButtonFeedback(View view, int color, int hoverColor, int pressedColor) {
        ShapeDrawable background = rect(color, 2.0f, 0, 0);
        background.setColor(new ColorStateList(new int[][]{
                {-R.attr.state_enabled}, {R.attr.state_pressed}, {R.attr.state_hovered}, {}
        }, new int[]{COLOR_DISABLED, pressedColor, hoverColor, color}));
        background.setStroke(dp(1), new ColorStateList(new int[][]{
                {R.attr.state_enabled, R.attr.state_focused}, {}
        }, new int[]{UiThemes.HUD.border().focus(), 0}));
        view.setBackground(background);
        view.setFocusable(true);
    }

    private View closeButton() {
        FrameLayout button = new FrameLayout(getContext()) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        button.setBackground(rect(0x00000000, 2.0f, 0, 0));
        button.setContentDescription(tr("geometry_node.common.cancel"));
        button.setTooltipText(tr("geometry_node.common.cancel"));
        button.setOnClickListener(v -> ClientDialogueState.close());
        bindButtonFeedback(button, 0, COLOR_BUTTON_HOVER, COLOR_BUTTON_PRESSED);

        VectorIconView icon = new VectorIconView(getContext(), VectorIconView.Kind.CLOSE, COLOR_MUTED);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(18), dp(18));
        iconLp.gravity = Gravity.CENTER;
        button.addView(icon, iconLp);
        return button;
    }

    private View createDivider() {
        View divider = new View(getContext());
        divider.setBackground(rect(COLOR_DIVIDER, 0.0f, 0, 0));
        return divider;
    }

    private TextView label(CharSequence text, float sizeDp, int color, int gravity) {
        TextView view = new TextView(getContext()) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        UIUtils.setLockedTextSize(view, sizeDp * DISPLAY_SCALE);
        view.setTextColor(color);
        view.setText(text == null ? "" : text);
        view.setGravity(gravity);
        return view;
    }

    private ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp * DISPLAY_SCALE));
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private int dp(float value) {
        return UIUtils.dp2pxInt(value * DISPLAY_SCALE);
    }

    private record ShopState(String title, String message, boolean messageSuccess, List<ShopOffer> offers) {
        private static ShopState from(PacketOpenDialogue packet) {
            ShopPagePayload shop = packet == null ? null : packet.shop();
            if (shop == null) {
                return new ShopState("", "", true, List.of());
            }
            String messageKey = shop.feedback().messageKey();
            String message = messageKey.isBlank()
                    ? shop.feedback().message()
                    : tr(messageKey);
            return new ShopState(shop.title(), message, shop.feedback().success(), parseOffers(shop.offers()));
        }
    }

    private record ShopOffer(
            String id,
            String title,
            int maxUses,
            int uses,
            boolean enabled,
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

        private String remainingText() {
            return maxUses > 0
                    ? tr("geometry_node.shop.remaining_count", Math.max(0, maxUses - uses), maxUses)
                    : tr("geometry_node.shop.remaining_infinite");
        }
    }

    private static List<ShopOffer> parseOffers(List<ShopPagePayload.Offer> offers) {
        List<ShopOffer> result = new ArrayList<>();
        for (ShopPagePayload.Offer offer : offers) {
            result.add(new ShopOffer(
                    offer.id(),
                    offer.title(),
                    offer.maxUses(),
                    offer.uses(),
                    offer.enabled(),
                    parseStacks(offer.costs()),
                    parseStacks(offer.rewards())
            ));
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> parseStacks(List<ShopPagePayload.Item> items) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (ShopPagePayload.Item item : items) {
            String stackJson = item.stackJson();
            if (stackJson.isBlank()) {
                continue;
            }
            ItemStack stack = ItemCodecUtils.fromJson(stackJson, mc.level.registryAccess());
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

}
