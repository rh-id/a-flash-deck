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

package m.co.rh.id.a_flash_deck.app.anki;

import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ApkgGenerator {
    private static final String TAG = ApkgGenerator.class.getName();

    public static SQLiteDatabase createTempDatabase(File dbFile) {
        return SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), null);
    }

    public static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY, " +
                "guid TEXT NOT NULL, " +
                "mid INTEGER NOT NULL, " +
                "mod INTEGER NOT NULL, " +
                "usn INTEGER NOT NULL, " +
                "tags TEXT NOT NULL, " +
                "flds TEXT NOT NULL, " +
                "sfld TEXT NOT NULL, " +
                "csum INTEGER NOT NULL, " +
                "flags INTEGER NOT NULL, " +
                "data TEXT NOT NULL)");

        db.execSQL("CREATE TABLE cards (" +
                "id INTEGER PRIMARY KEY, " +
                "nid INTEGER NOT NULL, " +
                "did INTEGER NOT NULL, " +
                "ord INTEGER NOT NULL, " +
                "mod INTEGER NOT NULL, " +
                "usn INTEGER NOT NULL, " +
                "type INTEGER NOT NULL, " +
                "queue INTEGER NOT NULL, " +
                "due INTEGER NOT NULL, " +
                "ivl INTEGER NOT NULL, " +
                "factor INTEGER NOT NULL, " +
                "reps INTEGER NOT NULL, " +
                "lapses INTEGER NOT NULL, " +
                "left INTEGER NOT NULL, " +
                "odue INTEGER NOT NULL, " +
                "odid INTEGER NOT NULL, " +
                "flags INTEGER NOT NULL, " +
                "data TEXT NOT NULL)");

        db.execSQL("CREATE TABLE decks (" +
                "id INTEGER PRIMARY KEY, " +
                "conf INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "mtime_secs INTEGER NOT NULL, " +
                "usn INTEGER NOT NULL, " +
                "common TEXT NOT NULL, " +
                "kind TEXT NOT NULL)");

        db.execSQL("CREATE TABLE col (" +
                "id INTEGER PRIMARY KEY, " +
                "crt INTEGER NOT NULL, " +
                "mod INTEGER NOT NULL, " +
                "scm INTEGER NOT NULL, " +
                "ver INTEGER NOT NULL, " +
                "dty INTEGER NOT NULL, " +
                "usn INTEGER NOT NULL, " +
                "ls INTEGER NOT NULL, " +
                "conf TEXT NOT NULL, " +
                "models TEXT NOT NULL, " +
                "decks TEXT NOT NULL, " +
                "dconf TEXT NOT NULL, " +
                "tags TEXT NOT NULL)");

        db.execSQL("CREATE TABLE revlog (" +
                "id INTEGER PRIMARY KEY, " +
                "cid INTEGER NOT NULL, " +
                "usn INTEGER NOT NULL, " +
                "ease INTEGER NOT NULL, " +
                "ivl INTEGER NOT NULL, " +
                "lastIvl INTEGER NOT NULL, " +
                "factor INTEGER NOT NULL, " +
                "time INTEGER NOT NULL, " +
                "type INTEGER NOT NULL)");
    }

    public static long insertBasicNotetype(SQLiteDatabase db) throws JSONException {
        long notetypeId = System.currentTimeMillis() + (long)(Math.random() * 1000);
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

        db.beginTransaction();
        try {
            db.execSQL("INSERT INTO col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{
                            1,
                            System.currentTimeMillis() / 1000,
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            11,
                            0,
                            -1,
                            0,
                            "{}",
                            models.toString(),
                            "{}",
                            "{}",
                            "{}"
                    });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return notetypeId;
    }

    public static long insertDeck(SQLiteDatabase db, String deckName) {
        long deckId = System.currentTimeMillis() + (long)(Math.random() * 1000);
        db.execSQL("INSERT INTO decks (id, conf, name, mtime_secs, usn, common, kind) VALUES (?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                        deckId,
                        1,
                        deckName,
                        System.currentTimeMillis() / 1000,
                        -1,
                        "{}",
                        "default"
                });
        return deckId;
    }

    public static long insertNote(SQLiteDatabase db, String guid, long deckId, long notetypeId, String field1, String field2) {
        long noteId = System.currentTimeMillis() + (long)(Math.random() * 1000);
        String flds = field1 + "\u001f" + field2;
        String sfld = field1.length() > 0 ? field1 : field2;
        int csum = (sfld != null ? sfld.hashCode() : 0);
        long mod = System.currentTimeMillis();

        db.execSQL("INSERT INTO notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                        noteId,
                        guid,
                        notetypeId,
                        mod,
                        -1,
                        "",
                        flds,
                        sfld,
                        csum,
                        0,
                        ""
                });
        return noteId;
    }

    public static long insertCard(SQLiteDatabase db, long noteId, long deckId, int ordinal) {
        long cardId = System.currentTimeMillis() + (long)(Math.random() * 1000);
        long mod = System.currentTimeMillis();

        db.execSQL("INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                        cardId,
                        noteId,
                        deckId,
                        ordinal,
                        mod,
                        -1,
                        0,
                        0,
                        0,
                        0,
                        2500,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        ""
                });
        return cardId;
    }

    public static void updateColDecks(SQLiteDatabase db, String decksJson) {
        db.execSQL("UPDATE col SET decks = ?", new Object[]{decksJson});
    }

    public static String createMediaJson(Map<String, Integer> mediaMap) throws JSONException {
        JSONObject mediaJson = new JSONObject();
        for (Map.Entry<String, Integer> entry : mediaMap.entrySet()) {
            mediaJson.put(String.valueOf(entry.getValue()), entry.getKey());
        }
        return mediaJson.toString();
    }

    public static File generateApkg(File dbFile, Map<String, File> mediaFiles, String mediaJson, String outputFileName) throws IOException {
        File outputFile = new File(new File(dbFile.getParent()).getParentFile(), outputFileName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            ZipEntry dbEntry = new ZipEntry("collection.anki21");
            zos.putNextEntry(dbEntry);
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(dbFile))) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    zos.write(buffer, 0, bytesRead);
                }
            }
            zos.closeEntry();

            ZipEntry db2Entry = new ZipEntry("collection.anki2");
            zos.putNextEntry(db2Entry);
            zos.closeEntry();

            ZipEntry mediaEntry = new ZipEntry("media");
            zos.putNextEntry(mediaEntry);
            zos.write(mediaJson.getBytes());
            zos.closeEntry();

            for (Map.Entry<String, File> entry : mediaFiles.entrySet()) {
                ZipEntry mediaFileEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(mediaFileEntry);
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(entry.getValue()))) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }
                zos.closeEntry();
            }
        }
        return outputFile;
    }
}
