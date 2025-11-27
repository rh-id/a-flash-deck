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
import android.media.MediaRecorder;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;
import m.co.rh.id.aprovider.ProviderValue;

public class AudioRecorder implements ProviderDisposable {
    private static final String TAG = AudioRecorder.class.getName();

    private final ProviderValue<FileHelper> mFileHelper;
    private final ProviderValue<ILogger> mLogger;
    private final ReentrantLock mLock;
    private volatile MediaRecorder mMediaRecorder;
    private File mAudioRecordFile;

    public AudioRecorder(Provider provider) {
        mFileHelper = provider.lazyGet(FileHelper.class);
        mLogger = provider.lazyGet(ILogger.class);
        mLock = new ReentrantLock();
    }

    public void startRecording() {
        mLock.lock();
        try {
            mAudioRecordFile = mFileHelper.get().createTempFile("audio-record");
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mMediaRecorder.setOutputFile(mAudioRecordFile.getAbsolutePath());
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch (Exception e) {
            mLogger.get().e(TAG, e.getMessage(), e);
            // Always clean up MediaRecorder on initialization failure
            if (mMediaRecorder != null) {
                try {
                    mMediaRecorder.release();
                } catch (Exception releaseException) {
                    mLogger.get().e(TAG, "Error releasing MediaRecorder: " + releaseException.getMessage());
                }
                mMediaRecorder = null;
            }
            mAudioRecordFile = null; // Clear invalid file reference
        } finally {
            mLock.unlock();
        }
    }

    public void stopRecording() {
        mLock.lock();
        try {
            if (mMediaRecorder != null) {
                try {
                    mMediaRecorder.stop();
                } catch (RuntimeException e) {
                    mLogger.get().e(TAG, "Error stopping recorder: " + e.getMessage(), e);
                }
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } finally {
            mLock.unlock(); // Always release lock
        }
    }

    public File getAudioRecord() {
        return mAudioRecordFile;
    }

    @Override
    public void dispose(Context context) {
        if (mMediaRecorder != null) {
            stopRecording();
        }
    }
}
