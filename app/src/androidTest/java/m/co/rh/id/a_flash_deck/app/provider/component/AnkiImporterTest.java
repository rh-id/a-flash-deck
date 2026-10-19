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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiImportSummary;
import m.co.rh.id.a_flash_deck.app.util.provider.TestDatabaseProviderModule;
import m.co.rh.id.a_flash_deck.app.provider.command.ImportResult;
import m.co.rh.id.a_flash_deck.base.dao.AnkiNoteGuidDao;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    private CardMediaStore cardMediaStore;
    private DeckDao deckDao;
    private CardDao cardDao;
    private AnkiNoteGuidDao ankiNoteGuidDao;
    private DeckCardRepository deckCardRepo;
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
        ankiNoteGuidDao = testProvider.get(AnkiNoteGuidDao.class);
        deckCardRepo = testProvider.get(DeckCardRepository.class);
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
        cardDao.insertCard(card1);
        cardDao.insertCard(card2);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        deckCardRepo.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile).decks;
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
     * Regression test: a GENUINE schema-11 package (decks in the col.decks
     * JSON, no decks table — the shape every real Anki legacy export has)
     * imports with the correct deck name instead of failing with
     * "no such table: decks". Also verifies the reversed-notetype mapping
     * still applies when the genuine package uses "Basic (and reversed
     * card)" with two card rows per note.
     */
    @Test
    public void importGenuineSchema11Package_deckNameResolvedFromColJson() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = AnkiApkgTestBuilder.genuine(tempDir)
                .addNotetype(100, "Basic (and reversed card)", 0, new String[]{"Front", "Back"}, 2)
                .addDeck(200, "Genuine Import Deck")
                .addNoteWithGuid(300, "genuine-guid-1", 100, "Genuine Q", "Genuine A")
                .addCard(400, 300, 200, 0)
                .addCard(401, 300, 200, 1)
                .build(new File(tempDir, "genuine_import.apkg"), "collection.anki21");

        List<DeckModel> imported = importer.importApkg(apkgFile).decks;
        assertEquals(1, imported.size());

        DeckModel deckModel = imported.get(0);
        assertEquals("Genuine Import Deck", deckModel.getDeck().name);
        assertEquals(1, deckModel.getCardList().size());

        Card importedCard = deckModel.getCardList().get(0);
        assertTrue(importedCard.isReversibleQA);
        assertEquals("Genuine Q", importedCard.question);
        assertEquals("Genuine A", importedCard.answer);
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
        String imageName = cardMediaStore.createCardQuestionImage(testImage, "test_image.jpg").getName();

        File testAnswerImage = AnkiTestDataHelper.createTestPngFile(tempDir, "test_answer.png");
        String answerImageName = cardMediaStore.createCardAnswerImage(testAnswerImage, "test_answer.png").getName();

        Card card = AnkiTestDataHelper.createTestCardWithImages(
            deck.id, 1, "What is this?", "This is an image", 
            imageName, answerImageName
        );
        cardDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        deckCardRepo.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile).decks;
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
        String voiceName = cardMediaStore.createCardQuestionVoice(testVoice, "test_audio.mp3").getName();

        File testAnswerVoice = AnkiTestDataHelper.createTestAudioFile(tempDir, "test_answer_audio.mp3");
        String answerVoiceName = cardMediaStore.createCardAnswerVoice(testAnswerVoice, "test_answer_audio.mp3").getName();

        Card card = AnkiTestDataHelper.createTestCardWithVoice(
            deck.id, 1, "Say this word", "Hello", 
            voiceName, answerVoiceName
        );
        cardDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        deckCardRepo.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile).decks;
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
        cardDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(newDeck)));
        deckCardRepo.deleteDeck(newDeck);

        List<DeckModel> imported = importer.importApkg(apkgFile).decks;
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
        cardDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));
        assertNotNull(apkgFile);

        deckCardRepo.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile).decks;
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
        String imageName = cardMediaStore.createCardQuestionImage(testImage, "test_image.jpg").getName();

        Card card = AnkiTestDataHelper.createTestCardWithImages(
            deck.id, 1, "Q", "A", 
            imageName, "missing_image.png"
        );
        cardDao.insertCard(card);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck)));

        deckCardRepo.deleteDeck(deck);

        List<DeckModel> imported = importer.importApkg(apkgFile).decks;
        assertEquals(1, imported.size());

        DeckModel deckModel = imported.get(0);
        assertEquals(1, deckModel.getCardList().size());

        Card importedCard = deckModel.getCardList().get(0);
        assertNotNull(importedCard.questionImage);
        assertNull(importedCard.answerImage);
    }

    /**
     * Tests importing an APKG with multiple decks.
     *
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>APKG export works correctly with multiple decks in a single file</li>
     *   <li>Import creates expected number of decks with their respective cards</li>
     *   <li>All deck names are preserved</li>
     *   <li>Each deck's cards are correctly imported</li>
     * </ul>
     */
    @Test
    public void importMultipleDecks_bothImported() throws IOException {
        AnkiExporter exporter = new AnkiExporter(testProvider);
        AnkiImporter importer = new AnkiImporter(testProvider);

        Deck deck1 = AnkiTestDataHelper.createTestDeck("First Deck");
        deckDao.insertDeck(deck1);

        Card card1 = AnkiTestDataHelper.createTestCard(deck1.id, 1, "Question 1", "Answer 1");
        cardDao.insertCard(card1);

        Deck deck2 = AnkiTestDataHelper.createTestDeck("Second Deck");
        deckDao.insertDeck(deck2);

        Card card2 = AnkiTestDataHelper.createTestCard(deck2.id, 1, "Question 2", "Answer 2");
        cardDao.insertCard(card2);

        File apkgFile = exporter.exportApkg(new ArrayList<>(List.of(deck1, deck2)));
        assertNotNull(apkgFile);
        assertTrue(apkgFile.exists());

        deckCardRepo.deleteDeck(deck1);
        deckCardRepo.deleteDeck(deck2);

        List<DeckModel> imported = importer.importApkg(apkgFile).decks;
        assertEquals(2, imported.size());

        DeckModel deckModel1 = null;
        DeckModel deckModel2 = null;
        for (DeckModel dm : imported) {
            if ("First Deck".equals(dm.getDeck().name)) {
                deckModel1 = dm;
            } else if ("Second Deck".equals(dm.getDeck().name)) {
                deckModel2 = dm;
            }
        }
        assertNotNull(deckModel1);
        assertNotNull(deckModel2);

        assertEquals(1, deckModel1.getCardList().size());

        Card importedCard1 = deckModel1.getCardList().get(0);
        assertEquals("Question 1", importedCard1.question);
        assertEquals("Answer 1", importedCard1.answer);
        assertNull(importedCard1.questionImage);
        assertNull(importedCard1.answerImage);
        assertNull(importedCard1.questionVoice);
        assertNull(importedCard1.answerVoice);

        assertEquals(1, deckModel2.getCardList().size());

        Card importedCard2 = deckModel2.getCardList().get(0);
        assertEquals("Question 2", importedCard2.question);
        assertEquals("Answer 2", importedCard2.answer);
        assertNull(importedCard2.questionImage);
        assertNull(importedCard2.answerImage);
        assertNull(importedCard2.questionVoice);
        assertNull(importedCard2.answerVoice);
    }

    /**
     * Tests importing a "Basic (and reversed card)" notetype package.
     *
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Notes of the reversed Basic variant are imported</li>
     *   <li>Exactly ONE app card is created per note even though the notetype
     *       has two card rows (forward and reversed)</li>
     *   <li>The imported card is flagged as reversible QA</li>
     *   <li>The summary reports the import without warnings</li>
     * </ul>
     */
    @Test
    public void importReversedNotetype_singleReversibleCard() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic (and reversed card)", 0, new String[]{"Front", "Back"}, 2)
                .addDeck(200, "Reversed Deck")
                .addNote(300, 100, "Front A", "Back A")
                .addCard(400, 300, 200, 0)
                .addCard(401, 300, 200, 1)
                .build(new File(tempDir, "reversed.apkg"), "collection.anki21");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(1, result.summary.decksImported);
        assertEquals(1, result.summary.notesImported);
        assertEquals(1, result.summary.cardsImported);
        assertFalse(result.summary.hasWarnings());

        DeckModel deckModel = result.decks.get(0);
        assertEquals("Reversed Deck", deckModel.getDeck().name);
        assertEquals(1, deckModel.getCardList().size());

        Card importedCard = deckModel.getCardList().get(0);
        assertEquals("Front A", importedCard.question);
        assertEquals("Back A", importedCard.answer);
        assertTrue(importedCard.isReversibleQA);
        assertFalse(importedCard.isReversed);
    }

    /**
     * Tests importing a package that contains both a Basic notetype and a
     * Cloze notetype.
     *
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>Basic notes are imported normally</li>
     *   <li>Cloze notes are NOT imported but reported in the summary as
     *       skipped notetype with their note count</li>
     * </ul>
     */
    @Test
    public void importMixedClozePackage_skipsClozeInSummary() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addNotetype(101, "Cloze", 1, new String[]{"Text"}, 1)
                .addDeck(200, "Mixed Deck")
                .addNote(300, 100, "What is 2+2?", "4")
                .addCard(400, 300, 200, 0)
                .addNote(301, 101, "{{c1::Berlin}} is the capital of Germany")
                .addCard(401, 301, 200, 0)
                .build(new File(tempDir, "mixed_cloze.apkg"), "collection.anki21");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(1, result.summary.decksImported);
        assertEquals(1, result.summary.notesImported);
        assertEquals(1, result.summary.cardsImported);
        assertTrue(result.summary.hasWarnings());
        assertEquals(1, result.summary.skippedNotetypes.size());

        AnkiImportSummary.SkippedNotetype skipped = result.summary.skippedNotetypes.get(0);
        assertEquals("Cloze", skipped.name);
        assertEquals(1, skipped.noteCount);
        assertEquals("cloze", skipped.reason);

        DeckModel deckModel = result.decks.get(0);
        assertEquals(1, deckModel.getCardList().size());
        assertEquals("What is 2+2?", deckModel.getCardList().get(0).question);
    }

    /**
     * Tests importing a package with ONLY cloze notes: nothing is importable,
     * so the import fails with a clear validation error.
     */
    @Test
    public void importClozeOnlyPackage_throwsNothingImported() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Cloze", 1, new String[]{"Text"}, 1)
                .addDeck(200, "Cloze Deck")
                .addNote(300, 100, "{{c1::Berlin}} is the capital of Germany")
                .addCard(400, 300, 200, 0)
                .build(new File(tempDir, "cloze_only.apkg"), "collection.anki21");

        try {
            importer.importApkg(apkgFile);
            fail("Expected ValidationException for cloze-only package");
        } catch (ValidationException e) {
            assertEquals(appContext.getString(R.string.error_nothing_imported), e.getMessage());
        }
    }

    /**
     * Tests importing a legacy 2.0 package whose zip contains ONLY
     * collection.anki2.
     *
     * <p>This test verifies that legacy Anki 2.0 packages import fine.</p>
     */
    @Test
    public void importLegacyAnki2Package_importsCards() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Legacy Deck")
                .addNote(300, 100, "Legacy Q", "Legacy A")
                .addCard(400, 300, 200, 0)
                .build(new File(tempDir, "legacy.apkg"), "collection.anki2");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());

        DeckModel deckModel = result.decks.get(0);
        assertEquals("Legacy Deck", deckModel.getDeck().name);
        assertEquals(1, deckModel.getCardList().size());

        Card importedCard = deckModel.getCardList().get(0);
        assertEquals("Legacy Q", importedCard.question);
        assertEquals("Legacy A", importedCard.answer);
        assertFalse(importedCard.isReversibleQA);
    }

    /**
     * Tests importing the modern decoy package (collection.anki2 with only the
     * "Please update to the latest Anki version" note): must fail with a clear
     * error instead of silently importing nothing.
     */
    @Test
    public void importDecoyPackage_throwsDecoyError() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front"}, 1)
                .addNote(300, 100, "Please update to the latest Anki version")
                .build(new File(tempDir, "decoy.apkg"), "collection.anki2");

        try {
            importer.importApkg(apkgFile);
            fail("Expected ValidationException for decoy package");
        } catch (ValidationException e) {
            assertEquals(appContext.getString(R.string.error_anki_decoy_package), e.getMessage());
        }
    }

    /**
     * Tests importing a modern-only package whose collection.anki21b payload
     * is NOT valid zstd: must fail with the modern-package error
     * (R.string.error_anki_modern_package) rather than crashing.
     */
    @Test
    public void importCorruptedModernZstdPackage_throwsModernPackageError() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        File apkgFile = AnkiApkgTestBuilder.buildModernOnly(
                new File(tempDir, "modern.apkg"));

        try {
            importer.importApkg(apkgFile);
            fail("Expected ValidationException for modern zstd package");
        } catch (ValidationException e) {
            assertEquals(appContext.getString(R.string.error_anki_modern_package), e.getMessage());
        }
    }

    /**
     * Tests that extra images/sounds per card side are counted as dropped
     * media in the summary (only the first image/audio per side is supported),
     * while the first image/audio of each side is kept.
     */
    @Test
    public void importDeckWithExtraMedia_countsDroppedMedia() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        // real decodable media content, the importer re-compresses images
        // when copying them into app storage
        Map<String, byte[]> mediaContents = new HashMap<>();
        mediaContents.put("0", readFileBytes(
                AnkiTestDataHelper.createTestImageFile(tempDir, "first.jpg")));
        mediaContents.put("1", readFileBytes(
                AnkiTestDataHelper.createTestAudioFile(tempDir, "first.mp3")));
        mediaContents.put("2", readFileBytes(
                AnkiTestDataHelper.createTestImageFile(tempDir, "second.jpg")));
        mediaContents.put("3", readFileBytes(
                AnkiTestDataHelper.createTestAudioFile(tempDir, "second.mp3")));

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Media Deck")
                .addNote(300, 100,
                        "Question<img src=\"first.jpg\"><img src=\"second.jpg\">[sound:first.mp3][sound:second.mp3]",
                        "Answer")
                .addCard(400, 300, 200, 0)
                .withMedia(mediaContents,
                        "{\"0\":\"first.jpg\",\"1\":\"first.mp3\",\"2\":\"second.jpg\",\"3\":\"second.mp3\"}")
                .build(new File(tempDir, "extra_media.apkg"), "collection.anki21");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(1, deckModelCardCount(result));
        assertEquals(2, result.summary.droppedMediaCount);
        assertTrue(result.summary.hasWarnings());

        DeckModel deckModel = result.decks.get(0);
        Card importedCard = deckModel.getCardList().get(0);
        // first image and sound of the question side are kept (renamed)
        assertNotNull(importedCard.questionImage);
        assertNotNull(importedCard.questionVoice);
    }

    /**
     * Tests importing a modern default export package (collection.anki21b,
     * schema 18, zstd compressed): cards import with correct deck and text,
     * and re-importing the same file skips everything as guid duplicates.
     */
    @Test
    public void importModernAnki21bPackage_importsCards_andGuidDedupOnReimport() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = AnkiApkgTestBuilder.modern(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Modern Deck")
                .addNote(300, 100, "Modern Q", "Modern A")
                .addCard(400, 300, 200, 0)
                .build(new File(tempDir, "modern_import.apkg"), "collection.anki21b");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(1, result.summary.notesImported);
        assertEquals(1, result.summary.cardsImported);
        assertEquals(1, result.summary.decksImported);

        DeckModel deckModel = result.decks.get(0);
        assertEquals("Modern Deck", deckModel.getDeck().name);
        assertEquals(1, deckModel.getCardList().size());
        Card importedCard = deckModel.getCardList().get(0);
        assertEquals("Modern Q", importedCard.question);
        assertEquals("Modern A", importedCard.answer);
        assertFalse(importedCard.isReversibleQA);

        // guid of the imported note is stored, a re-import is fully deduped
        deckCardRepo.importDeckPayloads(result.payloads);
        assertEquals(1, deckDao.getAllDecks().size());
        assertEquals(1, cardDao.getCardByDeckId(deckDao.getAllDecks().get(0).id).size());

        ImportResult reimport = importer.importApkg(apkgFile);
        assertEquals(0, reimport.decks.size());
        assertEquals(1, reimport.summary.duplicateNotesSkipped);
        assertEquals(0, reimport.summary.cardsImported);
    }

    /**
     * Tests that "Basic (and reversed card)" notes of a modern package map
     * to reversible app cards.
     */
    @Test
    public void importModernAnki21bReversedPackage_reversibleCard() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = AnkiApkgTestBuilder.modern(tempDir)
                .addNotetype(100, "Basic (and reversed card)", 0, new String[]{"Front", "Back"}, 2)
                .addDeck(200, "Reversed Deck")
                .addNote(300, 100, "Front Q", "Back A")
                .addCard(400, 300, 200, 0)
                .addCard(401, 300, 200, 1)
                .build(new File(tempDir, "modern_reversed.apkg"), "collection.anki21b");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(1, result.summary.notesImported);
        assertEquals(1, deckModelCardCount(result));

        Card importedCard = result.decks.get(0).getCardList().get(0);
        assertEquals("Front Q", importedCard.question);
        assertTrue(importedCard.isReversibleQA);
    }

    /**
     * Tests that cloze notes of a modern package (kind = 1 inside the
     * notetypes.config protobuf blob) are skipped and reported in the
     * summary.
     */
    @Test
    public void importModernAnki21bClozePackage_skippedInSummary() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = AnkiApkgTestBuilder.modern(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addNotetype(101, "Cloze", 1, new String[]{"Text"}, 1)
                .addDeck(200, "Mixed Modern Deck")
                .addNote(300, 100, "What is 2+2?", "4")
                .addCard(400, 300, 200, 0)
                .addNote(301, 101, "{{c1::Berlin}} is the capital of Germany")
                .addCard(401, 301, 200, 0)
                .build(new File(tempDir, "modern_mixed.apkg"), "collection.anki21b");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(1, result.summary.notesImported);
        assertTrue(result.summary.hasWarnings());
        assertEquals(1, result.summary.skippedNotetypes.size());

        AnkiImportSummary.SkippedNotetype skipped = result.summary.skippedNotetypes.get(0);
        assertEquals("Cloze", skipped.name);
        assertEquals(1, skipped.noteCount);
        assertEquals("cloze", skipped.reason);

        DeckModel deckModel = result.decks.get(0);
        assertEquals(1, deckModel.getCardList().size());
        assertEquals("What is 2+2?", deckModel.getCardList().get(0).question);
    }

    /**
     * Tests modern media handling end to end: images and sounds referenced
     * by a note round through the zstd compressed media manifest and zstd
     * compressed zip contents and end up imported into app storage.
     */
    @Test
    public void importModernAnki21bWithMedia_extractsMedia() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        LinkedHashMap<String, byte[]> mediaByName = new LinkedHashMap<>();
        mediaByName.put("pic.jpg", readFileBytes(
                AnkiTestDataHelper.createTestImageFile(tempDir, "pic.jpg")));
        mediaByName.put("sound.mp3", readFileBytes(
                AnkiTestDataHelper.createTestAudioFile(tempDir, "sound.mp3")));

        File apkgFile = AnkiApkgTestBuilder.modern(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Modern Media Deck")
                .addNote(300, 100,
                        "Question<img src=\"pic.jpg\">",
                        "Answer[sound:sound.mp3]")
                .addCard(400, 300, 200, 0)
                .withModernMedia(mediaByName)
                .build(new File(tempDir, "modern_media_import.apkg"), "collection.anki21b");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(1, deckModelCardCount(result));
        assertEquals(0, result.summary.droppedMediaCount);

        Card importedCard = result.decks.get(0).getCardList().get(0);
        assertNotNull(importedCard.questionImage);
        assertNotNull(importedCard.answerVoice);
        assertNull(importedCard.questionVoice);
        assertNull(importedCard.answerImage);
    }

    /**
     * Tests importing a big deck with 1100 notes (exceeds SQLite's 999 host
     * parameter limit, forcing the chunked nid IN (...) query).
     */
    @Test(timeout = 120000)
    public void importBigDeck_1100Notes_allImported() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        int noteCount = 1100;
        AnkiApkgTestBuilder builder = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Big Deck");
        builder.runInTransaction(() -> {
            for (int i = 0; i < noteCount; i++) {
                builder.addNote(1000L + i, 100, "Q" + i, "A" + i);
                builder.addCard(5000L + i, 1000L + i, 200, 0);
            }
        });
        File apkgFile = builder.build(new File(tempDir, "big.apkg"), "collection.anki21");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(noteCount, result.summary.notesImported);
        assertEquals(noteCount, result.summary.cardsImported);
        assertEquals(1, result.summary.decksImported);

        DeckModel deckModel = result.decks.get(0);
        assertEquals(noteCount, deckModel.getCardList().size());
        assertEquals("Q0", deckModel.getCardList().get(0).question);
        assertEquals("Q1099", deckModel.getCardList().get(noteCount - 1).question);
    }

    /**
     * Tests guid-based duplicate detection: importing the same .apkg twice
     * must not create any new deck or card the second time.
     *
     * <p>This test verifies that:</p>
     * <ul>
     *   <li>The first import imports all notes</li>
     *   <li>The second import returns no decks and reports every note as
     *       skipped duplicate</li>
     *   <li>No empty duplicate deck is created (deck and card counts
     *       unchanged)</li>
     *   <li>The duplicate count is part of the summary dialog message</li>
     * </ul>
     */
    @Test
    public void reimportSameApkg_duplicatesSkipped_noEmptyDeckCreated() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Dup Deck")
                .addNoteWithGuid(300, "guid-A", 100, "Q1", "A1")
                .addNoteWithGuid(301, "guid-B", 100, "Q2", "A2")
                .addCard(400, 300, 200, 0)
                .addCard(401, 301, 200, 0)
                .build(new File(tempDir, "dup_reimport.apkg"), "collection.anki21");

        // first import: everything is new
        ImportResult first = importer.importApkg(apkgFile);
        assertEquals(1, first.decks.size());
        assertEquals(0, first.summary.duplicateNotesSkipped);
        assertFalse(first.summary.hasWarnings());
        deckCardRepo.importDeckPayloads(first.payloads);

        assertEquals(1, deckDao.getAllDecks().size());
        assertEquals(2, cardDao.getCardByDeckId(deckDao.getAllDecks().get(0).id).size());

        // second import of the same file: everything is a duplicate
        ImportResult second = importer.importApkg(apkgFile);
        assertEquals(0, second.decks.size());
        assertEquals(0, deckModelCardCount(second));
        assertEquals(2, second.summary.duplicateNotesSkipped);
        assertEquals(0, second.summary.decksImported);
        assertEquals(0, second.summary.cardsImported);
        // duplicates alone are not a warning, but they are reported
        assertFalse(second.summary.hasWarnings());
        assertTrue(second.buildSummaryMessage(appContext)
                .contains(appContext.getString(R.string.import_summary_duplicates_skipped, 2)));

        deckCardRepo.importDeckPayloads(second.payloads);
        assertEquals(1, deckDao.getAllDecks().size());
        assertEquals(2, cardDao.getCardByDeckId(deckDao.getAllDecks().get(0).id).size());
    }

    /**
     * Tests a partial re-import: the package contains one edited note (new
     * guid) and one unchanged note. Only the edited note is imported, into
     * a renamed deck (name conflict logic untouched); the unchanged note is
     * skipped as duplicate.
     */
    @Test
    public void reimportApkgWithChangedGuid_onlyNewNoteImported() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File v1Dir = new File(tempDir, "partial_v1");
        File v2Dir = new File(tempDir, "partial_v2");
        assertTrue(v1Dir.mkdirs() || v1Dir.isDirectory());
        assertTrue(v2Dir.mkdirs() || v2Dir.isDirectory());

        new AnkiApkgTestBuilder(v1Dir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Partial Deck")
                .addNoteWithGuid(300, "guid-keep-1", 100, "Q1", "A1")
                .addNoteWithGuid(301, "guid-keep-2", 100, "Q2", "A2")
                .addCard(400, 300, 200, 0)
                .addCard(401, 301, 200, 0)
                .build(new File(tempDir, "partial_v1.apkg"), "collection.anki21");

        ImportResult first = importer.importApkg(new File(tempDir, "partial_v1.apkg"));
        assertEquals(1, first.decks.size());
        deckCardRepo.importDeckPayloads(first.payloads);

        // same deck and one unchanged note, the second note got a new guid
        // (edited note replaces guid-keep-2)
        new AnkiApkgTestBuilder(v2Dir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Partial Deck")
                .addNoteWithGuid(300, "guid-keep-1", 100, "Q1", "A1")
                .addNoteWithGuid(301, "guid-changed", 100, "Q2 edited", "A2")
                .addCard(400, 300, 200, 0)
                .addCard(401, 301, 200, 0)
                .build(new File(tempDir, "partial_v2.apkg"), "collection.anki21");

        ImportResult second = importer.importApkg(new File(tempDir, "partial_v2.apkg"));
        assertEquals(1, second.decks.size());
        assertEquals(1, deckModelCardCount(second));
        assertEquals(1, second.summary.duplicateNotesSkipped);
        assertEquals(1, second.summary.decksImported);
        assertEquals(1, second.summary.cardsImported);

        // deck rename conflict logic stays: the new card lands in a new deck
        DeckModel deckModel = second.decks.get(0);
        assertEquals("Partial Deck (2)", deckModel.getDeck().name);
        assertEquals("Q2 edited", deckModel.getCardList().get(0).question);

        deckCardRepo.importDeckPayloads(second.payloads);
        assertEquals(2, deckDao.getAllDecks().size());
        assertEquals(3, cardDao.getCardByDeckId(deckDao.getAllDecks().get(0).id).size()
                + cardDao.getCardByDeckId(deckDao.getAllDecks().get(1).id).size());
    }

    /**
     * Tests within-file duplicate detection: the same guid occurring twice
     * in one imported file must import only the first occurrence and count
     * the second as skipped duplicate.
     */
    @Test
    public void importApkgWithDuplicateGuidsWithinFile_secondOccurrenceSkipped() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Within File Dup Deck")
                .addNoteWithGuid(300, "guid-dup", 100, "Q1", "A1")
                .addNoteWithGuid(301, "guid-dup", 100, "Q2", "A2")
                .addCard(400, 300, 200, 0)
                .addCard(401, 301, 200, 0)
                .build(new File(tempDir, "within_file_dup.apkg"), "collection.anki21");

        ImportResult result = importer.importApkg(apkgFile);
        assertEquals(1, result.decks.size());
        assertEquals(1, deckModelCardCount(result));
        assertEquals(1, result.summary.notesImported);
        assertEquals(1, result.summary.duplicateNotesSkipped);

        deckCardRepo.importDeckPayloads(result.payloads);
        assertEquals(1, deckDao.getAllDecks().size());
        assertEquals(1, cardDao.getCardByDeckId(deckDao.getAllDecks().get(0).id).size());
    }

    /**
     * Tests that the stored guids cascade with their cards: after deleting
     * the imported deck the same .apkg file imports fully again.
     */
    @Test
    public void reimportAfterDeckDelete_importsFullyAgain() throws Exception {
        AnkiImporter importer = new AnkiImporter(testProvider);

        File apkgFile = new AnkiApkgTestBuilder(tempDir)
                .addNotetype(100, "Basic", 0, new String[]{"Front", "Back"}, 1)
                .addDeck(200, "Cascade Deck")
                .addNoteWithGuid(300, "guid-cascade-1", 100, "Q1", "A1")
                .addNoteWithGuid(301, "guid-cascade-2", 100, "Q2", "A2")
                .addCard(400, 300, 200, 0)
                .addCard(401, 301, 200, 0)
                .build(new File(tempDir, "cascade.apkg"), "collection.anki21");

        // first import stores the guids
        ImportResult first = importer.importApkg(apkgFile);
        deckCardRepo.importDeckPayloads(first.payloads);
        assertEquals(2, ankiNoteGuidDao.findExistingGuids(
                List.of("guid-cascade-1", "guid-cascade-2")).size());

        // deleting the deck removes the cards and cascades the guid rows
        deckCardRepo.deleteDeck(deckDao.getAllDecks().get(0));
        assertEquals(0, ankiNoteGuidDao.findExistingGuids(
                List.of("guid-cascade-1", "guid-cascade-2")).size());

        // so the same file imports fully again
        ImportResult second = importer.importApkg(apkgFile);
        assertEquals(1, second.decks.size());
        assertEquals(2, deckModelCardCount(second));
        assertEquals(0, second.summary.duplicateNotesSkipped);

        deckCardRepo.importDeckPayloads(second.payloads);
        assertEquals(1, deckDao.getAllDecks().size());
        assertEquals(2, cardDao.getCardByDeckId(deckDao.getAllDecks().get(0).id).size());
    }

    private int deckModelCardCount(ImportResult result) {
        int count = 0;
        for (DeckModel deckModel : result.decks) {
            count += deckModel.getCardList().size();
        }
        return count;
    }

    private byte[] readFileBytes(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
        }
        return baos.toByteArray();
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
