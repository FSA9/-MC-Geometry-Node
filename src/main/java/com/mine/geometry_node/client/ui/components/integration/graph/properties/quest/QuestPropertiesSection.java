package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.GraphPropertiesTarget;
import java.util.function.Supplier;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi;
import icyllis.modernui.view.View;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.composite.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.rect;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.tr;

/**
 * Quest-specific content owned by the graph-properties panel.
 */
public final class QuestPropertiesSection extends LinearLayout {
    private static final int COLOR_SECTION = UiThemes.EDITOR.surface().panel();
    private static final int COLOR_LABEL = UiThemes.EDITOR.text().muted();
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_MUTED = UiThemes.EDITOR.text().hint();
    private static final int COLOR_BUTTON = GraphPropertiesUi.ACTION_BACKGROUND;
    private static final int COLOR_BUTTON_HOVER = GraphPropertiesUi.ACTION_HOVER;

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

    public QuestPropertiesSection(Context context, Runnable onChanged, Runnable onPreview,
                                  Supplier<GraphPropertiesTarget> captureTarget) {
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
        UiIconButton editTitle = richTextButton();
        editTitle.setOnClickListener(v -> openTitleEditor(editTitle));
        LayoutParams titleButtonParams = GraphPropertiesUi.actionParams();
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
        UiIconButton editDescription = richTextButton();
        editDescription.setOnClickListener(v -> openDescriptionEditor(editDescription));
        LayoutParams descriptionButtonParams = GraphPropertiesUi.actionParams();
        descriptionRow.addView(editDescription, descriptionButtonParams);
        addView(descriptionRow,
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(76)));

        mConditionOverview = new QuestConditionOverviewView(context);
        LayoutParams conditionParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        conditionParams.topMargin = UIUtils.dp2pxInt(8);
        addView(mConditionOverview, conditionParams);

        mObjectivesEditor = new QuestObjectivesEditor(context, this::changed, captureTarget);
        LayoutParams objectiveParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        objectiveParams.topMargin = UIUtils.dp2pxInt(8);
        addView(mObjectivesEditor, objectiveParams);

        mRewardsEditor = new QuestRewardsEditor(context, this::changed, captureTarget);
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
        mObjectivesEditor.setObjectivesPreservingFocusedEdit(value.objectives());
        mRewardsEditor.setRewardsPreservingFocusedEdit(value.rewards());
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

    private void openTitleEditor(View anchor) {
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

    private void openDescriptionEditor(View anchor) {
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
        EditText input = new UiTextInput(getContext(), new UiTextInput.Style(11, 8,
                UiMetrics.RADIUS_INPUT, UiMetrics.BORDER, UiThemes.EDITOR.text().normal(), UiThemes.EDITOR.text().hint(),
                UiThemes.EDITOR.control().input(), UiThemes.EDITOR.control().inputBorder()));
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
        return input;
    }

    private UiIconButton richTextButton() {
        return GraphPropertiesUi.actionIcon(getContext(), SvgIconView.Icon.MORE_HORIZONTAL,
                "geometry_node.graph_properties.quest.edit_rich_text");
    }

    private TextView actionButton(String text, String tooltip) {
        TextView button = GraphPropertiesUi.button(getContext(), text, tooltip,
                COLOR_TEXT, COLOR_BUTTON, COLOR_BUTTON_HOVER);
        UIUtils.setLockedTextSize(button, 11.0f);
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
