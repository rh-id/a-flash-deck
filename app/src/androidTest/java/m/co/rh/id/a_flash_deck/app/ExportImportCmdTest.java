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

package m.co.rh.id.a_flash_deck.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import m.co.rh.id.a_flash_deck.app.provider.command.ExportImportCmd;
import m.co.rh.id.a_flash_deck.app.provider.component.AnkiExporter;
import m.co.rh.id.a_flash_deck.app.provider.component.AnkiImporter;
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

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExportImportCmdTest {
    private static final String DBNAME = ExportImportCmdTest.class.getName() + "-testDb";

    private Provider testProvider;

    @Before
    public void beforeTest() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testProvider = Provider.createProvider(appContext, new ProviderModule() {
            @Override
            public void provides(ProviderRegistry providerRegistry, Provider provider) {
                providerRegistry.registerModule(new TestDatabaseProviderModule(DBNAME));
                providerRegistry.register(ExecutorService.class, Executors::newSingleThreadExecutor);
                providerRegistry.register(ILogger.class, () -> new AndroidLogger(ILogger.VERBOSE));
                providerRegistry.register(FileHelper.class, () -> new FileHelper(provider));
                providerRegistry.registerLazy(AnkiImporter.class, () -> new AnkiImporter(provider));
                providerRegistry.registerLazy(AnkiExporter.class, () -> new AnkiExporter(provider));
            }

            @Override
            public void dispose(Provider provider) {
                // leave blank
            }
        });
    }

    @After
    public void afterTest() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testProvider.dispose();
        appContext.deleteDatabase(DBNAME);
    }

    @Test
    public void exportImportFile() {
        ExportImportCmd cmd = new ExportImportCmd(testProvider);
        // prepare data
        DeckDao deckDao = testProvider.get(DeckDao.class);
        Date date = new Date();
        Deck deck = new Deck();
        deck.id = 1L;
        deck.name = "test deck";
        deck.createdDateTime = date;
        deck.updatedDateTime = date;
        Card card = new Card();
        card.id = 1L;
        card.deckId = 1L;
        card.ordinal = 1;
        card.question = "this is question";
        card.answer = "this is answer";
        Card card2 = new Card();
        card2.id = 2L;
        card2.deckId = 1L;
        card2.ordinal = 2;
        card2.question = "this is question 2";
        card2.answer = "this is answer 2";
        deckDao.insertDeck(deck);
        deckDao.insertCard(card);
        deckDao.insertCard(card2);

        File exportedFile = cmd.exportFile(Collections.singletonList(deck)).blockingGet();
        assertTrue(exportedFile.exists());

        // delete record after export
        deckDao.deleteDeck(deck);

        List<DeckModel> deckModelList = cmd.importFile(exportedFile).blockingGet();

        assertEquals(1, deckModelList.size());
        Deck deckResult = deckModelList.get(0).getDeck();
        assertNotNull(deckResult);
        assertNotEquals(deck.id, deckResult.id);
        assertEquals(deck.name, deckResult.name);
        assertEquals(deck.createdDateTime, deckResult.createdDateTime);
        assertEquals(deck.updatedDateTime, deckResult.updatedDateTime);
        ArrayList<Card> cardArrayListResult = deckModelList.get(0).getCardList();
        assertEquals(2, cardArrayListResult.size());
        Card resultCard = cardArrayListResult.get(0);
        Card resultCard2 = cardArrayListResult.get(1);
        assertNotEquals(card.id, resultCard.id);
        assertNotEquals(card.deckId, deckResult.id);
        assertEquals(card.ordinal, resultCard.ordinal);
        assertEquals(card.question, resultCard.question);
        assertEquals(card.answer, resultCard.answer);
        assertNotEquals(card.id, resultCard2.id);
        assertNotEquals(card2.deckId, deckResult.id);
        assertEquals(card2.ordinal, resultCard2.ordinal);
        assertEquals(card2.question, resultCard2.question);
        assertEquals(card2.answer, resultCard2.answer);

        // check database
        List<Deck> deckList = deckDao.getAllDecks();
        assertEquals(1, deckList.size());
        Deck dbDeck = deckList.get(0);
        assertNotEquals(deck.id, dbDeck.id);
        assertEquals(deck.name, dbDeck.name);
        assertEquals(deck.createdDateTime, dbDeck.createdDateTime);
        assertEquals(deck.updatedDateTime, dbDeck.updatedDateTime);

        List<Card> cardList = deckDao.getCardByDeckId(dbDeck.id);
        assertEquals(2, cardList.size());
        Card dbCard = cardList.get(0);
        Card dbCard2 = cardList.get(1);
        assertNotEquals(card.id, dbCard.id);
        assertNotEquals(card.deckId, dbDeck.id);
        assertEquals(card.ordinal, dbCard.ordinal);
        assertEquals(card.question, dbCard.question);
        assertEquals(card.answer, dbCard.answer);
        assertNotEquals(card2.id, dbCard2.id);
        assertNotEquals(card2.deckId, dbDeck.id);
        assertEquals(card2.ordinal, dbCard2.ordinal);
        assertEquals(card2.question, dbCard2.question);
        assertEquals(card2.answer, dbCard2.answer);
    }
}