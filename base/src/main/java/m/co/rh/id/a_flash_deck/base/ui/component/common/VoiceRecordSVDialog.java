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

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_flash_deck.base.R;
import m.co.rh.id.a_flash_deck.base.component.AudioRecorder;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.aprovider.Provider;

/**
 * Common dialog to show message only
 */
public class VoiceRecordSVDialog extends StatefulViewDialog<Activity> implements NavOnRequestPermissionResult, View.OnTouchListener {
    private static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 0;
    private static final String TAG = VoiceRecordSVDialog.class.getName();

    @NavInject
    private transient Provider mProvider;
    private transient CompositeDisposable mCompositeDisposable;
    private transient PublishSubject<Boolean> mOnRecordingSubject;
    private boolean mPermissionRequested;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mCompositeDisposable == null) {
            mCompositeDisposable = new CompositeDisposable();
        }
        if (mOnRecordingSubject == null) {
            mOnRecordingSubject = PublishSubject.create();
        }
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.common_voice_record, container, false);
        TextView titleText = rootLayout.findViewById(R.id.text_title);
        Button recordButton = rootLayout.findViewById(R.id.button_record);
        recordButton.setOnTouchListener(this);
        String onNotRecordTitle = activity.getString(R.string.press_and_hold_to_record);
        String onRecordTitle = activity.getString(R.string.speak_now);
        mCompositeDisposable.add(mOnRecordingSubject.observeOn(AndroidSchedulers.mainThread())
                .subscribe(aBoolean -> {
                    if (aBoolean) {
                        titleText.setText(onRecordTitle);
                    } else {
                        titleText.setText(onNotRecordTitle);
                    }
                }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mCompositeDisposable.dispose();
        mCompositeDisposable = null;
        mOnRecordingSubject.onComplete();
        mOnRecordingSubject = null;
    }

    @Override
    protected void onShowDialog(DialogInterface dialog) {
        if (!mPermissionRequested) {
            Activity activity = getNavigator().getActivity();
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_CODE_RECORD_AUDIO_PERMISSION);
            }
            mPermissionRequested = true;
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        AudioRecorder audioRecorder = mProvider.get(AudioRecorder.class);
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mOnRecordingSubject.onNext(true);
                audioRecorder.startRecording();
                return true;
            case MotionEvent.ACTION_UP:
                audioRecorder.stopRecording();
                mOnRecordingSubject.onNext(false);
                getNavigator().pop(Result.withFile(audioRecorder.getAudioRecord()));
                return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_RECORD_AUDIO_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    CommonNavConfig commonNavConfig = mProvider.get(CommonNavConfig.class);
                    String title = activity.getString(R.string.error);
                    String content = activity.getString(R.string.permission_denied_record_audio);
                    INavigator navigator = getNavigator();
                    navigator.pop();
                    navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                            commonNavConfig.args_commonMessageDialog(title, content));
                }
            }
        }
    }

    public static class Result implements Serializable {
        static Result withFile(File file) {
            Result result = new Result();
            result.mFile = file;
            return result;
        }

        private File mFile;

        public File getFile() {
            return mFile;
        }
    }
}
