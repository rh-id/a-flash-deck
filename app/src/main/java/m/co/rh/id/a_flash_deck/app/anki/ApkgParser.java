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

package m.co.rh.id.a_flash_deck.app.anki;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import m.co.rh.id.a_flash_deck.app.anki.model.AnkiCard;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiDeck;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiField;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNote;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNotetype;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiTemplate;

import com.github.luben.zstd.ZstdInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkgParser {
    private static final String TAG = ApkgParser.class.getName();

    private static final String ENTRY_META = "meta";
    private static final String ENTRY_MEDIA = "media";
    private static final String ENTRY_COLLECTION_ANKI2 = "collection.anki2";
    private static final String ENTRY_COLLECTION_ANKI21 = "collection.anki21";
    private static final String ENTRY_COLLECTION_ANKI21B = "collection.anki21b";
    // chunk size for the cards nid IN (...) query, stays well below SQLite's
    // 999 default host parameter limit
    private static final int NOTE_ID_CHUNK_SIZE = 500;

    // PackageMetadata.Version enum values from anki/import_export.proto,
    // stored as field 1 varint in the tiny "meta" zip entry
    private static final int META_VERSION_LEGACY_1 = 1;
    private static final int META_VERSION_LEGACY_2 = 2;
    private static final int META_VERSION_LATEST = 3;
    // collection schema versions found in col.ver, both are importable
    private static final int SCHEMA_VER_11 = 11;
    private static final int SCHEMA_VER_18 = 18;
    // NotetypeConfig field numbers from anki/notetypes.proto (schema 18
    // notetypes.config blob)
    private static final int NOTETYPE_CONFIG_FIELD_KIND = 1;
    private static final int NOTETYPE_CONFIG_FIELD_CSS = 3;
    // Template.Config field numbers from anki/notetypes.proto (schema 18
    // templates.config blob)
    private static final int TEMPLATE_CONFIG_FIELD_Q_FORMAT = 1;
    private static final int TEMPLATE_CONFIG_FIELD_A_FORMAT = 2;
    // MediaEntries field numbers from anki/import_export.proto
    private static final int MEDIA_ENTRIES_FIELD_ENTRIES = 1;
    private static final int MEDIA_ENTRY_FIELD_NAME = 1;

    /**
     * Extracted media of an .apkg package. files maps the numeric media key
     * ("0", "1", ...) to the extracted (for modern packages: decompressed)
     * file, namesByKey maps the same numeric key to the original media file
     * name recorded in the package media manifest.
     */
    public static final class MediaData {
        public final Map<String, File> files;
        public final Map<String, String> namesByKey;

        MediaData(Map<String, File> files, Map<String, String> namesByKey) {
            this.files = files;
            this.namesByKey = namesByKey;
        }
    }

    /**
     * Extracts the Anki collection database from an .apkg file and opens it
     * read only.
     *
     * <p>The collection entry is chosen from the protobuf "meta" entry when
     * present: Latest (3) selects collection.anki21b (zstd compressed, schema
     * 18), Legacy2 (2) selects collection.anki21 and Legacy1 (1) selects
     * collection.anki2. Old exporters write no meta entry, in that case the
     * entry is inferred: collection.anki21 first (legacy 2.1 format), then
     * collection.anki2 (legacy 2.0 format), then collection.anki21b. Real
     * legacy exports (meta = Legacy2, "Support older Anki versions" ticked)
     * contain collection.anki21 plus a dummy collection.anki2 decoy and never
     * anki21b, so the anki21-before-anki21b preference is purely defensive
     * ordering.</p>
     *
     * <p>For collection.anki21b the payload is a zstd frame wrapping the
     * SQLite file; it is streamed to a temp file without buffering the whole
     * database in memory. After opening, the col.ver value must be 11 or 18,
     * any other value (or an unreadable collection) is rejected as an
     * {@link AnkiUnsupportedPackageException} with reason
     * {@link AnkiUnsupportedPackageException.Reason#MODERN_ZSTD}. When
     * falling back to collection.anki2, the modern decoy stub collection
     * (a single note asking the user to update Anki) is detected and
     * rejected.</p>
     */
    public static SQLiteDatabase extractAndReadDatabase(File apkgFile, File tempDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            ZipEntry anki21Entry = zipFile.getEntry(ENTRY_COLLECTION_ANKI21);
            ZipEntry anki2Entry = zipFile.getEntry(ENTRY_COLLECTION_ANKI2);
            ZipEntry anki21bEntry = zipFile.getEntry(ENTRY_COLLECTION_ANKI21B);
            Integer metaVersion = readPackageMetaVersion(zipFile);

            ZipEntry dbEntry;
            boolean legacyFallback;
            boolean zstdCompressed;
            if (metaVersion != null && metaVersion == META_VERSION_LATEST) {
                // modern default export: collection.anki21b is the real
                // collection, any collection.anki2 companion is only the
                // decoy stub and must not be parsed
                dbEntry = anki21bEntry;
                legacyFallback = false;
                zstdCompressed = true;
                if (dbEntry == null) {
                    throw new IOException("meta declares a modern package but no "
                            + ENTRY_COLLECTION_ANKI21B + " entry found");
                }
            } else if (metaVersion != null && metaVersion == META_VERSION_LEGACY_2) {
                dbEntry = anki21Entry;
                legacyFallback = false;
                zstdCompressed = false;
                if (dbEntry == null) {
                    throw new IOException("meta declares a legacy 2.1 package but no "
                            + ENTRY_COLLECTION_ANKI21 + " entry found");
                }
            } else if (metaVersion != null && metaVersion == META_VERSION_LEGACY_1) {
                dbEntry = anki2Entry;
                legacyFallback = true;
                zstdCompressed = false;
                if (dbEntry == null) {
                    throw new IOException("meta declares a legacy 2.0 package but no "
                            + ENTRY_COLLECTION_ANKI2 + " entry found");
                }
            } else {
                // no (or unrecognized) meta entry, old exporter: infer from
                // the entry names
                if (anki21Entry != null) {
                    // prefer legacy anki21 even when anki21b exists; real
                    // legacy exports never carry both, this ordering is only
                    // defensive against synthetic/hostile packages
                    dbEntry = anki21Entry;
                    legacyFallback = false;
                    zstdCompressed = false;
                } else if (anki2Entry != null) {
                    // legacy 2.0 package or the anki2 companion of a modern
                    // package, guard against the modern decoy stub
                    dbEntry = anki2Entry;
                    legacyFallback = true;
                    zstdCompressed = false;
                } else if (anki21bEntry != null) {
                    dbEntry = anki21bEntry;
                    legacyFallback = false;
                    zstdCompressed = true;
                } else {
                    throw new IOException("No Anki collection database found in APKG file");
                }
            }

            File dbFile = new File(tempDir, dbEntry.getName());
            try (InputStream is = zipFile.getInputStream(dbEntry);
                 BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dbFile))) {
                if (zstdCompressed) {
                    try {
                        decompressZstdToFile(is, bos);
                    } catch (IOException e) {
                        throw new AnkiUnsupportedPackageException(
                                AnkiUnsupportedPackageException.Reason.MODERN_ZSTD,
                                ENTRY_COLLECTION_ANKI21B + " is not a valid zstd compressed collection", e);
                    }
                } else {
                    copyStream(is, bos);
                }
            }
            SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            if (legacyFallback && isDecoyCollection(db)) {
                db.close();
                throw new AnkiUnsupportedPackageException(AnkiUnsupportedPackageException.Reason.DECOY,
                        ENTRY_COLLECTION_ANKI2 + " contains only the modern Anki decoy collection");
            }
            if (zstdCompressed) {
                int schemaVersion = readColSchemaVersion(db);
                if (schemaVersion != SCHEMA_VER_11 && schemaVersion != SCHEMA_VER_18) {
                    db.close();
                    throw new AnkiUnsupportedPackageException(AnkiUnsupportedPackageException.Reason.MODERN_ZSTD,
                            ENTRY_COLLECTION_ANKI21B + " has an unsupported collection schema version: " + schemaVersion);
                }
            }
            return db;
        }
    }

    /**
     * Returns true when the given collection looks like the decoy stub that
     * modern Anki versions embed in exported apkg files: every modern apkg
     * ships a dummy collection.anki2 whose single note asks the user to
     * update Anki, and parsing that stub would silently import nothing (or
     * garbage) instead of the real collection.anki21b payload.
     *
     * <p>The heuristic is defense in depth for meta-less archives only: at
     * most one note whose first field text (lowercased) mentions both "anki"
     * and "version". Upstream localizes the decoy text via
     * tr.exporting_colpkg_too_new(), so the exact sentence cannot be matched
     * for every language ("Please update to the latest Anki version",
     * "Bitte aktualisieren Sie auf die neueste Anki-Version", ...); the
     * brand/version keywords cover the known variants. A false negative is
     * acceptable: real modern exports always carry a meta entry selecting
     * collection.anki21b, so their decoy collection.anki2 is never selected
     * for parsing in the first place.</p>
     */
    private static boolean isDecoyCollection(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            cursor = db.query("notes", new String[]{"flds"}, null, null, null, null, null, "2");
            int rowCount = 0;
            String firstFlds = null;
            while (cursor.moveToNext()) {
                if (rowCount == 0) {
                    firstFlds = cursor.isNull(0) ? "" : cursor.getString(0);
                }
                rowCount++;
            }
            if (rowCount > 1 || firstFlds == null) {
                return false;
            }
            String text = firstFlds.toLowerCase();
            return text.contains("anki") && text.contains("version");
        } catch (Exception e) {
            // missing/corrupt notes table, not a decoy, let the caller fail
            // later with a more precise error
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Reads col.ver, the collection schema version. Returns -1 when the col
     * table is missing or unreadable.
     */
    private static int readColSchemaVersion(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            cursor = db.query("col", new String[]{"ver"}, null, null, null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Extracts all media of an .apkg file into tempDir. Handles both legacy
     * packages (plain JSON media manifest, plain media contents) and modern
     * packages (zstd compressed MediaEntries protobuf manifest, zstd
     * compressed media contents).
     */
    public static MediaData extractMedia(File apkgFile, File tempDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            Integer metaVersion = readPackageMetaVersion(zipFile);
            if (metaVersion != null && metaVersion == META_VERSION_LATEST) {
                return extractModernMedia(zipFile, tempDir);
            }
            return new MediaData(extractMediaFiles(zipFile, tempDir), parseMediaJson(zipFile));
        } catch (JSONException e) {
            throw new IOException("Malformed media manifest in APKG file", e);
        }
    }

    public static Map<String, File> extractMediaFiles(File apkgFile, File tempDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            return extractMediaFiles(zipFile, tempDir);
        }
    }

    private static Map<String, File> extractMediaFiles(ZipFile zipFile, File tempDir) throws IOException {
        Map<String, File> mediaFiles = new HashMap<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.matches("^\\d+$") && name.indexOf('/') < 0 && name.indexOf('\\') < 0) {
                File mediaFile = new File(tempDir, name);
                try (InputStream is = zipFile.getInputStream(entry);
                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mediaFile))) {
                    copyStream(is, bos);
                }
                mediaFiles.put(name, mediaFile);
            }
        }
        return mediaFiles;
    }

    /**
     * Extracts media of a modern (Latest) package. The "media" zip entry is
     * a zstd compressed MediaEntries protobuf whose repeated entries define
     * the real file names; each entry's content is zstd compressed in the
     * zip under its enumerate index ("0", "1", ...). Contents are streamed
     * to tempDir without buffering whole files in memory.
     */
    private static MediaData extractModernMedia(ZipFile zipFile, File tempDir) throws IOException {
        Map<Integer, String> indexToName;
        ZipEntry manifestEntry = zipFile.getEntry(ENTRY_MEDIA);
        if (manifestEntry == null) {
            indexToName = new HashMap<>();
        } else {
            byte[] manifestBytes;
            try (InputStream is = zipFile.getInputStream(manifestEntry)) {
                manifestBytes = decompressZstdToBytes(is);
            }
            indexToName = parseModernMediaManifest(manifestBytes);
        }

        Map<String, File> mediaFiles = new LinkedHashMap<>();
        Map<String, String> namesByKey = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> manifestEntry2 : indexToName.entrySet()) {
            String numericKey = String.valueOf(manifestEntry2.getKey());
            ZipEntry contentEntry = zipFile.getEntry(numericKey);
            if (contentEntry == null) {
                // manifest references media not present in the package,
                // importer reports it as missing media later
                continue;
            }
            File mediaFile = new File(tempDir, numericKey);
            try (InputStream is = zipFile.getInputStream(contentEntry);
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(mediaFile))) {
                decompressZstdToFile(is, os);
            }
            mediaFiles.put(numericKey, mediaFile);
            namesByKey.put(numericKey, manifestEntry2.getValue());
        }
        return new MediaData(mediaFiles, namesByKey);
    }

    /**
     * Parses a MediaEntries protobuf manifest into index to real file name.
     * The enumerate position of each repeated MediaEntry is the name of its
     * zip entry (mirrors rslib SafeMediaEntry::from_entry).
     */
    private static Map<Integer, String> parseModernMediaManifest(byte[] manifest) throws IOException {
        Map<Integer, String> indexToName = new LinkedHashMap<>();
        ProtoReader reader = new ProtoReader(manifest);
        int[] wireType = new int[1];
        int index = 0;
        while (reader.hasNext()) {
            int fieldNumber = reader.readTag(wireType);
            if (wireType[0] == ProtoReader.WIRE_TYPE_LENGTH_DELIMITED
                    && fieldNumber == MEDIA_ENTRIES_FIELD_ENTRIES) {
                byte[] entry = reader.readLengthDelimited();
                String name = readProtoStringField(entry, MEDIA_ENTRY_FIELD_NAME);
                if (name != null) {
                    indexToName.put(index, name);
                }
                index++;
            } else {
                reader.skipField(wireType[0]);
            }
        }
        return indexToName;
    }

    public static Map<String, String> parseMediaJson(ZipFile zipFile) throws IOException, JSONException {
        ZipEntry mediaEntry = zipFile.getEntry(ENTRY_MEDIA);
        if (mediaEntry == null) {
            return new HashMap<>();
        }
        try (InputStream is = zipFile.getInputStream(mediaEntry);
             BufferedInputStream bis = new BufferedInputStream(is)) {
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
            JSONObject mediaJson = new JSONObject(sb.toString());
            Map<String, String> mediaMap = new HashMap<>();
            Iterator<String> keys = mediaJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                mediaMap.put(key, mediaJson.getString(key));
            }
            return mediaMap;
        }
    }

    public static List<AnkiNote> readNotes(SQLiteDatabase db, long notetypeId) {
        List<AnkiNote> notes = new ArrayList<>();
        String[] columns = {"id", "guid", "mid", "mod", "usn", "tags", "flds", "sfld", "csum", "flags", "data"};
        String selection = "mid = ?";
        String[] selectionArgs = {String.valueOf(notetypeId)};
        Cursor cursor = db.query("notes", columns, selection, selectionArgs, null, null, null);
        try {
            while (cursor.moveToNext()) {
                AnkiNote note = new AnkiNote();
                note.id = cursor.getLong(0);
                note.guid = cursor.getString(1);
                note.mid = cursor.getLong(2);
                note.mod = cursor.getLong(3);
                note.usn = cursor.getInt(4);
                note.tags = cursor.isNull(5) ? "" : cursor.getString(5);
                note.flds = cursor.isNull(6) ? "" : cursor.getString(6);
                note.sfld = cursor.isNull(7) ? "" : cursor.getString(7);
                note.csum = cursor.getInt(8);
                note.flags = cursor.getLong(9);
                note.data = cursor.isNull(10) ? "" : cursor.getString(10);
                notes.add(note);
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    /**
     * Counts the notes of a notetype with a single COUNT(*) query, without
     * materializing their field text. Used for notetypes whose notes are
     * skipped (cloze/unsupported) so a large package does not load all of
     * its note contents into memory just to report the skipped count.
     */
    public static int countNotes(SQLiteDatabase db, long notetypeId) {
        Cursor cursor = null;
        try {
            cursor = db.query("notes", new String[]{"COUNT(*)"}, "mid = ?",
                    new String[]{String.valueOf(notetypeId)}, null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static List<AnkiCard> readCards(SQLiteDatabase db, List<Long> noteIds) {
        List<AnkiCard> cards = new ArrayList<>();
        if (noteIds.isEmpty()) {
            return cards;
        }
        String[] columns = {"id", "nid", "did", "ord", "mod", "usn", "type", "queue", "due", "ivl", "factor", "reps", "lapses", "left", "odue", "odid", "flags", "data"};
        // query in chunks, a single nid IN (...) with one placeholder per note
        // breaks past SQLite's 999 host parameter limit on large decks
        for (int start = 0; start < noteIds.size(); start += NOTE_ID_CHUNK_SIZE) {
            int end = Math.min(start + NOTE_ID_CHUNK_SIZE, noteIds.size());
            List<Long> chunk = noteIds.subList(start, end);
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < chunk.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String selection = "nid IN (" + placeholders + ")";
            String[] selectionArgs = new String[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                selectionArgs[i] = String.valueOf(chunk.get(i));
            }
            Cursor cursor = db.query("cards", columns, selection, selectionArgs, null, null, null);
            try {
                while (cursor.moveToNext()) {
                    AnkiCard card = new AnkiCard();
                    card.id = cursor.getLong(0);
                    card.nid = cursor.getLong(1);
                    card.did = cursor.getLong(2);
                    card.ord = cursor.getInt(3);
                    card.mod = cursor.getLong(4);
                    card.usn = cursor.getInt(5);
                    card.type = cursor.getInt(6);
                    card.queue = cursor.getInt(7);
                    card.due = cursor.getInt(8);
                    card.ivl = cursor.getInt(9);
                    card.factor = cursor.getInt(10);
                    card.reps = cursor.getInt(11);
                    card.lapses = cursor.getInt(12);
                    card.left = cursor.getInt(13);
                    card.odue = cursor.getInt(14);
                    card.odid = cursor.getLong(15);
                    card.flags = cursor.getInt(16);
                    card.data = cursor.isNull(17) ? "" : cursor.getString(17);
                    cards.add(card);
                }
            } finally {
                cursor.close();
            }
        }
        return cards;
    }

    /**
     * Reads all decks. The deck storage differs between collection schema 11
     * and schema 18 (dedicated normalized decks table), so the schema version
     * is dispatched here and callers can use this method unchanged.
     *
     * <p>Genuine schema-11 collections keep decks ONLY in the col.decks JSON:
     * Anki's schema11_downgrade.sql drops the normalized decks/notetypes/
     * fields/templates tables, and pre-2.1.22 .anki2-based packages never had
     * them. The normalized decks table therefore only exists from schema 15
     * up, or in packages written by this app's own legacy exporter — those
     * are handled by the table fallback.</p>
     */
    public static List<AnkiDeck> readDecks(SQLiteDatabase db) throws JSONException {
        if (readColSchemaVersion(db) >= SCHEMA_VER_18) {
            return readDecksSchema18(db);
        }
        List<AnkiDeck> decks = readDecksFromColJson(db);
        if (decks.isEmpty()) {
            decks = readDecksFromTable(db);
        }
        return decks;
    }

    /**
     * Reads decks from the schema-11 col.decks JSON. Real Anki writes one
     * JSON object per deck keyed by its id; the mtime field is named "mod"
     * there (the table column "mtime_secs" only exists from schema 15 up).
     */
    private static List<AnkiDeck> readDecksFromColJson(SQLiteDatabase db) throws JSONException {
        List<AnkiDeck> decks = new ArrayList<>();
        Cursor cursor = db.query("col", new String[]{"decks"}, null, null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                String decksJson = cursor.getString(0);
                if (decksJson != null && !decksJson.isEmpty()) {
                    JSONObject decksObj = new JSONObject(decksJson);
                    Iterator<String> deckKeys = decksObj.keys();
                    while (deckKeys.hasNext()) {
                        String key = deckKeys.next();
                        JSONObject deckObj = decksObj.getJSONObject(key);
                        AnkiDeck deck = new AnkiDeck();
                        deck.id = deckObj.optLong("id");
                        if (deck.id == 0) {
                            deck.id = Long.parseLong(key);
                        }
                        deck.name = deckObj.getString("name");
                        deck.mtimeStamp = deckObj.optLong("mod", 0);
                        deck.usn = deckObj.optInt("usn", -1);
                        deck.conf = "";
                        deck.common = "";
                        deck.kind = "";
                        deck.children = new ArrayList<>();
                        decks.add(deck);
                    }
                }
            }
        } finally {
            cursor.close();
        }
        return decks;
    }

    /**
     * Reads decks from the normalized decks table (schema 15+ layout; also
     * written by this app's own legacy exporter for schema-11 packages).
     */
    private static List<AnkiDeck> readDecksFromTable(SQLiteDatabase db) {
        List<AnkiDeck> decks = new ArrayList<>();
        Cursor cursor = db.query("decks", null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                AnkiDeck deck = new AnkiDeck();
                deck.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                deck.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                deck.mtimeStamp = cursor.getLong(cursor.getColumnIndexOrThrow("mtime_secs"));
                deck.usn = cursor.getInt(cursor.getColumnIndexOrThrow("usn"));
                deck.conf = cursor.isNull(cursor.getColumnIndexOrThrow("conf")) ? "" : cursor.getString(cursor.getColumnIndexOrThrow("conf"));
                deck.common = cursor.isNull(cursor.getColumnIndexOrThrow("common")) ? "" : cursor.getString(cursor.getColumnIndexOrThrow("common"));
                deck.kind = cursor.isNull(cursor.getColumnIndexOrThrow("kind")) ? "" : cursor.getString(cursor.getColumnIndexOrThrow("kind"));
                deck.children = new ArrayList<>();
                decks.add(deck);
            }
        } finally {
            cursor.close();
        }
        return decks;
    }

    /**
     * Reads decks from a schema 18 collection (decks table without conf
     * column, common/kind are protobuf blobs which the importer does not
     * need).
     */
    private static List<AnkiDeck> readDecksSchema18(SQLiteDatabase db) {
        List<AnkiDeck> decks = new ArrayList<>();
        Cursor cursor = db.query("decks", new String[]{"id", "name", "mtime_secs", "usn"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                AnkiDeck deck = new AnkiDeck();
                deck.id = cursor.getLong(0);
                deck.name = cursor.getString(1);
                deck.mtimeStamp = cursor.getLong(2);
                deck.usn = cursor.getInt(3);
                deck.conf = "";
                deck.common = "";
                deck.kind = "";
                deck.children = new ArrayList<>();
                decks.add(deck);
            }
        } finally {
            cursor.close();
        }
        return decks;
    }

    /**
     * Reads all notetypes. Schema 11 collections keep notetypes in the
     * col.models JSON, schema 18 collections (modern anki21b packages) use
     * dedicated tables with protobuf config blobs, so the schema version is
     * dispatched here and callers can use this method unchanged.
     */
    public static List<AnkiNotetype> readNotetypes(SQLiteDatabase db) throws JSONException, IOException {
        if (readColSchemaVersion(db) >= SCHEMA_VER_18) {
            return readNotetypesSchema18(db);
        }
        List<AnkiNotetype> notetypes = new ArrayList<>();
        Cursor cursor = db.query("col", new String[]{"models"}, null, null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                String modelsJson = cursor.getString(0);
                JSONObject models = new JSONObject(modelsJson);
                Iterator<String> modelKeys = models.keys();
                while (modelKeys.hasNext()) {
                    String key = modelKeys.next();
                    JSONObject modelObj = models.getJSONObject(key);
                    AnkiNotetype notetype = new AnkiNotetype();
                    notetype.id = modelObj.getLong("id");
                    notetype.name = modelObj.getString("name");
                    notetype.type = modelObj.optInt("type", 0);
                    notetype.css = modelObj.optString("css", "");

                    JSONArray fldsArray = modelObj.getJSONArray("flds");
                    notetype.flds = new ArrayList<>();
                    for (int i = 0; i < fldsArray.length(); i++) {
                        JSONObject fieldObj = fldsArray.getJSONObject(i);
                        AnkiField field = new AnkiField();
                        field.name = fieldObj.getString("name");
                        field.ord = fieldObj.getInt("ord");
                        field.sticky = fieldObj.optBoolean("sticky", false);
                        notetype.flds.add(field);
                    }

                    JSONArray tmplsArray = modelObj.getJSONArray("tmpls");
                    notetype.tmpls = new ArrayList<>();
                    for (int i = 0; i < tmplsArray.length(); i++) {
                        JSONObject tmplObj = tmplsArray.getJSONObject(i);
                        AnkiTemplate tmpl = new AnkiTemplate();
                        tmpl.name = tmplObj.getString("name");
                        tmpl.qfmt = tmplObj.getString("qfmt");
                        tmpl.afmt = tmplObj.getString("afmt");
                        tmpl.ord = tmplObj.getInt("ord");
                        notetype.tmpls.add(tmpl);
                    }
                    notetypes.add(notetype);
                }
            }
        } finally {
            cursor.close();
        }
        return notetypes;
    }

    /**
     * Reads notetypes from a schema 18 collection. The notetype kind (0 =
     * standard, 1 = cloze) lives inside the notetypes.config protobuf blob
     * (NotetypeConfig.kind), as do css and the template formats.
     */
    private static List<AnkiNotetype> readNotetypesSchema18(SQLiteDatabase db) throws IOException {
        Map<Long, List<AnkiField>> fieldsByNotetypeId = readFieldsSchema18(db);
        Map<Long, List<AnkiTemplate>> templatesByNotetypeId = readTemplatesSchema18(db);
        List<AnkiNotetype> notetypes = new ArrayList<>();
        Cursor cursor = db.query("notetypes", new String[]{"id", "name", "config"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                AnkiNotetype notetype = new AnkiNotetype();
                notetype.id = cursor.getLong(0);
                notetype.name = cursor.getString(1);
                byte[] config = cursor.isNull(2) ? null : cursor.getBlob(2);
                notetype.type = (int) readProtoVarintField(config, NOTETYPE_CONFIG_FIELD_KIND);
                String css = readProtoStringField(config, NOTETYPE_CONFIG_FIELD_CSS);
                notetype.css = css == null ? "" : css;
                List<AnkiField> fields = fieldsByNotetypeId.get(notetype.id);
                notetype.flds = fields == null ? new ArrayList<>() : fields;
                List<AnkiTemplate> templates = templatesByNotetypeId.get(notetype.id);
                notetype.tmpls = templates == null ? new ArrayList<>() : templates;
                notetypes.add(notetype);
            }
        } finally {
            cursor.close();
        }
        return notetypes;
    }

    /**
     * Reads the ordered field rows of a schema 18 collection grouped by
     * notetype id.
     */
    private static Map<Long, List<AnkiField>> readFieldsSchema18(SQLiteDatabase db) {
        Map<Long, List<AnkiField>> fieldsByNotetypeId = new HashMap<>();
        Cursor cursor = db.query("fields", new String[]{"ntid", "ord", "name"}, null, null, null, null, "ntid, ord");
        try {
            while (cursor.moveToNext()) {
                AnkiField field = new AnkiField();
                field.ord = cursor.getInt(1);
                field.name = cursor.getString(2);
                field.sticky = false;
                List<AnkiField> fields = fieldsByNotetypeId.get(cursor.getLong(0));
                if (fields == null) {
                    fields = new ArrayList<>();
                    fieldsByNotetypeId.put(cursor.getLong(0), fields);
                }
                fields.add(field);
            }
        } finally {
            cursor.close();
        }
        return fieldsByNotetypeId;
    }

    /**
     * Reads the ordered template rows of a schema 18 collection grouped by
     * notetype id. The question/answer formats live inside the
     * templates.config protobuf blob (Template.Config q_format/a_format).
     */
    private static Map<Long, List<AnkiTemplate>> readTemplatesSchema18(SQLiteDatabase db) throws IOException {
        Map<Long, List<AnkiTemplate>> templatesByNotetypeId = new HashMap<>();
        Cursor cursor = db.query("templates", new String[]{"ntid", "ord", "name", "config"}, null, null, null, null, "ntid, ord");
        try {
            while (cursor.moveToNext()) {
                AnkiTemplate template = new AnkiTemplate();
                template.ord = cursor.getInt(1);
                template.name = cursor.getString(2);
                byte[] config = cursor.isNull(3) ? null : cursor.getBlob(3);
                String qfmt = readProtoStringField(config, TEMPLATE_CONFIG_FIELD_Q_FORMAT);
                String afmt = readProtoStringField(config, TEMPLATE_CONFIG_FIELD_A_FORMAT);
                template.qfmt = qfmt == null ? "" : qfmt;
                template.afmt = afmt == null ? "" : afmt;
                List<AnkiTemplate> templates = templatesByNotetypeId.get(cursor.getLong(0));
                if (templates == null) {
                    templates = new ArrayList<>();
                    templatesByNotetypeId.put(cursor.getLong(0), templates);
                }
                templates.add(template);
            }
        } finally {
            cursor.close();
        }
        return templatesByNotetypeId;
    }

    /**
     * Returns true when the notetype is a Basic variant that can be mapped to
     * an app card: standard type, exactly two fields and a name starting with
     * "Basic" (case-insensitive, e.g. "Basic", "Basic (and reversed card)").
     */
    public static boolean isBasicNotetype(AnkiNotetype notetype) {
        return notetype != null
                && notetype.type == 0
                && notetype.flds != null
                && notetype.flds.size() == 2
                && notetype.name != null
                && notetype.name.trim().toLowerCase().startsWith("basic");
    }

    /**
     * Returns true when the notetype is a cloze notetype (schema type 1).
     */
    public static boolean isClozeNotetype(AnkiNotetype notetype) {
        return notetype != null && notetype.type == 1;
    }

    /**
     * Reads the "meta" zip entry (PackageMetadata protobuf, field 1 varint)
     * and returns its version value, or null when the entry is absent or
     * malformed (old exporters write no meta entry, entry-name inference
     * applies then).
     */
    private static Integer readPackageMetaVersion(ZipFile zipFile) throws IOException {
        ZipEntry metaEntry = zipFile.getEntry(ENTRY_META);
        if (metaEntry == null) {
            return null;
        }
        byte[] metaBytes;
        try (InputStream is = zipFile.getInputStream(metaEntry)) {
            metaBytes = readStreamToBytes(is);
        }
        try {
            long version = readProtoVarintField(metaBytes, 1, -1);
            if (version >= 0 && version <= Integer.MAX_VALUE) {
                return (int) version;
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static long readProtoVarintField(byte[] data, int fieldNumber) throws IOException {
        return readProtoVarintField(data, fieldNumber, 0);
    }

    /**
     * Returns the value of a varint field of a protobuf message, or the given
     * default when the field is absent. Unknown fields are skipped.
     */
    private static long readProtoVarintField(byte[] data, int fieldNumber, long defaultValue) throws IOException {
        ProtoReader reader = new ProtoReader(data);
        int[] wireType = new int[1];
        while (reader.hasNext()) {
            int field = reader.readTag(wireType);
            if (wireType[0] == ProtoReader.WIRE_TYPE_VARINT && field == fieldNumber) {
                return reader.readVarint();
            }
            reader.skipField(wireType[0]);
        }
        return defaultValue;
    }

    /**
     * Returns the value of a length-delimited string field of a protobuf
     * message, or null when the field is absent. Unknown fields are skipped.
     */
    private static String readProtoStringField(byte[] data, int fieldNumber) throws IOException {
        ProtoReader reader = new ProtoReader(data);
        int[] wireType = new int[1];
        while (reader.hasNext()) {
            int field = reader.readTag(wireType);
            if (wireType[0] == ProtoReader.WIRE_TYPE_LENGTH_DELIMITED && field == fieldNumber) {
                return new String(reader.readLengthDelimited(), StandardCharsets.UTF_8);
            }
            reader.skipField(wireType[0]);
        }
        return null;
    }

    /**
     * Minimal protobuf wire-format reader for the few tiny blobs of modern
     * (schema 18) apkg packages (meta, notetypes.config, media manifest),
     * where adding a protobuf library is not justified. Unknown fields are
     * skipped per https://protobuf.dev/programming-guides/encoding/.
     */
    private static final class ProtoReader {
        static final int WIRE_TYPE_VARINT = 0;
        static final int WIRE_TYPE_FIXED_64 = 1;
        static final int WIRE_TYPE_LENGTH_DELIMITED = 2;
        static final int WIRE_TYPE_FIXED_32 = 5;

        private final byte[] data;
        private int pos;

        ProtoReader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        boolean hasNext() {
            return pos < data.length;
        }

        /**
         * Reads the next field tag, returns the field number and stores the
         * wire type in wireType[0].
         */
        int readTag(int[] wireType) throws IOException {
            long key = readVarint();
            int fieldNumber = (int) (key >>> 3);
            wireType[0] = (int) (key & 0x07);
            if (fieldNumber <= 0) {
                throw new IOException("Malformed protobuf tag");
            }
            return fieldNumber;
        }

        long readVarint() throws IOException {
            long result = 0;
            int shift = 0;
            while (true) {
                if (pos >= data.length) {
                    throw new IOException("Truncated protobuf varint");
                }
                byte b = data[pos++];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
                if (shift >= 64) {
                    throw new IOException("Malformed protobuf varint");
                }
            }
        }

        byte[] readLengthDelimited() throws IOException {
            long length = readVarint();
            if (length < 0 || pos + length > data.length) {
                throw new IOException("Truncated protobuf length-delimited field");
            }
            byte[] result = Arrays.copyOfRange(data, pos, pos + (int) length);
            pos += (int) length;
            return result;
        }

        void skipField(int wireType) throws IOException {
            switch (wireType) {
                case WIRE_TYPE_VARINT:
                    readVarint();
                    break;
                case WIRE_TYPE_FIXED_64:
                    requireAvailable(8);
                    break;
                case WIRE_TYPE_LENGTH_DELIMITED:
                    readLengthDelimited();
                    break;
                case WIRE_TYPE_FIXED_32:
                    requireAvailable(4);
                    break;
                default:
                    // wire types 3/4 (deprecated start/end groups) and invalid
                    // values indicate a corrupt message
                    throw new IOException("Unsupported protobuf wire type " + wireType);
            }
        }

        private void requireAvailable(int bytes) throws IOException {
            if (pos + bytes > data.length) {
                throw new IOException("Truncated protobuf fixed-size field");
            }
            pos += bytes;
        }
    }

    /**
     * Streams the zstd frame from the given input to the given output.
     * Handles large collections without buffering them in memory. Does not
     * close the given streams.
     */
    private static void decompressZstdToFile(InputStream input, OutputStream output) throws IOException {
        try (ZstdInputStream zis = new ZstdInputStream(input)) {
            copyStream(zis, output);
        }
    }

    /**
     * Decompresses a small complete zstd frame (meta/manifest sized) fully
     * into memory.
     */
    private static byte[] decompressZstdToBytes(InputStream input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZstdInputStream zis = new ZstdInputStream(input)) {
            copyStream(zis, bos);
        }
        return bos.toByteArray();
    }

    private static byte[] readStreamToBytes(InputStream input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copyStream(input, bos);
        return bos.toByteArray();
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }
}
