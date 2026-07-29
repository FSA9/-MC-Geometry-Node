package com.mine.geometry_node.client.ui.editor.graph.properties;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdSetGraphMetadata;
import com.mine.geometry_node.client.ui.persistence.GraphTagIO;
import com.mine.geometry_node.client.ui.common.TagFlowLayout;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.node.NodeGraph;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前本地图的属性编辑器。描述在失焦时提交，标签修改立即提交。
 */
public final class GraphPropertiesPanel extends LinearLayout implements EditorContext.EditorListener {
    private static final int COLOR_BACKGROUND = 0xFF303030;
    private static final int COLOR_SECTION = 0xFF292929;
    private static final int COLOR_INPUT = 0xFF242424;
    private static final int COLOR_INPUT_BORDER = 0xFF4A4A4A;
    private static final int COLOR_LABEL = 0xFFA8A8A8;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF777777;
    private static final int COLOR_BUTTON = 0xFF4A4A4A;
    private static final int COLOR_BUTTON_HOVER = 0xFF5A5A5A;
    private static final int COLOR_TAG = 0xFF424242;
    private static final int COLOR_TAG_HOVER = 0xFF505050;
    private static final int COLOR_TAG_BORDER = 0xFF5C5C5C;
    private static final int COLOR_REMOVE_HOVER = 0xFF744646;

    private final LinearLayout mContent;
    private final ScrollView mScroll;
    private final TextView mEmptyView;
    private final TextView mFileValue;
    private final TextView mTypeValue;
    private final EditText mCommentInput;
    private final TagFlowLayout mTagList;
    private final EditText mTagInput;
    private final List<String> mTags = new ArrayList<>();

    private GraphSession mSession;
    private boolean mUpdating;

    public GraphPropertiesPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(rect(COLOR_BACKGROUND, 0.0f, 0, 0));

        mEmptyView = label(context, tr("geometry_node.graph_properties.no_graph"), 12.0f, COLOR_MUTED);
        mEmptyView.setGravity(Gravity.CENTER);
        addView(mEmptyView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mScroll = new ScrollView(context);
        mContent = new LinearLayout(context);
        mContent.setOrientation(VERTICAL);
        mContent.setPadding(
                UIUtils.dp2pxInt(10),
                UIUtils.dp2pxInt(8),
                UIUtils.dp2pxInt(10),
                UIUtils.dp2pxInt(14));
        mScroll.addView(mContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mFileValue = valueField(context);
        addPropertyRow(tr("geometry_node.graph_properties.file"), mFileValue);

        mTypeValue = valueField(context);
        addPropertyRow(tr("geometry_node.graph_properties.type"), mTypeValue);

        addSectionTitle(tr("geometry_node.graph_properties.comment"), 10);
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
        mContent.addView(mCommentInput, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(92)));

        addSectionTitle(tr("geometry_node.graph_properties.tags"), 12);
        mTagList = new TagFlowLayout(context, 6, 6);
        mContent.addView(mTagList, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams inputRowLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(30));
        inputRowLp.topMargin = UIUtils.dp2pxInt(8);
        mContent.addView(inputRow, inputRowLp);

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
        inputRow.addView(mTagInput, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

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
        LayoutParams addLp = new LayoutParams(UIUtils.dp2pxInt(30), ViewGroup.LayoutParams.MATCH_PARENT);
        addLp.leftMargin = UIUtils.dp2pxInt(6);
        inputRow.addView(addTag, addLp);

        showSessionState(false);
    }

    public void bindSession(GraphSession session) {
        if (mSession == session) {
            syncFromGraph();
            return;
        }

        commitPendingEdits();
        if (mSession != null) mSession.editorContext.removeListener(this);
        mSession = session;
        if (mSession != null) mSession.editorContext.addListener(this);
        syncFromGraph();
    }

    public void commitPendingEdits() {
        if (mUpdating || mSession == null) return;
        submitMetadata(mCommentInput.getText().toString(), mTags);
    }

    public void commitPendingEdits(GraphSession session) {
        if (mSession == session) commitPendingEdits();
    }

    @Override
    public void onGraphMetadataChanged() {
        post(this::syncFromGraph);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSession != null) mSession.editorContext.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        commitPendingEdits();
        if (mSession != null) mSession.editorContext.removeListener(this);
        super.onDetachedFromWindow();
    }

    private void syncFromGraph() {
        NodeGraph graph = mSession != null ? mSession.editorContext.getGraph() : null;
        showSessionState(graph != null);
        if (graph == null) {
            mTags.clear();
            rebuildTags();
            return;
        }

        mUpdating = true;
        String pendingComment = mCommentInput.hasFocus() ? mCommentInput.getText().toString() : null;
        mFileValue.setText(mSession.tabName != null ? mSession.tabName : "");
        mTypeValue.setText(graphKindLabel(graph.getKind()));
        mCommentInput.setText(pendingComment != null ? pendingComment : (graph.comment != null ? graph.comment : ""));
        mTags.clear();
        if (graph.tags != null) mTags.addAll(graph.tags);
        rebuildTags();
        mUpdating = false;
    }

    private void addInputTags() {
        if (mSession == null) return;
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
        if (changed) submitMetadata(mCommentInput.getText().toString(), mTags);
    }

    private void removeTag(String tag) {
        if (mSession == null || !mTags.remove(tag)) return;
        submitMetadata(mCommentInput.getText().toString(), mTags);
    }

    private void submitMetadata(String comment, List<String> tags) {
        if (mSession == null) return;
        mSession.editorContext.getCommandManager().execute(new CmdSetGraphMetadata(
                mSession.editorContext.getGraphController(),
                comment,
                List.copyOf(tags)));
    }

    private void rebuildTags() {
        mTagList.removeAllViews();
        if (mTags.isEmpty()) {
            TextView empty = label(getContext(), tr("geometry_node.graph_properties.no_tags"), 11.0f, COLOR_MUTED);
            mTagList.addView(empty, new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    UIUtils.dp2pxInt(24)));
            return;
        }

        for (String tag : new ArrayList<>(mTags)) {
            mTagList.addView(createTagChip(tag), new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    UIUtils.dp2pxInt(24)));
        }
    }

    private View createTagChip(String tag) {
        LinearLayout chip = new LinearLayout(getContext());
        chip.setOrientation(HORIZONTAL);
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
        chip.addView(text, new LayoutParams(
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
        chip.addView(remove, new LayoutParams(UIUtils.dp2pxInt(18), UIUtils.dp2pxInt(18)));
        return chip;
    }

    private void addPropertyRow(String name, TextView value) {
        TextView label = label(getContext(), name, 10.5f, COLOR_LABEL);
        mContent.addView(label, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(20)));
        mContent.addView(value, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(28)));
    }

    private void addSectionTitle(String title, int topMarginDp) {
        TextView label = label(getContext(), title, 10.5f, COLOR_LABEL);
        label.setBackground(rect(COLOR_SECTION, 0.0f, 0, 0));
        label.setPadding(UIUtils.dp2pxInt(6), 0, UIUtils.dp2pxInt(6), 0);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(24));
        params.topMargin = UIUtils.dp2pxInt(topMarginDp);
        params.bottomMargin = UIUtils.dp2pxInt(5);
        mContent.addView(label, params);
    }

    private static TextView valueField(Context context) {
        TextView view = label(context, "", 11.0f, COLOR_TEXT);
        view.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        view.setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_INPUT_BORDER));
        return view;
    }

    private void showSessionState(boolean hasSession) {
        mEmptyView.setVisibility(hasSession ? View.GONE : View.VISIBLE);
        mScroll.setVisibility(hasSession ? View.VISIBLE : View.GONE);
    }

    private static String graphKindLabel(GraphKind kind) {
        String suffix = switch (kind != null ? kind : GraphKind.UNKNOWN) {
            case BLUEPRINT -> "blueprint";
            case DIALOGUE -> "dialogue";
            case BEHAVIOR_TREE -> "behavior_tree";
            case UNKNOWN -> "unknown";
        };
        return tr("geometry_node.graph_properties.kind." + suffix);
    }

    private static TextView label(Context context, String text, float sizeDp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(0, UIUtils.dp2px(sizeDp));
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        return view;
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

}
