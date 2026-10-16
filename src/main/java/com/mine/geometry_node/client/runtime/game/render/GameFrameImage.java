package com.mine.geometry_node.client.runtime.game.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ISurface;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.engine.Swizzle;
import icyllis.arc3d.granite.GraniteImage;
import icyllis.arc3d.opengl.GLBackendImage;
import icyllis.arc3d.opengl.GLDevice;
import icyllis.arc3d.opengl.GLImageDesc;
import icyllis.arc3d.opengl.GLTexture;
import icyllis.arc3d.opengl.GLTextureMutableState;
import icyllis.arc3d.opengl.GLUtil;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;

/** Presents the shared game texture inside the normal ModernUI drawing order.
 * The publication owns one Arc3D reference. Recorded UI draws retain their own
 * references, so resizing or closing a game view cannot delete an in-flight image.
 */
public final class GameFrameImage {
    private static final Channel GAME = new Channel();

    private GameFrameImage() {}

    static Channel gameChannel() { return GAME; }

    public static Size size() { return GAME.size(); }
    public static boolean isAvailable() { return GAME.isAvailable(); }
    public static boolean draw(Canvas canvas, Size size, float left, float top, float right, float bottom) {
        return GAME.draw(canvas, size, left, top, right, bottom);
    }

    /** Each render source owns a publication; UI recordings never switch source underneath a draw. */
    public static final class Channel {
        private final Object LOCK = new Object();
        private static final Size EMPTY = new Size(0, 0);
        private Frame frame;

        /** Render thread only. Success transfers ownership of texture to this bridge.
         * The producer may keep writing pixels, but must not close the texture itself.
         * Unsupported backends return false without taking ownership.
         */
        public boolean publish(GpuTexture texture) {
            return publish(texture, null, () -> Minecraft.getInstance().execute(texture::close));
        }

        boolean publish(GpuTexture texture, PresentedViewportFrame metadata, Runnable released) {
            Core.checkRenderThread();
            var context = Core.peekImmediateContext();
            if (!(texture instanceof GlTexture glTexture) || texture.isClosed()
                    || texture.getFormat() != TextureFormat.RGBA8
                    || context == null || !(context.getDevice() instanceof GLDevice device)) {
                return false;
            }
            int width = texture.getWidth(0);
            int height = texture.getHeight(0);
            var desc = new GLImageDesc(GL11C.GL_TEXTURE_2D, GL11C.GL_RGBA8,
                    Engine.ImageType.k2D, GLUtil.glFormatToImageFormat(GL11C.GL_RGBA8),
                    width, height, 1, 1, 1, 1, ISurface.FLAG_SAMPLED_IMAGE);
            GLTexture image = GLTexture.wrap(device,
                    new GLBackendImage(glTexture.glId(), desc, new GLTextureMutableState()));
            if (image == null) return false;
            // Wrapped Arc3D textures never delete the external GL name themselves.
            // This callback runs only after both recording and GPU command references
            // are gone. Minecraft owns the GL resource and must release it on its thread.
            image.setReleaseCallback(released);
            Frame previous;
            synchronized (LOCK) {
                previous = frame;
                frame = new Frame(image, new Size(width, height), metadata);
            }
            if (previous != null) previous.image.unref();
            return true;
        }

        /** Render thread only; safe to call repeatedly when no game views are visible. */
        public void clear() {
            Frame previous;
            synchronized (LOCK) {
                previous = frame;
                frame = null;
            }
            if (previous != null) previous.image.unref();
        }

        /** A single size snapshot avoids mixing dimensions across a resize. */
        public Size size() {
            synchronized (LOCK) {
                return frame == null ? EMPTY : frame.size;
            }
        }

        public boolean isAvailable() { return size().available(); }
        public int width() { return size().width(); }
        public int height() { return size().height(); }

        /** UI thread only. The supplied rectangle is in the view's local coordinates. */
        public boolean draw(Canvas canvas, float left, float top, float right, float bottom) {
            return draw(canvas, null, left, top, right, bottom);
        }

        /** Skip a stale layout if the producer resized after the view measured its frame. */
        public boolean draw(Canvas canvas, Size expectedSize,
                                   float left, float top, float right, float bottom) {
            return drawFrame(canvas, expectedSize, left, top, right, bottom) != null;
        }

        public PresentedViewportFrame drawPresented(Canvas canvas, Size expectedSize,
                                                     float left, float top, float right, float bottom) {
            Frame drawn = drawFrame(canvas, expectedSize, left, top, right, bottom);
            return drawn == null ? null : drawn.metadata;
        }

        private Frame drawFrame(Canvas canvas, Size expectedSize,
                                float left, float top, float right, float bottom) {
            if (!(canvas instanceof ArcCanvas arcCanvas) || !(right > left) || !(bottom > top)) return null;
            var context = Core.requireUiRecordingContext();
            context.checkOwnerThread();
            Frame current;
            synchronized (LOCK) {
                current = frame;
                if (current == null || (expectedSize != null && !current.size.equals(expectedSize))) return null;
                current.image.ref();
            }
            // Each constructor takes ownership of its argument's existing reference.
            var view = new ImageProxyView(ImageProxy.wrap(current.image),
                    Engine.SurfaceOrigin.kLowerLeft, Swizzle.RGB1);
            GraniteImage image = null;
            try {
                image = new GraniteImage(context, view, ColorInfo.CT_RGBA_8888, ColorInfo.AT_OPAQUE, null);
                arcCanvas.getCanvas().drawImageRect(image, new Rect2f(left, top, right, bottom),
                        SamplingOptions.LINEAR, null);
                return current;
            } finally {
                if (image != null) image.unref();
                else view.unref();
            }
        }

        private record Frame(GLTexture image, Size size, PresentedViewportFrame metadata) {}
    }

    public record Size(int width, int height) {
        public boolean available() { return width > 0 && height > 0; }
    }

}
