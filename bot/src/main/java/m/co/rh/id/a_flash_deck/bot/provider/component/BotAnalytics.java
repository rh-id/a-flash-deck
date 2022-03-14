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

package m.co.rh.id.a_flash_deck.bot.provider.component;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.bot.dao.CardLogDao;
import m.co.rh.id.a_flash_deck.bot.dao.SuggestedCardDao;
import m.co.rh.id.a_flash_deck.bot.entity.CardLog;
import m.co.rh.id.a_flash_deck.bot.provider.notifier.SuggestedCardChangeNotifier;
import m.co.rh.id.a_flash_deck.bot.workmanager.BotAnalyzeWorker;
import m.co.rh.id.a_flash_deck.bot.workmanager.BotLogCleanerWorker;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class BotAnalytics implements ProviderDisposable {
    public static final int ACTION_OPEN_NOTIFICATION = 1;
    public static final int ACTION_DELETE_NOTIFICATION = 2;
    public static final int ACTION_OPEN_TEST_ANSWER = 3;

    private static final String TAG = BotAnalytics.class.getName();
    private static final String LOG_CLEANER_TAG = TAG + "_LOG_CLEANER_TAG";
    private static final String ANALYZE_TAG = TAG + "_ANALYZE_TAG";

    private ExecutorService mExecutorService;
    private WorkManager mWorkManager;
    private CardLogDao mCardLogDao;
    private SuggestedCardDao mSuggestedCardDao;
    private SuggestedCardChangeNotifier mSuggestedCardChangeNotifier;
    private DeckDao mDeckDao;
    private DeckChangeNotifier mDeckChangeNotifier;
    private CompositeDisposable mCompositeDisposable;

    public BotAnalytics(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mCardLogDao = provider.get(CardLogDao.class);
        mSuggestedCardDao = provider.get(SuggestedCardDao.class);
        mSuggestedCardChangeNotifier = provider.get(SuggestedCardChangeNotifier.class);
        mDeckDao = provider.get(DeckDao.class);
        mDeckChangeNotifier = provider.get(DeckChangeNotifier.class);
        mCompositeDisposable = new CompositeDisposable();
        init();
    }

    private void init() {
        mCompositeDisposable.add(mDeckChangeNotifier.getDeletedCardFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(card -> {
                    Long cardId = card.id;
                    mExecutorService.execute(() -> mCardLogDao.deleteCardLogsByCardId(cardId));
                    mExecutorService.execute(() -> {
                        mSuggestedCardDao.deleteSuggestedCardByCardId(cardId);
                        mSuggestedCardChangeNotifier.reloadSuggestedCard();
                    });
                }));
        mCompositeDisposable.add(mDeckChangeNotifier.getDeletedDeckFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(deck -> {
                    mExecutorService.execute(() -> {
                        List<Long> cardLogCardIds = mCardLogDao.findAllCardLogCardIds();
                        List<Long> existingCardIds = mDeckDao.findCardIdsByCardIds(cardLogCardIds);
                        Set<Long> removedCardIds = new LinkedHashSet<>(cardLogCardIds);
                        removedCardIds.removeAll(existingCardIds);
                        mCardLogDao.deleteCardLogsByCardIds(new ArrayList<>(removedCardIds));
                    });
                    mExecutorService.execute(() -> {
                        List<Long> suggestedCardIds = mSuggestedCardDao.findAllSuggestedCardCardIds();
                        List<Long> existingCardIds = mDeckDao.findCardIdsByCardIds(suggestedCardIds);
                        Set<Long> removedCardIds = new LinkedHashSet<>(suggestedCardIds);
                        removedCardIds.removeAll(existingCardIds);
                        mSuggestedCardDao.deleteSuggestedCardByCardIds(new ArrayList<>(removedCardIds));
                        mSuggestedCardChangeNotifier.reloadSuggestedCard();
                    });
                }));
        mExecutorService.execute(() -> {
            PeriodicWorkRequest logCleanerWorkerRequest = new PeriodicWorkRequest.Builder(BotLogCleanerWorker.class,
                    30, TimeUnit.DAYS)
                    .addTag(LOG_CLEANER_TAG)
                    .build();
            mWorkManager.enqueueUniquePeriodicWork(LOG_CLEANER_TAG, ExistingPeriodicWorkPolicy.KEEP, logCleanerWorkerRequest);
            PeriodicWorkRequest analyzeRequest = new PeriodicWorkRequest.Builder(BotAnalyzeWorker.class,
                    3, TimeUnit.DAYS)
                    .addTag(ANALYZE_TAG)
                    .build();
            mWorkManager.enqueueUniquePeriodicWork(ANALYZE_TAG, ExistingPeriodicWorkPolicy.KEEP, analyzeRequest);
        });
    }

    public void trackOpenNotification(long cardId) {
        mExecutorService.execute(() -> {
            CardLog cardLog = new CardLog();
            cardLog.action = ACTION_OPEN_NOTIFICATION;
            cardLog.cardId = cardId;
            mCardLogDao.insert(cardLog);
        });
    }

    public void trackDeleteNotification(long cardId) {
        mExecutorService.execute(() -> {
            CardLog cardLog = new CardLog();
            cardLog.action = ACTION_DELETE_NOTIFICATION;
            cardLog.cardId = cardId;
            mCardLogDao.insert(cardLog);
        });
    }

    public void trackOpenTestAnswer(long cardId) {
        mExecutorService.execute(() -> {
            CardLog cardLog = new CardLog();
            cardLog.action = ACTION_OPEN_TEST_ANSWER;
            cardLog.cardId = cardId;
            mCardLogDao.insert(cardLog);
        });
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
