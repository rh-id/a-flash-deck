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

import org.json.JSONArray;

import java.util.List;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.workmanager.GenerateDeckFromImageWorker;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerTags;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class GenerateDeckFromImageCmd {
    private final Context mContext;
    private final ProviderValue<WorkManager> mWorkManager;
    private final BehaviorSubject<String> mValidationSubject;

    public GenerateDeckFromImageCmd(Provider provider) {
        mContext = provider.getContext();
        mWorkManager = provider.lazyGet(WorkManager.class);
        mValidationSubject = BehaviorSubject.create();
    }

    public boolean valid(List<String> imagePaths, int maxCards) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            mValidationSubject.onNext(mContext.getString(R.string.ai_images_required));
            return false;
        }
        if (maxCards < 1) {
            mValidationSubject.onNext(mContext.getString(R.string.ai_max_cards_range));
            return false;
        }
        mValidationSubject.onNext("");
        return true;
    }

    public void execute(List<String> imagePaths, int maxCards, String modelId) {
        execute(imagePaths, null, maxCards, modelId);
    }

    public void execute(List<String> imagePaths, String prompt, int maxCards, String modelId) {
        JSONArray imagePathsArray = new JSONArray(imagePaths);
        String imagePathsJson = imagePathsArray.toString();

        Data.Builder dataBuilder = new Data.Builder()
                .putString(WorkManagerKeys.AI_GENERATE_FROM_IMAGE_IMAGE_PATHS, imagePathsJson)
                .putInt(WorkManagerKeys.AI_GENERATE_FROM_IMAGE_MAX_CARDS, maxCards)
                .putString(WorkManagerKeys.AI_GENERATE_FROM_IMAGE_MODEL_ID, modelId);
        if (prompt != null && !prompt.trim().isEmpty()) {
            dataBuilder.putString(WorkManagerKeys.AI_GENERATE_FROM_IMAGE_PROMPT, prompt.trim());
        }
        Data inputData = dataBuilder.build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(GenerateDeckFromImageWorker.class)
                .setInputData(inputData)
                .addTag(WorkManagerTags.AI_GENERATE_DECK_FROM_IMAGE)
                .build();
        mWorkManager.get().enqueue(workRequest);
    }

    public Flowable<String> getValidationFlowable() {
        return Flowable.fromObservable(mValidationSubject, BackpressureStrategy.BUFFER);
    }
}
