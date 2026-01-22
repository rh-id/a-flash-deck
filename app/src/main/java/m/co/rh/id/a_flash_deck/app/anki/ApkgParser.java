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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import m.co.rh.id.a_flash_deck.app.anki.model.AnkiCard;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiDeck;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiField;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNote;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNotetype;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiTemplate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkgParser {
    private static final String TAG = ApkgParser.class.getName();

    public static SQLiteDatabase extractAndReadDatabase(File apkgFile, File tempDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            ZipEntry dbEntry = zipFile.getEntry("collection.anki21");
            if (dbEntry == null) {
                throw new IOException("collection.anki21 not found in APKG file");
            }
            File dbFile = new File(tempDir, "collection.anki21");
            try (InputStream is = zipFile.getInputStream(dbEntry);
                 BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dbFile))) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
            }
            SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            return db;
        }
    }

    public static Map<String, File> extractMediaFiles(File apkgFile, File tempDir) throws IOException {
        Map<String, File> mediaFiles = new HashMap<>();
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.matches("^\\d+$") && name.indexOf('/') < 0 && name.indexOf('\\') < 0) {
                    File mediaFile = new File(tempDir, name);
                    try (InputStream is = zipFile.getInputStream(entry);
                         BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mediaFile))) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                    }
                    mediaFiles.put(name, mediaFile);
                }
            }
        }
        return mediaFiles;
    }

    public static Map<String, String> parseMediaJson(ZipFile zipFile) throws IOException, JSONException {
        ZipEntry mediaEntry = zipFile.getEntry("media");
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
            for (String key : mediaJson.keySet()) {
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
                note.tags = cursor.getString(5);
                note.flds = cursor.getString(6);
                note.sfld = cursor.getString(7);
                note.csum = cursor.getInt(8);
                note.flags = cursor.getLong(9);
                note.data = cursor.getString(10);
                notes.add(note);
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    public static List<AnkiCard> readCards(SQLiteDatabase db, List<Long> noteIds) {
        List<AnkiCard> cards = new ArrayList<>();
        if (noteIds.isEmpty()) {
            return cards;
        }
        String[] columns = {"id", "nid", "did", "ord", "mod", "usn", "type", "queue", "due", "ivl", "factor", "reps", "lapses", "left", "odue", "odid", "flags", "data"};
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < noteIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String selection = "nid IN (" + placeholders + ")";
        String[] selectionArgs = new String[noteIds.size()];
        for (int i = 0; i < noteIds.size(); i++) {
            selectionArgs[i] = String.valueOf(noteIds.get(i));
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
                card.data = cursor.getString(17);
                cards.add(card);
            }
        } finally {
            cursor.close();
        }
        return cards;
    }

    public static List<AnkiDeck> readDecks(SQLiteDatabase db) throws JSONException {
        List<AnkiDeck> decks = new ArrayList<>();
        Cursor cursor = db.query("decks", null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                AnkiDeck deck = new AnkiDeck();
                deck.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                deck.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                deck.mtimeStamp = cursor.getLong(cursor.getColumnIndexOrThrow("mtime_secs"));
                deck.usn = cursor.getInt(cursor.getColumnIndexOrThrow("usn"));
                deck.conf = cursor.getString(cursor.getColumnIndexOrThrow("conf"));
                deck.common = cursor.getString(cursor.getColumnIndexOrThrow("common"));
                deck.kind = cursor.getString(cursor.getColumnIndexOrThrow("kind"));
                deck.children = new ArrayList<>();
                decks.add(deck);
            }
        } finally {
            cursor.close();
        }
        return decks;
    }

    public static List<AnkiNotetype> readNotetypes(SQLiteDatabase db) throws JSONException {
        List<AnkiNotetype> notetypes = new ArrayList<>();
        Cursor cursor = db.query("col", new String[]{"models"}, null, null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                String modelsJson = cursor.getString(0);
                JSONObject models = new JSONObject(modelsJson);
                for (String key : models.keys()) {
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

    public static boolean isBasicNotetype(AnkiNotetype notetype) {
        return notetype != null
                && notetype.flds != null
                && notetype.flds.size() == 2
                && "Basic".equalsIgnoreCase(notetype.name);
    }
}
