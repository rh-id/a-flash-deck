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

package m.co.rh.id.a_flash_deck.timer.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.constants.WorkManagerKeys;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class NotificationTimerWorker extends Worker {
    private static final String TAG = NotificationTimerWorker.class.getName();

    public NotificationTimerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long notificationTimerId = getInputData().getLong(WorkManagerKeys.LONG_NOTIFICATION_TIMER_ID,
                -1);
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
        int startHour = appSharedPreferences.getNotificationStartTimeHourOfDay();
        int startMin = appSharedPreferences.getNotificationStartTimeMinute();
        int endHour = appSharedPreferences.getNotificationEndTimeHourOfDay();
        int endMin = appSharedPreferences.getNotificationEndTimeMinute();
        LocalTime startLocalTime = LocalTime.of(startHour, startMin);
        LocalTime endLocalTime = LocalTime.of(endHour, endMin);

        Date currentDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMin = calendar.get(Calendar.MINUTE);
        LocalTime currentLocalTime = LocalTime.of(currentHour, currentMin);

        NotificationTimerDao notificationTimerDao = provider.get(NotificationTimerDao.class);
        NotificationTimer notificationTimer = notificationTimerDao.findById(notificationTimerId);
        if (currentLocalTime.isBefore(startLocalTime) || currentLocalTime.isAfter(endLocalTime)) {
            provider.get(ILogger.class)
                    .d(TAG, "Notification timer " + notificationTimer.name + " is outside notification time config");
            return Result.success();
        }

        IAppNotificationHandler iAppNotificationHandler = provider.get(IAppNotificationHandler.class);
        iAppNotificationHandler.cancelNotificationSync(notificationTimer);

        try {
            JSONArray jsonArray = new JSONArray(notificationTimer.selectedDeckIds);
            int size = jsonArray.length();
            List<Long> deckIds = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                deckIds.add(jsonArray.getLong(i));
            }
            List<Card> cards = provider.get(DeckDao.class).getCardByDeckIds(deckIds);
            Collections.shuffle(cards);
            Card selectedCard = null;
            if (notificationTimer.displayedCardIds == null) {
                selectedCard = cards.get(0);
                JSONArray displayedCardIdsJsonInput = new JSONArray();
                displayedCardIdsJsonInput.put(selectedCard.id);
                notificationTimer.displayedCardIds = displayedCardIdsJsonInput.toString();
                notificationTimer.currentCardId = selectedCard.id;
            } else {
                JSONArray displayedCardIdsJson = new JSONArray(notificationTimer.displayedCardIds);
                int displayedCardIdsSize = displayedCardIdsJson.length();
                List<Long> displayedCardIds = new ArrayList<>();
                for (int i = 0; i < displayedCardIdsSize; i++) {
                    displayedCardIds.add(displayedCardIdsJson.getLong(i));
                }
                List<Long> allCardIds = new ArrayList<>();
                for (Card card : cards) {
                    allCardIds.add(card.id);
                }
                allCardIds.removeAll(displayedCardIds);
                if (allCardIds.isEmpty()) {
                    selectedCard = cards.get(0);
                    JSONArray displayedCardIdsJsonInput = new JSONArray();
                    displayedCardIdsJsonInput.put(selectedCard.id);
                    notificationTimer.displayedCardIds = displayedCardIdsJsonInput.toString();
                    notificationTimer.currentCardId = selectedCard.id;
                } else {
                    Collections.shuffle(allCardIds);
                    Long cardId = allCardIds.get(0);
                    displayedCardIdsJson.put(cardId);
                    notificationTimer.displayedCardIds = displayedCardIdsJson.toString();
                    notificationTimer.currentCardId = cardId;

                    for (Card card : cards) {
                        if (card.id.equals(cardId)) {
                            selectedCard = card;
                            break;
                        }
                    }
                }
            }
            notificationTimerDao.update(notificationTimer);
            provider.get(IAppNotificationHandler.class)
                    .postNotificationTimer(notificationTimer, selectedCard);
            provider.get(ILogger.class)
                    .d(TAG, "Notification timer " + notificationTimer.name + " executed");
        } catch (JSONException jsonException) {
            provider.get(ILogger.class)
                    .e(TAG, jsonException.getMessage(), jsonException);
            return Result.failure();
        }
        return Result.success();
    }
}
