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
import com.mine.geometry_node.client.ui.components.composite.table.UiTableColumns;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
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
    private boolean alternate;

    static final int DISCLOSURE_WIDTH = 24;

    DataLibraryEntryCard(DataLibraryDragSurface surface, UiTableColumns columns,
                         String title, String typeName, PortType type,
                         boolean selected, Runnable toggleExpanded, Consumer<Boolean> selectionChanged) {
        this.surface = surface;
        Context context = surface.getContext();
        surface.setOrientation(LinearLayout.VERTICAL);
        surface.setPadding(0, 0, 0, 0);
        surface.setResultRow();
        UiTableRow header = new UiTableRow(context, columns);
        header.setMinimumHeight(px(UiTableStyle.ROW_HEIGHT));
        rowNumber = UiTableStyle.rowNumberCell(context, 1);
        header.addView(rowNumber, new ViewGroup.LayoutParams(-1, -1));
        check = new UiCheckBox(context);
        check.setContentDescription(tr("geometry_node.data_library.select_entry"));
        check.setOnCheckedChangeListener((button, checked) -> selectionChanged.accept(checked));
        header.addView(UiTableStyle.selectionCell(context, check), new ViewGroup.LayoutParams(-1, -1));
        disclosure = new SvgIconView(context, SvgIconView.Icon.forExpandedState(false), UiThemes.EDITOR.text().secondary());
        UiIconButton toggle = UiIconButton.createThemed(context, UiThemes.EDITOR,
                disclosure, UiIconButton.Preset.INLINE);
        toggle.setContentDescription(tr("geometry_node.data_library.expand_entry"));
        toggle.setOnClickListener(view -> toggleExpanded.run());
        header.addView(toggle, new ViewGroup.LayoutParams(-1, px(24)));
        name = UiTableStyle.cell(context, title, UiThemes.EDITOR.text().normal());
        header.addView(name, new ViewGroup.LayoutParams(-1, -1));
        header.addView(UiTableStyle.cell(context, typeName, UiThemes.EDITOR.text().normal()),
                new ViewGroup.LayoutParams(-1, -1));
        registryType = UiTableStyle.cell(context, "", UiThemes.EDITOR.text().normal());
        header.addView(registryType, new ViewGroup.LayoutParams(-1, -1));
        boolean entity = type == PortType.ENTITY || type == PortType.ENTITY_TEMPLATE;
        boolean item = type == PortType.ITEM_STACK;
        entityPreview = entity ? new EntityPreviewCell(context) : null;
        if (entityPreview != null) entityPreview.setVisibilityRow(header);
        itemPreview = item ? new ItemPreviewCell(context) : null;
        if (itemPreview != null) itemPreview.setVisibilityRow(header);
        value = entity || item ? null : UiTableStyle.cell(context, "", UiThemes.EDITOR.text().secondary());
        header.addView(entity ? entityPreview : item ? itemPreview : value, new ViewGroup.LayoutParams(-1, -1));
        tags = new UiTagStrip(context);
        header.addView(tags, new ViewGroup.LayoutParams(-1, -1));
        data = new UiDataSummaryCell(context);
        header.addView(data, new ViewGroup.LayoutParams(-1, -1));
        surface.addView(header, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.selected = selected;
        check.setChecked(selected);
        applySelectionBackground();
    }

    DataLibraryDragSurface surface() { return surface; }
    TextView nameView() { return name; }
    void setRowNumber(int number) { rowNumber.setText(Integer.toString(number)); }
    boolean isExpanded() { return expanded; }
    void setManageDataAction(Runnable action) { manageData = action; }

    void setAlternate(boolean alternate) {
        this.alternate = alternate;
        applySelectionBackground();
    }

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
            View divider = new View(surface.getContext());
            divider.setBackground(background(UiTableStyle.dividerColor(UiThemes.EDITOR)));
            surface.addView(divider, new LinearLayout.LayoutParams(-1, px(1)));
            surface.addView(Objects.requireNonNull(editor),
                    new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        expanded = value;
        disclosure.setIcon(SvgIconView.Icon.forExpandedState(value));
        ((UiIconButton) disclosure.getParent()).setContentDescription(tr(value
                ? "geometry_node.data_library.collapse_entry" : "geometry_node.data_library.expand_entry"));
    }

    private void applySelectionBackground() {
        surface.setResultRowPalette(UiTableStyle.rowPalette(selected, UiTableStyle.rowColor(alternate)));
    }

    private static ShapeDrawable background(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }
    private static String tr(String key) { return Component.translatable(key).getString(); }
}
