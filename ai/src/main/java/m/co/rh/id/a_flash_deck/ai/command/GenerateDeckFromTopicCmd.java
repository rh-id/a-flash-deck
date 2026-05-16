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

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.workmanager.GenerateDeckWorker;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerTags;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class GenerateDeckFromTopicCmd {
    private static final String TAG = GenerateDeckFromTopicCmd.class.getName();

    private final Context mContext;
    private final ProviderValue<WorkManager> mWorkManager;
    private final BehaviorSubject<String> mTopicValidationSubject;

    public GenerateDeckFromTopicCmd(Provider provider) {
        mContext = provider.getContext();
        mWorkManager = provider.lazyGet(WorkManager.class);
        mTopicValidationSubject = BehaviorSubject.create();
    }

    public boolean valid(String topic, int cardCount) {
        if (topic == null || topic.trim().isEmpty()) {
            mTopicValidationSubject.onNext(mContext.getString(R.string.ai_topic_is_required));
            return false;
        }
        if (cardCount < 1) {
            mTopicValidationSubject.onNext(mContext.getString(R.string.ai_card_count_range));
            return false;
        }
        mTopicValidationSubject.onNext("");
        return true;
    }

    public void execute(String topic, int cardCount, String modelId) {
        Data inputData = new Data.Builder()
                .putString(WorkManagerKeys.AI_GENERATE_DECK_TOPIC, topic)
                .putInt(WorkManagerKeys.AI_GENERATE_DECK_CARD_COUNT, cardCount)
                .putString(WorkManagerKeys.AI_GENERATE_DECK_MODEL_ID, modelId)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(GenerateDeckWorker.class)
                .setInputData(inputData)
                .addTag(WorkManagerTags.AI_GENERATE_DECK)
                .build();
        mWorkManager.get().enqueue(workRequest);
    }

    public Flowable<String> getTopicValidation() {
        return Flowable.fromObservable(mTopicValidationSubject, BackpressureStrategy.BUFFER);
    }
}