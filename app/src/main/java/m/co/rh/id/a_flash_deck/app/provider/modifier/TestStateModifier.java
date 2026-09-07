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

package m.co.rh.id.a_flash_deck.app.provider.modifier;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.StudyDao;
import m.co.rh.id.a_flash_deck.base.dao.TestDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.entity.Test;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.TestEvent;
import m.co.rh.id.a_flash_deck.base.model.TestState;
import m.co.rh.id.a_flash_deck.base.provider.notifier.TestChangeNotifier;
import m.co.rh.id.a_flash_deck.base.repository.StudyRepository;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class TestStateModifier {
    private static final String TAG = TestStateModifier.class.getName();
    private final Object mLock = new Object();

    protected Context mAppContext;
    private ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<TestChangeNotifier> mTestChangeNotifier;
    protected ProviderValue<CardDao> mCardDao;
    private ProviderValue<StudyDao> mStudyDao;
    private ProviderValue<StudyRepository> mStudyRepository;
    private ProviderValue<TestDao> mTestDao;
    private ProviderValue<ILogger> mLogger;

    public TestStateModifier(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mTestChangeNotifier = provider.lazyGet(TestChangeNotifier.class);
        mCardDao = provider.lazyGet(CardDao.class);
        mStudyDao = provider.lazyGet(StudyDao.class);
        mStudyRepository = provider.lazyGet(StudyRepository.class);
        mTestDao = provider.lazyGet(TestDao.class);
        mLogger = provider.lazyGet(ILogger.class);
    }

    public Single<TestState> previousCard(TestState testState) {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                Test test = mTestDao.get().getTestById(testState.getTestId());
                testState.previousCard();
                serializeTest(testState, test);
                mTestChangeNotifier.get().testStateChange(testState);
                return testState;
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    public Single<TestState> nextCard(TestState testState) {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                Test test = mTestDao.get().getTestById(testState.getTestId());
                testState.nextCard();
                serializeTest(testState, test);
                mTestChangeNotifier.get().testStateChange(testState);
                return testState;
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    public Single<TestState> gradeCurrentCard(TestState testState, int grade) {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                Card card = testState.currentCard();
                mStudyRepository.get().applyGrade(card.id, grade, new Date());
                // check BEFORE nextCard(): TestState.nextCard() increments past the end on the last card
                boolean isLastCard = testState.getCurrentCardIndex() == testState.getTotalCards() - 1;
                if (!isLastCard) {
                    Test test = mTestDao.get().getTestById(testState.getTestId());
                    testState.nextCard();
                    serializeTest(testState, test);
                    mTestChangeNotifier.get().testStateChange(testState);
                }
                return testState;
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    public Single<TestState> stopActiveTest() {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                TestState testState = getActiveTestSync();
                return stopTestSync(testState);
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    public Single<TestState> stopTest(TestState testState) {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                return stopTestSync(testState);
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    private TestState stopTestSync(TestState testState) {
        Test test = mTestDao.get().getTestById(testState.getTestId());
        File file = new File(test.stateFileLocation);
        file.delete();
        mTestDao.get().delete(test);
        mTestChangeNotifier.get().stopTest(new TestEvent(testState, test));
        return testState;
    }

    /**
     * @return any test that is currently running
     */
    public Single<Optional<TestState>> getActiveTest() {
        return Single.fromCallable(() ->
                Optional.ofNullable(getActiveTestSync())).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    @Nullable
    private TestState getActiveTestSync() throws IOException, ClassNotFoundException {
        TestState testState;
        Test test = mTestDao.get().getCurrentTest();
        if (test != null) {
            try {
                testState = deserializeTest(test);
            } catch (Exception e) {
                mLogger.get().d(TAG, "Failed to load test state", e);
                mExecutorService.get().execute(() -> mTestDao.get().delete(test));
                throw e;
            }
        } else {
            testState = null;
        }
        return testState;
    }

    /**
     * Starts a new test with the cards of the given decks.
     * Suspended cards are excluded from the test and the number of excluded
     * cards is reported via {@link StartTestResult#skippedSuspendedCount}.
     *
     * @return the newly started test state with the excluded suspended count
     * @throws ValidationException when the deck list is empty or no selectable cards remain
     */
    public Single<StartTestResult> startTest(List<Deck> deckList) {
        return Single.fromCallable(() -> {
                    synchronized (mLock) {
                        if (deckList != null && !deckList.isEmpty()) {
                            List<Long> deckIds = new ArrayList<>();
                            for (Deck deck : deckList) {
                                deckIds.add(deck.id);
                            }
                            List<Card> allCards = mCardDao.get().findCardByDeckIds(deckIds);
                            List<Card> selectableCards = mStudyDao.get().findNonSuspendedCardsByDeckIds(deckIds);
                            int skippedSuspended = allCards.size() - selectableCards.size();
                            return new StartTestResult(prepareTest(selectableCards), skippedSuspended);
                        } else {
                            throw new ValidationException(mAppContext.getString(R.string.error_no_card_from_deck));
                        }
                    }
                })
                .subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    /**
     * Starts a new test with all due or new cards across all decks.
     *
     * @return the newly started test state
     * @throws ValidationException if no cards are due for study
     */
    public Single<TestState> startAllDueTest() {
        return Single.fromCallable(() -> {
                    synchronized (mLock) {
                        List<Card> cardList = mStudyRepository.get()
                                .findDueCards(System.currentTimeMillis());
                        if (cardList.isEmpty()) {
                            throw new ValidationException(mAppContext.getString(R.string.error_no_due_cards));
                        }
                        return prepareTest(cardList);
                    }
                })
                .subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    /**
     * Starts a new test with the cards of the given ids.
     * Suspended cards are silently excluded from the test.
     *
     * @return the newly started test state
     * @throws ValidationException when the id list is empty or no selectable cards remain
     */
    public Single<TestState> startTestWithCardIds(List<Long> cardIds) {
        return Single.fromCallable(() -> {
                    synchronized (mLock) {
                        if (cardIds != null && !cardIds.isEmpty()) {
                            List<Card> selectableCards = mStudyDao.get().findNonSuspendedCardsByCardIds(cardIds);
                            return prepareTest(selectableCards);
                        } else {
                            throw new ValidationException(mAppContext.getString(R.string.error_no_card_from_deck));
                        }
                    }
                })
                .subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    @NonNull
    public TestState prepareTest(List<Card> cardList) throws IOException {
        if (cardList.isEmpty()) {
            throw new ValidationException(mAppContext.getString(R.string.error_no_card_from_deck));
        } else {
            Collections.shuffle(cardList);
            for (Card card:cardList) {
                Random random = new Random();
                if(card.isReversibleQA){
                    card.isReversed = random.nextBoolean();
                }
            }
            String uuid = UUID.randomUUID().toString();
            File stateFileParent = new File(mAppContext.getFilesDir(),
                    "test/state");
            stateFileParent.mkdirs();
            File stateFile = new File(stateFileParent, uuid);
            stateFile.createNewFile();
            Test test = new Test();
            test.stateFileLocation = stateFile.getAbsolutePath();
            mTestDao.get().insertTest(test);
            TestState testState = new TestState(cardList, test.id);
            serializeTest(testState, test);
            TestEvent event = new TestEvent(testState, test);
            mTestChangeNotifier.get().startTest(event);
            return testState;
        }
    }

    private void serializeTest(TestState testState, Test test) throws IOException {
        File file = new File(test.stateFileLocation);
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(file))) {
            objectOutputStream.writeObject(testState);
        }
    }

    private TestState deserializeTest(Test test) throws IOException, ClassNotFoundException {
        File file = new File(test.stateFileLocation);
        TestState testState;
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(file))) {
            testState = (TestState) objectInputStream.readObject();
        }
        return testState;
    }
}
