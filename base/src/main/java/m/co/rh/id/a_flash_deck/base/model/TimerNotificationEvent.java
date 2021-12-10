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

package m.co.rh.id.a_flash_deck.base.model;

import java.io.Serializable;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;

public class TimerNotificationEvent implements Serializable {
    private NotificationTimer mNotificationTimer;
    private Card mSelectedCard;

    public TimerNotificationEvent(NotificationTimer notificationTimer, Card selectedCard) {
        mNotificationTimer = notificationTimer;
        mSelectedCard = selectedCard;
    }

    public NotificationTimer getTimerNotification() {
        return mNotificationTimer;
    }

    public Card getSelectedCard() {
        return mSelectedCard;
    }
}
