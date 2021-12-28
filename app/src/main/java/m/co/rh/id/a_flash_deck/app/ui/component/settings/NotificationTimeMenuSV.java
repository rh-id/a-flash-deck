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

package m.co.rh.id.a_flash_deck.app.ui.component.settings;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimeChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class NotificationTimeMenuSV extends StatefulView<Activity> implements View.OnClickListener {
    private static final String TAG = NotificationTimeMenuSV.class.getName();
    @NavInject
    private transient INavigator mNavigator;

    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;

    private DateFormat mTimeFormat;
    private Serializable mStartTimeResult;
    private Serializable mEndTimeResult;

    public NotificationTimeMenuSV() {
        mTimeFormat = new SimpleDateFormat("HH:mm");
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        View view = activity.getLayoutInflater().inflate(R.layout.menu_notification_time, container, false);
        AppSharedPreferences appSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
        String startTime = mTimeFormat.format(appSharedPreferences.getNotificationStartTime());
        String endTime = mTimeFormat.format(appSharedPreferences.getNotificationEndTime());
        TextView titleText = view.findViewById(R.id.text_title);
        titleText.setText(activity.getString(R.string.notification_time_title, startTime, endTime));
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(this);
        mSvProvider.get(RxDisposer.class)
                .add("createView_onStartEndTimeChanged",
                        mSvProvider.get(NotificationTimeChangeNotifier.class)
                                .getStartEndDateFlow().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(dateDateEntry -> {
                                    Context context = mSvProvider.getContext();
                                    String startTimeChange = mTimeFormat.format(dateDateEntry.getKey());
                                    String endTimeChange = mTimeFormat.format(dateDateEntry.getValue());
                                    titleText.setText(context
                                            .getString(R.string.notification_time_title, startTimeChange, endTimeChange));
                                    mSvProvider.get(ILogger.class)
                                            .i(TAG, context.getString(R.string.notification_time_updated));
                                }));
        return view;
    }

    @Override
    public void onClick(View view) {
        AppSharedPreferences appSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
        String startTimeTitle = view.getContext().getString(R.string.title_start_time);
        int startTimeHourOfDay = appSharedPreferences.getNotificationStartTimeHourOfDay();
        int startTimeMinute = appSharedPreferences.getNotificationStartTimeMinute();
        mNavigator.push(Routes.COMMON_TIMEPICKER_DIALOG,
                mSvProvider.get(CommonNavConfig.class)
                        .args_commonTimePickerDialog(startTimeTitle,
                                startTimeHourOfDay, startTimeMinute, true),
                (navigator, navRoute, activity, currentView) -> {
                    Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                    Serializable startTimeResult = provider.get(CommonNavConfig.class).result_commonTimePickerDialog(navRoute);
                    if (startTimeResult != null) {
                        mStartTimeResult = startTimeResult;
                        AppSharedPreferences appSharedPreferencesEndTime = provider.get(AppSharedPreferences.class);
                        String endTimeTitle = provider.getContext().getString(R.string.title_end_time);
                        int endTimeHourOfDay = appSharedPreferencesEndTime.getNotificationEndTimeHourOfDay();
                        int endTimeMinute = appSharedPreferencesEndTime.getNotificationEndTimeMinute();
                        navigator.push(Routes.COMMON_TIMEPICKER_DIALOG,
                                provider.get(CommonNavConfig.class).args_commonTimePickerDialog(endTimeTitle,
                                        endTimeHourOfDay, endTimeMinute, true),
                                (navigator1, navRoute1, activity1, currentView1) -> {
                                    Provider provider1 = (Provider) navigator1.getNavConfiguration().getRequiredComponent();
                                    CommonNavConfig commonNavConfig = provider1.get(CommonNavConfig.class);
                                    Serializable endTimeResult =
                                            commonNavConfig.result_commonTimePickerDialog(navRoute1);
                                    if (endTimeResult != null) {
                                        mEndTimeResult = endTimeResult;
                                        int startHourOfDay = commonNavConfig.result_commonTimePickerDialog_hourOfDay(mStartTimeResult);
                                        int startMinute = commonNavConfig.result_commonTimePickerDialog_minute(mStartTimeResult);
                                        int endHourOfDay = commonNavConfig.result_commonTimePickerDialog_hourOfDay(mEndTimeResult);
                                        int endMinute = commonNavConfig.result_commonTimePickerDialog_minute(mEndTimeResult);
                                        AppSharedPreferences appSharedPreferences1 = provider1.get(AppSharedPreferences.class);
                                        appSharedPreferences1
                                                .setNotificationStartTime(startHourOfDay, startMinute);
                                        appSharedPreferences1
                                                .setNotificationEndTime(endHourOfDay, endMinute);
                                        provider1.get(NotificationTimeChangeNotifier.class)
                                                .onDateChanged(appSharedPreferences1.getNotificationStartTime(),
                                                        appSharedPreferences1.getNotificationEndTime());
                                    }
                                });
                    }
                });
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }
}
