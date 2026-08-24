/*
 *     Copyright (C) 2021-present Ruby Hartono
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
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Store and manage card media files (images and voices)
 */
public class CardMediaStore {
    private static final int THUMBNAIL_WIDTH = 320;
    private static final int THUMBNAIL_HEIGHT = 180;

    private final File mCardQuestionImageParent;
    private final File mCardAnswerImageParent;
    private final File mCardQuestionImageThumbnailParent;
    private final File mCardAnswerImageThumbnailParent;
    private final File mCardQuestionVoiceParent;
    private final File mCardAnswerVoiceParent;
    private final ProviderValue<ImageHelper> mImageHelper;
    private final Context mAppContext;

    public CardMediaStore(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        File fileDir = mAppContext.getFilesDir();
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
        mImageHelper = provider.lazyGet(ImageHelper.class);
    }

    /**
     * Functional interface for writing media files
     */
    private interface MediaWriter {
        void write(File outFile) throws IOException;
    }

    /**
     * Helper method to create media file with error handling
     */
    private File createMediaFile(File parent, String fileName, MediaWriter writer) throws IOException {
        File outFile = new File(parent, fileName);
        try {
            outFile.createNewFile();
            writer.write(outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    // Question Image Methods

    public File createCardQuestionImage(File inFile, String fileName) throws IOException {
        return createMediaFile(mCardQuestionImageParent, fileName,
                outFile -> mImageHelper.get().compress(Uri.fromFile(inFile), outFile));
    }

    public File createCardQuestionImage(Uri content) throws IOException {
        return createMediaFile(mCardQuestionImageParent, UUID.randomUUID().toString() + ".jpg",
                outFile -> mImageHelper.get().compress(content, outFile));
    }

    public File createCardQuestionImageThumbnail(Uri content, String fileName) throws IOException {
        return createMediaFile(mCardQuestionImageThumbnailParent, fileName,
                outFile -> mImageHelper.get().compress(content, outFile, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
    }

    public void deleteCardQuestionImage(String fileName) {
        deleteMediaFile(fileName, mCardQuestionImageParent, mCardQuestionImageThumbnailParent);
    }

    public File getCardQuestionImage(String fileName) {
        return new File(mCardQuestionImageParent, fileName);
    }

    public File getCardQuestionImageThumbnail(String fileName) {
        return new File(mCardQuestionImageThumbnailParent, fileName);
    }

    public File getCardQuestionImageParent() {
        return mCardQuestionImageParent;
    }

    // Answer Image Methods

    public File createCardAnswerImage(File inFile, String fileName) throws IOException {
        return createMediaFile(mCardAnswerImageParent, fileName,
                outFile -> mImageHelper.get().compress(Uri.fromFile(inFile), outFile));
    }

    public File createCardAnswerImage(Uri content) throws IOException {
        return createMediaFile(mCardAnswerImageParent, UUID.randomUUID().toString() + ".jpg",
                outFile -> mImageHelper.get().compress(content, outFile));
    }

    public File createCardAnswerImageThumbnail(Uri content, String fileName) throws IOException {
        return createMediaFile(mCardAnswerImageThumbnailParent, fileName,
                outFile -> mImageHelper.get().compress(content, outFile, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
    }

    public void deleteCardAnswerImage(String fileName) {
        deleteMediaFile(fileName, mCardAnswerImageParent, mCardAnswerImageThumbnailParent);
    }

    public File getCardAnswerImage(String fileName) {
        return new File(mCardAnswerImageParent, fileName);
    }

    public File getCardAnswerImageThumbnail(String fileName) {
        return new File(mCardAnswerImageThumbnailParent, fileName);
    }

    public File getCardAnswerImageParent() {
        return mCardAnswerImageParent;
    }

    // Question Voice Methods

    public File createCardQuestionVoice(File inFile, String fileName) throws IOException {
        return createMediaFile(mCardQuestionVoiceParent, fileName,
                outFile -> copyFile(Uri.fromFile(inFile), outFile));
    }

    public File createCardQuestionVoice(Uri content) throws IOException {
        return createMediaFile(mCardQuestionVoiceParent, UUID.randomUUID().toString(),
                outFile -> copyFile(content, outFile));
    }

    public void deleteCardQuestionVoice(String fileName) {
        deleteMediaFile(fileName, mCardQuestionVoiceParent);
    }

    public File getCardQuestionVoice(String fileName) {
        return new File(mCardQuestionVoiceParent, fileName);
    }

    public File getCardQuestionVoiceParent() {
        return mCardQuestionVoiceParent;
    }

    // Answer Voice Methods

    public File createCardAnswerVoice(File inFile, String fileName) throws IOException {
        return createMediaFile(mCardAnswerVoiceParent, fileName,
                outFile -> copyFile(Uri.fromFile(inFile), outFile));
    }

    public File createCardAnswerVoice(Uri content) throws IOException {
        return createMediaFile(mCardAnswerVoiceParent, UUID.randomUUID().toString(),
                outFile -> copyFile(content, outFile));
    }

    public void deleteCardAnswerVoice(String fileName) {
        deleteMediaFile(fileName, mCardAnswerVoiceParent);
    }

    public File getCardAnswerVoice(String fileName) {
        return new File(mCardAnswerVoiceParent, fileName);
    }

    public File getCardAnswerVoiceParent() {
        return mCardAnswerVoiceParent;
    }

    /**
     * Helper method to delete media file from multiple parent directories
     */
    private void deleteMediaFile(String fileName, File... parents) {
        if (fileName != null && !fileName.isEmpty()) {
            for (File parent : parents) {
                File file = new File(parent, fileName);
                file.delete();
            }
        }
    }

    /**
     * Copy file from URI to destination file
     */
    private void copyFile(Uri content, File outFile) throws IOException {
        if (content != null) {
            ContentResolver cr = mAppContext.getContentResolver();
            try (InputStream inputStream = cr.openInputStream(content);
                 BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                 FileOutputStream fileOutputStream = new FileOutputStream(outFile);
                 BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream)) {
                byte[] buff = new byte[2048];
                int b;
                while ((b = bufferedInputStream.read(buff)) != -1) {
                    bufferedOutputStream.write(buff, 0, b);
                }
            }
        }
    }
}
