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

package m.co.rh.id.a_flash_deck.base.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;

public class NotificationTimerChangeNotifier {
    private PublishSubject<NotificationTimer> mAddedNotificationTimerSubject;
    private PublishSubject<NotificationTimer> mUpdatedNotificationTimerSubject;
    private PublishSubject<NotificationTimer> mDeletedNotificationTimerSubject;

    public NotificationTimerChangeNotifier() {
        mAddedNotificationTimerSubject = PublishSubject.create();
        mUpdatedNotificationTimerSubject = PublishSubject.create();
        mDeletedNotificationTimerSubject = PublishSubject.create();
    }

    public void timerAdded(NotificationTimer notificationTimer) {
        if (notificationTimer != null) {
            mAddedNotificationTimerSubject.onNext(notificationTimer);
        }
    }

    public void timerUpdated(NotificationTimer notificationTimer) {
        if (notificationTimer != null) {
            mUpdatedNotificationTimerSubject.onNext(notificationTimer);
        }
    }

    public void timerDeleted(NotificationTimer notificationTimer) {
        if (notificationTimer != null) {
            mDeletedNotificationTimerSubject.onNext(notificationTimer);
        }
    }

    public Flowable<NotificationTimer> getAddedTimerNotificationFlow() {
        return Flowable.fromObservable(mAddedNotificationTimerSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<NotificationTimer> getUpdatedTimerNotificationFlow() {
        return Flowable.fromObservable(mUpdatedNotificationTimerSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<NotificationTimer> getDeletedTimerNotificationFlow() {
        return Flowable.fromObservable(mDeletedNotificationTimerSubject, BackpressureStrategy.BUFFER);
    }
}
