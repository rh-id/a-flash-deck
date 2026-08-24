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

package m.co.rh.id.a_flash_deck.ai.command;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.workmanager.GenerateDeckFromCardWorker;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerTags;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class GenerateDeckFromCardCmd {
    private final Context mContext;
    private final ProviderValue<WorkManager> mWorkManager;
    private final BehaviorSubject<String> mPromptValidationSubject;

    public GenerateDeckFromCardCmd(Provider provider) {
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

    public void execute(long cardId, String prompt, int maxCards, String modelId) {
        Data inputData = new Data.Builder()
                .putLong(WorkManagerKeys.AI_GENERATE_FROM_CARD_CARD_ID, cardId)
                .putString(WorkManagerKeys.AI_GENERATE_FROM_CARD_PROMPT, prompt)
                .putInt(WorkManagerKeys.AI_GENERATE_FROM_CARD_MAX_CARDS, maxCards)
                .putString(WorkManagerKeys.AI_GENERATE_FROM_CARD_MODEL_ID, modelId)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(GenerateDeckFromCardWorker.class)
                .setInputData(inputData)
                .addTag(WorkManagerTags.AI_GENERATE_DECK_FROM_CARD)
                .build();
        mWorkManager.get().enqueue(workRequest);
    }

    public Flowable<String> getPromptValidation() {
        return Flowable.fromObservable(mPromptValidationSubject, BackpressureStrategy.BUFFER);
    }
}
