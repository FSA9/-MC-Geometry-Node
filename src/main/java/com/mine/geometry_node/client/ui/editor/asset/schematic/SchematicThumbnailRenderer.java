package com.mine.geometry_node.client.ui.editor.asset.schematic;

import com.mine.geometry_node.core.engine.system.asset.preview.generator.schematic.SchematicThumbnail;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.schematic.SchematicThumbnailProjection;

/** Client adapter that supplies texture-derived material colors to the shared projection. */
final class SchematicThumbnailRenderer {
    private SchematicThumbnailRenderer() {
    }

    static void render(SchematicThumbnail thumbnail, float width, float height, float padding,
                       MaterialResolver materialResolver, QuadSink sink) {
        SchematicThumbnailProjection.render(thumbnail, width, height, padding,
                (state, fallback) -> {
                    SchematicThumbnailMaterialResolver.MaterialColors colors =
                            materialResolver.resolve(state, fallback);
                    return new SchematicThumbnailProjection.MaterialColors(
                            colors.top(), colors.left(), colors.right());
                }, sink::draw);
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
