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

package m.co.rh.id.a_flash_deck.ai.command;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;

import java.util.ArrayList;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.workmanager.GenerateDeckFromExistingWorker;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerTags;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class GenerateDeckFromExistingCmd {
    private static final String TAG = GenerateDeckFromExistingCmd.class.getName();

    private final Context mContext;
    private final ProviderValue<WorkManager> mWorkManager;
    private final BehaviorSubject<String> mPromptValidationSubject;

    public GenerateDeckFromExistingCmd(Provider provider) {
        mContext = provider.getContext();
        mWorkManager = provider.lazyGet(WorkManager.class);
        mPromptValidationSubject = BehaviorSubject.create();
    }

    public boolean valid(String prompt, int maxCards) {
        if (prompt == null || prompt.trim().isEmpty()) {
            mPromptValidationSubject.onNext(mContext.getString(R.string.ai_prompt_is_required));
            return false;
        }
        if (maxCards < 1) {
            mPromptValidationSubject.onNext(mContext.getString(R.string.ai_max_cards_range));
            return false;
        }
        mPromptValidationSubject.onNext("");
        return true;
    }

    public void execute(ArrayList<Long> selectedDeckIds, String prompt, int maxCards, String modelId) {
        JSONArray deckIdsArray = new JSONArray(selectedDeckIds);
        String deckIdsJson = deckIdsArray.toString();

        Data inputData = new Data.Builder()
                .putString(WorkManagerKeys.AI_GENERATE_FROM_EXISTING_DECK_IDS, deckIdsJson)
                .putString(WorkManagerKeys.AI_GENERATE_FROM_EXISTING_PROMPT, prompt)
                .putInt(WorkManagerKeys.AI_GENERATE_FROM_EXISTING_MAX_CARDS, maxCards)
                .putString(WorkManagerKeys.AI_GENERATE_FROM_EXISTING_MODEL_ID, modelId)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(GenerateDeckFromExistingWorker.class)
                .setInputData(inputData)
                .addTag(WorkManagerTags.AI_GENERATE_DECK_FROM_EXISTING)
                .build();
        mWorkManager.get().enqueue(workRequest);
    }

    public Flowable<String> getPromptValidation() {
        return Flowable.fromObservable(mPromptValidationSubject, BackpressureStrategy.BUFFER);
    }
}