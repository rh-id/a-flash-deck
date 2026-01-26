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

package m.co.rh.id.a_flash_deck.app.provider.component;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.anki.ApkgGenerator;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class AnkiExporter {
    private static final String TAG = AnkiExporter.class.getName();
    private static final String FIELD_SEPARATOR = "\u001f";

    protected Context mAppContext;
    protected ILogger mLogger;
    protected DeckDao mDeckDao;
    protected FileHelper mFileHelper;

    public AnkiExporter(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.get(ILogger.class);
        mDeckDao = provider.get(DeckDao.class);
        mFileHelper = provider.get(FileHelper.class);
    }

    public File exportApkg(List<Deck> deckList) {
        File tempDir = null;
        File dbFile = null;
        try {
            if (deckList == null || deckList.isEmpty()) {
                throw new ValidationException(mAppContext.getString(R.string.error_no_deck_selected));
            }

            List<Long> deckIds = new ArrayList<>();
            for (Deck deck : deckList) {
                deckIds.add(deck.id);
            }

            List<Card> allCards = mDeckDao.findCardByDeckIds(deckIds);
            if (allCards.isEmpty()) {
                throw new ValidationException("No cards found in selected decks");
            }

            Map<String, Integer> mediaMap = buildMediaMap(allCards);

            tempDir = new File(mAppContext.getCacheDir(), "anki_export_" + System.currentTimeMillis());
            tempDir.mkdirs();

            dbFile = new File(tempDir, "collection.anki21");
            SQLiteDatabase db = ApkgGenerator.createTempDatabase(dbFile);
            ApkgGenerator.createTables(db);

            long notetypeId = insertBasicNotetypeAndGetId(db);

            Map<Long, Long> deckIdMap = new LinkedHashMap<>();
            for (Deck deck : deckList) {
                long newDeckId = ApkgGenerator.insertDeck(db, deck.name);
                deckIdMap.put(deck.id, newDeckId);
            }

            JSONObject decksJson = new JSONObject();
            for (Map.Entry<Long, Long> entry : deckIdMap.entrySet()) {
                Long originalId = entry.getKey();
                Long newId = entry.getValue();
                Deck deck = findDeckById(deckList, originalId);
                if (deck != null) {
                    JSONObject deckJson = new JSONObject();
                    deckJson.put("name", deck.name);
                    deckJson.put("conf", 1);
                    deckJson.put("usn", -1);
                    deckJson.put("mtime_secs", System.currentTimeMillis() / 1000);
                    decksJson.put(String.valueOf(newId), deckJson);
                }
            }
            ApkgGenerator.updateColDecks(db, decksJson.toString());

            db.beginTransaction();
            try {
                for (Card card : allCards) {
                    String guid = generateGuid();
                    String field1 = constructNoteField(card.question, card.questionImage, card.questionVoice);
                    String field2 = constructNoteField(card.answer, card.answerImage, card.answerVoice);

                    Long deckId = deckIdMap.get(card.deckId);
                    if (deckId == null) {
                        deckId = deckIdMap.values().iterator().next();
                    }

                    long noteId = ApkgGenerator.insertNote(db, guid, deckId, notetypeId, field1, field2);
                    ApkgGenerator.insertCard(db, noteId, deckId, card.ordinal);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            db.close();

            Map<String, File> mediaFiles = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : mediaMap.entrySet()) {
                String mediaName = entry.getKey();
                Integer numericId = entry.getValue();
                File sourceFile = findMediaFile(allCards, mediaName);
                if (sourceFile != null && sourceFile.exists()) {
                    mediaFiles.put(String.valueOf(numericId), sourceFile);
                } else {
                    mLogger.w(TAG, "Media file not found: " + mediaName);
                }
            }

            String mediaJson = ApkgGenerator.createMediaJson(mediaMap);

            String outputFileName = "deck_export.apkg";
            File outputFile = ApkgGenerator.generateApkg(dbFile, mediaFiles, mediaJson, outputFileName);

            return outputFile;
        } catch (IOException | JSONException e) {
            mLogger.e(TAG, "Failed to export APKG", e);
            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_create_file));
        } finally {
            if (tempDir != null) {
                deleteDirectory(tempDir);
            }
        }
    }

    private Map<String, Integer> buildMediaMap(List<Card> cards) {
        Map<String, Integer> mediaMap = new LinkedHashMap<>();
        int nextId = 0;

        for (Card card : cards) {
            if (card.questionImage != null && !card.questionImage.isEmpty() && !mediaMap.containsKey(card.questionImage)) {
                mediaMap.put(card.questionImage, nextId++);
            }
            if (card.answerImage != null && !card.answerImage.isEmpty() && !mediaMap.containsKey(card.answerImage)) {
                mediaMap.put(card.answerImage, nextId++);
            }
            if (card.questionVoice != null && !card.questionVoice.isEmpty() && !mediaMap.containsKey(card.questionVoice)) {
                mediaMap.put(card.questionVoice, nextId++);
            }
            if (card.answerVoice != null && !card.answerVoice.isEmpty() && !mediaMap.containsKey(card.answerVoice)) {
                mediaMap.put(card.answerVoice, nextId++);
            }
        }

        return mediaMap;
    }

    private String generateGuid() {
        return java.util.UUID.randomUUID().toString();
    }

    private String constructNoteField(String text, String image, String voice) {
        StringBuilder sb = new StringBuilder();
        if (text != null && !text.isEmpty()) {
            text = Normalizer.normalize(text, Normalizer.Form.NFC);
            sb.append(text);
        }
        if (image != null && !image.isEmpty()) {
            sb.append("<img src=\"").append(image).append("\">");
        }
        if (voice != null && !voice.isEmpty()) {
            sb.append("[sound:").append(voice).append("]");
        }
        return sb.toString();
    }

    private long insertBasicNotetypeAndGetId(SQLiteDatabase db) throws JSONException {
        long notetypeId = System.currentTimeMillis() + (long) (Math.random() * 1000);
        JSONObject model = new JSONObject();
        model.put("id", notetypeId);
        model.put("name", "Basic");
        model.put("type", 0);
        model.put("css", ".card { font-family: arial; font-size: 20px; text-align: center; color: black; background-color: white; }");
        model.put("latexPre", "[latex]");
        model.put("latexPost", "[/latex]");

        JSONArray flds = new JSONArray();
        JSONObject frontField = new JSONObject();
        frontField.put("name", "Front");
        frontField.put("ord", 0);
        frontField.put("sticky", false);
        flds.put(frontField);

        JSONObject backField = new JSONObject();
        backField.put("name", "Back");
        backField.put("ord", 1);
        backField.put("sticky", false);
        flds.put(backField);
        model.put("flds", flds);

        JSONArray tmpls = new JSONArray();
        JSONObject card1 = new JSONObject();
        card1.put("name", "Card 1");
        card1.put("qfmt", "{{Front}}");
        card1.put("afmt", "{{FrontSide}}<hr id=answer>{{Back}}");
        card1.put("ord", 0);
        tmpls.put(card1);

        JSONObject card2 = new JSONObject();
        card2.put("name", "Card 2");
        card2.put("qfmt", "{{Back}}");
        card2.put("afmt", "{{FrontSide}}<hr id=answer>{{Front}}");
        card2.put("ord", 1);
        tmpls.put(card2);

        model.put("tmpls", tmpls);

        JSONObject models = new JSONObject();
        models.put(String.valueOf(notetypeId), model);

        db.execSQL("UPDATE col SET models = ?", new Object[]{models.toString()});

        return notetypeId;
    }

    private Deck findDeckById(List<Deck> decks, Long deckId) {
        for (Deck deck : decks) {
            if (deck.id.equals(deckId)) {
                return deck;
            }
        }
        return null;
    }

    private File findMediaFile(List<Card> cards, String mediaName) {
        for (Card card : cards) {
            if (card.questionImage != null && card.questionImage.equals(mediaName)) {
                return mFileHelper.getCardQuestionImage(mediaName);
            }
            if (card.answerImage != null && card.answerImage.equals(mediaName)) {
                return mFileHelper.getCardAnswerImage(mediaName);
            }
            if (card.questionVoice != null && card.questionVoice.equals(mediaName)) {
                return mFileHelper.getCardQuestionVoice(mediaName);
            }
            if (card.answerVoice != null && card.answerVoice.equals(mediaName)) {
                return mFileHelper.getCardAnswerVoice(mediaName);
            }
        }
        return null;
    }

    private void deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
