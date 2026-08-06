package com.mine.geometry_node.client.quest.ui;

import com.mine.geometry_node.client.dialogue.ModernDialogueText;
import com.mine.geometry_node.client.dialogue.ui.DialogueHudTheme;
import com.mine.geometry_node.client.quest.ClientQuestScreenState;
import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.quest.model.QuestHintType;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.OnBackPressedCallback;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class QuestScreenFragment extends Fragment {
    private static final float DISPLAY_SCALE = 2.0f;
    private static final int WINDOW_WIDTH_DP = 696;
    private static final int WINDOW_HEIGHT_DP = 420;
    private static final int LIST_WIDTH_DP = 238;
    private static final String ALL_STATUS_ID = "all";
    private static final String ALL_STATUS_TRANSLATION_KEY = "geometry_node.quest.status.all";
    private static final int ALL_STATUS_COLOR = 0xFFE6E6E6;

    private FrameLayout root;
    private LinearLayout window;
    private OnBackPressedCallback backPressedCallback;
    private String selectedStatusId = "";
    private String selectedTaskKey = "";
    private String pendingAbandonTaskKey = "";
    private boolean waitingForServer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = getContext();
        UIUtils.syncFixedDensity();

        root = new FrameLayout(context);
        root.setBackground(rect(DialogueHudTheme.OVERLAY_DIM, 0.0f, 0, 0));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnClickListener(v -> {
        });
        root.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ESCAPE) {
                ClientQuestScreenState.close();
                return true;
            }
            return false;
        });

        window = new LinearLayout(context);
        window.setOrientation(LinearLayout.VERTICAL);
        window.setPadding(dp(18), dp(14), dp(18), dp(16));
        window.setBackground(rect(DialogueHudTheme.PANEL, 3.0f, 1, DialogueHudTheme.DIVIDER));
        window.setOnClickListener(v -> {
        });
        FrameLayout.LayoutParams windowParams = new FrameLayout.LayoutParams(dp(WINDOW_WIDTH_DP), dp(WINDOW_HEIGHT_DP));
        windowParams.gravity = Gravity.CENTER;
        root.addView(window, windowParams);

        refresh(ClientQuestScreenState.current());
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

    public void refresh(@Nullable PacketQuestScreenSnapshot snapshot) {
        waitingForServer = false;
        if (window == null) return;
        window.setAlpha(1.0f);
        window.removeAllViews();

        if (snapshot == null) {
            window.addView(label(tr("geometry_node.quest.screen.unavailable"), 14.0f,
                    DialogueHudTheme.TEXT_MUTED, Gravity.CENTER),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        normalizeSelection(snapshot);
        window.addView(createHeader(snapshot), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        window.addView(divider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        window.addView(createStatusBar(snapshot), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        bodyLp.topMargin = dp(8);
        window.addView(createBody(snapshot), bodyLp);
    }

    private View createHeader(PacketQuestScreenSnapshot snapshot) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = label(tr("geometry_node.quest.screen.title"), 17.0f,
                DialogueHudTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        if (!snapshot.actionResult().isBlank()) {
            int color = snapshot.actionSuccessful() ? DialogueHudTheme.SUCCESS : DialogueHudTheme.ERROR;
            String feedbackText = snapshot.actionMessage().isBlank()
                    ? actionResult(snapshot.actionResult())
                    : snapshot.actionMessage();
            TextView feedback = label(feedbackText, 10.5f, color,
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            feedback.setSingleLine(true);
            feedback.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams feedbackLp = new LinearLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.MATCH_PARENT);
            feedbackLp.rightMargin = dp(8);
            header.addView(feedback, feedbackLp);
        }

        header.addView(closeButton(), new LinearLayout.LayoutParams(dp(30), dp(30)));
        return header;
    }

    private View createStatusBar(PacketQuestScreenSnapshot snapshot) {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                float amount = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (amount != 0.0f) {
                    scroll.scrollBy((int) (-amount * dp(42)), 0);
                    return true;
                }
            }
            return false;
        });

        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(7), 0, 0);

        List<PacketQuestScreenSnapshot.StatusView> statusTabs = new ArrayList<>(snapshot.statuses());
        statusTabs.add(new PacketQuestScreenSnapshot.StatusView(
                ALL_STATUS_ID, ALL_STATUS_TRANSLATION_KEY, ALL_STATUS_COLOR, false));
        for (PacketQuestScreenSnapshot.StatusView status : statusTabs) {
            boolean selected = status.id().equals(selectedStatusId);
            int baseColor = selected
                    ? DialogueHudTheme.SURFACE_HOVER
                    : DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0x80);
            TextView tab = label(tr(status.translationKey()) + "  " + countForStatus(snapshot, status.id()),
                    11.5f, selected ? status.color() : DialogueHudTheme.TEXT_MUTED, Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setEllipsize(TextUtils.TruncateAt.END);
            setPanelBackground(tab, baseColor, selected ? 1 : 0, status.color());
            tab.setOnClickListener(v -> {
                if (!status.id().equals(selectedStatusId)) {
                    selectedStatusId = status.id();
                    selectedTaskKey = "";
                    pendingAbandonTaskKey = "";
                    refresh(ClientQuestScreenState.current());
                }
            });
            bindHover(tab, baseColor, DialogueHudTheme.BUTTON_HOVER, selected ? 1 : 0, status.color());
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(dp(120), dp(36));
            if (bar.getChildCount() > 0) tabLp.leftMargin = dp(6);
            bar.addView(tab, tabLp);
        }
        scroll.addView(bar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    private View createBody(PacketQuestScreenSnapshot snapshot) {
        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setBackground(rect(DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0x76), 2.0f, 0, 0));
        body.addView(createQuestList(snapshot),
                new LinearLayout.LayoutParams(dp(LIST_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT);
        dividerLp.topMargin = dp(10);
        dividerLp.bottomMargin = dp(10);
        body.addView(divider(), dividerLp);
        body.addView(createDetails(snapshot), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        return body;
    }

    private View createQuestList(PacketQuestScreenSnapshot snapshot) {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(10), dp(10), dp(10), dp(10));
        column.addView(label(tr("geometry_node.quest.screen.list"), 11.0f,
                        DialogueHudTheme.TEXT_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        List<PacketQuestScreenSnapshot.QuestView> quests = questsForStatus(snapshot, selectedStatusId);
        if (quests.isEmpty()) {
            list.addView(label(tr("geometry_node.quest.screen.empty"), 11.5f,
                            DialogueHudTheme.TEXT_MUTED, Gravity.CENTER),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
            return column;
        }

        for (PacketQuestScreenSnapshot.QuestView quest : quests) {
            boolean selected = quest.taskKey().equals(selectedTaskKey);
            int baseColor = selected
                    ? DialogueHudTheme.BUTTON_HOVER
                    : DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0xA0);
            TextView row = label(ModernDialogueText.display(ModernDialogueText.parse(quest.title())),
                    12.0f, selected ? DialogueHudTheme.TEXT_PRIMARY : DialogueHudTheme.TEXT_MUTED,
                    Gravity.LEFT | Gravity.CENTER_VERTICAL);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setPadding(dp(10), 0, dp(8), 0);
            setPanelBackground(row, baseColor, selected ? 1 : 0, statusColor(snapshot, quest.statusId()));
            row.setOnClickListener(v -> {
                selectedTaskKey = quest.taskKey();
                pendingAbandonTaskKey = "";
                refresh(ClientQuestScreenState.current());
            });
            bindHover(row, baseColor, DialogueHudTheme.SURFACE_HOVER,
                    selected ? 1 : 0, statusColor(snapshot, quest.statusId()));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            rowLp.bottomMargin = dp(6);
            list.addView(row, rowLp);
        }
        return column;
    }

    private View createDetails(PacketQuestScreenSnapshot snapshot) {
        LinearLayout details = new LinearLayout(getContext());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(18), dp(14), dp(18), dp(14));

        PacketQuestScreenSnapshot.QuestView quest = selectedQuest(snapshot);
        if (quest == null) {
            details.addView(label(tr("geometry_node.quest.screen.select_hint"), 12.0f,
                            DialogueHudTheme.TEXT_MUTED, Gravity.CENTER),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return details;
        }

        details.addView(label(statusName(snapshot, quest.statusId()), 10.5f,
                        statusColor(snapshot, quest.statusId()), Gravity.LEFT | Gravity.CENTER_VERTICAL),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        TextView title = label(ModernDialogueText.display(ModernDialogueText.parse(quest.title())),
                18.0f, DialogueHudTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        details.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        details.addView(divider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView descriptionScroll = new ScrollView(getContext());
        LinearLayout detailContent = new LinearLayout(getContext());
        detailContent.setOrientation(LinearLayout.VERTICAL);
        TextView description = label(ModernDialogueText.display(ModernDialogueText.parse(quest.description())),
                12.0f, DialogueHudTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.TOP);
        description.setPadding(0, dp(12), dp(4), dp(12));
        detailContent.addView(description,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!quest.objectives().isEmpty()) {
            TextView objectiveTitle = label(tr("geometry_node.quest.screen.objectives"), 10.5f,
                    DialogueHudTheme.TEXT_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams objectiveTitleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
            objectiveTitleLp.topMargin = dp(4);
            detailContent.addView(objectiveTitle, objectiveTitleLp);
            for (PacketQuestScreenSnapshot.ObjectiveView objective : quest.objectives()) {
                LinearLayout.LayoutParams objectiveLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                objectiveLp.bottomMargin = dp(7);
                detailContent.addView(createObjectiveView(objective), objectiveLp);
            }
        }
        if (!quest.rewards().isEmpty()) {
            TextView rewardTitle = label(tr("geometry_node.quest.screen.rewards"), 10.5f,
                    DialogueHudTheme.TEXT_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rewardTitleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
            rewardTitleLp.topMargin = dp(6);
            detailContent.addView(rewardTitle, rewardTitleLp);
            for (PacketQuestScreenSnapshot.RewardView reward : quest.rewards()) {
                LinearLayout.LayoutParams rewardLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rewardLp.bottomMargin = dp(7);
                detailContent.addView(createRewardView(reward), rewardLp);
            }
        }
        descriptionScroll.addView(detailContent,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        details.addView(descriptionScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        View actions = createActions(snapshot, quest);
        if (actions != null) {
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
            actionLp.topMargin = dp(8);
            details.addView(actions, actionLp);
        }
        return details;
    }

    private View createObjectiveView(PacketQuestScreenSnapshot.ObjectiveView objective) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        boolean completed = objective.counterEnabled()
                && objective.targetValue() > 0.0
                && objective.currentValue() >= objective.targetValue();
        row.setBackground(rect(
                DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0xA8),
                2.0f, 1, completed ? DialogueHudTheme.SUCCESS : DialogueHudTheme.DIVIDER));

        LinearLayout contentRow = new LinearLayout(getContext());
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setGravity(Gravity.CENTER_VERTICAL);
        QuestHintType hintType = QuestHintType.fromId(objective.hintType());
        if (hintType != QuestHintType.NONE) {
            QuestHintView hint = new QuestHintView(getContext());
            hint.setHint(hintType, objective.hintValue());
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(dp(34), dp(34));
            hintLp.rightMargin = dp(8);
            contentRow.addView(hint, hintLp);
        }

        TextView content = label(ModernDialogueText.display(ModernDialogueText.parse(objective.content())),
                11.5f, completed ? DialogueHudTheme.SUCCESS : DialogueHudTheme.TEXT_PRIMARY,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        contentRow.addView(content, new LinearLayout.LayoutParams(0, dp(34), 1.0f));

        if (objective.quantityEnabled()) {
            String quantity = objective.counterEnabled()
                    ? formatNumber(objective.currentValue()) + " / " + formatNumber(objective.targetValue())
                    : "x " + formatNumber(objective.targetValue());
            TextView amount = label(quantity, 10.5f, DialogueHudTheme.TEXT_MUTED,
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(dp(92), dp(34));
            amountLp.leftMargin = dp(8);
            contentRow.addView(amount, amountLp);
        }

        row.addView(contentRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        if (objective.counterEnabled()) {
            QuestProgressBar progress = new QuestProgressBar(
                    getContext(), objective.currentValue(), objective.targetValue());
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
            progressLp.topMargin = dp(5);
            row.addView(progress, progressLp);
        }
        return row;
    }

    private View createRewardView(PacketQuestScreenSnapshot.RewardView reward) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.setBackground(rect(
                DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0xA8),
                2.0f, 1, DialogueHudTheme.DIVIDER));

        QuestHintType hintType = QuestHintType.fromId(reward.hintType());
        if (hintType != QuestHintType.NONE) {
            QuestHintView hint = new QuestHintView(getContext());
            hint.setHint(hintType, reward.hintValue());
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(dp(34), dp(34));
            hintLp.rightMargin = dp(8);
            row.addView(hint, hintLp);
        }

        TextView content = label(ModernDialogueText.display(ModernDialogueText.parse(reward.content())),
                11.5f, DialogueHudTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(content, new LinearLayout.LayoutParams(0, dp(34), 1.0f));

        TextView amount = label("x " + formatNumber(reward.amount()), 11.0f,
                DialogueHudTheme.TEXT_MUTED, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(dp(92), dp(34));
        amountLp.leftMargin = dp(8);
        row.addView(amount, amountLp);
        return row;
    }

    @Nullable
    private View createActions(PacketQuestScreenSnapshot snapshot, PacketQuestScreenSnapshot.QuestView quest) {
        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        if ("unaccepted".equals(quest.statusId()) && quest.acceptEnabled()) {
            actions.addView(actionButton(tr("geometry_node.quest.screen.accept"),
                            DialogueHudTheme.BUTTON, DialogueHudTheme.BUTTON_HOVER,
                            () -> ClientQuestScreenState.accept(quest.taskKey())),
                    new LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.MATCH_PARENT));
            return actions;
        }
        PacketQuestScreenSnapshot.StatusView status = findStatus(snapshot, quest.statusId());
        if (status != null && status.graphActive() && !quest.instanceId().isBlank()) {
            String abandonText = quest.taskKey().equals(pendingAbandonTaskKey)
                    ? tr("geometry_node.quest.screen.confirm_abandon")
                    : tr("geometry_node.quest.screen.abandon");
            TextView abandon = actionButton(
                    abandonText,
                    DialogueHudTheme.withAlpha(DialogueHudTheme.ERROR, 0x82),
                    DialogueHudTheme.withAlpha(DialogueHudTheme.ERROR, 0xB8),
                    () -> abandonQuest(quest));
            actions.addView(abandon, new LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.MATCH_PARENT));

            TextView submit = actionButton(tr("geometry_node.quest.screen.submit"),
                    DialogueHudTheme.BUTTON, DialogueHudTheme.BUTTON_HOVER,
                    () -> ClientQuestScreenState.submit(quest.taskKey(), quest.instanceId()));
            LinearLayout.LayoutParams submitLp = new LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.MATCH_PARENT);
            submitLp.leftMargin = dp(8);
            actions.addView(submit, submitLp);
            return actions;
        }
        return null;
    }

    private boolean abandonQuest(PacketQuestScreenSnapshot.QuestView quest) {
        if (!quest.taskKey().equals(pendingAbandonTaskKey)) {
            pendingAbandonTaskKey = quest.taskKey();
            refresh(ClientQuestScreenState.current());
            return false;
        }
        pendingAbandonTaskKey = "";
        return ClientQuestScreenState.abandon(quest.taskKey(), quest.instanceId());
    }

    private TextView actionButton(String text,
                                  int backgroundColor,
                                  int hoverColor,
                                  BooleanSupplier action) {
        TextView button = label(text, 12.0f, 0xFFFFFFFF, Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        setPanelBackground(button, backgroundColor, 0, 0);
        button.setOnClickListener(v -> performAction(action));
        bindHover(button, backgroundColor, hoverColor, 0, 0);
        return button;
    }

    private void performAction(BooleanSupplier action) {
        if (ClientQuestScreenState.isPreviewActive()
                || waitingForServer || action == null || !action.getAsBoolean()) return;
        waitingForServer = true;
        window.setAlpha(0.76f);
    }

    private void normalizeSelection(PacketQuestScreenSnapshot snapshot) {
        if (!ALL_STATUS_ID.equals(selectedStatusId) && findStatus(snapshot, selectedStatusId) == null) {
            selectedStatusId = firstPopulatedStatus(snapshot);
        }

        if (!ALL_STATUS_ID.equals(selectedStatusId) && !selectedTaskKey.isBlank()) {
            for (PacketQuestScreenSnapshot.QuestView quest : snapshot.quests()) {
                if (quest.taskKey().equals(selectedTaskKey)) {
                    selectedStatusId = quest.statusId();
                    break;
                }
            }
        }
        List<PacketQuestScreenSnapshot.QuestView> visible = questsForStatus(snapshot, selectedStatusId);
        boolean selectedExists = visible.stream().anyMatch(quest -> quest.taskKey().equals(selectedTaskKey));
        if (!selectedExists) selectedTaskKey = visible.isEmpty() ? "" : visible.get(0).taskKey();
    }

    private static String firstPopulatedStatus(PacketQuestScreenSnapshot snapshot) {
        for (PacketQuestScreenSnapshot.StatusView status : snapshot.statuses()) {
            if (countForStatus(snapshot, status.id()) > 0) return status.id();
        }
        return snapshot.statuses().isEmpty() ? ALL_STATUS_ID : snapshot.statuses().get(0).id();
    }

    private static List<PacketQuestScreenSnapshot.QuestView> questsForStatus(
            PacketQuestScreenSnapshot snapshot, String statusId) {
        if (ALL_STATUS_ID.equals(statusId)) {
            return new ArrayList<>(snapshot.quests());
        }
        List<PacketQuestScreenSnapshot.QuestView> result = new ArrayList<>();
        for (PacketQuestScreenSnapshot.QuestView quest : snapshot.quests()) {
            if (quest.statusId().equals(statusId)) result.add(quest);
        }
        return result;
    }

    @Nullable
    private PacketQuestScreenSnapshot.QuestView selectedQuest(PacketQuestScreenSnapshot snapshot) {
        for (PacketQuestScreenSnapshot.QuestView quest : snapshot.quests()) {
            if ((ALL_STATUS_ID.equals(selectedStatusId) || quest.statusId().equals(selectedStatusId))
                    && quest.taskKey().equals(selectedTaskKey)) return quest;
        }
        return null;
    }

    private static int countForStatus(PacketQuestScreenSnapshot snapshot, String statusId) {
        if (ALL_STATUS_ID.equals(statusId)) return snapshot.quests().size();
        int count = 0;
        for (PacketQuestScreenSnapshot.QuestView quest : snapshot.quests()) {
            if (quest.statusId().equals(statusId)) count++;
        }
        return count;
    }

    @Nullable
    private static PacketQuestScreenSnapshot.StatusView findStatus(
            PacketQuestScreenSnapshot snapshot, String statusId) {
        for (PacketQuestScreenSnapshot.StatusView status : snapshot.statuses()) {
            if (status.id().equals(statusId)) return status;
        }
        return null;
    }

    private static int statusColor(PacketQuestScreenSnapshot snapshot, String statusId) {
        PacketQuestScreenSnapshot.StatusView status = findStatus(snapshot, statusId);
        return status != null ? status.color() : DialogueHudTheme.TEXT_MUTED;
    }

    private static String statusName(PacketQuestScreenSnapshot snapshot, String statusId) {
        PacketQuestScreenSnapshot.StatusView status = findStatus(snapshot, statusId);
        return status != null ? tr(status.translationKey()) : statusId;
    }

    private static String actionResult(String result) {
        String key = "geometry_node.quest.action_result." + result;
        String translated = tr(key);
        return translated.equals(key) ? result : translated;
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value);
    }

    private View closeButton() {
        FrameLayout button = new FrameLayout(getContext());
        setPanelBackground(button, 0x00000000, 0, 0);
        button.setContentDescription(tr("geometry_node.common.cancel"));
        button.setTooltipText(tr("geometry_node.common.cancel"));
        button.setOnClickListener(v -> ClientQuestScreenState.close());
        bindHover(button, 0x00000000, DialogueHudTheme.BUTTON_HOVER, 0, 0);
        VectorIconView icon = new VectorIconView(getContext(), VectorIconView.Kind.CLOSE, DialogueHudTheme.TEXT_MUTED);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(17), dp(17));
        iconLp.gravity = Gravity.CENTER;
        button.addView(icon, iconLp);
        return button;
    }

    private void bindHover(View view, int normalColor, int hoverColor, int strokeWidth, int strokeColor) {
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                setPanelBackground(v, hoverColor, strokeWidth, strokeColor);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                setPanelBackground(v, normalColor, strokeWidth, strokeColor);
            }
            return false;
        });
    }

    private void setPanelBackground(View view, int color, int strokeWidth, int strokeColor) {
        view.setBackground(rect(color, 2.0f, strokeWidth, strokeColor));
    }

    private void registerBackPressedCallback() {
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ClientQuestScreenState.close();
            }
        };
        UIManager.getInstance().getOnBackPressedDispatcher().addCallback(backPressedCallback);
    }

    private View divider() {
        View divider = new View(getContext());
        divider.setBackground(rect(DialogueHudTheme.DIVIDER, 0.0f, 0, 0));
        return divider;
    }

    private TextView label(CharSequence text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(getContext(), "", sizeDp * DISPLAY_SCALE, color);
        view.setText(text == null ? "" : text);
        view.setGravity(gravity);
        return view;
    }

    private ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp * DISPLAY_SCALE));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return UIUtils.dp2pxInt(value * DISPLAY_SCALE);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
