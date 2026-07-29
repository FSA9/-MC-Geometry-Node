package com.mine.geometry_node.client.ui.editor.asset.dialog;

import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ui.persistence.GraphTagIO;
import com.mine.geometry_node.client.ui.common.TagFlowLayout;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class GraphTagDialog extends AssetDialogBase {
    private static final int COLOR_INPUT_BG = 0xFF202020;
    private static final int COLOR_TAG_BG = 0xFF26384A;
    private static final int COLOR_TAG_BG_HOVER = 0xFF31485E;
    private static final int COLOR_TAG_BORDER = 0x664F6D87;
    private static final int COLOR_TAG_TEXT = 0xFFE7F1FF;
    private static final int COLOR_TAG_REMOVE_BG = 0x334F6478;
    private static final int COLOR_TAG_REMOVE_BG_HOVER = 0xFF7C3F48;
    private static final int COLOR_TAG_REMOVE_TEXT = 0xFFBFD1E4;

    private final File mFile;
    private final Consumer<List<String>> mOnSaved;
    private final List<String> mTags = new ArrayList<>();
    private final TagFlowLayout mTagList;
    private final EditText mInput;
    private final TextView mStatus;
    private String mLoadError;

    public GraphTagDialog(Context context, File file, Consumer<List<String>> onSaved) {
        super(context, "编辑图标签");
        mFile = file;
        mOnSaved = onSaved;

        try {
            mTags.addAll(GraphTagIO.readTags(GraphTagIO.readGraphRoot(file)));
        } catch (Exception e) {
            mLoadError = "读取失败: " + e.getMessage();
        }

        TextView fileName = label(context, file.getName(), 13, 0xFFE6E6E6);
        fileName.setSingleLine(true);
        mPanel.addView(fileName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(24)
        ));

        setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                addInputTags();
                return true;
            }
            return false;
        });

        mTagList = new TagFlowLayout(context, 8, 8);
        mTagList.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackground(rect(0xFF222222, 4));
        scrollView.addView(mTagList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(168)
        );
        scrollLp.topMargin = UIUtils.dp2pxInt(6);
        mPanel.addView(scrollView, scrollLp);

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams inputRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(34)
        );
        inputRowLp.topMargin = UIUtils.dp2pxInt(10);
        mPanel.addView(inputRow, inputRowLp);

        mInput = new EditText(context);
        mInput.setSingleLine(true);
        mInput.setHint("输入标签，回车添加");
        mInput.setHintTextColor(0xFF777777);
        mInput.setTextColor(0xFFFFFFFF);
        mInput.setBackground(rect(COLOR_INPUT_BG, 4));
        mInput.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        mInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                addInputTags();
                return true;
            }
            return false;
        });
        inputRow.addView(mInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        Button add = button(context, "添加", 0xFF356A9A);
        add.setOnClickListener(v -> addInputTags());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(82),
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        addLp.leftMargin = UIUtils.dp2pxInt(8);
        inputRow.addView(add, addLp);

        mStatus = label(context, mLoadError != null ? mLoadError : "", 11, mLoadError != null ? 0xFFFF9999 : 0xFF888888);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(22)
        );
        statusLp.topMargin = UIUtils.dp2pxInt(4);
        mPanel.addView(mStatus, statusLp);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        Button cancel = button(context, "取消", 0xFF4A4A4A);
        cancel.setOnClickListener(v -> dismiss());
        Button save = button(context, "保存", 0xFF2F7DDE);
        save.setOnClickListener(v -> saveTags());
        actions.addView(cancel, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32));
        saveLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(save, saveLp);
        mPanel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(36)
        ));

        rebuildTagList();
        mInput.requestFocus();
    }

    private void rebuildTagList() {
        mTagList.removeAllViews();

        if (mTags.isEmpty()) {
            TextView empty = label(getContext(), "暂无标签", 12, 0xFF888888);
            empty.setGravity(Gravity.CENTER);
            mTagList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(42)
            ));
            return;
        }

        for (String tag : new ArrayList<>(mTags)) {
            mTagList.addView(createEditableTagChip(tag), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    UIUtils.dp2pxInt(26)
            ));
        }
    }

    private LinearLayout createEditableTagChip(String tag) {
        LinearLayout chip = new LinearLayout(getContext());
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(4), 0);
        chip.setBackground(pill(COLOR_TAG_BG, 13, 1, COLOR_TAG_BORDER));
        chip.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                chip.setBackground(pill(COLOR_TAG_BG_HOVER, 13, 1, COLOR_TAG_BORDER));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                chip.setBackground(pill(COLOR_TAG_BG, 13, 1, COLOR_TAG_BORDER));
            }
            return false;
        });

        TextView text = label(getContext(), "#" + tag, 12, COLOR_TAG_TEXT);
        text.setSingleLine(true);
        text.setPadding(0, 0, UIUtils.dp2pxInt(7), 0);
        chip.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView remove = label(getContext(), "x", 11, COLOR_TAG_REMOVE_TEXT);
        remove.setGravity(Gravity.CENTER);
        remove.setBackground(pill(COLOR_TAG_REMOVE_BG, 9, 0, 0));
        remove.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                remove.setTextColor(0xFFFFFFFF);
                remove.setBackground(pill(COLOR_TAG_REMOVE_BG_HOVER, 9, 0, 0));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                remove.setTextColor(COLOR_TAG_REMOVE_TEXT);
                remove.setBackground(pill(COLOR_TAG_REMOVE_BG, 9, 0, 0));
            }
            return false;
        });
        remove.setOnClickListener(v -> {
            mTags.remove(tag);
            rebuildTagList();
        });
        chip.addView(remove, new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(18),
                UIUtils.dp2pxInt(18)
        ));

        return chip;
    }

    private void addInputTags() {
        String raw = mInput.getText().toString();
        if (raw.trim().isEmpty()) return;

        int before = mTags.size();
        Set<String> existing = new LinkedHashSet<>(mTags);
        for (String part : raw.split("[,，;；\\s]+")) {
            String normalized = GraphTagIO.normalizeTag(part);
            if (!normalized.isEmpty() && existing.add(normalized)) {
                mTags.add(normalized);
            }
        }

        mInput.setText("");
        if (mTags.size() == before) {
            setStatus("没有新增标签", 0xFFB0B0B0);
        } else {
            setStatus("", 0xFF888888);
            rebuildTagList();
        }
    }

    private void saveTags() {
        if (mLoadError != null) {
            setStatus("JSON 读取失败，未保存", 0xFFFF9999);
            return;
        }

        addInputTags();
        try {
            GraphTagIO.writeTags(mFile, mTags);
            if (mOnSaved != null) {
                mOnSaved.accept(List.copyOf(mTags));
            }
            dismiss();
        } catch (Exception e) {
            setStatus("保存失败: " + e.getMessage(), 0xFFFF9999);
        }
    }

    private void setStatus(String text, int color) {
        mStatus.setText(text);
        mStatus.setTextColor(color);
    }

    private ShapeDrawable pill(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = rect(color, radiusDp);
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }
}
