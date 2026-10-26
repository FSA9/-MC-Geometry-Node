package com.mine.geometry_node.client.integration.game;

import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.render.SharedGameFrame;
import com.mine.geometry_node.client.runtime.game.render.ViewportFramePublication;
import com.mine.geometry_node.client.runtime.game.render.ViewportFrameSize;

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
import org.lwjgl.opengl.GL11C;

/** Presents runtime-owned game/editor textures inside the normal ModernUI drawing order.
 * The publication owns one Arc3D reference. Recorded UI draws retain their own
 * references, so resizing or closing a game view cannot delete an in-flight image.
 */
public final class GameFrameImage {
    private GameFrameImage() {}

    private static Channel channel(ViewportFramePublication publication) {
        if (publication == null) return null;
        if (publication instanceof Channel channel) return channel;
        throw new IllegalStateException("Viewport publication does not use the installed ModernUI bridge");
    }

    public static ViewportFrameSize size() {
        var publication = SharedGameFrame.framePublication();
        return publication == null ? ViewportFrameSize.EMPTY : publication.size();
    }
    public static boolean isAvailable() { return size().available(); }
    public static boolean draw(Canvas canvas, ViewportFrameSize size, float left, float top, float right, float bottom) {
        var channel = channel(SharedGameFrame.framePublication());
        return channel != null && channel.draw(canvas, size, left, top, right, bottom);
    }

    /** Admission and recording keep the runtime's session lock; the bridge retains the exact image/metadata pair. */
    public static PresentedViewportFrame drawPresented(Object owner, Canvas canvas, ViewportFrameSize size,
                                                        float left, float top, float right, float bottom) {
        return EditViewportRuntime.withFramePublication(owner,
                publication -> channel(publication).drawPresented(canvas, size, left, top, right, bottom));
    }

    /** Each render source owns a publication; UI recordings never switch source underneath a draw. */
    public static final class Channel implements ViewportFramePublication {
        private final Object LOCK = new Object();
        private Frame frame;

        /** Unsupported backends return false without taking ownership. */
        @Override
        public boolean publish(GpuTexture texture, PresentedViewportFrame metadata, Runnable released) {
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
                frame = new Frame(image, new ViewportFrameSize(width, height), metadata);
            }
            if (previous != null) previous.image.unref();
            return true;
        }

        /** Render thread only; safe to call repeatedly when no game views are visible. */
        @Override
        public void clear() {
            Frame previous;
            synchronized (LOCK) {
                previous = frame;
                frame = null;
            }
            if (previous != null) previous.image.unref();
        }

        /** A single size snapshot avoids mixing dimensions across a resize. */
        @Override
        public ViewportFrameSize size() {
            synchronized (LOCK) {
                return frame == null ? ViewportFrameSize.EMPTY : frame.size;
            }
        }

        /** UI thread only. The supplied rectangle is in the view's local coordinates. */
        public boolean draw(Canvas canvas, float left, float top, float right, float bottom) {
            return draw(canvas, null, left, top, right, bottom);
        }

        /** Skip a stale layout if the producer resized after the view measured its frame. */
        public boolean draw(Canvas canvas, ViewportFrameSize expectedSize,
                                   float left, float top, float right, float bottom) {
            return drawFrame(canvas, expectedSize, left, top, right, bottom) != null;
        }

        public PresentedViewportFrame drawPresented(Canvas canvas, ViewportFrameSize expectedSize,
                                                     float left, float top, float right, float bottom) {
            Frame drawn = drawFrame(canvas, expectedSize, left, top, right, bottom);
            return drawn == null ? null : drawn.metadata;
        }

        private Frame drawFrame(Canvas canvas, ViewportFrameSize expectedSize,
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

        private record Frame(GLTexture image, ViewportFrameSize size, PresentedViewportFrame metadata) {}
    }

}
