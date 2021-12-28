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

package m.co.rh.id.a_flash_deck.app.provider.modifier;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.TestDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.entity.Test;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.TestEvent;
import m.co.rh.id.a_flash_deck.base.model.TestState;
import m.co.rh.id.a_flash_deck.base.provider.notifier.TestChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class TestStateModifier {
    private static final String TAG = TestStateModifier.class.getName();

    protected Context mAppContext;
    private ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<TestChangeNotifier> mTestChangeNotifier;
    protected ProviderValue<DeckDao> mDeckDao;
    private ProviderValue<TestDao> mTestDao;
    private ProviderValue<ILogger> mLogger;

    public TestStateModifier(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mTestChangeNotifier = provider.lazyGet(TestChangeNotifier.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mTestDao = provider.lazyGet(TestDao.class);
        mLogger = provider.lazyGet(ILogger.class);
    }

    public Single<TestState> previousCard(TestState testState) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            Test test = mTestDao.get().getTestById(testState.getTestId());
            testState.previousCard();
            serializeTest(testState, test);
            mTestChangeNotifier.get().testStateChange(testState);
            return testState;
        }));
    }

    public Single<TestState> nextCard(TestState testState) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            Test test = mTestDao.get().getTestById(testState.getTestId());
            testState.nextCard();
            serializeTest(testState, test);
            mTestChangeNotifier.get().testStateChange(testState);
            return testState;
        }));
    }

    public Single<TestState> stopActiveTest() {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            TestState testState = getActiveTestSync();
            return stopTestSync(testState);
        }));
    }

    public Single<TestState> stopTest(TestState testState) {
        return Single.fromFuture(mExecutorService.get().submit(() ->
                stopTestSync(testState)));
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
        return Single.fromFuture(mExecutorService.get().submit(() ->
                Optional.ofNullable(getActiveTestSync())));
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

    public Single<TestState> startTest(List<Deck> deckList) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
                    if (deckList != null && !deckList.isEmpty()) {
                        List<Card> cardList = mDeckDao.get().getCardsByDecks(deckList);
                        if (cardList.isEmpty()) {
                            throw new ValidationException(mAppContext.getString(R.string.error_no_card_from_deck));
                        } else {
                            Collections.shuffle(cardList);
                            String uuid = UUID.randomUUID().toString();
                            File stateFileParent = new File(mAppContext.getFilesDir(),
                                    "test/state");
                            stateFileParent.mkdirs();
                            File stateFile = new File(stateFileParent, uuid);
                            stateFile.createNewFile();
                            Test test = new Test();
                            test.stateFileLocation = stateFile.getAbsolutePath();
                            mTestDao.get().insertTest(test);
                            TestState testState = new TestState(deckList, cardList, test.id);
                            serializeTest(testState, test);
                            TestEvent event = new TestEvent(testState, test);
                            mTestChangeNotifier.get().startTest(event);
                            return testState;
                        }
                    } else {
                        throw new ValidationException(mAppContext.getString(R.string.error_no_card_from_deck));
                    }
                })
        );
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
