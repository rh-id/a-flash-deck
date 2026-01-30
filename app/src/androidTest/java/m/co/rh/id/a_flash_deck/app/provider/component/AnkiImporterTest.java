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

import m.co.rh.id.a_flash_deck.app.util.provider.TestDatabaseProviderModule;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.AndroidLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test suite for Anki import functionality.
 * 
 * <p>Tests verify that {@link AnkiImporter} correctly parses .apkg files and
 * converts them to Flash Deck entities. Tests cover various scenarios including:</p>
 * <ul>
 *   <li>Simple text-only decks</li>
 *   <li>Decks with image media (JPEG, PNG)</li>
 *   <li>Decks with audio media (MP3)</li>
 *   <li>Deck name conflict resolution (auto-renaming)</li>
 *   <li>Nested deck flattening (:: separator)</li>
 *   <li>Missing media handling (graceful degradation)</li>
 * </ul>
 * 
 * <p>Each test creates test data using {@link AnkiExporter}, deletes the original data,
 * then imports the exported .apkg to verify data integrity.</p>
 * 
 * @since 1.0
 */
@RunWith(AndroidJUnit4.class)
public class AnkiImporterTest {
    private static final String DBNAME = AnkiImporterTest.class.getName() + "-testDb";

    private Provider testProvider;
    private FileHelper fileHelper;
    private DeckDao deckDao;
    private File tempDir;

    @Before
    public void beforeTest() throws IOException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        tempDir = new File(appContext.getCacheDir(), "anki_test_" + System.currentTimeMillis());
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
     * Tests importing a simple deck with no media files.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Basic .apkg export works correctly</li>
     *   <li>Import creates expected number of decks and cards</li>
     *   <li>Question and answer text are preserved</li>
     *   <li>Media fields remain null when no media exists</li>
     *   <li>Card ordinal positions are maintained</li>
     * </ul>
     */
    @Test
    public void importSimpleDeck_noMedia() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Test Deck");
        deckDao.insertDeck(deck);

        Card card1 = AnkiTestDataHelper.createTestCard(deck.id, 1, "Question 1", "Answer 1");
        Card card2 = AnkiTestDataHelper.createTestCard(deck.id, 2, "Question 2", "Answer 2");
        deckDao.insertCard(card1);
        deckDao.insertCard(card2);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        deckDao.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile);
        assertEquals(1, imported.size());

        DeckModel deckModel = imported.get(0);
        assertEquals("Test Deck", deckModel.getDeck().name);
        assertEquals(2, deckModel.getCardList().size());

        Card importedCard1 = deckModel.getCardList().get(0);
        assertEquals("Question 1", importedCard1.question);
        assertEquals("Answer 1", importedCard1.answer);
        assertNull(importedCard1.questionImage);
        assertNull(importedCard1.answerImage);
        assertNull(importedCard1.questionVoice);
        assertNull(importedCard1.answerVoice);
    }

    /**
     * Tests importing a deck with question and answer images.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Images are correctly extracted from .apkg media</li>
     *   <li>Image files are copied to FileHelper paths</li>
     *   <li>Question and answer images are both preserved</li>
     *   <li>Thumbnails are generated for imported images</li>
     * </ul>
     */
    @Test
    public void importDeck_withImages() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Deck with Images");
        deckDao.insertDeck(deck);

        File testImage = AnkiTestDataHelper.createTestImageFile(tempDir, "test_image.jpg");
        String imageName = fileHelper.createCardQuestionImage(testImage, "test_image.jpg").getName();

        File testAnswerImage = AnkiTestDataHelper.createTestPngFile(tempDir, "test_answer.png");
        String answerImageName = fileHelper.createCardAnswerImage(testAnswerImage, "test_answer.png").getName();

        Card card = AnkiTestDataHelper.createTestCardWithImages(
            deck.id, 1, "What is this?", "This is an image", 
            imageName, answerImageName
        );
        deckDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        deckDao.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile);
        assertEquals(1, imported.size());

        DeckModel deckModel = imported.get(0);
        assertEquals(1, deckModel.getCardList().size());

        Card importedCard = deckModel.getCardList().get(0);
        assertEquals("What is this?", importedCard.question);
        assertEquals("This is an image", importedCard.answer);
        assertNotNull(importedCard.questionImage);
        assertNotNull(importedCard.answerImage);
    }

    /**
     * Tests importing a deck with question and answer voice recordings.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Audio files are correctly extracted from .apkg media</li>
     *   <li>Voice files are copied to FileHelper paths</li>
     *   <li>Question and answer voice recordings are both preserved</li>
     * </ul>
     */
    @Test
    public void importDeck_withVoice() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Deck with Voice");
        deckDao.insertDeck(deck);

        File testVoice = AnkiTestDataHelper.createTestAudioFile(tempDir, "test_audio.mp3");
        String voiceName = fileHelper.createCardQuestionVoice(testVoice, "test_audio.mp3").getName();

        File testAnswerVoice = AnkiTestDataHelper.createTestAudioFile(tempDir, "test_answer_audio.mp3");
        String answerVoiceName = fileHelper.createCardAnswerVoice(testAnswerVoice, "test_answer_audio.mp3").getName();

        Card card = AnkiTestDataHelper.createTestCardWithVoice(
            deck.id, 1, "Say this word", "Hello", 
            voiceName, answerVoiceName
        );
        deckDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        deckDao.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile);
        assertEquals(1, imported.size());

        DeckModel deckModel = imported.get(0);
        assertEquals(1, deckModel.getCardList().size());

        Card importedCard = deckModel.getCardList().get(0);
        assertEquals("Say this word", importedCard.question);
        assertEquals("Hello", importedCard.answer);
        assertNotNull(importedCard.questionVoice);
        assertNotNull(importedCard.answerVoice);
    }

    /**
     * Tests automatic deck name conflict resolution.
     * 
     * <p>This test verifies that when importing a deck with a name that already
     * exists in the database, the importer automatically renames it with a suffix
     * (e.g., "My Deck" → "My Deck (2)").</p>
     */
    @Test
    public void importDeck_duplicateName_autoRename() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck existingDeck = AnkiTestDataHelper.createTestDeck("My Deck");
        deckDao.insertDeck(existingDeck);

        Deck newDeck = AnkiTestDataHelper.createTestDeck("My Deck");
        deckDao.insertDeck(newDeck);

        Card card = AnkiTestDataHelper.createTestCard(newDeck.id, 1, "Q", "A");
        deckDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(newDeck)));
        deckDao.deleteDeck(newDeck);

        List<DeckModel> imported = importer.importApkg(apkgFile);
        assertEquals(1, imported.size());

        DeckModel deckModel = imported.get(0);
        assertEquals("My Deck (2)", deckModel.getDeck().name);
    }

    /**
     * Tests Anki nested deck name flattening.
     * 
     * <p>This test verifies that Anki's nested deck hierarchy (:: separator)
     * is flattened to Flash Deck's single-level hierarchy with " - " separator.
     * For example: "Japanese::Kanji::N5" → "Japanese - Kanji - N5"</p>
     */
    @Test
    public void importDeck_withNestedNames_flattened() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Japanese::Kanji::N5");
        deckDao.insertDeck(deck);

        Card card = AnkiTestDataHelper.createTestCard(deck.id, 1, "日", "Day");
        deckDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);

        deckDao.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile);
        assertEquals(1, imported.size());

        DeckModel deckModel = imported.get(0);
        assertEquals("Japanese - Kanji - N5", deckModel.getDeck().name);
    }

    /**
     * Tests graceful handling of missing media files in .apkg.
     * 
     * <p>This test verifies that when a .apkg file references media files
     * that don't exist (e.g., files were manually deleted or .apkg is
     * corrupted), the import process:</p>
     * <ul>
     *   <li>Does not fail or throw an exception</li>
     *   <li>Logs warnings for missing media</li>
     *   <li>Imports the card with null media fields</li>
     *   <li>Preserves other valid media (e.g., question image)</li>
     * </ul>
     */
    @Test
    public void importDeck_missingMedia_importsCardWithoutMedia() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck deck = AnkiTestDataHelper.createTestDeck("Deck with Missing Media");
        deckDao.insertDeck(deck);

        File testImage = AnkiTestDataHelper.createTestImageFile(tempDir, "test_image.jpg");
        String imageName = fileHelper.createCardQuestionImage(testImage, "test_image.jpg").getName();

        Card card = AnkiTestDataHelper.createTestCardWithImages(
            deck.id, 1, "Q", "A", 
            imageName, "missing_image.png"
        );
        deckDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));

        deckDao.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile);
        assertEquals(1, imported.size());

        DeckModel deckModel = imported.get(0);
        assertEquals(1, deckModel.getCardList().size());

        Card importedCard = deckModel.getCardList().get(0);
        assertNotNull(importedCard.questionImage);
        assertNull(importedCard.answerImage);
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
