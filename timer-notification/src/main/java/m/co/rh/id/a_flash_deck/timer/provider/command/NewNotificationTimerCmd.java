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

import androidx.work.Data;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerTags;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimerChangeNotifier;
import m.co.rh.id.a_flash_deck.timer.R;
import m.co.rh.id.a_flash_deck.timer.workmanager.NotificationTimerWorker;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NewNotificationTimerCmd {
    protected Context mAppContext;
    protected ProviderValue<WorkManager> mWorkManager;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<NotificationTimerDao> mNotificationTimerDao;
    protected ProviderValue<NotificationTimerChangeNotifier> mNotificationTimerChangeNotifier;
    protected ProviderValue<DeckDao> mDeckDao;
    protected ProviderValue<AppSharedPreferences> mAppSharedPreferences;
    protected BehaviorSubject<String> mNameValidSubject;
    protected BehaviorSubject<String> mSelectedDeckIdsValidSubject;

    public NewNotificationTimerCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mWorkManager = provider.lazyGet(WorkManager.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mNotificationTimerDao = provider.lazyGet(NotificationTimerDao.class);
        mNotificationTimerChangeNotifier = provider.lazyGet(NotificationTimerChangeNotifier.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mAppSharedPreferences = provider.lazyGet(AppSharedPreferences.class);
        mNameValidSubject = BehaviorSubject.create();
        mSelectedDeckIdsValidSubject = BehaviorSubject.create();
    }

    public boolean valid(NotificationTimer notificationTimer) {
        boolean isValid = false;
        if (notificationTimer != null) {
            boolean nameValid = false;
            boolean selectedDeckIdsValid = false;
            if (notificationTimer.name != null && !notificationTimer.name.isEmpty()) {
                nameValid = true;
                mNameValidSubject.onNext("");
            } else {
                mNameValidSubject.onNext(mAppContext.getString(R.string.name_is_required));
            }
            if (notificationTimer.selectedDeckIds != null && !notificationTimer.selectedDeckIds.isEmpty()) {
                selectedDeckIdsValid = true;
                mSelectedDeckIdsValidSubject.onNext("");
            } else {
                mSelectedDeckIdsValidSubject.onNext(mAppContext.getString(R.string.error_no_deck_selected));
            }
            isValid = nameValid && selectedDeckIdsValid;
        }
        return isValid;
    }

    public Single<NotificationTimer> execute(NotificationTimer notificationTimer) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
                    if (!valid(notificationTimer)) {
                        throw new ValidationException(getValidationError());
                    } else {
                        String selectedDeckIds = notificationTimer.selectedDeckIds;
                        JSONArray jsonArray = new JSONArray(selectedDeckIds);
                        int size = jsonArray.length();
                        List<Long> deckIds = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            deckIds.add(jsonArray.getLong(i));
                        }
                        List<Card> cardList = mDeckDao.get().findCardByDeckIds(deckIds);
                        if (cardList.isEmpty()) {
                            throw new ValidationException(mAppContext.getString(R.string.error_no_card_from_deck));
                        }
                    }
                    mNotificationTimerDao.get().insertNotificationTimer(notificationTimer);
                    mNotificationTimerChangeNotifier.get().timerAdded(notificationTimer);

                    PeriodicWorkRequest.Builder workBuilder = new PeriodicWorkRequest.Builder(NotificationTimerWorker.class,
                            notificationTimer.periodInMinutes, TimeUnit.MINUTES);
                    workBuilder.setInputData(new Data.Builder()
                            .putLong(WorkManagerKeys.LONG_NOTIFICATION_TIMER_ID, notificationTimer.id)
                            .build());
                    workBuilder.setInitialDelay(notificationTimer.periodInMinutes, TimeUnit.MINUTES);
                    workBuilder.addTag(WorkManagerTags.NOTIFICATION_TIMER + notificationTimer.id);
                    mWorkManager.get().enqueue(workBuilder.build());
                    return notificationTimer;
                })
        );
    }

    public String getValidationError() {
        String nameValid = mNameValidSubject.getValue();
        if (nameValid != null && !nameValid.isEmpty()) {
            return nameValid;
        }
        String selectedDeckIdsValid = mSelectedDeckIdsValidSubject.getValue();
        if (selectedDeckIdsValid != null && !selectedDeckIdsValid.isEmpty()) {
            return selectedDeckIdsValid;
        }
        return "";
    }

    public Flowable<String> getNameValid() {
        return Flowable.fromObservable(mNameValidSubject, BackpressureStrategy.BUFFER);
    }
}
