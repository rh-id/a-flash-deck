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

import org.json.JSONArray;

import java.util.ArrayList;
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

public class GenerateDeckFromExistingWorker extends Worker {
    private static final String TAG = GenerateDeckFromExistingWorker.class.getName();

    public GenerateDeckFromExistingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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

        String deckIdsJson = getInputData().getString(WorkManagerKeys.AI_GENERATE_FROM_EXISTING_DECK_IDS);
        String prompt = getInputData().getString(WorkManagerKeys.AI_GENERATE_FROM_EXISTING_PROMPT);
        int maxCards = getInputData().getInt(WorkManagerKeys.AI_GENERATE_FROM_EXISTING_MAX_CARDS, 10);
        String modelId = getInputData().getString(WorkManagerKeys.AI_GENERATE_FROM_EXISTING_MODEL_ID);

        try {
            ArrayList<Long> deckIds = new ArrayList<>();
            JSONArray deckIdsArray = new JSONArray(deckIdsJson);
            for (int i = 0; i < deckIdsArray.length(); i++) {
                deckIds.add(deckIdsArray.getLong(i));
            }

            ArrayList<Deck> decks = new ArrayList<>(deckDao.findDeckByIds(deckIds));
            ArrayList<Card> cards = new ArrayList<>(deckDao.findCardByDeckIds(deckIds));

            AiGeneratedDeck aiDeck = geminiService.generateDeckFromExisting(decks, cards, prompt, maxCards, modelId).blockingGet();
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
                    context.getString(R.string.ai_generation_from_existing_success, deck.name, String.valueOf(aiDeck.cards.size())));

            return Result.success();
        } catch (Exception exception) {
            Context context = getApplicationContext();
            appNotificationHandler.postGeneralMessage(
                    context.getString(R.string.ai_notification_title),
                    context.getString(R.string.ai_generation_from_existing_failed, exception.getMessage()));
            logger.e(TAG, "Failed to generate deck from existing decks", exception);
            return Result.failure();
        }
    }
}