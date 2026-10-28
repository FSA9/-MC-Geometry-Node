package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.editor.graph.node.NodeUiMetrics;
import com.mine.geometry_node.client.integration.graph.GraphPortOptions;

import com.mine.geometry_node.client.feature.graph.edit.EditorContext;
import com.mine.geometry_node.client.ui.components.composite.menu.UiSearchableMenu;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.InlineActionButton;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.port.PortOptionResolver;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.client.feature.faction.ClientFactionRepository;
import com.mine.geometry_node.client.ui.components.integration.faction.FactionCatalogSubscription;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import java.util.List;
import java.util.Map;

public class SelectHintRenderer implements UIHintRenderer {

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 1.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        // 1. 获取选项列表
        PortOptionResolver.Resolution resolution = resolveOptions(row);
        List<String> resolvedOptions = resolution.options().stream().map(PortOptionResolver.Option::id).toList();
        Map<String, String> optionLabels = resolution.options().stream().collect(java.util.stream.Collectors.toMap(
                PortOptionResolver.Option::id, PortOptionResolver.Option::label, (first, ignored) -> first,
                java.util.LinkedHashMap::new));

        // 2. 统一使用 leftPort 的 ID 作为数据存储的 Key
        String portId = row.leftPort() != null ? row.leftPort().id() : "";
        Object val = null;
        if (row.leftPort() != null) {
            val = UIHintValueBinder.getValue(nodeData, row.leftPort());
        }

        String selectedValue = val != null ? val.toString() : (resolvedOptions.isEmpty() ? "" : resolvedOptions.get(0));
        String displayVal = optionLabels.getOrDefault(selectedValue, selectedValue);
        String title = selectTitle(nodeData, row);

        UiSelectButton dropdownBtn = new UiSelectButton(context, displayVal, NodeUiMetrics.TEXT_SIZE_LABEL,
                () -> ConfigManager.INSTANCE.getConfig().node.cornerRadius);
        boolean factionOptions = RegistryDataManager.FACTION_REGISTRY_ID.equals(resolution.registryId());
        LiveMenu liveMenu = new LiveMenu();

        if (factionOptions) {
            var subscription = new FactionCatalogSubscription(dropdownBtn, ClientFactionRepository.INSTANCE, () -> {
                PortOptionResolver.Resolution latest = resolveOptions(row);
                liveMenu.catalogChanged(latest);
                Object current = UIHintValueBinder.getValue(nodeData, row.leftPort());
                String id = current == null ? "" : current.toString();
                String label = latest.options().stream().filter(option -> option.id().equals(id))
                        .map(PortOptionResolver.Option::label).findFirst().orElse(id);
                dropdownBtn.setValue(label);
            });
            dropdownBtn.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { subscription.open(); }
                @Override public void onViewDetachedFromWindow(View view) { subscription.close(); liveMenu.close(); }
            });
        }

        dropdownBtn.setOnClickListener(v -> {
            // Catalogs can change while a graph stays open; never retain the initial option list.
            PortOptionResolver.Resolution currentResolution = resolveOptions(row);
            List<String> currentOptions = currentResolution.options().stream().map(PortOptionResolver.Option::id).toList();
            Map<String, String> currentLabels = currentResolution.options().stream().collect(java.util.stream.Collectors.toMap(
                    PortOptionResolver.Option::id, PortOptionResolver.Option::label, (first, ignored) -> first,
                    java.util.LinkedHashMap::new));
            icyllis.modernui.view.ViewParent parent = v.getParent();

            while (parent != null && !(parent instanceof InteractionContext)) {
                parent = parent.getParent();
            }

            if (parent instanceof InteractionContext interactionContext && !portId.isEmpty()) {
                java.util.function.Consumer<String> commit = selectedVal -> {
                    // A catalog reply can arrive before its UI notification closes an old menu.
                    boolean dynamic = currentResolution.source() == PortOptionResolver.Source.DYNAMIC;
                    PortOptionResolver.Resolution latest = dynamic ? resolveOptions(row) : currentResolution;
                    if (dynamic && !LiveMenu.canSelect(latest, selectedVal)) {
                        liveMenu.close();
                        return;
                    }
                    String label = latest.options().stream().filter(option -> option.id().equals(selectedVal))
                            .map(PortOptionResolver.Option::label).findFirst().orElse(selectedVal);
                    dropdownBtn.setValue(label);
                    UIHintValueBinder.commit(editorContext, nodeData, portId, selectedVal);
                };
                var presentation = SelectMenuPresentations.get(currentResolution.registryId());
                if (presentation != null && interactionContext instanceof FrameLayout host) {
                    Object current = UIHintValueBinder.getValue(nodeData, row.leftPort());
                    presentation.show(context, title, currentResolution,
                            current == null ? selectedValue : current.toString(), v, host, commit);
                } else if (interactionContext instanceof ViewGroup host) {
                    var menu = new UiSearchableMenu(context, title, currentOptions, currentLabels, commit);
                    if (factionOptions) liveMenu.open(currentResolution, menu::dismiss);
                    menu.showAt(v, host);
                }
            }
        });
        return dropdownBtn;
    }

    /** A transient menu's catalog snapshot becomes invalid when IDs, labels, or availability change. */
    static final class LiveMenu {
        private PortOptionResolver.Resolution snapshot;
        private Runnable dismiss;

        void open(PortOptionResolver.Resolution options, Runnable close) {
            close();
            snapshot = options;
            dismiss = close;
        }

        void catalogChanged(PortOptionResolver.Resolution options) {
            if (snapshot != null && !snapshot.equals(options)) close();
        }

        void close() {
            Runnable action = dismiss;
            dismiss = null;
            snapshot = null;
            if (action != null) action.run();
        }

        static boolean canSelect(PortOptionResolver.Resolution options, String id) {
            return options.available() && options.options().stream().anyMatch(option -> option.id().equals(id));
        }
    }

    private static PortOptionResolver.Resolution resolveOptions(PortRow row) {
        return GraphPortOptions.resolve(row);
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = NodeUiMetrics.LABEL_MARGIN_PORT;
        float endX = nodeWidth - NodeUiMetrics.LABEL_MARGIN_PORT;

        float inputBoxHeight = UIHintUtils.getStandardInputHeight();
        float verticalMargin = (NodeUiMetrics.ROW_HEIGHT - inputBoxHeight) / 2.0f;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        int leftMarginPx = UIUtils.dp2pxInt(startX);
        int widthPx = UIHintUtils.hasDynamicRemoveButton(row)
                ? InlineActionButton.rightColumnLeftPx(nodeWidth) - leftMarginPx
                : UIUtils.dp2pxInt(endX - startX);
        int heightPx = UIUtils.dp2pxInt(inputBoxHeight);

        if (lp == null) {
            lp = new FrameLayout.LayoutParams(widthPx, heightPx);
        } else {
            lp.width = widthPx;
            lp.height = heightPx;
        }

        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = leftMarginPx;
        lp.topMargin = UIUtils.dp2pxInt(currentY + NodeUiMetrics.ROW_HEIGHT + verticalMargin);

        view.setLayoutParams(lp);
    }

    private static String selectTitle(NodeData nodeData, PortRow row) {
        if (row == null || row.leftPort() == null || row.leftPort().displayName() == null) {
            return "";
        }
        String defaultName = row.leftPort().displayName().getString();
        return nodeData != null ? nodeData.getEffectivePortName("inputs", row.leftPort().id(), defaultName) : defaultName;
    }

}
