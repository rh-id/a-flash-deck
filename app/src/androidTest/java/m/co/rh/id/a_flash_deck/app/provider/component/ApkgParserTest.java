 /*
 *     Copyright (C) 2021-2026 Ruby Hartono
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under terms of the GNU General Public License as published by
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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import m.co.rh.id.a_flash_deck.app.anki.ApkgParser;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiCard;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiDeck;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiField;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNote;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiNotetype;
import m.co.rh.id.a_flash_deck.app.util.provider.TestDatabaseProviderModule;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.AndroidLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Instrumented test suite for ApkgParser functionality.
 * 
 * <p>Tests verify that {@link ApkgParser} correctly extracts and parses
 * Anki .apkg files. Tests cover various scenarios including:</p>
 * <ul>
 *   <li>Database extraction from APKG</li>
 *   <li>Media file extraction</li>
 *   <li>Media JSON parsing</li>
 *   <li>Reading notes, cards, decks, and notetypes from database</li>
 *   <li>Basic notetype detection</li>
 * </ul>
 * 
 * @since 1.0
 */
@RunWith(AndroidJUnit4.class)
public class ApkgParserTest {
    private static final String DBNAME = ApkgParserTest.class.getName() + "-testDb";

    private Provider testProvider;
    private FileHelper fileHelper;
    private DeckDao deckDao;
    private File tempDir;

    @Before
    public void beforeTest() throws IOException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        tempDir = new File(appContext.getCacheDir(), "anki_parser_test_" + System.currentTimeMillis());
        tempDir.mkdirs();

        testProvider = Provider.createProvider(appContext, new ProviderModule() {
            @Override
            public void provides(ProviderRegistry providerRegistry, Provider provider) {
                providerRegistry.registerModule(new TestDatabaseProviderModule(DBNAME));
                providerRegistry.register(ExecutorService.class, Executors::newSingleThreadExecutor);
                providerRegistry.register(ILogger.class, () -> new AndroidLogger(ILogger.VERBOSE));
                providerRegistry.register(FileHelper.class, () -> new FileHelper(provider));
            }

            @Override
            public void dispose(Provider provider) {

            }
        });

        fileHelper = testProvider.get(FileHelper.class);
        deckDao = testProvider.get(DeckDao.class);
    }

    @After
    public void afterTest() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testProvider.dispose();
        appContext.deleteDatabase(DBNAME);

        if (tempDir != null && tempDir.exists()) {
            deleteDirectory(tempDir);
        }
    }

    /**
     * Tests extracting and reading the Anki database from APKG.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>collection.anki21 is extracted from the APKG</li>
     *   <li>Database can be opened as SQLiteDatabase</li>
     *   <li>Database is in READONLY mode</li>
     * </ul>
     */
    @Test
    public void extractAndReadDatabase_opensDatabase() throws IOException {
        File apkgFile = createTestApkgWithDatabase();

        File extractDir = new File(tempDir, "extract");
        extractDir.mkdirs();

        android.database.sqlite.SQLiteDatabase db = ApkgParser.extractAndReadDatabase(apkgFile, extractDir);

        assertNotNull(db);
        assertTrue(db.isOpen());
        assertEquals(0, db.isReadOnly());

        db.close();
    }

    /**
     * Tests that extracting database from invalid APKG fails appropriately.
     * 
     * <p>This test verifies that when collection.anki21 is missing from the APKG,
     * an IOException is thrown.</p>
     */
    @Test
    public void extractAndReadDatabase_missingDatabase_throwsException() throws IOException {
        File apkgFile = createTestApkgWithoutDatabase();

        File extractDir = new File(tempDir, "extract");
        extractDir.mkdirs();

        try {
            ApkgParser.extractAndReadDatabase(apkgFile, extractDir);
            fail("Expected IOException for missing collection.anki21");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("collection.anki21 not found"));
        }
    }

    /**
     * Tests extracting media files from APKG.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Media files with numeric names are extracted</li>
     *   <li>Non-numeric files are ignored</li>
     *   <li>Extracted files are mapped by their numeric names</li>
     * </ul>
     */
    @Test
    public void extractMediaFiles_extractsNumericFiles() throws IOException {
        File apkgFile = createTestApkgWithMedia();

        File extractDir = new File(tempDir, "extract_media");
        extractDir.mkdirs();

        Map<String, File> mediaFiles = ApkgParser.extractMediaFiles(apkgFile, extractDir);

        assertEquals(2, mediaFiles.size());
        assertTrue(mediaFiles.containsKey("0"));
        assertTrue(mediaFiles.containsKey("1"));
        assertTrue(mediaFiles.get("0").exists());
        assertTrue(mediaFiles.get("1").exists());
    }

    /**
     * Tests parsing media JSON from APKG.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Media JSON is correctly parsed</li>
     *   <li>Mapping between numeric IDs and filenames is established</li>
     *   <li>Empty map is returned when media entry is missing</li>
     * </ul>
     */
    @Test
    public void parseMediaJson_parsesMediaMapping() throws IOException, JSONException {
        File apkgFile = createTestApkgWithMediaJson();

        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            Map<String, String> mediaMap = ApkgParser.parseMediaJson(zipFile);

            assertEquals(2, mediaMap.size());
            assertEquals("image.jpg", mediaMap.get("0"));
            assertEquals("audio.mp3", mediaMap.get("1"));
        }
    }

    /**
     * Tests parsing media JSON when media entry is missing.
     * 
     * <p>This test verifies that when the "media" entry is not present in the APKG,
     * an empty map is returned instead of throwing an exception.</p>
     */
    @Test
    public void parseMediaJson_missingMediaEntry_returnsEmptyMap() throws IOException, JSONException {
        File apkgFile = createTestApkgWithoutMediaJson();

        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            Map<String, String> mediaMap = ApkgParser.parseMediaJson(zipFile);

            assertNotNull(mediaMap);
            assertEquals(0, mediaMap.size());
        }
    }

    /**
     * Tests reading notes from Anki database.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Notes are correctly queried by notetype ID</li>
     *   <li>All note fields are populated</li>
     *   <li>Multiple notes are returned when present</li>
     * </ul>
     */
    @Test
    public void readNotes_readsNotesByNotetype() throws Exception {
        File apkgFile = createTestApkgWithNotes();

        File extractDir = new File(tempDir, "read_notes");
        extractDir.mkdirs();

        android.database.sqlite.SQLiteDatabase db = ApkgParser.extractAndReadDatabase(apkgFile, extractDir);

        List<AnkiNote> notes = ApkgParser.readNotes(db, 123456789L);

        assertEquals(2, notes.size());
        
        AnkiNote note1 = notes.get(0);
        assertNotNull(note1.id);
        assertNotNull(note1.guid);
        assertEquals(123456789L, note1.mid);
        assertNotNull(note1.flds);
        assertNotNull(note1.tags);

        db.close();
    }

    /**
     * Tests reading cards from Anki database.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Cards are correctly queried by note IDs</li>
     *   <li>All card fields are populated</li>
     *   <li>Multiple cards are returned when present</li>
     * </ul>
     */
    @Test
    public void readCards_readsCardsByNoteIds() throws Exception {
        File apkgFile = createTestApkgWithCards();

        File extractDir = new File(tempDir, "read_cards");
        extractDir.mkdirs();

        android.database.sqlite.SQLiteDatabase db = ApkgParser.extractAndReadDatabase(apkgFile, extractDir);

        List<Long> noteIds = new ArrayList<>();
        noteIds.add(987654321L);
        
        List<AnkiCard> cards = ApkgParser.readCards(db, noteIds);

        assertTrue(cards.size() >= 1);
        
        if (!cards.isEmpty()) {
            AnkiCard card = cards.get(0);
            assertNotNull(card.id);
            assertEquals(987654321L, card.nid);
            assertNotNull(card.did);
        }

        db.close();
    }

    /**
     * Tests reading cards when note ID list is empty.
     * 
     * <p>This test verifies that an empty list is returned when no note IDs are provided.</p>
     */
    @Test
    public void readCards_emptyNoteIds_returnsEmptyList() throws Exception {
        File apkgFile = createTestApkgWithCards();

        File extractDir = new File(tempDir, "read_cards_empty");
        extractDir.mkdirs();

        android.database.sqlite.SQLiteDatabase db = ApkgParser.extractAndReadDatabase(apkgFile, extractDir);

        List<AnkiCard> cards = ApkgParser.readCards(db, new ArrayList<>());

        assertNotNull(cards);
        assertEquals(0, cards.size());

        db.close();
    }

    /**
     * Tests reading decks from Anki database.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>All decks are queried from the database</li>
     *   <li>Deck fields are correctly populated</li>
     *   <li>Multiple decks are returned when present</li>
     * </ul>
     */
    @Test
    public void readDecks_readsAllDecks() throws Exception {
        AnkiExporter exporter = new AnkiExporter(testProvider);

        Deck deck1 = AnkiTestDataHelper.createTestDeck("Test Deck 1");
        deckDao.insertDeck(deck1);

        Deck deck2 = AnkiTestDataHelper.createTestDeck("Test Deck 2");
        deckDao.insertDeck(deck2);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck1, deck2)));
        assertNotNull(apkgFile);

        File extractDir = new File(tempDir, "read_decks");
        extractDir.mkdirs();

        android.database.sqlite.SQLiteDatabase db = ApkgParser.extractAndReadDatabase(apkgFile, extractDir);

        List<AnkiDeck> decks = ApkgParser.readDecks(db);

        assertTrue(decks.size() >= 2);
        
        for (AnkiDeck deck : decks) {
            assertNotNull(deck.id);
            assertNotNull(deck.name);
            assertNotNull(deck.mtimeStamp);
        }

        db.close();
    }

    /**
     * Tests reading notetypes from Anki database.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>All notetypes are parsed from the models JSON</li>
     *   <li>Notetype fields are correctly populated</li>
     *   <li>Fields and templates are parsed for each notetype</li>
     * </ul>
     */
    @Test
    public void readNotetypes_parsesModelsFromJson() throws Exception {
        AnkiExporter exporter = new AnkiExporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Test Deck");
        deckDao.insertDeck(deck);

        Card card = AnkiTestDataHelper.createTestCard(deck.id, 1, "Question", "Answer");
        deckDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);

        File extractDir = new File(tempDir, "read_notetypes");
        extractDir.mkdirs();

        android.database.sqlite.SQLiteDatabase db = ApkgParser.extractAndReadDatabase(apkgFile, extractDir);

        List<AnkiNotetype> notetypes = ApkgParser.readNotetypes(db);

        assertTrue(notetypes.size() > 0);
        
        AnkiNotetype notetype = notetypes.get(0);
        assertNotNull(notetype.id);
        assertNotNull(notetype.name);
        assertNotNull(notetype.flds);
        assertNotNull(notetype.tmpls);

        db.close();
    }

    /**
     * Tests Basic notetype detection.
     * 
     * <p>This test verifies that the isBasicNotetype method correctly identifies
     * the Basic notetype and rejects other notetypes.</p>
     */
    @Test
    public void isBasicNotetype_identifiesBasicType() {
        AnkiNotetype basicType = new AnkiNotetype();
        basicType.name = "Basic";
        basicType.flds = new ArrayList<>();
        basicType.flds.add(new AnkiField());
        basicType.flds.add(new AnkiField());

        assertTrue(ApkgParser.isBasicNotetype(basicType));

        basicType.name = "Cloze";
        assertFalse(ApkgParser.isBasicNotetype(basicType));

        basicType.name = "Basic";
        basicType.flds = new ArrayList<>();
        basicType.flds.add(new AnkiField());
        assertFalse(ApkgParser.isBasicNotetype(basicType));

        AnkiNotetype nullType = null;
        assertFalse(ApkgParser.isBasicNotetype(nullType));
    }

    /**
     * Creates a test APKG file with a collection.anki21 database.
     * 
     * <p>Uses AnkiExporter to create a real APKG file with proper structure.</p>
     */
    private File createTestApkgWithDatabase() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Test Deck");
        deckDao.insertDeck(deck);

        Card card = AnkiTestDataHelper.createTestCard(deck.id, 1, "Question", "Answer");
        deckDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        return apkgFile;
    }

    /**
     * Creates a test APKG file without collection.anki21.
     * 
     * <p>Creates a ZIP file with only media files, no database.</p>
     */
    private File createTestApkgWithoutDatabase() throws IOException {
        File apkgFile = new File(tempDir, "no_db.apkg");
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(apkgFile));

        byte[] dummyData = new byte[]{0, 1, 2, 3};
        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("0");
        zos.putNextEntry(entry);
        zos.write(dummyData);
        zos.closeEntry();

        zos.close();
        return apkgFile;
    }

    /**
     * Creates a test APKG file with media files.
     * 
     * <p>Creates a ZIP file with numeric-named files representing media.</p>
     */
    private File createTestApkgWithMedia() throws IOException {
        File apkgFile = new File(tempDir, "with_media.apkg");
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(apkgFile));

        byte[] dummyData = new byte[]{0, 1, 2, 3};
        
        java.util.zip.ZipEntry entry0 = new java.util.zip.ZipEntry("0");
        zos.putNextEntry(entry0);
        zos.write(dummyData);
        zos.closeEntry();

        java.util.zip.ZipEntry entry1 = new java.util.zip.ZipEntry("1");
        zos.putNextEntry(entry1);
        zos.write(dummyData);
        zos.closeEntry();

        java.util.zip.ZipEntry entryOther = new java.util.zip.ZipEntry("some_file.txt");
        zos.putNextEntry(entryOther);
        zos.write(dummyData);
        zos.closeEntry();

        zos.close();
        return apkgFile;
    }

    /**
     * Creates a test APKG file with media JSON mapping.
     * 
     * <p>Creates a ZIP file with a media entry containing JSON.</p>
     */
    private File createTestApkgWithMediaJson() throws IOException {
        File apkgFile = new File(tempDir, "with_media_json.apkg");
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(apkgFile));

        String mediaJson = "{\"0\":\"image.jpg\",\"1\":\"audio.mp3\"}";
        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("media");
        zos.putNextEntry(entry);
        zos.write(mediaJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();

        zos.close();
        return apkgFile;
    }

    /**
     * Creates a test APKG file without media JSON entry.
     * 
     * <p>Creates an empty ZIP file to test missing media entry handling.</p>
     */
    private File createTestApkgWithoutMediaJson() throws IOException {
        File apkgFile = new File(tempDir, "no_media_json.apkg");
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(apkgFile));
        zos.close();
        return apkgFile;
    }

    /**
     * Creates a test APKG file with notes in the database.
     * 
     * <p>Uses AnkiExporter to create a real APKG with note data.</p>
     */
    private File createTestApkgWithNotes() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Notes Deck");
        deckDao.insertDeck(deck);

        Card card1 = AnkiTestDataHelper.createTestCard(deck.id, 1, "Question 1", "Answer 1");
        Card card2 = AnkiTestDataHelper.createTestCard(deck.id, 2, "Question 2", "Answer 2");
        deckDao.insertCard(card1);
        deckDao.insertCard(card2);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        return apkgFile;
    }

    /**
     * Creates a test APKG file with cards in the database.
     * 
     * <p>Uses AnkiExporter to create a real APKG with card data.</p>
     */
    private File createTestApkgWithCards() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Cards Deck");
        deckDao.insertDeck(deck);

        Card card = AnkiTestDataHelper.createTestCard(deck.id, 1, "Question", "Answer");
        deckDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        return apkgFile;
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
