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

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Class to provide files through this app
 */
public class FileHelper {
    private static final String TAG = FileHelper.class.getName();

    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;
    private final File mLogFile;
    private final File mTempFileRoot;
    private final File mCardQuestionImageParent;
    private final File mCardAnswerImageParent;
    private final File mCardQuestionImageThumbnailParent;
    private final File mCardAnswerImageThumbnailParent;
    private final File mCardQuestionVoiceParent;
    private final File mCardAnswerVoiceParent;

    public FileHelper(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        File cacheDir = mAppContext.getCacheDir();
        File fileDir = mAppContext.getFilesDir();
        mLogFile = new File(cacheDir, "alogger/app.log");
        mTempFileRoot = new File(cacheDir, "/tmp");
        mTempFileRoot.mkdirs();
        mCardQuestionImageParent = new File(fileDir, "app/card/question/image");
        mCardQuestionImageParent.mkdirs();
        mCardAnswerImageParent = new File(fileDir, "app/card/answer/image");
        mCardAnswerImageParent.mkdirs();
        mCardQuestionImageThumbnailParent = new File(fileDir, "app/card/question/image/thumbnail");
        mCardQuestionImageThumbnailParent.mkdirs();
        mCardAnswerImageThumbnailParent = new File(fileDir, "app/card/answer/image/thumbnail");
        mCardAnswerImageThumbnailParent.mkdirs();
        mCardQuestionVoiceParent = new File(fileDir, "app/card/question/voice");
        mCardQuestionVoiceParent.mkdirs();
        mCardAnswerVoiceParent = new File(fileDir, "app/card/answer/voice");
        mCardAnswerVoiceParent.mkdirs();
    }

    public File createTempFile(String fileName) throws IOException {
        return createTempFile(fileName, null);
    }

    /**
     * Create temporary file
     *
     * @param fileName file name for this file
     * @param content  content of the file to write to this temp file
     * @return temporary file
     * @throws IOException when failed to create file
     */
    public File createTempFile(String fileName, Uri content) throws IOException {
        File parent = new File(mTempFileRoot, UUID.randomUUID().toString());
        parent.mkdirs();
        String fName = fileName;
        if (fName == null || fName.isEmpty()) {
            fName = UUID.randomUUID().toString();
        }
        File tmpFile = new File(parent, fName);
        tmpFile.createNewFile();
        copyFile(content, tmpFile);
        return tmpFile;
    }

    public void clearLogFile() {
        if (mLogFile.exists()) {
            mLogFile.delete();
            try {
                mLogFile.createNewFile();
            } catch (Throwable throwable) {
                mLogger.get().e(TAG, "Failed to create new file for log", throwable);
            }
        }
    }

    public File getLogFile() {
        return mLogFile;
    }

    public File createImageTempFile() throws IOException {
        File parent = new File(mTempFileRoot, UUID.randomUUID().toString());
        parent.mkdirs();
        File tmpFile = new File(parent, UUID.randomUUID().toString() + ".jpg");
        tmpFile.createNewFile();
        return tmpFile;
    }

    public File createImageTempFile(Uri content) throws IOException {
        File outFile = createImageTempFile();
        try {
            copyImage(content, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File createCardQuestionImage(File inFile, String fileName) throws IOException {
        File outFile = new File(mCardQuestionImageParent, fileName);
        try {
            outFile.createNewFile();
            copyImage(Uri.fromFile(inFile), outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File createCardQuestionImage(Uri content) throws IOException {
        File outFile = newCardQuestionImage();
        try {
            outFile.createNewFile();
            copyImage(content, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File createCardQuestionImageThumbnail(Uri content, String fileName) throws IOException {
        File outFile = new File(mCardQuestionImageThumbnailParent, fileName);
        try {
            outFile.createNewFile();
            copyImage(content, outFile, 320, 180);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File createCardAnswerImageThumbnail(Uri content, String fileName) throws IOException {
        File outFile = new File(mCardAnswerImageThumbnailParent, fileName);
        try {
            outFile.createNewFile();
            copyImage(content, outFile, 320, 180);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public void deleteCardQuestionImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mCardQuestionImageParent, fileName);
            file.delete();
            File thumbnail = new File(mCardQuestionImageThumbnailParent, fileName);
            thumbnail.delete();
        }
    }

    public void deleteCardAnswerImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mCardAnswerImageParent, fileName);
            file.delete();
            File thumbnail = new File(mCardAnswerImageThumbnailParent, fileName);
            thumbnail.delete();
        }
    }

    public File createCardQuestionVoice(File inFile, String fileName) throws IOException {
        File outFile = new File(mCardQuestionVoiceParent, fileName);
        try {
            outFile.createNewFile();
            copyFile(Uri.fromFile(inFile), outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File createCardQuestionVoice(Uri content) throws IOException {
        String fName = UUID.randomUUID().toString();
        File outFile = new File(mCardQuestionVoiceParent, fName);
        try {
            outFile.createNewFile();
            copyFile(content, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File createCardAnswerVoice(File inFile, String fileName) throws IOException {
        File outFile = new File(mCardAnswerVoiceParent, fileName);
        try {
            outFile.createNewFile();
            copyFile(Uri.fromFile(inFile), outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File createCardAnswerVoice(Uri content) throws IOException {
        String fName = UUID.randomUUID().toString();
        File outFile = new File(mCardAnswerVoiceParent, fName);
        try {
            outFile.createNewFile();
            copyFile(content, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public void deleteCardQuestionVoice(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mCardQuestionVoiceParent, fileName);
            file.delete();
        }
    }

    public void deleteCardAnswerVoice(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mCardAnswerVoiceParent, fileName);
            file.delete();
        }
    }

    public File getCardQuestionImage(String fileName) {
        return new File(mCardQuestionImageParent, fileName);
    }

    public File getCardAnswerImage(String fileName) {
        return new File(mCardAnswerImageParent, fileName);
    }

    public File getCardQuestionImageThumbnail(String fileName) {
        return new File(mCardQuestionImageThumbnailParent, fileName);
    }

    public File getCardAnswerImageThumbnail(String fileName) {
        return new File(mCardAnswerImageThumbnailParent, fileName);
    }

    public File getCardQuestionVoice(String fileName) {
        return new File(mCardQuestionVoiceParent, fileName);
    }

    public File getCardAnswerVoice(String fileName) {
        return new File(mCardAnswerVoiceParent, fileName);
    }

    public File createCardAnswerImage(File inFile, String fileName) throws IOException {
        File outFile = new File(mCardAnswerImageParent, fileName);
        try {
            outFile.createNewFile();
            copyImage(Uri.fromFile(inFile), outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File createCardAnswerImage(Uri content) throws IOException {
        File tmpFile = newCardAnswerImage();
        try {
            tmpFile.createNewFile();
            copyImage(content, tmpFile);
            return tmpFile;
        } catch (Exception e) {
            tmpFile.delete();
            throw e;
        }
    }

    @NonNull
    private File newCardQuestionImage() {
        String ext = ".jpg";
        String fName = UUID.randomUUID().toString();
        return new File(mCardQuestionImageParent, fName + ext);
    }

    @NonNull
    private File newCardAnswerImage() {
        String ext = ".jpg";
        String fName = UUID.randomUUID().toString();
        return new File(mCardAnswerImageParent, fName + ext);
    }

    private void copyImage(Uri content, File outFile) throws IOException {
        copyImage(content, outFile, 1280, 720);
    }

    private void copyImage(Uri content, File outFile, int width, int height) throws IOException {
        ContentResolver contentResolver = mAppContext.getContentResolver();
        InputStream fis = contentResolver.openInputStream(content);
        if (fis == null) {
            throw new IOException("Unable to open input stream for " + content);
        }

        BitmapFactory.Options bmOptions = getBitmapOptionForCompression(fis, width, height);
        try {
            fis.close();
        } catch (IOException e) {
            mLogger.get().e(TAG, "Error closing stream", e);
        }

        OutputStream fileOutputStream = new BufferedOutputStream(
                new FileOutputStream(outFile), 10240);
        Bitmap bitmap = processExifAttr(mAppContext, content, bmOptions);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream);
        fileOutputStream.flush();
        fileOutputStream.close();
    }

    private BitmapFactory.Options getBitmapOptionForCompression(InputStream fis, int width, int height) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(fis, null, bmOptions);
        int inWidth = bmOptions.outWidth;
        int inHeight = bmOptions.outHeight;
        int outWidth = width;
        int outHeight = height;
        if (inHeight > inWidth) {
            outHeight = width;
            outWidth = height;
        }
        int scaleFactor = Math.max(1, Math.min(inWidth / outWidth, inHeight / outHeight));
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        return bmOptions;
    }

    private Bitmap processExifAttr(Context context, Uri imageUri, BitmapFactory.Options bmOptions) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        InputStream inputStream = contentResolver.openInputStream(imageUri);
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

        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, bmOptions);
        if (bitmap == null) {
            // Add logging or throw an exception if bitmap decoding fails
            mLogger.get().e(TAG, "BitmapFactory.decodeStream returned null for " + imageUri);
            throw new IOException("Failed to decode bitmap from stream for URI: " + imageUri);
        }

        try {
            inputStream.close();
        } catch (IOException e) {
            mLogger.get().e(TAG, "Error closing stream", e);
        }

        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.setRotate(rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    matrix, true);
        }
        return bitmap;
    }

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

    private void copyFile(Uri content, File tmpFile) throws IOException {
        if (content != null) {
            ContentResolver cr = mAppContext.getContentResolver();
            InputStream inputStream = cr.openInputStream(content);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

            FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            byte[] buff = new byte[2048];
            int b = bufferedInputStream.read(buff);
            while (b != -1) {
                bufferedOutputStream.write(buff);
                b = bufferedInputStream.read(buff);
            }
            bufferedOutputStream.close();
            fileOutputStream.close();
            bufferedInputStream.close();
            inputStream.close();
        }
    }

    public void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[5000];
        int length;
        while ((length = in.read(buf)) > 0) {
            out.write(buf, 0, length);
        }
    }

    public File getCardQuestionImageParent() {
        return mCardQuestionImageParent;
    }

    public File getCardAnswerImageParent() {
        return mCardAnswerImageParent;
    }

    public File getCardQuestionVoiceParent() {
        return mCardQuestionVoiceParent;
    }
}
