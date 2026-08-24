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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class PagedDeckItemsCmd {
    private static final String TAG = PagedDeckItemsCmd.class.getName();

    private ExecutorService mExecutorService;
    private DeckDao mDeckDao;
    private ILogger mLogger;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<Deck>> mDeckItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final BehaviorSubject<Set<Long>> mSelectedDeckIdsSubject;
    private final BehaviorSubject<Integer> mTotalCountSubject;

    public PagedDeckItemsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mDeckDao = provider.get(DeckDao.class);
        mLogger = provider.get(ILogger.class);
        mDeckItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        mSelectedDeckIdsSubject = BehaviorSubject.createDefault(new LinkedHashSet<>());
        mTotalCountSubject = BehaviorSubject.createDefault(0);
        resetPage();
    }

    private boolean isSearching() {
        return mSearch != null && !mSearch.isEmpty();
    }

    public synchronized boolean isSelected(Deck deck) {
        if (deck != null) {
            Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
            return selectedDeckIds.contains(deck.id);
        }
        return false;
    }

    public synchronized void selectDeck(Deck deck, boolean clearOtherSelection) {
        if (deck == null) return;
        Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
        if (clearOtherSelection) {
            selectedDeckIds.clear();
        }
        selectedDeckIds.add(deck.id);
        mSelectedDeckIdsSubject.onNext(selectedDeckIds);
    }

    public synchronized void unSelectDeck(Deck deck) {
        if (deck == null) return;
        Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
        selectedDeckIds.remove(deck.id);
        mSelectedDeckIdsSubject.onNext(selectedDeckIds);
    }

    public void selectAllDecks() {
        mExecutorService.execute(() -> {
            List<Deck> deckList;
            if (isSearching()) {
                deckList = mDeckDao.searchDeck(mSearch);
            } else {
                deckList = mDeckDao.getAllDecks();
            }
            synchronized (PagedDeckItemsCmd.this) {
                Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
                if (deckList != null) {
                    for (Deck deck : deckList) {
                        selectedDeckIds.add(deck.id);
                    }
                    mTotalCountSubject.onNext(deckList.size());
                }
                mSelectedDeckIdsSubject.onNext(selectedDeckIds);
            }
        });
    }

    public synchronized void unselectAllDecks() {
        Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
        selectedDeckIds.clear();
        mSelectedDeckIdsSubject.onNext(selectedDeckIds);
    }

    public Flowable<Set<Long>> getSelectedDeckIdsFlow() {
        return Flowable.fromObservable(mSelectedDeckIdsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Integer> getTotalCountFlow() {
        return Flowable.fromObservable(mTotalCountSubject, BackpressureStrategy.BUFFER);
    }

    public synchronized int getSelectedCount() {
        Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
        return selectedDeckIds != null ? selectedDeckIds.size() : 0;
    }

    public int getTotalCount() {
        Integer total = mTotalCountSubject.getValue();
        return total != null ? total : 0;
    }

    public void search(String search) {
        mSearch = search;
        mExecutorService.execute(() -> {
            if (!isSearching()) {
                load();
            } else {
                mIsLoadingSubject.onNext(true);
                try {
                    List<Deck> deckList = mDeckDao.searchDeck(search);
                    ArrayList<Deck> deckArrayList = new ArrayList<>();
                    if (deckList != null && !deckList.isEmpty()) {
                        deckArrayList.addAll(deckList);
                    }
                    mTotalCountSubject.onNext(deckArrayList.size());
                    mDeckItemsSubject.onNext(deckArrayList);
                } catch (Throwable throwable) {
                    mLogger.e(TAG, throwable.getMessage(), throwable);
                } finally {
                    mIsLoadingSubject.onNext(false);
                }
            }
        });
    }

    public void loadNextPage() {
        // no pagination for search
        if (isSearching()) return;
        if (getAllDeckItems().size() < mLimit) {
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
                int total = mDeckDao.countDeck();
                mTotalCountSubject.onNext(total);
                mDeckItemsSubject.onNext(
                        loadDeckItems());
            } catch (Throwable throwable) {
                mLogger.e(TAG, throwable.getMessage(), throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private ArrayList<Deck> loadDeckItems() {
        List<Deck> deckList = mDeckDao.getDeckWithLimit(mLimit);
        ArrayList<Deck> deckArrayList = new ArrayList<>();
        if (deckList != null && !deckList.isEmpty()) {
            deckArrayList.addAll(deckList);
        }
        return deckArrayList;
    }

    public ArrayList<Deck> getAllDeckItems() {
        return mDeckItemsSubject.getValue();
    }

    public Flowable<ArrayList<Deck>> getDecksFlow() {
        return Flowable.fromObservable(mDeckItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 10;
    }

    public Single<ArrayList<Deck>> getSelectedDecks() {
        return Single.fromCallable(() -> {
            Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
            ArrayList<Deck> returnedDeck = new ArrayList<>();
            if (selectedDeckIds != null && !selectedDeckIds.isEmpty()) {
                List<Long> idList = new ArrayList<>(selectedDeckIds);
                List<Deck> decksFromDb = mDeckDao.findDeckByIds(idList);
                if (decksFromDb != null) {
                    returnedDeck.addAll(decksFromDb);
                }
            }
            return returnedDeck;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
