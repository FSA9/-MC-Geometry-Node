package com.mine.geometry_node.client.ui.editor.graph;

import com.mine.geometry_node.client.ui.persistence.PathUtils;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.granite.Recording;
import icyllis.arc3d.opengl.GLTexture;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.widget.Toast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL33C;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ViewportImageExporter {
    private static final float PIXELS_PER_DP = 2.0f;
    private static final float PADDING_DP = 32.0f;
    private static final int MAX_SIDE_PX = 8192;
    private static final long MAX_PIXEL_COUNT = 64L * 1024L * 1024L;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-ViewportImageExporter");
        thread.setDaemon(true);
        return thread;
    });

    private ViewportImageExporter() {}

    static void export(Viewport viewport, GraphSession session) {
        if (viewport == null || session == null || !viewport.isReady()) return;

        RectF bounds = new RectF();
        if (!viewport.collectExportBounds(bounds)) {
            showToast(viewport, "geometry_node.viewport.export.empty", Toast.LENGTH_SHORT);
            return;
        }
        bounds.inset(-PADDING_DP, -PADDING_DP);
        if (!bounds.isFinite() || bounds.isEmpty()) {
            showToast(viewport, "geometry_node.viewport.export.invalid_bounds", Toast.LENGTH_LONG);
            return;
        }

        int width = Math.max(1, (int) Math.ceil(bounds.width() * PIXELS_PER_DP));
        int height = Math.max(1, (int) Math.ceil(bounds.height() * PIXELS_PER_DP));
        if (width > MAX_SIDE_PX || height > MAX_SIDE_PX || (long) width * height > MAX_PIXEL_COUNT) {
            showToast(viewport, "geometry_node.viewport.export.too_large", Toast.LENGTH_LONG);
            return;
        }

        GraniteSurface surface;
        try {
            surface = GraniteSurface.makeRenderTarget(
                    Core.requireUiRecordingContext(),
                    ImageInfo.make(width, height, ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB),
                    false,
                    Engine.SurfaceOrigin.kUpperLeft,
                    "GeometryNodeScreenshot"
            );
        } catch (Throwable error) {
            showToast(viewport, "geometry_node.viewport.export.render_target_failed", Toast.LENGTH_LONG, messageOf(error));
            error.printStackTrace();
            return;
        }
        if (surface == null) {
            showToast(viewport, "geometry_node.viewport.export.render_target_unavailable", Toast.LENGTH_LONG);
            return;
        }

        float uiDensity = UIUtils.dp2px(1.0f);
        if (!(uiDensity > 0.0f)) {
            surface.unref();
            showToast(viewport, "geometry_node.viewport.export.density_unavailable", Toast.LENGTH_LONG);
            return;
        }

        ViewportCamera exportCamera = new ViewportCamera(null);
        float cameraScale = PIXELS_PER_DP / uiDensity;
        exportCamera.setScale(cameraScale);
        exportCamera.setPosition(
                -UIUtils.dp2px(bounds.left) * cameraScale,
                -UIUtils.dp2px(bounds.top) * cameraScale
        );

        Canvas canvas = new ArcCanvas(surface.getCanvas());
        try {
            viewport.drawForExport(canvas, exportCamera, width, height);
            canvas.restoreToCount(1);
        } catch (Throwable error) {
            surface.unref();
            showToast(viewport, "geometry_node.viewport.export.draw_failed", Toast.LENGTH_LONG, messageOf(error));
            error.printStackTrace();
            return;
        }

        Recording recording;
        try {
            recording = Core.requireUiRecordingContext().snap();
        } catch (Throwable error) {
            surface.unref();
            showToast(viewport, "geometry_node.viewport.export.recording_failed", Toast.LENGTH_LONG, messageOf(error));
            error.printStackTrace();
            return;
        }
        if (recording == null) {
            surface.unref();
            showToast(viewport, "geometry_node.viewport.export.recording_unavailable", Toast.LENGTH_LONG);
            return;
        }

        Path output = createOutputPath(session);
        try {
            Minecraft.getInstance().execute(() -> renderAndRead(viewport, surface, recording, width, height, output));
        } catch (Throwable error) {
            recording.close();
            surface.unref();
            showToast(viewport, "geometry_node.viewport.export.submit_failed", Toast.LENGTH_LONG, messageOf(error));
            error.printStackTrace();
        }
    }

    private static void renderAndRead(
            Viewport viewport,
            GraniteSurface surface,
            Recording recording,
            int width,
            int height,
            Path output
    ) {
        Bitmap bitmap = null;
        boolean recordingClosed = false;
        try {
            var context = Core.requireImmediateContext();
            boolean added;
            try {
                added = context.addTask(recording);
            } finally {
                recording.close();
                recordingClosed = true;
            }
            if (!added || !context.submit()) {
                throw new IllegalStateException(translate("geometry_node.viewport.export.error.gpu_rejected"));
            }
            if (!(surface.getBackingTarget().getImage() instanceof GLTexture texture)) {
                throw new IllegalStateException(translate("geometry_node.viewport.export.error.not_gl_texture"));
            }

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Format.RGBA_8888);
            bitmap.setPremultiplied(true);
            readTexture(texture, bitmap);

            Bitmap captured = bitmap;
            IO_EXECUTOR.execute(() -> saveBitmap(viewport, captured, output));
            bitmap = null;
        } catch (Throwable error) {
            if (bitmap != null) bitmap.close();
            postToast(viewport, "geometry_node.viewport.export.failed", Toast.LENGTH_LONG, messageOf(error));
            error.printStackTrace();
        } finally {
            if (!recordingClosed) recording.close();
            MuiModApi.postToUiThread(surface::unref);
        }
    }

    private static void readTexture(GLTexture texture, Bitmap bitmap) {
        int oldPackRowLength = GL33C.glGetInteger(GL33C.GL_PACK_ROW_LENGTH);
        int oldPackSkipRows = GL33C.glGetInteger(GL33C.GL_PACK_SKIP_ROWS);
        int oldPackSkipPixels = GL33C.glGetInteger(GL33C.GL_PACK_SKIP_PIXELS);
        int oldPackAlignment = GL33C.glGetInteger(GL33C.GL_PACK_ALIGNMENT);
        int oldPixelPackBuffer = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldTexture = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
        try {
            GL33C.glPixelStorei(GL33C.GL_PACK_ROW_LENGTH, 0);
            GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_ROWS, 0);
            GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_PIXELS, 0);
            GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, 1);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture.getHandle());
            GL33C.glGetTexImage(
                    GL33C.GL_TEXTURE_2D,
                    0,
                    GL33C.GL_RGBA,
                    GL33C.GL_UNSIGNED_BYTE,
                    bitmap.getAddress()
            );
        } finally {
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, oldTexture);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, oldPixelPackBuffer);
            GL33C.glPixelStorei(GL33C.GL_PACK_ROW_LENGTH, oldPackRowLength);
            GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_ROWS, oldPackSkipRows);
            GL33C.glPixelStorei(GL33C.GL_PACK_SKIP_PIXELS, oldPackSkipPixels);
            GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, oldPackAlignment);
        }
    }

    private static void saveBitmap(Viewport viewport, Bitmap bitmap, Path output) {
        Bitmap converted = null;
        try {
            converted = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Format.RGBA_8888);
            converted.setPremultiplied(false);
            converted.setPixels(bitmap, 0, 0, 0, 0, bitmap.getWidth(), bitmap.getHeight());
            Files.createDirectories(output.getParent());
            converted.saveToPath(Bitmap.SaveFormat.PNG, 0, output);
            postToast(viewport, "geometry_node.viewport.export.success", Toast.LENGTH_LONG, output);
            System.out.println("[ViewportImageExporter] Exported: " + output);
        } catch (Throwable error) {
            postToast(viewport, "geometry_node.viewport.export.save_failed", Toast.LENGTH_LONG, messageOf(error));
            error.printStackTrace();
        } finally {
            bitmap.close();
            if (converted != null) converted.close();
        }
    }

    private static Path createOutputPath(GraphSession session) {
        String baseName = sanitizeFileName(session.tabName);
        if (baseName.isBlank()) baseName = "graph";
        if (baseName.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        }
        if (baseName.isBlank()) baseName = "graph";
        String fileName = baseName + "_" + FILE_TIME.format(LocalDateTime.now()) + ".png";
        return PathUtils.resolveWorkspacePath("geometry_nodes/screenshot").toPath().resolve(fileName);
    }

    private static String sanitizeFileName(String value) {
        if (value == null) return "";
        String sanitized = value.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_").trim();
        while (sanitized.endsWith(".") || sanitized.endsWith(" ")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized;
    }

    private static void showToast(Viewport viewport, String translationKey, int duration, Object... args) {
        Toast.makeText(viewport.getContext(), translate(translationKey, args), duration).show();
    }

    private static void postToast(Viewport viewport, String translationKey, int duration, Object... args) {
        MuiModApi.postToUiThread(() -> showToast(viewport, translationKey, duration, args));
    }

    private static String translate(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).getString();
    }

    private static String messageOf(Throwable error) {
        String message = error != null ? error.getMessage() : null;
        return message == null || message.isBlank()
                ? translate("geometry_node.viewport.export.error.unknown")
                : message;
    }
}
