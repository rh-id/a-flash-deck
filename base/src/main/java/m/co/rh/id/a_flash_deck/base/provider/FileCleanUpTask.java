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

package m.co.rh.id.a_flash_deck.base.provider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.TestDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Test;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Helper to cleanup unused file
 */
public class FileCleanUpTask {
    private static final String TAG = FileCleanUpTask.class.getName();

    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<TestDao> mTestDao;
    private final ProviderValue<CardDao> mCardDao;
    private final ProviderValue<CardMediaStore> mCardMediaStore;
    private final ProviderValue<ILogger> mLogger;

    public FileCleanUpTask(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mTestDao = provider.lazyGet(TestDao.class);
        mCardDao = provider.lazyGet(CardDao.class);
        mCardMediaStore = provider.lazyGet(CardMediaStore.class);
        mLogger = provider.lazyGet(ILogger.class);
        cleanUp();
    }

    private void cleanUp() {
        Future<Test> testFuture = mExecutorService.get().submit(() -> mTestDao.get().getCurrentTest());
        Future<List<String>> questionImageFileList = submitFileList(mCardMediaStore.get().getCardQuestionImageParent());
        Future<List<String>> questionVoiceFileList = submitFileList(mCardMediaStore.get().getCardQuestionVoiceParent());
        Future<List<String>> answerImageFileList = submitFileList(mCardMediaStore.get().getCardAnswerImageParent());
        Future<List<String>> answerVoiceFileList = submitFileList(mCardMediaStore.get().getCardAnswerVoiceParent());

        mExecutorService.get().execute(() -> {
            try {
                Test test = testFuture.get();
                if (test == null) {
                    List<Future<Boolean>> taskList = new ArrayList<>();
                    taskList.add(
                            mExecutorService.get().submit(() -> {
                                List<String> questionImageNames = questionImageFileList.get();
                                if (!questionImageNames.isEmpty()) {
                                    for (String questionImage : questionImageNames) {
                                        Card card = mCardDao.get().findCardByQuestionImage(questionImage);
                                        if (card == null) {
                                            mCardMediaStore.get().deleteCardQuestionImage(questionImage);
                                        }
                                    }
                                }
                                return true;
                            })
                    );
                    taskList.add(
                            mExecutorService.get().submit(() -> {
                                List<String> questionVoiceNames = questionVoiceFileList.get();
                                if (!questionVoiceNames.isEmpty()) {
                                    for (String questionVoiceName : questionVoiceNames) {
                                        Card card = mCardDao.get().findCardByQuestionVoice(questionVoiceName);
                                        if (card == null) {
                                            mCardMediaStore.get().deleteCardQuestionVoice(questionVoiceName);
                                        }
                                    }
                                }
                                return true;
                            })
                    );
                    taskList.add(
                            mExecutorService.get().submit(() -> {
                                List<String> answerImageNames = answerImageFileList.get();
                                if (!answerImageNames.isEmpty()) {
                                    for (String answerImage : answerImageNames) {
                                        Card card = mCardDao.get().findCardByAnswerImage(answerImage);
                                        if (card == null) {
                                            mCardMediaStore.get().deleteCardAnswerImage(answerImage);
                                        }
                                    }
                                }
                                return true;
                            })
                    );
                    taskList.add(
                            mExecutorService.get().submit(() -> {
                                List<String> answerVoiceNames = answerVoiceFileList.get();
                                if (!answerVoiceNames.isEmpty()) {
                                    for (String answerVoiceName : answerVoiceNames) {
                                        Card card = mCardDao.get().findCardByAnswerVoice(answerVoiceName);
                                        if (card == null) {
                                            mCardMediaStore.get().deleteCardAnswerVoice(answerVoiceName);
                                        }
                                    }
                                }
                                return true;
                            })
                    );
                    for (Future<Boolean> task : taskList) {
                        task.get();
                    }
                }
            } catch (Exception e) {
                mLogger.get().d(TAG, "Error occurred when cleaning file", e);
            }
        });
    }

    /**
     * Submit task to list non-directory files in parent directory
     */
    private Future<List<String>> submitFileList(File parent) {
        return mExecutorService.get().submit(
                () -> {
                    File[] files = parent.listFiles();
                    List<String> fileNames = new ArrayList<>();
                    if (files != null && files.length > 0) {
                        for (File file : files) {
                            if (!file.isDirectory()) {
                                fileNames.add(file.getName());
                            }
                        }
                    }
                    return fileNames;
                }
        );
    }
}
