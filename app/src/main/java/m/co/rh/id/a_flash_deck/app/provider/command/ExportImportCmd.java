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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class ExportImportCmd {
    private static final String TAG = ExportImportCmd.class.getName();
    private static final String ZIP_CONTENT_DECKS_JSON = "Decks.json";
    private static final String ZIP_CONTENT_IMAGE_QUESTION_DIR = "media/image/question/";
    private static final String ZIP_CONTENT_IMAGE_ANSWER_DIR = "media/image/answer/";

    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<ILogger> mLogger;
    protected ProviderValue<DeckDao> mDeckDao;
    protected ProviderValue<FileHelper> mFileHelper;

    public ExportImportCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mLogger = provider.lazyGet(ILogger.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mFileHelper = provider.lazyGet(FileHelper.class);
    }

    public Single<File> exportFile(List<Deck> deckList) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
                    if (!deckList.isEmpty()) {
                        try {
                            File zipFile = mFileHelper.get().createTempFile("Decks.zip");
                            ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile));
                            ZipEntry jsonZipEntry = new ZipEntry(ZIP_CONTENT_DECKS_JSON);
                            zipOutputStream.putNextEntry(jsonZipEntry);

                            JSONArray jsonArray = new JSONArray();
                            List<Card> allCards = new ArrayList<>();
                            for (Deck deck : deckList) {
                                List<Card> cardList = mDeckDao.get().getCardByDeckId(deck.id);
                                DeckModel deckModel = new DeckModel(deck, cardList);
                                jsonArray.put(deckModel.toJson());
                                allCards.addAll(cardList);
                            }
                            zipOutputStream.write(jsonArray.toString().getBytes());
                            zipOutputStream.closeEntry();

                            // next entries are images
                            for (Card card : allCards) {
                                if (card.questionImage != null) {
                                    File questionImage = mFileHelper.get().getCardQuestionImage(card.questionImage);
                                    ZipEntry questionImageZip = new ZipEntry(ZIP_CONTENT_IMAGE_QUESTION_DIR + card.questionImage);
                                    zipOutputStream.putNextEntry(questionImageZip);
                                    mFileHelper.get().copyStream(new FileInputStream(questionImage), zipOutputStream);
                                    zipOutputStream.closeEntry();
                                }
                                if (card.answerImage != null) {
                                    File answerImage = mFileHelper.get().getCardAnswerImage(card.answerImage);
                                    ZipEntry answerImageZip = new ZipEntry(ZIP_CONTENT_IMAGE_ANSWER_DIR + card.answerImage);
                                    zipOutputStream.putNextEntry(answerImageZip);
                                    mFileHelper.get().copyStream(new FileInputStream(answerImage), zipOutputStream);
                                    zipOutputStream.closeEntry();
                                }
                            }
                            zipOutputStream.close();
                            return zipFile;
                        } catch (IOException e) {
                            mLogger.get().d(TAG, e.getMessage(), e);
                            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_create_file));
                        }
                    } else {
                        throw new ValidationException(mAppContext.getString(R.string.error_no_deck_selected));
                    }
                })
        );
    }

    public Single<List<DeckModel>> importFile(File file) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
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
                                BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                File imageTempFile = mFileHelper.get().createImageTempFile();
                                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(imageTempFile));
                                mFileHelper.get().copyStream(bis, bos);
                                bis.close();
                                bos.close();
                                mFileHelper.get().createCardQuestionImage(imageTempFile, fileName);
                                mFileHelper.get().createCardQuestionImageThumbnail(Uri.fromFile(imageTempFile), fileName);
                            }
                            if (zipEntry.getName().startsWith(ZIP_CONTENT_IMAGE_ANSWER_DIR)) {
                                String fileName = zipEntry.getName().substring(ZIP_CONTENT_IMAGE_ANSWER_DIR.length() - 1);
                                BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                File imageTempFile = mFileHelper.get().createImageTempFile();
                                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(imageTempFile));
                                mFileHelper.get().copyStream(bis, bos);
                                bis.close();
                                bos.close();
                                mFileHelper.get().createCardAnswerImage(imageTempFile, fileName);
                                mFileHelper.get().createCardAnswerImageThumbnail(Uri.fromFile(imageTempFile), fileName);
                            }
                        }

                        if (!deckModelList.isEmpty()) {
                            mDeckDao.get().importDecks(deckModelList);
                        }
                        return deckModelList;
                    } catch (ZipException e) {
                        mLogger.get().d(TAG, "Not a zip file try json");
                        // to support old file format Decks.json instead of zip
                        try (FileInputStream fis = new FileInputStream(file)) {
                            List<DeckModel> deckModelList = getDeckModelsFromJson(fis);
                            if (!deckModelList.isEmpty()) {
                                mDeckDao.get().importDecks(deckModelList);
                            }
                            return deckModelList;
                        } catch (Exception exception) {
                            mLogger.get().d(TAG, exception.getMessage(), e);
                            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_parse_file));
                        }
                    } catch (FileNotFoundException e) {
                        mLogger.get().d(TAG, e.getMessage(), e);
                        throw new ValidationException(mAppContext.getString(R.string.error_failed_to_open_file));
                    } catch (Exception e) {
                        mLogger.get().d(TAG, e.getMessage(), e);
                        throw new ValidationException(mAppContext.getString(R.string.error_failed_to_parse_file));
                    }
                })
        );
    }

    @NonNull
    private List<DeckModel> getDeckModelsFromJson(InputStream is) throws IOException, JSONException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
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
