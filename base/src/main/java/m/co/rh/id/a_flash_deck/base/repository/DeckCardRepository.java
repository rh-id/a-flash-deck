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

package m.co.rh.id.a_flash_deck.base.repository;

import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.CardReviewStateDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;
import m.co.rh.id.a_flash_deck.base.room.AppDatabase;

/**
 * Repository for cross-entity deck and card operations
 */
public class DeckCardRepository {

    private AppDatabase mAppDatabase;
    private DeckDao mDeckDao;
    private CardDao mCardDao;
    private CardReviewStateDao mReviewStateDao;

    public DeckCardRepository(AppDatabase appDatabase, DeckDao deckDao, CardDao cardDao,
                              CardReviewStateDao reviewStateDao) {
        mAppDatabase = appDatabase;
        mDeckDao = deckDao;
        mCardDao = cardDao;
        mReviewStateDao = reviewStateDao;
    }

    /**
     * Delete a deck and all its cards atomically
     */
    public void deleteDeck(Deck deck) {
        if (deck == null) {
            return;
        }
        mAppDatabase.runInTransaction(() -> {
            // must be deleted before the cards, it looks up card ids by deck id
            mReviewStateDao.deleteByDeckIds(Collections.singletonList(deck.id));
            mDeckDao.delete(deck);
            mCardDao.deleteCardsByDeckId(deck.id);
        });
    }

    /**
     * Delete a card and its review state atomically
     */
    public void deleteCard(Card card) {
        if (card == null) {
            return;
        }
        mAppDatabase.runInTransaction(() -> {
            if (card.id != null) {
                mReviewStateDao.deleteByCardId(card.id);
            }
            mCardDao.deleteCard(card);
        });
    }

    /**
     * Import decks and their cards atomically
     */
    public void importDecks(List<DeckModel> deckModels) {
        if (deckModels == null || deckModels.isEmpty()) return;
        mAppDatabase.runInTransaction(() -> {
            for (DeckModel deckModel : deckModels) {
                Deck deck = deckModel.getDeck();
                // imported deck id and our deck id must not same
                deck.id = null;
                mDeckDao.insertDeck(deck);
                List<Card> cardList = deckModel.getCardList();
                if (cardList != null && !cardList.isEmpty()) {
                    for (Card card : cardList) {
                        // replace imported deck id with our deck id
                        card.deckId = deck.id;
                        card.id = null;
                        mCardDao.insertCard(card);
                    }
                }
            }
        });
    }
}
