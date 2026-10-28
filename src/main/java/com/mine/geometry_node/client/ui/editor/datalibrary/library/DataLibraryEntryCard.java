package com.mine.geometry_node.client.ui.editor.datalibrary.library;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.feature.datalibrary.DataLibraryRepository;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagStrip;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataSummaryCell;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityPreviewCell;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ItemPreviewCell;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.client.ui.components.composite.table.UiTable;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Compact result row; the existing drag surface also hosts the full inline value editor. */
final class DataLibraryEntryCard {
    private final DataLibraryDragSurface surface;
    private final UiTable table;
    private final UiTableRow cells;
    private final TextView name;
    private final TextView value;
    private final TextView registryType;
    private final EntityPreviewCell entityPreview;
    private final ItemPreviewCell itemPreview;
    private final TextView rowNumber;
    private final UiTagStrip tags;
    private final UiDataSummaryCell data;
    private Runnable manageData;
    private final UiCheckBox check;
    private final SvgIconView disclosure;
    private boolean selected;
    private boolean expanded;
    private int rowIndex;

    static final int DISCLOSURE_WIDTH = 24;

    DataLibraryEntryCard(DataLibraryDragSurface surface, UiTable table,
                         String title, String typeName, PortType type,
                         boolean selected, Runnable toggleExpanded, Consumer<Boolean> selectionChanged) {
        this.surface = surface;
        this.table = table;
        Context context = surface.getContext();
        surface.setOrientation(LinearLayout.VERTICAL);
        surface.setPadding(0, 0, 0, 0);
        rowNumber = UiTableStyle.rowNumberCell(context, 1);
        check = new UiCheckBox(context);
        check.setContentDescription(tr("geometry_node.data_library.select_entry"));
        check.setOnCheckedChangeListener((button, checked) -> selectionChanged.accept(checked));
        disclosure = new SvgIconView(context, SvgIconView.Icon.forExpandedState(false), UiThemes.EDITOR.text().secondary());
        UiIconButton toggle = UiIconButton.createThemed(context, UiThemes.EDITOR,
                disclosure, UiIconButton.Preset.INLINE);
        toggle.setContentDescription(tr("geometry_node.data_library.expand_entry"));
        toggle.setOnClickListener(view -> toggleExpanded.run());
        toggle.setLayoutParams(new ViewGroup.LayoutParams(-1, px(24)));
        name = UiTableStyle.cell(context, title, UiThemes.EDITOR.text().normal());
        registryType = UiTableStyle.cell(context, "", UiThemes.EDITOR.text().normal());
        boolean entity = type == PortType.ENTITY || type == PortType.ENTITY_TEMPLATE;
        boolean item = type == PortType.ITEM_STACK;
        entityPreview = entity ? new EntityPreviewCell(context) : null;
        itemPreview = item ? new ItemPreviewCell(context) : null;
        value = entity || item ? null : UiTableStyle.cell(context, "", UiThemes.EDITOR.text().secondary());
        tags = new UiTagStrip(context);
        data = new UiDataSummaryCell(context);
        cells = table.createRow(id -> switch (id) {
            case "row_number" -> rowNumber;
            case "selection" -> UiTableStyle.selectionCell(context, check);
            case "disclosure" -> toggle;
            case "name" -> name;
            case "type" -> UiTableStyle.cell(context, typeName, UiThemes.EDITOR.text().normal());
            case "registry_type" -> registryType;
            case "value" -> entity ? entityPreview : item ? itemPreview : value;
            case "tags" -> tags;
            case "data" -> data;
            default -> throw new IllegalArgumentException("Unknown data-library column: " + id);
        });
        cells.setSurfaceOwner(surface);
        if (entityPreview != null) entityPreview.setVisibilityRow(cells);
        if (itemPreview != null) itemPreview.setVisibilityRow(cells);
        surface.addView(cells, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.selected = selected;
        check.setChecked(selected);
        applySelectionBackground();
    }

    DataLibraryDragSurface surface() { return surface; }
    TextView nameView() { return name; }
    void setRowIndex(int index) {
        rowIndex = index;
        rowNumber.setText(Integer.toString(index + 1));
        applySelectionBackground();
    }
    boolean isExpanded() { return expanded; }
    void setManageDataAction(Runnable action) { manageData = action; }

    void setName(String value) {
        if (!Objects.equals(name.getText().toString(), value)) name.setText(value);
    }

    void setSummary(DataLibraryRepository.Entry entry, String summary) {
        setSummary(entry, summary, "");
    }

    /** resolvedEntityType belongs to the caller's current metadata snapshot, independently of preview visibility. */
    void setSummary(DataLibraryRepository.Entry entry, String summary, String resolvedEntityType) {
        String registryId = DataLibraryValuePresentation.registryType(entry, resolvedEntityType);
        if (!Objects.equals(registryType.getText().toString(), registryId)) registryType.setText(registryId);
        if (entityPreview != null) {
            if (entry.type() == PortType.ENTITY_TEMPLATE) {
                entityPreview.setTemplate(EntityTemplateValue.from(entry.value()));
            } else {
                entityPreview.setReference(entry.value() instanceof UUID id ? id : null, "");
            }
        } else if (itemPreview != null) {
            itemPreview.setStack(DataLibraryValuePresentation.previewStack(entry));
        } else {
            String text = DataLibraryValuePresentation.tableValueText(entry, summary);
            if (!Objects.equals(value.getText().toString(), text)) value.setText(text);
            value.setTooltipText(text);
        }
        tags.setTags(entry.tags());
        var values = entry.data();
        String summaryText = values.entrySet().stream().limit(2)
                .map(field -> field.getKey() + ": " + UiDataSummaryCell.summaryValue(field.getValue()))
                .collect(java.util.stream.Collectors.joining(" · "));
        data.setSummary(values.size(), summaryText, () -> { if (manageData != null) manageData.run(); });
    }

    void setSelected(boolean value) {
        check.setChecked(value);
        if (selected == value) return;
        selected = value;
        applySelectionBackground();
    }

    void setExpandedContent(boolean value, View editor) {
        while (surface.getChildCount() > 1) surface.removeViewAt(1);
        if (value) {
            surface.addView(Objects.requireNonNull(editor),
                    new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        expanded = value;
        disclosure.setIcon(SvgIconView.Icon.forExpandedState(value));
        ((UiIconButton) disclosure.getParent()).setContentDescription(tr(value
                ? "geometry_node.data_library.collapse_entry" : "geometry_node.data_library.expand_entry"));
    }

    private void applySelectionBackground() {
        table.styleRow(cells, rowIndex, selected);
    }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }
    private static String tr(String key) { return Component.translatable(key).getString(); }
}
