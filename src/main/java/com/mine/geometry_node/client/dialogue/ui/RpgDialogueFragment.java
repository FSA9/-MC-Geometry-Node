package com.mine.geometry_node.client.dialogue.ui;

import com.mine.geometry_node.client.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.dialogue.ModernDialogueText;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialogueText;
import com.mine.geometry_node.core.engine.system.dialogue.richtext.DialogueRichText;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.OnBackPressedCallback;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.text.SpannableStringBuilder;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * In-game RPG dialogue overlay rendered with ModernUI.
 */
public class RpgDialogueFragment extends Fragment {
    private static final int TEXT_MAIN = DialogueHudTheme.TEXT_PRIMARY;
    private static final int TEXT_MUTED = DialogueHudTheme.TEXT_MUTED;
    private static final int DIVIDER = DialogueHudTheme.DIVIDER;
    private static final int CHOICE_BG = DialogueHudTheme.withAlpha(DialogueHudTheme.PANEL, 0x40);
    private static final int CHOICE_BG_DEFAULT = DialogueHudTheme.withAlpha(DialogueHudTheme.BUTTON, 0x4A);
    private static final int CHOICE_BG_HOVER = DialogueHudTheme.withAlpha(DialogueHudTheme.BUTTON_HOVER, 0x66);
    private static final int CHOICE_BG_PRESSED = DialogueHudTheme.withAlpha(DialogueHudTheme.ACCENT_PRESSED, 0x88);
    private static final int CHOICE_BG_DISABLED = DialogueHudTheme.withAlpha(DialogueHudTheme.PANEL, 0x25);
    private static final int CHOICE_STROKE = DialogueHudTheme.withAlpha(DialogueHudTheme.ACCENT, 0x44);
    private static final int CHOICE_STROKE_HOVER = DialogueHudTheme.withAlpha(DialogueHudTheme.ACCENT, 0x88);
    private static final int CHOICE_STROKE_DISABLED = DialogueHudTheme.withAlpha(DialogueHudTheme.TEXT_MUTED, 0x28);
    private static final float LAYOUT_SCALE = 2.0f;
    private static final int PANEL_MIN_HEIGHT_DP = 142;
    private static final int PANEL_MAX_HEIGHT_DP = 360;
    private static final int BODY_LINE_HEIGHT_DP = 24;

    private FrameLayout root;
    private LinearLayout panel;
    private TextView bodyView;
    private OnBackPressedCallback backPressedCallback;
    private boolean waitingForServer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = getContext();
        UIUtils.syncFixedDensity();

        root = new FrameLayout(context);
        root.setOnClickListener(v -> {
        });
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ESCAPE) {
                return ClientDialogueState.close();
            }
            return false;
        });

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        panel.setOnClickListener(v -> {
        });

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.BOTTOM;
        panelParams.leftMargin = dp(64);
        panelParams.rightMargin = dp(64);
        panelParams.bottomMargin = dp(82);
        root.addView(panel, panelParams);

        refresh(ClientDialogueState.current());
        registerBackPressedCallback();
        root.post(root::requestFocus);
        return root;
    }

    @Override
    public void onDestroyView() {
        if (backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }
        super.onDestroyView();
    }

    public void refresh(PacketOpenDialogue packet) {
        waitingForServer = false;
        if (panel == null) {
            return;
        }

        panel.setAlpha(1.0f);
        panel.removeAllViews();
        bodyView = null;
        if (packet == null) {
            panel.addView(label("Dialogue is unavailable.", 16.0f, TEXT_MUTED, Gravity.CENTER_VERTICAL),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
            return;
        }

        DialogueRichText bodyText = ModernDialogueText.parse(packet.bodyText());
        int panelHeight = panelHeightDp(bodyText.plainText(), packet.choices().size());
        panel.addView(createTextColumn(packet, bodyText), new LinearLayout.LayoutParams(
                0,
                dp(panelHeight),
                7.0f
        ));

        panel.addView(createActionColumn(packet), new LinearLayout.LayoutParams(
                0,
                dp(panelHeight),
                3.0f
        ));

        TextView currentBody = bodyView;
        panel.post(() -> adjustPanelHeight(currentBody, packet.choices().size()));
    }

    private View createTextColumn(PacketOpenDialogue packet, DialogueRichText bodyText) {
        Context context = getContext();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.BOTTOM);
        column.setPadding(0, 0, dp(40), 0);

        Component speaker = packet.speaker().getString().isBlank()
                ? Component.literal("Dialogue").withStyle(ChatFormatting.GOLD)
                : packet.speaker();
        VanillaComponentView speakerView = new VanillaComponentView(context, speaker, 22.0f * LAYOUT_SCALE);
        speakerView.setPadding(0, 0, 0, dp(4));
        column.addView(speakerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(32)
        ));

        View divider = new View(context);
        divider.setBackground(rect(DIVIDER, 0.0f, 0, 0));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        dividerParams.rightMargin = dp(24);
        dividerParams.bottomMargin = dp(12);
        column.addView(divider, dividerParams);

        TextView body = label(ModernDialogueText.display(bodyText), 18.0f, TEXT_MAIN, Gravity.LEFT | Gravity.TOP);
        body.setMinLines(3);
        body.setLineSpacing(UIUtils.dp2px(3.0f * LAYOUT_SCALE), 1.06f);
        bodyView = body;
        body.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                adjustPanelHeight(body, packet.choices().size()));
        ScrollView bodyScroll = new ScrollView(context);
        bodyScroll.setFillViewport(true);
        bodyScroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        column.addView(bodyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        return column;
    }

    private int panelHeightDp(String bodyText, int choiceCount) {
        return panelHeightDp(visualLineCount(bodyText), choiceCount);
    }

    private int panelHeightDp(int lineCount, int choiceCount) {
        int bodyLines = Math.max(3, lineCount);
        int bodyHeight = 45 + bodyLines * BODY_LINE_HEIGHT_DP;
        int choiceHeight = Math.max(1, choiceCount) * 38;
        return Math.max(PANEL_MIN_HEIGHT_DP, Math.min(PANEL_MAX_HEIGHT_DP, Math.max(bodyHeight, choiceHeight)));
    }

    private void adjustPanelHeight(TextView expectedBody, int choiceCount) {
        if (panel == null || expectedBody == null || expectedBody != bodyView || panel.getChildCount() != 2) {
            return;
        }
        int height = dp(panelHeightDp(expectedBody.getLineCount(), choiceCount));
        for (int i = 0; i < panel.getChildCount(); i++) {
            ViewGroup.LayoutParams params = panel.getChildAt(i).getLayoutParams();
            if (params.height != height) {
                params.height = height;
                panel.getChildAt(i).setLayoutParams(params);
            }
        }
    }

    private static int visualLineCount(String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
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

    private View createActionColumn(PacketOpenDialogue packet) {
        Context context = getContext();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        column.setPadding(dp(18), 0, 0, 0);

        if (!packet.choices().isEmpty()) {
            ScrollView choiceScroll = new ScrollView(context);
            choiceScroll.setFillViewport(true);
            choiceScroll.addView(createChoices(packet), new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            column.addView(choiceScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1.0f
            ));
        }

        return column;
    }

    private View createChoices(PacketOpenDialogue packet) {
        Context context = getContext();
        LinearLayout choices = new LinearLayout(context);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setGravity(Gravity.RIGHT | Gravity.BOTTOM);

        for (PacketOpenDialogue.Choice choice : packet.choices()) {
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(32)
            );
            rowParams.topMargin = dp(6);
            choices.addView(createChoiceRow(choice), rowParams);
        }

        return choices;
    }

    private View createChoiceRow(PacketOpenDialogue.Choice choice) {
        DialogueRichText choiceText = ModernDialogueText.parse(choice.text());
        CharSequence text = choice.enabled() ? ModernDialogueText.display(choiceText) : disabledChoiceText(choiceText, choice.disabledReason());
        TextView row = label(text, 14.0f, choice.enabled() ? TEXT_MAIN : TEXT_MUTED, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setEnabled(choice.enabled());
        applyChoiceBackground(row, false, false, choice.enabled(), choice.defaultChoice());
        if (choice.enabled()) {
            bindChoiceFeedback(row, choice.defaultChoice());
            row.setOnClickListener(v -> {
                if (waitingForServer) {
                    return;
                }
                waitingForServer = ClientDialogueState.choose(choice.choiceId());
                if (waitingForServer) {
                    panel.setAlpha(0.72f);
                }
            });
        }
        return row;
    }

    private void bindChoiceFeedback(TextView row, boolean defaultChoice) {
        final boolean[] hovered = {false};
        final boolean[] pressed = {false};
        Runnable update = () -> applyChoiceBackground(row, hovered[0], pressed[0], true, defaultChoice);

        row.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                hovered[0] = true;
                update.run();
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                hovered[0] = false;
                pressed[0] = false;
                update.run();
            }
            return false;
        });

        row.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    pressed[0] = true;
                    update.run();
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pressed[0] = false;
                    update.run();
                }
                default -> {
                }
            }
            return false;
        });
    }

    private void applyChoiceBackground(View view, boolean hovered, boolean pressed, boolean enabled, boolean defaultChoice) {
        int background;
        int stroke;
        if (!enabled) {
            background = CHOICE_BG_DISABLED;
            stroke = CHOICE_STROKE_DISABLED;
        } else if (pressed) {
            background = CHOICE_BG_PRESSED;
            stroke = CHOICE_STROKE_HOVER;
        } else if (hovered) {
            background = CHOICE_BG_HOVER;
            stroke = CHOICE_STROKE_HOVER;
        } else if (defaultChoice) {
            background = CHOICE_BG_DEFAULT;
            stroke = CHOICE_STROKE_HOVER;
        } else {
            background = CHOICE_BG;
            stroke = CHOICE_STROKE;
        }
        view.setBackground(rect(background, 3.0f, 1, stroke));
    }

    private CharSequence disabledChoiceText(DialogueRichText choiceText, DialogueText disabledReason) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(ModernDialogueText.display(choiceText));
        DialogueRichText parsedReason = ModernDialogueText.parse(disabledReason);
        if (!parsedReason.plainText().isBlank()) {
            text.append(" - ");
            text.append(ModernDialogueText.display(parsedReason));
        }
        return text;
    }

    private TextView label(CharSequence text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(getContext(), "", sizeDp * LAYOUT_SCALE, color);
        view.setText(text == null ? "" : text);
        view.setGravity(gravity);
        return view;
    }

    private ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp * LAYOUT_SCALE));
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return UIUtils.dp2pxInt(value * LAYOUT_SCALE);
    }
}
