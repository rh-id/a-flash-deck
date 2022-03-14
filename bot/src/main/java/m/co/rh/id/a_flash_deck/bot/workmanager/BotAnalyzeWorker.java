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

package m.co.rh.id.a_flash_deck.bot.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.bot.R;
import m.co.rh.id.a_flash_deck.bot.dao.CardLogDao;
import m.co.rh.id.a_flash_deck.bot.dao.SuggestedCardDao;
import m.co.rh.id.a_flash_deck.bot.entity.SuggestedCard;
import m.co.rh.id.a_flash_deck.bot.provider.component.BotAnalytics;
import m.co.rh.id.a_flash_deck.bot.provider.notifier.SuggestedCardChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class BotAnalyzeWorker extends Worker {
    private static final String TAG = BotAnalyzeWorker.class.getName();

    public BotAnalyzeWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        ExecutorService executorService = provider.get(ExecutorService.class);
        ILogger logger = provider.get(ILogger.class);
        IAppNotificationHandler appNotificationHandler = provider.get(IAppNotificationHandler.class);
        SuggestedCardChangeNotifier suggestedCardChangeNotifier = provider.get(SuggestedCardChangeNotifier.class);
        DeckDao deckDao = provider.get(DeckDao.class);
        CardLogDao cardLogDao = provider.get(CardLogDao.class);
        SuggestedCardDao suggestedCardDao = provider.get(SuggestedCardDao.class);
        int countCards = suggestedCardDao.countSuggestedCard();
        if (countCards > 0) {
            suggestedCardDao.deleteAllSuggestedCard();
        }
        LocalDate today = LocalDate.now();
        LocalDate _1dayBefore = today.minusDays(1);
        LocalDate _2dayBefore = today.minusDays(2);
        long _2dayCreatedFrom = _2dayBefore.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long _2dayCreatedTo = _2dayBefore.atTime(OffsetTime.MAX).toInstant().toEpochMilli();
        long _1dayCreatedFrom = _1dayBefore.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long _1dayCreatedTo = _1dayBefore.atTime(OffsetTime.MAX).toInstant().toEpochMilli();
        long todayCreatedFrom = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long todayCreatedTo = today.atTime(OffsetTime.now()).toInstant().toEpochMilli();
        List<Long> cardIds = cardLogDao.findCardLogCardIdByCreatedDateFromTo(_2dayCreatedFrom, todayCreatedTo);
        // check if the card actually still exist
        List<Long> existingCardIds = deckDao.findCardIdsByCardIds(cardIds);
        Set<Long> selectedCardIds = new LinkedHashSet<>(existingCardIds);
        List<Future<CardIdScore>> cardFutureList = new ArrayList<>();
        for (Long cardId : selectedCardIds) {
            cardFutureList.add(executorService.submit(() -> {
                int count2DayOpenNotification = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_OPEN_NOTIFICATION, _2dayCreatedFrom, _2dayCreatedTo);
                int count2DayDeleteNotification = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_DELETE_NOTIFICATION, _2dayCreatedFrom, _2dayCreatedTo);
                int count2DayOpenTestAnswer = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_OPEN_TEST_ANSWER, _2dayCreatedFrom, _2dayCreatedTo);
                int count1DayOpenNotification = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_OPEN_NOTIFICATION, _1dayCreatedFrom, _1dayCreatedTo);
                int count1DayDeleteNotification = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_DELETE_NOTIFICATION, _1dayCreatedFrom, _1dayCreatedTo);
                int count1DayOpenTestAnswer = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_OPEN_TEST_ANSWER, _1dayCreatedFrom, _1dayCreatedTo);
                int countTodayOpenNotification = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_OPEN_NOTIFICATION, todayCreatedFrom, todayCreatedTo);
                int countTodayDeleteNotification = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_DELETE_NOTIFICATION, todayCreatedFrom, todayCreatedTo);
                int countTodayOpenTestAnswer = cardLogDao.countCardLogByCardIdAndActionAndCreatedDateFromTo(cardId, BotAnalytics.ACTION_OPEN_TEST_ANSWER, todayCreatedFrom, todayCreatedTo);
                int score = 0;
                if (count2DayOpenNotification > 0 && count2DayOpenNotification <= 3) {
                    score += 3;
                }
                if (count2DayDeleteNotification > 1) {
                    score -= 1;
                }
                if (count2DayOpenTestAnswer > 0) {
                    score += 3;
                }

                if (count1DayOpenNotification > 0 && count1DayOpenNotification <= 3) {
                    score += 2;
                }
                if (count1DayDeleteNotification > 1) {
                    score -= 1;
                }
                if (count1DayOpenTestAnswer > 0) {
                    score += 2;
                }

                if (countTodayOpenNotification > 0 && countTodayOpenNotification <= 3) {
                    score += 1;
                }
                if (countTodayDeleteNotification > 1) {
                    score -= 1;
                }
                if (countTodayOpenTestAnswer > 0) {
                    score += 1;
                }
                CardIdScore cardIdScore = new CardIdScore();
                cardIdScore.cardId = cardId;
                cardIdScore.score = score;
                return cardIdScore;
            }));
        }
        int size = cardFutureList.size();
        for (int i = 0; i < size; i++) {
            Future<CardIdScore> cardIdScoreFuture = cardFutureList.get(i);
            try {
                CardIdScore cardIdScore = cardIdScoreFuture.get();
                if (cardIdScore.score >= 3) {
                    SuggestedCard suggestedCard = new SuggestedCard();
                    suggestedCard.cardId = cardIdScore.cardId;
                    suggestedCardDao.insert(suggestedCard);
                }
            } catch (Exception e) {
                logger.d(TAG, "Failed to process some Card ID");
            }
        }
        suggestedCardChangeNotifier.reloadSuggestedCard();
        countCards = suggestedCardDao.countSuggestedCard();
        if (countCards > 0) {
            Context context = getApplicationContext();
            appNotificationHandler.postGeneralMessage(context.getString(R.string.flash_bot),
                    context.getString(R.string.flash_bot_message));
        }
        return Result.success();
    }

    private static class CardIdScore {
        Long cardId;
        int score;
    }
}
