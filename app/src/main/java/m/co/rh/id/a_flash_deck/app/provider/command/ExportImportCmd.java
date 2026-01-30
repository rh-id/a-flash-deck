/*
 *     Copyright (C) 2021-2026 Ruby Hartono
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

package m.co.rh.id.a_flash_deck.app.provider.command;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.component.AnkiExporter;
import m.co.rh.id.a_flash_deck.app.provider.component.AnkiImporter;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class ExportImportCmd {
    private static final String TAG = ExportImportCmd.class.getName();
    private static final String ZIP_CONTENT_DECKS_JSON = "Decks.json";
    private static final String ZIP_CONTENT_IMAGE_QUESTION_DIR = "media/image/question/";
    private static final String ZIP_CONTENT_IMAGE_ANSWER_DIR = "media/image/answer/";
    private static final String ZIP_CONTENT_VOICE_QUESTION_DIR = "media/voice/question/";

    protected Context mAppContext;
    protected AnkiImporter mAnkiImporter;
    protected AnkiExporter mAnkiExporter;
    protected ExecutorService mExecutorService;
    protected ILogger mLogger;
    protected DeckDao mDeckDao;
    protected FileHelper mFileHelper;

    public ExportImportCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mDeckDao = provider.get(DeckDao.class);
        mFileHelper = provider.get(FileHelper.class);
        mAnkiImporter = provider.get(AnkiImporter.class);
        mAnkiExporter = provider.get(AnkiExporter.class);
    }

    public Single<File> exportFile(List<Deck> deckList) {
        return exportFile(deckList, "native");
    }

    public Single<File> exportFile(List<Deck> deckList, String format) {
        if ("anki".equals(format)) {
            return Single.fromFuture(
                    mExecutorService.submit(() -> mAnkiExporter.exportApkg(deckList))
            );
        }
        return exportNativeFile(deckList);
    }

    private Single<File> exportNativeFile(List<Deck> deckList) {
        return Single.fromFuture(
                mExecutorService.submit(() -> {
                    if (!deckList.isEmpty()) {
                        File zipFile = mFileHelper.createTempFile("Decks.zip");
                        try {
                            List<Card> allCards = mDeckDao.getCardsByDecks(deckList);
                            Map<Long, List<Card>> cardsByDeckId = new HashMap<>();
                            for (Card card : allCards) {
                                cardsByDeckId.computeIfAbsent(card.deckId, k -> new ArrayList<>()).add(card);
                            }
                            
                            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {
                                ZipEntry jsonZipEntry = new ZipEntry(ZIP_CONTENT_DECKS_JSON);
                                zipOutputStream.putNextEntry(jsonZipEntry);

                                zipOutputStream.write("[".getBytes(StandardCharsets.UTF_8));
                                boolean firstDeck = true;
                                for (Deck deck : deckList) {
                                    if (!firstDeck) {
                                        zipOutputStream.write(",".getBytes(StandardCharsets.UTF_8));
                                    }
                                    firstDeck = false;
                                    List<Card> cardList = cardsByDeckId.getOrDefault(deck.id, new ArrayList<>());
                                    DeckModel deckModel = new DeckModel(deck, cardList);
                                    zipOutputStream.write(deckModel.toJson().toString().getBytes(StandardCharsets.UTF_8));
                                }
                                zipOutputStream.write("]".getBytes(StandardCharsets.UTF_8));
                                zipOutputStream.closeEntry();

                                for (Card card : allCards) {
                                    if (card.questionImage != null) {
                                        File questionImage = mFileHelper.getCardQuestionImage(card.questionImage);
                                        if (questionImage != null && questionImage.exists() && questionImage.canRead()) {
                                            ZipEntry questionImageZip = new ZipEntry(ZIP_CONTENT_IMAGE_QUESTION_DIR + card.questionImage);
                                            zipOutputStream.putNextEntry(questionImageZip);
                                            try (FileInputStream fis = new FileInputStream(questionImage)) {
                                                mFileHelper.copyStream(fis, zipOutputStream);
                                            }
                                            zipOutputStream.closeEntry();
                                        }
                                    }
                                    if (card.answerImage != null) {
                                        File answerImage = mFileHelper.getCardAnswerImage(card.answerImage);
                                        if (answerImage != null && answerImage.exists() && answerImage.canRead()) {
                                            ZipEntry answerImageZip = new ZipEntry(ZIP_CONTENT_IMAGE_ANSWER_DIR + card.answerImage);
                                            zipOutputStream.putNextEntry(answerImageZip);
                                            try (FileInputStream fis = new FileInputStream(answerImage)) {
                                                mFileHelper.copyStream(fis, zipOutputStream);
                                            }
                                            zipOutputStream.closeEntry();
                                        }
                                    }
                                    if (card.questionVoice != null) {
                                        File file = mFileHelper.getCardQuestionVoice(card.questionVoice);
                                        if (file != null && file.exists() && file.canRead()) {
                                            ZipEntry zipEntry = new ZipEntry(ZIP_CONTENT_VOICE_QUESTION_DIR + card.questionVoice);
                                            zipOutputStream.putNextEntry(zipEntry);
                                            try (FileInputStream fis = new FileInputStream(file)) {
                                                mFileHelper.copyStream(fis, zipOutputStream);
                                            }
                                            zipOutputStream.closeEntry();
                                        }
                                    }
                                }
                            }
                            return zipFile;
                        } catch (IOException e) {
                            mLogger.d(TAG, e.getMessage(), e);
                            if (zipFile.exists()) {
                                zipFile.delete();
                            }
                            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_create_file));
                        }
                    } else {
                        throw new ValidationException(mAppContext.getString(R.string.error_no_deck_selected));
                    }
                })
        );
    }

    public Single<List<DeckModel>> importFile(File file) {
        if (file.getName().toLowerCase().endsWith(".apkg")) {
            return Single.fromFuture(
                    mExecutorService.submit(() -> mAnkiImporter.importApkg(file))
            );
        }
        return Single.fromFuture(
                mExecutorService.submit(() -> {
                    try (ZipFile zipFile = new ZipFile(file)) {
                        Enumeration<? extends ZipEntry> zipEntryEnumeration = zipFile.entries();
                        List<DeckModel> deckModelList = new ArrayList<>();
                        while (zipEntryEnumeration.hasMoreElements()) {
                            ZipEntry zipEntry = zipEntryEnumeration.nextElement();
                            // process deck json
                            if (zipEntry.getName().equals(ZIP_CONTENT_DECKS_JSON)) {
                                InputStream is = zipFile.getInputStream(zipEntry);
                                List<DeckModel> deckModelsFromJson = getDeckModelsFromJson(is);
                                deckModelList.addAll(deckModelsFromJson);
                            }
                            if (zipEntry.getName().startsWith(ZIP_CONTENT_IMAGE_QUESTION_DIR)) {
                                String fileName = zipEntry.getName().substring(ZIP_CONTENT_IMAGE_QUESTION_DIR.length() - 1);
                                File imageTempFile = mFileHelper.createImageTempFile();
                                try (BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(imageTempFile))) {
                                    mFileHelper.copyStream(bis, bos);
                                }
                                mFileHelper.createCardQuestionImage(imageTempFile, fileName);
                                mFileHelper.createCardQuestionImageThumbnail(Uri.fromFile(imageTempFile), fileName);
                            }
                            if (zipEntry.getName().startsWith(ZIP_CONTENT_IMAGE_ANSWER_DIR)) {
                                String fileName = zipEntry.getName().substring(ZIP_CONTENT_IMAGE_ANSWER_DIR.length() - 1);
                                File imageTempFile = mFileHelper.createImageTempFile();
                                try (BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(imageTempFile))) {
                                    mFileHelper.copyStream(bis, bos);
                                }
                                mFileHelper.createCardAnswerImage(imageTempFile, fileName);
                                mFileHelper.createCardAnswerImageThumbnail(Uri.fromFile(imageTempFile), fileName);
                            }
                            if (zipEntry.getName().startsWith(ZIP_CONTENT_VOICE_QUESTION_DIR)) {
                                String fileName = zipEntry.getName().substring(ZIP_CONTENT_VOICE_QUESTION_DIR.length() - 1);
                                File tempFile = mFileHelper.createTempFile(fileName);
                                try (BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                                    mFileHelper.copyStream(bis, bos);
                                }
                                mFileHelper.createCardQuestionVoice(tempFile, fileName);
                            }
                        }

                        if (!deckModelList.isEmpty()) {
                            mDeckDao.importDecks(deckModelList);
                        }
                        return deckModelList;
                    } catch (ZipException e) {
                        mLogger.d(TAG, "Not a zip file try json");
                        // to support old file format Decks.json instead of zip
                        try (FileInputStream fis = new FileInputStream(file)) {
                            List<DeckModel> deckModelList = getDeckModelsFromJson(fis);
                            if (!deckModelList.isEmpty()) {
                                mDeckDao.importDecks(deckModelList);
                            }
                            return deckModelList;
                        } catch (Exception exception) {
                            mLogger.d(TAG, exception.getMessage(), e);
                            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_parse_file));
                        }
                    } catch (FileNotFoundException e) {
                        mLogger.d(TAG, e.getMessage(), e);
                        throw new ValidationException(mAppContext.getString(R.string.error_failed_to_open_file));
                    } catch (Exception e) {
                        mLogger.d(TAG, e.getMessage(), e);
                        throw new ValidationException(mAppContext.getString(R.string.error_failed_to_parse_file));
                    }
                })
        );
    }

    @NonNull
    private List<DeckModel> getDeckModelsFromJson(InputStream is) throws IOException, JSONException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is))) {
            String jsonString = bufferedReader.readLine();
            JSONArray jsonArray = new JSONArray(jsonString);
            int size = jsonArray.length();
            List<DeckModel> deckModelsFromJson = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                DeckModel deckModel = new DeckModel();
                deckModel.fromJson(jsonObject);
                deckModelsFromJson.add(deckModel);
            }
            return deckModelsFromJson;
        }
    }

    public Single<List<DeckModel>> importFile(Uri uri) {
        return Single.fromFuture(
                mExecutorService.submit(() -> {
                    File file = mFileHelper
                            .createTempFile("Deck-Import", uri);
                    return this.importFile(file).blockingGet();
                })
        );
    }
}
