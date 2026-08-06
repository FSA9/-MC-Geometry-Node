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
    private static final float TEXT_SCALE = 1.7f;
    private static final int SCREEN_HORIZONTAL_PADDING_DP = 38;
    private static final int SCREEN_VERTICAL_PADDING_DP = 22;
    private static final int LIST_WIDTH_DP = 218;
    private static final String ALL_STATUS_ID = "all";
    private static final String ALL_STATUS_TRANSLATION_KEY = "geometry_node.quest.status.all";
    private static final int ALL_STATUS_COLOR = 0xFFE6E6E6;
    private static final int SCREEN_DIM = 0x52000000;
    private static final int SCREEN_PANEL = DialogueHudTheme.withAlpha(DialogueHudTheme.PANEL, 0xA8);
    private static final int BODY_SURFACE = DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0x62);
    private static final int DETAIL_SURFACE = DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0x4E);

    private FrameLayout root;
    private LinearLayout screenContent;
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
        root.setClipChildren(false);
        root.setBackground(rect(SCREEN_DIM, 0.0f, 0, 0));
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

        screenContent = new LinearLayout(context);
        screenContent.setClipChildren(false);
        screenContent.setOrientation(LinearLayout.VERTICAL);
        screenContent.setPadding(dp(SCREEN_HORIZONTAL_PADDING_DP), dp(SCREEN_VERTICAL_PADDING_DP),
                dp(SCREEN_HORIZONTAL_PADDING_DP), dp(SCREEN_VERTICAL_PADDING_DP));
        screenContent.setBackground(rect(SCREEN_PANEL, 0.0f, 0, 0));
        screenContent.setOnClickListener(v -> {
        });
        root.addView(screenContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
        if (screenContent == null) return;
        screenContent.setAlpha(1.0f);
        screenContent.removeAllViews();

        if (snapshot == null) {
            screenContent.addView(label(tr("geometry_node.quest.screen.unavailable"), 11.5f,
                    DialogueHudTheme.TEXT_MUTED, Gravity.CENTER),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        normalizeSelection(snapshot);
        screenContent.addView(createHeader(snapshot), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        screenContent.addView(divider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        screenContent.addView(createStatusBar(snapshot), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        bodyLp.topMargin = dp(6);
        screenContent.addView(createBody(snapshot), bodyLp);
    }

    private View createHeader(PacketQuestScreenSnapshot snapshot) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = label(tr("geometry_node.quest.screen.title"), 14.5f,
                DialogueHudTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        if (!snapshot.actionResult().isBlank()) {
            int color = snapshot.actionSuccessful() ? DialogueHudTheme.SUCCESS : DialogueHudTheme.ERROR;
            String feedbackText = snapshot.actionMessage().isBlank()
                    ? actionResult(snapshot.actionResult())
                    : snapshot.actionMessage();
            TextView feedback = label(feedbackText, 9.5f, color,
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            feedback.setSingleLine(true);
            feedback.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams feedbackLp = new LinearLayout.LayoutParams(dp(210), ViewGroup.LayoutParams.MATCH_PARENT);
            feedbackLp.rightMargin = dp(8);
            header.addView(feedback, feedbackLp);
        }

        header.addView(closeButton(), new LinearLayout.LayoutParams(dp(28), dp(28)));
        return header;
    }

    private View createStatusBar(PacketQuestScreenSnapshot snapshot) {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                float amount = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (amount != 0.0f) {
                    scroll.scrollBy((int) (-amount * dp(36)), 0);
                    return true;
                }
            }
            return false;
        });

        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(4), 0, 0);

        List<PacketQuestScreenSnapshot.StatusView> statusTabs = new ArrayList<>(snapshot.statuses());
        statusTabs.add(new PacketQuestScreenSnapshot.StatusView(
                ALL_STATUS_ID, ALL_STATUS_TRANSLATION_KEY, ALL_STATUS_COLOR, false));
        for (PacketQuestScreenSnapshot.StatusView status : statusTabs) {
            boolean selected = status.id().equals(selectedStatusId);
            int baseColor = selected
                    ? DialogueHudTheme.SURFACE_HOVER
                    : DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0x80);
            TextView tab = label(tr(status.translationKey()) + "  " + countForStatus(snapshot, status.id()),
                    9.5f, selected ? status.color() : DialogueHudTheme.TEXT_MUTED, Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setEllipsize(TextUtils.TruncateAt.END);
            setSquareBackground(tab, baseColor, selected ? 1 : 0, status.color());
            tab.setOnClickListener(v -> {
                if (!status.id().equals(selectedStatusId)) {
                    selectedStatusId = status.id();
                    selectedTaskKey = "";
                    pendingAbandonTaskKey = "";
                    refresh(ClientQuestScreenState.current());
                }
            });
            bindSquareHover(tab, baseColor, DialogueHudTheme.BUTTON_HOVER, selected ? 1 : 0, status.color());
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(dp(104), dp(30));
            if (bar.getChildCount() > 0) tabLp.leftMargin = dp(3);
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
        body.setClipChildren(false);
        body.setBackground(rect(BODY_SURFACE, 0.0f, 0, 0));
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
        column.setBackground(rect(DialogueHudTheme.withAlpha(DialogueHudTheme.PANEL, 0x54), 0.0f, 0, 0));
        column.setPadding(dp(10), dp(8), dp(8), dp(8));
        TextView listTitle = label(tr("geometry_node.quest.screen.list"), 9.0f,
                DialogueHudTheme.TEXT_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        listTitle.setPadding(dp(6), 0, 0, 0);
        column.addView(listTitle,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(3), 0, 0);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        List<PacketQuestScreenSnapshot.QuestView> quests = questsForStatus(snapshot, selectedStatusId);
        if (quests.isEmpty()) {
            list.addView(label(tr("geometry_node.quest.screen.empty"), 9.5f,
                            DialogueHudTheme.TEXT_MUTED, Gravity.CENTER),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
            return column;
        }

        for (PacketQuestScreenSnapshot.QuestView quest : quests) {
            boolean selected = quest.taskKey().equals(selectedTaskKey);
            int baseColor = selected
                    ? DialogueHudTheme.BUTTON_HOVER
                    : DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0xA0);
            int color = statusColor(snapshot, quest.statusId());
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            setSquareBackground(row, baseColor, 0, 0);

            View statusMarker = new View(getContext());
            statusMarker.setBackground(rect(color, 0.0f, 0, 0));
            row.addView(statusMarker, new LinearLayout.LayoutParams(dp(6), ViewGroup.LayoutParams.MATCH_PARENT));

            TextView rowTitle = label(ModernDialogueText.display(ModernDialogueText.parse(quest.title())),
                    10.0f, selected ? DialogueHudTheme.TEXT_PRIMARY : DialogueHudTheme.TEXT_MUTED,
                    Gravity.LEFT | Gravity.CENTER_VERTICAL);
            rowTitle.setSingleLine(true);
            rowTitle.setEllipsize(TextUtils.TruncateAt.END);
            rowTitle.setPadding(dp(9), 0, dp(7), 0);
            rowTitle.setClickable(false);
            row.addView(rowTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            row.setOnClickListener(v -> {
                selectedTaskKey = quest.taskKey();
                pendingAbandonTaskKey = "";
                refresh(ClientQuestScreenState.current());
            });
            bindSquareHover(row, baseColor, DialogueHudTheme.SURFACE_HOVER, 0, 0);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
            rowLp.bottomMargin = dp(2);
            list.addView(row, rowLp);
        }
        return column;
    }

    private View createDetails(PacketQuestScreenSnapshot snapshot) {
        LinearLayout details = new LinearLayout(getContext());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(16), dp(10), dp(14), dp(10));

        PacketQuestScreenSnapshot.QuestView quest = selectedQuest(snapshot);
        if (quest == null) {
            details.addView(label(tr("geometry_node.quest.screen.select_hint"), 10.0f,
                            DialogueHudTheme.TEXT_MUTED, Gravity.CENTER),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return details;
        }

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(ModernDialogueText.display(ModernDialogueText.parse(quest.title())),
                15.5f, DialogueHudTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        details.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        details.addView(divider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topRow = detailRow(
                createDetailSection("geometry_node.quest.screen.content", DialogueHudTheme.ACCENT,
                        createDescriptionContent(quest)),
                createDetailSection("geometry_node.quest.screen.conditions", DialogueHudTheme.WARNING,
                        createConditionsContent(quest)));
        grid.addView(topRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        grid.addView(spacer(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        LinearLayout bottomRow = detailRow(
                createDetailSection("geometry_node.quest.screen.objectives", DialogueHudTheme.ACCENT,
                        createObjectivesContent(quest)),
                createDetailSection("geometry_node.quest.screen.rewards", DialogueHudTheme.SUCCESS,
                        createRewardsContent(quest)));
        grid.addView(bottomRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        gridLp.topMargin = dp(8);
        details.addView(grid, gridLp);

        View actions = createActions(snapshot, quest);
        if (actions != null) {
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
            actionLp.topMargin = dp(8);
            details.addView(actions, actionLp);
        }
        return details;
    }

    private LinearLayout detailRow(View left, View right) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        row.addView(spacer(), new LinearLayout.LayoutParams(dp(8), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        return row;
    }

    private View createDetailSection(String titleKey, int markerColor, View content) {
        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(rect(DETAIL_SURFACE, 0.0f, 0, 0));

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        View marker = new View(getContext());
        marker.setBackground(rect(markerColor, 0.0f, 0, 0));
        header.addView(marker, new LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = label(tr(titleKey), 9.0f, DialogueHudTheme.TEXT_MUTED,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, dp(6), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        section.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        section.addView(divider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        section.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        return section;
    }

    private View createDescriptionContent(PacketQuestScreenSnapshot.QuestView quest) {
        ScrollView scroll = new ScrollView(getContext());
        TextView description = label(ModernDialogueText.display(ModernDialogueText.parse(quest.description())),
                10.5f, DialogueHudTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.TOP);
        description.setPadding(dp(10), dp(9), dp(10), dp(9));
        scroll.addView(description, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View createConditionsContent(PacketQuestScreenSnapshot.QuestView quest) {
        LinearLayout columns = new LinearLayout(getContext());
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(dp(9), dp(7), dp(9), dp(8));
        columns.addView(createConditionColumn(
                        "geometry_node.quest.screen.acceptance_conditions", quest.acceptanceConditions()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        columns.addView(spacer(), new LinearLayout.LayoutParams(dp(7), ViewGroup.LayoutParams.MATCH_PARENT));
        columns.addView(divider(), new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT));
        columns.addView(spacer(), new LinearLayout.LayoutParams(dp(7), ViewGroup.LayoutParams.MATCH_PARENT));
        columns.addView(createConditionColumn(
                        "geometry_node.quest.screen.completion_conditions", quest.completionConditions()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        return columns;
    }

    private View createConditionColumn(
            String titleKey,
            List<PacketQuestScreenSnapshot.ConditionView> conditions) {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(tr(titleKey), 8.5f, DialogueHudTheme.WARNING,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        List<PacketQuestScreenSnapshot.ConditionView> values = conditions != null ? conditions : List.of();
        for (PacketQuestScreenSnapshot.ConditionView condition : values) {
            int stateColor = !condition.evaluated()
                    ? DialogueHudTheme.TEXT_MUTED
                    : condition.allowed() ? DialogueHudTheme.SUCCESS : DialogueHudTheme.ERROR;
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackground(rect(
                    DialogueHudTheme.withAlpha(stateColor, 0x24),
                    0.0f,
                    1,
                    DialogueHudTheme.withAlpha(stateColor, 0xA0)));
            View marker = new View(getContext());
            marker.setBackground(rect(stateColor, 0.0f, 0, 0));
            row.addView(marker, new LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT));
            TextView text = label(condition.text(), 9.0f, DialogueHudTheme.TEXT_PRIMARY,
                    Gravity.LEFT | Gravity.CENTER_VERTICAL);
            text.setMinHeight(dp(26));
            text.setPadding(dp(7), dp(3), dp(6), dp(3));
            row.addView(text, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(2);
            list.addView(row, rowLp);
        }
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        return column;
    }

    private View createObjectivesContent(PacketQuestScreenSnapshot.QuestView quest) {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        for (PacketQuestScreenSnapshot.ObjectiveView objective : quest.objectives()) {
            LinearLayout.LayoutParams objectiveLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            objectiveLp.bottomMargin = dp(4);
            list.addView(createObjectiveView(objective), objectiveLp);
        }
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View createRewardsContent(PacketQuestScreenSnapshot.QuestView quest) {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        for (PacketQuestScreenSnapshot.RewardView reward : quest.rewards()) {
            LinearLayout.LayoutParams rewardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rewardLp.bottomMargin = dp(4);
            list.addView(createRewardView(reward), rewardLp);
        }
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View createObjectiveView(PacketQuestScreenSnapshot.ObjectiveView objective) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(7), dp(5), dp(7), dp(5));
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
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(dp(30), dp(30));
            hintLp.rightMargin = dp(7);
            contentRow.addView(hint, hintLp);
        }

        TextView content = label(ModernDialogueText.display(ModernDialogueText.parse(objective.content())),
                10.0f, completed ? DialogueHudTheme.SUCCESS : DialogueHudTheme.TEXT_PRIMARY,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        contentRow.addView(content, new LinearLayout.LayoutParams(0, dp(30), 1.0f));

        if (objective.quantityEnabled()) {
            String quantity = objective.counterEnabled()
                    ? formatNumber(objective.currentValue()) + " / " + formatNumber(objective.targetValue())
                    : "x " + formatNumber(objective.targetValue());
            TextView amount = label(quantity, 9.0f, DialogueHudTheme.TEXT_MUTED,
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(dp(86), dp(30));
            amountLp.leftMargin = dp(6);
            contentRow.addView(amount, amountLp);
        }

        row.addView(contentRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        if (objective.counterEnabled()) {
            QuestProgressBar progress = new QuestProgressBar(
                    getContext(), objective.currentValue(), objective.targetValue());
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
            progressLp.topMargin = dp(4);
            row.addView(progress, progressLp);
        }
        return row;
    }

    private View createRewardView(PacketQuestScreenSnapshot.RewardView reward) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(5), dp(7), dp(5));
        row.setBackground(rect(
                DialogueHudTheme.withAlpha(DialogueHudTheme.SURFACE, 0xA8),
                2.0f, 1, DialogueHudTheme.DIVIDER));

        QuestHintType hintType = QuestHintType.fromId(reward.hintType());
        if (hintType != QuestHintType.NONE) {
            QuestHintView hint = new QuestHintView(getContext());
            hint.setHint(hintType, reward.hintValue());
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(dp(30), dp(30));
            hintLp.rightMargin = dp(7);
            row.addView(hint, hintLp);
        }

        TextView content = label(ModernDialogueText.display(ModernDialogueText.parse(reward.content())),
                10.0f, DialogueHudTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(content, new LinearLayout.LayoutParams(0, dp(30), 1.0f));

        TextView amount = label("x " + formatNumber(reward.amount()), 9.5f,
                DialogueHudTheme.TEXT_MUTED, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(dp(86), dp(30));
        amountLp.leftMargin = dp(6);
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
        TextView button = label(text, 10.0f, 0xFFFFFFFF, Gravity.CENTER);
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
        screenContent.setAlpha(0.76f);
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

    private void bindSquareHover(View view, int normalColor, int hoverColor, int strokeWidth, int strokeColor) {
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                setSquareBackground(v, hoverColor, strokeWidth, strokeColor);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                setSquareBackground(v, normalColor, strokeWidth, strokeColor);
            }
            return false;
        });
    }

    private void setSquareBackground(View view, int color, int strokeWidth, int strokeColor) {
        view.setBackground(rect(color, 0.0f, strokeWidth, strokeColor));
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

    private View spacer() {
        return new View(getContext());
    }

    private TextView label(CharSequence text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(getContext(), "", sizeDp * TEXT_SCALE, color);
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
