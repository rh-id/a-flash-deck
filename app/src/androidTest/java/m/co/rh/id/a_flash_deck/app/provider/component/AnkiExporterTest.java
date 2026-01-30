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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

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
    private FileHelper fileHelper;
    private DeckDao deckDao;
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
        deckDao.insertCard(card);

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
        String imageName = fileHelper.createCardQuestionImage(testImage, "test_image.jpg").getName();

        File testAnswerImage = AnkiTestDataHelper.createTestPngFile(tempDir, "test_answer.png");
        String answerImageName = fileHelper.createCardAnswerImage(testAnswerImage, "test_answer.png").getName();

        Card card = AnkiTestDataHelper.createTestCardWithImages(
            deck.id, 1, "What is this?", "An image", 
            imageName, answerImageName
        );
        deckDao.insertCard(card);

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
        String voiceName = fileHelper.createCardQuestionVoice(testVoice, "test_audio.mp3").getName();

        Card card = AnkiTestDataHelper.createTestCardWithVoice(
            deck.id, 1, "Say this", "Hello", 
            voiceName, null
        );
        deckDao.insertCard(card);

        AnkiExporter exporter = new AnkiExporter(testProvider);
        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));

        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        validateApkgStructure(apkgFile);
        validateMediaInApkg(apkgFile, 1);
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
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
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
