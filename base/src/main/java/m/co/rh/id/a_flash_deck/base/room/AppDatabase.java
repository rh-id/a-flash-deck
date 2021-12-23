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

package m.co.rh.id.a_flash_deck.base.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_flash_deck.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.dao.TestDao;
import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.entity.Test;


@Database(entities = {Deck.class, Card.class, Test.class,
        AndroidNotification.class, NotificationTimer.class},
        version = 5)
public abstract class AppDatabase extends RoomDatabase {
    public abstract DeckDao deckDao();

    public abstract TestDao testDao();

    public abstract AndroidNotificationDao androidNotificationDao();

    public abstract NotificationTimerDao timerNotificationDao();
}
