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

package m.co.rh.id.a_flash_deck.base.component;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.Optional;

import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.model.NotificationTimerEvent;

public interface IAppNotificationHandler {
    String KEY_INT_REQUEST_ID = "KEY_INT_REQUEST_ID";
    String CHANNEL_ID_NOTIFICATION_TIMER = "CHANNEL_ID_NOTIFICATION_TIMER";
    String GROUP_KEY_NOTIFICATION_TIMER = "GROUP_KEY_NOTIFICATION_TIMER";
    int GROUP_SUMMARY_ID_NOTIFICATION_TIMER = 0;


    void postNotificationTimer(NotificationTimer notificationTimer, Card selectedCard);

    void removeNotification(Intent intent);

    void processNotification(@NonNull Intent intent);

    Flowable<Optional<NotificationTimerEvent>> getTimerNotificationEventFlow();

    void clearEvent();

    @WorkerThread
    void cancelNotificationSync(NotificationTimer notificationTimer);

    void playVoice(Intent intent);
}
