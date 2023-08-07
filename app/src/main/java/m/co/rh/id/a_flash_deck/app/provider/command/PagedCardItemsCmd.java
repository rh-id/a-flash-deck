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

package m.co.rh.id.a_flash_deck.app.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class PagedCardItemsCmd implements ProviderDisposable {
    private final ExecutorService mExecutorService;
    private final DeckDao mDeckDao;
    private int mLimit;
    private Long mDeckId;
    private String mSearch;
    private final BehaviorSubject<ArrayList<Card>> mCardItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final CompositeDisposable mCompositeDisposable;

    public PagedCardItemsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mDeckDao = provider.get(DeckDao.class);
        DeckChangeNotifier deckChangeNotifier = provider.get(DeckChangeNotifier.class);
        mCardItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        resetPage();
        mCompositeDisposable = new CompositeDisposable();
        mCompositeDisposable.add(deckChangeNotifier.getMovedCardFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(moveCardEvent -> refresh()));
        mCompositeDisposable.add(deckChangeNotifier.getAddedCardFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(card -> refresh()));
        mCompositeDisposable.add(deckChangeNotifier.getUpdatedCardFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(card -> refresh()));
        mCompositeDisposable.add(deckChangeNotifier.getDeletedCardFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(card -> refresh()));
    }

    private boolean isSearching() {
        return mSearch != null && !mSearch.isEmpty();
    }

    public void search(String search) {
        mSearch = search;
        mExecutorService.execute(() -> {
            if (!isSearching()) {
                load();
            } else {
                mIsLoadingSubject.onNext(true);
                try {
                    Set<Card> cardSet = new LinkedHashSet<>();
                    if (mDeckId == null) {
                        cardSet.addAll(mDeckDao.searchCard(search));
                    } else {
                        cardSet.addAll(mDeckDao.searchCardByDeckId(mDeckId, search));
                    }
                    // also search the deck name and append it altogether
                    List<Deck> deckList = mDeckDao.searchDeck(search);
                    List<Card> cardFromDeck = mDeckDao.getCardsByDecks(deckList);
                    cardSet.addAll(cardFromDeck);

                    ArrayList<Card> cardArrayList = new ArrayList<>();
                    if (!cardSet.isEmpty()) {
                        cardArrayList.addAll(cardSet);
                    }
                    mCardItemsSubject.onNext(cardArrayList);
                } catch (Throwable throwable) {
                    mCardItemsSubject.onError(throwable);
                } finally {
                    mIsLoadingSubject.onNext(false);
                }
            }
        });
    }

    public void loadNextPage() {
        // no pagination for search
        if (isSearching()) return;
        if (getAllCardItems().size() < mLimit) {
            return;
        }
        mLimit += mLimit;
        load();
    }

    public void refresh() {
        if (isSearching()) {
            doSearch();
        } else {
            load();
        }
    }

    private void doSearch() {
        search(mSearch);
    }

    private void load() {
        mExecutorService.execute(() -> {
            mIsLoadingSubject.onNext(true);
            try {
                mCardItemsSubject.onNext(
                        loadCardItems());
            } catch (Throwable throwable) {
                mCardItemsSubject.onError(throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private ArrayList<Card> loadCardItems() {
        List<Card> cardList;
        if (mDeckId == null) {
            cardList = mDeckDao.getCardWithLimit(mLimit);
        } else {
            cardList = mDeckDao.getCardByDeckIdWithLimit(mDeckId, mLimit);
        }
        ArrayList<Card> cardArrayList = new ArrayList<>();
        if (cardList != null && !cardList.isEmpty()) {
            cardArrayList.addAll(cardList);
        }
        return cardArrayList;
    }

    public ArrayList<Card> getAllCardItems() {
        return mCardItemsSubject.getValue();
    }

    public Flowable<ArrayList<Card>> getCardsFlow() {
        return Flowable.fromObservable(mCardItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 100;
    }

    public void setDeckId(Long deckId) {
        mDeckId = deckId;
    }

    @Override
    public void dispose(Context context) {
        if (!mCompositeDisposable.isDisposed()) {
            mCompositeDisposable.dispose();
        }
    }
}
