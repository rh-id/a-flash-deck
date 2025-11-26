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
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import java.util.concurrent.locks.ReentrantLock;

import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;
import m.co.rh.id.aprovider.ProviderValue;

public class AudioPlayer implements ProviderDisposable {
    private static final String TAG = AudioPlayer.class.getName();

    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;
    private final ReentrantLock mLock;
    private volatile MediaPlayer mediaPlayer;

    public AudioPlayer(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mLock = new ReentrantLock();
    }

    public void play(Uri uri) {
        if (mediaPlayer != null) {
            stop();
        }
        mLock.lock();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
            mediaPlayer.setDataSource(mAppContext, uri);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            mLogger.get().e(TAG, e.getMessage(), e);
            // Clean up MediaPlayer if any step fails
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.release();
                } catch (Exception releaseException) {
                    mLogger.get().e(TAG, "Error releasing MediaPlayer: " + releaseException.getMessage());
                }
                mediaPlayer = null;
            }
        } finally {
            mLock.unlock();
        }
    }

    public void stop() {
        mLock.lock();
        try {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                } catch (RuntimeException e) {
                    mLogger.get().e(TAG, "Error stopping player: " + e.getMessage(), e);
                }
                mediaPlayer.reset();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } finally {
            mLock.unlock(); // Always release lock
        }
        mLock.unlock();
    }

    @Override
    public void dispose(Context context) {
        if (mediaPlayer != null) {
            stop();
        }
    }
}
