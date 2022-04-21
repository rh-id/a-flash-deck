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

package m.co.rh.id.a_flash_deck.timer.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.aprovider.Provider;

public class PagedNotificationTimerItemsCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private NotificationTimerDao mNotificationTimerDao;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<NotificationTimer>> mNotificationTimerItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedNotificationTimerItemsCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mNotificationTimerDao = provider.get(NotificationTimerDao.class);
        mNotificationTimerItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        resetPage();
    }

    public void search(String search) {
        mSearch = search;
        mExecutorService.execute(() -> {
            if (!isSearching()) {
                load();
            } else {
                mIsLoadingSubject.onNext(true);
                try {
                    List<NotificationTimer> timerList = mNotificationTimerDao.search(search);
                    ArrayList<NotificationTimer> timerArrayList = new ArrayList<>();
                    if (timerList != null && !timerList.isEmpty()) {
                        timerArrayList.addAll(timerList);
                    }
                    mNotificationTimerItemsSubject.onNext(timerArrayList);
                } catch (Throwable throwable) {
                    mNotificationTimerItemsSubject.onError(throwable);
                } finally {
                    mIsLoadingSubject.onNext(false);
                }
            }
        });
    }

    public void loadNextPage() {
        // no pagination for search
        if (isSearching()) return;
        if (getAllNotificationTimerItems().size() < mLimit) {
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

    private void load() {
        mExecutorService.execute(() -> {
            mIsLoadingSubject.onNext(true);
            try {
                mNotificationTimerItemsSubject.onNext(
                        loadNotificationTimerItems());
            } catch (Throwable throwable) {
                mNotificationTimerItemsSubject.onError(throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private boolean isSearching() {
        return mSearch != null && !mSearch.isEmpty();
    }

    private void doSearch() {
        search(mSearch);
    }

    private ArrayList<NotificationTimer> loadNotificationTimerItems() {
        List<NotificationTimer> timerList = mNotificationTimerDao.getAllWithLimit(mLimit);
        ArrayList<NotificationTimer> timerArrayList = new ArrayList<>();
        if (timerList != null && !timerList.isEmpty()) {
            timerArrayList.addAll(timerList);
        }
        return timerArrayList;
    }

    public ArrayList<NotificationTimer> getAllNotificationTimerItems() {
        return mNotificationTimerItemsSubject.getValue();
    }

    public Flowable<ArrayList<NotificationTimer>> getNotificationTimersFlow() {
        return Flowable.fromObservable(mNotificationTimerItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 10;
    }
}
