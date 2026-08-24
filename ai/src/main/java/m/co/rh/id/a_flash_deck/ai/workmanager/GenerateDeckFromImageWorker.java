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

package m.co.rh.id.a_flash_deck.ai.workmanager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.model.AiGeneratedDeck;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.aprovider.Provider;

public class GenerateDeckFromImageWorker extends BaseGenerateDeckWorker {
    private static final String TAG = GenerateDeckFromImageWorker.class.getName();
    /** Max dimension (width or height) for resized image before encoding. */
    private static final int MAX_IMAGE_DIM = 1024;
    /** JPEG compression quality (0-100). */
    private static final int JPEG_QUALITY = 85;

    public GenerateDeckFromImageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Provider provider = getProvider();
        GeminiService geminiService = provider.get(GeminiService.class);

        String imagePathsJson = getInputData().getString(WorkManagerKeys.AI_GENERATE_FROM_IMAGE_IMAGE_PATHS);
        String prompt = getInputData().getString(WorkManagerKeys.AI_GENERATE_FROM_IMAGE_PROMPT);
        int maxCards = getInputData().getInt(WorkManagerKeys.AI_GENERATE_FROM_IMAGE_MAX_CARDS, 10);
        String modelId = getInputData().getString(WorkManagerKeys.AI_GENERATE_FROM_IMAGE_MODEL_ID);

        List<File> imageFiles = new ArrayList<>();
        try {
            if (imagePathsJson == null || imagePathsJson.trim().isEmpty()) {
                throw new IllegalArgumentException("No image paths provided in input data");
            }
            JSONArray pathsArray = new JSONArray(imagePathsJson);
            for (int i = 0; i < pathsArray.length(); i++) {
                imageFiles.add(new File(pathsArray.getString(i)));
            }

            List<String> base64Images = encodeImages(imageFiles);
            if (base64Images.isEmpty()) {
                throw new IllegalStateException("No valid images could be processed or decoded");
            }

            AiGeneratedDeck aiDeck = geminiService.generateDeckFromImages(base64Images, prompt, maxCards, modelId).blockingGet();
            Deck deck = saveAiDeckToDatabase(aiDeck);

            String notificationTitle = getApplicationContext().getString(R.string.ai_notification_title);
            postNotification(notificationTitle,
                    getApplicationContext().getString(R.string.ai_generate_from_image_success, deck.name, String.valueOf(aiDeck.cards.size())),
                    deck.id);

            return Result.success();
        } catch (Exception exception) {
            String notificationTitle = getApplicationContext().getString(R.string.ai_notification_title);
            postNotification(notificationTitle,
                    getApplicationContext().getString(R.string.ai_generate_from_image_failed, exception.getMessage()));
            getLogger().e(TAG, "Failed to generate deck from images", exception);
            return Result.failure();
        } finally {
            // Clean up temp files
            for (File file : imageFiles) {
                if (file != null && file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
    }

    /**
     * Scale each image to MAX_IMAGE_DIM on the longest side, compress to JPEG,
     * and return Base64-encoded strings.
     */
    private List<String> encodeImages(List<File> imageFiles) throws Exception {
        List<String> result = new ArrayList<>();
        for (File file : imageFiles) {
            if (file == null || !file.exists()) {
                getLogger().e(TAG, "Image file does not exist: " + (file != null ? file.getAbsolutePath() : "null"));
                continue;
            }
            // Decode with inSampleSize to avoid OOM on very large images
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            int inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, MAX_IMAGE_DIM * 2);
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = inSampleSize;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bitmap == null) {
                getLogger().e(TAG, "Could not decode image: " + file.getAbsolutePath());
                continue;
            }
            // Scale down to MAX_IMAGE_DIM on longest side
            Bitmap scaled = scaleBitmap(bitmap, MAX_IMAGE_DIM);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            scaled.recycle();
            byte[] bytes = baos.toByteArray();
            result.add(Base64.getEncoder().encodeToString(bytes));
        }
        return result;
    }

    private int calculateInSampleSize(int width, int height, int maxDim) {
        int inSampleSize = 1;
        int longestSide = Math.max(width, height);
        while (longestSide / (inSampleSize * 2) >= maxDim) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private Bitmap scaleBitmap(Bitmap bitmap, int maxDim) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int longestSide = Math.max(w, h);
        if (longestSide <= maxDim) {
            return bitmap;
        }
        float scale = (float) maxDim / longestSide;
        int newW = Math.round(w * scale);
        int newH = Math.round(h * scale);
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true);
    }
}
