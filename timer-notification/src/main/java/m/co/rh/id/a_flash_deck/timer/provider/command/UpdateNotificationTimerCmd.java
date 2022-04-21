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

import androidx.work.Data;
import androidx.work.PeriodicWorkRequest;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerTags;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.timer.workmanager.NotificationTimerWorker;
import m.co.rh.id.aprovider.Provider;

public class UpdateNotificationTimerCmd extends NewNotificationTimerCmd {

    public UpdateNotificationTimerCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<NotificationTimer> execute(NotificationTimer notificationTimer) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
                    if (!valid(notificationTimer)) {
                        throw new ValidationException(getValidationError());
                    }
                    NotificationTimer timerBeforeUpdate = mNotificationTimerDao.get().findById(notificationTimer.id);
                    mNotificationTimerDao.get().update(notificationTimer);
                    mNotificationTimerChangeNotifier.get().timerUpdated(notificationTimer);

                    if (timerBeforeUpdate.periodInMinutes != notificationTimer.periodInMinutes) {
                        mWorkManager.get().cancelAllWorkByTag(WorkManagerTags.NOTIFICATION_TIMER +
                                notificationTimer.id);

                        PeriodicWorkRequest.Builder workBuilder = new PeriodicWorkRequest.Builder(NotificationTimerWorker.class,
                                notificationTimer.periodInMinutes, TimeUnit.MINUTES);
                        workBuilder.setInputData(new Data.Builder()
                                .putLong(WorkManagerKeys.LONG_NOTIFICATION_TIMER_ID, notificationTimer.id)
                                .build());
                        workBuilder.setInitialDelay(notificationTimer.periodInMinutes, TimeUnit.MINUTES);
                        workBuilder.addTag(WorkManagerTags.NOTIFICATION_TIMER + notificationTimer.id);
                        mWorkManager.get().enqueue(workBuilder.build());
                    }
                    return notificationTimer;
                })
        );
    }
}
