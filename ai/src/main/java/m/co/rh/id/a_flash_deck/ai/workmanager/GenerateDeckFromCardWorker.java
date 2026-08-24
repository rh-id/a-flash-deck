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

package m.co.rh.id.a_flash_deck.ai.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.model.AiGeneratedDeck;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.aprovider.Provider;

public class GenerateDeckFromCardWorker extends BaseGenerateDeckWorker {
    private static final String TAG = GenerateDeckFromCardWorker.class.getName();

    public GenerateDeckFromCardWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Provider provider = getProvider();
        GeminiService geminiService = provider.get(GeminiService.class);
        CardDao cardDao = provider.get(CardDao.class);

        long cardId = getInputData().getLong(WorkManagerKeys.AI_GENERATE_FROM_CARD_CARD_ID, -1L);
        String prompt = getInputData().getString(WorkManagerKeys.AI_GENERATE_FROM_CARD_PROMPT);
        int maxCards = getInputData().getInt(WorkManagerKeys.AI_GENERATE_FROM_CARD_MAX_CARDS, 10);
        String modelId = getInputData().getString(WorkManagerKeys.AI_GENERATE_FROM_CARD_MODEL_ID);

        try {
            Card card = cardDao.getCardByCardId(cardId);
            if (card == null) {
                throw new IllegalStateException("Card not found: " + cardId);
            }

            AiGeneratedDeck aiDeck = geminiService.generateDeckFromCard(card, prompt, maxCards, modelId).blockingGet();
            Deck deck = saveAiDeckToDatabase(aiDeck);

            String notificationTitle = getApplicationContext().getString(R.string.ai_notification_title);
            postNotification(notificationTitle,
                    getApplicationContext().getString(R.string.ai_generation_from_card_success, deck.name, String.valueOf(aiDeck.cards.size())),
                    deck.id);

            return Result.success();
        } catch (Exception exception) {
            String notificationTitle = getApplicationContext().getString(R.string.ai_notification_title);
            postNotification(notificationTitle,
                    getApplicationContext().getString(R.string.ai_generation_from_card_failed, exception.getMessage()));
            getLogger().e(TAG, "Failed to generate deck from card", exception);
            return Result.failure();
        }
    }
}
