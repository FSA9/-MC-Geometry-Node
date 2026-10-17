package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.components.input.UiSearchableMenu;
import com.mine.geometry_node.client.ui.components.input.UiSelectButton;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.InlineActionButton;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.port.PortOptionResolver;
import com.mine.geometry_node.core.node.definition.port.PortRow;
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
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        PortOptionResolver.Resolution resolution = PortOptionResolver.resolve(row,
                minecraft.level == null ? null : minecraft.level.registryAccess(),
                key -> net.minecraft.network.chat.Component.translatable(key).getString());
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

        UiSelectButton dropdownBtn = new UiSelectButton(context, displayVal, UIConstants.Node.TEXT_SIZE_LABEL,
                () -> ConfigManager.INSTANCE.getConfig().node.cornerRadius);

        dropdownBtn.setOnClickListener(v -> {
            icyllis.modernui.view.ViewParent parent = v.getParent();

            while (parent != null && !(parent instanceof InteractionContext)) {
                parent = parent.getParent();
            }

            if (parent instanceof InteractionContext interactionContext && !portId.isEmpty()) {
                UiSearchableMenu menu = new UiSearchableMenu(context, title, resolvedOptions, optionLabels, selectedVal -> {
                    dropdownBtn.setValue(optionLabels.getOrDefault(selectedVal, selectedVal));
                    UIHintValueBinder.commit(editorContext, nodeData, portId, selectedVal);
                });

                if (interactionContext instanceof ViewGroup host) {
                    menu.showAt(v, host);
                }
            }
        });
        return dropdownBtn;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        float inputBoxHeight = UIHintUtils.getStandardInputHeight();
        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - inputBoxHeight) / 2.0f;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        int leftMarginPx = UIUtils.dp2pxInt(startX);
        int widthPx = hasDynamicRemoveButton(row)
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
        lp.topMargin = UIUtils.dp2pxInt(currentY + UIConstants.Node.ROW_HEIGHT + verticalMargin);

        view.setLayoutParams(lp);
    }

    private static boolean hasDynamicRemoveButton(PortRow row) {
        return row != null
                && row.hintParams() != null
                && Boolean.TRUE.equals(row.hintParams().get(PortMetaKeys.IS_DYNAMIC))
                && row.hintParams().get(PortMetaKeys.DYNAMIC_INDEX) instanceof Integer;
    }

    private static String selectTitle(NodeData nodeData, PortRow row) {
        if (row == null || row.leftPort() == null || row.leftPort().displayName() == null) {
            return "";
        }
        String defaultName = row.leftPort().displayName().getString();
        return nodeData != null ? nodeData.getEffectivePortName("inputs", row.leftPort().id(), defaultName) : defaultName;
    }

}
