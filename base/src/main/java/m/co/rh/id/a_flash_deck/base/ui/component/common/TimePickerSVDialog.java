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

package m.co.rh.id.a_flash_deck.base.ui.component.common;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.widget.TimePicker;

import java.io.Serializable;

import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;

/**
 * Common dialog for time picker
 */
public class TimePickerSVDialog extends StatefulViewDialog<Activity> implements TimePickerDialog.OnTimeSetListener {

    @NavInject
    private transient NavRoute mNavRoute;

    private Result mResult;

    @Override
    protected Dialog createDialog(Activity activity) {
        TimePickerDialog timePickerDialog = new TimePickerDialog(activity, this,
                getHourOfDay(), getMinute(), is24HourFormat());
        String title = getTitle();
        if (title != null) {
            timePickerDialog.setTitle(title);
        }
        return timePickerDialog;
    }

    public String getTitle() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mTitle;
        }
        return null;
    }

    public int getHourOfDay() {
        if (mResult != null) {
            return mResult.mHourOfDay;
        }
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mHourOfDay;
        }
        return 6;
    }

    public int getMinute() {
        if (mResult != null) {
            return mResult.mMinute;
        }
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mMinute;
        }
        return 0;
    }

    public boolean is24HourFormat() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mIs24HourFormat;
        }
        return true;
    }

    @Override
    public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
        mResult = Result.newResult(hourOfDay, minute);
    }

    @Override
    protected Serializable getDialogResult() {
        return mResult;
    }

    public static class Result implements Serializable {
        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private static Result newResult(int hourOfDay, int minute) {
            Result result = new Result();
            result.mHourOfDay = hourOfDay;
            result.mMinute = minute;
            return result;
        }

        private int mHourOfDay;
        private int mMinute;

        public int getHourOfDay() {
            return mHourOfDay;
        }

        public int getMinute() {
            return mMinute;
        }
    }

    public static class Args implements Serializable {
        public static Args newArgs(String title, int hourOfDay, int minute, boolean is24HourFormat) {
            Args args = new Args();
            args.mTitle = title;
            args.mHourOfDay = hourOfDay;
            args.mMinute = minute;
            args.mIs24HourFormat = is24HourFormat;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private String mTitle;
        private int mHourOfDay;
        private int mMinute;
        private boolean mIs24HourFormat;
    }
}
