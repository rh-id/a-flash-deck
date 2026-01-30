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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Instrumented test suite for Anki round-trip functionality.
 * 
 * <p>Round-trip tests verify bidirectional compatibility between Flash Deck
 * and Anki formats. Each test:</p>
 * 
 * <ol>
 *   <li>Creates test data in Flash Deck format</li>
 *   <li>Exports to .apkg format using {@link AnkiExporter}</li>
 *   <li>Deletes original Flash Deck data</li>
 *   <li>Imports back using {@link AnkiImporter}</li>
 *   <li>Verifies all data is preserved correctly</li>
 * </ol>
 * 
 * <p>This comprehensive testing ensures:</p>
 * <ul>
 *   <li>Data integrity through format conversion</li>
 *   <li>Media files (images, audio) are preserved</li>
 *   <li>Text content (question, answer) is unchanged</li>
 *   <li>Card ordering is maintained</li>
 * </ul>
 * 
 * @since 1.0
 */
@RunWith(AndroidJUnit4.class)
public class AnkiRoundTripTest {
    private static final String DBNAME = AnkiRoundTripTest.class.getName() + "-testDb";

    private Provider testProvider;
    private FileHelper fileHelper;
    private DeckDao deckDao;
    private File tempDir;

    @Before
    public void beforeTest() throws IOException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        tempDir = new File(appContext.getCacheDir(), "anki_roundtrip_test_" + System.currentTimeMillis());
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
     * Tests complete round-trip of simple text-only deck.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Deck name is preserved</li>
     *   <li>All cards are exported and imported</li>
     *   <li>Question text is unchanged</li>
     *   <li>Answer text is unchanged</li>
     *   <li>Card ordinal positions are maintained</li>
     *   <li>Media fields remain null (no false positives)</li>
     *   <li>New IDs are assigned (no ID collision)</li>
     * </ul>
     */
    @Test
    public void roundTrip_simpleDeck_preservesAllData() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck originalDeck = AnkiTestDataHelper.createTestDeck("Original Deck");
        deckDao.insertDeck(originalDeck);

        Card originalCard1 = AnkiTestDataHelper.createTestCard(originalDeck.id, 1, "Q1", "A1");
        Card originalCard2 = AnkiTestDataHelper.createTestCard(originalDeck.id, 2, "Q2", "A2");
        deckDao.insertCard(originalCard1);
        deckDao.insertCard(originalCard2);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(originalDeck)));

        deckDao.deleteDeck(originalDeck);

        List<DeckModel> importedDecks = importer.importApkg(apkgFile);
        assertEquals(1, importedDecks.size());

        DeckModel importedDeckModel = importedDecks.get(0);
        Deck importedDeck = importedDeckModel.getDeck();

        assertEquals(originalDeck.name, importedDeck.name);
        assertNotEquals(originalDeck.id, importedDeck.id);

        List<Card> importedCards = importedDeckModel.getCardList();
        assertEquals(2, importedCards.size());

        Card importedCard1 = importedCards.get(0);
        Card importedCard2 = importedCards.get(1);

        assertEquals(originalCard1.question, importedCard1.question);
        assertEquals(originalCard1.answer, importedCard1.answer);
        assertEquals(originalCard1.ordinal, importedCard1.ordinal);
        assertNull(importedCard1.questionImage);
        assertNull(importedCard1.answerImage);
        assertNull(importedCard1.questionVoice);
        assertNull(importedCard1.answerVoice);

        assertEquals(originalCard2.question, importedCard2.question);
        assertEquals(originalCard2.answer, importedCard2.answer);
        assertEquals(originalCard2.ordinal, importedCard2.ordinal);
    }

    /**
     * Tests round-trip of deck with question and answer images.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Both question and answer images are preserved</li>
     *   <li>Image files are correctly extracted and re-copied</li>
     *   <li>Text content remains unchanged</li>
     * </ul>
     */
    @Test
    public void roundTrip_deckWithImages_preservesMedia() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck originalDeck = AnkiTestDataHelper.createTestDeck("Deck with Images");
        deckDao.insertDeck(originalDeck);

        File testImage1 = AnkiTestDataHelper.createTestImageFile(tempDir, "question_image.jpg");
        String questionImageName = fileHelper.createCardQuestionImage(testImage1, "question_image.jpg").getName();

        File testImage2 = AnkiTestDataHelper.createTestPngFile(tempDir, "answer_image.png");
        String answerImageName = fileHelper.createCardAnswerImage(testImage2, "answer_image.png").getName();

        Card originalCard = AnkiTestDataHelper.createTestCardWithImages(
            originalDeck.id, 1, "What color is this?", "Blue", 
            questionImageName, answerImageName
        );
        deckDao.insertCard(originalCard);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(originalDeck)));

        deckDao.deleteDeck(originalDeck);

        List<DeckModel> importedDecks = importer.importApkg(apkgFile);
        assertEquals(1, importedDecks.size());

        DeckModel importedDeckModel = importedDecks.get(0);
        Card importedCard = importedDeckModel.getCardList().get(0);

        assertEquals(originalCard.question, importedCard.question);
        assertEquals(originalCard.answer, importedCard.answer);
        assertNotNull(importedCard.questionImage);
        assertNotNull(importedCard.answerImage);
    }

    /**
     * Tests round-trip of deck with question and answer voice recordings.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Both question and answer audio are preserved</li>
     *   <li>Audio files are correctly extracted and re-copied</li>
     *   <li>Text content remains unchanged</li>
     * </ul>
     */
    @Test
    public void roundTrip_deckWithVoice_preservesAudio() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck originalDeck = AnkiTestDataHelper.createTestDeck("Deck with Voice");
        deckDao.insertDeck(originalDeck);

        File testVoice1 = AnkiTestDataHelper.createTestAudioFile(tempDir, "question_audio.mp3");
        String questionVoiceName = fileHelper.createCardQuestionVoice(testVoice1, "question_audio.mp3").getName();

        File testVoice2 = AnkiTestDataHelper.createTestAudioFile(tempDir, "answer_audio.mp3");
        String answerVoiceName = fileHelper.createCardAnswerVoice(testVoice2, "answer_audio.mp3").getName();

        Card originalCard = AnkiTestDataHelper.createTestCardWithVoice(
            originalDeck.id, 1, "Listen and repeat", "Test", 
            questionVoiceName, answerVoiceName
        );
        deckDao.insertCard(originalCard);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(originalDeck)));

        deckDao.deleteDeck(originalDeck);

        List<DeckModel> importedDecks = importer.importApkg(apkgFile);
        assertEquals(1, importedDecks.size());

        DeckModel importedDeckModel = importedDecks.get(0);
        Card importedCard = importedDeckModel.getCardList().get(0);

        assertEquals(originalCard.question, importedCard.question);
        assertEquals(originalCard.answer, importedCard.answer);
        assertNotNull(importedCard.questionVoice);
        assertNotNull(importedCard.answerVoice);
    }

    /**
     * Tests round-trip of complete deck with all data types.
     * 
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Mixed content (text + image + audio) is preserved</li>
     *   <li>Question media (both image and audio) are preserved</li>
     *   <li>Answer media remains null (not corrupted)</li>
     *   <li>Ordinal position is maintained</li>
     *   <li>Text content is unchanged</li>
     * </ul>
     */
    @Test
    public void roundTrip_completeDeck_preservesAllDataTypes() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck originalDeck = AnkiTestDataHelper.createTestDeck("Complete Test Deck");
        deckDao.insertDeck(originalDeck);

        File testImage = AnkiTestDataHelper.createTestImageFile(tempDir, "test_img.jpg");
        String imageName = fileHelper.createCardQuestionImage(testImage, "test_img.jpg").getName();

        File testVoice = AnkiTestDataHelper.createTestAudioFile(tempDir, "test_audio.mp3");
        String voiceName = fileHelper.createCardQuestionVoice(testVoice, "test_audio.mp3").getName();

        Card originalCard = new Card();
        originalCard.deckId = originalDeck.id;
        originalCard.ordinal = 1;
        originalCard.question = "Complete card";
        originalCard.answer = "With all media";
        originalCard.questionImage = imageName;
        originalCard.questionVoice = voiceName;
        originalCard.answerImage = null;
        originalCard.answerVoice = null;
        originalCard.isReversibleQA = false;
        originalCard.isReversed = false;
        deckDao.insertCard(originalCard);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(originalDeck)));

        deckDao.deleteDeck(originalDeck);

        List<DeckModel> importedDecks = importer.importApkg(apkgFile);
        assertEquals(1, importedDecks.size());

        DeckModel importedDeckModel = importedDecks.get(0);
        Card importedCard = importedDeckModel.getCardList().get(0);

        assertEquals(originalCard.question, importedCard.question);
        assertEquals(originalCard.answer, importedCard.answer);
        assertEquals(originalCard.ordinal, importedCard.ordinal);
        assertNotNull(importedCard.questionImage);
        assertNotNull(importedCard.questionVoice);
        assertNull(importedCard.answerImage);
        assertNull(importedCard.answerVoice);
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
