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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppSharedPreferences {
    private static final String SHARED_PREFERENCES_NAME = "AppSharedPreferences";
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<Handler> mHandler;
    private SharedPreferences mSharedPreferences;

    private int mSelectedTheme;
    private String mSelectedThemeKey;

    private Date mNotificationStartTime;
    private String mNotificationStartTimeKey;

    private Date mNotificationEndTime;
    private String mNotificationEndTimeKey;

    public AppSharedPreferences(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mHandler = provider.lazyGet(Handler.class);
        mSharedPreferences = provider.getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        initValue();
    }

    private void initValue() {
        mSelectedThemeKey = SHARED_PREFERENCES_NAME
                + ".selectedTheme";
        mNotificationStartTimeKey = SHARED_PREFERENCES_NAME
                + ".notificationStartTime";
        mNotificationEndTimeKey = SHARED_PREFERENCES_NAME
                + ".notificationEndTime";

        int selectedTheme = mSharedPreferences.getInt(
                mSelectedThemeKey,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setSelectedTheme(selectedTheme);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 6);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long notificationStartTime = mSharedPreferences.getLong(mNotificationStartTimeKey,
                calendar.getTime().getTime());
        notificationStartTime(notificationStartTime);

        calendar.set(Calendar.HOUR_OF_DAY, 21);
        long notificationEndTime = mSharedPreferences.getLong(mNotificationEndTimeKey,
                calendar.getTime().getTime());
        notificationEndTime(notificationEndTime);
    }

    private void selectedTheme(int setting) {
        mSelectedTheme = setting;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putInt(mSelectedThemeKey, setting)
                        .commit());
    }

    public void setSelectedTheme(int setting) {
        selectedTheme(setting);
        mHandler.get().post(() ->
                AppCompatDelegate.setDefaultNightMode(setting));
    }

    public int getSelectedTheme() {
        return mSelectedTheme;
    }

    private void notificationStartTime(long timeUtc) {
        mNotificationStartTime = new Date(timeUtc);
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putLong(mNotificationStartTimeKey,
                        mNotificationStartTime.getTime())
                        .commit());
    }

    public void setNotificationStartTime(long timeUtc) {
        notificationStartTime(timeUtc);
    }

    public void setNotificationStartTime(int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        setNotificationStartTime(calendar.getTimeInMillis());
    }

    public Date getNotificationStartTime() {
        return mNotificationStartTime;
    }

    public int getNotificationStartTimeHourOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(mNotificationStartTime);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    public int getNotificationStartTimeMinute() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(mNotificationStartTime);
        return calendar.get(Calendar.MINUTE);
    }

    public int getNotificationEndTimeHourOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(mNotificationEndTime);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    public int getNotificationEndTimeMinute() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(mNotificationEndTime);
        return calendar.get(Calendar.MINUTE);
    }

    private void notificationEndTime(long timeUtc) {
        mNotificationEndTime = new Date(timeUtc);
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putLong(mNotificationEndTimeKey,
                        mNotificationEndTime.getTime())
                        .commit());
    }

    public void setNotificationEndTime(long timeUtc) {
        notificationEndTime(timeUtc);
    }

    public void setNotificationEndTime(int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        setNotificationEndTime(calendar.getTimeInMillis());
    }

    public Date getNotificationEndTime() {
        return mNotificationEndTime;
    }
}
