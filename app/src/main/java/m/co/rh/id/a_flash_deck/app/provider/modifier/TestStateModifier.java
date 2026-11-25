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
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.component.McqDistractorGenerator;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.StudyDao;
import m.co.rh.id.a_flash_deck.base.dao.TestDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.entity.Test;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.McqOptions;
import m.co.rh.id.a_flash_deck.base.model.ReviewScheduler;
import m.co.rh.id.a_flash_deck.base.model.TestEvent;
import m.co.rh.id.a_flash_deck.base.model.TestMode;
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
    private ProviderValue<AppSharedPreferences> mAppSharedPreferences;
    private ProviderValue<McqDistractorGenerator> mMcqDistractorGenerator;

    public TestStateModifier(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mTestChangeNotifier = provider.lazyGet(TestChangeNotifier.class);
        mCardDao = provider.lazyGet(CardDao.class);
        mStudyDao = provider.lazyGet(StudyDao.class);
        mStudyRepository = provider.lazyGet(StudyRepository.class);
        mTestDao = provider.lazyGet(TestDao.class);
        mLogger = provider.lazyGet(ILogger.class);
        mAppSharedPreferences = provider.lazyGet(AppSharedPreferences.class);
        mMcqDistractorGenerator = provider.lazyGet(McqDistractorGenerator.class);
    }

    public Single<TestState> previousCard(TestState testState) {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                Card card = testState.previousCard();
                if (card == null) {
                    return testState;
                }
                Test test = mTestDao.get().getTestById(testState.getTestId());
                testState.onCardShown();
                generateMcqOptionsIfNeeded(testState);
                serializeTest(testState, test);
                mTestChangeNotifier.get().testStateChange(testState);
                return testState;
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    public Single<TestState> nextCard(TestState testState) {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                Card card = testState.nextCard();
                if (card == null) {
                    return testState;
                }
                Test test = mTestDao.get().getTestById(testState.getTestId());
                testState.onCardShown();
                generateMcqOptionsIfNeeded(testState);
                serializeTest(testState, test);
                mTestChangeNotifier.get().testStateChange(testState);
                return testState;
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    /**
     * Applies the given grade to the current card and records the answer in
     * the test state, a card graded AGAIN is requeued to the end of the test.
     *
     * @return the updated test state, sessionComplete is true when the last
     * card was graded
     */
    public Single<GradeResult> gradeCurrentCard(TestState testState, int grade) {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                Card card = testState.currentCard();
                Test test = mTestDao.get().getTestById(testState.getTestId());
                if (test == null) {
                    return new GradeResult(testState, false);
                }
                boolean cardExists = mCardDao.get().getCardByCardId(card.id) != null;
                Date now = new Date();
                if (cardExists) {
                    TestState.AnswerTiming timing = testState.recordAnswer(grade, now.getTime());
                    mStudyRepository.get().applyGrade(card.id, card.deckId, grade, now,
                            timing.beforeRevealMs, timing.afterRevealMs, card.isReversed);
                    boolean requeued = grade == ReviewScheduler.GRADE_AGAIN;
                    if (requeued) {
                        testState.requeueCurrentCard();
                        // in MCQ mode, remember the shown distractors and drop
                        // the used options so the re-show regenerates a fresh
                        // non-repeating set
                        if (testState.getTestMode() == TestMode.MULTIPLE_CHOICE) {
                            McqOptions mcqOptions = testState.getMcqOptions(card.id);
                            if (mcqOptions != null) {
                                testState.stashPendingExclusions(card.id, mcqOptions.getDistractorCardIds());
                                testState.removeMcqOptions(card.id);
                            }
                        }
                    }
                }
                // check BEFORE nextCard(): TestState.nextCard() no-ops on the last card
                boolean sessionComplete = testState.getCurrentCardIndex() >= testState.getTotalCards() - 1;
                if (!sessionComplete) {
                    testState.nextCard();
                    testState.onCardShown();
                    generateMcqOptionsIfNeeded(testState);
                    serializeTest(testState, test);
                    mTestChangeNotifier.get().testStateChange(testState);
                }
                return new GradeResult(testState, sessionComplete);
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    public Single<Optional<TestState>> stopActiveTest() {
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                TestState testState = getActiveTestSync();
                return Optional.ofNullable(stopTestSync(testState));
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
        if (testState == null) {
            return null;
        }
        Test test = mTestDao.get().getTestById(testState.getTestId());
        if (test == null) {
            return testState;
        }
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
        return Single.fromCallable(() -> {
            synchronized (mLock) {
                return Optional.ofNullable(getActiveTestSync());
            }
        }).subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    @Nullable
    private TestState getActiveTestSync() throws IOException, ClassNotFoundException {
        TestState testState;
        Test test = mTestDao.get().getCurrentTest();
        if (test != null) {
            try {
                testState = deserializeTest(test);
                // the card is shown again on resume, restart its elapsed time
                testState.onCardShown();
                // regenerate missing MCQ options so a resumed test can also
                // render cards whose options were never generated before
                generateMcqOptionsIfNeeded(testState);
            } catch (Exception e) {
                mLogger.get().d(TAG, "Failed to load test state", e);
                mExecutorService.get().execute(() -> {
                    if (test.stateFileLocation != null) {
                        new File(test.stateFileLocation).delete();
                    }
                    mTestDao.get().delete(test);
                });
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
     * @param testMode the test mode to run, normalized via {@link TestMode#normalize(int)}
     * @return the newly started test state with the excluded suspended count
     * @throws ValidationException when the deck list is empty or no selectable cards remain
     */
    public Single<StartTestResult> startTest(List<Deck> deckList, int testMode) {
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
                            return new StartTestResult(prepareTest(selectableCards, testMode), skippedSuspended);
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
                        return prepareTest(cardList, TestMode.normalize(
                                mAppSharedPreferences.get().getLastTestMode()));
                    }
                })
                .subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    /**
     * Starts a new test with the due or new cards of the given decks.
     *
     * @return the newly started test state
     * @throws ValidationException when the deck list is empty or no cards are due for study
     */
    public Single<TestState> startDueTestForDecks(List<Deck> deckList) {
        return Single.fromCallable(() -> {
                    synchronized (mLock) {
                        if (deckList != null && !deckList.isEmpty()) {
                            List<Long> deckIds = new ArrayList<>();
                            for (Deck deck : deckList) {
                                deckIds.add(deck.id);
                            }
                            List<Card> cardList = mStudyRepository.get()
                                    .findDueCardsByDeckIds(deckIds);
                            if (cardList.isEmpty()) {
                                throw new ValidationException(mAppContext.getString(R.string.error_no_due_cards));
                            }
                            return prepareTest(cardList, TestMode.normalize(
                                    mAppSharedPreferences.get().getLastTestMode()));
                        } else {
                            throw new ValidationException(mAppContext.getString(R.string.error_no_due_cards));
                        }
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
                            return prepareTest(selectableCards, TestMode.normalize(
                                    mAppSharedPreferences.get().getLastTestMode()));
                        } else {
                            throw new ValidationException(mAppContext.getString(R.string.error_no_card_from_deck));
                        }
                    }
                })
                .subscribeOn(Schedulers.from(mExecutorService.get()));
    }

    @NonNull
    public TestState prepareTest(List<Card> cardList, int testMode) throws IOException {
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
            testState.setTestMode(TestMode.normalize(testMode));
            testState.onSessionStarted();
            generateMcqOptionsIfNeeded(testState);
            serializeTest(testState, test);
            TestEvent event = new TestEvent(testState, test);
            mTestChangeNotifier.get().startTest(event);
            return testState;
        }
    }

    /**
     * Generates the multiple-choice options of the current card when the
     * test runs in MCQ mode. Options are kept in the TestState (and thus
     * serialized) so they stay stable across prev/next navigation and app
     * restarts; they are only regenerated once consumed (e.g. after an
     * AGAIN requeue), honoring the stashed distractor exclusions so the
     * same option set does not immediately repeat.
     */
    private void generateMcqOptionsIfNeeded(TestState testState) {
        if (testState.getTestMode() != TestMode.MULTIPLE_CHOICE) {
            return;
        }
        Card current = testState.currentCard();
        if (current == null || current.id == null) {
            return;
        }
        if (testState.getMcqOptions(current.id) != null) {
            // already generated, stable across navigation & resume
            return;
        }
        // honor the stashed exclusion ids of a previous requeue, so the
        // re-show does not repeat the same distractor set (unless the pool
        // would fall below 2 candidates); the stash is only consumed once
        // generation actually stored options — a failed generate (null)
        // keeps the exclusions intact for the next regeneration attempt
        List<Long> exclusions = testState.peekPendingExclusions(current.id);
        McqOptions options = mMcqDistractorGenerator.get().generate(
                current, testState.getCards(), exclusions, new Random());
        if (options != null) {
            testState.consumePendingExclusions(current.id);
            testState.putMcqOptions(current.id, options);
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
