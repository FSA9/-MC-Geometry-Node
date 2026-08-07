package com.mine.geometry_node.client.ui.editor.asset.image;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

import java.io.File;

public final class ImageThumbnailView extends View {
    private static final int BACKGROUND_COLOR = 0xFF24282D;
    private static final int FALLBACK_COLOR = 0xFF77C99D;

    private final File mFile;
    private final Paint mPaint = new Paint();
    private ImageThumbnailCache.Subscription mSubscription;
    private Bitmap mSourceBitmap;
    private Image mImage;

    public ImageThumbnailView(Context context, File file) {
        super(context);
        mFile = file;
        mPaint.setAntiAlias(true);
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSubscription = ImageThumbnailCache.subscribe(mFile, this);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mSubscription != null) {
            mSubscription.close();
            mSubscription = null;
        }
        closeImage();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setColor(BACKGROUND_COLOR);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);

        Bitmap bitmap = mSubscription != null ? mSubscription.bitmap() : null;
        if (bitmap == null) {
            drawFallback(canvas);
            return;
        }
        if (bitmap != mSourceBitmap) {
            closeImage();
            mSourceBitmap = bitmap;
            try {
                mImage = Image.createTextureFromBitmap(bitmap);
            } catch (RuntimeException ignored) {
                mImage = null;
            }
        }
        if (mImage == null || mImage.isClosed()) {
            drawFallback(canvas);
            return;
        }

        float padding = UIUtils.dp2px(2.0f);
        float availableWidth = Math.max(1.0f, getWidth() - padding * 2.0f);
        float availableHeight = Math.max(1.0f, getHeight() - padding * 2.0f);
        float scale = Math.min(availableWidth / mImage.getWidth(), availableHeight / mImage.getHeight());
        float width = mImage.getWidth() * scale;
        float height = mImage.getHeight() * scale;
        float left = (getWidth() - width) * 0.5f;
        float top = (getHeight() - height) * 0.5f;
        mPaint.setColor(0xFFFFFFFF);
        canvas.drawImage(mImage, 0, 0, mImage.getWidth(), mImage.getHeight(),
                left, top, left + width, top + height, mPaint);
    }

    private void drawFallback(Canvas canvas) {
        float inset = UIUtils.dp2px(6.0f);
        mPaint.setColor(FALLBACK_COLOR);
        canvas.drawRect(inset, inset, Math.max(inset, getWidth() - inset), Math.max(inset, getHeight() - inset), mPaint);
    }

    private void closeImage() {
        if (mImage != null) {
            mImage.close();
            mImage = null;
        }
        mSourceBitmap = null;
    }

    public static void invalidate(File file) {
        ImageThumbnailCache.invalidate(file);
    }

    public static void invalidateUnder(File file) {
        ImageThumbnailCache.invalidateUnder(file);
    }

    public static void clearCache() {
        ImageThumbnailCache.clear();
    }
}
