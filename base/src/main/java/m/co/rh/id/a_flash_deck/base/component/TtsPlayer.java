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
import android.speech.tts.TextToSpeech;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Not thread-safe by itself. Only ever constructed and invoked by
 * {@link AudioService}, which passes its facade lock in; every public
 * method must be called while that lock is held. The engine's init
 * callback is marshalled onto that same lock via an executor, so no
 * framework thread ever touches this class's state directly.
 */
class TtsPlayer implements ProviderDisposable {
    private static final String TAG = TtsPlayer.class.getName();
    // pause before the single speak retry, see speakInternal
    private static final long SPEAK_RETRY_DELAY_MS = 120;

    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ReentrantLock mFacadeLock;
    private final BehaviorSubject<Boolean> mAvailabilitySubject;
    private volatile TextToSpeech textToSpeech;
    private volatile boolean mAvailable;
    private volatile boolean mInitFailed;
    private boolean mDisposed;
    private PendingSpeakRequest mPendingRequest;

    TtsPlayer(Provider provider, ReentrantLock facadeLock) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mFacadeLock = facadeLock;
        mAvailabilitySubject = BehaviorSubject.createDefault(false);
    }

    public boolean speak(String text, String utteranceId, Locale locale) {
        mFacadeLock.lock();
        try {
            if (mDisposed || mInitFailed) {
                return false;
            }
            if (textToSpeech == null) {
                mPendingRequest = new PendingSpeakRequest(text, utteranceId, locale);
                initInternal();
                if (mInitFailed) {
                    // engine creation threw synchronously, the failure is
                    // already latched: nothing stays pending and the caller
                    // is told the speak attempt failed
                    mPendingRequest = null;
                    return false;
                }
            } else if (!mAvailable) {
                // engine init is still in progress, latest request wins
                mPendingRequest = new PendingSpeakRequest(text, utteranceId, locale);
            } else {
                return speakInternal(text, utteranceId, locale);
            }
            // optimistic, fails are reported through the subjects
            return true;
        } finally {
            mFacadeLock.unlock();
        }
    }

    public void stop() {
        mFacadeLock.lock();
        try {
            // a new user intent supersedes any pending request
            mPendingRequest = null;
            if (textToSpeech != null) {
                try {
                    textToSpeech.stop();
                } catch (Exception e) {
                    mLogger.get().e(TAG, "Error stopping TextToSpeech: " + e.getMessage(), e);
                }
            }
        } finally {
            mFacadeLock.unlock();
        }
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    /**
     * True when the engine init has permanently failed for this process
     * lifetime (the constructor threw or the engine reported an error), as
     * opposed to an init still being in progress
     */
    public boolean isInitFailed() {
        return mInitFailed;
    }

    public boolean isSpeaking() {
        mFacadeLock.lock();
        try {
            return textToSpeech != null && mAvailable && textToSpeech.isSpeaking();
        } finally {
            mFacadeLock.unlock();
        }
    }

    public Set<Locale> getAvailableLanguages() {
        mFacadeLock.lock();
        try {
            if (textToSpeech == null && !mInitFailed && !mDisposed) {
                // trigger the lazy engine init, the availability subject
                // emits once it completes
                initInternal();
            }
            if (textToSpeech != null && mAvailable) {
                Set<Locale> availableLanguages = textToSpeech.getAvailableLanguages();
                if (availableLanguages != null) {
                    return availableLanguages;
                }
            }
        } finally {
            mFacadeLock.unlock();
        }
        return Collections.emptySet();
    }

    public BehaviorSubject<Boolean> getAvailabilitySubject() {
        return mAvailabilitySubject;
    }

    /**
     * Resolves a BCP-47 language tag (e.g. deck.ttsLanguage) to a Locale,
     * falling back to the system default when the tag is null or empty
     */
    public Locale resolveLocale(String languageTag) {
        if (languageTag != null && !languageTag.isEmpty()) {
            return Locale.forLanguageTag(languageTag);
        }
        return Locale.getDefault();
    }

    private void initInternal() {
        try {
            // the enqueue returns immediately so the framework releases its
            // internal lock before our handler runs; the executor marshals
            // the callback onto the facade lock, the enqueue itself must
            // never block on our locks
            textToSpeech = new TextToSpeech(mAppContext, status ->
                    mExecutorService.get().execute(() -> handleInitResult(status)));
        } catch (Exception e) {
            mLogger.get().e(TAG, "Error creating TextToSpeech: " + e.getMessage(), e);
            handleInitFailure();
        }
    }

    private void handleInitResult(int status) {
        // dispatched here by the executor, serialized onto the facade lock
        // with all other player access
        mFacadeLock.lock();
        try {
            if (mDisposed) {
                return;
            }
            if (status == TextToSpeech.SUCCESS) {
                mAvailable = true;
                mAvailabilitySubject.onNext(true);
                applyPendingRequest();
            } else {
                handleInitFailure();
            }
        } finally {
            mFacadeLock.unlock();
        }
    }

    // lock-free by design: only ever called while the facade lock is held
    // (from initInternal's synchronous-failure path and from handleInitResult)
    private void handleInitFailure() {
        if (mInitFailed) {
            // already latched (e.g. a synchronous engine-creation throw),
            // stay idempotent so a late listener callback cannot re-latch
            return;
        }
        // the engine stays unavailable for the rest of this process lifetime
        mInitFailed = true;
        mAvailable = false;
        mPendingRequest = null;
        if (textToSpeech != null) {
            try {
                textToSpeech.shutdown();
            } catch (Exception e) {
                mLogger.get().e(TAG, "Error shutting down TextToSpeech: " + e.getMessage(), e);
            }
            textToSpeech = null;
        }
        mAvailabilitySubject.onNext(false);
    }

    private void applyPendingRequest() {
        if (mPendingRequest != null) {
            PendingSpeakRequest pendingRequest = mPendingRequest;
            mPendingRequest = null;
            speakInternal(pendingRequest.text, pendingRequest.utteranceId, pendingRequest.locale);
        }
    }

    /**
     * Speaks the text, retrying once after a short pause when the engine
     * transiently rejects the request. A stop() while the engine is actively
     * synthesizing followed immediately by speak() can transiently return
     * ERROR on some engines while the previous utterance is being torn down,
     * so a false return reliably means the engine is unusable, not a
     * transient stop-speak race
     */
    private boolean speakInternal(String text, String utteranceId, Locale locale) {
        if (speakAttempt(text, utteranceId, locale)) {
            return true;
        }
        mLogger.get().d(TAG, "Transient speak rejection, retrying once");
        // brief pause while still holding the lock, the same magnitude as
        // AudioPlayer's synchronous prepare(), bounded and only ever once.
        // mDisposed/mInitFailed cannot change here, every mutation of them
        // (init callback, dispose) takes this same lock held by the caller
        try {
            Thread.sleep(SPEAK_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return speakAttempt(text, utteranceId, locale);
    }

    private boolean speakAttempt(String text, String utteranceId, Locale locale) {
        try {
            setLanguageInternal(locale);
            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            return result == TextToSpeech.SUCCESS;
        } catch (Exception e) {
            mLogger.get().e(TAG, "Error speaking text: " + e.getMessage(), e);
            return false;
        }
    }

    private void setLanguageInternal(Locale locale) {
        if (locale != null
                && textToSpeech.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            textToSpeech.setLanguage(locale);
            return;
        }
        Locale systemDefault = Locale.getDefault();
        if (textToSpeech.isLanguageAvailable(systemDefault) >= TextToSpeech.LANG_AVAILABLE) {
            textToSpeech.setLanguage(systemDefault);
            return;
        }
        Locale engineDefault = textToSpeech.getDefaultLanguage();
        if (engineDefault != null) {
            textToSpeech.setLanguage(engineDefault);
        }
    }

    @Override
    public void dispose(Context context) {
        mFacadeLock.lock();
        try {
            if (mDisposed) {
                return;
            }
            mDisposed = true;
            mPendingRequest = null;
            if (textToSpeech != null) {
                try {
                    textToSpeech.stop();
                    textToSpeech.shutdown();
                } catch (Exception e) {
                    mLogger.get().e(TAG, "Error disposing TextToSpeech: " + e.getMessage(), e);
                }
                textToSpeech = null;
            }
            mAvailable = false;
            mAvailabilitySubject.onNext(false);
            mAvailabilitySubject.onComplete();
        } finally {
            mFacadeLock.unlock();
        }
    }

    private static class PendingSpeakRequest {
        public final String text;
        public final String utteranceId;
        public final Locale locale;

        private PendingSpeakRequest(String text, String utteranceId, Locale locale) {
            this.text = text;
            this.utteranceId = utteranceId;
            this.locale = locale;
        }
    }
}
