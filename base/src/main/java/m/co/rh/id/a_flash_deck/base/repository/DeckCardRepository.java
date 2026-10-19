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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import m.co.rh.id.a_flash_deck.base.dao.AnkiNoteGuidDao;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.CardReviewStateDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.ReviewLogDao;
import m.co.rh.id.a_flash_deck.base.entity.AnkiNoteGuid;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.model.DeckImportPayload;
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
    private ReviewLogDao mReviewLogDao;
    private AnkiNoteGuidDao mAnkiNoteGuidDao;

    public DeckCardRepository(AppDatabase appDatabase, DeckDao deckDao, CardDao cardDao,
                              CardReviewStateDao reviewStateDao, ReviewLogDao reviewLogDao,
                              AnkiNoteGuidDao ankiNoteGuidDao) {
        mAppDatabase = appDatabase;
        mDeckDao = deckDao;
        mCardDao = cardDao;
        mReviewStateDao = reviewStateDao;
        mReviewLogDao = reviewLogDao;
        mAnkiNoteGuidDao = ankiNoteGuidDao;
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
            mReviewLogDao.deleteByDeckIds(Collections.singletonList(deck.id));
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
                mReviewLogDao.deleteByCardId(card.id);
            }
            mCardDao.deleteCard(card);
        });
    }

    /**
     * Import decks and their cards atomically (no Anki guid duplicate
     * detection).
     */
    public void importDecks(List<DeckModel> deckModels) {
        if (deckModels == null || deckModels.isEmpty()) return;
        List<DeckImportPayload> payloads = new ArrayList<>(deckModels.size());
        for (DeckModel deckModel : deckModels) {
            payloads.add(new DeckImportPayload(deckModel, Collections.emptyList()));
        }
        importDeckPayloads(payloads);
    }

    /**
     * Import decks and their cards atomically, skipping cards whose Anki note
     * guid was already imported (or repeats within the same import). A deck
     * whose cards are all skipped is not created at all. Deck and card models
     * are updated in place with the generated ids; check
     * {@link DeckImportResult#createdDeckModels} to know which decks were
     * created.
     */
    public DeckImportResult importDeckPayloads(List<DeckImportPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return DeckImportResult.empty();
        }
        int[] cardsImported = new int[1];
        int[] duplicateCardsSkipped = new int[1];
        List<DeckModel> createdDeckModels = new ArrayList<>();
        mAppDatabase.runInTransaction(() -> {
            // gather all guids first for a single batched existence check
            List<String> allGuids = new ArrayList<>();
            for (DeckImportPayload payload : payloads) {
                for (String guid : payload.getCardGuids()) {
                    if (DeckImportPayload.isValidGuid(guid)) {
                        allGuids.add(guid);
                    }
                }
            }
            Set<String> existingGuids = new HashSet<>(mAnkiNoteGuidDao.findExistingGuids(allGuids));
            for (DeckImportPayload payload : payloads) {
                importDeckPayload(payload, existingGuids,
                        createdDeckModels, cardsImported, duplicateCardsSkipped);
            }
        });
        return new DeckImportResult(createdDeckModels.size(), cardsImported[0],
                duplicateCardsSkipped[0], createdDeckModels);
    }

    private void importDeckPayload(DeckImportPayload payload, Set<String> existingGuids,
                                   List<DeckModel> createdDeckModels,
                                   int[] cardsImported, int[] duplicateCardsSkipped) {
        if (payload == null || payload.getDeckModel() == null) {
            return;
        }
        Deck deck = payload.getDeckModel().getDeck();
        List<Card> cardList = payload.getDeckModel().getCardList();
        if (deck == null || cardList == null) {
            return;
        }
        // imported deck id and our deck id must not be the same
        deck.id = null;
        if (cardList.isEmpty()) {
            // keep legacy behavior: empty decks are imported as-is
            mDeckDao.insertDeck(deck);
            createdDeckModels.add(payload.getDeckModel());
            return;
        }
        boolean deckCreated = false;
        for (int i = 0; i < cardList.size(); i++) {
            Card card = cardList.get(i);
            if (card == null) {
                continue;
            }
            String guid = payload.getCardGuid(i);
            if (DeckImportPayload.isValidGuid(guid)) {
                if (existingGuids.contains(guid)) {
                    // the note was already imported before (or repeats
                    // within this very import), skip the card entirely
                    duplicateCardsSkipped[0]++;
                    continue;
                }
                // mark as seen so later occurrences in this import skip too
                existingGuids.add(guid);
            }
            if (!deckCreated) {
                // create the deck only when it keeps at least one card
                mDeckDao.insertDeck(deck);
                createdDeckModels.add(payload.getDeckModel());
                deckCreated = true;
            }
            // replace imported deck id with our deck id
            card.deckId = deck.id;
            card.id = null;
            mCardDao.insertCard(card);
            if (DeckImportPayload.isValidGuid(guid)) {
                AnkiNoteGuid ankiNoteGuid = new AnkiNoteGuid();
                ankiNoteGuid.cardId = card.id;
                ankiNoteGuid.guid = guid;
                mAnkiNoteGuidDao.insert(ankiNoteGuid);
            }
            cardsImported[0]++;
        }
    }
}
