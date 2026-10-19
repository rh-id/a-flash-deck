/*
 *     Copyright (C) 2021-present Ruby Hartono
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

package m.co.rh.id.a_flash_deck.timer.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.Serializable;
import java.util.ArrayList;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.timer.R;
import m.co.rh.id.a_flash_deck.timer.provider.command.NewNotificationTimerCmd;
import m.co.rh.id.a_flash_deck.timer.provider.command.UpdateNotificationTimerCmd;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class NotificationTimerDetailSVDialog extends StatefulViewDialog<Activity> implements RequireComponent<Provider>, View.OnClickListener, NavOnRequestPermissionResult {
    private static String TAG = NotificationTimerDetailSVDialog.class.getName();

    @NavInject
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient NewNotificationTimerCmd mNewNotificationTimerCmd;

    private String mTitle;
    private NotificationTimer mNotificationTimer;
    private SerialBehaviorSubject<String> mNameSubject;

    private transient TextWatcher mNameTextWatcher;
    private transient NumberPicker.OnValueChangeListener mPeriodOnValueChangeListener;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mNameTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mNotificationTimer.name = editable.toString();
                mNewNotificationTimerCmd.valid(mNotificationTimer);
            }
        };
        mPeriodOnValueChangeListener = (numberPicker, oldVal, newVal) ->
                mNotificationTimer.periodInMinutes = newVal;
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mNameSubject = new SerialBehaviorSubject<>();
        Args args = Args.of(mNavRoute);
        if (args != null && args.isUpdate()) {
            mNotificationTimer = args.getNotificationTimer();
        } else {
            mNotificationTimer = new NotificationTimer();
            mNotificationTimer.selectedDeckIds = getSelectedDecks();
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        mTitle = activity.getString(R.string.title_add_notification_timer);
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.dialog_notification_timer_detail, container, false);
        setNameSubject();
        NumberPicker numberPickerMinute = rootLayout.findViewById(R.id.number_picker_minutes);
        numberPickerMinute.setMinValue(15);
        numberPickerMinute.setMaxValue(120);
        numberPickerMinute.setValue(mNotificationTimer.periodInMinutes);
        numberPickerMinute.setOnValueChangedListener(mPeriodOnValueChangeListener);
        TextView textViewTitle = rootLayout.findViewById(R.id.text_title);
        textViewTitle.setText(mTitle);
        EditText editTextName = rootLayout.findViewById(R.id.edit_text_name);
        editTextName.addTextChangedListener(mNameTextWatcher);
        Button buttonCancel = rootLayout.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(this);
        Button buttonSave = rootLayout.findViewById(R.id.button_save);
        buttonSave.setOnClickListener(this);
        if (isUpdate()) {
            mNewNotificationTimerCmd = mSvProvider.get(UpdateNotificationTimerCmd.class);
        } else {
            mNewNotificationTimerCmd = mSvProvider.get(NewNotificationTimerCmd.class);
        }

        mRxDisposer.add("createView_onNameChanged", mNameSubject.getSubject()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        editTextName::setText));
        mRxDisposer.add("createView_onValidName",
                mNewNotificationTimerCmd.getNameValid()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s.isEmpty()) {
                                editTextName.setError(null);
                            } else {
                                editTextName.setError(s);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mNavRoute = null;
        mNotificationTimer = null;
        mNewNotificationTimerCmd = null;
        mNameTextWatcher = null;
        mPeriodOnValueChangeListener = null;
    }

    private String getSelectedDecks() {
        Args args = Args.of(mNavRoute);
        return args.getSelectedDecks();
    }

    private void setNameSubject() {
        String name = mNotificationTimer.name;
        if (name != null) {
            mNameSubject.onNext(mNotificationTimer.name);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_save) {
            if (mNewNotificationTimerCmd.valid(mNotificationTimer)) {
                boolean pushPermissionDialogOnSuccess = requestNotificationPermissionIfNeeded();
                mSvProvider.get(RxDisposer.class)
                        .add("onClick_newNotificationTimer", mNewNotificationTimerCmd.execute(mNotificationTimer)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((timerNotification, throwable) -> {
                                    Context context = mSvProvider.getContext();
                                    if (throwable != null) {
                                        String errorMessage;
                                        if (throwable.getCause() instanceof ValidationException) {
                                            errorMessage = throwable.getCause().getMessage();
                                        } else {
                                            errorMessage = throwable.getMessage();
                                        }
                                        UiUtils.pushMessageDialog(getNavigator(), context,
                                                R.string.title_error, errorMessage);
                                    } else {
                                        String successMessage;
                                        if (isUpdate()) {
                                            successMessage = context.getString(R.string.success_updating_new_notification_timer, timerNotification.name);
                                        } else {
                                            successMessage = context.getString(R.string.success_adding_new_notification_timer, timerNotification.name);
                                        }
                                        mSvProvider.get(ILogger.class)
                                                .i(TAG, successMessage);
                                        // captured before the pop because this dialog gets
                                        // disposed once popped, making getNavigator() invalid
                                        INavigator navigator = getNavigator();
                                        navigator.pop(Result.newResult(mNotificationTimer));
                                        // pushed AFTER the pop because a dialog pushed before
                                        // it would be consumed by that pop (live-test verified);
                                        // at this point the timer dialog is gone and the dialog
                                        // stays on screen until dismissed
                                        if (pushPermissionDialogOnSuccess) {
                                            UiUtils.pushMessageDialog(navigator, context,
                                                    R.string.title_error, R.string.permission_denied_notification);
                                        }
                                    }
                                }));
            } else {
                mLogger.i(TAG, mNewNotificationTimerCmd.getValidationError());
            }
        } else if (id == R.id.button_cancel) {
            getNavigator().pop();
        }
    }

    /**
     * Android 13 (API 33) and above require POST_NOTIFICATIONS to be requested
     * at runtime, notifications silently no-op otherwise. The save flow always
     * proceeds regardless of the permission state, this only tries to secure
     * the permission so the saved timer can actually fire (full flow mechanics
     * in {@link UiUtils}). Returns true when the permission is permanently
     * denied and the settings message dialog must be pushed after the save's
     * pop instead: a route pushed here would be immediately consumed by the
     * save's pop(Result), while the system request dialog is an OS overlay
     * and immune, hence it may fire upfront.
     */
    private boolean requestNotificationPermissionIfNeeded() {
        Activity activity = getNavigator().getActivity();
        if (UiUtils.isPostNotificationsGranted(activity)) {
            return false;
        }
        if (UiUtils.canAskPostNotificationsAgain(activity)) {
            UiUtils.requestPostNotificationsPermission(activity,
                    UiUtils.REQUEST_CODE_POST_NOTIFICATIONS_TIMER_DETAIL);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        UiUtils.onPermissionResult(requestCode, UiUtils.REQUEST_CODE_POST_NOTIFICATIONS_TIMER_DETAIL, grantResults,
                () -> { },
                // the save flow runs concurrently and may already have popped
                // this dialog by the time the result arrives, hence feedback
                // uses a toast instead of pushing a route
                () -> Toast.makeText(activity.getApplicationContext(),
                        activity.getString(R.string.permission_denied_notification),
                        Toast.LENGTH_SHORT).show());
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        return args.isUpdate();
    }

    public static class Args implements Serializable {
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

        public static Args withSelectedDecks(ArrayList<Deck> selectedDecks) {
            Args args = new Args();
            if (selectedDecks != null && !selectedDecks.isEmpty()) {
                JSONArray jsonArray = new JSONArray();
                for (Deck deck : selectedDecks) {
                    jsonArray.put(deck.id);
                }
                args.mSelectedDecks = jsonArray.toString();
            }
            return args;
        }

        public static Args forUpdate(NotificationTimer notificationTimer) {
            Args args = new Args();
            args.mNotificationTimer = notificationTimer;
            args.mSelectedDecks = notificationTimer.selectedDeckIds;
            args.operation = 1;
            return args;
        }

        private byte operation;
        private String mSelectedDecks;
        private NotificationTimer mNotificationTimer;

        private Args() {
        }

        public String getSelectedDecks() {
            return mSelectedDecks;
        }

        public NotificationTimer getNotificationTimer() {
            return mNotificationTimer;
        }

        public boolean isUpdate() {
            return operation == 1;
        }
    }

    public static class Result implements Serializable {
        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        public static Result newResult(NotificationTimer notificationTimer) {
            Result result = new Result();
            result.mNotificationTimer = notificationTimer;
            return result;
        }

        private NotificationTimer mNotificationTimer;

        private Result() {
        }

        public NotificationTimer getDeck() {
            return mNotificationTimer;
        }
    }
}
