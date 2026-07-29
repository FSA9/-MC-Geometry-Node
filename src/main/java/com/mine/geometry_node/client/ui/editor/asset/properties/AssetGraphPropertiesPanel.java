package com.mine.geometry_node.client.ui.editor.asset.properties;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.persistence.GraphTagIO;
import com.mine.geometry_node.client.ui.common.TagFlowLayout;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.GraphKind;
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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 编辑资产浏览器中当前选中的单个本地 JSON 图属性。
 */
public final class AssetGraphPropertiesPanel extends LinearLayout {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-GraphProperties-IO");
        thread.setDaemon(true);
        return thread;
    });

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

    private final Runnable mOnMetadataSaved;
    private final ScrollView mScroll;
    private final LinearLayout mContent;
    private final TextView mEmptyView;
    private final TextView mFileValue;
    private final TextView mTypeValue;
    private final TextView mStatus;
    private final EditText mCommentInput;
    private final TagFlowLayout mTagList;
    private final EditText mTagInput;
    private final List<String> mTags = new ArrayList<>();

    private File mFile;
    private String mLoadedComment = "";
    private List<String> mLoadedTags = List.of();
    private Future<?> mLoadTask;
    private int mLoadSerial;
    private boolean mUpdating;

    public AssetGraphPropertiesPanel(Context context, Runnable onMetadataSaved) {
        super(context);
        mOnMetadataSaved = onMetadataSaved;
        setOrientation(VERTICAL);
        setBackground(rect(COLOR_BACKGROUND, 0.0f, 0, 0));

        mEmptyView = label(context, tr("geometry_node.graph_properties.select_local_graph"), 12.0f, COLOR_MUTED);
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
        LayoutParams inputRowParams = new LayoutParams(
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
        LayoutParams addParams = new LayoutParams(UIUtils.dp2pxInt(30), ViewGroup.LayoutParams.MATCH_PARENT);
        addParams.leftMargin = UIUtils.dp2pxInt(6);
        inputRow.addView(addTag, addParams);

        mStatus = label(context, "", 10.0f, COLOR_MUTED);
        LayoutParams statusParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(22));
        statusParams.topMargin = UIUtils.dp2pxInt(6);
        mContent.addView(mStatus, statusParams);

        showEmpty(tr("geometry_node.graph_properties.select_local_graph"));
    }

    public void bindSelection(List<AssetEntry> entries) {
        File selectedFile = resolveSelectedFile(entries);
        commitPendingEdits();
        cancelLoad();
        mFile = selectedFile;
        mLoadedComment = "";
        mLoadedTags = List.of();
        mTags.clear();

        if (mFile == null) {
            showEmpty(tr("geometry_node.graph_properties.select_local_graph"));
            return;
        }

        showEmpty(tr("geometry_node.graph_properties.loading"));
        int loadSerial = ++mLoadSerial;
        File requestedFile = mFile;
        mLoadTask = IO_EXECUTOR.submit(() -> {
            try {
                GraphTagIO.GraphMetadata metadata = GraphTagIO.readMetadata(requestedFile);
                post(() -> applyLoadedMetadata(loadSerial, requestedFile, metadata));
            } catch (Exception e) {
                post(() -> showLoadError(loadSerial, requestedFile, e));
            }
        });
    }

    public void commitPendingEdits() {
        if (mUpdating || mFile == null || mScroll.getVisibility() != View.VISIBLE) return;
        String comment = mCommentInput.getText().toString().trim();
        List<String> tags = List.copyOf(mTags);
        if (comment.equals(mLoadedComment) && tags.equals(mLoadedTags)) return;

        File targetFile = mFile;
        mLoadedComment = comment;
        mLoadedTags = tags;
        setStatus("", COLOR_MUTED);
        IO_EXECUTOR.submit(() -> saveMetadata(targetFile, comment, tags));
    }

    @Override
    protected void onDetachedFromWindow() {
        commitPendingEdits();
        cancelLoad();
        super.onDetachedFromWindow();
    }

    private void applyLoadedMetadata(int loadSerial, File requestedFile, GraphTagIO.GraphMetadata metadata) {
        if (loadSerial != mLoadSerial || !sameFile(mFile, requestedFile)) return;
        mLoadTask = null;
        mUpdating = true;
        mLoadedComment = metadata.comment() != null ? metadata.comment().trim() : "";
        mLoadedTags = metadata.tags() != null ? List.copyOf(metadata.tags()) : List.of();
        mFileValue.setText(requestedFile.getName());
        mTypeValue.setText(graphKindLabel(metadata.kind()));
        mCommentInput.setText(mLoadedComment);
        mTags.clear();
        mTags.addAll(mLoadedTags);
        rebuildTags();
        setStatus("", COLOR_MUTED);
        mUpdating = false;
        showContent();
    }

    private void showLoadError(int loadSerial, File requestedFile, Exception error) {
        if (loadSerial != mLoadSerial || !sameFile(mFile, requestedFile)) return;
        mLoadTask = null;
        showEmpty(tr("geometry_node.graph_properties.load_failed"));
        System.err.println("[AssetGraphPropertiesPanel] Failed to read " + requestedFile + ": " + error.getMessage());
    }

    private void saveMetadata(File targetFile, String comment, List<String> tags) {
        try {
            GraphTagIO.writeMetadata(targetFile, comment, tags);
            post(() -> {
                syncOpenSession(targetFile, comment, tags);
                if (sameFile(mFile, targetFile)) setStatus("", COLOR_MUTED);
                if (mOnMetadataSaved != null) mOnMetadataSaved.run();
            });
        } catch (Exception e) {
            post(() -> {
                if (sameFile(mFile, targetFile)) {
                    setStatus(tr("geometry_node.graph_properties.save_failed"), COLOR_ERROR);
                }
            });
            System.err.println("[AssetGraphPropertiesPanel] Failed to save " + targetFile + ": " + e.getMessage());
        }
    }

    private void addInputTags() {
        if (mFile == null) return;
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

    private void showEmpty(String text) {
        mEmptyView.setText(text);
        mEmptyView.setVisibility(View.VISIBLE);
        mScroll.setVisibility(View.GONE);
    }

    private void showContent() {
        mEmptyView.setVisibility(View.GONE);
        mScroll.setVisibility(View.VISIBLE);
    }

    private void setStatus(String text, int color) {
        mStatus.setText(text);
        mStatus.setTextColor(color);
    }

    private void cancelLoad() {
        mLoadSerial++;
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
    }

    private static File resolveSelectedFile(List<AssetEntry> entries) {
        if (entries == null || entries.size() != 1) return null;
        AssetEntry entry = entries.get(0);
        if (entry == null || entry.sourceKind() != AssetSourceKind.LOCAL
                || !entry.isJsonFile() || entry.localFile() == null || !entry.localFile().isFile()) {
            return null;
        }
        return entry.localFile();
    }

    private static void syncOpenSession(File file, String comment, List<String> tags) {
        for (GraphSession session : DocumentManager.INSTANCE.getSessions()) {
            if (session == null || session.editorContext == null || session.editorContext.getGraph() == null) continue;
            if (!sameFile(file, new File(session.fileId))) continue;
            session.editorContext.getGraph().comment = comment;
            session.editorContext.getGraph().tags = new ArrayList<>(tags);
            session.editorContext.notifyGraphMetadataChanged();
        }
    }

    private static boolean sameFile(File first, File second) {
        if (first == null || second == null) return false;
        try {
            String firstPath = first.getCanonicalPath();
            String secondPath = second.getCanonicalPath();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            return windows ? firstPath.equalsIgnoreCase(secondPath) : firstPath.equals(secondPath);
        } catch (Exception ignored) {
            return first.getAbsoluteFile().equals(second.getAbsoluteFile());
        }
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

    private static TextView valueField(Context context) {
        TextView view = label(context, "", 11.0f, COLOR_TEXT);
        view.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        view.setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_INPUT_BORDER));
        return view;
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
