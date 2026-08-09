package com.mine.geometry_node.client.ui.settings.window;

import com.mine.geometry_node.client.ui.common.UiActionButton;
import com.mine.geometry_node.client.ui.persistence.config.ConfigDraft;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.ConfigRegistry;
import com.mine.geometry_node.client.ui.settings.editor.ConfigEntryEditor;
import com.mine.geometry_node.client.ui.settings.editor.SettingsEditorFactory;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPage;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageContext;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageDefinition;
import com.mine.geometry_node.client.ui.shell.MainUiServices;
import com.mine.geometry_node.client.ui.shell.layer.OverlayCloseReason;
import com.mine.geometry_node.client.ui.shell.layer.modal.ModalOptions;
import com.mine.geometry_node.client.ui.shell.layer.modal.ModalWindowView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class SettingsWindow extends ModalWindowView {
    private static final int COLOR_PANEL = 0xFF303030;
    private static final int COLOR_NAV = 0xFF292929;
    private static final int COLOR_BORDER = 0xFF181818;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF929292;
    private static final int COLOR_DANGER = 0xFFE18A8A;

    private final MainUiServices services;
    private final ConfigDraft draft;
    private final SettingsWindowEnvironment editorEnvironment;
    private final List<SettingsPageDefinition> definitions;
    private final Map<String, SettingsPage> pages = new LinkedHashMap<>();
    private final Consumer<SettingsWindow> destroyedCallback;
    private final SettingsNavigationView navigation;
    private final SettingsContentHost contentHost;
    private final EditText searchInput;
    private final TextView statusView;
    private final UiActionButton applyButton;
    private String selectedPageId;
    private boolean allowDiscard;
    private boolean confirmationOpen;
    private boolean disposed;

    SettingsWindow(Context context, MainUiServices services, Consumer<SettingsWindow> destroyedCallback) {
        super(context, tr("geometry_node.settings.title"), MovementMode.DRAGGABLE);
        this.services = services;
        this.destroyedCallback = destroyedCallback != null ? destroyedCallback : ignored -> { };
        draft = ConfigManager.INSTANCE.createDraft();
        editorEnvironment = new SettingsWindowEnvironment(context, services);
        definitions = services.settingsPageRegistry().definitions();
        if (definitions.isEmpty()) {
            throw new IllegalStateException("No settings pages are registered");
        }

        setPreferredSizeDp(820.0f, 600.0f);
        setMinimumSizeDp(600.0f, 420.0f);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(VERTICAL);
        root.setBackground(rect(COLOR_PANEL, 0));

        searchInput = createSearchInput(context);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(32.0f)
        );
        searchParams.bottomMargin = UIUtils.dp2pxInt(9.0f);
        root.addView(searchInput, searchParams);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(HORIZONTAL);
        navigation = new SettingsNavigationView(context, definitions, this::selectPage);
        navigation.setBackground(rect(COLOR_NAV, COLOR_BORDER));
        body.addView(navigation, new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(170.0f),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        contentHost = new SettingsContentHost(context);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f
        );
        contentParams.leftMargin = UIUtils.dp2pxInt(10.0f);
        body.addView(contentHost, contentParams);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        setContent(root);

        statusView = UIUtils.createLockedTextView(context, "", 10.0f, COLOR_MUTED);
        statusView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.addView(statusView, new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(150.0f),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        actions.addView(UiActionButton.create(context, tr("geometry_node.settings.action.restore_defaults"), UiActionButton.Role.QUIET,
                view -> resetAll()), buttonParams(112.0f, true));
        actions.addView(UiActionButton.create(context, tr("geometry_node.settings.action.cancel"), UiActionButton.Role.SECONDARY,
                view -> requestClose()), buttonParams(84.0f, true));
        applyButton = UiActionButton.create(context, tr("geometry_node.settings.action.apply"), UiActionButton.Role.SECONDARY,
                view -> applyChanges(false));
        actions.addView(applyButton, buttonParams(84.0f, true));
        actions.addView(UiActionButton.create(context, tr("geometry_node.settings.action.ok"), UiActionButton.Role.PRIMARY,
                view -> applyChanges(true)), buttonParams(70.0f, false));
        setActions(actions);

        createPages(context);
        selectPage(definitions.get(0).id());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) { applySearch(editable.toString()); }
        });
        updateState();
    }

    @Override
    public boolean canClose(OverlayCloseReason reason) {
        if (allowDiscard || reason == OverlayCloseReason.HOST_DESTROYED || !draft.isDirty()) {
            return true;
        }
        if (!confirmationOpen) {
            confirmationOpen = true;
            showDiscardConfirmation();
        }
        return false;
    }

    @Override
    protected void onWindowDestroyed() {
        disposePages();
        destroyedCallback.accept(this);
    }

    void disposeUnshown() {
        disposePages();
    }

    private void createPages(Context context) {
        try {
            SettingsPageContext pageContext = new SettingsPageContext(
                    context,
                    draft,
                    ConfigRegistry.INSTANCE,
                    SettingsEditorFactory.INSTANCE,
                    editorEnvironment,
                    this::updateState
            );
            for (SettingsPageDefinition definition : definitions) {
                SettingsPage page = definition.factory().create(pageContext);
                if (page == null || page.getView() == null) {
                    throw new IllegalStateException("Settings page factory returned no view: " + definition.id());
                }
                pages.put(definition.id(), page);
            }
        } catch (RuntimeException exception) {
            disposePages();
            throw exception;
        }
    }

    private void selectPage(String pageId) {
        SettingsPage page = pages.get(pageId);
        if (page == null) {
            return;
        }
        editorEnvironment.closeTransient();
        selectedPageId = pageId;
        navigation.setSelected(pageId);
        contentHost.show(page);
    }

    private void applySearch(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<String, Boolean> matches = new LinkedHashMap<>();
        for (SettingsPageDefinition definition : definitions) {
            SettingsPage page = pages.get(definition.id());
            String pageText = (definition.id() + "\n"
                    + Component.translatable(definition.titleTranslationKey()).getString())
                    .toLowerCase(Locale.ROOT);
            boolean pageTitleMatch = !normalized.isEmpty() && pageText.contains(normalized);
            matches.put(definition.id(), page != null
                    && page.applySearch(pageTitleMatch ? "" : normalized));
        }
        navigation.applyFilter(id -> matches.getOrDefault(id, false));
        if (!matches.getOrDefault(selectedPageId, false)) {
            definitions.stream()
                    .map(SettingsPageDefinition::id)
                    .filter(id -> matches.getOrDefault(id, false))
                    .findFirst()
                    .ifPresent(this::selectPage);
        }
    }

    private void applyChanges(boolean closeAfterApply) {
        InvalidEditor invalid = firstInvalidEditor();
        if (invalid != null) {
            searchInput.setText("");
            selectPage(invalid.pageId());
            invalid.editor().getView().requestFocus();
            statusView.setText(invalid.editor().validationMessage());
            statusView.setTextColor(COLOR_DANGER);
            return;
        }
        if (draft.isDirty()) {
            ConfigManager.INSTANCE.apply(draft);
        }
        statusView.setText("");
        statusView.setTextColor(COLOR_MUTED);
        updateState();
        if (closeAfterApply) {
            allowDiscard = true;
            requestClose();
        }
    }

    private InvalidEditor firstInvalidEditor() {
        for (SettingsPageDefinition definition : definitions) {
            SettingsPage page = pages.get(definition.id());
            if (page == null) {
                continue;
            }
            for (ConfigEntryEditor<?> editor : page.editors()) {
                if (!editor.isValid()) {
                    return new InvalidEditor(definition.id(), editor);
                }
            }
        }
        return null;
    }

    private void resetAll() {
        draft.resetAll();
        pages.values().forEach(SettingsPage::refresh);
        statusView.setText("");
        updateState();
    }

    private void updateState() {
        if (applyButton != null) {
            applyButton.setEnabled(draft.isDirty());
            applyButton.setAlpha(draft.isDirty() ? 1.0f : 0.55f);
        }
    }

    private void showDiscardConfirmation() {
        DiscardChangesDialog dialog = new DiscardChangesDialog(getContext(), () -> {
            confirmationOpen = false;
            allowDiscard = true;
            requestClose();
        }, () -> confirmationOpen = false);
        try {
            services.layerManager().showModal(dialog, new ModalOptions(false, true, this));
        } catch (RuntimeException exception) {
            confirmationOpen = false;
            throw exception;
        }
    }

    private void disposePages() {
        if (disposed) {
            return;
        }
        disposed = true;
        editorEnvironment.close();
        for (SettingsPage page : pages.values()) {
            page.dispose();
        }
        pages.clear();
    }

    private EditText createSearchInput(Context context) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(0, UIUtils.dp2px(11.0f));
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setHint(tr("geometry_node.settings.search_hint"));
        input.setPadding(UIUtils.dp2pxInt(9.0f), 0, UIUtils.dp2pxInt(9.0f), 0);
        input.setBackground(rect(0xFF242424, 0xFF4A4A4A));
        return input;
    }

    private static LinearLayout.LayoutParams buttonParams(float widthDp, boolean withRightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(widthDp),
                UIUtils.dp2pxInt(30.0f)
        );
        if (withRightMargin) {
            params.rightMargin = UIUtils.dp2pxInt(6.0f);
        }
        return params;
    }

    private static ShapeDrawable rect(int color, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(2.0f));
        if (strokeColor != 0) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(1.0f)), strokeColor);
        }
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private record InvalidEditor(String pageId, ConfigEntryEditor<?> editor) {
    }

    private static final class DiscardChangesDialog extends ModalWindowView {
        private final Runnable cancelled;
        private boolean decided;

        private DiscardChangesDialog(Context context, Runnable discard, Runnable cancelled) {
            super(context, tr("geometry_node.settings.unsaved.title"), MovementMode.FIXED_CENTER);
            this.cancelled = cancelled;
            setPreferredSizeDp(390.0f, 170.0f);
            TextView message = UIUtils.createLockedTextView(context,
                    tr("geometry_node.settings.unsaved.message"), 11.0f, COLOR_TEXT);
            message.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            message.setSingleLine(false);
            setContent(message);

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            UiActionButton cancel = UiActionButton.create(context, tr("geometry_node.settings.action.cancel"),
                    UiActionButton.Role.SECONDARY,
                    view -> requestClose());
            actions.addView(cancel, buttonParams(84.0f, true));
            UiActionButton confirm = UiActionButton.create(context, tr("geometry_node.settings.action.discard"),
                    UiActionButton.Role.DANGER, view -> {
                decided = true;
                if (requestClose()) {
                    discard.run();
                }
            });
            actions.addView(confirm, buttonParams(112.0f, false));
            setActions(actions);
        }

        @Override
        protected void onWindowDestroyed() {
            if (!decided) {
                cancelled.run();
            }
        }
    }
}
