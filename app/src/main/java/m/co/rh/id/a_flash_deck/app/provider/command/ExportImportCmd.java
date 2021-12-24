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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

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

    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<ILogger> mLogger;
    protected ProviderValue<DeckDao> mDeckDao;
    protected ProviderValue<FileHelper> mFileProvider;

    public ExportImportCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mLogger = provider.lazyGet(ILogger.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mFileProvider = provider.lazyGet(FileHelper.class);
    }

    public Single<File> exportFile(List<Deck> deckList) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
                    if (!deckList.isEmpty()) {
                        try {
                            File file = mFileProvider.get().createTempFile("Decks.json");
                            JSONArray jsonArray = new JSONArray();
                            for (Deck deck : deckList) {
                                List<Card> cardList = mDeckDao.get().getCardByDeckId(deck.id);
                                DeckModel deckModel = new DeckModel(deck, cardList);
                                jsonArray.put(deckModel.toJson());
                            }
                            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
                            bufferedWriter.write(jsonArray.toString());
                            bufferedWriter.close();
                            return file;
                        } catch (IOException e) {
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
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
                        String jsonString = bufferedReader.readLine();
                        JSONArray jsonArray = new JSONArray(jsonString);
                        List<DeckModel> deckModelList = new ArrayList<>();
                        int size = jsonArray.length();
                        for (int i = 0; i < size; i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            DeckModel deckModel = new DeckModel();
                            deckModel.fromJson(jsonObject);
                            deckModelList.add(deckModel);
                        }
                        mDeckDao.get().importDecks(deckModelList);
                        return deckModelList;
                    } catch (FileNotFoundException e) {
                        throw new ValidationException(mAppContext.getString(R.string.error_failed_to_open_file));
                    } catch (Exception e) {
                        throw new ValidationException(mAppContext.getString(R.string.error_failed_to_parse_file));
                    }
                })
        );
    }
}
