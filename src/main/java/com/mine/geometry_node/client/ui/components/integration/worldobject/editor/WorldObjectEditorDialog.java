package com.mine.geometry_node.client.ui.components.integration.worldobject.editor;

import com.mine.geometry_node.client.feature.scene.ClientSceneService;
import com.mine.geometry_node.client.feature.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.layout.TagFlowLayout;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagChip;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.components.composite.navigation.UiSubTabs;
import com.mine.geometry_node.client.ui.components.integration.metadata.DataDictionaryBinding;
import com.mine.geometry_node.client.ui.components.integration.metadata.RepositoryDataDictionaryBindings;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryEditor;
import com.mine.geometry_node.client.ui.components.integration.scene.properties.ScenePropertiesView;
import com.mine.geometry_node.client.ui.overlay.OverlayCloseReason;
import com.mine.geometry_node.client.ui.overlay.OverlayHost;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Three-page world-object editor. Scene transactions and metadata remain owned by their existing services. */
public final class WorldObjectEditorDialog extends ModalWindowView {
    private final WorldObjectEntry entry;
    private final ClientWorldObjectRepository.Session metadata;
    private final BooleanSupplier scopeCurrent;
    private final Runnable applied, release;
    private final ScenePropertiesView properties;
    private final WorldObjectBasicSummary summary;
    private final TextView availability, tagStatus;
    private final UiTextInput tagInput;
    private final UiActionButton addTag;
    private final TagFlowLayout tags;
    private final FrameLayout pages;
    private final View[] pageViews;
    private final UiSubTabs tabs;
    private final DataDictionaryBinding binding;
    private UiDataDictionaryEditor.Embedded data;
    private List<String> renderedTags;
    private boolean active, closed;
    private int selectedTab;
    private boolean dismissingMenuGesture;
    private final Runnable refreshTask = this::refresh;
    private final Runnable metadataChanged = () -> MuiModApi.postToUiThread(this::refresh);

    public static void show(View owner, WorldObjectEntry entry, ClientWorldObjectRepository.Session source,
                            BooleanSupplier scopeCurrent, Runnable applied, Runnable release) {
        OverlayHost host = OverlayHost.find(owner);
        if (host == null || !scopeCurrent.getAsBoolean()) { release.run(); return; }
        // Fork before opening: subsequent table queries must not cancel this editor's metadata operations.
        ClientWorldObjectRepository.Session metadata;
        try { metadata = source.forkDataSession(entry); }
        catch (IllegalStateException unavailable) { metadata = null; }
        try {
            WorldObjectEditorDialog dialog = new WorldObjectEditorDialog(owner, entry, metadata, scopeCurrent, applied, release);
            host.showModal(dialog, new ModalOptions(false, true, owner, UiThemes.EDITOR.surface().modalScrim()));
        } catch (RuntimeException failure) {
            if (metadata != null) metadata.close();
            release.run(); throw failure;
        }
    }

    WorldObjectEditorDialog(View owner, WorldObjectEntry entry, ClientWorldObjectRepository.Session metadata,
                                    BooleanSupplier scopeCurrent, Runnable applied, Runnable release) {
        super(owner.getContext(), tr("world") + " · " + (entry.name().isBlank() ? entry.type() : entry.name()), MovementMode.DRAGGABLE,
                Style.forTheme(UiThemes.EDITOR));
        bindSessionOwner(owner);
        this.entry = entry; this.metadata = metadata;
        this.scopeCurrent = Objects.requireNonNull(scopeCurrent); this.applied = Objects.requireNonNull(applied); this.release = Objects.requireNonNull(release);
        setPreferredSizeDp(900, 680); setMinimumSizeDp(540, 420);
        contentHost().setPadding(0, 0, 0, 0);
        LinearLayout root = column();
        tabs = new UiSubTabs(getContext(), UiThemes.EDITOR);
        String[] labels = { Component.translatable("geometry_node.world_object_editor.basic").getString(), tr("tags"), tr("data.title") };
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            tabs.addTab(Integer.toString(i), labels[i], () -> selectTab(index));
        }
        root.addView(tabs, new LinearLayout.LayoutParams(-1, px(40)));
        pages = new FrameLayout(getContext()); root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout basic = column();
        availability = text(""); basic.addView(availability, new LinearLayout.LayoutParams(-1, -2));
        ClientSceneService scene = ClientSceneService.INSTANCE;
        long generation = scene.confirmedSnapshot().worldGeneration();
        properties = new ScenePropertiesView(getContext(), scene, scopeCurrent, snapshot ->
                scopeCurrent.getAsBoolean() && snapshot.worldGeneration() == generation
                        && entry.dimension().equals(snapshot.dimension()) ? snapshot.object(entry.sceneObjectId()) : null);
        properties.setOnApplied(applied);
        properties.setSelectionSummaryEnabled(false);
        basic.addView(properties, new LinearLayout.LayoutParams(-1, 0, 1));
        summary = new WorldObjectBasicSummary(getContext(), entry);
        basic.addView(summary, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout tagPage = column(); tagPage.setPadding(px(20), px(16), px(20), px(16));
        LinearLayout compose = new LinearLayout(getContext());
        tagInput = new UiTextInput(getContext()); tagInput.setSingleLine(true); tagInput.setHint(tr("tags"));
        tagInput.setEditActions(() -> editTag(tagInput.getText().toString(), true), () -> tagInput.setText(""));
        compose.addView(tagInput, new LinearLayout.LayoutParams(0, px(32), 1));
        addTag = button(tr("add_tag"), () -> editTag(tagInput.getText().toString(), true));
        compose.addView(addTag, new LinearLayout.LayoutParams(-2, px(32)));
        tagPage.addView(compose, new LinearLayout.LayoutParams(-1, -2));
        UiScrollView scroll = new UiScrollView(getContext(), UiThemes.EDITOR.scroll());
        tags = new TagFlowLayout(getContext(), 8, 8); tags.setPadding(0, px(16), 0, px(16));
        scroll.addView(tags, new FrameLayout.LayoutParams(-1, -2));
        tagPage.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        tagStatus = text(""); tagPage.addView(tagStatus, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout dataPage = new FrameLayout(getContext());
        binding = metadata == null ? null : RepositoryDataDictionaryBindings.world(metadata, entry, scopeCurrent);
        pageViews = new View[]{basic, tagPage, dataPage};
        for (View page : pageViews) pages.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout footer = new LinearLayout(getContext());
        footer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        footer.setPadding(px(18), px(10), px(18), px(10));
        footer.addView(button(Component.translatable("geometry_node.entity_editor.close").getString(), this::requestClose),
                new LinearLayout.LayoutParams(-2, px(32)));
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        setContent(root); selectTab(0);
    }

    private void selectTab(int index) {
        if (closed || active && selectedTab == index) return;
        if (active && selectedTab == 0 && index != 0) properties.pause();
        if (data != null && index != 2) data.suspend();
        selectedTab = index;
        if (index == 2 && data == null) {
            FrameLayout host = (FrameLayout) pageViews[2];
            if (binding == null) {
                if (host.getChildCount() == 0) host.addView(text(unavailable()), new FrameLayout.LayoutParams(-1, -2));
            }
            else {
                data = UiDataDictionaryEditor.createEmbedded(this, binding, UiThemes.EDITOR, 12,
                        UiDataDictionaryEditor.CommitMode.ON_FOCUS_LOSS);
                host.addView(data.view(), new FrameLayout.LayoutParams(-1, -1));
            }
        }
        for (int i = 0; i < pageViews.length; i++) {
            pageViews[i].setVisibility(i == index ? VISIBLE : GONE);
        }
        tabs.setSelectedTab(Integer.toString(index));
        if (active && index == 0) properties.resume();
        if (active && data != null && index == 2) data.resume();
        if (active) refresh();
    }

    @Override protected void onWindowShown() {
        if (closed) return;
        active = true;
        if (metadata != null) metadata.addChangeListener(metadataChanged);
        refresh();
    }
    @Override protected void onWindowSuspended() {
        active = false; removeCallbacks(refreshTask); properties.pause();
        if (data != null) data.suspend();
    }
    @Override protected void onWindowResumed() {
        if (closed) return;
        active = true; if (selectedTab == 0) properties.resume();
        if (data != null && selectedTab == 2) data.resume(); refresh();
    }
    @Override protected void onWindowClosed(OverlayCloseReason reason) { dispose(); }
    @Override protected void onWindowDestroyed() { dispose(); }
    @Override public boolean onEscapePressed() {
        if (properties.isMenuOpen()) { properties.dismissMenu(); return true; }
        return data != null && selectedTab == 2 && data.onEscapePressed();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (active && selectedTab == 0 && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            dismissingMenuGesture = properties.dismissIfOutside(event.getRawX(), event.getRawY());
            properties.commitIfOutside(event.getRawX(), event.getRawY());
        }
        if (dismissingMenuGesture) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                dismissingMenuGesture = false;
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private WorldObjectEntry liveEntry() {
        return metadata == null ? null : metadata.snapshot().entries().stream()
                .filter(value -> value.key().equals(entry.key())).findFirst().orElse(null);
    }
    private void refresh() {
        removeCallbacks(refreshTask);
        if (!active || closed) return;
        if (selectedTab == 0) {
            properties.refresh();
            boolean available = properties.hasTarget();
            properties.setVisibility(available ? VISIBLE : GONE);
            summary.setVisibility(available ? GONE : VISIBLE);
            availability.setText(available ? "" : Component.translatable(WorldObjectBasicSummary.reason(entry,
                    ClientSceneService.INSTANCE.confirmedSnapshot().dimension(), scopeCurrent.getAsBoolean())).getString());
            availability.setVisibility(available ? GONE : VISIBLE);
        }
        WorldObjectEntry live = liveEntry();
        boolean editable = scopeCurrent.getAsBoolean() && live != null
                && metadata.canEditTags(live) && !metadata.isTagUpdatePending();
        tagInput.setEnabled(editable); addTag.setEnabled(editable);
        List<String> values = live == null ? entry.tags() : live.tags();
        if (!values.equals(renderedTags) || tags.isEnabled() != editable) {
            renderedTags = List.copyOf(values); tags.setEnabled(editable); tags.removeAllViews();
            for (String tag : values) {
                UiTagChip chip = new UiTagChip(getContext(), tag, UiThemes.EDITOR, 12);
                if (editable) chip.setOnRemove(Component.translatable("geometry_node.entity_editor.tags.remove").getString(),
                        () -> editTag(tag, false));
                tags.addView(chip, new TagFlowLayout.LayoutParams(-2, px(UiTagChip.HEIGHT_DP)));
            }
        }
        String message = metadata == null ? "" : metadata.tagUpdateMessage();
        tagStatus.setText(!message.isEmpty() ? Component.translatable("geometry_node." + message).getString()
                : !editable ? unavailable() : "");
        if (selectedTab == 0) postDelayed(refreshTask, 200);
    }
    private void editTag(String tag, boolean add) {
        WorldObjectEntry live = liveEntry();
        if (!active || closed || !scopeCurrent.getAsBoolean() || live == null) return;
        if (add ? metadata.addTag(live, tag) : metadata.removeTag(live, tag)) {
            if (add) tagInput.setText("");
        }
        refresh();
    }
    private void dispose() {
        if (closed) return;
        closed = true; active = false; removeCallbacks(refreshTask); properties.suspend(); release.run();
        if (data != null) data.dispose();
        if (metadata != null) metadata.removeChangeListener(metadataChanged);
        if (metadata == null) applied.run();
        else new PendingMetadataClose(metadata, entry, applied).start();
    }

    /** Repository ACK/disconnect/15s transport expiry settles writes without retaining a closed modal or polling. */
    static final class PendingMetadataClose {
        private final ClientWorldObjectRepository.Session session;
        private final WorldObjectEntry entry;
        private final Runnable applied;
        private final Runnable changed;
        private boolean finished;
        PendingMetadataClose(ClientWorldObjectRepository.Session session, WorldObjectEntry entry, Runnable applied) {
            this(session, entry, applied, MuiModApi::postToUiThread);
        }
        PendingMetadataClose(ClientWorldObjectRepository.Session session, WorldObjectEntry entry, Runnable applied,
                             java.util.function.Consumer<Runnable> uiQueue) {
            this.session = session; this.entry = entry; this.applied = applied;
            changed = () -> uiQueue.accept(this::settle);
        }
        void start() { session.addChangeListener(changed); settle(); }
        void settle() {
            if (finished || session.isTagUpdatePending() || session.dataSnapshot(entry).pending()) return;
            finished = true; session.removeChangeListener(changed); session.close(); applied.run();
        }
    }
    private LinearLayout column() {
        LinearLayout column = new LinearLayout(getContext()); column.setOrientation(VERTICAL); return column;
    }
    private TextView text(String value) { return UIUtils.createLockedTextView(getContext(), value, 12, UiThemes.EDITOR.text().muted()); }
    private UiActionButton button(String label, Runnable action) {
        UiActionButton button = new UiActionButton(getContext(), label, UiActionButton.Role.QUIET, UiActionButton.Density.COMPACT);
        button.setOnClickListener(view -> action.run()); return button;
    }
    private static String unavailable() { return Component.translatable("geometry_node.world_object_editor.unavailable").getString(); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
    private static String tr(String key) { return Component.translatable("geometry_node.database." + key).getString(); }
}
