package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.ui.components.scroll.UiScrollView;

import com.mine.geometry_node.client.config.ConfigCategory;
import com.mine.geometry_node.client.config.ConfigEntry;
import com.mine.geometry_node.client.ui.components.icon.SvgIconView;
import com.mine.geometry_node.client.ui.settings.editor.ConfigEntryEditor;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPage;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageContext;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Shared renderer for pages that are projections of the config registry. */
abstract class ConfigEntriesSettingsPage implements SettingsPage {
    private static final int COLOR_GROUP = 0xFF242424;
    private static final int COLOR_HEADER = 0xFF292929;
    private static final int COLOR_HEADER_HOVER = 0xFF383838;
    private static final int COLOR_BORDER = 0xFF444444;
    private static final int COLOR_TITLE = 0xFFE3E3E3;
    private static final int COLOR_DISCLOSURE = 0xFFAAAAAA;

    private final ScrollView root;
    private final List<ConfigEntryEditor<?>> editors = new ArrayList<>();
    private final List<GroupBinding> groupBindings = new ArrayList<>();
    private boolean disposed;

    ConfigEntriesSettingsPage(SettingsPageContext context, Predicate<ConfigEntry<?>> filter) {
        this(context, ignored -> false, filter);
    }

    ConfigEntriesSettingsPage(SettingsPageContext context, Predicate<ConfigCategory> retainedCategory,
                              Predicate<ConfigEntry<?>> filter) {
        root = new UiScrollView(context.uiContext());
        root.setFillViewport(true);

        LinearLayout content = new LinearLayout(context.uiContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = UIUtils.dp2pxInt(12.0f);
        content.setPadding(padding, padding, padding, padding);
        root.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        var groups = ConfigCategoryTree.build(context.configRegistry().categories(),
                context.configRegistry().entries(), retainedCategory, filter);
        try {
            for (var group : groups) {
                GroupBinding binding = createGroup(context, group, 0);
                groupBindings.add(binding);
                content.addView(binding.view(), groupParams());
            }
        } catch (RuntimeException exception) {
            dispose();
            throw exception;
        }
    }

    @Override
    public final View getView() {
        return root;
    }

    @Override
    public final List<ConfigEntryEditor<?>> editors() {
        return List.copyOf(editors);
    }

    @Override
    public final boolean applySearch(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean pageMatch = false;
        for (GroupBinding group : groupBindings) pageMatch |= group.applySearch(normalized, false);
        return pageMatch;
    }

    @Override
    public final void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (ConfigEntryEditor<?> editor : editors) {
            editor.dispose();
        }
        editors.clear();
        groupBindings.clear();
        root.removeAllViews();
    }

    private GroupBinding createGroup(SettingsPageContext context, ConfigCategoryTree.Group specification, int depth) {
        ConfigCategory category = specification.category();
        LinearLayout group = new LinearLayout(context.uiContext());
        group.setOrientation(LinearLayout.VERTICAL);
        if (depth == 0) group.setBackground(groupBackground());

        LinearLayout header = new LinearLayout(context.uiContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        header.setBackground(headerBackground(COLOR_HEADER));

        SvgIconView disclosure = new SvgIconView(context.uiContext(),
                SvgIconView.Icon.forExpandedState(false), COLOR_DISCLOSURE);
        header.addView(disclosure, new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(20), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = UIUtils.createLockedTextView(
                context.uiContext(),
                Component.translatable(category.titleTranslationKey()).getString(),
                10.5f,
                COLOR_TITLE
        );
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        titleParams.leftMargin = UIUtils.dp2pxInt(3);
        header.addView(title, titleParams);
        group.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(depth == 0 ? 30 : 28)));

        LinearLayout entriesHost = new LinearLayout(context.uiContext());
        entriesHost.setOrientation(LinearLayout.VERTICAL);
        entriesHost.setPadding(UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(3),
                UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(4));
        entriesHost.setVisibility(View.GONE);
        group.addView(entriesHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<EntryBinding> entryBindings = new ArrayList<>();
        for (ConfigEntry<?> entry : specification.entries()) {
            ConfigEntryEditor<?> editor = context.editorFactory().create(
                    context.uiContext(), context.draft(), entry, context.editorEnvironment());
            editor.setOnStateChangedListener(() -> {
                revalidateEditors();
                context.onStateChanged().run();
            });
            editors.add(editor);
            entryBindings.add(new EntryBinding(editor, searchText(entry)));
            entriesHost.addView(editor.getView(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        String description = category.descriptionTranslationKey() == null ? ""
                : Component.translatable(category.descriptionTranslationKey()).getString();
        TextView help = null;
        if (!description.isEmpty()) {
            help = UIUtils.createLockedTextView(context.uiContext(), description, 10.5f, COLOR_DISCLOSURE);
            help.setPadding(0, UIUtils.dp2pxInt(6), 0, UIUtils.dp2pxInt(6));
            entriesHost.addView(help, new LinearLayout.LayoutParams(-1, -2));
        }
        List<GroupBinding> children = new ArrayList<>();
        for (var child : specification.children()) {
            GroupBinding binding = createGroup(context, child, depth + 1);
            children.add(binding);
            LinearLayout.LayoutParams params = groupParams();
            params.topMargin = UIUtils.dp2pxInt(3);
            params.bottomMargin = UIUtils.dp2pxInt(3);
            entriesHost.addView(binding.view(), params);
        }
        GroupBinding binding = new GroupBinding(group, entriesHost, disclosure, List.copyOf(entryBindings),
                List.copyOf(children), help, (Component.translatable(category.titleTranslationKey()).getString()
                + "\n" + description).toLowerCase(Locale.ROOT));
        header.setOnClickListener(view -> binding.toggle());
        header.setOnHoverListener((view, event) -> {
            switch (event.getAction()) {
                case icyllis.modernui.view.MotionEvent.ACTION_HOVER_ENTER ->
                        header.setBackground(headerBackground(COLOR_HEADER_HOVER));
                case icyllis.modernui.view.MotionEvent.ACTION_HOVER_EXIT ->
                        header.setBackground(headerBackground(COLOR_HEADER));
            }
            return true;
        });
        return binding;
    }

    private void revalidateEditors() {
        for (ConfigEntryEditor<?> editor : editors) {
            editor.revalidate();
        }
    }

    private static LinearLayout.LayoutParams groupParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = UIUtils.dp2pxInt(10.0f);
        return params;
    }

    private static ShapeDrawable groupBackground() {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(COLOR_GROUP);
        drawable.setCornerRadius(UIUtils.dp2px(2.0f));
        drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(1.0f)), COLOR_BORDER);
        return drawable;
    }

    private static ShapeDrawable headerBackground(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(2.0f));
        return drawable;
    }

    private static String searchText(ConfigEntry<?> entry) {
        return (entry.id() + "\n"
                + Component.translatable(entry.labelTranslationKey()).getString() + "\n"
                + Component.translatable(entry.descriptionTranslationKey()).getString())
                .toLowerCase(Locale.ROOT);
    }

    private record EntryBinding(ConfigEntryEditor<?> editor, String searchText) {
    }

    private static final class GroupBinding {
        private final LinearLayout view;
        private final LinearLayout entriesHost;
        private final SvgIconView disclosure;
        private final List<EntryBinding> entries;
        private final List<GroupBinding> children;
        private final TextView help;
        private final String searchText;
        private boolean expanded;
        private boolean searchExpanded;

        private GroupBinding(LinearLayout view, LinearLayout entriesHost, SvgIconView disclosure,
                             List<EntryBinding> entries, List<GroupBinding> children, TextView help, String searchText) {
            this.view = view;
            this.entriesHost = entriesHost;
            this.disclosure = disclosure;
            this.entries = entries;
            this.children = children;
            this.help = help;
            this.searchText = searchText;
            updateExpansion();
        }

        private LinearLayout view() { return view; }
        private boolean applySearch(String query, boolean ancestorMatches) {
            boolean categoryMatch = ancestorMatches || (!query.isEmpty() && searchText.contains(query));
            boolean matches = query.isEmpty() || categoryMatch;
            for (EntryBinding entry : entries) {
                boolean visible = query.isEmpty() || categoryMatch || entry.searchText().contains(query);
                entry.editor().getView().setVisibility(visible ? View.VISIBLE : View.GONE);
                matches |= visible;
            }
            for (GroupBinding child : children) matches |= child.applySearch(query, categoryMatch);
            if (help != null) help.setVisibility(query.isEmpty() || categoryMatch ? View.VISIBLE : View.GONE);
            view.setVisibility(matches ? View.VISIBLE : View.GONE);
            setSearchExpanded(!query.isEmpty() && matches);
            return matches;
        }

        private void toggle() {
            expanded = !expanded;
            updateExpansion();
        }

        private void setSearchExpanded(boolean value) {
            searchExpanded = value;
            updateExpansion();
        }

        private void updateExpansion() {
            boolean visible = expanded || searchExpanded;
            entriesHost.setVisibility(visible ? View.VISIBLE : View.GONE);
            disclosure.setIcon(SvgIconView.Icon.forExpandedState(visible));
        }
    }
}
