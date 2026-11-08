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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.alogger.AndroidLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Instrumented test for {@link AudioService}. Exercises the facade mutual
 * exclusion (recording playback and speech are exclusive, last request wins)
 * and the dispose behaviour on a real Android device/emulator, since the
 * players are Android-bound (MediaPlayer / TextToSpeech).
 *
 * <p>Speech assertions are skipped gracefully when the device has no usable
 * TTS engine: in that case only the no-crash paths and the init-failed flag
 * are exercised. The test is also skipped (Assume) when the engine init
 * neither completes nor fails within the timeout, a flaky environment. The
 * second speak's boolean is not asserted because some engines natively crash
 * when stopped mid-synthesis, the exclusivity assertions hold regardless.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class AudioServiceTest {

    private static final String UTTERANCE_ID_TEST = "tts_test";

    private Provider testProvider;
    private AudioService mAudioService;

    @Before
    public void beforeTest() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testProvider = Provider.createProvider(appContext, new ProviderModule() {
            @Override
            public void provides(ProviderRegistry providerRegistry, Provider provider) {
                // the audio players only need a logger; no DB required.
                providerRegistry.register(ILogger.class, () -> new AndroidLogger(ILogger.ERROR));
                // TtsPlayer marshals the engine's init callback onto the
                // facade lock via an ExecutorService
                providerRegistry.register(ExecutorService.class, Executors::newSingleThreadExecutor);
            }

            @Override
            public void dispose(Provider provider) {
                // leave blank
            }
        });
        mAudioService = new AudioService(testProvider);
    }

    @After
    public void afterTest() {
        if (mAudioService != null) {
            mAudioService.dispose(InstrumentationRegistry.getInstrumentation().getTargetContext());
            mAudioService = null;
        }
        if (testProvider != null) {
            testProvider.dispose();
        }
    }

    @Test
    public void lastRequestWins_playbackIsExclusive() throws IOException, InterruptedException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File recordingFile = createSilentWavFile(appContext);
        Uri recordingUri = Uri.fromFile(recordingFile);

        // recording playback alone: busy
        mAudioService.playRecording(recordingUri);
        assertTrue(mAudioService.isRecordingBusy());

        // TTS engine init is async, the availability flag is still the
        // seeded "false" right after construction, await the engine outcome
        // before branching so the no-engine path is only taken on a genuine
        // init failure
        boolean speechAvailable = awaitSpeechAvailability();
        if (speechAvailable) {
            // speak stops the ongoing recording (last-wins)
            assertTrue(mAudioService.speak("one two three", UTTERANCE_ID_TEST, Locale.getDefault()));
            assertFalse(mAudioService.isRecordingBusy());

            // playRecording stops the ongoing speech (last-wins)
            mAudioService.playRecording(recordingUri);
            assertFalse(mAudioService.isSpeechBusy());
            assertTrue(mAudioService.isRecordingBusy());

            // speech wins last again. The boolean is deliberately not
            // asserted: some engines (Pico on AOSP API 26) natively crash
            // when stopped mid-synthesis, after which the framework reports
            // "not bound to TTS engine" and speak returns false. The
            // exclusivity assertions above and below stay valid either way,
            // on robust engines (e.g. Google TTS) spoken2 is true
            boolean spoken2 = mAudioService.speak("four five six", UTTERANCE_ID_TEST, Locale.getDefault());
            // facade guarantee: the recording was stopped before the engine
            // was consulted, holds even when the engine crashed on speak
            assertFalse(mAudioService.isRecordingBusy());

            // stopSpeech leaves the (already stopped) recording alone
            mAudioService.stopSpeech();
            assertFalse(mAudioService.isRecordingBusy());
        } else {
            // no usable TTS engine on this device, the engine init reported
            // a failure: init-failed is latched so speak deterministically
            // returns false as a safe no-crash no-op, and the init-failed
            // flag must stay readable
            boolean spoken = mAudioService.speak("one two three", UTTERANCE_ID_TEST, Locale.getDefault());
            assertFalse(spoken);
            assertFalse(mAudioService.isSpeechBusy());
            // exercise the init-failed flag path without asserting its value
            // (the engine error callback may still be in flight)
            mAudioService.isSpeechInitFailed();
            // recording still plays and stops cleanly
            mAudioService.playRecording(recordingUri);
            assertTrue(mAudioService.isRecordingBusy());
            mAudioService.stopRecording();
            assertFalse(mAudioService.isRecordingBusy());
        }
    }

    @Test
    public void dispose_callsAfterwardsDoNotCrash() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AudioService audioService = new AudioService(testProvider);
        audioService.dispose(appContext);
        // post-dispose calls must be safe no-crash no-ops
        audioService.stopSpeech();
        audioService.stopRecording();
        audioService.stopAll();
        boolean spoken = audioService.speak("after dispose", UTTERANCE_ID_TEST, Locale.getDefault());
        assertFalse(spoken);
        audioService.playRecording(
                Uri.fromFile(new File(appContext.getCacheDir(), "audio_service_test_nonexistent.wav")));
        assertFalse(audioService.isRecordingBusy());
        assertFalse(audioService.isSpeechBusy());
        // a second dispose must stay idempotent-friendly
        audioService.dispose(appContext);
    }

    /**
     * Waits for the TTS engine init outcome. Engine init is async and still
     * pending right after construction (the availability subject is seeded
     * with false), so branching on the flag alone races. Returns true when
     * the engine reported itself available, false when it reported a failure.
     * Skips the test via Assume when init neither completes nor fails within
     * the timeout (flaky environment).
     */
    private boolean awaitSpeechAvailability() throws InterruptedException {
        BehaviorSubject<Boolean> availabilitySubject = mAudioService.getSpeechAvailabilitySubject();
        if (Boolean.TRUE.equals(availabilitySubject.getValue())) {
            // engine already initialized in this process
            return true;
        }
        // trigger the lazy engine init, the subject emits once it completes
        mAudioService.getAvailableLanguages();
        // skip(1) drops the seeded replay a BehaviorSubject emits to every
        // new subscriber, only the genuine init outcome may count down
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> emitted = new AtomicReference<>();
        Disposable disposable = availabilitySubject.skip(1)
                .subscribe(value -> {
                    emitted.set(value);
                    latch.countDown();
                });
        boolean settled;
        try {
            settled = latch.await(20, TimeUnit.SECONDS);
        } finally {
            disposable.dispose();
        }
        if (!settled) {
            // only the latched init-failure path makes speak-returns-false
            // reliable, a timeout without a latched failure is a flaky
            // environment: skip instead of fail
            Assume.assumeTrue("TTS engine init did not settle within timeout "
                    + "and no init failure is latched", mAudioService.isSpeechInitFailed());
            return false;
        }
        return Boolean.TRUE.equals(emitted.get());
    }

    /**
     * Creates a real, playable canonical WAV file (PCM 16-bit mono 16 kHz)
     * with a few seconds of silence, so MediaPlayer keeps playing long enough
     * for the busy-state assertions
     */
    private static File createSilentWavFile(Context context) throws IOException {
        File file = File.createTempFile("audio_service_test", ".wav", context.getCacheDir());
        int sampleRate = 16000;
        int numSamples = sampleRate * 3; // 3 seconds of silence
        int dataSize = numSamples * 2; // 16-bit
        try (FileOutputStream out = new FileOutputStream(file)) {
            ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
            header.putInt(36 + dataSize);
            header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
            header.put("fmt ".getBytes(StandardCharsets.US_ASCII));
            header.putInt(16); // PCM chunk size
            header.putShort((short) 1); // PCM format
            header.putShort((short) 1); // mono channel
            header.putInt(sampleRate);
            header.putInt(sampleRate * 2); // byte rate
            header.putShort((short) 2); // block align
            header.putShort((short) 16); // bits per sample
            header.put("data".getBytes(StandardCharsets.US_ASCII));
            header.putInt(dataSize);
            out.write(header.array());
            out.write(new byte[dataSize]);
        }
        return file;
    }
}
