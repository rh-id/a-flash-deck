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

package m.co.rh.id.a_flash_deck.app.provider.component;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.Spanned;

import androidx.core.text.HtmlCompat;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.anki.AnkiUnsupportedPackageException;
import m.co.rh.id.a_flash_deck.app.anki.ApkgParser;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiCard;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiDeck;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiImportSummary;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNote;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNotetype;
import m.co.rh.id.a_flash_deck.base.dao.AnkiNoteGuidDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.DeckImportPayload;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;
import m.co.rh.id.a_flash_deck.base.provider.CardMediaStore;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.app.provider.command.ImportResult;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class AnkiImporter {
    private static final String TAG = AnkiImporter.class.getName();
    private static final String FIELD_SEPARATOR = "\u001f";

    private final Pattern mImgPattern;
    private final Pattern mSoundPattern;

    protected Context mAppContext;
    protected ILogger mLogger;
    protected DeckDao mDeckDao;
    protected AnkiNoteGuidDao mAnkiNoteGuidDao;
    protected FileHelper mFileHelper;
    protected CardMediaStore mCardMediaStore;

    public AnkiImporter(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.get(ILogger.class);
        mDeckDao = provider.get(DeckDao.class);
        mAnkiNoteGuidDao = provider.get(AnkiNoteGuidDao.class);
        mFileHelper = provider.get(FileHelper.class);
        mCardMediaStore = provider.get(CardMediaStore.class);
        mImgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        mSoundPattern = Pattern.compile("\\[sound:([^\\]]+)\\]");
    }

    public ImportResult importApkg(File apkgFile) {
        File tempDir = null;
        try {
            if (!apkgFile.getName().toLowerCase().endsWith(".apkg")) {
                throw new ValidationException(mAppContext.getString(R.string.error_invalid_apkg));
            }

            tempDir = new File(mAppContext.getCacheDir(), "anki_import_" + System.currentTimeMillis());
            tempDir.mkdirs();

            SQLiteDatabase db = ApkgParser.extractAndReadDatabase(apkgFile, tempDir);
            // media extraction handles both legacy JSON manifests and modern
            // zstd MediaEntries protobuf manifests
            ApkgParser.MediaData mediaData = ApkgParser.extractMedia(apkgFile, tempDir);

            List<AnkiNotetype> notetypes;
            List<AnkiDeck> decks;
            // notes of Basic variant notetypes, the only notes importable
            List<AnkiNote> importedNotes = new ArrayList<>();
            // reversibility per imported note id, derived from its notetype name
            Map<Long, Boolean> reversibleNoteIds = new HashMap<>();
            List<AnkiCard> cards = new ArrayList<>();
            AnkiImportSummary summary = new AnkiImportSummary();

            try {
                notetypes = ApkgParser.readNotetypes(db);
                decks = ApkgParser.readDecks(db);

                for (AnkiNotetype notetype : notetypes) {
                    if (ApkgParser.isBasicNotetype(notetype)) {
                        // notes are only materialized for importable
                        // notetypes; skipped ones use COUNT(*) so a huge
                        // cloze/unsupported package never loads all of its
                        // note field text into memory
                        List<AnkiNote> notetypeNotes = ApkgParser.readNotes(db, notetype.id);
                        importedNotes.addAll(notetypeNotes);
                        boolean reversible = isReversedNotetype(notetype);
                        List<Long> notetypeNoteIds = new ArrayList<>();
                        for (AnkiNote note : notetypeNotes) {
                            reversibleNoteIds.put(note.id, reversible);
                            notetypeNoteIds.add(note.id);
                        }
                        cards.addAll(ApkgParser.readCards(db, notetypeNoteIds));
                    } else if (ApkgParser.isClozeNotetype(notetype)) {
                        int noteCount = ApkgParser.countNotes(db, notetype.id);
                        mLogger.i(TAG, "Skipping cloze notetype: " + notetype.name
                                + " (" + noteCount + " notes)");
                        summary.skippedNotetypes.add(new AnkiImportSummary.SkippedNotetype(
                                notetype.name, noteCount, "cloze"));
                    } else {
                        int noteCount = ApkgParser.countNotes(db, notetype.id);
                        mLogger.i(TAG, "Skipping unsupported notetype: " + notetype.name
                                + " (" + noteCount + " notes)");
                        summary.skippedNotetypes.add(new AnkiImportSummary.SkippedNotetype(
                                notetype.name, noteCount, "unsupported"));
                    }
                }
            } finally {
                db.close();
            }

            List<Deck> existingDecks = mDeckDao.getAllDecks();
            Set<String> existingNames = new HashSet<>();
            for (Deck deck : existingDecks) {
                existingNames.add(deck.name);
            }

            Map<Long, String> deckIdToResolvedNameMap = new HashMap<>();
            for (AnkiDeck ankiDeck : decks) {
                String flattenedName = flattenDeckName(ankiDeck.name);
                String resolvedName = resolveDeckNameConflict(flattenedName, existingNames);
                deckIdToResolvedNameMap.put(ankiDeck.id, resolvedName);
                existingNames.add(resolvedName);
            }

            // map each imported note to its first card row (lowest id), deck
            // and ordinal are taken from it; every note produces exactly one
            // app card even when the notetype generates multiple card rows
            // (e.g. "Basic (and reversed card)")
            Map<Long, AnkiCard> firstCardByNoteId = new HashMap<>();
            for (AnkiCard ankiCard : cards) {
                AnkiCard existing = firstCardByNoteId.get(ankiCard.nid);
                if (existing == null || ankiCard.id < existing.id) {
                    firstCardByNoteId.put(ankiCard.nid, ankiCard);
                }
            }

            // guids of all importable notes, batched existence check against
            // previously imported notes for duplicate detection
            List<String> noteGuids = new ArrayList<>();
            for (AnkiNote note : importedNotes) {
                noteGuids.add(note.guid);
            }
            Set<String> existingNoteGuids = new HashSet<>(mAnkiNoteGuidDao.findExistingGuids(noteGuids));
            // guids seen during this import, the same guid occurring twice
            // within the file is a duplicate as well
            Set<String> seenNoteGuids = new HashSet<>();
            // source note guid per parsed card, kept by identity so the card
            // list can be re-sorted below without losing the alignment
            Map<Card, String> noteGuidByCard = new IdentityHashMap<>();

            Map<Long, DeckModel> deckModelMap = new HashMap<>();
            for (AnkiNote note : importedNotes) {
                if (note.guid != null && !note.guid.isEmpty()) {
                    if (existingNoteGuids.contains(note.guid) || !seenNoteGuids.add(note.guid)) {
                        mLogger.i(TAG, "Skipping duplicate note, guid: " + note.guid);
                        summary.duplicateNotesSkipped++;
                        continue;
                    }
                }
                if (note.flds == null || note.flds.isEmpty()) {
                    mLogger.d(TAG, "Skipping note with empty fields");
                    continue;
                }
                String[] fields = note.flds.split(FIELD_SEPARATOR);
                if (fields.length < 1) {
                    mLogger.d(TAG, "Skipping note with no fields");
                    continue;
                }

                AnkiCard noteCard = firstCardByNoteId.get(note.id);
                if (noteCard == null) {
                    mLogger.d(TAG, "Skipping note without card row, note id: " + note.id);
                    continue;
                }

                String deckName = deckIdToResolvedNameMap.get(noteCard.did);
                if (deckName == null) {
                    deckName = mAppContext.getString(R.string.imported_deck_default_name);
                }

                DeckModel deckModel = deckModelMap.get(noteCard.did);
                if (deckModel == null) {
                    Deck deck = new Deck();
                    deck.name = deckName;
                    deckModel = new DeckModel(deck, new ArrayList<>());
                    deckModelMap.put(noteCard.did, deckModel);
                }

                String field1 = fields[0];
                String field2 = fields.length >= 2 ? fields[1] : "";

                String questionText = parseFieldText(field1);
                String questionImage = parseFieldImage(field1, summary);
                String questionVoice = parseFieldVoice(field1, summary);

                String answerText = parseFieldText(field2);
                String answerImage = parseFieldImage(field2, summary);
                String answerVoice = parseFieldVoice(field2, summary);

                Card card = new Card();
                card.question = questionText;
                card.answer = answerText;
                card.questionImage = questionImage;
                card.answerImage = answerImage;
                card.questionVoice = questionVoice;
                card.answerVoice = answerVoice;
                card.ordinal = noteCard.ord;
                card.isReversibleQA = reversibleNoteIds.getOrDefault(note.id, false);
                card.isReversed = false;

                deckModel.getCardList().add(card);
                noteGuidByCard.put(card, note.guid);
                summary.notesImported++;
                summary.cardsImported++;
            }

            // a package whose notes are all duplicates was still processed
            // successfully (nothing new to import), only fail when there is
            // nothing importable at all
            if (summary.notesImported == 0 && summary.duplicateNotesSkipped == 0
                    && deckModelMap.isEmpty()) {
                throw new ValidationException(mAppContext.getString(R.string.error_nothing_imported));
            }

            copyMediaToAppPaths(mediaData.files, mediaData.namesByKey, deckModelMap);
            generateThumbnailsForMedia(deckModelMap);

            for (DeckModel deckModel : deckModelMap.values()) {
                deckModel.getCardList().sort((c1, c2) -> Long.compare(c1.ordinal, c2.ordinal));
            }

            List<DeckImportPayload> payloads = new ArrayList<>();
            for (DeckModel deckModel : deckModelMap.values()) {
                List<String> cardGuids = new ArrayList<>();
                for (Card card : deckModel.getCardList()) {
                    cardGuids.add(noteGuidByCard.get(card));
                }
                payloads.add(new DeckImportPayload(deckModel, cardGuids));
            }

            summary.decksImported = deckModelMap.size();
            return new ImportResult(payloads, summary, true);
        } catch (AnkiUnsupportedPackageException e) {
            mLogger.e(TAG, "Failed to import APKG, unsupported package", e);
            String message = e.reason == AnkiUnsupportedPackageException.Reason.MODERN_ZSTD
                    ? mAppContext.getString(R.string.error_anki_modern_package)
                    : mAppContext.getString(R.string.error_anki_decoy_package);
            throw new ValidationException(message);
        } catch (IOException | JSONException e) {
            mLogger.e(TAG, "Failed to import APKG", e);
            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_parse_file));
        } finally {
            if (tempDir != null) {
                mFileHelper.deleteDirectory(tempDir);
            }
        }
    }

    /**
     * Returns true when the Basic variant notetype name contains "reversed"
     * (case-insensitive, e.g. "Basic (and reversed card)"). Such notes get a
     * second card in Anki asking the back side first, which maps to the app's
     * reversible question card.
     */
    private boolean isReversedNotetype(AnkiNotetype notetype) {
        return notetype.name != null && notetype.name.toLowerCase().contains("reversed");
    }

    private String parseFieldText(String htmlField) {
        if (htmlField == null || htmlField.isEmpty()) {
            return "";
        }

        String text = htmlField;
        text = text.replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n");

        Spanned spanned = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY);
        String plainText = spanned.toString();

        plainText = Normalizer.normalize(plainText, Normalizer.Form.NFC);

        plainText = plainText.replaceAll("\\[sound:[^\\]]+\\]", "");
        plainText = plainText.replace("\uFFFC", "");

        return plainText;
    }

    private String parseFieldImage(String htmlField, AnkiImportSummary summary) {
        if (htmlField == null || htmlField.isEmpty()) {
            return null;
        }

        Matcher matcher = mImgPattern.matcher(htmlField);
        if (matcher.find()) {
            String imageName = matcher.group(1);
            // count extra images, only the first image per card side is supported
            while (matcher.find()) {
                summary.droppedMediaCount++;
            }
            return imageName;
        }
        return null;
    }

    private String parseFieldVoice(String htmlField, AnkiImportSummary summary) {
        if (htmlField == null || htmlField.isEmpty()) {
            return null;
        }

        Matcher matcher = mSoundPattern.matcher(htmlField);
        if (matcher.find()) {
            String voiceName = matcher.group(1);
            // count extra sounds, only the first sound per card side is supported
            while (matcher.find()) {
                summary.droppedMediaCount++;
            }
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
        Map<String, String> inverseMediaMap = new HashMap<>();
        for (Map.Entry<String, String> entry : mediaMapping.entrySet()) {
            inverseMediaMap.put(entry.getValue(), entry.getKey());
        }

        for (DeckModel deckModel : deckModelMap.values()) {
            for (Card card : deckModel.getCardList()) {
                if (card.questionImage != null) {
                    String numericKey = inverseMediaMap.get(card.questionImage);
                    if (numericKey != null && mediaFiles.containsKey(numericKey)) {
                        File sourceFile = mediaFiles.get(numericKey);
                        String extension = getFileExtension(card.questionImage);
                        if (extension == null) {
                            extension = ".jpg";
                        }
                        String newFileName = generateUniqueFileName() + extension;
                        mCardMediaStore.createCardQuestionImage(sourceFile, newFileName);
                        card.questionImage = newFileName;
                    } else {
                        mLogger.w(TAG, "Missing media file for question image: " + card.questionImage);
                        card.questionImage = null;
                    }
                }

                if (card.answerImage != null) {
                    String numericKey = inverseMediaMap.get(card.answerImage);
                    if (numericKey != null && mediaFiles.containsKey(numericKey)) {
                        File sourceFile = mediaFiles.get(numericKey);
                        String extension = getFileExtension(card.answerImage);
                        if (extension == null) {
                            extension = ".jpg";
                        }
                        String newFileName = generateUniqueFileName() + extension;
                        mCardMediaStore.createCardAnswerImage(sourceFile, newFileName);
                        card.answerImage = newFileName;
                    } else {
                        mLogger.w(TAG, "Missing media file for answer image: " + card.answerImage);
                        card.answerImage = null;
                    }
                }

                if (card.questionVoice != null) {
                    String numericKey = inverseMediaMap.get(card.questionVoice);
                    if (numericKey != null && mediaFiles.containsKey(numericKey)) {
                        File sourceFile = mediaFiles.get(numericKey);
                        String newFileName = generateUniqueFileName();
                        mCardMediaStore.createCardQuestionVoice(sourceFile, newFileName);
                        card.questionVoice = newFileName;
                    } else {
                        mLogger.w(TAG, "Missing media file for question voice: " + card.questionVoice);
                        card.questionVoice = null;
                    }
                }

                if (card.answerVoice != null) {
                    String numericKey = inverseMediaMap.get(card.answerVoice);
                    if (numericKey != null && mediaFiles.containsKey(numericKey)) {
                        File sourceFile = mediaFiles.get(numericKey);
                        String newFileName = generateUniqueFileName();
                        mCardMediaStore.createCardAnswerVoice(sourceFile, newFileName);
                        card.answerVoice = newFileName;
                    } else {
                        mLogger.w(TAG, "Missing media file for answer voice: " + card.answerVoice);
                        card.answerVoice = null;
                    }
                }
            }
        }
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
        return UUID.randomUUID().toString();
    }

    private void generateThumbnailsForMedia(Map<Long, DeckModel> deckModelMap) {
        for (DeckModel deckModel : deckModelMap.values()) {
            for (Card card : deckModel.getCardList()) {
                if (card.questionImage != null) {
                    try {
                        File imageFile = mCardMediaStore.getCardQuestionImage(card.questionImage);
                        Uri uri = Uri.fromFile(imageFile);
                        mCardMediaStore.createCardQuestionImageThumbnail(uri, card.questionImage);
                    } catch (Exception e) {
                        mLogger.e(TAG, "Failed to create thumbnail for question image", e);
                    }
                }

                if (card.answerImage != null) {
                    try {
                        File imageFile = mCardMediaStore.getCardAnswerImage(card.answerImage);
                        Uri uri = Uri.fromFile(imageFile);
                        mCardMediaStore.createCardAnswerImageThumbnail(uri, card.answerImage);
                    } catch (Exception e) {
                        mLogger.e(TAG, "Failed to create thumbnail for answer image", e);
                    }
                }
            }
        }
    }
}
