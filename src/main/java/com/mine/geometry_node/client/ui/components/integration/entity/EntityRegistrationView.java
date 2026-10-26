package com.mine.geometry_node.client.ui.components.integration.entity;

import com.mine.geometry_node.client.feature.entity.index.ClientEntityIndex;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.state.UiStateScopes;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One registration command surface, shared by directory cells and the entity's general page. */
public final class EntityRegistrationView extends LinearLayout {
    interface Gateway {
        ClientEntityIndex.Request read(String dimension, UUID entity);
        ClientEntityIndex.Request setRegistered(String dimension, UUID entity, boolean registered, long revision);
    }
    private static final Gateway LIVE = new Gateway() {
        public ClientEntityIndex.Request read(String dimension, UUID entity) { return ClientEntityIndex.INSTANCE.read(dimension, entity); }
        public ClientEntityIndex.Request setRegistered(String dimension, UUID entity, boolean registered, long revision) {
            return ClientEntityIndex.INSTANCE.setRegistered(dimension, entity, registered, revision);
        }
    };
    private final UUID entity;
    private final String dimension, scope;
    private final UiTheme theme;
    private final UiCheckBox checkbox;
    private final UiDisplayText status;
    private final UiActionButton retry;
    private final Gateway gateway;
    private final Consumer<Runnable> queue;
    private final long connectionGeneration;
    private final BooleanSupplier editable;
    private final Runnable applied;
    private ClientEntityIndex.Request read;
    private boolean available, registered, pending, disposed, seeded, seedRegistered;
    private long revision, seedRevision, generation;
    private String error = "";

    public EntityRegistrationView(Context context, UiTheme theme, UUID entity, String dimension,
                                  boolean compact, BooleanSupplier editable, Runnable applied) {
        this(context, theme, entity, dimension, compact, editable, applied, LIVE, MuiModApi::postToUiThread);
    }
    EntityRegistrationView(Context context, UiTheme theme, UUID entity, String dimension,
                           boolean compact, BooleanSupplier editable, Runnable applied, Gateway gateway, Consumer<Runnable> queue) {
        super(context); this.theme = theme; this.entity = Objects.requireNonNull(entity);
        this.gateway = gateway; this.queue = queue;
        connectionGeneration = gateway == LIVE ? ClientEntityIndex.INSTANCE.generation() : 0;
        this.dimension = dimension; this.editable = editable; this.applied = applied; scope = UiStateScopes.current();
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(px(compact ? 9 : 0), 0, px(4), 0);
        checkbox = UiCheckBox.createThemed(context, theme);
        checkbox.setContentDescription(tr("register")); addView(checkbox, new LayoutParams(px(18), px(18)));
        status = new UiDisplayText(context, "", 12, theme.text().muted());
        LayoutParams caption = new LayoutParams(0, -2, 1); caption.leftMargin = px(8);
        addView(status, caption); status.setVisibility(compact ? GONE : VISIBLE);
        retry = UiActionButton.createThemed(context, theme, tr("retry"), UiActionButton.Role.QUIET, view -> refresh());
        addView(retry, new LayoutParams(-2, px(30))); retry.setVisibility(GONE);
        checkbox.setOnCheckedChangeListener((view, value) -> write(value)); refreshInputState();
    }

    /** Directory DTOs seed the control without issuing a read for every table row. */
    public void bindConfirmed(boolean registered, long revision) {
        if (disposed || pending || seeded && seedRegistered == registered && seedRevision == revision) return;
        seeded = true; seedRegistered = registered; seedRevision = revision;
        this.registered = registered; this.revision = revision; available = true; error = ""; render();
    }

    public void refresh() {
        if (!current() || pending) return;
        if (read != null) read.close();
        long token = ++generation; pending = true; error = ""; render();
        try {
            read = gateway.read(dimension, entity);
            read.result().whenComplete((reply, failed) -> queue.accept(() -> {
                if (!current() || generation != token) return;
                read = null; pending = false;
                if (failed != null || reply == null) error = tr("failed");
                else if (!reply.success()) error = failureMessage(reply.message());
                else { available = true; registered = reply.registered(); revision = reply.revision(); }
                if (!error.isEmpty()) available = false;
                render();
            }));
        } catch (RuntimeException failed) { pending = false; available = false; error = tr("failed"); render(); }
    }

    private void write(boolean value) {
        if (!current() || !available || pending || !editable.getAsBoolean()) { render(); return; }
        pending = true; error = ""; long token = ++generation; render();
        try {
            var request = gateway.setRegistered(dimension, entity, value, revision);
            request.result().whenComplete((reply, failed) -> queue.accept(() -> {
                if (!current() || generation != token) return;
                pending = false;
                if (failed != null || reply == null) error = tr("failed");
                else if (reply.success()) {
                    registered = reply.registered(); revision = reply.revision(); available = true;
                } else error = failureMessage(reply.message());
                if (!error.isEmpty()) available = false;
                render();
                if (failed == null && reply != null && reply.success()) applied.run();
            }));
        } catch (RuntimeException failed) { pending = false; available = false; error = tr("failed"); render(); }
    }

    public void refreshInputState() {
        checkbox.setEnabled(current() && available && !pending && editable.getAsBoolean());
        retry.setEnabled(current() && !pending && editable.getAsBoolean());
    }
    public void dispose() {
        if (disposed) return;
        disposed = true; generation++;
        if (read != null) { read.close(); read = null; }
        // Submitted mutations belong to the connection and must drain after presentation closes.
        refreshInputState();
    }
    private boolean current() {
        return !disposed && Objects.equals(scope, UiStateScopes.current())
                && (gateway != LIVE || connectionGeneration == ClientEntityIndex.INSTANCE.generation());
    }
    private void render() {
        checkbox.setChecked(registered);
        String caption = !error.isEmpty() ? error : pending ? tr("pending") : !available ? tr("unavailable")
                : tr(registered ? "registered" : "unregistered");
        status.setText(caption); status.setTextColor(error.isEmpty() ? theme.text().muted() : theme.status().error());
        retry.setVisibility(error.isEmpty() ? GONE : VISIBLE);
        setTooltipText(caption); checkbox.setTooltipText(caption); refreshInputState();
    }
    private static String message(String key) { return Component.translatable(key).getString(); }
    private static String failureMessage(String key) { return message("geometry_node.entity_registration.error." + key); }
    private static String tr(String key) { return message("geometry_node.entity_registration." + key); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
