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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.WorkerThread;

import java.util.concurrent.atomic.AtomicInteger;

import m.co.rh.id.a_flash_deck.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;

public class AndroidNotificationRepo {
    private static final String SHARED_PREFERENCES_NAME = "AndroidNotificationRepo";

    private SharedPreferences mSharedPreferences;
    private AndroidNotificationDao mAndroidNotificationDao;

    private AtomicInteger mRequestId;
    private String mRequestIdKey;

    @WorkerThread
    public AndroidNotificationRepo(Context context,
                                   AndroidNotificationDao androidNotificationDao) {
        mSharedPreferences = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        mAndroidNotificationDao = androidNotificationDao;

        mRequestIdKey = SHARED_PREFERENCES_NAME
                + ".requestId";
        mRequestId = new AtomicInteger(mSharedPreferences.getInt(mRequestIdKey, 0));
    }

    public synchronized AndroidNotification findByRequestId(int requestId) {
        return mAndroidNotificationDao.findByRequestId(requestId);
    }

    public synchronized AndroidNotification findByGroupTagAndRefId(String groupKey, Long refId) {
        return mAndroidNotificationDao.findByGroupTagAndRefId(groupKey, refId);
    }

    public synchronized void insertNotification(AndroidNotification androidNotification) {
        androidNotification.requestId = mRequestId.getAndIncrement();
        androidNotification.id = mAndroidNotificationDao.insert(androidNotification);
        mSharedPreferences.edit().putInt(mRequestIdKey, mRequestId.get())
                .commit();
    }

    public synchronized void deleteNotificationByRequestId(int requestId) {
        mAndroidNotificationDao.deleteByRequestId(requestId);
    }

    public synchronized void deleteNotification(AndroidNotification androidNotification) {
        mAndroidNotificationDao.delete(androidNotification);
    }
}
