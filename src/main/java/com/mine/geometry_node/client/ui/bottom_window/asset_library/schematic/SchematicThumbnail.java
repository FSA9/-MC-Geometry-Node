package com.mine.geometry_node.client.ui.bottom_window.asset_library.schematic;

import java.util.List;

public record SchematicThumbnail(
        int width,
        int height,
        int length,
        int gridWidth,
        int gridLength,
        List<Column> columns,
        boolean incomplete,
        String message
) {
    public SchematicThumbnail {
        columns = columns == null ? List.of() : List.copyOf(columns);
        message = message == null ? "" : message;
    }

    public boolean hasPreview() {
        return !columns.isEmpty() && gridWidth > 0 && gridLength > 0;
    }

    public static SchematicThumbnail error(String message) {
        return new SchematicThumbnail(0, 0, 0, 0, 0, List.of(), false, message);
    }

    public record Column(int x, int z, int y, int color, String state) {
        public Column {
            state = state == null ? "" : state;
        }
    }
}
