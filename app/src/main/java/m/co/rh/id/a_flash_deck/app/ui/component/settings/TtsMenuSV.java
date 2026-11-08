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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textview.MaterialTextView;

import java.util.Locale;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.component.AudioService;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings menu for text-to-speech: toggles TTS playback and test-time
 * autoplay, shows the engine status, and lets the user verify the engine
 * with a sample sentence.
 */
public class TtsMenuSV extends StatefulView<Activity>
        implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private static final String UTTERANCE_ID_TTS_TEST = "tts_test";

    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;
    private transient MaterialTextView mTtsStatusText;
    private transient Activity mActivity;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        mActivity = activity;
        AppSharedPreferences appSharedPreferences =
                mSvProvider.get(AppSharedPreferences.class);
        View view = activity.getLayoutInflater()
                .inflate(R.layout.menu_tts, container, false);
        // status stays hidden while the engine init is pending
        mTtsStatusText = view.findViewById(R.id.text_tts_status);
        MaterialCheckBox switchTtsEnabled = view.findViewById(R.id.switch_tts_enabled);
        // apply current value before attaching the listener so
        // initialization does not write back to the shared preferences
        switchTtsEnabled.setChecked(appSharedPreferences.isTtsEnabled());
        switchTtsEnabled.setOnCheckedChangeListener(this);
        MaterialCheckBox switchTtsAutoplay = view.findViewById(R.id.switch_tts_autoplay);
        switchTtsAutoplay.setChecked(appSharedPreferences.isTtsAutoplayEnabled());
        switchTtsAutoplay.setOnCheckedChangeListener(this);
        MaterialButton buttonTestVoice = view.findViewById(R.id.button_tts_test_voice);
        buttonTestVoice.setOnClickListener(this);
        AudioService audioService =
                mSvProvider.get(AudioService.class);
        if (audioService.isSpeechAvailable()) {
            // engine already initialized in this process
            updateTtsReadyStatus(activity, audioService);
        } else if (audioService.isSpeechInitFailed()) {
            // late subscriber after a pre-existing engine failure: the
            // availability subject will not emit again on its own
            showTtsUnavailableStatus();
        }
        // trigger the lazy engine init, the subscription below updates
        // the status once the engine reports itself available
        audioService.getAvailableLanguages();
        mSvProvider.get(RxDisposer.class).add("TtsMenuSV_availability",
                audioService.getSpeechAvailabilitySubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::handleTtsAvailability));
        return view;
    }

    private void handleTtsAvailability(Boolean available) {
        Activity activity = mActivity;
        if (mTtsStatusText == null || activity == null) {
            return;
        }
        if (available) {
            updateTtsReadyStatus(activity, mSvProvider.get(AudioService.class));
        } else if (mSvProvider.get(AudioService.class).isSpeechInitFailed()) {
            showTtsUnavailableStatus();
        }
        // else: engine init still in progress, status stays hidden until
        // the availability subject reports the init outcome
    }

    private void showTtsUnavailableStatus() {
        mTtsStatusText.setText(R.string.tts_unavailable);
        mTtsStatusText.setVisibility(View.VISIBLE);
    }

    private void updateTtsReadyStatus(Activity activity,
                                      AudioService audioService) {
        Set<Locale> availableLanguages = audioService.getAvailableLanguages();
        mTtsStatusText.setText(activity.getString(R.string.tts_status_ready,
                Locale.getDefault().getDisplayName(), availableLanguages.size()));
        mTtsStatusText.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.button_tts_test_voice) {
            Activity activity = mActivity;
            if (activity == null) {
                return;
            }
            boolean spoken = mSvProvider.get(AudioService.class)
                    .speak(activity.getString(R.string.tts_test_sentence),
                            UTTERANCE_ID_TTS_TEST, Locale.getDefault());
            if (!spoken) {
                Toast.makeText(activity.getApplicationContext(),
                        activity.getString(R.string.tts_unavailable),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        AppSharedPreferences appSharedPreferences =
                mSvProvider.get(AppSharedPreferences.class);
        if (compoundButton.getId() == R.id.switch_tts_enabled) {
            appSharedPreferences.setTtsEnabled(isChecked);
        } else if (compoundButton.getId() == R.id.switch_tts_autoplay) {
            appSharedPreferences.setTtsAutoplayEnabled(isChecked);
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mTtsStatusText = null;
        mActivity = null; // avoid activity leak
    }
}
