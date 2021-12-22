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
import m.co.rh.id.aprovider.ProviderValue;

public class PagedNotificationTimerItemsCmd {
    private Context mAppContext;
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<NotificationTimerDao> mTimerNotificationDao;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<NotificationTimer>> mTimerNotificationItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedNotificationTimerItemsCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mTimerNotificationDao = provider.lazyGet(NotificationTimerDao.class);
        mTimerNotificationItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        resetPage();
    }

    public void search(String search) {
        mSearch = search;
        mExecutorService.get().execute(() -> {
            if (!isSearching()) {
                load();
            } else {
                mIsLoadingSubject.onNext(true);
                try {
                    List<NotificationTimer> timerList = mTimerNotificationDao.get().search(search);
                    ArrayList<NotificationTimer> timerArrayList = new ArrayList<>();
                    if (timerList != null && !timerList.isEmpty()) {
                        timerArrayList.addAll(timerList);
                    }
                    mTimerNotificationItemsSubject.onNext(timerArrayList);
                } catch (Throwable throwable) {
                    mTimerNotificationItemsSubject.onError(throwable);
                } finally {
                    mIsLoadingSubject.onNext(false);
                }
            }
        });
    }

    public void loadNextPage() {
        // no pagination for search
        if (isSearching()) return;
        if (getAllTimerNotificationItems().size() < mLimit) {
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
        mExecutorService.get().execute(() -> {
            mIsLoadingSubject.onNext(true);
            try {
                mTimerNotificationItemsSubject.onNext(
                        loadTimerNotificationItems());
            } catch (Throwable throwable) {
                mTimerNotificationItemsSubject.onError(throwable);
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

    private ArrayList<NotificationTimer> loadTimerNotificationItems() {
        List<NotificationTimer> timerList = mTimerNotificationDao.get().getAllWithLimit(mLimit);
        ArrayList<NotificationTimer> timerArrayList = new ArrayList<>();
        if (timerList != null && !timerList.isEmpty()) {
            timerArrayList.addAll(timerList);
        }
        return timerArrayList;
    }

    public ArrayList<NotificationTimer> getAllTimerNotificationItems() {
        return mTimerNotificationItemsSubject.getValue();
    }

    public Flowable<ArrayList<NotificationTimer>> getTimerNotificationsFlow() {
        return Flowable.fromObservable(mTimerNotificationItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 10;
    }
}
