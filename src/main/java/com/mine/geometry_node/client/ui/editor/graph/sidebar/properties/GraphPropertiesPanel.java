package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties;

import com.mine.geometry_node.client.ui.components.common.TagFlowLayout;
import com.mine.geometry_node.client.ui.persistence.GraphTagIO;
import com.mine.geometry_node.client.ui.editor.graph.preview.EditorPreviewController;
import com.mine.geometry_node.client.ui.editor.graph.properties.GraphPropertiesSnapshot;
import com.mine.geometry_node.client.ui.editor.graph.properties.GraphPropertiesTarget;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanel;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelDefinition;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.common.GraphTypeSelector;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.quest.QuestPropertiesSection;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.rect;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.tr;

/**
 * Shared graph-properties page. The bound target supplies storage semantics.
 */
public final class GraphPropertiesPanel extends FrameLayout implements SidebarPanel {
    public static final String PANEL_ID = "properties";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(
            PANEL_ID,
            "geometry_node.graph_properties.title",
            100,
            Set.of(SidebarPanelScope.GRAPH_EDITOR, SidebarPanelScope.ASSET_BROWSER),
            GraphPropertiesPanel::create);

    private static final int COLOR_BACKGROUND = 0xFF303030;
    private static final int COLOR_SECTION = 0xFF292929;
    private static final int COLOR_INPUT = 0xFF242424;
    private static final int COLOR_INPUT_BORDER = 0xFF4A4A4A;
    private static final int COLOR_LABEL = 0xFFA8A8A8;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF777777;
    private static final int COLOR_ERROR = 0xFFE18A8A;
    private static final int COLOR_BUTTON = 0xFF4A4A4A;
    private static final int COLOR_BUTTON_HOVER = 0xFF5A5A5A;
    private static final int COLOR_TAG = 0xFF424242;
    private static final int COLOR_TAG_HOVER = 0xFF505050;
    private static final int COLOR_TAG_BORDER = 0xFF5C5C5C;
    private static final int COLOR_REMOVE_HOVER = 0xFF744646;

    private final ScrollView mScroll;
    private final LinearLayout mContent;
    private final TextView mLoadError;
    private final TextView mFileValue;
    private final GraphTypeSelector mTypeSelector;
    private final QuestPropertiesSection mQuestSection;
    private final TextView mCommentSectionTitle;
    private final EditText mCommentInput;
    private final TagFlowLayout mTagList;
    private final EditText mTagInput;
    private final TextView mSaveStatus;
    private final List<String> mTags = new ArrayList<>();

    private GraphPropertiesTarget mTarget;
    private GraphPropertiesSnapshot mLoaded;
    private int mGeneration;
    private boolean mUpdating;

    public GraphPropertiesPanel(Context context) {
        super(context);
        setBackground(rect(COLOR_BACKGROUND, 0.0f, 0, 0));

        mScroll = new ScrollView(context);
        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setPadding(
                UIUtils.dp2pxInt(10),
                UIUtils.dp2pxInt(8),
                UIUtils.dp2pxInt(10),
                UIUtils.dp2pxInt(14));
        mScroll.addView(mContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        mFileValue = valueField(context);
        addPropertyRow(tr("geometry_node.graph_properties.file"), mFileValue);
        mTypeSelector = new GraphTypeSelector(context);
        mTypeSelector.setOnSelected(graphTypeId -> {
            updateQuestSectionVisibility();
            commitPendingEdits();
        });
        addPropertyRow(tr("geometry_node.graph_properties.type"), mTypeSelector);

        mQuestSection = new QuestPropertiesSection(
                context,
                this::commitPendingEdits,
                this::previewQuest);
        mContent.addView(mQuestSection, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        mCommentSectionTitle = addSectionTitle(tr("geometry_node.graph_properties.comment"), 10);
        mCommentInput = new EditText(context);
        mCommentInput.setSingleLine(false);
        mCommentInput.setMinLines(4);
        mCommentInput.setTextColor(COLOR_TEXT);
        mCommentInput.setHintTextColor(COLOR_MUTED);
        mCommentInput.setHint(tr("geometry_node.graph_properties.comment_placeholder"));
        mCommentInput.setTextSize(0, UIUtils.dp2px(11.0f));
        mCommentInput.setGravity(Gravity.LEFT | Gravity.TOP);
        mCommentInput.setPadding(
                UIUtils.dp2pxInt(8),
                UIUtils.dp2pxInt(6),
                UIUtils.dp2pxInt(8),
                UIUtils.dp2pxInt(6));
        mCommentInput.setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_INPUT_BORDER));
        mCommentInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitPendingEdits();
        });
        mContent.addView(mCommentInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(92)));

        addSectionTitle(tr("geometry_node.graph_properties.tags"), 12);
        mTagList = new TagFlowLayout(context, 6, 6);
        mContent.addView(mTagList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams inputRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(30));
        inputRowParams.topMargin = UIUtils.dp2pxInt(8);
        mContent.addView(inputRow, inputRowParams);

        mTagInput = new EditText(context);
        mTagInput.setSingleLine(true);
        mTagInput.setTextColor(COLOR_TEXT);
        mTagInput.setHintTextColor(COLOR_MUTED);
        mTagInput.setHint(tr("geometry_node.graph_properties.tag_placeholder"));
        mTagInput.setTextSize(0, UIUtils.dp2px(11.0f));
        mTagInput.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        mTagInput.setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_INPUT_BORDER));
        mTagInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                addInputTags();
                return true;
            }
            return false;
        });
        inputRow.addView(mTagInput, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f));

        TextView addTag = label(context, "+", 16.0f, COLOR_TEXT);
        addTag.setGravity(Gravity.CENTER);
        addTag.setBackground(rect(COLOR_BUTTON, 3.0f, 0, 0));
        addTag.setOnHoverListener((v, event) -> {
            addTag.setBackground(rect(
                    event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? COLOR_BUTTON_HOVER : COLOR_BUTTON,
                    3.0f, 0, 0));
            return false;
        });
        addTag.setOnClickListener(v -> addInputTags());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(30),
                ViewGroup.LayoutParams.MATCH_PARENT);
        addParams.leftMargin = UIUtils.dp2pxInt(6);
        inputRow.addView(addTag, addParams);

        mSaveStatus = label(context, "", 10.0f, COLOR_ERROR);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(22));
        statusParams.topMargin = UIUtils.dp2pxInt(6);
        mContent.addView(mSaveStatus, statusParams);
        mSaveStatus.setVisibility(View.GONE);

        mLoadError = label(context, "", 12.0f, COLOR_ERROR);
        mLoadError.setGravity(Gravity.CENTER);
        mLoadError.setVisibility(View.GONE);
        addView(mLoadError, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        hideContent();
    }

    private static GraphPropertiesPanel create(SidebarPanelContext context) {
        return new GraphPropertiesPanel(context.uiContext());
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onDeselected() {
        commitPendingEdits();
    }

    public void bind(GraphPropertiesTarget target) {
        if (mTarget == target) {
            reloadTarget();
            return;
        }

        commitPendingEdits();
        clearTransientUi();
        detachTargetListener();
        mGeneration++;
        mTarget = target;
        mLoaded = null;
        hideContent();
        if (mTarget == null) return;

        attachTargetListener();
        reloadTarget();
    }

    public void commitPendingEdits() {
        GraphPropertiesTarget target = mTarget;
        GraphPropertiesSnapshot previous = mLoaded;
        if (mUpdating || target == null || previous == null || mScroll.getVisibility() != View.VISIBLE) return;

        String comment = target.normalizeComment(mCommentInput.getText().toString());
        List<String> tags = List.copyOf(mTags);
        String graphTypeId = mTypeSelector.selectedId();
        QuestDefinition questDefinition = mQuestSection.definition();
        if (graphTypeId.equals(previous.graphTypeId())
                && comment.equals(previous.comment())
                && tags.equals(previous.tags())
                && questDefinition.equals(previous.questDefinition())) return;

        GraphPropertiesSnapshot pending = previous.withMetadata(
                graphTypeId,
                comment,
                tags,
                questDefinition);
        int generation = mGeneration;
        mLoaded = pending;
        hideSaveError();
        target.save(graphTypeId, comment, tags, questDefinition).whenComplete((ignored, error) -> post(() -> {
            if (error == null) target.onSaveSucceeded(pending);
            if (target != mTarget || generation != mGeneration) return;
            if (error == null) {
                hideSaveError();
            } else {
                if (mLoaded == pending) mLoaded = previous;
                showSaveError();
            }
        }));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachTargetListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        clearTransientUi();
        commitPendingEdits();
        detachTargetListener();
        super.onDetachedFromWindow();
    }

    private void attachTargetListener() {
        if (mTarget != null && isAttachedToWindow()) {
            mTarget.setChangeListener(this::scheduleReload);
        }
    }

    private void detachTargetListener() {
        if (mTarget != null) mTarget.setChangeListener(null);
    }

    private void scheduleReload() {
        GraphPropertiesTarget target = mTarget;
        int generation = mGeneration;
        post(() -> {
            if (target == mTarget && generation == mGeneration) reloadTarget();
        });
    }

    private void reloadTarget() {
        GraphPropertiesTarget target = mTarget;
        if (target == null) {
            hideContent();
            return;
        }

        int generation = mGeneration;
        if (mLoaded == null) hideContent();
        target.load().whenComplete((snapshot, error) -> post(() -> {
            if (target != mTarget || generation != mGeneration) return;
            if (error != null) {
                mLoaded = null;
                clearTransientUi();
                showLoadError();
            } else if (snapshot == null) {
                mLoaded = null;
                hideContent();
            } else {
                applySnapshot(snapshot);
            }
        }));
    }

    private void applySnapshot(GraphPropertiesSnapshot snapshot) {
        mUpdating = true;
        String pendingComment = mLoaded != null && mCommentInput.hasFocus()
                ? mCommentInput.getText().toString()
                : null;
        mLoaded = snapshot;
        mFileValue.setText(snapshot.fileName());
        mTypeSelector.setSelectedId(snapshot.graphTypeId());
        mQuestSection.setDefinition(snapshot.questDefinition(), snapshot.conditionOverview());
        updateQuestSectionVisibility();
        mCommentInput.setText(pendingComment != null ? pendingComment : snapshot.comment());
        mTags.clear();
        mTags.addAll(snapshot.tags());
        rebuildTags();
        mUpdating = false;
        mLoadError.setVisibility(View.GONE);
        mScroll.setVisibility(View.VISIBLE);
        hideSaveError();
    }

    private void hideContent() {
        clearTransientUi();
        mUpdating = true;
        mTags.clear();
        mTypeSelector.setSelectedId("");
        mQuestSection.clear();
        updateQuestSectionVisibility();
        rebuildTags();
        mUpdating = false;
        mScroll.setVisibility(View.GONE);
        mLoadError.setVisibility(View.GONE);
        hideSaveError();
    }

    private void showLoadError() {
        mScroll.setVisibility(View.GONE);
        mLoadError.setText(tr("geometry_node.graph_properties.load_failed"));
        mLoadError.setVisibility(View.VISIBLE);
    }

    private void showSaveError() {
        mSaveStatus.setText(tr("geometry_node.graph_properties.save_failed"));
        mSaveStatus.setVisibility(View.VISIBLE);
    }

    private void hideSaveError() {
        mSaveStatus.setText("");
        mSaveStatus.setVisibility(View.GONE);
    }

    private void addInputTags() {
        if (mTarget == null || mScroll.getVisibility() != View.VISIBLE) return;
        String raw = mTagInput.getText().toString();
        if (raw.trim().isEmpty()) return;

        Set<String> seen = new LinkedHashSet<>(mTags);
        boolean changed = false;
        for (String part : raw.split("[,，;；\\s]+")) {
            String normalized = GraphTagIO.normalizeTag(part);
            if (!normalized.isEmpty() && seen.add(normalized)) {
                mTags.add(normalized);
                changed = true;
            }
        }
        mTagInput.setText("");
        if (changed) {
            rebuildTags();
            commitPendingEdits();
        }
    }

    private void removeTag(String tag) {
        if (!mTags.remove(tag)) return;
        rebuildTags();
        commitPendingEdits();
    }

    private void rebuildTags() {
        mTagList.removeAllViews();
        if (mTags.isEmpty()) {
            TextView empty = label(getContext(), tr("geometry_node.graph_properties.no_tags"), 11.0f, COLOR_MUTED);
            mTagList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    UIUtils.dp2pxInt(24)));
            return;
        }

        for (String tag : new ArrayList<>(mTags)) {
            mTagList.addView(createTagChip(tag), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    UIUtils.dp2pxInt(24)));
        }
    }

    private View createTagChip(String tag) {
        LinearLayout chip = new LinearLayout(getContext());
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(3), 0);
        chip.setBackground(rect(COLOR_TAG, 3.0f, 1, COLOR_TAG_BORDER));
        chip.setOnHoverListener((v, event) -> {
            chip.setBackground(rect(
                    event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? COLOR_TAG_HOVER : COLOR_TAG,
                    3.0f, 1, COLOR_TAG_BORDER));
            return false;
        });

        TextView text = label(getContext(), "#" + tag, 10.5f, COLOR_TEXT);
        text.setPadding(0, 0, UIUtils.dp2pxInt(5), 0);
        chip.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView remove = label(getContext(), "x", 10.0f, 0xFFBBBBBB);
        remove.setGravity(Gravity.CENTER);
        remove.setBackground(rect(0x00000000, 2.0f, 0, 0));
        remove.setOnHoverListener((v, event) -> {
            remove.setBackground(rect(
                    event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? COLOR_REMOVE_HOVER : 0x00000000,
                    2.0f, 0, 0));
            return false;
        });
        remove.setOnClickListener(v -> removeTag(tag));
        chip.addView(remove, new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(18),
                UIUtils.dp2pxInt(18)));
        return chip;
    }

    private void addPropertyRow(String name, View value) {
        TextView rowLabel = label(getContext(), name, 10.5f, COLOR_LABEL);
        mContent.addView(rowLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(20)));
        mContent.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(28)));
    }

    private TextView addSectionTitle(String title, int topMarginDp) {
        return addSectionTitle(mContent, title, topMarginDp);
    }

    private TextView addSectionTitle(LinearLayout parent, String title, int topMarginDp) {
        TextView sectionLabel = label(getContext(), title, 10.5f, COLOR_LABEL);
        sectionLabel.setBackground(rect(COLOR_SECTION, 0.0f, 0, 0));
        sectionLabel.setPadding(UIUtils.dp2pxInt(6), 0, UIUtils.dp2pxInt(6), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(24));
        params.topMargin = UIUtils.dp2pxInt(topMarginDp);
        params.bottomMargin = UIUtils.dp2pxInt(5);
        parent.addView(sectionLabel, params);
        return sectionLabel;
    }

    private static TextView valueField(Context context) {
        TextView view = label(context, "", 11.0f, COLOR_TEXT);
        view.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        view.setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_INPUT_BORDER));
        return view;
    }

    private void clearTransientUi() {
        mTypeSelector.dismissMenu();
    }

    private void updateQuestSectionVisibility() {
        boolean questGraph = GraphTypeRegistry.QUEST.id().equals(mTypeSelector.selectedId());
        mQuestSection.setVisibility(questGraph ? View.VISIBLE : View.GONE);
        mCommentSectionTitle.setVisibility(questGraph ? View.GONE : View.VISIBLE);
        mCommentInput.setVisibility(questGraph ? View.GONE : View.VISIBLE);
    }

    private void previewQuest() {
        if (!GraphTypeRegistry.QUEST.id().equals(mTypeSelector.selectedId()) || mLoaded == null) return;
        QuestDefinition definition = mQuestSection.definition();
        commitPendingEdits();
        EditorPreviewController.previewQuest(mLoaded.fileName(), definition, mLoaded.conditionOverview());
    }

}
