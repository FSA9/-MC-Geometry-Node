package com.mine.geometry_node.client.ui.editor.asset.schematic;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewLimits;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/** Creates the canonical transparent, tightly cropped PNG attached to a schematic upload. */
public final class SchematicUploadPreviewGenerator {
    private static final int CROP_PADDING = 3;

    private SchematicUploadPreviewGenerator() {
    }

    public static SchematicThumbnail read(Path source) throws IOException, InterruptedException {
        return SchematicThumbnailReader.read(source.toFile());
    }

    public static SchematicUploadPreview render(SchematicThumbnail thumbnail) throws IOException {
        if (thumbnail == null || !thumbnail.hasPreview()) {
            throw new IOException(thumbnail == null ? "Schematic nativepreview is unavailable" : thumbnail.message());
        }
        int width = AssetPreviewLimits.TARGET_WIDTH;
        int height = AssetPreviewLimits.TARGET_HEIGHT;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            SchematicThumbnailRenderer.render(thumbnail, width, height, 8.0f,
                    SchematicThumbnailMaterialResolver::resolve,
                    (x0, y0, x1, y1, x2, y2, x3, y3, color) -> {
                        graphics.setColor(new Color(color, true));
                        graphics.fill(new Polygon(
                                new int[]{Math.round(x0), Math.round(x1), Math.round(x2), Math.round(x3)},
                                new int[]{Math.round(y0), Math.round(y1), Math.round(y2), Math.round(y3)}, 4));
                    });
        } finally {
            graphics.dispose();
        }
        BufferedImage cropped = cropTransparentBounds(image);
        byte[] encoded = encodePng(cropped);
        if (!AssetPreviewLimits.validEncodedSize(encoded.length)) {
            throw new IOException("Encoded schematic nativepreview exceeds the protocol limit");
        }
        return new SchematicUploadPreview(AssetPreviewFormat.PNG,
                cropped.getWidth(), cropped.getHeight(), encoded);
    }

    private static BufferedImage cropTransparentBounds(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY) return source;
        minX = Math.max(0, minX - CROP_PADDING);
        minY = Math.max(0, minY - CROP_PADDING);
        maxX = Math.min(source.getWidth() - 1, maxX + CROP_PADDING);
        maxY = Math.min(source.getHeight() - 1, maxY + CROP_PADDING);
        BufferedImage cropped = new BufferedImage(maxX - minX + 1, maxY - minY + 1,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.drawImage(source, -minX, -minY, null);
        } finally {
            graphics.dispose();
        }
        return cropped;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", bytes)) throw new IOException("PNG encoder is unavailable");
            return bytes.toByteArray();
        }
    }
}
