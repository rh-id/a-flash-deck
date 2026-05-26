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

import m.co.rh.id.a_flash_deck.ai.model.AiGeneratedCard;
import m.co.rh.id.a_flash_deck.ai.model.AiGeneratedDeck;
import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public abstract class BaseGenerateDeckWorker extends Worker {

    public BaseGenerateDeckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    protected Provider getProvider() {
        return BaseApplication.of(getApplicationContext()).getProvider();
    }

    protected Deck saveAiDeckToDatabase(AiGeneratedDeck aiDeck) {
        Provider provider = getProvider();
        DeckDao deckDao = provider.get(DeckDao.class);
        DeckChangeNotifier deckChangeNotifier = provider.get(DeckChangeNotifier.class);

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

        return deck;
    }

    protected void postNotification(String title, String message) {
        Provider provider = getProvider();
        IAppNotificationHandler handler = provider.get(IAppNotificationHandler.class);
        handler.postGeneralMessage(title, message);
    }

    protected void postNotification(String title, String message, long deckId) {
        Provider provider = getProvider();
        IAppNotificationHandler handler = provider.get(IAppNotificationHandler.class);
        handler.postDeckMessage(title, message, deckId);
    }

    protected ILogger getLogger() {
        return getProvider().get(ILogger.class);
    }
}