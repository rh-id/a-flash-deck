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

package m.co.rh.id.a_flash_deck.app.ui.component.settings;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.RadioGroup;

import com.google.android.material.textview.MaterialTextView;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.model.FsrsParams;
import m.co.rh.id.a_flash_deck.base.model.SchedulerMode;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.repository.StudyRepository;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings menu for the review scheduler: selects the scheduling mode
 * (classic SM-2-lite or FSRS-6) and, for FSRS, the desired
 * retention. Changing either setting persists the preference and triggers a
 * one-shot background recompute of all due dates.
 */
public class SchedulerMenuSV extends StatefulView<Activity>
        implements RadioGroup.OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener {
    private static final String TAG = SchedulerMenuSV.class.getName();

    /**
     * SeekBar progress is 0..RETENTION_PERCENT_RANGE and maps to
     * 70..98 percent desired retention (SeekBar android:min needs API 26,
     * minSdk is 23)
     */
    private static final int RETENTION_PERCENT_MIN = 70;
    private static final int RETENTION_PERCENT_RANGE = 28;

    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;
    private transient MaterialTextView mRetentionValueText;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        AppSharedPreferences appSharedPreferences =
                mSvProvider.get(AppSharedPreferences.class);
        View view = activity.getLayoutInflater()
                .inflate(R.layout.menu_scheduler, container, false);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup_scheduler_mode);
        // apply current value before attaching the listener so
        // initialization does not write back to the shared preferences
        radioGroup.check(getSelectedRadioId(appSharedPreferences));
        radioGroup.setOnCheckedChangeListener(this);
        mRetentionValueText = view.findViewById(R.id.text_retention_value);
        int retentionPercent = getRetentionPercent(
                appSharedPreferences.getDesiredRetention());
        updateRetentionValueText(activity, retentionPercent);
        SeekBar seekBar = view.findViewById(R.id.seekBar_retention);
        seekBar.setProgress(retentionPercent - RETENTION_PERCENT_MIN);
        seekBar.setOnSeekBarChangeListener(this);
        // the desired retention only affects the FSRS scheduler
        View containerRetention = view.findViewById(R.id.container_retention);
        containerRetention.setVisibility(isFsrs(appSharedPreferences)
                ? View.VISIBLE : View.GONE);
        return view;
    }

    private boolean isFsrs(AppSharedPreferences appSharedPreferences) {
        return appSharedPreferences.getSchedulerMode() == SchedulerMode.FSRS;
    }

    private int getSelectedRadioId(AppSharedPreferences appSharedPreferences) {
        return isFsrs(appSharedPreferences)
                ? R.id.radio_scheduler_fsrs : R.id.radio_scheduler_classic;
    }

    private static int getRetentionPercent(double desiredRetention) {
        return (int) Math.round(desiredRetention * 100);
    }

    private void updateRetentionValueText(Context context, int retentionPercent) {
        mRetentionValueText.setText(context.getString(
                R.string.scheduler_retention_percent_format, retentionPercent));
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
        int mode = checkedId == R.id.radio_scheduler_fsrs
                ? SchedulerMode.FSRS : SchedulerMode.SM2;
        AppSharedPreferences appSharedPreferences =
                mSvProvider.get(AppSharedPreferences.class);
        if (mode == appSharedPreferences.getSchedulerMode()) {
            return;
        }
        appSharedPreferences.setSchedulerMode(mode);
        // the desired retention only affects the FSRS scheduler
        View retentionGroup = radioGroup.getRootView()
                .findViewById(R.id.container_retention);
        retentionGroup.setVisibility(mode == SchedulerMode.FSRS
                ? View.VISIBLE : View.GONE);
        rescheduleAll(radioGroup.getContext());
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!fromUser) {
            return;
        }
        updateRetentionValueText(seekBar.getContext(),
                RETENTION_PERCENT_MIN + progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // not needed
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int retentionPercent = RETENTION_PERCENT_MIN + seekBar.getProgress();
        mSvProvider.get(AppSharedPreferences.class)
                .setDesiredRetention(retentionPercent / 100.0);
        rescheduleAll(seekBar.getContext());
    }

    /**
     * One-shot background recompute of all due dates after a scheduler
     * setting change, disposing the subscription with the view's provider.
     */
    private void rescheduleAll(Context context) {
        mSvProvider.get(RxDisposer.class)
                .add("SchedulerMenuSV_rescheduleAll",
                        mSvProvider.get(StudyRepository.class)
                                .rescheduleAllForSchedulerChange()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((count, throwable) -> {
                                    if (throwable != null) {
                                        mSvProvider.get(ILogger.class)
                                                .e(TAG, context.getString(
                                                        R.string.error_failed_to_reschedule_reviews),
                                                        throwable);
                                    }
                                }));
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mRetentionValueText = null;
    }
}
