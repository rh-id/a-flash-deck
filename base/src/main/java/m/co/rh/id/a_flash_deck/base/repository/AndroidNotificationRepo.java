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

package m.co.rh.id.a_flash_deck.base.repository;

import m.co.rh.id.a_flash_deck.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;

public class AndroidNotificationRepo {
    private AndroidNotificationDao mAndroidNotificationDao;

    public AndroidNotificationRepo(AndroidNotificationDao androidNotificationDao) {
        mAndroidNotificationDao = androidNotificationDao;
    }

    public synchronized AndroidNotification findByRequestId(int requestId) {
        return mAndroidNotificationDao.findByRequestId(requestId);
    }

    public synchronized AndroidNotification findByGroupTagAndRefId(String groupKey, Long refId) {
        return mAndroidNotificationDao.findByGroupTagAndRefId(groupKey, refId);
    }

    public synchronized void insertNotification(AndroidNotification androidNotification) {
        mAndroidNotificationDao.insertNotification(androidNotification);
    }

    public synchronized void deleteNotificationByRequestId(int requestId) {
        mAndroidNotificationDao.deleteByRequestId(requestId);
    }

    public synchronized void deleteNotification(AndroidNotification androidNotification) {
        mAndroidNotificationDao.deleteNotification(androidNotification);
    }
}
