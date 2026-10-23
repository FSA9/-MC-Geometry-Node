package com.mine.geometry_node.client.ui.editor.entity;

import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.layout.TagFlowLayout;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagChip;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.List;
import static com.mine.geometry_node.client.ui.editor.entity.EntityEditorViews.*;

/** Shared tag operations and chips; completed changes enter the owner's automatic-save session. */
final class EntityTagsView extends UiScrollView {
    private final EntityEditDraft draft;
    private final UiTheme theme;
    private final TagFlowLayout tags;
    private final UiTextInput input;
    private final UiDisplayText count, error;
    private List<String> renderedTags = List.of();
    EntityTagsView(Context context, UiTheme theme, EntityEditDraft draft) {
        super(context, theme.scroll()); this.theme = theme; this.draft = draft;
        setHorizontalScrollBarEnabled(false); LinearLayout root = column(context);
        root.setPadding(px(24), px(20), px(24), px(20));
        count = text(context, "", theme.text().muted()); root.addView(count, size(-1, 28));
        LinearLayout compose = row(context); input = input(context, theme); input.setHint(tr("tags.input"));
        compose.addView(input, new LinearLayout.LayoutParams(0, px(32), 1));
        var add = UiIconButton.createThemed(context, theme,
                new SvgIconView(context, SvgIconView.Icon.PLUS, theme.text().strong())).tooltip(tr("tags.add"));
        add.setOnClickListener(view -> add());
        LinearLayout.LayoutParams ap = size(32, 32); ap.leftMargin = px(8);
        compose.addView(add, ap); root.addView(compose, size(-1, 42));
        tags = new TagFlowLayout(context, 8, 8); tags.setPadding(px(12), px(12), px(12), px(12));
        tags.setMinimumHeight(px(150)); tags.setBackground(fill(theme.control().input())); root.addView(tags, size(-1, -2));
        error = text(context, "", theme.status().error()); root.addView(error, size(-1, 32));
        input.setOnKeyListener((view, code, event) -> {
            if (code != KeyEvent.KEY_ENTER && code != KeyEvent.KEY_KP_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) add(); return true;
        });
        addView(root, new FrameLayout.LayoutParams(-1, -2)); render();
    }
    private void add() {
        try { draft.addTag(input.getText().toString()); input.setText(""); error.setText(""); render(); }
        catch (IllegalArgumentException invalid) { error.setText(tr("tags.invalid")); }
    }
    /** Refresh server acknowledgements while retaining the unfinished tag input. */
    void refreshConfirmed() { render(); }
    private void render() {
        List<String> values = draft.tags();
        count.setText(tr("tags.count", values.size()));
        if (values.equals(renderedTags) && tags.getChildCount() == values.size()) return;
        renderedTags = List.copyOf(values);
        tags.removeAllViews();
        for (String tag : values) {
            UiTagChip chip = new UiTagChip(getContext(), tag, theme, UiMetrics.TEXT_BODY);
            chip.setOnRemove(tr("tags.remove"), () -> { draft.removeTag(tag); render(); });
            tags.addView(chip, new TagFlowLayout.LayoutParams(-2, px(UiTagChip.HEIGHT_DP)));
        }
    }
}
