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

package m.co.rh.id.a_flash_deck.base;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.CardReviewStateDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.StudyDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.room.AppDatabase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class StudyDaoDueQueryTest {

    private AppDatabase db;
    private DeckDao deckDao;
    private CardDao cardDao;
    private CardReviewStateDao reviewStateDao;
    private StudyDao studyDao;

    @Before
    public void createDb() {
        db = Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        deckDao = db.deckDao();
        cardDao = db.cardDao();
        reviewStateDao = db.cardReviewStateDao();
        studyDao = db.studyDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void findDueAndNewCardsByDeckIds_includesNewAndDueCardsOfRequestedDecksOnly() {
        Deck deck = buildDeck("Deck A");
        Deck otherDeck = buildDeck("Deck B");
        deckDao.insertDeck(deck);
        deckDao.insertDeck(otherDeck);

        // no review-state row -> new card, selectable
        Card newCard = buildCard(deck.id, 0);
        // due date in the past -> due, selectable
        Card dueCard = buildCard(deck.id, 1);
        // due date in the future -> not selectable
        Card futureCard = buildCard(deck.id, 2);
        // suspended -> not selectable
        Card suspendedCard = buildCard(deck.id, 3);
        // due card of a different deck -> excluded when filtering by deck ids
        Card otherDeckCard = buildCard(otherDeck.id, 4);
        cardDao.insertCard(newCard);
        cardDao.insertCard(dueCard);
        cardDao.insertCard(futureCard);
        cardDao.insertCard(suspendedCard);
        cardDao.insertCard(otherDeckCard);

        long now = System.currentTimeMillis();
        insertState(dueCard, now - 1, false);
        insertState(futureCard, now + 60000, false);
        insertState(suspendedCard, now - 1, true);
        insertState(otherDeckCard, now - 1, false);

        List<Long> deckIds = Collections.singletonList(deck.id);
        List<Card> result = studyDao.findDueAndNewCardsByDeckIds(deckIds, now);

        Set<Long> resultIds = new HashSet<>();
        for (Card card : result) {
            resultIds.add(card.id);
        }
        assertEquals(new HashSet<>(Arrays.asList(newCard.id, dueCard.id)), resultIds);

        assertEquals(2, studyDao.countDueAndNewCardsBySingleDeckId(deck.id, now));
        assertEquals(1, studyDao.countDueAndNewCardsBySingleDeckId(otherDeck.id, now));
    }

    @Test
    public void findDueAndNewCardsByDeckIds_nullAndEmptyInput() {
        List<Card> nullResult = studyDao.findDueAndNewCardsByDeckIds(null,
                System.currentTimeMillis());
        assertNotNull(nullResult);
        assertEquals(0, nullResult.size());

        List<Card> emptyResult = studyDao.findDueAndNewCardsByDeckIds(new ArrayList<>(),
                System.currentTimeMillis());
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.size());
    }

    @Test
    public void countDueAndNewCardsBySingleDeckId_suspendedRowDoesNotDropNeverStudiedCards() {
        Deck deck = buildDeck("Deck A");
        deckDao.insertDeck(deck);

        // never studied, no review-state row at all
        Card neverStudied = buildCard(deck.id, 0);
        // suspend before first grade lazily creates a review-state row with
        // suspended = 1 and due_date_time NULL
        Card suspendedNeverStudied = buildCard(deck.id, 1);
        cardDao.insertCard(neverStudied);
        cardDao.insertCard(suspendedNeverStudied);

        insertState(suspendedNeverStudied, null, true);

        long now = System.currentTimeMillis();
        // the suspended NULL-due row must not break the LEFT JOIN handling:
        // the never-studied card without any row stays selectable
        assertEquals(1, studyDao.countDueAndNewCardsBySingleDeckId(deck.id, now));

        List<Card> result = studyDao.findDueAndNewCardsByDeckIds(
                Collections.singletonList(deck.id), now);
        assertEquals(1, result.size());
        assertEquals(neverStudied.id, result.get(0).id);

        // unsuspending the lazily created row (due still NULL) makes the
        // card selectable again
        insertState(suspendedNeverStudied, null, false);
        assertEquals(2, studyDao.countDueAndNewCardsBySingleDeckId(deck.id, now));

        List<Card> resultAfterUnsuspend = studyDao.findDueAndNewCardsByDeckIds(
                Collections.singletonList(deck.id), now);
        assertEquals(2, resultAfterUnsuspend.size());
        Set<Long> resultIds = new HashSet<>();
        for (Card card : resultAfterUnsuspend) {
            resultIds.add(card.id);
        }
        assertTrue(resultIds.contains(suspendedNeverStudied.id));
    }

    private void insertState(Card card, Long dueDateTimeMillis, boolean suspended) {
        CardReviewState cardReviewState = new CardReviewState();
        cardReviewState.cardId = card.id;
        cardReviewState.suspended = suspended;
        if (dueDateTimeMillis != null) {
            cardReviewState.dueDateTime = new Date(dueDateTimeMillis);
        }
        reviewStateDao.insertReviewState(cardReviewState);
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
