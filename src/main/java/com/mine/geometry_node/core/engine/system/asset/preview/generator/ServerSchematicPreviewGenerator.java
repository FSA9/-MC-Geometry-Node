package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewLimits;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.schematic.SchematicThumbnail;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.schematic.SchematicThumbnailProjection;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.schematic.SchematicThumbnailReader;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import net.minecraft.server.MinecraftServer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Dedicated-server-safe schematic sampler, renderer, and preview publisher. */
public final class ServerSchematicPreviewGenerator implements ServerAssetPreviewGenerator {
    private static final int CROP_PADDING = 3;

    private final ServerAssetPreviewStore store;

    public ServerSchematicPreviewGenerator(ServerAssetPreviewStore store) {
        this.store = store;
    }

    @Override
    public ServerAssetPreviewStore.StoredPreview generate(MinecraftServer server, Path source,
                                                          AssetPreviewRevision revision) throws IOException {
        BasicFileAttributes before = attributes(source);
        verifyRevision(before, revision);
        if (before.size() > AssetPreviewLimits.MAX_SCHEMATIC_SOURCE_BYTES) {
            throw new PreviewUnavailableException("Schematic source exceeds preview limit");
        }

        SchematicThumbnail thumbnail;
        try {
            thumbnail = SchematicThumbnailReader.read(source);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Schematic preview generation interrupted", exception);
        }
        if (!thumbnail.hasPreview()) {
            throw new PreviewUnavailableException(thumbnail.message().isBlank()
                    ? "Schematic preview is unavailable" : thumbnail.message());
        }

        BufferedImage rendered = render(thumbnail);
        BufferedImage cropped = cropTransparentBounds(rendered);
        Path staging = RemoteAssetFileService.previewCacheRoot(server).resolve("staging")
                .resolve(revision.cacheKey() + ".png.tmp");
        try {
            Files.createDirectories(staging.getParent());
            if (!ImageIO.write(cropped, "png", staging.toFile())) {
                throw new IOException("PNG preview encoder is unavailable");
            }
            verifyRevision(attributes(source), revision);
            int encodedBytes = Math.toIntExact(Files.size(staging));
            if (!AssetPreviewLimits.validEncodedSize(encodedBytes)) {
                throw new PreviewUnavailableException("Encoded schematic preview exceeds protocol limit");
            }
            AssetPreviewDescriptor descriptor = new AssetPreviewDescriptor(revision, AssetPreviewFormat.PNG,
                    cropped.getWidth(), cropped.getHeight(), encodedBytes, AssetTransferHashing.sha256(staging));
            return store.publish(server, staging, descriptor);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static BufferedImage render(SchematicThumbnail thumbnail) {
        int width = AssetPreviewLimits.TARGET_WIDTH;
        int height = AssetPreviewLimits.TARGET_HEIGHT;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            SchematicThumbnailProjection.render(thumbnail, width, height, 8.0f,
                    (state, fallback) -> SchematicThumbnailProjection.MaterialColors.uniform(fallback),
                    (x0, y0, x1, y1, x2, y2, x3, y3, color) -> {
                        graphics.setColor(new Color(color, true));
                        graphics.fill(new Polygon(
                                new int[]{Math.round(x0), Math.round(x1), Math.round(x2), Math.round(x3)},
                                new int[]{Math.round(y0), Math.round(y1), Math.round(y2), Math.round(y3)}, 4));
                    });
        } finally {
            graphics.dispose();
        }
        return image;
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

    private static BasicFileAttributes attributes(Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
            throw new PreviewUnavailableException("Schematic source is unavailable");
        }
        return Files.readAttributes(source, BasicFileAttributes.class);
    }

    private static void verifyRevision(BasicFileAttributes attributes, AssetPreviewRevision revision)
            throws PreviewSourceChangedException {
        if (attributes.size() != revision.sourceSize()
                || attributes.lastModifiedTime().toMillis() != revision.sourceLastModified()) {
            throw new PreviewSourceChangedException();
        }
    }
}
