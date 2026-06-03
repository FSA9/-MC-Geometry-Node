package com.mine.geometry_node.client.dialogue.ui;

import com.mine.geometry_node.client.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
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

/**
 * In-game RPG dialogue overlay rendered with ModernUI.
 */
public class RpgDialogueFragment extends Fragment {
    private static final int TEXT_MAIN = 0xFFF4F1E8;
    private static final int TEXT_MUTED = 0xFF9DA5B4;
    private static final int TEXT_ACCENT = 0xFFE2C16A;
    private static final int CHOICE_BG = 0x55171B23;
    private static final int CHOICE_BG_HOVER = 0x77262C36;
    private static final int CHOICE_BG_PRESSED = 0x99414B5F;
    private static final int CHOICE_BG_DISABLED = 0x33171B23;
    private static final int CHOICE_STROKE = 0x66E2C16A;
    private static final int CHOICE_STROKE_HOVER = 0xAAE2C16A;
    private static final int CHOICE_STROKE_DISABLED = 0x339DA5B4;

    private FrameLayout root;
    private LinearLayout panel;
    private OnBackPressedCallback backPressedCallback;
    private boolean waitingForServer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = getContext();
        UIConstants.mDensity = context.getResources().getDisplayMetrics().density;

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
        panel.setGravity(Gravity.BOTTOM);
        panel.setOnClickListener(v -> {
        });

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.BOTTOM;
        panelParams.leftMargin = dp(48);
        panelParams.rightMargin = dp(48);
        panelParams.bottomMargin = dp(96);
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
        if (packet == null) {
            panel.addView(label("Dialogue is unavailable.", 16.0f, TEXT_MUTED, Gravity.CENTER_VERTICAL),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
            return;
        }

        panel.addView(createTextColumn(packet), new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        panel.addView(createActionColumn(packet), new LinearLayout.LayoutParams(
                dp(280),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private View createTextColumn(PacketOpenDialogue packet) {
        Context context = getContext();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, dp(36), 0);

        String speaker = packet.speaker() == null || packet.speaker().isBlank() ? "Dialogue" : packet.speaker();
        TextView speakerView = label(speaker, 16.0f, TEXT_ACCENT, Gravity.LEFT);
        speakerView.setPadding(0, 0, 0, dp(8));
        column.addView(speakerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(30)
        ));

        TextView body = label(packet.bodyText(), 20.0f, TEXT_MAIN, Gravity.LEFT);
        body.setMinLines(3);
        body.setLineSpacing(UIUtils.dp2px(3.0f), 1.08f);
        column.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return column;
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
        column.setGravity(Gravity.RIGHT);

        if (!packet.choices().isEmpty()) {
            column.addView(createChoices(packet), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.min(dp(260), dp(42 * packet.choices().size()))
            ));
        }

        return column;
    }

    private View createChoices(PacketOpenDialogue packet) {
        Context context = getContext();
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);

        LinearLayout choices = new LinearLayout(context);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setGravity(Gravity.RIGHT);
        choices.setPadding(0, dp(8), 0, 0);

        for (PacketOpenDialogue.Choice choice : packet.choices()) {
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(34)
            );
            rowParams.bottomMargin = dp(6);
            choices.addView(createChoiceRow(choice), rowParams);
        }

        scrollView.addView(choices, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scrollView;
    }

    private View createChoiceRow(PacketOpenDialogue.Choice choice) {
        String text = choice.enabled() ? "> " + choice.text() : choice.text();
        TextView row = label(text, 15.0f, choice.enabled() ? TEXT_MAIN : TEXT_MUTED, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setEnabled(choice.enabled());
        applyChoiceBackground(row, false, false, choice.enabled());
        if (choice.enabled()) {
            bindChoiceFeedback(row);
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

    private void bindChoiceFeedback(TextView row) {
        final boolean[] hovered = {false};
        final boolean[] pressed = {false};
        Runnable update = () -> applyChoiceBackground(row, hovered[0], pressed[0], true);

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

    private void applyChoiceBackground(View view, boolean hovered, boolean pressed, boolean enabled) {
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
        } else {
            background = CHOICE_BG;
            stroke = CHOICE_STROKE;
        }
        view.setBackground(rect(background, 3.0f, 1, stroke));
    }

    private TextView label(String text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(getContext(), text == null ? "" : text, sizeDp, color);
        view.setGravity(gravity);
        return view;
    }

    private ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return UIUtils.dp2pxInt(value);
    }
}
