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

package m.co.rh.id.a_flash_deck.app.provider.command;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.repository.StudyRepository;
import m.co.rh.id.aprovider.Provider;

public class DeckQueryCmd {
    private ExecutorService mExecutorService;
    private DeckDao mDeckDao;
    private CardDao mCardDao;
    private StudyRepository mStudyRepository;

    public DeckQueryCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mDeckDao = provider.get(DeckDao.class);
        mCardDao = provider.get(CardDao.class);
        mStudyRepository = provider.get(StudyRepository.class);
    }

    public Single<Integer> countCards(Deck deck) {
        return Single.fromCallable(() ->
                mCardDao.countCardByDeckId(deck.id))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<Deck> getDeckById(long deckId) {
        return Single.fromCallable(() ->
                mDeckDao.getDeckById(deckId))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * @return the review state of a card wrapped in Optional, empty when the
     * card is new/never studied (review-state rows are created lazily on
     * first grade or first suspend)
     */
    public Single<Optional<CardReviewState>> getReviewStateByCardId(long cardId) {
        return Single.fromCallable(() ->
                Optional.ofNullable(mStudyRepository.getReviewStateByCardId(cardId)))
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
