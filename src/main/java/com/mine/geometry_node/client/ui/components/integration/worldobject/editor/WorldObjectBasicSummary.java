package com.mine.geometry_node.client.ui.components.integration.worldobject.editor;

import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.layout.UiPropertyRow;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Read-only DTO properties for providers without an editable scene form or a current-dimension snapshot. */
final class WorldObjectBasicSummary extends UiScrollView {
    WorldObjectBasicSummary(Context context, WorldObjectEntry entry) {
        super(context, UiThemes.EDITOR.scroll());
        LinearLayout rows = new LinearLayout(context); rows.setOrientation(LinearLayout.VERTICAL);
        int padding = UIUtils.dp2pxInt(16); rows.setPadding(padding, padding, padding, padding);
        addView(rows, new LinearLayout.LayoutParams(-1, -2));
        add(rows, "geometry_node.database.id", entry.key());
        add(rows, "geometry_node.database.type", entry.type());
        add(rows, "geometry_node.database.dimension", entry.dimension());
        add(rows, "geometry_node.database.spatial_position", vector(entry.position().x, entry.position().y, entry.position().z));
        add(rows, "geometry_node.world_object_editor.bounds_min", vector(entry.bounds().minX, entry.bounds().minY, entry.bounds().minZ));
        add(rows, "geometry_node.world_object_editor.bounds_max", vector(entry.bounds().maxX, entry.bounds().maxY, entry.bounds().maxZ));
        add(rows, "geometry_node.database.source", entry.source().isBlank() ? "—" : entry.source());
        add(rows, "geometry_node.database.lifetime", Component.translatableWithFallback("geometry_node.database.lifetime." + entry.lifetime(), entry.lifetime()).getString());
    }
    private void add(LinearLayout rows, String key, String value) {
        TextView label = UIUtils.createLockedTextView(getContext(), Component.translatable(key).getString(), 12, UiThemes.EDITOR.text().muted());
        label.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = UIUtils.createLockedTextView(getContext(), value, 12, UiThemes.EDITOR.text().normal());
        text.setMaxLines(4); text.setTooltipText(value); text.setGravity(Gravity.CENTER_VERTICAL);
        UiPropertyRow row = new UiPropertyRow(getContext(), label, text, null,
                new UiPropertyRow.Layout(100, 32, 10, 0, 0), new LinearLayout.LayoutParams(0, -2, 1));
        rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    static String reason(WorldObjectEntry entry, String currentDimension, boolean scopeCurrent) {
        if (!scopeCurrent) return "geometry_node.world_object_editor.unavailable";
        if (!currentDimension.isEmpty() && !entry.dimension().equals(currentDimension))
            return "geometry_node.world_object_editor.other_dimension";
        return entry.sceneObjectId() == null ? "geometry_node.world_object_editor.read_only"
                : "geometry_node.world_object_editor.snapshot_unavailable";
    }
    private static String vector(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", x, y, z);
    }
}
