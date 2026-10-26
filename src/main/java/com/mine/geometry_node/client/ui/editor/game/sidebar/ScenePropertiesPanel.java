package com.mine.geometry_node.client.ui.editor.game.sidebar;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.composite.layout.UiPropertyRow;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.feature.scene.ClientSceneService;
import com.mine.geometry_node.client.feature.scene.SceneEditCapabilities;
import com.mine.geometry_node.client.ui.components.composite.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.*;
import com.mine.geometry_node.client.ui.editor.game.GameViewportWindow;
import com.mine.geometry_node.client.ui.editor.game.ViewportMode;
import com.mine.geometry_node.client.ui.editor.game.sidebar.properties.ScenePropertyControl;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.property.SceneInspectorSchemas;
import com.mine.geometry_node.core.engine.system.scene.property.SceneInspectorSchemas.*;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Schema-driven edits form one transaction and wait for server acknowledgement. */
public final class ScenePropertiesPanel extends UiScrollView implements SidebarPanel {
    public static final String PANEL_ID = "scene_properties";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(PANEL_ID,
            "geometry_node.game_view.scene.properties", 90, Set.of(SidebarPanelScope.GAME_VIEWPORT),
            c -> new ScenePropertiesPanel(c.uiContext(), c.requireOwner(GameViewportWindow.class)), SvgIconView.Icon.PROPERTIES);
    private final ClientSceneService service;
    private final java.util.function.BooleanSupplier editingMode;
    private final Map<String, UiTextInput> fields = new LinkedHashMap<>();
    private final Map<String, ScenePropertyControl> controls = new LinkedHashMap<>();
    private final Map<String, UiIconButton> copyButtons = new LinkedHashMap<>();
    private final LinearLayout form;
    private final TextView status;
    private Schema schema;
    private List<FieldSignature> formSignature = List.of();
    private SceneObject editing, submitted;
    private boolean updatingFields, dirty;
    private long editingGeneration;
    private String validationMessage = "";
    private UiPopover choiceMenu;
    private long menuEpoch;
    private final Runnable autoCommit = this::commitDraft;

    public ScenePropertiesPanel(Context context, GameViewportWindow owner) {
        this(context, ClientSceneService.INSTANCE, () -> owner.mode() == ViewportMode.EDIT);
    }

    ScenePropertiesPanel(Context context, ClientSceneService service, java.util.function.BooleanSupplier editingMode) {
        super(context);
        this.service = service;
        this.editingMode = editingMode;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int p = UIUtils.dp2pxInt(10);
        column.setPadding(p, p, p, p);
        addView(column, new ViewGroup.LayoutParams(-1, -2));
        status = UIUtils.createLockedTextView(context, "", 11, UiThemes.EDITOR.text().muted());
        column.addView(status, new LinearLayout.LayoutParams(-1, -2));
        form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        column.addView(form, new LinearLayout.LayoutParams(-1, -2));
        refresh();
    }

    private void watch(UiTextInput input) {
        input.addTextChangedListener(new icyllis.modernui.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // A listener can refresh the inspector before Editable receives the new text.
                markDirty();
            }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(icyllis.modernui.text.Editable text) { markDirty(); }
        });
        input.setOnKeyListener((view, key, event) -> {
            if (key != KeyEvent.KEY_ENTER && key != KeyEvent.KEY_KP_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) commitDraft();
            return true;
        });
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused && dirty && !updatingFields) commitDraft();
        });
    }

    private void markDirty() {
        if (updatingFields || editing == null) return;
        dirty = true;
        validationMessage = "";
        removeCallbacks(autoCommit);
    }

    private void commitControl() {
        if (updatingFields) return;
        markDirty();
        commitDraft();
    }

    /** Parent invokes before dispatch, including clicks on non-focusable blank space. */
    public void commitIfOutside(float rootX, float rootY) {
        if (!(findFocus() instanceof UiTextInput input)) return;
        float[] point = new float[2];
        if (!UIUtils.rootToLocal(input, rootX, rootY, new icyllis.modernui.graphics.Matrix(), point)
                || point[0] < 0 || point[1] < 0 || point[0] >= input.getWidth() || point[1] >= input.getHeight()) {
            commitDraft();
            input.clearFocus();
        }
    }

    /** Owner consumes the entire outside-click gesture when this returns true. */
    public boolean dismissIfOutside(float rootX, float rootY) {
        if (choiceMenu == null || !choiceMenu.dismissIfOutside(rootX, rootY)) return false;
        dismissChoices();
        return true;
    }

    public boolean isMenuOpen() { return choiceMenu != null && choiceMenu.isOpen(); }
    public void dismissMenu() { dismissChoices(); }

    private void discardDraft() {
        removeCallbacks(autoCommit);
        dirty = false;
        submitted = null;
        validationMessage = "";
        dismissChoices();
    }

    @Override protected void onDetachedFromWindow() {
        discardDraft();
        editing = null;
        super.onDetachedFromWindow();
    }

    @Override public View getView() { return this; }
    @Override public void onSelected() { refresh(); }
    @Override public void onDeselected() { dismissChoices(); }

    public void refresh() {
        if (updatingFields) return;
        var snapshot = service.confirmedSnapshot();
        SceneObject object = snapshot.active() == null ? null : snapshot.object(snapshot.active());
        boolean editable = editingMode.getAsBoolean() && snapshot.canEdit() && object != null
                && SceneEditCapabilities.evaluate(SceneEditCapabilities.Action.PROPERTIES, List.of(object), false).allowed();
        form.setVisibility(object == null ? GONE : VISIBLE);
        if (object == null) {
            discardDraft();
            editing = null;
            updateStatus(snapshot, null);
            return;
        }
        boolean sameTarget = editing != null && editingGeneration == snapshot.worldGeneration()
                && object.id().equals(editing.id());
        if (sameTarget && submitted != null) {
            if (submitted.sameContent(object)) {
                // Rebase on our broadcast/ACK without replacing newer text in the controls.
                editing = object;
                if (!snapshot.pending()) submitted = null;
            } else if (!snapshot.pending() || object.revision() != editing.revision()
                    || !object.sameContent(editing)) {
                discardDraft();
                editing = null;
            }
        }
        boolean reset = editing == null || editingGeneration != snapshot.worldGeneration()
                || !object.id().equals(editing.id()) || object.revision() != editing.revision()
                || !object.sameContent(editing) || (!editable && (dirty || submitted != null));
        if (reset) discardDraft();
        if (!editable) dismissChoices();

        Schema nextSchema = SceneInspectorSchemas.schema(object);
        List<FieldSignature> signature = nextSchema == schema ? formSignature
                : nextSchema.fields().stream().map(FieldSignature::of).toList();
        boolean rebuild = !signature.equals(formSignature);
        boolean preserveValues = !reset && (dirty || submitted != null);
        Map<String, String> preserved = new LinkedHashMap<>();
        if (rebuild && preserveValues) {
            for (FieldSignature field : signature) {
                ScenePropertyControl old = controls.get(field.key());
                if (old != null && field.editable() && field.equals(FieldSignature.of(old.field()))) {
                    preserved.put(field.key(), old.value());
                }
            }
        }
        updatingFields = true;
        try {
            schema = nextSchema;
            editing = object;
            editingGeneration = snapshot.worldGeneration();
            if (rebuild) rebuildForm(signature);
            for (Field field : schema.fields()) {
                ScenePropertyControl control = controls.get(field.key());
                if (rebuild || !preserveValues || !field.editable()) {
                    control.setValue(preserved.containsKey(field.key()) ? preserved.get(field.key()) : field.value(object));
                }
                control.setEditable(editable);
                copyButtons.get(field.key()).setVisibility(control.view().isEnabled() ? GONE : VISIBLE);
            }
        } finally {
            updatingFields = false;
        }
        updateStatus(snapshot, object);
    }

    private void updateStatus(ClientSceneService.Snapshot snapshot, SceneObject object) {
        status.setText(!validationMessage.isBlank() ? validationMessage
                : !snapshot.message().isBlank() ? snapshot.message()
                : object == null ? tr("nothing_selected")
                : Component.translatable("geometry_node.game_view.scene.selected", snapshot.selected().size()).getString());
    }

    private void rebuildForm(List<FieldSignature> signature) {
        dismissChoices();
        fields.clear();
        controls.clear();
        copyButtons.clear();
        form.removeAllViews();
        formSignature = signature;
        for (Field field : schema.fields()) {
            ScenePropertyControl control = new ScenePropertyControl(getContext(), field, this::commitControl, this::showChoices);
            if (controls.putIfAbsent(field.key(), control) != null) throw new IllegalArgumentException("Duplicate inspector field: " + field.key());
            if (control.textInput() != null) {
                fields.put(field.key(), control.textInput());
                watch(control.textInput());
            }
            TextView label = UIUtils.createLockedTextView(getContext(), field.label().getString(), 11, UiThemes.EDITOR.text().secondary());
            label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTooltipText(field.label().getString());
            UiIconButton copy = UiIconButton.createThemed(getContext(), UiThemes.EDITOR,
                    SvgIconView.Icon.COPY, Component.translatable("geometry_node.game_view.scene.copy_value").getString(),
                    UiIconButton.Preset.INLINE);
            copy.setOnClickListener(v -> {
                if (controls.get(field.key()) != control) return;
                String value = control.value();
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> minecraft.keyboardHandler.setClipboard(value));
            });
            copyButtons.put(field.key(), copy);
            boolean checkbox = control.view() instanceof UiCheckBox;
            UiPropertyRow row = new UiPropertyRow(getContext(), label, control.view(), copy,
                    new UiPropertyRow.Layout(80, 28, 8, 24, 6), checkbox
                    ? new LinearLayout.LayoutParams(UIUtils.dp2pxInt(20), UIUtils.dp2pxInt(20))
                    : new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(28), 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = UIUtils.dp2pxInt(5);
            form.addView(row, lp);
        }
    }

    private void commitDraft() {
        if (!dirty || updatingFields) return;
        refresh();
        if (!dirty) return;
        var snapshot = service.confirmedSnapshot();
        if (editing == null || editingGeneration != snapshot.worldGeneration()
                || !editingMode.getAsBoolean() || !snapshot.canEdit()) return;
        if (snapshot.pending()) {
            removeCallbacks(autoCommit);
            postDelayed(autoCommit, 100);
            return;
        }
        try {
            validationMessage = "";
            Map<String, String> values = new LinkedHashMap<>();
            for (Field field : schema.fields()) {
                if (field.editable()) values.put(field.key(), controls.get(field.key()).value());
            }
            SceneObject replacement = schema.apply(editing, values);
            if (replacement.sameContent(editing)) { dirty = false; return; }
            service.update(editingGeneration, replacement);
            if (service.snapshot().pending()) { submitted = replacement; dirty = false; }
        } catch (RuntimeException invalid) {
            validationMessage = tr("invalid");
            status.setText(validationMessage);
        }
    }

    private void showChoices(ScenePropertyControl control) {
        refresh();
        if (controls.get(control.field().key()) != control || !control.view().isEnabled()) return;
        dismissChoices();
        FrameLayout host = menuHost();
        if (host == null) return;
        long token = menuEpoch;
        SceneObject target = editing;
        long generation = editingGeneration;
        LinearLayout options = new LinearLayout(getContext());
        options.setOrientation(LinearLayout.VERTICAL);
        for (Choice choice : control.field().options()) {
            UiActionButton option = new UiActionButton(getContext(), choice.label().getString(),
                    UiActionButton.Role.QUIET, UiActionButton.Density.COMPACT);
            option.setOnClickListener(v -> {
                if (token != menuEpoch || choiceMenu == null || !choiceMenu.isOpen()) return;
                refresh();
                if (token != menuEpoch || controls.get(control.field().key()) != control
                        || editingGeneration != generation || !editing.sameContent(target)
                        || editing.revision() != target.revision() || !control.view().isEnabled()) return;
                dismissChoices();
                control.choose(choice.value());
            });
            options.addView(option, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(28)));
        }
        choiceMenu = new UiPopover(getContext(), options, 220);
        choiceMenu.showBelow(control.view(), host);
    }

    private FrameLayout menuHost() {
        View current = this;
        while (current.getParent() instanceof View parent) {
            current = parent;
            if (current instanceof FrameLayout frame && !(current instanceof ScrollView)) return frame;
        }
        return null;
    }

    private void dismissChoices() {
        menuEpoch++;
        if (choiceMenu != null) choiceMenu.dismiss();
        choiceMenu = null;
    }

    private record FieldSignature(String key, Component label, Kind kind, List<Choice> options, boolean editable) {
        static FieldSignature of(Field field) {
            return new FieldSignature(field.key(), field.label(), field.kind(), List.copyOf(field.options()), field.editable());
        }
    }

    private static String tr(String key) { return Component.translatable("geometry_node.game_view.scene." + key).getString(); }
}
