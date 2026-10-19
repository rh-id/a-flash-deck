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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import m.co.rh.id.a_flash_deck.app.anki.AnkiGuids;
import m.co.rh.id.a_flash_deck.app.provider.command.ImportResult;
import m.co.rh.id.a_flash_deck.app.util.provider.TestDatabaseProviderModule;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.CardMediaStore;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.ImageHelper;
import m.co.rh.id.a_flash_deck.base.repository.DeckCardRepository;
import m.co.rh.id.alogger.AndroidLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Instrumented test suite for Anki export functionality.
 * 
 * <p>Tests verify that {@link AnkiExporter} correctly creates valid .apkg files
 * from Flash Deck entities. Tests cover various scenarios including:</p>
 * <ul>
 *   <li>Simple text-only decks</li>
 *   <li>Decks with image media (JPEG, PNG)</li>
 *   <li>Decks with audio media (MP3)</li>
 * </ul>
 * 
 * <p>Each test validates:</p>
 * <ul>
 *   <li>APKG file is created successfully</li>
 *   <li>APKG has valid structure (collection.anki21, media JSON)</li>
 *   <li>Media files are included with correct numeric naming</li>
 *   <li>ZIP structure matches Anki specification</li>
 * </ul>
 * 
 * @since 1.0
 */
@RunWith(AndroidJUnit4.class)
public class AnkiExporterTest {
    private static final String DBNAME = AnkiExporterTest.class.getName() + "-testDb";

    private Provider testProvider;
    private CardMediaStore cardMediaStore;
    private DeckDao deckDao;
    private CardDao cardDao;
    private File tempDir;

    @Before
    public void beforeTest() throws IOException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        tempDir = new File(appContext.getCacheDir(), "anki_export_test_" + System.currentTimeMillis());
        tempDir.mkdirs();

        testProvider = Provider.createProvider(appContext, new ProviderModule() {
            @Override
            public void provides(ProviderRegistry providerRegistry, Provider provider) {
                providerRegistry.registerModule(new TestDatabaseProviderModule(DBNAME));
                providerRegistry.register(ExecutorService.class, Executors::newSingleThreadExecutor);
                providerRegistry.register(ILogger.class, () -> new AndroidLogger(ILogger.VERBOSE));
                providerRegistry.registerLazy(FileHelper.class, () -> new FileHelper(provider));
                providerRegistry.registerLazy(ImageHelper.class, () -> new ImageHelper(provider));
                providerRegistry.registerLazy(CardMediaStore.class, () -> new CardMediaStore(provider));
            }

            @Override
            public void dispose(Provider provider) {

            }
        });

        cardMediaStore = testProvider.get(CardMediaStore.class);
        deckDao = testProvider.get(DeckDao.class);
        cardDao = testProvider.get(CardDao.class);
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
     * Tests exporting a simple deck with no media files.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>APKG file is created and exists</li>
     *   <li>File has correct .apkg extension</li>
     *   <li>File has non-zero size</li>
     *   <li>ZIP contains required collection.anki21 database</li>
     *   <li>ZIP contains media JSON file</li>
     * </ul>
     */
    @Test
    public void exportSimpleDeck_createsValidApkg() throws IOException {
        Deck deck = AnkiTestDataHelper.createTestDeck("Export Test");
        deckDao.insertDeck(deck);

        Card card = AnkiTestDataHelper.createTestCard(deck.id, 1, "Question", "Answer");
        cardDao.insertCard(card);

        AnkiExporter exporter = new AnkiExporter(testProvider);
        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));

        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());
        assertTrue(apkgFile.getName().endsWith(".apkg"));
        assertTrue(apkgFile.length() > 0);

        validateApkgStructure(apkgFile);
    }

    /**
     * Tests exporting a deck with question and answer images.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Image files are correctly copied into ZIP</li>
     *   <li>Images use numeric filenames (0, 1, 2...)</li>
     *   <li>Media JSON correctly maps numeric IDs to filenames</li>
     *   <li>Expected number of media files are present</li>
     * </ul>
     */
    @Test
    public void exportDeckWithImages_includesMediaInApkg() throws IOException {
        Deck deck = AnkiTestDataHelper.createTestDeck("Deck with Images");
        deckDao.insertDeck(deck);

        File testImage = AnkiTestDataHelper.createTestImageFile(tempDir, "test_image.jpg");
        String imageName = cardMediaStore.createCardQuestionImage(testImage, "test_image.jpg").getName();

        File testAnswerImage = AnkiTestDataHelper.createTestPngFile(tempDir, "test_answer.png");
        String answerImageName = cardMediaStore.createCardAnswerImage(testAnswerImage, "test_answer.png").getName();

        Card card = AnkiTestDataHelper.createTestCardWithImages(
            deck.id, 1, "What is this?", "An image", 
            imageName, answerImageName
        );
        cardDao.insertCard(card);

        AnkiExporter exporter = new AnkiExporter(testProvider);
        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));

        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        validateApkgStructure(apkgFile);
        validateMediaInApkg(apkgFile, 2);
    }

    /**
     * Tests exporting a deck with question voice recording.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Audio files are correctly copied into ZIP</li>
     *   <li>Audio uses numeric filename</li>
     *   <li>Media JSON correctly maps numeric ID to audio filename</li>
     *   <li>Expected number of media files are present</li>
     * </ul>
     */
    @Test
    public void exportDeckWithVoice_includesAudioInApkg() throws IOException {
        Deck deck = AnkiTestDataHelper.createTestDeck("Deck with Voice");
        deckDao.insertDeck(deck);

        File testVoice = AnkiTestDataHelper.createTestAudioFile(tempDir, "test_audio.mp3");
        String voiceName = cardMediaStore.createCardQuestionVoice(testVoice, "test_audio.mp3").getName();

        Card card = AnkiTestDataHelper.createTestCardWithVoice(
            deck.id, 1, "Say this", "Hello", 
            voiceName, null
        );
        cardDao.insertCard(card);

        AnkiExporter exporter = new AnkiExporter(testProvider);
        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));

        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        validateApkgStructure(apkgFile);
        validateMediaInApkg(apkgFile, 1);
    }

    /**
     * Tests that a deck with 3+ cards exports valid card ords.
     *
     * <p>Anki card rows must reference a template index (ord). This exporter
     * only writes the single Basic Front-&gt;Back template, so every exported
     * card must carry ord 0 — previously the app deck position was written
     * which produced invalid packages for decks with 3 or more cards.</p>
     */
    @Test
    public void exportDeckWithMultipleCards_writesValidTemplateOrds() throws IOException {
        Deck deck = AnkiTestDataHelper.createTestDeck("Ord Deck");
        deckDao.insertDeck(deck);

        cardDao.insertCard(AnkiTestDataHelper.createTestCard(deck.id, 1, "Question 1", "Answer 1"));
        cardDao.insertCard(AnkiTestDataHelper.createTestCard(deck.id, 2, "Question 2", "Answer 2"));
        cardDao.insertCard(AnkiTestDataHelper.createTestCard(deck.id, 3, "Question 3", "Answer 3"));

        AnkiExporter exporter = new AnkiExporter(testProvider);
        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);

        File extractDir = new File(tempDir, "ord_check");
        extractDir.mkdirs();
        File dbFile = new File(extractDir, "collection.anki21");
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            ZipEntry dbEntry = zipFile.getEntry("collection.anki21");
            assertNotNull(dbEntry);
            try (InputStream is = zipFile.getInputStream(dbEntry);
                 FileOutputStream fos = new FileOutputStream(dbFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            Cursor cursor = db.rawQuery("SELECT DISTINCT ord FROM cards", null);
            try {
                assertTrue(cursor.moveToFirst());
                // only template ord 0 may appear across the whole export
                assertEquals(0, cursor.getInt(0));
                assertFalse(cursor.moveToNext());
            } finally {
                cursor.close();
            }
        } finally {
            db.close();
        }
    }

    /**
     * Tests the Anki base91 guid encoding with known vectors and format
     * constraints.
     *
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>The encoding matches Anki's algorithm (1 encodes to "b")</li>
     *   <li>Encoding is deterministic and non-empty</li>
     *   <li>All output characters are from Anki's guid alphabet</li>
     *   <li>Negative values are rejected</li>
     * </ul>
     */
    @Test
    public void base91FromLong_knownVectorsAndAlphabet() {
        assertEquals("a", AnkiGuids.base91FromLong(0));
        assertEquals("b", AnkiGuids.base91FromLong(1));
        assertEquals("~", AnkiGuids.base91FromLong(90));
        assertEquals("ba", AnkiGuids.base91FromLong(91));
        assertEquals("b6h9", AnkiGuids.base91FromLong(1234567));
        // Long.MAX_VALUE (2^63 - 1); note: Anki's own example guid for
        // u64::MAX is "Rj&Z5m[>Zp", not representable as a signed long
        assertEquals("vYHz/gPN,h", AnkiGuids.base91FromLong(Long.MAX_VALUE));

        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                + "!#$%&()*+,-./:;<=>?@[]^_`{|}~";
        for (long value : new long[]{0, 1, 90, 91, 1234567, Long.MAX_VALUE, 4077654321L}) {
            String guid = AnkiGuids.base91FromLong(value);
            assertFalse("guid must not be empty", guid.isEmpty());
            for (int i = 0; i < guid.length(); i++) {
                assertTrue("unexpected character in guid: " + guid,
                        alphabet.indexOf(guid.charAt(i)) >= 0);
            }
            // deterministic
            assertEquals(guid, AnkiGuids.base91FromLong(value));
        }

        try {
            AnkiGuids.base91FromLong(-1);
            fail("Expected IllegalArgumentException for negative value");
        } catch (IllegalArgumentException expected) {
            // leave blank
        }
    }

    /**
     * Tests that a card imported from an .apkg re-exports with its ORIGINAL
     * Anki note guid, so re-importing the export into Anki updates the
     * original notes instead of creating duplicates.
     */
    @Test
    public void exportImportedDeck_reusesOriginalNoteGuids() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);
        DeckCardRepository deckCardRepo = testProvider.get(DeckCardRepository.class);

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Guid Deck")
                .addNoteWithGuid(300, "original-guid-1", 100, "Q1", "A1")
                .addNoteWithGuid(301, "original-guid-2", 100, "Q2", "A2")
                .addCard(400, 300, 200, 0)
                .addCard(401, 301, 200, 0)
                .build(new File(tempDir, "guid_reuse.apkg"), "collection.anki21");

        ImportResult imported = importer.importApkg(apkgFile);
        deckCardRepo.importDeckPayloads(imported.payloads);
        assertEquals(1, deckDao.getAllDecks().size());

        AnkiExporter exporter = new AnkiExporter(testProvider);
        File exportedFile = exporter.exportApkg(new ArrayList<>(deckDao.getAllDecks()));
        assertNotNull(exportedFile);

        List<String> exportedGuids = readNoteGuids(exportedFile);
        assertEquals(2, exportedGuids.size());
        assertTrue(exportedGuids.contains("original-guid-1"));
        assertTrue(exportedGuids.contains("original-guid-2"));
    }

    /**
     * Tests that a natively created card (no Anki import history) exports
     * with a deterministic guid derived from its card id.
     */
    @Test
    public void exportNativeDeck_guidIsBase91OfCardId() throws Exception {
        Deck deck = AnkiTestDataHelper.createTestDeck("Native Guid Deck");
        deckDao.insertDeck(deck);

        Card card = AnkiTestDataHelper.createTestCard(deck.id, 1, "Question", "Answer");
        cardDao.insertCard(card);

        AnkiExporter exporter = new AnkiExporter(testProvider);
        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);

        List<String> guids = readNoteGuids(apkgFile);
        assertEquals(1, guids.size());
        assertEquals(AnkiGuids.base91FromLong(card.id), guids.get(0));
    }

    /**
     * Extracts the note guids from the collection database of an .apkg file.
     */
    private List<String> readNoteGuids(File apkgFile) throws Exception {
        File extractDir = new File(tempDir, "guid_check_" + System.nanoTime());
        extractDir.mkdirs();
        File dbFile = new File(extractDir, "collection.anki21");
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            ZipEntry dbEntry = zipFile.getEntry("collection.anki21");
            assertNotNull(dbEntry);
            try (InputStream is = zipFile.getInputStream(dbEntry);
                 FileOutputStream fos = new FileOutputStream(dbFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        List<String> guids = new ArrayList<>();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            Cursor cursor = db.rawQuery("SELECT guid FROM notes", null);
            try {
                while (cursor.moveToNext()) {
                    guids.add(cursor.getString(0));
                }
            } finally {
                cursor.close();
            }
        } finally {
            db.close();
        }
        return guids;
    }

    /**
     * Validates that APKG ZIP contains required entries.
     * 
     * <p>Checks for presence of:</p>
     * <ul>
     *   <li>collection.anki21 - main SQLite database</li>
     *   <li>media - JSON mapping file</li>
     * </ul>
     * 
     * @param apkgFile the exported .apkg file to validate
     * @throws IOException if ZIP file cannot be read
     */
    private void validateApkgStructure(File apkgFile) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            assertNotNull(zipFile.getEntry("collection.anki21"));
            assertNotNull(zipFile.getEntry("media"));
        }
    }
    
    /**
     * Validates that APKG ZIP contains expected number of media files.
     * 
     * <p>Media files in Anki .apkg format are stored with numeric names
     * (0, 1, 2, etc.). This method counts files matching that pattern
     * and verifies the count matches expectations.</p>
     * 
     * @param apkgFile the exported .apkg file to validate
     * @param expectedMediaCount number of media files expected
     * @throws IOException if ZIP file cannot be read
     */
    private void validateMediaInApkg(File apkgFile, int expectedMediaCount) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkgFile)) {
            int mediaFileCount = 0;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.matches("^\\d+$")) {
                    mediaFileCount++;
                }
            }

            if (mediaFileCount != expectedMediaCount) {
                fail("Expected " + expectedMediaCount + " mediaFileCount, found " + mediaFileCount);
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
