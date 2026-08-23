/*
 *     Copyright (C) 2021 Ruby Hartono
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package m.co.rh.id.a_flash_deck.base.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Helper class for image processing and compression
 */
public class ImageHelper {
    private static final String TAG = ImageHelper.class.getName();
    private static final int MAX_IMAGE_WIDTH = 1280;
    private static final int MAX_IMAGE_HEIGHT = 720;
    private static final int JPEG_QUALITY = 90;

    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;

    public ImageHelper(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
    }

    /**
     * Compress image to default size (1280x720)
     *
     * @param content  URI of the source image
     * @param outFile  destination file
     * @throws IOException when failed to compress
     */
    public void compress(Uri content, File outFile) throws IOException {
        compress(content, outFile, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
    }

    /**
     * Compress image to specified size with EXIF rotation correction
     *
     * @param content  URI of the source image
     * @param outFile  destination file
     * @param width    target width
     * @param height   target height
     * @throws IOException when failed to compress
     */
    public void compress(Uri content, File outFile, int width, int height) throws IOException {
        ContentResolver contentResolver = mAppContext.getContentResolver();
        Bitmap bitmap = null;
        
        try (InputStream fis = contentResolver.openInputStream(content)) {
            if (fis == null) {
                throw new IOException("Unable to open input stream for " + content);
            }

            BitmapFactory.Options bmOptions = getBitmapOptionForCompression(fis, width, height);
            bitmap = processExifAttr(mAppContext, content, bmOptions);
            
            try (OutputStream fileOutputStream = new BufferedOutputStream(
                    new FileOutputStream(outFile), 10240)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fileOutputStream);
                fileOutputStream.flush();
            }
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    /**
     * Calculate bitmap options for compression with proper sampling
     */
    private BitmapFactory.Options getBitmapOptionForCompression(InputStream fis, int width, int height) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(fis, null, bmOptions);
        int inWidth = bmOptions.outWidth;
        int inHeight = bmOptions.outHeight;
        int outWidth = width;
        int outHeight = height;
        if (inHeight > inWidth) {
            // Swap dimensions for portrait images
            outHeight = width;
            outWidth = height;
        }
        int scaleFactor = Math.max(1, Math.min(inWidth / outWidth, inHeight / outHeight));
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        return bmOptions;
    }

    /**
     * Process EXIF attributes and rotate bitmap if needed
     */
    private Bitmap processExifAttr(Context context, Uri imageUri, BitmapFactory.Options bmOptions) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        InputStream inputStream = null;
        Bitmap bitmap = null;
        Bitmap rotatedBitmap = null;

        try {
            inputStream = contentResolver.openInputStream(imageUri);
            if (inputStream == null) {
                throw new IOException("Unable to open stream for URI: " + imageUri);
            }

            if (!inputStream.markSupported()) {
                inputStream = new BufferedInputStream(inputStream);
            }
            inputStream.mark(Integer.MAX_VALUE); // Mark the beginning of the stream

            ExifInterface exifInterface = new ExifInterface(inputStream);
            int rotation = getRotation(exifInterface);

            try {
                inputStream.reset(); // Reset the stream to the beginning
            } catch (IOException e) {
                mLogger.get().w(TAG, "Failed to reset input stream, trying to reopen.", e);
                // If reset fails, close and reopen the stream
                try {
                    inputStream.close();
                } catch (IOException closeErr) {
                    mLogger.get().e(TAG, "Error closing initial stream", closeErr);
                }
                inputStream = contentResolver.openInputStream(imageUri);
                if (inputStream == null) {
                    throw new IOException("Unable to open stream for URI: " + imageUri);
                }
            }

            bitmap = BitmapFactory.decodeStream(inputStream, null, bmOptions);
            if (bitmap == null) {
                mLogger.get().e(TAG, "BitmapFactory.decodeStream returned null for " + imageUri);
                throw new IOException("Failed to decode bitmap from stream for URI: " + imageUri);
            }

            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.setRotate(rotation);
                try {
                    rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                            matrix, true);
                    bitmap.recycle(); // Recycle original bitmap after creating rotated copy
                    return rotatedBitmap;
                } catch (Throwable t) {
                    bitmap.recycle(); // Recycle original bitmap if rotation fails
                    throw t;
                }
            }
            return bitmap;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    mLogger.get().e(TAG, "Error closing stream", e);
                }
            }
        }
    }

    /**
     * Get rotation angle from EXIF data
     */
    private int getRotation(ExifInterface exifInterface) {
        int rotation = 0;
        int exifRotation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

        if (exifRotation != ExifInterface.ORIENTATION_UNDEFINED) {
            switch (exifRotation) {
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
            }
        }
        return rotation;
    }
}
