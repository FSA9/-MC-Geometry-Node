package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewLimits;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import com.mine.geometry_node.core.engine.system.visual.image.ImageAssetFormats;
import net.minecraft.server.MinecraftServer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;

/** Dedicated-server-safe image decoder, scaler, and nativepreview publisher. */
public final class ServerImagePreviewGenerator {
    private final ServerAssetPreviewStore store;

    public ServerImagePreviewGenerator(ServerAssetPreviewStore store) {
        this.store = store;
    }

    public ServerAssetPreviewStore.StoredPreview generate(MinecraftServer server, Path source,
                                                          AssetPreviewRevision revision) throws IOException {
        BasicFileAttributes before = attributes(source);
        verifyRevision(before, revision);
        if (before.size() > AssetPreviewLimits.MAX_IMAGE_SOURCE_BYTES) {
            throw new PreviewUnavailableException("Image source exceeds nativepreview limit");
        }
        BufferedImage decoded = decode(source);
        BufferedImage scaled = scale(decoded);
        Path staging = RemoteAssetFileService.previewCacheRoot(server).resolve("staging")
                .resolve(revision.cacheKey() + ".png.tmp");
        try {
            Files.createDirectories(staging.getParent());
            if (!ImageIO.write(scaled, "png", staging.toFile())) {
                throw new IOException("PNG nativepreview encoder is unavailable");
            }
            BasicFileAttributes after = attributes(source);
            verifyRevision(after, revision);
            int encodedBytes = Math.toIntExact(Files.size(staging));
            if (!AssetPreviewLimits.validEncodedSize(encodedBytes)) {
                throw new PreviewUnavailableException("Encoded image nativepreview exceeds protocol limit");
            }
            AssetPreviewDescriptor descriptor = new AssetPreviewDescriptor(revision, AssetPreviewFormat.PNG,
                    scaled.getWidth(), scaled.getHeight(), encodedBytes, AssetTransferHashing.sha256(staging));
            return store.publish(server, staging, descriptor);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static BufferedImage decode(Path source) throws IOException {
        ImageAssetFormats.Format format = ImageAssetFormats.fromPath(source.toString());
        if (format == null) throw new PreviewUnavailableException("Unsupported image type");
        if (format == ImageAssetFormats.Format.TGA) return TgaImageDecoder.read(source);
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new PreviewUnavailableException("Cannot inspect image source");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new PreviewUnavailableException("No decoder for image source");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!AssetPreviewLimits.validImageSourceDimensions(width, height)) {
                    throw new PreviewUnavailableException("Image dimensions exceed nativepreview limit");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new PreviewUnavailableException("Image decoder returned no pixels");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scale(BufferedImage source) {
        int canvasWidth = AssetPreviewLimits.TARGET_WIDTH;
        int canvasHeight = AssetPreviewLimits.TARGET_HEIGHT;
        double scale = Math.min(canvasWidth / (double) source.getWidth(), canvasHeight / (double) source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (canvasWidth - width) / 2;
        int y = (canvasHeight - height) / 2;
        BufferedImage result = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static BasicFileAttributes attributes(Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
            throw new PreviewUnavailableException("Image source is unavailable");
        }
        return Files.readAttributes(source, BasicFileAttributes.class);
    }

    private static void verifyRevision(BasicFileAttributes attributes, AssetPreviewRevision revision)
            throws SourceChangedException {
        if (attributes.size() != revision.sourceSize()
                || attributes.lastModifiedTime().toMillis() != revision.sourceLastModified()) {
            throw new SourceChangedException();
        }
    }

    public static final class PreviewUnavailableException extends IOException {
        public PreviewUnavailableException(String message) {
            super(message);
        }
    }

    public static final class SourceChangedException extends IOException {
        public SourceChangedException() {
            super("Image source revision changed while generating nativepreview");
        }
    }
}
