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
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.aprovider.Provider;

public class PagedDeckItemsCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private DeckDao mDeckDao;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<Deck>> mDeckItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final BehaviorSubject<Set<Long>> mSelectedDeckIdsSubject;

    public PagedDeckItemsCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mDeckDao = provider.get(DeckDao.class);
        mDeckItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        mSelectedDeckIdsSubject = BehaviorSubject.createDefault(new LinkedHashSet<>());
        resetPage();
    }

    private boolean isSearching() {
        return mSearch != null && !mSearch.isEmpty();
    }

    public boolean isSelected(Deck deck) {
        if (deck != null) {
            Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
            return selectedDeckIds.contains(deck.id);
        }
        return false;
    }

    public void selectDeck(Deck deck, boolean clearOtherSelection) {
        Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
        if (clearOtherSelection) {
            selectedDeckIds.clear();
        }
        selectedDeckIds.add(deck.id);
        mSelectedDeckIdsSubject.onNext(selectedDeckIds);
    }

    public void unSelectDeck(Deck deck) {
        Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
        selectedDeckIds.remove(deck.id);
        mSelectedDeckIdsSubject.onNext(selectedDeckIds);
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
                    mDeckItemsSubject.onNext(deckArrayList);
                } catch (Throwable throwable) {
                    mDeckItemsSubject.onError(throwable);
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
                mDeckItemsSubject.onNext(
                        loadDeckItems());
            } catch (Throwable throwable) {
                mDeckItemsSubject.onError(throwable);
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

    public Set<Long> getSelectedIds() {
        return mSelectedDeckIdsSubject.getValue();
    }

    public Flowable<ArrayList<Deck>> getDecksFlow() {
        return Flowable.fromObservable(mDeckItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Set<Long>> getSelectedIdsFlow() {
        return Flowable.fromObservable(mSelectedDeckIdsSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 10;
    }

    public ArrayList<Deck> getSelectedDecks() {
        Set<Long> selectedDeckIds = mSelectedDeckIdsSubject.getValue();
        ArrayList<Deck> returnedDeck = new ArrayList<>();
        if (!selectedDeckIds.isEmpty()) {
            ArrayList<Deck> deckItems = getAllDeckItems();
            if (!deckItems.isEmpty()) {
                for (Deck deck : deckItems) {
                    if (selectedDeckIds.contains(deck.id)) {
                        returnedDeck.add(deck);
                    }
                }
            }
        }
        return returnedDeck;
    }
}
