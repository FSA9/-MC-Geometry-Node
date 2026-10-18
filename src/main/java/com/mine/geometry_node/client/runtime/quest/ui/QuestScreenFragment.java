package com.mine.geometry_node.client.runtime.quest.ui;

import com.mine.geometry_node.client.ui.components.scroll.UiScrollView;

import com.mine.geometry_node.client.ui.components.scroll.UiHorizontalScrollView;

import com.mine.geometry_node.client.runtime.dialogue.ModernDialogueText;
import com.mine.geometry_node.client.runtime.quest.ClientQuestScreenState;
import com.mine.geometry_node.client.ui.components.icon.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
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
    private static final int SCREEN_PANEL = QuestScreenTheme.withAlpha(QuestScreenTheme.PANEL, 0xA8);
    private static final int BODY_SURFACE = QuestScreenTheme.withAlpha(QuestScreenTheme.SURFACE, 0x62);
    private static final int DETAIL_SURFACE = QuestScreenTheme.withAlpha(QuestScreenTheme.SURFACE, 0x4E);

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
                    QuestScreenTheme.TEXT_MUTED, Gravity.CENTER),
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
                QuestScreenTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        if (!snapshot.actionResult().isBlank()) {
            int color = snapshot.actionSuccessful() ? QuestScreenTheme.SUCCESS : QuestScreenTheme.ERROR;
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
        HorizontalScrollView scroll = new UiHorizontalScrollView(getContext());
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
                    ? QuestScreenTheme.SURFACE_HOVER
                    : QuestScreenTheme.withAlpha(QuestScreenTheme.SURFACE, 0x80);
            TextView tab = label(tr(status.translationKey()) + "  " + countForStatus(snapshot, status.id()),
                    9.5f, selected ? status.color() : QuestScreenTheme.TEXT_MUTED, Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setEllipsize(TextUtils.TruncateAt.END);
            setPanelBackground(tab, baseColor, 0, 0);
            tab.setOnClickListener(v -> {
                if (!status.id().equals(selectedStatusId)) {
                    selectedStatusId = status.id();
                    selectedTaskKey = "";
                    pendingAbandonTaskKey = "";
                    refresh(ClientQuestScreenState.current());
                }
            });
            bindHover(tab, baseColor, QuestScreenTheme.BUTTON_HOVER, 0, 0);
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
        body.addView(divider(), dividerLp);
        body.addView(createDetails(snapshot), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        return body;
    }

    private View createQuestList(PacketQuestScreenSnapshot snapshot) {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(rect(QuestScreenTheme.withAlpha(QuestScreenTheme.PANEL, 0x54), 0.0f, 0, 0));
        column.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView listTitle = label(tr("geometry_node.quest.screen.list"), 9.0f,
                QuestScreenTheme.TEXT_MUTED, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        listTitle.setPadding(dp(6), 0, 0, 0);
        column.addView(listTitle,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        ScrollView scroll = new UiScrollView(getContext());
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(3), 0, 0);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        List<PacketQuestScreenSnapshot.QuestView> quests = questsForStatus(snapshot, selectedStatusId);
        if (quests.isEmpty()) {
            list.addView(label(tr("geometry_node.quest.screen.empty"), 9.5f,
                            QuestScreenTheme.TEXT_MUTED, Gravity.CENTER),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
            return column;
        }

        for (PacketQuestScreenSnapshot.QuestView quest : quests) {
            boolean selected = quest.taskKey().equals(selectedTaskKey);
            int baseColor = selected
                    ? QuestScreenTheme.BUTTON_HOVER
                    : QuestScreenTheme.withAlpha(QuestScreenTheme.SURFACE, 0xA0);
            int color = statusColor(snapshot, quest.statusId());
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            setPanelBackground(row, baseColor, 0, 0);

            View statusMarker = new View(getContext());
            statusMarker.setBackground(rect(color, 0.0f, 0, 0));
            row.addView(statusMarker, new LinearLayout.LayoutParams(dp(6), ViewGroup.LayoutParams.MATCH_PARENT));

            TextView rowTitle = label(ModernDialogueText.display(ModernDialogueText.parse(quest.title())),
                    10.0f, selected ? QuestScreenTheme.TEXT_PRIMARY : QuestScreenTheme.TEXT_MUTED,
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
            bindHover(row, baseColor, QuestScreenTheme.SURFACE_HOVER, 0, 0);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
            rowLp.bottomMargin = dp(3);
            list.addView(row, rowLp);
        }
        return column;
    }

    private View createDetails(PacketQuestScreenSnapshot snapshot) {
        LinearLayout details = new LinearLayout(getContext());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), dp(10), dp(12), dp(10));

        PacketQuestScreenSnapshot.QuestView quest = selectedQuest(snapshot);
        if (quest == null) {
            details.addView(label(tr("geometry_node.quest.screen.select_hint"), 10.0f,
                            QuestScreenTheme.TEXT_MUTED, Gravity.CENTER),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return details;
        }

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(ModernDialogueText.display(ModernDialogueText.parse(quest.title())),
                15.5f, QuestScreenTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        PacketQuestScreenSnapshot.StatusView status = findStatus(snapshot, quest.statusId());
        if (status != null) {
            TextView state = label(tr(status.translationKey()), 9.0f, status.color(), Gravity.CENTER);
            state.setPadding(dp(10), 0, dp(10), 0);
            state.setBackground(rect(QuestScreenTheme.withAlpha(status.color(), 0x18), 0, 0, 0));
            header.addView(state, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)));
        }
        details.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        details.addView(divider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // One scroll surface keeps long descriptions, objectives and rewards together.
        ScrollView scroll = new UiScrollView(getContext());
        scroll.setFillViewport(true);
        LinearLayout main = new LinearLayout(getContext());
        main.setOrientation(LinearLayout.VERTICAL);
        main.addView(createDetailSection("geometry_node.quest.screen.content", createDescriptionContent(quest)));
        LinearLayout.LayoutParams objectivesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        objectivesLp.topMargin = dp(12);
        main.addView(createDetailSection("geometry_node.quest.screen.objectives", createObjectivesContent(quest)), objectivesLp);

        LinearLayout side = new LinearLayout(getContext());
        side.setOrientation(LinearLayout.VERTICAL);
        side.addView(createDetailSection("geometry_node.quest.screen.conditions", createConditionsContent(quest)));
        LinearLayout.LayoutParams rewardsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rewardsLp.topMargin = dp(12);
        side.addView(createDetailSection("geometry_node.quest.screen.rewards", createRewardsContent(quest)), rewardsLp);

        LinearLayout columns = new LinearLayout(getContext());
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        columns.addView(spacer(), new LinearLayout.LayoutParams(dp(12), 1));
        columns.addView(side, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        scroll.addView(columns, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollLp.topMargin = dp(12);
        details.addView(scroll, scrollLp);

        View actions = createActions(snapshot, quest);
        if (actions != null) {
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
            actionLp.topMargin = dp(8);
            details.addView(actions, actionLp);
        }
        return details;
    }

    private View createDetailSection(String titleKey, View content) {
        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(rect(DETAIL_SURFACE, 0.0f, 0, 0));

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(tr(titleKey), 9.0f, QuestScreenTheme.TEXT_PRIMARY,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, dp(6), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        section.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        section.addView(divider(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        section.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return section;
    }

    private View createDescriptionContent(PacketQuestScreenSnapshot.QuestView quest) {
        TextView description = label(ModernDialogueText.display(ModernDialogueText.parse(quest.description())),
                10.5f, QuestScreenTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.TOP);
        description.setPadding(dp(10), dp(9), dp(10), dp(9));
        return description;
    }

    private View createConditionsContent(PacketQuestScreenSnapshot.QuestView quest) {
        LinearLayout columns = new LinearLayout(getContext());
        columns.setOrientation(LinearLayout.VERTICAL);
        columns.setPadding(dp(9), dp(7), dp(9), dp(8));
        columns.addView(createConditionColumn(
                        "geometry_node.quest.screen.acceptance_conditions", quest.acceptanceConditions()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        columns.addView(spacer(), new LinearLayout.LayoutParams(1, dp(8)));
        columns.addView(createConditionColumn(
                        "geometry_node.quest.screen.completion_conditions", quest.completionConditions()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return columns;
    }

    private View createConditionColumn(
            String titleKey,
            List<PacketQuestScreenSnapshot.ConditionView> conditions) {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(tr(titleKey), 8.5f, QuestScreenTheme.WARNING,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        List<PacketQuestScreenSnapshot.ConditionView> values = conditions != null ? conditions : List.of();
        for (PacketQuestScreenSnapshot.ConditionView condition : values) {
            int stateColor = !condition.evaluated()
                    ? QuestScreenTheme.TEXT_MUTED
                    : condition.allowed() ? QuestScreenTheme.SUCCESS : QuestScreenTheme.ERROR;
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackground(rect(
                    QuestScreenTheme.withAlpha(stateColor, 0x24),
                    0.0f, 0, 0));
            View marker = new View(getContext());
            marker.setBackground(rect(stateColor, 0.0f, 0, 0));
            row.addView(marker, new LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT));
            TextView text = label(condition.text(), 9.0f, QuestScreenTheme.TEXT_PRIMARY,
                    Gravity.LEFT | Gravity.CENTER_VERTICAL);
            text.setMinHeight(dp(26));
            text.setPadding(dp(7), dp(3), dp(6), dp(3));
            row.addView(text, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(2);
            list.addView(row, rowLp);
        }
        column.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    private View createObjectivesContent(PacketQuestScreenSnapshot.QuestView quest) {
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        for (PacketQuestScreenSnapshot.ObjectiveView objective : quest.objectives()) {
            LinearLayout.LayoutParams objectiveLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            objectiveLp.bottomMargin = dp(4);
            list.addView(createObjectiveView(objective), objectiveLp);
        }
        return list;
    }

    private View createRewardsContent(PacketQuestScreenSnapshot.QuestView quest) {
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        for (PacketQuestScreenSnapshot.RewardView reward : quest.rewards()) {
            LinearLayout.LayoutParams rewardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rewardLp.bottomMargin = dp(4);
            list.addView(createRewardView(reward), rewardLp);
        }
        return list;
    }

    private View createObjectiveView(PacketQuestScreenSnapshot.ObjectiveView objective) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(7), dp(5), dp(7), dp(5));
        boolean completed = objective.counterEnabled()
                && objective.targetValue() > 0.0
                && objective.currentValue() >= objective.targetValue();
        row.setBackground(rect(
                completed ? QuestScreenTheme.withAlpha(QuestScreenTheme.SUCCESS, 0x14)
                        : QuestScreenTheme.withAlpha(QuestScreenTheme.SURFACE, 0x48),
                0.0f, 0, 0));

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
                10.0f, completed ? QuestScreenTheme.SUCCESS : QuestScreenTheme.TEXT_PRIMARY,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        content.setMinHeight(dp(30));
        contentRow.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        if (objective.quantityEnabled()) {
            String quantity = objective.counterEnabled()
                    ? formatNumber(objective.currentValue()) + " / " + formatNumber(objective.targetValue())
                    : "x " + formatNumber(objective.targetValue());
            TextView amount = label(quantity, 9.0f, QuestScreenTheme.TEXT_MUTED,
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
            amountLp.leftMargin = dp(6);
            contentRow.addView(amount, amountLp);
        }

        row.addView(contentRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (objective.counterEnabled()) {
            QuestProgressBar progress = new QuestProgressBar(
                    getContext(), objective.currentValue(), objective.targetValue());
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
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
                QuestScreenTheme.withAlpha(QuestScreenTheme.SURFACE, 0x48),
                0.0f, 0, 0));

        QuestHintType hintType = QuestHintType.fromId(reward.hintType());
        if (hintType != QuestHintType.NONE) {
            QuestHintView hint = new QuestHintView(getContext());
            hint.setHint(hintType, reward.hintValue());
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(dp(30), dp(30));
            hintLp.rightMargin = dp(7);
            row.addView(hint, hintLp);
        }

        TextView content = label(ModernDialogueText.display(ModernDialogueText.parse(reward.content())),
                10.0f, QuestScreenTheme.TEXT_PRIMARY, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        content.setMinHeight(dp(30));
        row.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView amount = label("x " + formatNumber(reward.amount()), 9.5f,
                QuestScreenTheme.TEXT_MUTED, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
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
                            QuestScreenTheme.BUTTON, QuestScreenTheme.BUTTON_HOVER,
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
                    QuestScreenTheme.withAlpha(QuestScreenTheme.ERROR, 0x82),
                    QuestScreenTheme.withAlpha(QuestScreenTheme.ERROR, 0xB8),
                    () -> abandonQuest(quest));
            actions.addView(abandon, new LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.MATCH_PARENT));

            TextView submit = actionButton(tr("geometry_node.quest.screen.submit"),
                    QuestScreenTheme.BUTTON, QuestScreenTheme.BUTTON_HOVER,
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
        return status != null ? status.color() : QuestScreenTheme.TEXT_MUTED;
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
        bindHover(button, 0x00000000, QuestScreenTheme.BUTTON_HOVER, 0, 0);
        VectorIconView icon = new VectorIconView(getContext(), VectorIconView.Kind.CLOSE, QuestScreenTheme.TEXT_MUTED);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(17), dp(17));
        iconLp.gravity = Gravity.CENTER;
        button.addView(icon, iconLp);
        return button;
    }

    private void bindHover(View view, int normalColor, int hoverColor, int strokeWidth, int strokeColor) {
        ShapeDrawable background = rect(normalColor, 0, strokeWidth, strokeColor);
        view.setBackground(background);
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                background.setColor(hoverColor);
                v.invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                background.setColor(normalColor);
                v.invalidate();
            }
            return false;
        });
    }

    private void setPanelBackground(View view, int color, int strokeWidth, int strokeColor) {
        view.setBackground(rect(color, 0, strokeWidth, strokeColor));
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
        divider.setBackground(rect(QuestScreenTheme.DIVIDER, 0.0f, 0, 0));
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
