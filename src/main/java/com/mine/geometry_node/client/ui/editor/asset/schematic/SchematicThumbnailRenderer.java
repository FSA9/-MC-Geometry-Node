package com.mine.geometry_node.client.ui.editor.asset.schematic;

/** Shared fixed-camera projection used by local Views and uploaded schematic previews. */
final class SchematicThumbnailRenderer {
    private SchematicThumbnailRenderer() {
    }

    static void render(SchematicThumbnail thumbnail, float width, float height, float padding,
                       MaterialResolver materialResolver, QuadSink sink) {
        int gridWidth = Math.max(1, thumbnail.gridWidth());
        int gridLength = Math.max(1, thumbnail.gridLength());
        float availableW = Math.max(1.0f, width - padding * 2.0f);
        float availableH = Math.max(1.0f, height - padding * 2.0f);
        float diagonal = Math.max(2.0f, gridWidth + gridLength);
        float maxLift = availableH * 0.30f;
        float tileW = Math.max(1.4f, Math.min(availableW * 1.85f / diagonal,
                Math.max(1.0f, availableH - maxLift) * 3.0f / diagonal));
        float tileH = tileW * 0.5f;
        float sideDepthBase = tileH * 0.88f;
        float maxSideDepth = sideDepthBase + maxLift * 0.46f;
        float isoHeight = (gridWidth + gridLength) * tileH * 0.5f + maxSideDepth;
        float originX = width * 0.5f + (gridLength - gridWidth) * tileW * 0.25f;
        float originY = Math.max(padding + maxLift, (height - isoHeight) * 0.5f + maxLift) + tileH;
        int maxY = Math.max(1, thumbnail.height() - 1);

        for (SchematicThumbnail.Column column : thumbnail.columns()) {
            float cx = originX + (column.x() - column.z()) * tileW * 0.5f;
            float baseY = originY + (column.x() + column.z()) * tileH * 0.5f;
            float heightRatio = Math.clamp(column.y() / (float) maxY, 0.0f, 1.0f);
            float lift = maxLift * heightRatio;
            drawColumn(sink, cx, baseY - lift, tileW * 0.54f, tileH * 0.60f,
                    sideDepthBase + lift * 0.46f,
                    materialResolver.resolve(column.state(), column.color()), heightRatio);
        }
    }

    private static void drawColumn(QuadSink sink, float cx, float cy, float halfW, float halfH,
                                   float sideDepth,
                                   SchematicThumbnailMaterialResolver.MaterialColors colors,
                                   float heightRatio) {
        float topY = cy - halfH;
        float rightX = cx + halfW;
        float bottomY = cy + halfH;
        float leftX = cx - halfW;

        sink.draw(leftX, cy, cx, bottomY, cx, bottomY + sideDepth, leftX, cy + sideDepth,
                shade(colors.left(), 0.58f + heightRatio * 0.08f));
        sink.draw(cx, bottomY, rightX, cy, rightX, cy + sideDepth, cx, bottomY + sideDepth,
                shade(colors.right(), 0.70f + heightRatio * 0.08f));
        sink.draw(cx, topY, rightX, cy, cx, bottomY, leftX, cy,
                shade(colors.top(), 0.92f + heightRatio * 0.12f));
    }

    private static int shade(int color, float factor) {
        int a = color >>> 24;
        int r = Math.clamp((int) (((color >>> 16) & 0xFF) * factor), 0, 255);
        int g = Math.clamp((int) (((color >>> 8) & 0xFF) * factor), 0, 255);
        int b = Math.clamp((int) ((color & 0xFF) * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @FunctionalInterface
    interface MaterialResolver {
        SchematicThumbnailMaterialResolver.MaterialColors resolve(String state, int fallbackColor);
    }

    @FunctionalInterface
    interface QuadSink {
        void draw(float x0, float y0, float x1, float y1, float x2, float y2,
                  float x3, float y3, int color);
    }
}
