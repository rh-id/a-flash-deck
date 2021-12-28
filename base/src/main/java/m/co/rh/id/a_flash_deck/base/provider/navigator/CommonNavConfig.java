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

package m.co.rh.id.a_flash_deck.base.provider.navigator;

import android.app.Activity;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.ui.component.common.BooleanSVDialog;
import m.co.rh.id.a_flash_deck.base.ui.component.common.CommonImageViewPage;
import m.co.rh.id.a_flash_deck.base.ui.component.common.MessageSVDialog;
import m.co.rh.id.a_flash_deck.base.ui.component.common.TimePickerSVDialog;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.StatefulViewFactory;

@SuppressWarnings("rawtypes")
public class CommonNavConfig {
    private Map<String, StatefulViewFactory<Activity, StatefulView>> mNavMap;

    public CommonNavConfig() {
        mNavMap = new LinkedHashMap<>();
        mNavMap.put(Routes.COMMON_BOOLEAN_DIALOG, (args, activity) -> new BooleanSVDialog());
        mNavMap.put(Routes.COMMON_MESSAGE_DIALOG, (args, activity) -> new MessageSVDialog());
        mNavMap.put(Routes.COMMON_TIMEPICKER_DIALOG, (args, activity) -> new TimePickerSVDialog());
        mNavMap.put(Routes.COMMON_IMAGEVIEW, (args, activity) -> new CommonImageViewPage());
    }

    public Map<String, StatefulViewFactory<Activity, StatefulView>> getNavMap() {
        return mNavMap;
    }

    public Serializable args_commonBooleanDialog(String title, String content) {
        return BooleanSVDialog.Args.newArgs(title, content);
    }

    public boolean result_commonBooleanDialog(NavRoute navRoute) {
        if (navRoute == null) return false;
        Serializable serializable = navRoute.getRouteResult();
        if (serializable instanceof Boolean) {
            return (boolean) serializable;
        }
        return false;
    }

    public Serializable args_commonMessageDialog(String title, String message) {
        return MessageSVDialog.Args.newArgs(title, message);
    }

    public Serializable args_commonTimePickerDialog(String title, int hourOfDay, int minute, boolean is24HourFormat) {
        return TimePickerSVDialog.Args.newArgs(title, hourOfDay, minute, is24HourFormat);
    }

    public Serializable result_commonTimePickerDialog(NavRoute navRoute) {
        if (navRoute == null) return null;
        return TimePickerSVDialog.Result.of(navRoute.getRouteResult());
    }

    public Integer result_commonTimePickerDialog_hourOfDay(Serializable serializable) {
        if (!(serializable instanceof TimePickerSVDialog.Result)) return null;
        return ((TimePickerSVDialog.Result) serializable).getHourOfDay();
    }

    public Integer result_commonTimePickerDialog_minute(Serializable serializable) {
        if (!(serializable instanceof TimePickerSVDialog.Result)) return null;
        return ((TimePickerSVDialog.Result) serializable).getMinute();
    }

    public Serializable args_commonImageView(File file) {
        return CommonImageViewPage.Args.withFileAbsolutePath(file.getAbsolutePath());
    }
}
