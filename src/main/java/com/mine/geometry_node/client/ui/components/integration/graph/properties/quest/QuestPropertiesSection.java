package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

import com.mine.geometry_node.client.ui.components.integration.faction.FactionRichField;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.GraphPropertiesTarget;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.View;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.EnumMap;
import java.util.function.Supplier;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.solid;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.tr;

/** Quest-specific content owned by the graph-properties panel. */
public final class QuestPropertiesSection extends LinearLayout {
    private enum TextField {
        TITLE("title"), DESCRIPTION("description");

        final String label;
        TextField(String label) { this.label = label; }

        RichTextValue read(QuestDefinition definition) {
            return switch (this) {
                case TITLE -> definition.title();
                case DESCRIPTION -> definition.description();
            };
        }
    }

    private final Runnable mOnChanged;
    private final LinearLayout mTextFieldsHost;
    private final EnumMap<TextField, FactionRichField> mTextFields = new EnumMap<>(TextField.class);
    private final EnumMap<QuestConditionKind, QuestConditionsEditor> mConditionsEditors = new EnumMap<>(QuestConditionKind.class);
    private final EnumMap<QuestConditionKind, UiActionButton> mConditionTabs = new EnumMap<>(QuestConditionKind.class);
    private final EnumMap<QuestConditionKind, View> mConditionUnderlines = new EnumMap<>(QuestConditionKind.class);
    private final QuestEntryUi.SectionHeader mConditionsHeader;
    private QuestConditionKind mSelectedCondition = QuestConditionKind.VISIBILITY;
    private final QuestObjectivesEditor mObjectivesEditor;
    private final QuestRewardsEditor mRewardsEditor;

    private QuestDefinition mDefinition = QuestDefinition.EMPTY;
    private int mGeneration;
    private boolean mHasDefinition;
    private boolean mUpdating;

    public QuestPropertiesSection(Context context, Runnable onChanged, Runnable onPreview,
                                  Supplier<GraphPropertiesTarget> captureTarget) {
        super(context);
        mOnChanged = onChanged;
        setOrientation(VERTICAL);

        TextView preview = GraphPropertiesUi.button(context,
                tr("geometry_node.button.nativepreview"),
                tr("geometry_node.graph_properties.quest.nativepreview"),
                UiThemes.EDITOR.text().normal(), GraphPropertiesUi.ACTION_BACKGROUND, GraphPropertiesUi.ACTION_HOVER);
        UIUtils.setLockedTextSize(preview, 11.0f);
        preview.setOnClickListener(v -> {
            if (onPreview != null) onPreview.run();
        });
        LayoutParams previewParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32));
        previewParams.topMargin = UIUtils.dp2pxInt(8);
        previewParams.bottomMargin = UIUtils.dp2pxInt(16);
        addView(preview, previewParams);

        mTextFieldsHost = new LinearLayout(context);
        mTextFieldsHost.setOrientation(VERTICAL);
        addView(mTextFieldsHost, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout conditionsBody = new LinearLayout(context);
        conditionsBody.setOrientation(VERTICAL);
        mConditionsHeader = new QuestEntryUi.SectionHeader(context,
                "geometry_node.graph_properties.quest.conditions", "geometry_node.graph_properties.quest.condition_add",
                conditionsBody, () -> mConditionsEditors.get(mSelectedCondition).addCondition());
        addDivider();
        addView(mConditionsHeader, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(conditionsBody, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout tabs = new LinearLayout(context);
        tabs.setBaselineAligned(false);
        conditionsBody.addView(tabs, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(34)));
        View tabLine = new View(context);
        tabLine.setBackground(solid(UiThemes.EDITOR.border().divider()));
        conditionsBody.addView(tabLine, new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1)));
        for (QuestConditionKind kind : QuestConditionKind.values()) {
            addConditionTab(tabs, kind);
            QuestConditionsEditor editor = new QuestConditionsEditor(context, this::changed, this::updateConditionTabs);
            mConditionsEditors.put(kind, editor);
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = UIUtils.dp2pxInt(8);
            conditionsBody.addView(editor, params);
        }
        updateConditionTabs();

        addDivider();
        mObjectivesEditor = new QuestObjectivesEditor(context, this::changed, captureTarget);
        LayoutParams objectiveParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(mObjectivesEditor, objectiveParams);

        addDivider();
        mRewardsEditor = new QuestRewardsEditor(context, this::changed, captureTarget);
        LayoutParams rewardParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(mRewardsEditor, rewardParams);
    }

    public void setDefinition(QuestDefinition definition) {
        mUpdating = true;
        mDefinition = definition != null ? definition : QuestDefinition.EMPTY;
        if (mTextFields.isEmpty()) createTextFields();
        mTextFields.forEach((field, input) -> input.refresh(field.read(mDefinition)));
        mConditionsEditors.forEach((kind, editor) -> editor.setConditions(mDefinition.conditionDescriptions(kind)));
        mObjectivesEditor.setObjectivesPreservingFocusedEdit(mDefinition.objectives());
        mRewardsEditor.setRewardsPreservingFocusedEdit(mDefinition.rewards());
        mHasDefinition = true;
        mUpdating = false;
    }

    public void clear() {
        mGeneration++;
        mUpdating = true;
        mDefinition = QuestDefinition.EMPTY;
        mHasDefinition = false;
        // Retire the old controls so an open editor cannot submit into another document.
        mTextFields.values().forEach(input -> input.setEditable(false));
        mTextFields.clear();
        mTextFieldsHost.removeAllViews();
        mConditionsEditors.values().forEach(QuestConditionsEditor::clear);
        mObjectivesEditor.setObjectives(java.util.List.of());
        mRewardsEditor.setRewards(java.util.List.of());
        mUpdating = false;
    }

    public QuestDefinition definition() {
        mDefinition = new QuestDefinition(
                mDefinition.title(), mDefinition.description(),
                mObjectivesEditor.objectives(), mRewardsEditor.rewards(),
                mConditionsEditors.get(QuestConditionKind.VISIBILITY).conditions(),
                mConditionsEditors.get(QuestConditionKind.ACCEPTANCE).conditions(),
                mConditionsEditors.get(QuestConditionKind.COMPLETION).conditions());
        return mDefinition;
    }

    private void createTextFields() {
        int generation = mGeneration;
        for (TextField field : TextField.values()) {
            TextView title = label(getContext(), tr("geometry_node.graph_properties.quest." + field.label),
                    10.5f, UiThemes.EDITOR.text().muted());
            LayoutParams titleParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(20));
            if (field != TextField.TITLE) titleParams.topMargin = UIUtils.dp2pxInt(8);
            mTextFieldsHost.addView(title, titleParams);
            FactionRichField input = new FactionRichField(getContext(), UiThemes.EDITOR,
                    tr("geometry_node.graph_properties.quest.edit_rich_text"), field != TextField.TITLE, value -> {
                        if (generation != mGeneration || !mHasDefinition || mUpdating) return;
                        updateText(field, value);
                    });
            mTextFields.put(field, input);
            mTextFieldsHost.addView(input, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void addDivider() {
        View divider = new View(getContext());
        divider.setBackground(solid(UiThemes.EDITOR.border().divider()));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(1));
        params.topMargin = UIUtils.dp2pxInt(16);
        addView(divider, params);
    }

    private static String conditionTitle(QuestConditionKind kind) {
        return tr("geometry_node.graph_properties.quest." + switch (kind) {
            case VISIBILITY -> "visibility_conditions";
            case ACCEPTANCE -> "acceptance_conditions";
            case COMPLETION -> "completion_conditions";
        });
    }

    private void addConditionTab(LinearLayout tabs, QuestConditionKind kind) {
        UiActionButton button = new UiActionButton(getContext(), UiThemes.EDITOR, conditionTitle(kind),
                UiActionButton.Role.QUIET, UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, 0, 0, 0.4f));
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setSingleLine(true);
        UIUtils.setLockedTextSize(button, 10.5f);
        button.setTag(kind);
        button.setOnClickListener(v -> {mSelectedCondition = kind; updateConditionTabs();});
        // The full-width underline must not determine a wrap-content tab's width.
        FrameLayout host = new FrameLayout(getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                button.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec), MeasureSpec.AT_MOST), heightSpec);
                super.onMeasure(MeasureSpec.makeMeasureSpec(button.getMeasuredWidth(), MeasureSpec.EXACTLY), heightSpec);
            }
        };
        host.addView(button, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        View underline = new View(getContext());
        underline.setBackground(solid(UiThemes.EDITOR.selection().outline()));
        host.addView(underline, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(2), Gravity.BOTTOM));
        LayoutParams tabParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        if (!mConditionTabs.isEmpty()) tabParams.leftMargin = UIUtils.dp2pxInt(18);
        tabs.addView(host, tabParams);
        mConditionTabs.put(kind, button);
        mConditionUnderlines.put(kind, underline);
    }

    private void updateConditionTabs() {
        int total = 0;
        for (var entry : mConditionsEditors.entrySet()) {
            QuestConditionKind kind = entry.getKey();
            QuestConditionsEditor editor = entry.getValue();
            boolean selected = kind == mSelectedCondition;
            total += editor.count();
            editor.setVisibility(selected ? View.VISIBLE : View.GONE);
            UiActionButton tab = mConditionTabs.get(kind);
            tab.setText(conditionTitle(kind) + "  " + editor.count());
            tab.setSelected(selected);
            tab.setPalette(new UiActionButton.Palette(0, UiThemes.EDITOR.control().iconHover(),
                    UiThemes.EDITOR.control().iconPressed(), selected
                    ? UiThemes.EDITOR.text().normal() : UiThemes.EDITOR.text().muted()));
            mConditionUnderlines.get(kind).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
        mConditionsHeader.setCount(total);
        QuestConditionsEditor current = mConditionsEditors.get(mSelectedCondition);
        mConditionsHeader.setAddEnabled(current != null && current.canAdd());
    }

    private void updateText(TextField field, RichTextValue value) {
        QuestDefinition current = definition();
        mDefinition = new QuestDefinition(
                field == TextField.TITLE ? value : current.title(),
                field == TextField.DESCRIPTION ? value : current.description(),
                current.objectives(), current.rewards(),
                current.visibilityConditions(), current.acceptanceConditions(), current.completionConditions());
        mTextFields.get(field).refresh(value);
        changed();
    }

    private void changed() {
        if (!mUpdating && mOnChanged != null) mOnChanged.run();
    }
}
