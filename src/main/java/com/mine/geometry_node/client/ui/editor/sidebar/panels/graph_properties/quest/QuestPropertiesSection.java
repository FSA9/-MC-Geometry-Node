package com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.quest;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays.ExpandedTextInputOverlay;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.rect;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.styleButton;
import static com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.utils.GraphPropertiesUi.tr;

/**
 * Quest-specific content owned by the graph-properties panel.
 */
public final class QuestPropertiesSection extends LinearLayout {
    private static final int COLOR_SECTION = 0xFF292929;
    private static final int COLOR_INPUT = 0xFF242424;
    private static final int COLOR_INPUT_BORDER = 0xFF4A4A4A;
    private static final int COLOR_LABEL = 0xFFA8A8A8;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF777777;
    private static final int COLOR_BUTTON = 0xFF4A4A4A;
    private static final int COLOR_BUTTON_HOVER = 0xFF5A5A5A;

    private final Runnable mOnChanged;
    private final Runnable mOnPreview;
    private final EditText mTitleInput;
    private final EditText mDescriptionInput;
    private final QuestConditionOverviewView mConditionOverview;
    private final QuestObjectivesEditor mObjectivesEditor;
    private final QuestRewardsEditor mRewardsEditor;

    private QuestDefinition mDefinition = QuestDefinition.EMPTY;
    private int mGeneration;
    private boolean mHasDefinition;
    private boolean mUpdating;

    public QuestPropertiesSection(Context context, Runnable onChanged, Runnable onPreview) {
        super(context);
        mOnChanged = onChanged;
        mOnPreview = onPreview;
        setOrientation(VERTICAL);

        TextView sectionTitle = label(context,
                tr("geometry_node.graph_properties.quest.section"), 10.5f, COLOR_LABEL);
        sectionTitle.setBackground(rect(COLOR_SECTION, 0.0f, 0, 0));
        sectionTitle.setPadding(UIUtils.dp2pxInt(6), 0, UIUtils.dp2pxInt(6), 0);
        LayoutParams sectionTitleParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(24));
        sectionTitleParams.topMargin = UIUtils.dp2pxInt(10);
        sectionTitleParams.bottomMargin = UIUtils.dp2pxInt(5);
        addView(sectionTitle, sectionTitleParams);

        TextView preview = actionButton(
                tr("geometry_node.button.nativepreview"),
                tr("geometry_node.graph_properties.quest.nativepreview"));
        preview.setOnClickListener(v -> {
            if (mOnPreview != null) mOnPreview.run();
        });
        LayoutParams previewParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(28));
        previewParams.bottomMargin = UIUtils.dp2pxInt(8);
        addView(preview, previewParams);

        addView(label(context, tr("geometry_node.graph_properties.quest.title"), 10.5f, COLOR_LABEL),
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(20)));
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        mTitleInput = questInput(true, tr("geometry_node.graph_properties.quest.title_placeholder"));
        mTitleInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) changed();
        });
        titleRow.addView(mTitleInput, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        TextView editTitle = richTextButton();
        editTitle.setOnClickListener(v -> openTitleEditor(editTitle));
        LayoutParams titleButtonParams = new LayoutParams(
                UIUtils.dp2pxInt(30), ViewGroup.LayoutParams.MATCH_PARENT);
        titleButtonParams.leftMargin = UIUtils.dp2pxInt(6);
        titleRow.addView(editTitle, titleButtonParams);
        addView(titleRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

        TextView descriptionLabel = label(context,
                tr("geometry_node.graph_properties.quest.description"), 10.5f, COLOR_LABEL);
        LayoutParams descriptionLabelParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(20));
        descriptionLabelParams.topMargin = UIUtils.dp2pxInt(8);
        addView(descriptionLabel, descriptionLabelParams);
        LinearLayout descriptionRow = new LinearLayout(context);
        descriptionRow.setOrientation(LinearLayout.HORIZONTAL);
        descriptionRow.setGravity(Gravity.TOP);
        mDescriptionInput = questInput(
                false, tr("geometry_node.graph_properties.quest.description_placeholder"));
        mDescriptionInput.setMinLines(3);
        mDescriptionInput.setGravity(Gravity.LEFT | Gravity.TOP);
        mDescriptionInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) changed();
        });
        descriptionRow.addView(mDescriptionInput,
                new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        TextView editDescription = richTextButton();
        editDescription.setOnClickListener(v -> openDescriptionEditor(editDescription));
        LayoutParams descriptionButtonParams = new LayoutParams(
                UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(30));
        descriptionButtonParams.leftMargin = UIUtils.dp2pxInt(6);
        descriptionRow.addView(editDescription, descriptionButtonParams);
        addView(descriptionRow,
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(76)));

        mConditionOverview = new QuestConditionOverviewView(context);
        LayoutParams conditionParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        conditionParams.topMargin = UIUtils.dp2pxInt(8);
        addView(mConditionOverview, conditionParams);

        mObjectivesEditor = new QuestObjectivesEditor(context, this::changed);
        LayoutParams objectiveParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        objectiveParams.topMargin = UIUtils.dp2pxInt(8);
        addView(mObjectivesEditor, objectiveParams);

        mRewardsEditor = new QuestRewardsEditor(context, this::changed);
        LayoutParams rewardParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rewardParams.topMargin = UIUtils.dp2pxInt(8);
        addView(mRewardsEditor, rewardParams);
    }

    public void setDefinition(QuestDefinition definition, QuestConditionOverview conditionOverview) {
        QuestDefinition value = definition != null ? definition : QuestDefinition.EMPTY;
        String pendingTitle = mHasDefinition && mTitleInput.hasFocus()
                ? mTitleInput.getText().toString()
                : null;
        String pendingDescription = mHasDefinition && mDescriptionInput.hasFocus()
                ? mDescriptionInput.getText().toString()
                : null;
        RichTextValue title = preservePendingText(value.title(), pendingTitle);
        RichTextValue description = preservePendingText(value.description(), pendingDescription);

        mUpdating = true;
        mDefinition = new QuestDefinition(
                title, description, value.objectives(), value.rewards());
        mTitleInput.setText(title.plain());
        mDescriptionInput.setText(description.plain());
        mConditionOverview.setOverview(conditionOverview);
        mObjectivesEditor.setObjectives(value.objectives());
        mRewardsEditor.setRewards(value.rewards());
        mHasDefinition = true;
        mUpdating = false;
    }

    public void clear() {
        mGeneration++;
        mUpdating = true;
        mDefinition = QuestDefinition.EMPTY;
        mHasDefinition = false;
        mTitleInput.setText("");
        mDescriptionInput.setText("");
        mConditionOverview.setOverview(QuestConditionOverview.EMPTY);
        mObjectivesEditor.setObjectives(java.util.List.of());
        mRewardsEditor.setRewards(java.util.List.of());
        mUpdating = false;
    }

    public QuestDefinition definition() {
        mDefinition = new QuestDefinition(
                richTextAfterInlineEdit(mDefinition.title(), mTitleInput.getText().toString()),
                richTextAfterInlineEdit(mDefinition.description(), mDescriptionInput.getText().toString()),
                mObjectivesEditor.objectives(),
                mRewardsEditor.rewards());
        return mDefinition;
    }

    private void openTitleEditor(TextView anchor) {
        QuestDefinition current = definition();
        int generation = mGeneration;
        ExpandedTextInputOverlay.showRichText(
                getContext(),
                anchor,
                current.title(),
                value -> {
                    if (generation != mGeneration) return;
                    mDefinition = new QuestDefinition(
                            value,
                            mDefinition.description(),
                            mDefinition.objectives(),
                            mDefinition.rewards());
                    mTitleInput.setText(value.plain());
                    changed();
                });
    }

    private void openDescriptionEditor(TextView anchor) {
        QuestDefinition current = definition();
        int generation = mGeneration;
        ExpandedTextInputOverlay.showRichText(
                getContext(),
                anchor,
                current.description(),
                value -> {
                    if (generation != mGeneration) return;
                    mDefinition = new QuestDefinition(
                            mDefinition.title(),
                            value,
                            mDefinition.objectives(),
                            mDefinition.rewards());
                    mDescriptionInput.setText(value.plain());
                    changed();
                });
    }

    private void changed() {
        if (!mUpdating && mOnChanged != null) mOnChanged.run();
    }

    private EditText questInput(boolean singleLine, String hint) {
        EditText input = new EditText(getContext());
        input.setSingleLine(singleLine);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setHint(hint);
        input.setTextSize(0, UIUtils.dp2px(11.0f));
        input.setPadding(
                UIUtils.dp2pxInt(8),
                singleLine ? 0 : UIUtils.dp2pxInt(6),
                UIUtils.dp2pxInt(8),
                singleLine ? 0 : UIUtils.dp2pxInt(6));
        input.setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_INPUT_BORDER));
        return input;
    }

    private TextView richTextButton() {
        TextView button = label(getContext(), "...", 12.0f, COLOR_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setTooltipText(tr("geometry_node.graph_properties.quest.edit_rich_text"));
        styleButton(button, COLOR_BUTTON, COLOR_BUTTON_HOVER);
        return button;
    }

    private TextView actionButton(String text, String tooltip) {
        TextView button = label(getContext(), text, 11.0f, COLOR_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setTooltipText(tooltip);
        styleButton(button, COLOR_BUTTON, COLOR_BUTTON_HOVER);
        return button;
    }

    private static RichTextValue preservePendingText(RichTextValue value, String pendingText) {
        if (pendingText == null || pendingText.equals(value.plain())) return value;
        return RichTextValue.plain(pendingText);
    }

    private static RichTextValue richTextAfterInlineEdit(RichTextValue value, String text) {
        RichTextValue current = value != null ? value : RichTextValue.EMPTY;
        String normalizedText = text != null ? text : "";
        return normalizedText.equals(current.plain()) ? current : RichTextValue.plain(normalizedText);
    }

}
