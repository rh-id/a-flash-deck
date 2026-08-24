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

package m.co.rh.id.a_flash_deck.base.dao;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.room.AppDatabase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class CardDaoBatchQueryTest {

    private AppDatabase db;
    private CardDao cardDao;
    private DeckDao deckDao;

    @Before
    public void createDb() {
        db = Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        cardDao = db.cardDao();
        deckDao = db.deckDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void findCardsByCardIds_returnsAllRowsBeyondChunkSize() {
        // Insert 1 deck and 1001 cards
        Deck deck = buildDeck("Test Deck");
        deckDao.insertDeck(deck);

        List<Long> insertedCardIds = new ArrayList<>();
        final Card[] excludedCard = new Card[1];
        db.runInTransaction(() -> {
            for (int i = 0; i < 1001; i++) {
                Card card = buildCard(deck.id, i);
                cardDao.insertCard(card);
                insertedCardIds.add(card.id);
            }
            // Insert one extra card in the same deck but don't add it to the query list
            excludedCard[0] = buildCard(deck.id, 1001);
            excludedCard[0].question = "q-excluded";
            excludedCard[0].answer = "a-excluded";
            cardDao.insertCard(excludedCard[0]);
        });

        // Query all cards by their IDs (this should chunk at 500, then 500, then 1)
        List<Card> result = cardDao.findCardsByCardIds(insertedCardIds);

        // Assert all 1001 cards are returned
        assertEquals(1001, result.size());

        // Assert the set of returned IDs equals the set of inserted IDs
        Set<Long> returnedIds = new HashSet<>();
        for (Card card : result) {
            returnedIds.add(card.id);
        }
        Set<Long> expectedIds = new HashSet<>(insertedCardIds);
        assertEquals(expectedIds, returnedIds);

        // Assert the excluded card is not in the results
        assertTrue(!returnedIds.contains(excludedCard[0].id));
    }

    @Test
    public void findCardsByCardIds_deduplicatesInputAcrossChunks() {
        // Insert 1 deck and 1001 cards
        Deck deck = buildDeck("Test Deck");
        deckDao.insertDeck(deck);

        List<Long> insertedCardIds = new ArrayList<>();
        db.runInTransaction(() -> {
            for (int i = 0; i < 1001; i++) {
                Card card = buildCard(deck.id, i);
                cardDao.insertCard(card);
                insertedCardIds.add(card.id);
            }
        });

        // Build input list with duplicates spanning chunk boundaries
        List<Long> inputIds = new ArrayList<>(insertedCardIds);
        // Append first 600 IDs again — without global deduplication these duplicates would span chunk boundaries and return duplicate rows
        for (int i = 0; i < 600; i++) {
            inputIds.add(insertedCardIds.get(i));
        }

        // Query with duplicate IDs
        List<Card> result = cardDao.findCardsByCardIds(inputIds);

        // Assert deduplication happened: still only 1001 unique cards returned
        assertEquals(1001, result.size());

        // Assert all returned IDs are unique
        Set<Long> returnedIds = new HashSet<>();
        for (Card card : result) {
            returnedIds.add(card.id);
        }
        assertEquals(1001, returnedIds.size());

        // Assert the returned IDs match the original inserted IDs
        Set<Long> expectedIds = new HashSet<>(insertedCardIds);
        assertEquals(expectedIds, returnedIds);
    }

    @Test
    public void findCardsByCardIds_nullAndEmptyInput() {
        // Test null input returns empty non-null list
        List<Card> nullResult = cardDao.findCardsByCardIds(null);
        assertNotNull(nullResult);
        assertEquals(0, nullResult.size());

        // Test empty input returns empty non-null list
        List<Card> emptyResult = cardDao.findCardsByCardIds(new ArrayList<>());
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.size());
    }

    @Test
    public void findCardByDeckIds_returnsAllCardsAcrossChunks() {
        // Insert 501 decks with 2 cards each (1002 cards total)
        List<Long> deckIds = new ArrayList<>();
        db.runInTransaction(() -> {
            for (int i = 0; i < 501; i++) {
                Deck deck = buildDeck("Deck " + i);
                deckDao.insertDeck(deck);
                deckIds.add(deck.id);

                // Add 2 cards to each deck
                Card card1 = buildCard(deck.id, i * 2);
                cardDao.insertCard(card1);
                Card card2 = buildCard(deck.id, i * 2 + 1);
                cardDao.insertCard(card2);
            }
        });

        // Query all cards by all 501 deck IDs (this should chunk at 500, then 1)
        List<Card> result = cardDao.findCardByDeckIds(deckIds);

        // Assert all 1002 cards are returned (501 decks * 2 cards each)
        assertEquals(1002, result.size());

        // Assert all returned cards belong to one of the queried deck IDs
        Set<Long> returnedDeckIds = new HashSet<>();
        for (Card card : result) {
            returnedDeckIds.add(card.deckId);
        }
        Set<Long> expectedDeckIds = new HashSet<>(deckIds);
        assertEquals(expectedDeckIds, returnedDeckIds);
    }

    @Test
    public void findCardsByCardIds_exactlyAtChunkSizeUsesSingleQuery() {
        // Insert 1 deck and exactly 500 cards (chunk size boundary)
        Deck deck = buildDeck("Test Deck");
        deckDao.insertDeck(deck);

        List<Long> insertedCardIds = new ArrayList<>();
        db.runInTransaction(() -> {
            for (int i = 0; i < 500; i++) {
                Card card = buildCard(deck.id, i);
                cardDao.insertCard(card);
                insertedCardIds.add(card.id);
            }
        });

        // Query all 500 cards by their IDs (exactly at the chunk-size boundary — stays on the single-query path (size <= maxQuerySize))
        List<Card> result = cardDao.findCardsByCardIds(insertedCardIds);

        // Assert all 500 cards are returned
        assertEquals(500, result.size());

        // Assert the set of returned IDs equals the set of inserted IDs
        Set<Long> returnedIds = new HashSet<>();
        for (Card card : result) {
            returnedIds.add(card.id);
        }
        Set<Long> expectedIds = new HashSet<>(insertedCardIds);
        assertEquals(expectedIds, returnedIds);
    }

    private Deck buildDeck(String name) {
        Deck deck = new Deck();
        deck.name = name;
        return deck;
    }

    private Card buildCard(Long deckId, int i) {
        Card card = new Card();
        card.deckId = deckId;
        card.question = "q" + i;
        card.answer = "a" + i;
        return card;
    }
}
