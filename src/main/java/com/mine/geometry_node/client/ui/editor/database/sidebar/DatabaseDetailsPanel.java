package com.mine.geometry_node.client.ui.editor.database.sidebar;

import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryPreview;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryEditor;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanel;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelDefinition;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.editor.database.DatabaseSelection;
import com.mine.geometry_node.client.ui.editor.database.DatabaseWindow;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.stream.IntStream;

import static com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.px;
import static com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.tr;

/** Registered database inspector; the selected source supplies every value and command. */
public final class DatabaseDetailsPanel extends LinearLayout implements SidebarPanel {
    public static final String PANEL_ID = "database_details";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(
            PANEL_ID, "geometry_node.database.details", 100, Set.of(SidebarPanelScope.DATA_LIBRARY),
            context -> new DatabaseDetailsPanel(context.uiContext(), context.requireOwner(DatabaseWindow.class)),
            SvgIconView.Icon.PROPERTIES);
    private final DatabaseWindow owner;
    private final LinearLayout content;
    private final LinearLayout tabs;
    private final UiActionButton propertiesTab, dataTab;
    private final UiScrollView propertiesScroll, dataScroll;
    private final LinearLayout dataContent;
    private UiDataDictionaryPreview dataPreview;
    private String dataTarget, dataTitle, dataSubtitle;
    private boolean dataSelected;
    private DatabaseSelection current;
    private boolean initialized;
    private boolean active;
    private View previewView;
    private List<View> sectionViews = List.of();

    private DatabaseDetailsPanel(Context context, DatabaseWindow owner) {
        super(context);
        this.owner = owner;
        setOrientation(VERTICAL);
        tabs = new LinearLayout(context);
        propertiesTab = com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.button(context,
                tr("properties"), () -> selectData(false));
        dataTab = com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.button(context,
                tr("data.title"), () -> selectData(true));
        tabs.addView(propertiesTab, new LayoutParams(0, -1, 1));
        tabs.addView(dataTab, new LayoutParams(0, -1, 1));
        addView(tabs, new LayoutParams(-1, px(32)));
        FrameLayout body = new FrameLayout(context);
        addView(body, new LayoutParams(-1, 0, 1));
        propertiesScroll = new UiScrollView(context);
        propertiesScroll.setHorizontalScrollBarEnabled(false);
        content = DatabaseInspectorViews.vertical(context);
        content.setPadding(px(18), px(17), px(18), px(18));
        propertiesScroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        body.addView(propertiesScroll, new FrameLayout.LayoutParams(-1, -1));
        dataScroll = new UiScrollView(context);
        dataScroll.setHorizontalScrollBarEnabled(false);
        dataContent = DatabaseInspectorViews.vertical(context);
        dataContent.setPadding(px(18), px(17), px(18), px(18));
        dataScroll.addView(dataContent, new FrameLayout.LayoutParams(-1, -2));
        body.addView(dataScroll, new FrameLayout.LayoutParams(-1, -1));
        refresh();
    }

    @Override public View getView() { return this; }
    @Override public void onSelected() {
        active = true;
        refresh();
        updateTabVisibility();
    }
    @Override public void onDeselected() {
        active = false;
        dataScroll.setVisibility(GONE);
        if (previewView != null) previewView.setVisibility(INVISIBLE);
    }

    public void refresh() {
        DatabaseSelection selection = owner.selection();
        boolean unchanged = initialized && (current == null ? selection == null : current.hasSamePresentation(selection));
        DatabaseSelection previous = current;
        current = selection;
        updateDataSelection(selection);
        if (unchanged) return;
        initialized = true;
        if (selection == null) {
            previewView = null;
            sectionViews = List.of();
            content.removeAllViews();
            showEmpty();
            return;
        }
        List<View> children = new ArrayList<>();
        List<View> nextSections = new ArrayList<>();
        View icon = selection.iconPath() == null || selection.iconPath().isBlank()
                ? null
                : new SvgIconView(getContext(), selection.iconPath(), selection.accent());
        View identity = DatabaseInspectorViews.identity(getContext(), selection.title(),
                selection.subtitle(), icon, selection.accent());
        identity.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        children.add(identity);
        if (selection.preview() != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = px(12);
            if (previewView == null || previous == null || !Objects.equals(previous.preview(), selection.preview()))
                previewView = DatabaseInspectorViews.preview(getContext(), selection.preview());
            previewView.setVisibility(active && !dataSelected ? VISIBLE : INVISIBLE);
            previewView.setLayoutParams(params);
            children.add(previewView);
        } else previewView = null;
        if (!selection.actions().isEmpty()) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = px(17);
            // Callbacks can change with a new snapshot even if all displayed values stay identical.
            List<DatabaseSelection.Action> actions = IntStream.range(0, selection.actions().size())
                    .mapToObj(index -> new DatabaseSelection.Action(selection.actions().get(index).label(), () -> {
                        if (current != null && index < current.actions().size()) current.actions().get(index).run().run();
                    })).toList();
            View buttons = DatabaseInspectorViews.actions(getContext(), actions);
            buttons.setLayoutParams(params);
            children.add(buttons);
        }
        if (!selection.fields().isEmpty()) {
            LinearLayout section = DatabaseInspectorViews.section(getContext(), tr("details"), "");
            for (DatabaseSelection.Field field : selection.fields()) addMetadata(section, field);
            addSection(children, section);
        }
        for (int index = 0; index < selection.sections().size(); index++) {
            DatabaseSelection.Section model = selection.sections().get(index);
            if (previous != null && index < previous.sections().size() && index < sectionViews.size()
                    && (model.hasSamePresentation(previous.sections().get(index))
                    || sameTagTarget(model, previous.sections().get(index)))) {
                View retained = sectionViews.get(index);
                if (!model.hasSamePresentation(previous.sections().get(index))) {
                    LinearLayout section = (LinearLayout) retained;
                    DatabaseInspectorViews.updateTags(section.getChildAt(section.getChildCount() - 1),
                            model.fields(), model.tagEditor());
                }
                children.add(retained);
                nextSections.add(retained);
                continue;
            }
            int sectionIndex = index;
            LinearLayout section = DatabaseInspectorViews.section(getContext(), model.title(), model.subtitle());
            switch (model.kind()) {
                case POSITION -> section.addView(DatabaseInspectorViews.position(getContext(), model.fields()),
                        new LinearLayout.LayoutParams(-1, -2));
                case TAGS -> section.addView(DatabaseInspectorViews.tags(getContext(), model.fields(), model.tagEditor(), () -> {
                    if (!active || current == null || sectionIndex >= current.sections().size()
                            || sectionIndex >= sectionViews.size() || sectionViews.get(sectionIndex) != section) return null;
                    var latest = current.sections().get(sectionIndex).tagEditor();
                    return latest != null && model.tagEditor() != null
                            && latest.target().equals(model.tagEditor().target()) ? latest : null;
                }),
                        new LinearLayout.LayoutParams(-1, -2));
                case SOURCE -> section.addView(DatabaseInspectorViews.source(getContext(), model.fields()),
                        new LinearLayout.LayoutParams(-1, -2));
                case VALUE, REFERENCE -> {
                    for (DatabaseSelection.Field field : model.fields()) {
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                        if (section.getChildCount() > 2) params.topMargin = px(6);
                        section.addView(DatabaseInspectorViews.block(getContext(), field,
                                model.kind() == DatabaseSelection.Kind.REFERENCE), params);
                    }
                }
                case METADATA -> {
                    for (DatabaseSelection.Field field : model.fields()) addMetadata(section, field);
                }
            }
            addSection(children, section);
            nextSections.add(section);
        }
        if (selection.dataEditor() != null) {
            String target = selection.dataEditor().target();
            LinearLayout section = DatabaseInspectorViews.section(getContext(), tr("data.title"),
                    tr("data.count", selection.dataEditor().count()));
            UiActionButton manage = com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.button(
                    getContext(), tr("data.manage"), () -> {
                        var latest = current == null ? null : current.dataEditor();
                        if (active && section.getParent() == content && latest != null && target.equals(latest.target()))
                            UiDataDictionaryEditor.show(this, dataCaption(), latest.binding());
                    });
            section.addView(manage, new LayoutParams(-1, px(30)));
            addSection(children, section);
        }
        sectionViews = List.copyOf(nextSections);
        // Keep unchanged previews/editors attached: native resources, focus and drafts survive snapshot updates.
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            if (!children.contains(content.getChildAt(i))) content.removeViewAt(i);
        }
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (content.getChildAt(i) != child) {
                if (child.getParent() == content) content.removeView(child);
                content.addView(child, i);
            }
        }
    }

    private void updateDataSelection(DatabaseSelection selection) {
        var editor = selection == null ? null : selection.dataEditor();
        tabs.setVisibility(editor == null ? GONE : VISIBLE);
        if (editor == null) {
            dataSelected = false;
            dataTarget = null;
            dataPreview = null;
            dataContent.removeAllViews();
        } else {
            dataTab.setText(tr("data.title") + " · " + editor.count());
            if (!Objects.equals(dataTarget, editor.target())) {
                dataTarget = editor.target();
                dataContent.removeAllViews();
                View identity = DatabaseInspectorViews.identity(getContext(), selection.title(),
                        selection.subtitle(), null, selection.accent());
                dataContent.addView(identity, new LayoutParams(-1, -2));
                dataTitle = selection.title(); dataSubtitle = selection.subtitle();
                dataPreview = new UiDataDictionaryPreview(getContext());
                dataPreview.setBinding(editor.binding());
                String target = editor.target();
                UiDataDictionaryPreview mounted = dataPreview;
                dataPreview.setManageAction(() -> {
                    var latest = current == null ? null : current.dataEditor();
                    if (!active || dataPreview != mounted || latest == null || !target.equals(latest.target())) return;
                    UiDataDictionaryEditor.show(this, dataCaption(), latest.binding());
                });
                dataPreview.setManageEntryAction(key -> {
                    var latest = current == null ? null : current.dataEditor();
                    if (!active || dataPreview != mounted || latest == null || !target.equals(latest.target())) return;
                    UiDataDictionaryEditor.show(this, dataCaption(), latest.binding(), key);
                });
                LayoutParams params = new LayoutParams(-1, -2);
                params.topMargin = px(17);
                dataContent.addView(dataPreview, params);
                dataScroll.scrollTo(0, 0);
            } else if (!Objects.equals(dataTitle, selection.title()) || !Objects.equals(dataSubtitle, selection.subtitle())) {
                dataContent.removeViewAt(0);
                dataContent.addView(DatabaseInspectorViews.identity(getContext(), selection.title(), selection.subtitle(),
                        null, selection.accent()), 0, new LayoutParams(-1, -2));
                dataTitle = selection.title(); dataSubtitle = selection.subtitle();
            }
        }
        updateTabVisibility();
    }

    private String dataCaption() {
        if (current.subtitle() == null || current.subtitle().isBlank()) return current.title();
        return current.subtitle().startsWith(current.title() + " · ") ? current.subtitle()
                : current.title() + " · " + current.subtitle();
    }

    private void selectData(boolean selected) {
        dataSelected = selected && current != null && current.dataEditor() != null;
        updateTabVisibility();
    }

    private void updateTabVisibility() {
        propertiesScroll.setVisibility(dataSelected ? GONE : VISIBLE);
        dataScroll.setVisibility(active && dataSelected ? VISIBLE : GONE);
        if (previewView != null) previewView.setVisibility(active && !dataSelected ? VISIBLE : INVISIBLE);
        for (UiActionButton tab : List.of(propertiesTab, dataTab)) {
            boolean selected = tab == dataTab ? dataSelected : !dataSelected;
            tab.setPalette(new UiActionButton.Palette(selected ? UiTheme.Surface.HEADER : UiTheme.Surface.PANEL,
                    UiTheme.Control.ICON_HOVER, UiTheme.Control.ICON_PRESSED,
                    selected ? UiTheme.Text.STRONG : UiTheme.Text.MUTED));
        }
    }

    private void addMetadata(LinearLayout section, DatabaseSelection.Field field) {
        section.addView(DatabaseInspectorViews.metadata(getContext(), field),
                new LinearLayout.LayoutParams(-1, px(29)));
    }

    private static boolean sameTagTarget(DatabaseSelection.Section first, DatabaseSelection.Section second) {
        return first.kind() == DatabaseSelection.Kind.TAGS && second.kind() == DatabaseSelection.Kind.TAGS
                && Objects.equals(first.title(), second.title()) && Objects.equals(first.subtitle(), second.subtitle())
                && first.tagEditor() != null && second.tagEditor() != null
                && first.tagEditor().target().equals(second.tagEditor().target());
    }

    private void addSection(List<View> children, LinearLayout section) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = px(17);
        section.setLayoutParams(params);
        children.add(section);
    }

    private void showEmpty() {
        LinearLayout empty = DatabaseInspectorViews.vertical(getContext());
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(px(8), px(24), px(8), px(24));
        TextView hint = DatabaseInspectorViews.text(getContext(), tr("select_hint"), 11, UiTheme.Text.MUTED);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = px(14);
        empty.addView(hint, hintParams);
        content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }
}
