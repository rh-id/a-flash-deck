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

package m.co.rh.id.a_flash_deck.ai.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Date;

import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.model.AiGeneratedCard;
import m.co.rh.id.a_flash_deck.ai.model.AiGeneratedDeck;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class GenerateDeckWorker extends Worker {
    private static final String TAG = GenerateDeckWorker.class.getName();

    public GenerateDeckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        GeminiService geminiService = provider.get(GeminiService.class);
        DeckDao deckDao = provider.get(DeckDao.class);
        DeckChangeNotifier deckChangeNotifier = provider.get(DeckChangeNotifier.class);
        IAppNotificationHandler appNotificationHandler = provider.get(IAppNotificationHandler.class);
        ILogger logger = provider.get(ILogger.class);

        String topic = getInputData().getString(WorkManagerKeys.AI_GENERATE_DECK_TOPIC);
        int cardCount = getInputData().getInt(WorkManagerKeys.AI_GENERATE_DECK_CARD_COUNT, 10);
        String modelId = getInputData().getString(WorkManagerKeys.AI_GENERATE_DECK_MODEL_ID);

        try {
            AiGeneratedDeck aiDeck = geminiService.generateDeckFromTopic(topic, cardCount, modelId).blockingGet();
            Deck deck = new Deck();
            deck.name = aiDeck.deckName;
            deck.createdDateTime = new Date();
            deck.updatedDateTime = new Date();
            deckDao.insertDeck(deck);
            deckChangeNotifier.deckAdded(deck);

            int ordinal = 0;
            for (AiGeneratedCard aiCard : aiDeck.cards) {
                Card card = new Card();
                card.deckId = deck.id;
                card.ordinal = ordinal++;
                card.question = aiCard.question;
                card.answer = aiCard.answer;
                card.isReversibleQA = false;
                deckDao.insertCard(card);
                deckChangeNotifier.cardAdded(card);
            }

            Context context = getApplicationContext();
            appNotificationHandler.postGeneralMessage(
                    context.getString(R.string.ai_notification_title),
                    context.getString(R.string.ai_generation_success, deck.name, String.valueOf(cardCount)));

            return Result.success();
        } catch (Exception exception) {
            Context context = getApplicationContext();
            appNotificationHandler.postGeneralMessage(
                    context.getString(R.string.ai_notification_title),
                    context.getString(R.string.ai_generation_failed, topic, exception.getMessage()));
            logger.e(TAG, "Failed to generate deck from topic: " + topic, exception);
            return Result.failure();
        }
    }
}