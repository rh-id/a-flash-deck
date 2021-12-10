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

import androidx.work.WorkManager;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerTags;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimerChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteNotificationTimerCmd {
    private ProviderValue<WorkManager> mWorkManager;
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<NotificationTimerDao> mTimerNotificationDao;
    private ProviderValue<NotificationTimerChangeNotifier> mNotificationTimerChangeNotifier;

    public DeleteNotificationTimerCmd(Provider provider) {
        mWorkManager = provider.lazyGet(WorkManager.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mTimerNotificationDao = provider.lazyGet(NotificationTimerDao.class);
        mNotificationTimerChangeNotifier = provider.lazyGet(NotificationTimerChangeNotifier.class);
    }

    public Single<NotificationTimer> execute(NotificationTimer notificationTimer) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mTimerNotificationDao.get().delete(notificationTimer);
            mNotificationTimerChangeNotifier.get().timerDeleted(notificationTimer);
            mWorkManager.get().cancelAllWorkByTag(WorkManagerTags.NOTIFICATION_TIMER +
                    notificationTimer.id);
            return notificationTimer;
        }));
    }
}
