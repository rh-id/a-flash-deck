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

package m.co.rh.id.a_flash_deck.base.component;

import android.content.Context;
import android.net.Uri;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * Single entry point for all audio playback: voice recordings (via
 * {@link AudioPlayer}) and text-to-speech (via {@link TtsPlayer}). Every
 * public method serializes on a facade-level lock so a recording and a
 * speech never overlap, starting one stops the other (last request wins).
 *
 * <p>The facade lock is the only lock in the audio stack: both players are
 * package-private, not thread-safe by themselves, and are only ever touched
 * while the facade lock is held. TtsPlayer receives this lock in its
 * constructor and marshals the engine's init callback (which arrives from
 * framework threads holding none of our locks) onto it via an executor, so
 * no lock-ordering deadlock is possible. Only ever reach the players
 * through this facade.
 */
public class AudioService implements ProviderDisposable {

    private final Provider mProvider;
    private final ReentrantLock mLock;
    private final AudioPlayer mAudioPlayer;
    private final TtsPlayer mTtsPlayer;

    public AudioService(Provider provider) {
        mProvider = provider;
        mLock = new ReentrantLock();
        mAudioPlayer = new AudioPlayer(provider);
        mTtsPlayer = new TtsPlayer(provider, mLock);
    }

    /**
     * Stops any ongoing speech, then plays the voice recording at the given
     * URI (last request wins)
     */
    public void playRecording(Uri uri) {
        mLock.lock();
        try {
            mTtsPlayer.stop();
            mAudioPlayer.play(uri);
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Stops any ongoing recording playback, then speaks the given text with
     * the given locale (last request wins). The text must already be stripped
     * via {@link MarkdownRenderer#toSpeakableText(String)} by the caller.
     * Returns false when the TTS engine is unavailable, callers should show
     * a toast
     */
    public boolean speak(String text, String utteranceId, Locale locale) {
        mLock.lock();
        try {
            mAudioPlayer.stop();
            return mTtsPlayer.speak(text, utteranceId, locale);
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Stops any ongoing speech, ongoing recording playback is left untouched
     * (recordings historically keep playing across screens)
     */
    public void stopSpeech() {
        mLock.lock();
        try {
            mTtsPlayer.stop();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Stops any ongoing recording playback, ongoing speech is left untouched.
     * Has no production caller, kept for symmetry with {@link #stopSpeech()}
     */
    public void stopRecording() {
        mLock.lock();
        try {
            mAudioPlayer.stop();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Stops both recording playback and speech. Reserved for teardown paths
     * that need everything to go silent
     */
    public void stopAll() {
        mLock.lock();
        try {
            mTtsPlayer.stop();
            mAudioPlayer.stop();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * True when the TTS engine is initialized and ready
     */
    public boolean isSpeechAvailable() {
        mLock.lock();
        try {
            return mTtsPlayer.isAvailable();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * True when the engine init has permanently failed for this process
     * lifetime (the constructor threw or the engine reported an error), as
     * opposed to an init still being in progress
     */
    public boolean isSpeechInitFailed() {
        mLock.lock();
        try {
            return mTtsPlayer.isInitFailed();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Emits true once the TTS engine reports itself available (and false on
     * failure/dispose), subscribers should observe on the main thread
     */
    public BehaviorSubject<Boolean> getSpeechAvailabilitySubject() {
        mLock.lock();
        try {
            return mTtsPlayer.getAvailabilitySubject();
        } finally {
            mLock.unlock();
        }
    }

    public Set<Locale> getAvailableLanguages() {
        mLock.lock();
        try {
            return mTtsPlayer.getAvailableLanguages();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Resolves a BCP-47 language tag (e.g. deck.ttsLanguage) to a Locale,
     * falling back to the system default when the tag is null or empty
     */
    public Locale resolveLocale(String languageTag) {
        mLock.lock();
        try {
            return mTtsPlayer.resolveLocale(languageTag);
        } finally {
            mLock.unlock();
        }
    }

    /**
     * True while a voice recording is being played, test hook not used by
     * production code
     */
    public boolean isRecordingBusy() {
        mLock.lock();
        try {
            return mAudioPlayer.isPlaying();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * True while a speech utterance is queued or being spoken, test hook not
     * used by production code
     */
    public boolean isSpeechBusy() {
        mLock.lock();
        try {
            return mTtsPlayer.isSpeaking();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void dispose(Context context) {
        mLock.lock();
        try {
            mTtsPlayer.dispose(context);
            mAudioPlayer.dispose(context);
        } finally {
            mLock.unlock();
        }
    }
}
