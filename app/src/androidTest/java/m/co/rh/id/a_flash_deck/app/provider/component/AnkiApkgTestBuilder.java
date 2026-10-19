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

import android.database.sqlite.SQLiteDatabase;

import com.github.luben.zstd.ZstdOutputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import m.co.rh.id.a_flash_deck.app.anki.ApkgGenerator;

/**
 * Test helper that synthesizes Anki .apkg files without needing a real Anki
 * export. Builds a schema-11 SQLite collection (same layout as
 * {@link ApkgGenerator}) with freely configurable notetypes, decks, notes and
 * cards, then zips it under caller-chosen collection entry names
 * (collection.anki21 / collection.anki2 / collection.anki21b).
 *
 * <p>Use {@link #modern(File)} for the modern default export format: a
 * schema-18 collection zstd compressed into collection.anki21b with a meta
 * protobuf entry and a zstd compressed MediaEntries media manifest.</p>
 *
 * <p>Typical usage:</p>
 * <pre>
 * File apkg = new AnkiApkgTestBuilder(tempDir)
 *         .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
 *         .addDeck(200, "My Deck")
 *         .addNote(300, 100, "Q", "A")
 *         .addCard(400, 300, 200, 0)
 *         .build(new File(tempDir, "test.apkg"), "collection.anki21");
 * </pre>
 */
public class AnkiApkgTestBuilder {

    private final SQLiteDatabase db;
    private final File dbFile;
    private final boolean modernSchema;
    // genuine schema-11 mode: decks live in the col.decks JSON and the
    // normalized decks table is dropped (mirroring real Anki downgrades)
    private final boolean genuineSchema11;
    private final JSONObject models = new JSONObject();
    private final JSONObject decksJson = new JSONObject();
    private Map<String, byte[]> mediaFileContents;
    private String mediaJson = "{}";
    // modern schema 18 only: media files in manifest order (enumerate index
    // of each entry names its zip entry, mirroring real Anki exports)
    private LinkedHashMap<String, byte[]> modernMediaFilesByName;

    public AnkiApkgTestBuilder(File tempDir) throws IOException {
        this(tempDir, false, false);
    }

    private AnkiApkgTestBuilder(File tempDir, boolean modernSchema, boolean genuineSchema11) throws IOException {
        this.modernSchema = modernSchema;
        this.genuineSchema11 = genuineSchema11;
        dbFile = new File(tempDir, modernSchema ? "synthetic_collection.anki21b" : "synthetic_collection.anki21");
        db = ApkgGenerator.createTempDatabase(dbFile);
        if (modernSchema) {
            createTablesSchema18(db);
        } else {
            ApkgGenerator.createTables(db);
            if (genuineSchema11) {
                // real schema-11 collections have no normalized decks table
                // (dropped by Anki's schema11_downgrade.sql, never present in
                // pre-2.1.22 .anki2-based packages)
                db.execSQL("DROP TABLE decks");
            }
        }
    }

    /**
     * Creates a builder that produces a GENUINE schema-11 package the way
     * real Anki does: decks stored in the col.decks JSON (keyed by deck id,
     * mtime field "mod"), NO normalized decks table — the shape produced by
     * Anki's own downgrades ("Support older Anki versions") and by pre-2.1.22
     * .anki2-based exports. Our own legacy exporter writes a decks table
     * instead, which is NOT the real-world shape.
     */
    public static AnkiApkgTestBuilder genuine(File tempDir) throws IOException {
        return new AnkiApkgTestBuilder(tempDir, false, true);
    }

    /**
     * Creates a builder that produces a modern default export package: a
     * schema-18 collection (col.ver = 18, empty col JSON columns, normalized
     * notetypes/fields/templates/decks tables) zstd compressed into
     * collection.anki21b, together with the meta protobuf entry (version 3)
     * and a zstd compressed MediaEntries media manifest.
     */
    public static AnkiApkgTestBuilder modern(File tempDir) throws IOException {
        return new AnkiApkgTestBuilder(tempDir, true, false);
    }

    /**
     * Adds a notetype definition. Legacy mode writes it into the col.models
     * JSON, modern mode writes notetypes/fields/templates table rows.
     *
     * @param id            notetype id (referenced by notes.mid)
     * @param name          notetype name, e.g. "Basic", "Basic (and reversed card)", "Cloze"
     * @param type          schema type: 0 = standard, 1 = cloze
     * @param fieldNames    field names in order
     * @param templateCount number of card templates to generate
     */
    public AnkiApkgTestBuilder addNotetype(long id, String name, int type, String[] fieldNames, int templateCount) throws JSONException {
        if (modernSchema) {
            return addNotetypeSchema18(id, name, type, fieldNames, templateCount);
        }
        JSONObject model = new JSONObject();
        model.put("id", id);
        model.put("name", name);
        model.put("type", type);
        model.put("css", "");

        JSONArray flds = new JSONArray();
        for (int i = 0; i < fieldNames.length; i++) {
            JSONObject field = new JSONObject();
            field.put("name", fieldNames[i]);
            field.put("ord", i);
            field.put("sticky", false);
            flds.put(field);
        }
        model.put("flds", flds);

        JSONArray tmpls = new JSONArray();
        for (int i = 0; i < templateCount; i++) {
            JSONObject tmpl = new JSONObject();
            tmpl.put("name", "Card " + (i + 1));
            tmpl.put("qfmt", "{{" + fieldNames[0] + "}}");
            tmpl.put("afmt", "{{FrontSide}}<hr id=answer>{{"
                    + (fieldNames.length > 1 ? fieldNames[1] : fieldNames[0]) + "}}");
            tmpl.put("ord", i);
            tmpls.put(tmpl);
        }
        model.put("tmpls", tmpls);

        models.put(String.valueOf(id), model);
        return this;
    }

    /**
     * Writes a schema-18 notetype: the notetypes row carries the
     * NotetypeConfig protobuf blob (kind field 1: 0 = standard, 1 = cloze),
     * fields and templates go into their own tables.
     */
    private AnkiApkgTestBuilder addNotetypeSchema18(long id, String name, int type, String[] fieldNames, int templateCount) {
        db.execSQL("INSERT INTO notetypes (id, name, mtime_secs, usn, config) VALUES (?, ?, ?, ?, ?)",
                new Object[]{id, name, System.currentTimeMillis() / 1000, -1, notetypeConfigProto(type)});
        for (int i = 0; i < fieldNames.length; i++) {
            db.execSQL("INSERT INTO fields (ntid, ord, name, config) VALUES (?, ?, ?, ?)",
                    new Object[]{id, i, fieldNames[i], new byte[0]});
        }
        for (int i = 0; i < templateCount; i++) {
            String qfmt = "{{" + fieldNames[0] + "}}";
            String afmt = "{{FrontSide}}<hr id=answer>{{"
                    + (fieldNames.length > 1 ? fieldNames[1] : fieldNames[0]) + "}}";
            db.execSQL("INSERT INTO templates (ntid, ord, name, mtime_secs, usn, config) VALUES (?, ?, ?, ?, ?, ?)",
                    new Object[]{id, i, "Card " + (i + 1), System.currentTimeMillis() / 1000, -1,
                            templateConfigProto(qfmt, afmt)});
        }
        return this;
    }

    public AnkiApkgTestBuilder addDeck(long id, String name) throws JSONException {
        if (modernSchema) {
            db.execSQL("INSERT INTO decks (id, name, mtime_secs, usn, common, kind) VALUES (?, ?, ?, ?, ?, ?)",
                    new Object[]{id, name, System.currentTimeMillis() / 1000, -1, "{}", "default"});
        } else if (genuineSchema11) {
            // real schema-11 shape: deck object in the col.decks JSON, no table row
            JSONObject deck = new JSONObject();
            deck.put("id", id);
            deck.put("name", name);
            deck.put("mod", System.currentTimeMillis() / 1000);
            deck.put("usn", -1);
            deck.put("conf", 1);
            deck.put("dyn", 0);
            decksJson.put(String.valueOf(id), deck);
        } else {
            db.execSQL("INSERT INTO decks (id, conf, name, mtime_secs, usn, common, kind) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{id, 1, name, System.currentTimeMillis() / 1000, -1, "{}", "default"});
        }
        return this;
    }

    /**
     * Adds a note whose flds is the given fields joined with the Anki
     * field separator (\u001f). The note guid is a random UUID.
     */
    public AnkiApkgTestBuilder addNote(long id, long notetypeId, String... fields) {
        return addNoteWithGuid(id, UUID.randomUUID().toString(), notetypeId, fields);
    }

    /**
     * Adds a note with an explicit guid, useful for duplicate detection
     * tests (re-import, guid reuse within one file).
     */
    public AnkiApkgTestBuilder addNoteWithGuid(long id, String guid, long notetypeId, String... fields) {
        StringBuilder flds = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                flds.append('\u001f');
            }
            flds.append(fields[i]);
        }
        String sfld = fields.length > 0 ? fields[0] : "";
        db.execSQL("INSERT INTO notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{id, guid, notetypeId, System.currentTimeMillis(), -1,
                        "", flds.toString(), sfld, sfld.hashCode(), 0, ""});
        return this;
    }

    public AnkiApkgTestBuilder addCard(long id, long noteId, long deckId, int ord) {
        db.execSQL("INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{id, noteId, deckId, ord, System.currentTimeMillis(), -1, 0, 0, 0, 0, 2500, 0, 0, 0, 0, 0, 0, ""});
        return this;
    }

    /**
     * Runs the given operations inside a single database transaction, useful
     * when adding many notes/cards to keep the test fast.
     */
    public AnkiApkgTestBuilder runInTransaction(Runnable operations) {
        db.beginTransaction();
        try {
            operations.run();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return this;
    }

    /**
     * Optionally sets the media JSON mapping (numeric id to file name) and
     * the corresponding media file contents written into the zip. Legacy
     * mode only.
     */
    public AnkiApkgTestBuilder withMedia(Map<String, byte[]> fileContents, String mediaJson) {
        this.mediaFileContents = fileContents;
        this.mediaJson = mediaJson;
        return this;
    }

    /**
     * Sets the media of a modern package: the given map order defines the
     * MediaEntries manifest order, the enumerate index of each entry is its
     * zip entry name and the contents are zstd compressed (mirroring real
     * Anki modern exports).
     */
    public AnkiApkgTestBuilder withModernMedia(LinkedHashMap<String, byte[]> filesByName) {
        this.modernMediaFilesByName = filesByName;
        return this;
    }

    /**
     * Writes the col row, closes the database and zips it under each of the
     * given collection entry names together with the media manifest.
     */
    public File build(File outputFile, String... collectionEntryNames) throws IOException, JSONException {
        if (modernSchema) {
            db.execSQL("INSERT INTO col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{1, System.currentTimeMillis() / 1000, System.currentTimeMillis(),
                            System.currentTimeMillis(), 18, 0, -1, 0, "", "", "", "", ""});
        } else {
            db.execSQL("INSERT INTO col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{1, System.currentTimeMillis() / 1000, System.currentTimeMillis(),
                            System.currentTimeMillis(), 11, 0, -1, 0, "{}", models.toString(),
                            genuineSchema11 ? decksJson.toString() : "{}", "{}", "{}"});
        }
        db.close();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            if (modernSchema) {
                File compressedDbFile = new File(dbFile.getParent(), dbFile.getName() + ".zst");
                zstdCompressFile(dbFile, compressedDbFile);
                for (String entryName : collectionEntryNames) {
                    writeZipEntryFromFile(zos, entryName, compressedDbFile);
                }
                // meta: PackageMetadata protobuf, field 1 varint version 3 (Latest)
                zos.putNextEntry(new ZipEntry("meta"));
                zos.write(new byte[]{0x08, 0x03});
                zos.closeEntry();
                writeModernMedia(zos);
            } else {
                for (String entryName : collectionEntryNames) {
                    writeZipEntryFromFile(zos, entryName, dbFile);
                }
                zos.putNextEntry(new ZipEntry("media"));
                zos.write(mediaJson.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                if (mediaFileContents != null) {
                    for (Map.Entry<String, byte[]> entry : mediaFileContents.entrySet()) {
                        zos.putNextEntry(new ZipEntry(entry.getKey()));
                        zos.write(entry.getValue());
                        zos.closeEntry();
                    }
                }
            }
        }
        return outputFile;
    }

    /**
     * Writes the modern media: a zstd compressed MediaEntries protobuf
     * manifest ("media" entry, repeated field 1 with the real file name per
     * entry) plus one zstd compressed zip entry per file named by its
     * enumerate index ("0", "1", ...).
     */
    private void writeModernMedia(ZipOutputStream zos) throws IOException {
        if (modernMediaFilesByName == null) {
            // a modern package without media still carries an (empty)
            // compressed manifest
            zos.putNextEntry(new ZipEntry("media"));
            zos.write(zstdCompress(new byte[0]));
            zos.closeEntry();
            return;
        }
        List<String> names = new ArrayList<>(modernMediaFilesByName.keySet());
        zos.putNextEntry(new ZipEntry("media"));
        zos.write(zstdCompress(mediaManifestProto(names)));
        zos.closeEntry();
        int index = 0;
        for (byte[] content : modernMediaFilesByName.values()) {
            zos.putNextEntry(new ZipEntry(String.valueOf(index)));
            zos.write(zstdCompress(content));
            zos.closeEntry();
            index++;
        }
    }

    /**
     * Builds a modern-only package: a zip containing ONLY a collection.anki21b
     * entry with an INVALID payload (plain bytes, not zstd), used to test the
     * corrupted-zstd error path (AnkiUnsupportedPackageException.Reason.MODERN_ZSTD).
     */
    public static File buildModernOnly(File outputFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            zos.putNextEntry(new ZipEntry("collection.anki21b"));
            zos.write(new byte[]{1, 2, 3, 4});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("media"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return outputFile;
    }

    /**
     * Creates the schema-18 table layout of modern Anki collections
     * (rslib schema11.sql + schema14/15/17/18 upgrades): notes and cards are
     * identical to schema 11, notetypes/fields/templates/decks are
     * normalized, col keeps its columns with empty JSON strings and
     * ver = 18. Deck presets (deck_config) and prefs (config) are not read
     * by the importer and therefore omitted.
     */
    private static void createTablesSchema18(SQLiteDatabase db) {
        ApkgGenerator.createTables(db);
        db.execSQL("DROP TABLE decks");
        db.execSQL("CREATE TABLE decks (" +
                "id INTEGER PRIMARY KEY NOT NULL, " +
                "name TEXT NOT NULL, " +
                "mtime_secs INTEGER NOT NULL, " +
                "usn INTEGER NOT NULL, " +
                "common BLOB NOT NULL, " +
                "kind BLOB NOT NULL)");
        db.execSQL("CREATE TABLE notetypes (" +
                "id INTEGER NOT NULL PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "mtime_secs INTEGER NOT NULL, " +
                "usn INTEGER NOT NULL, " +
                "config BLOB NOT NULL)");
        db.execSQL("CREATE TABLE fields (" +
                "ntid INTEGER NOT NULL, " +
                "ord INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "config BLOB NOT NULL, " +
                "PRIMARY KEY (ntid, ord))");
        db.execSQL("CREATE TABLE templates (" +
                "ntid INTEGER NOT NULL, " +
                "ord INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "mtime_secs INTEGER NOT NULL, " +
                "usn INTEGER NOT NULL, " +
                "config BLOB NOT NULL, " +
                "PRIMARY KEY (ntid, ord))");
    }

    /**
     * NotetypeConfig protobuf with only the kind field set (field 1 varint,
     * 0 = standard, 1 = cloze).
     */
    private static byte[] notetypeConfigProto(int kind) {
        return new byte[]{0x08, (byte) kind};
    }

    /**
     * Template.Config protobuf with the q_format (field 1) and a_format
     * (field 2) strings set.
     */
    private static byte[] templateConfigProto(String qfmt, String afmt) {
        byte[] qfmtBytes = qfmt.getBytes(StandardCharsets.UTF_8);
        byte[] afmtBytes = afmt.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[2 + qfmtBytes.length + 2 + afmtBytes.length];
        int pos = 0;
        result[pos++] = 0x0A; // field 1, length delimited
        result[pos++] = (byte) qfmtBytes.length;
        System.arraycopy(qfmtBytes, 0, result, pos, qfmtBytes.length);
        pos += qfmtBytes.length;
        result[pos++] = 0x12; // field 2, length delimited
        result[pos++] = (byte) afmtBytes.length;
        System.arraycopy(afmtBytes, 0, result, pos, afmtBytes.length);
        return result;
    }

    /**
     * MediaEntries protobuf: repeated MediaEntry (field 1), each with only
     * the name string set (field 1). The enumerate position of each entry is
     * the name of its zip entry.
     */
    private static byte[] mediaManifestProto(List<String> names) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            for (String name : names) {
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                bos.write(0x0A); // field 1, length delimited
                bos.write(nameBytes.length + 2);
                bos.write(0x0A); // MediaEntry.name, field 1
                bos.write(nameBytes.length);
                bos.write(nameBytes);
            }
        } catch (IOException e) {
            throw new IllegalStateException("ByteArrayOutputStream cannot throw", e);
        }
        return bos.toByteArray();
    }

    private static void zstdCompressFile(File input, File output) throws IOException {
        try (FileInputStream fis = new FileInputStream(input);
             ZstdOutputStream zos = new ZstdOutputStream(new FileOutputStream(output))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
        }
    }

    private static byte[] zstdCompress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZstdOutputStream zos = new ZstdOutputStream(bos)) {
            zos.write(data);
        }
        return bos.toByteArray();
    }

    private static void writeZipEntryFromFile(ZipOutputStream zos, String entryName, File file) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
        }
        zos.closeEntry();
    }
}
