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
import android.net.Uri;
import android.text.Html;
import android.text.Spanned;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.anki.ApkgParser;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiCard;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiDeck;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNote;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNotetype;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class AnkiImporter {
    private static final String TAG = AnkiImporter.class.getName();
    private static final String FIELD_SEPARATOR = "\u001f";

    protected Context mAppContext;
    protected ILogger mLogger;
    protected DeckDao mDeckDao;
    protected FileHelper mFileHelper;

    public AnkiImporter(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.get(ILogger.class);
        mDeckDao = provider.get(DeckDao.class);
        mFileHelper = provider.get(FileHelper.class);
    }

    public List<DeckModel> importApkg(File apkgFile) {
        File tempDir = null;
        try {
            if (!apkgFile.getName().toLowerCase().endsWith(".apkg")) {
                throw new ValidationException(mAppContext.getString(R.string.error_invalid_apkg));
            }

            tempDir = new File(mAppContext.getCacheDir(), "anki_import_" + System.currentTimeMillis());
            tempDir.mkdirs();

            SQLiteDatabase db = ApkgParser.extractAndReadDatabase(apkgFile, tempDir);
            Map<String, File> mediaFiles = ApkgParser.extractMediaFiles(apkgFile, tempDir);
            Map<String, String> mediaMapping;
            try (ZipFile zipFile = new ZipFile(apkgFile)) {
                mediaMapping = ApkgParser.parseMediaJson(zipFile);
            }

            List<AnkiNotetype> notetypes;
            List<AnkiNote> notes = new ArrayList<>();
            List<AnkiCard> cards = new ArrayList<>();
            List<AnkiDeck> decks;
            
            try {
                notetypes = ApkgParser.readNotetypes(db);
                decks = ApkgParser.readDecks(db);

                for (AnkiNotetype notetype : notetypes) {
                    if (ApkgParser.isBasicNotetype(notetype)) {
                        List<AnkiNote> notetypeNotes = ApkgParser.readNotes(db, notetype.id);
                        notes.addAll(notetypeNotes);
                    } else {
                        mLogger.d(TAG, "Skipping non-Basic notetype: " + notetype.name);
                    }
                }

                if (notes.isEmpty()) {
                    throw new ValidationException("No Basic cards found in APKG file");
                }

                List<Long> noteIds = new ArrayList<>();
                for (AnkiNote note : notes) {
                    noteIds.add(note.id);
                }
                cards.addAll(ApkgParser.readCards(db, noteIds));
            } finally {
                db.close();
            }

            List<Deck> existingDecks = mDeckDao.getAllDecks();
            Set<String> existingNames = new HashSet<>();
            for (Deck deck : existingDecks) {
                existingNames.add(deck.name);
            }

            Map<Long, String> deckIdToNameMap = new HashMap<>();
            Map<Long, String> deckIdToResolvedNameMap = new HashMap<>();
            for (AnkiDeck ankiDeck : decks) {
                String flattenedName = flattenDeckName(ankiDeck.name);
                String resolvedName = resolveDeckNameConflict(flattenedName, existingNames);
                deckIdToNameMap.put(ankiDeck.id, ankiDeck.name);
                deckIdToResolvedNameMap.put(ankiDeck.id, resolvedName);
                existingNames.add(resolvedName);
            }

            Map<Long, DeckModel> deckModelMap = new HashMap<>();
            for (AnkiNote note : notes) {
                String[] fields = note.flds.split(FIELD_SEPARATOR);
                if (fields.length < 1) {
                    mLogger.d(TAG, "Skipping note with no fields");
                    continue;
                }

                String field1 = fields.length >= 1 ? fields[0] : "";
                String field2 = fields.length >= 2 ? fields[1] : "";

                String questionText = parseFieldText(field1);
                String questionImage = parseFieldImage(field1);
                String questionVoice = parseFieldVoice(field1);

                String answerText = parseFieldText(field2);
                String answerImage = parseFieldImage(field2);
                String answerVoice = parseFieldVoice(field2);

                for (AnkiCard ankiCard : cards) {
                    if (ankiCard.nid != note.id) {
                        continue;
                    }

                    String deckName = deckIdToResolvedNameMap.get(ankiCard.did);
                    if (deckName == null) {
                        deckName = "Imported Deck";
                    }

                    DeckModel deckModel = deckModelMap.get(ankiCard.did);
                    if (deckModel == null) {
                        Deck deck = new Deck();
                        deck.name = deckName;
                        deckModel = new DeckModel(deck, new ArrayList<>());
                        deckModelMap.put(ankiCard.did, deckModel);
                    }

                    Card card = new Card();
                    card.question = questionText;
                    card.answer = answerText;
                    card.questionImage = questionImage != null ? questionImage : null;
                    card.answerImage = answerImage != null ? answerImage : null;
                    card.questionVoice = questionVoice != null ? questionVoice : null;
                    card.answerVoice = answerVoice != null ? answerVoice : null;
                    card.ordinal = ankiCard.ord;
                    card.isReversibleQA = false;
                    card.isReversed = false;

                    deckModel.getCardList().add(card);
                }
            }

            copyMediaToAppPaths(mediaFiles, mediaMapping, deckModelMap);
            generateThumbnailsForMedia(deckModelMap);

            for (DeckModel deckModel : deckModelMap.values()) {
                deckModel.getCardList().sort((c1, c2) -> Long.compare(c1.ordinal, c2.ordinal));
            }

            List<DeckModel> result = new ArrayList<>(deckModelMap.values());
            return result;
        } catch (IOException | JSONException e) {
            mLogger.e(TAG, "Failed to import APKG", e);
            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_parse_file));
        } finally {
            if (tempDir != null) {
                deleteDirectory(tempDir);
            }
        }
    }

    private String parseFieldText(String htmlField) {
        if (htmlField == null || htmlField.isEmpty()) {
            return "";
        }

        String text = htmlField;
        text = text.replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n");

        Spanned spanned = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
        String plainText = spanned.toString();

        plainText = Normalizer.normalize(plainText, Normalizer.Form.NFC);

        plainText = plainText.replaceAll("\\[sound:[^\\]]+\\]", "");
        plainText = plainText.replace("\uFFFC", "");

        return plainText;
    }

    private String parseFieldImage(String htmlField) {
        if (htmlField == null || htmlField.isEmpty()) {
            return null;
        }

        Pattern imgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = imgPattern.matcher(htmlField);
        if (matcher.find()) {
            String imageName = matcher.group(1);
            return imageName;
        }
        return null;
    }

    private String parseFieldVoice(String htmlField) {
        if (htmlField == null || htmlField.isEmpty()) {
            return null;
        }

        Pattern soundPattern = Pattern.compile("\\[sound:([^\\]]+)\\]");
        Matcher matcher = soundPattern.matcher(htmlField);
        if (matcher.find()) {
            String voiceName = matcher.group(1);
            return voiceName;
        }
        return null;
    }

    private String flattenDeckName(String ankiDeckName) {
        return ankiDeckName.replace("::", " - ");
    }

    private String resolveDeckNameConflict(String baseName, Set<String> existingNames) {
        String resolvedName = baseName;
        int suffix = 2;
        while (existingNames.contains(resolvedName)) {
            resolvedName = baseName + " (" + suffix + ")";
            suffix++;
        }
        return resolvedName;
    }

    private void copyMediaToAppPaths(Map<String, File> mediaFiles, Map<String, String> mediaMapping, Map<Long, DeckModel> deckModelMap) throws IOException {
        for (DeckModel deckModel : deckModelMap.values()) {
            for (Card card : deckModel.getCardList()) {
                if (card.questionImage != null) {
                    String numericKey = findNumericKeyForMedia(card.questionImage, mediaMapping);
                    if (numericKey != null && mediaFiles.containsKey(numericKey)) {
                        File sourceFile = mediaFiles.get(numericKey);
                        String extension = getFileExtension(card.questionImage);
                        if (extension == null) {
                            extension = ".jpg";
                        }
                        String newFileName = generateUniqueFileName() + extension;
                        mFileHelper.createCardQuestionImage(sourceFile, newFileName);
                        card.questionImage = newFileName;
                    } else {
                        mLogger.w(TAG, "Missing media file for question image: " + card.questionImage);
                        card.questionImage = null;
                    }
                }

                if (card.answerImage != null) {
                    String numericKey = findNumericKeyForMedia(card.answerImage, mediaMapping);
                    if (numericKey != null && mediaFiles.containsKey(numericKey)) {
                        File sourceFile = mediaFiles.get(numericKey);
                        String extension = getFileExtension(card.answerImage);
                        if (extension == null) {
                            extension = ".jpg";
                        }
                        String newFileName = generateUniqueFileName() + extension;
                        mFileHelper.createCardAnswerImage(sourceFile, newFileName);
                        card.answerImage = newFileName;
                    } else {
                        mLogger.w(TAG, "Missing media file for answer image: " + card.answerImage);
                        card.answerImage = null;
                    }
                }

                if (card.questionVoice != null) {
                    String numericKey = findNumericKeyForMedia(card.questionVoice, mediaMapping);
                    if (numericKey != null && mediaFiles.containsKey(numericKey)) {
                        File sourceFile = mediaFiles.get(numericKey);
                        String newFileName = generateUniqueFileName();
                        mFileHelper.createCardQuestionVoice(sourceFile, newFileName);
                        card.questionVoice = newFileName;
                    } else {
                        mLogger.w(TAG, "Missing media file for question voice: " + card.questionVoice);
                        card.questionVoice = null;
                    }
                }

                if (card.answerVoice != null) {
                    String numericKey = findNumericKeyForMedia(card.answerVoice, mediaMapping);
                    if (numericKey != null && mediaFiles.containsKey(numericKey)) {
                        File sourceFile = mediaFiles.get(numericKey);
                        String newFileName = generateUniqueFileName();
                        mFileHelper.createCardAnswerVoice(sourceFile, newFileName);
                        card.answerVoice = newFileName;
                    } else {
                        mLogger.w(TAG, "Missing media file for answer voice: " + card.answerVoice);
                        card.answerVoice = null;
                    }
                }
            }
        }
    }

    private String findNumericKeyForMedia(String mediaName, Map<String, String> mediaMapping) {
        for (Map.Entry<String, String> entry : mediaMapping.entrySet()) {
            if (entry.getValue().equals(mediaName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return "." + fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return null;
    }

    private String generateUniqueFileName() {
        return java.util.UUID.randomUUID().toString();
    }

    private void generateThumbnailsForMedia(Map<Long, DeckModel> deckModelMap) {
        for (DeckModel deckModel : deckModelMap.values()) {
            for (Card card : deckModel.getCardList()) {
                if (card.questionImage != null) {
                    try {
                        File imageFile = mFileHelper.getCardQuestionImage(card.questionImage);
                        Uri uri = Uri.fromFile(imageFile);
                        mFileHelper.createCardQuestionImageThumbnail(uri, card.questionImage);
                    } catch (Exception e) {
                        mLogger.e(TAG, "Failed to create thumbnail for question image", e);
                    }
                }

                if (card.answerImage != null) {
                    try {
                        File imageFile = mFileHelper.getCardAnswerImage(card.answerImage);
                        Uri uri = Uri.fromFile(imageFile);
                        mFileHelper.createCardAnswerImageThumbnail(uri, card.answerImage);
                    } catch (Exception e) {
                        mLogger.e(TAG, "Failed to create thumbnail for answer image", e);
                    }
                }
            }
        }
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
